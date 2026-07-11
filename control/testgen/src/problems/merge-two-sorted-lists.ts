import { DEFAULT_BUDGET } from '../framework/buckets.js'
import { fillBuckets } from '../framework/gen.js'
import { toList, fromList, ListNode } from '../framework/wire.js'
import type { ProblemModule } from '../framework/types.js'
import type { Rng } from '../framework/rng.js'

/**
 * merge-two-sorted-lists: list-node<int>, list-node<int> -> list-node<int>.
 * Inputs are sorted ascending arrays (linked-list wire form). Reference deserializes to
 * ListNodes, merges by pointer, and serializes back — exercising the wire helpers end-to-end.
 */

function sortedArr(rng: Rng, maxLen: number, lo: number, hi: number): number[] {
  const n = rng.int(0, maxLen)
  return rng.ints(n, lo, hi).sort((a, b) => a - b)
}

const isSortedAsc = (a: number[]) => a.every((x, i) => i === 0 || a[i - 1] <= x)

const mergeTwoSortedLists: ProblemModule = {
  meta: {
    slug: 'merge-two-sorted-lists',
    version: 1,
    rngSeed: 20260713,
    budget: DEFAULT_BUDGET,
    timeLimitMs: 2000,
    checker: 'exact',
  },

  generator(rng, budget) {
    return fillBuckets(rng, budget, (kind, i, r) => {
      if (kind === 'edge') {
        const catalog: unknown[][] = [
          [[1, 2, 4], [1, 3, 4]],
          [[], []],
          [[], [0]],
          [[5], []],
          [[1, 1, 1], [1, 1, 1]],
          [[-100, 0, 100], [-50, 50]],
        ]
        if (i < catalog.length) return catalog[i]
        return [sortedArr(r, 4, -10, 10), sortedArr(r, 4, -10, 10)]
      }
      if (kind === 'randomSmall') return [sortedArr(r, 20, -100, 100), sortedArr(r, 20, -100, 100)]
      if (kind === 'randomMedium') return [sortedArr(r, 50, -1000, 1000), sortedArr(r, 50, -1000, 1000)]
      return [sortedArr(r, 500, -100000, 100000), sortedArr(r, 500, -100000, 100000)]
    })
  },

  validator(input) {
    const [a, b] = input as [number[], number[]]
    for (const arr of [a, b]) {
      if (!Array.isArray(arr)) throw new Error('merge: inputs must be arrays')
      if (!isSortedAsc(arr)) throw new Error('merge: inputs must be sorted ascending')
      for (const x of arr) if (!Number.isInteger(x)) throw new Error('merge: values must be ints')
    }
  },

  // Oracle: pointer merge over real ListNodes.
  reference(...input) {
    const [a, b] = input as [number[], number[]]
    let l1 = toList(a)
    let l2 = toList(b)
    const dummy = new ListNode(0)
    let tail = dummy
    while (l1 && l2) {
      if (l1.val <= l2.val) {
        tail.next = l1
        l1 = l1.next
      } else {
        tail.next = l2
        l2 = l2.next
      }
      tail = tail.next
    }
    tail.next = l1 ?? l2
    return fromList(dummy.next)
  },

  // Cross-check: concat + sort (equivalent for two sorted inputs).
  brute(...input) {
    const [a, b] = input as [number[], number[]]
    return [...a, ...b].sort((x, y) => x - y)
  },
}

export default mergeTwoSortedLists
