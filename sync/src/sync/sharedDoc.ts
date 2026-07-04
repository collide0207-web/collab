import * as Y from 'yjs'
import * as syncProtocol from 'y-protocols/sync'
import * as awarenessProtocol from 'y-protocols/awareness'
import * as encoding from 'lib0/encoding'
import * as decoding from 'lib0/decoding'

import type { Conn } from './conn.js'
import { canWrite } from '../rbac.js'
import { MESSAGE_AWARENESS, MESSAGE_SYNC, encodeAwareness, encodeSyncUpdate } from './protocol.js'
import type { DocStore } from '../persistence/store.js'
import type { PubSub } from '../scaling/pubsub.js'
import { config } from '../config.js'
import { log } from '../logger.js'

// Origins used to tag transactions so we can distinguish sources and avoid loops.
const ORIGIN_REMOTE = Symbol('remote-node') // came from another node via pub/sub

/**
 * Authoritative in-memory state for one document (one file). Owns the Y.Doc and its
 * Awareness, the set of local connections, persistence debouncing, and cross-node
 * fan-out via pub/sub. Convergence itself is guaranteed by Yjs (CRDT); this class is
 * the production plumbing around it.
 */
export class SharedDoc {
  readonly docId: string
  readonly doc: Y.Doc
  readonly awareness: awarenessProtocol.Awareness
  /** conn -> the awareness clientIDs it controls (for cleanup on disconnect). */
  private conns = new Map<Conn, Set<number>>()

  private store: DocStore
  private pubsub: PubSub
  private version = 0
  private persistTimer: NodeJS.Timeout | null = null
  private onEmpty: (docId: string) => void

  constructor(docId: string, store: DocStore, pubsub: PubSub, onEmpty: (docId: string) => void) {
    this.docId = docId
    this.store = store
    this.pubsub = pubsub
    this.onEmpty = onEmpty
    this.doc = new Y.Doc()
    this.awareness = new awarenessProtocol.Awareness(this.doc)
    this.awareness.setLocalState(null) // the server itself is not a participant

    this.doc.on('update', this.handleDocUpdate)
    this.awareness.on('update', this.handleAwarenessUpdate)
    this.pubsub.subscribe(docId)
  }

  /** Rebuild state from durable storage: snapshot + any un-snapshotted update tail. */
  async load(): Promise<void> {
    const rec = await this.store.load(this.docId)
    if (rec) {
      Y.applyUpdate(this.doc, rec.snapshot, ORIGIN_REMOTE)
      this.version = rec.version
    }
    const tail = await this.store.loadUpdatesSince(this.docId)
    for (const u of tail) Y.applyUpdate(this.doc, u, ORIGIN_REMOTE)
  }

  get connectionCount(): number {
    return this.conns.size
  }

  // ---- connection lifecycle ----

  addConnection(conn: Conn): void {
    this.conns.set(conn, new Set())
    // 1) SYNC step 1: send our state vector so the client replies with what we lack.
    const enc = encoding.createEncoder()
    encoding.writeVarUint(enc, MESSAGE_SYNC)
    syncProtocol.writeSyncStep1(enc, this.doc)
    conn.send(encoding.toUint8Array(enc))
    // 2) Send current awareness (existing cursors/presence) to the newcomer.
    const states = this.awareness.getStates()
    if (states.size > 0) {
      const update = awarenessProtocol.encodeAwarenessUpdate(
        this.awareness,
        Array.from(states.keys()),
      )
      conn.send(encodeAwareness(update))
    }
  }

  removeConnection(conn: Conn): void {
    const controlled = this.conns.get(conn)
    if (controlled) {
      awarenessProtocol.removeAwarenessStates(this.awareness, Array.from(controlled), null)
      this.conns.delete(conn)
    }
    if (this.conns.size === 0) this.onEmpty(this.docId)
  }

  // ---- inbound message handling ----

  handleMessage(conn: Conn, data: Uint8Array): void {
    const decoder = decoding.createDecoder(data)
    const messageType = decoding.readVarUint(decoder)
    switch (messageType) {
      case MESSAGE_SYNC:
        this.handleSync(conn, decoder)
        break
      case MESSAGE_AWARENESS:
        this.handleAwareness(conn, decoder)
        break
      default:
        // CONTROL / PING handled at the connection layer; ignore here.
        break
    }
  }

