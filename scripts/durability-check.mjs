// End-to-end durability proof:
//  1. connect a WS client to the running collab server
//  2. run the Yjs sync handshake and insert text
//  3. wait past the persist debounce
//  4. read the snapshot BACK from Postgres and decode it — must equal what we typed
//
// Run with the server + docker up:  node scripts/durability-check.mjs
import WebSocket from 'ws'
import * as Y from 'yjs'
import * as syncProtocol from 'y-protocols/sync'
import * as encoding from 'lib0/encoding'
import * as decoding from 'lib0/decoding'
import pg from 'pg'

const DOC_ID = 'smoke-room/src/index.js'
const EXPECTED = 'hello-durable-世界-😀'
const MESSAGE_SYNC = 0

const doc = new Y.Doc()
const ws = new WebSocket(`ws://localhost:4000/doc/${encodeURIComponent(DOC_ID)}`)
ws.binaryType = 'arraybuffer'

function send(bytes) {
  if (ws.readyState === ws.OPEN) ws.send(bytes)
}

doc.on('update', (update) => {
  const enc = encoding.createEncoder()
  encoding.writeVarUint(enc, MESSAGE_SYNC)
  syncProtocol.writeUpdate(enc, update)
  send(encoding.toUint8Array(enc))
})

ws.on('open', () => {
  const enc = encoding.createEncoder()
  encoding.writeVarUint(enc, MESSAGE_SYNC)
  syncProtocol.writeSyncStep1(enc, doc)
  send(encoding.toUint8Array(enc))
})

ws.on('message', (data) => {
  const bytes = new Uint8Array(data)
  const decoder = decoding.createDecoder(bytes)
  const type = decoding.readVarUint(decoder)
  if (type === MESSAGE_SYNC) {
    const enc = encoding.createEncoder()
    encoding.writeVarUint(enc, MESSAGE_SYNC)
    syncProtocol.readSyncMessage(decoder, enc, doc, ws)
    if (encoding.length(enc) > 1) send(encoding.toUint8Array(enc))
  }
})

async function verifyInPostgres() {
  const client = new pg.Client({ connectionString: 'postgres://collide:collide@localhost:5432/collide' })
  await client.connect()
  const res = await client.query('SELECT snapshot FROM documents WHERE doc_id = $1', [DOC_ID])
  await client.end()
  if (res.rowCount === 0) throw new Error('FAIL: no snapshot row persisted')
  const restored = new Y.Doc()
  Y.applyUpdate(restored, new Uint8Array(res.rows[0].snapshot))
  const text = restored.getText('monaco').toString()
  if (text !== EXPECTED) throw new Error(`FAIL: persisted text "${text}" !== "${EXPECTED}"`)
  console.log(`PASS: snapshot persisted to Postgres and decoded correctly → "${text}"`)
}

ws.on('open', () => {
  setTimeout(() => doc.getText('monaco').insert(0, EXPECTED), 300) // after handshake
  setTimeout(async () => {
    ws.close()
    try {
      await verifyInPostgres()
      process.exit(0)
    } catch (e) {
      console.error(String(e))
      process.exit(1)
    }
  }, 3500) // > PERSIST_DEBOUNCE_MS (2000)
})

ws.on('error', (e) => {
  console.error('ws error', String(e))
  process.exit(1)
})
