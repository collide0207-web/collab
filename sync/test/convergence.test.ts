import { describe, it, expect } from 'vitest'
import * as Y from 'yjs'

/**
 * These tests prove the core requirement — the document ALWAYS converges to a single
 * correct state — using the same CRDT engine (Yjs) the server and clients run. They
 * exercise the hard concurrency cases from the spec without needing a network.
 */

/** Apply a set of updates to a fresh doc in a given order; return the text. */
function replay(updates: Uint8Array[], order: number[]): string {
  const doc = new Y.Doc()
  for (const i of order) Y.applyUpdate(doc, updates[i])
  return doc.getText('t').toString()
}

/** Capture the incremental update produced by a local mutation. */
function capture(doc: Y.Doc, mutate: () => void): Uint8Array {
  let out: Uint8Array = new Uint8Array()
  const handler = (u: Uint8Array) => (out = u)
  doc.on('update', handler)
  mutate()
  doc.off('update', handler)
  return out
}

describe('CRDT convergence', () => {
  it('two users typing at the same position converge identically on all replicas', () => {
    const a = new Y.Doc()
    const b = new Y.Doc()
    // sync empty docs
    Y.applyUpdate(b, Y.encodeStateAsUpdate(a))
    Y.applyUpdate(a, Y.encodeStateAsUpdate(b))

    const ua = capture(a, () => a.getText('t').insert(0, 'AAA'))
    const ub = capture(b, () => b.getText('t').insert(0, 'BBB'))

    // cross-apply concurrent edits
    Y.applyUpdate(a, ub)
    Y.applyUpdate(b, ua)

    expect(a.getText('t').toString()).toBe(b.getText('t').toString())
    expect(a.getText('t').toString().length).toBe(6) // no edits lost
  })

  it('is idempotent — applying the same update twice is a no-op', () => {
    const base = new Y.Doc()
    const u = capture(base, () => base.getText('t').insert(0, 'hello'))

    const d = new Y.Doc()
    Y.applyUpdate(d, u)
    Y.applyUpdate(d, u) // duplicate delivery
    expect(d.getText('t').toString()).toBe('hello')
  })

  it('is order-independent — out-of-order delivery converges', () => {
    const src = new Y.Doc()
    const updates: Uint8Array[] = []
    updates.push(capture(src, () => src.getText('t').insert(0, 'Hello ')))
    updates.push(capture(src, () => src.getText('t').insert(6, 'brave ')))
    updates.push(capture(src, () => src.getText('t').insert(12, 'world')))

    const inOrder = replay(updates, [0, 1, 2])
    const shuffled = replay(updates, [2, 0, 1])
    const reversed = replay(updates, [2, 1, 0])
    expect(shuffled).toBe(inOrder)
    expect(reversed).toBe(inOrder)
  })

  it('handles insert while another deletes overlapping text', () => {
    const a = new Y.Doc()
    a.getText('t').insert(0, '0123456789')
    const b = new Y.Doc()
    Y.applyUpdate(b, Y.encodeStateAsUpdate(a))

    const del = capture(a, () => a.getText('t').delete(2, 4)) // remove "2345"
    const ins = capture(b, () => b.getText('t').insert(4, 'XYZ')) // insert inside that range

    Y.applyUpdate(a, ins)
    Y.applyUpdate(b, del)
    expect(a.getText('t').toString()).toBe(b.getText('t').toString())
  })

  it('handles overlapping deletes (both remove part of the same range)', () => {
    const a = new Y.Doc()
    a.getText('t').insert(0, 'ABCDEFGH')
    const b = new Y.Doc()
    Y.applyUpdate(b, Y.encodeStateAsUpdate(a))

    const da = capture(a, () => a.getText('t').delete(1, 4)) // BCDE
    const db = capture(b, () => b.getText('t').delete(3, 4)) // DEFG (overlaps DE)

    Y.applyUpdate(a, db)
    Y.applyUpdate(b, da)
    expect(a.getText('t').toString()).toBe(b.getText('t').toString())
  })

  it('preserves Unicode / emoji correctly under concurrent edits', () => {
    const a = new Y.Doc()
    const b = new Y.Doc()
    const ua = capture(a, () => a.getText('t').insert(0, '😀🎉'))
    const ub = capture(b, () => b.getText('t').insert(0, 'café'))
    Y.applyUpdate(a, ub)
    Y.applyUpdate(b, ua)
    expect(a.getText('t').toString()).toBe(b.getText('t').toString())
  })

  it('per-user undo only reverts the user’s own operations', () => {
    const doc = new Y.Doc()
    const text = doc.getText('t')
    const meOrigin = { user: 'me' }

    // A collaborator's edit (different origin) — must never be undone by my undo.
    doc.transact(() => text.insert(0, 'REMOTE '), { user: 'other' })
    // My edit, tracked by my UndoManager.
    const undo = new Y.UndoManager(text, { trackedOrigins: new Set([meOrigin]) })
    doc.transact(() => text.insert(text.length, 'MINE'), meOrigin)

    expect(text.toString()).toBe('REMOTE MINE')
    undo.undo()
    expect(text.toString()).toBe('REMOTE ') // only my edit reverted
  })
})
