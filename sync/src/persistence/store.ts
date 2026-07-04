/**
 * Durable document storage. A document is persisted as a merged Yjs snapshot
 * (Y.encodeStateAsUpdate) plus, optionally, an append-only tail of recent updates
 * for crash-safety between snapshots.
 *
 * NOTE: persistence stores incremental *state*, never per-keystroke rows — the
 * server debounces snapshots. This satisfies "avoid transmitting/persisting the
 * entire document after every change" at the storage layer.
 */
export interface DocRecord {
  snapshot: Uint8Array
  version: number
}

export interface DocStore {
  /** Load the latest merged snapshot, or null if the doc has never been saved. */
  load(docId: string): Promise<DocRecord | null>
  /** Persist a merged snapshot (replaces prior snapshot + compacts the update tail). */
  saveSnapshot(docId: string, snapshot: Uint8Array, version: number): Promise<void>
  /** Append a single update for durability between snapshots (optional in dev). */
  appendUpdate(docId: string, update: Uint8Array): Promise<void>
  /** Load updates appended since the last snapshot (for crash recovery). */
  loadUpdatesSince(docId: string): Promise<Uint8Array[]>
  close(): Promise<void>
}

/** In-memory store — dev/tests only. Not durable across process restarts. */
export class MemoryDocStore implements DocStore {
  private snaps = new Map<string, DocRecord>()
  private tails = new Map<string, Uint8Array[]>()

  async load(docId: string): Promise<DocRecord | null> {
    return this.snaps.get(docId) ?? null
  }
  async saveSnapshot(docId: string, snapshot: Uint8Array, version: number): Promise<void> {
    this.snaps.set(docId, { snapshot, version })
    this.tails.set(docId, [])
  }
  async appendUpdate(docId: string, update: Uint8Array): Promise<void> {
    const t = this.tails.get(docId) ?? []
    t.push(update)
    this.tails.set(docId, t)
  }
  async loadUpdatesSince(docId: string): Promise<Uint8Array[]> {
    return this.tails.get(docId) ?? []
  }
  async close(): Promise<void> {}
}
