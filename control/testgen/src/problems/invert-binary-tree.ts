import { DEFAULT_BUDGET } from '../framework/buckets.js'
import { fillBuckets } from '../framework/gen.js'
import { toTree, fromTree, TreeNode } from '../framework/wire.js'
import { canonical } from '../framework/checkers.js'
import type { ProblemModule } from '../framework/types.js'
import type { Rng } from '../framework/rng.js'

/**
 * invert-binary-tree: tree-node<int> -> tree-node<int>.
 * Inputs are canonical level-order arrays. The generator builds real random trees and serializes
 * them (guaranteeing a valid, canonical wire form). Reference inverts recursively; brute inverts
 * iteratively — two independent methods.
 */

/** Random tree with up to `maxNodes` nodes; returns its canonical level-order wire array. */
function randomTreeWire(rng: Rng, maxNodes: number, lo: number, hi: number): (number | null)[] {
  if (maxNodes === 0) return []
  let budget = maxNodes
  const build = (): TreeNode | null => {
    if (budget <= 0) return null
    budget--
    const n = new TreeNode(rng.int(lo, hi))
    // Bias toward growing while budget remains.
    if (rng.next() < 0.7) n.left = build()
    if (rng.next() < 0.7) n.right = build()
    return n
  }
  return fromTree(build())
}

const invertBinaryTree: ProblemModule = {
  meta: {
    slug: 'invert-binary-tree',
    version: 1,
    rngSeed: 20260714,
    budget: DEFAULT_BUDGET,
    timeLimitMs: 2000,
    checker: 'exact',
  },

  generator(rng, budget) {
    return fillBuckets(rng, budget, (kind, i, r) => {
      if (kind === 'edge') {
        const catalog: unknown[][] = [
          [[4, 2, 7, 1, 3, 6, 9]],
          [[]],
          [[1]],
          [[1, 2]],
          [[1, null, 2]],
          [[1, 2, 3, null, null, 4, 5]],
          [[-1, -2, -3]],
        ]
        if (i < catalog.length) return catalog[i]
        return [randomTreeWire(r, r.int(1, 7), -10, 10)]
      }
      if (kind === 'randomSmall') return [randomTreeWire(r, r.int(1, 20), -100, 100)]
      if (kind === 'randomMedium') return [randomTreeWire(r, r.int(20, 100), -1000, 1000)]
      return [randomTreeWire(r, r.int(200, 800), -100000, 100000)]
    })
  },

  validator(input) {
    const [wire] = input as [(number | null)[]]
    if (!Array.isArray(wire)) throw new Error('invert: input must be an array')
    // Must already be canonical level-order (round-trips through the wire helpers unchanged).
    if (canonical(fromTree(toTree(wire))) !== canonical(wire)) throw new Error('invert: non-canonical tree wire')
    for (const x of wire) if (x != null && !Number.isInteger(x)) throw new Error('invert: values must be int|null')
  },

  // Oracle: recursive mirror.
  reference(...input) {
    const [wire] = input as [(number | null)[]]
    const invert = (n: TreeNode | null): TreeNode | null => {
      if (!n) return null
      const l = invert(n.left)
      n.left = invert(n.right)
      n.right = l
      return n
    }
    return fromTree(invert(toTree(wire)))
  },

  // Cross-check: iterative BFS swap.
  brute(...input) {
    const [wire] = input as [(number | null)[]]
    const root = toTree(wire)
    const q: (TreeNode | null)[] = [root]
    while (q.length) {
      const n = q.shift()
      if (!n) continue
      ;[n.left, n.right] = [n.right, n.left]
      q.push(n.left, n.right)
    }
    return fromTree(root)
  },
}

export default invertBinaryTree
