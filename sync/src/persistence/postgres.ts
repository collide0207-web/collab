import pg from 'pg'
import type { DocRecord, DocStore } from './store.js'
import { log } from '../logger.js'

/**
 * PostgreSQL-backed document store.
 *
 * Schema (created on init):
 *   documents(doc_id PK, snapshot BYTEA, version INT, updated_at TIMESTAMPTZ)
 *   document_updates(id BIGSERIAL, doc_id, seq, update BYTEA, created_at)
 *
 * Snapshots are the compacted source of truth; document_updates is the durable tail
 * written between snapshots so a crash loses at most the un-snapshotted tail, which
 * is replayed on load.
 */
export class PostgresDocStore implements DocStore {
  private pool: pg.Pool

  constructor(databaseUrl: string) {
    this.pool = new pg.Pool({ connectionString: databaseUrl, max: 10 })
    // node-postgres emits 'error' on IDLE clients when the DB drops the connection
    // (restart, network blip, idle timeout). Without a listener Node treats it as an
    // unhandled 'error' and crashes the whole process. Log and swallow it — the pool
    // discards the dead client and opens a fresh one on the next query.
    this.pool.on('error', (err) => {
      log.error('postgres pool: idle client error (recovering)', { err: String(err) })
    })
  }

  async init(): Promise<void> {
    await this.pool.query(`
      CREATE TABLE IF NOT EXISTS documents (
        doc_id     TEXT PRIMARY KEY,
        snapshot   BYTEA NOT NULL,
        version    INTEGER NOT NULL DEFAULT 0,
        updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
      );
      CREATE TABLE IF NOT EXISTS document_updates (
        id         BIGSERIAL PRIMARY KEY,
        doc_id     TEXT NOT NULL,
        update     BYTEA NOT NULL,
        created_at TIMESTAMPTZ NOT NULL DEFAULT now()
      );
      CREATE INDEX IF NOT EXISTS idx_doc_updates_doc ON document_updates(doc_id, id);
    `)
  }

  async load(docId: string): Promise<DocRecord | null> {
    const res = await this.pool.query(
      'SELECT snapshot, version FROM documents WHERE doc_id = $1',
      [docId],
    )
    if (res.rowCount === 0) return null
    const row = res.rows[0]
    return { snapshot: new Uint8Array(row.snapshot), version: row.version }
  }

  async saveSnapshot(docId: string, snapshot: Uint8Array, version: number): Promise<void> {
    const client = await this.pool.connect()
    try {
      await client.query('BEGIN')
      await client.query(
        `INSERT INTO documents (doc_id, snapshot, version, updated_at)
         VALUES ($1, $2, $3, now())
         ON CONFLICT (doc_id) DO UPDATE
           SET snapshot = EXCLUDED.snapshot, version = EXCLUDED.version, updated_at = now()`,
        [docId, Buffer.from(snapshot), version],
      )
      // Compact: drop the now-obsolete update tail.
      await client.query('DELETE FROM document_updates WHERE doc_id = $1', [docId])
      await client.query('COMMIT')
    } catch (e) {
      await client.query('ROLLBACK')
      throw e
    } finally {
      client.release()
    }
  }

  async appendUpdate(docId: string, update: Uint8Array): Promise<void> {
    await this.pool.query(
      'INSERT INTO document_updates (doc_id, update) VALUES ($1, $2)',
      [docId, Buffer.from(update)],
    )
  }

  async loadUpdatesSince(docId: string): Promise<Uint8Array[]> {
    const res = await this.pool.query(
      'SELECT update FROM document_updates WHERE doc_id = $1 ORDER BY id ASC',
      [docId],
    )
    return res.rows.map((r: { update: Buffer }) => new Uint8Array(r.update))
  }

  async close(): Promise<void> {
    await this.pool.end()
  }
}
