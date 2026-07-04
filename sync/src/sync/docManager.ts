import { SharedDoc } from './sharedDoc.js'
import type { DocStore } from '../persistence/store.js'
import type { PubSub } from '../scaling/pubsub.js'
import { config } from '../config.js'
import { log } from '../logger.js'

/**
 * Owns the set of resident documents on this node. Loads a SharedDoc on first
 * access, reference-counts connections, and evicts (after a final persist) once a
 * doc has been idle with no local connections. Routes cross-node pub/sub messages to
 * the right doc.
 */
export class DocManager {
  private docs = new Map<string, SharedDoc>()
  private loading = new Map<string, Promise<SharedDoc>>()
  private evictTimers = new Map<string, NodeJS.Timeout>()

  constructor(
    private store: DocStore,
    private pubsub: PubSub,
  ) {
    this.pubsub.onMessage((docId, channel, payload) => {
      const doc = this.docs.get(docId)
      if (!doc) return // we don't hold this doc; nothing to do
      if (channel === 'update') doc.applyRemoteUpdate(payload)
      else doc.applyRemoteAwareness(payload)
    })
  }

  /** Get (or load) the SharedDoc for a docId. Safe under concurrent callers. */
  async get(docId: string): Promise<SharedDoc> {
    const existing = this.docs.get(docId)
    if (existing) {
      this.cancelEvict(docId)
      return existing
    }
    const inFlight = this.loading.get(docId)
    if (inFlight) return inFlight

    const promise = (async () => {
      const doc = new SharedDoc(docId, this.store, this.pubsub, (id) => this.onDocEmpty(id))
      await doc.load()
      this.docs.set(docId, doc)
      this.loading.delete(docId)
      log.info('doc loaded', { docId })
      return doc
    })()
    this.loading.set(docId, promise)
    return promise
  }

  private onDocEmpty(docId: string): void {
    // Debounced eviction: keep the doc warm briefly in case someone reconnects.
    this.cancelEvict(docId)
    const t = setTimeout(() => {
      const doc = this.docs.get(docId)
      if (doc && doc.connectionCount === 0) {
        this.docs.delete(docId)
        this.evictTimers.delete(docId)
        void doc.destroy().then(() => log.info('doc evicted', { docId }))
      }
    }, config.limits.docIdleEvictMs)
    this.evictTimers.set(docId, t)
  }

  private cancelEvict(docId: string): void {
    const t = this.evictTimers.get(docId)
    if (t) {
      clearTimeout(t)
      this.evictTimers.delete(docId)
    }
  }

  /** Flush and destroy all docs (graceful shutdown). */
  async shutdown(): Promise<void> {
    for (const t of this.evictTimers.values()) clearTimeout(t)
    this.evictTimers.clear()
    await Promise.all(Array.from(this.docs.values()).map((d) => d.destroy()))
    this.docs.clear()
  }
}
