import { describe, expect, it } from 'vitest'
import { toList, fromList, toTree, fromTree, toGraph, fromGraph } from '../src/framework/wire.js'

describe('wire: list-node', () => {
  it('round-trips arrays including empty', () => {
    expect(fromList(toList([1, 2, 3]))).toEqual([1, 2, 3])
    expect(fromList(toList([]))).toEqual([])
    expect(toList([])).toBeNull()
  })
})

describe('wire: tree-node (matches SP2 canonical forms)', () => {
  it('round-trips level-order with nulls', () => {
    expect(fromTree(toTree([3, 9, 20, null, null, 15, 7]))).toEqual([3, 9, 20, null, null, 15, 7])
  })
  it('empty tree', () => {
    expect(toTree([])).toBeNull()
    expect(fromTree(null)).toEqual([])
  })
  it('trims trailing nulls on serialize', () => {
    // 1 with only a left child -> [1,2] (right + grandchildren nulls trimmed)
    expect(fromTree(toTree([1, 2, null]))).toEqual([1, 2])
  })
})

describe('wire: graph-node (Clone-Graph adjacency)', () => {
  it('identity round-trips the canonical adjacency', () => {
    const adj = [
      [2, 4],
      [1, 3],
      [2, 4],
      [1, 3],
    ]
    expect(fromGraph(toGraph(adj))).toEqual(adj)
  })
  it('empty graph and single node', () => {
    expect(toGraph([])).toBeNull()
    expect(fromGraph(null)).toEqual([])
    expect(fromGraph(toGraph([[]]))).toEqual([[]])
  })
})
