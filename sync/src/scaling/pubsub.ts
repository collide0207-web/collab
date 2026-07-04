import { Redis } from 'ioredis'

/**
 * Cross-node fan-out. When a doc receives an update on one node, it is published so
 * every other node holding that doc can apply it and forward to its own clients.
 * Because Yjs updates are commutative + idempotent, cross-node ordering is
 * irrelevant and re-delivery is safe.
 *
 * Each payload is prefixed with this node's id so a node ignores its own echoes.
 */
export type DocMessageHandler = (docId: string, channel: 'update' | 'awareness', payload: Uint8Array) => void

export interface PubSub {
  publish(docId: string, channel: 'update' | 'awareness', payload: Uint8Array): void
  subscribe(docId: string): void
  unsubscribe(docId: string): void
  onMessage(handler: DocMessageHandler): void
  close(): Promise<void>
  readonly nodeId: string
}

const enc = (nodeId: string, payload: Uint8Array): Buffer => {
  const idBuf = Buffer.from(nodeId, 'utf8')
  return Buffer.concat([Buffer.from([idBuf.length]), idBuf, Buffer.from(payload)])
}
const dec = (buf: Buffer): { nodeId: string; payload: Uint8Array } => {
  const len = buf[0]
  const nodeId = buf.subarray(1, 1 + len).toString('utf8')
  return { nodeId, payload: new Uint8Array(buf.subarray(1 + len)) }
}
const chan = (docId: string, channel: string) => `collab:${channel}:${docId}`

/** Single-node pub/sub — messages never leave the process. */
export class InProcessPubSub implements PubSub {
  readonly nodeId = 'local'
  private handler: DocMessageHandler | null = null
  publish(): void {
    /* no other nodes; local broadcast is handled directly by the DocManager */
  }
  subscribe(): void {}
  unsubscribe(): void {}
  onMessage(handler: DocMessageHandler): void {
    this.handler = handler
  }
  async close(): Promise<void> {
    this.handler = null
  }
}

/** Redis-backed pub/sub for horizontal scale. */
export class RedisPubSub implements PubSub {
  readonly nodeId: string
  private pub: Redis
  private sub: Redis
  private handler: DocMessageHandler | null = null
  private subscribed = new Set<string>()

  constructor(url: string, nodeId: string) {
    this.nodeId = nodeId
    this.pub = new Redis(url)
    this.sub = new Redis(url)
    this.sub.on('messageBuffer', (channelBuf: Buffer, message: Buffer) => {
      const channel = channelBuf.toString('utf8')
      const m = /^collab:(update|awareness):(.+)$/.exec(channel)
      if (!m || !this.handler) return
      const kind = m[1] as 'update' | 'awareness'
      const docId = m[2]
      const { nodeId, payload } = dec(message)
      if (nodeId === this.nodeId) return // ignore our own echo
      this.handler(docId, kind, payload)
    })
  }

  publish(docId: string, channel: 'update' | 'awareness', payload: Uint8Array): void {
    void this.pub.publish(chan(docId, channel), enc(this.nodeId, payload) as unknown as string)
  }
  subscribe(docId: string): void {
    if (this.subscribed.has(docId)) return
    this.subscribed.add(docId)
    void this.sub.subscribe(chan(docId, 'update'), chan(docId, 'awareness'))
  }
  unsubscribe(docId: string): void {
    if (!this.subscribed.has(docId)) return
    this.subscribed.delete(docId)
    void this.sub.unsubscribe(chan(docId, 'update'), chan(docId, 'awareness'))
  }
  onMessage(handler: DocMessageHandler): void {
    this.handler = handler
  }
  async close(): Promise<void> {
    await Promise.all([this.pub.quit(), this.sub.quit()])
  }
}