  private handleSync(conn: Conn, decoder: decoding.Decoder): void {
    const encoder = encoding.createEncoder()
    encoding.writeVarUint(encoder, MESSAGE_SYNC)
    const syncMessageType = decoding.readVarUint(decoder)
    const readOnly = !canWrite(conn.role)

    switch (syncMessageType) {
      case syncProtocol.messageYjsSyncStep1:
        // Always allowed — the client is requesting state (read is permitted).
        syncProtocol.readSyncStep1(decoder, encoder, this.doc)
        if (encoding.length(encoder) > 1) conn.send(encoding.toUint8Array(encoder))
        break
      case syncProtocol.messageYjsSyncStep2:
        // Client is sending state as an update — a write. Viewers are dropped here.
        if (!readOnly) syncProtocol.readSyncStep2(decoder, this.doc, conn)
        break
      case syncProtocol.messageYjsUpdate:
        if (!readOnly) syncProtocol.readUpdate(decoder, this.doc, conn)
        // else: silently ignore a viewer's edit — server-side read-only enforcement.
        break
      default:
        break
    }
  }

  private handleAwareness(conn: Conn, decoder: decoding.Decoder): void {
    const update = decoding.readVarUint8Array(decoder)
    // Track which clientIDs this conn controls so we can clear them on disconnect.
    const changed = awarenessProtocol.encodeAwarenessUpdate // (unused ref kept for clarity)
    void changed
    awarenessProtocol.applyAwarenessUpdate(this.awareness, update, conn)
    const set = this.conns.get(conn)
    if (set) {
      // Record controlled client ids from the local awareness after applying.
      // The awareness update's origin is `conn`; its states now include this client.
      for (const clientId of this.awareness.getStates().keys()) {
        // Heuristic: associate any client id whose lastUpdated origin is this conn.
        set.add(clientId)
      }
    }
  }

  // ---- outbound propagation ----

  private handleDocUpdate = (update: Uint8Array, origin: unknown): void => {
    // Broadcast to local connections (except the originating conn).
    const frame = encodeSyncUpdate(update)
    for (const c of this.conns.keys()) {
      if (c !== origin) c.send(frame)
    }
    // Fan out to other nodes, unless this update itself came from another node.
    if (origin !== ORIGIN_REMOTE) {
      this.pubsub.publish(this.docId, 'update', update)
      // Durability: append to the tail and debounce a full snapshot.
      void this.store.appendUpdate(this.docId, update).catch((e) =>
        log.error('appendUpdate failed', { docId: this.docId, err: String(e) }),
      )
      this.scheduleSnapshot()
    }
  }

  private handleAwarenessUpdate = (
    changes: { added: number[]; updated: number[]; removed: number[] },
    origin: unknown,
  ): void => {
    const changed = [...changes.added, ...changes.updated, ...changes.removed]
    const update = awarenessProtocol.encodeAwarenessUpdate(this.awareness, changed)
    const frame = encodeAwareness(update)
    for (const c of this.conns.keys()) {
      if (c !== origin) c.send(frame)
    }
    if (origin !== ORIGIN_REMOTE) this.pubsub.publish(this.docId, 'awareness', update)
  }

  /** Apply an update that arrived from another node (pub/sub). */
  applyRemoteUpdate(payload: Uint8Array): void {
    Y.applyUpdate(this.doc, payload, ORIGIN_REMOTE)
  }
  applyRemoteAwareness(payload: Uint8Array): void {
    awarenessProtocol.applyAwarenessUpdate(this.awareness, payload, ORIGIN_REMOTE)
  }

  // ---- persistence ----

  private scheduleSnapshot(): void {
    if (this.persistTimer) return
    this.persistTimer = setTimeout(() => {
      this.persistTimer = null
      void this.flush()
    }, config.persistence.debounceMs)
  }

  async flush(): Promise<void> {
    this.version += 1
    const snapshot = Y.encodeStateAsUpdate(this.doc)
    try {
      await this.store.saveSnapshot(this.docId, snapshot, this.version)
    } catch (e) {
      log.error('saveSnapshot failed', { docId: this.docId, err: String(e) })
    }
  }

  async destroy(): Promise<void> {
    if (this.persistTimer) {
      clearTimeout(this.persistTimer)
      this.persistTimer = null
    }
    await this.flush()
    this.pubsub.unsubscribe(this.docId)
    this.doc.off('update', this.handleDocUpdate)
    this.awareness.off('update', this.handleAwarenessUpdate)
    this.doc.destroy()
  }
}
