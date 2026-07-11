import { DEFAULT_BUDGET } from '../framework/buckets.js'
import { fillBuckets } from '../framework/gen.js'
import { toGraph, fromGraph, GraphNode } from '../framework/wire.js'
import { canonical } from '../framework/checkers.js'
import type { ProblemModule } from '../framework/types.js'
import type { Rng } from '../framework/rng.js'

/**
 * clone-graph: graph-node<int> -> graph-node<int>.
 * Input is a connected undirected graph as a 1-indexed adjacency list (Clone-Graph wire form).
 * Reference performs a real deep clone; brute normalizes without cloning — both must serialize to
 * the same canonical adjacency (neighbours sorted, BFS from node 1).
 */

/** Random connected undirected graph on `n` nodes -> canonical adjacency (values 1..n). */
function randomGraphWire(rng: Rng, n: number, extraProb: number): number[][] {
  if (n === 0) return []
  const adj: Set<number>[] = Array.from({ length: n }, () => new Set<number>())
  const link = (a: number, b: number) => {
    if (a === b) return
    adj[a - 1].add(b)
    adj[b - 1].add(a)
  }
  // Spanning tree guarantees connectivity.
  for (let v = 2; v <= n; v++) link(v, rng.int(1, v - 1))
  // Extra random edges.
  for (let a = 1; a <= n; a++)
    for (let b = a + 1; b <= n; b++) if (rng.next() < extraProb) link(a, b)
  // Canonicalize via the wire round-trip so it matches identity-clone output exactly.
  const raw = adj.map((s) => [...s].sort((x, y) => x - y))
  return fromGraph(toGraph(raw))
}

const cloneGraph: ProblemModule = {
  meta: {
    slug: 'clone-graph',
    version: 1,
    rngSeed: 20260715,
    budget: DEFAULT_BUDGET,
    timeLimitMs: 2000,
    checker: 'exact',
  },

  generator(rng, budget) {
    return fillBuckets(rng, budget, (kind, i, r) => {
      if (kind === 'edge') {
        const catalog: unknown[][] = [
          [[[2, 4], [1, 3], [2, 4], [1, 3]]],
          [[]],
          [[[]]],
          [[[2], [1]]],
          [[[2, 3], [1, 3], [1, 2]]],
        ]
        if (i < catalog.length) return catalog[i]
        return [randomGraphWire(r, r.int(1, 5), 0.4)]
      }
      if (kind === 'randomSmall') return [randomGraphWire(r, r.int(1, 8), 0.3)]
      if (kind === 'randomMedium') return [randomGraphWire(r, r.int(8, 30), 0.15)]
      return [randomGraphWire(r, r.int(30, 100), 0.05)]
    })
  },

  validator(input) {
    const [adj] = input as [number[][]]
    if (!Array.isArray(adj)) throw new Error('clone-graph: input must be an array')
    const n = adj.length
    for (let i = 0; i < n; i++) {
      const nbrs = adj[i]
      if (!Array.isArray(nbrs)) throw new Error('clone-graph: adjacency rows must be arrays')
      for (let k = 0; k < nbrs.length; k++) {
        const v = nbrs[k]
        if (!Number.isInteger(v) || v < 1 || v > n) throw new Error('clone-graph: neighbour out of range')
        if (v === i + 1) throw new Error('clone-graph: self-loop not allowed')
        if (k > 0 && nbrs[k - 1] >= v) throw new Error('clone-graph: neighbours must be sorted & unique')
        if (!adj[v - 1].includes(i + 1)) throw new Error('clone-graph: adjacency must be symmetric')
      }
    }
    // Must be canonical (identity round-trip unchanged).
    if (canonical(fromGraph(toGraph(adj))) !== canonical(adj)) throw new Error('clone-graph: non-canonical wire')
  },

  // Oracle: real deep clone via DFS.
  reference(...input) {
    const [adj] = input as [number[][]]
    const src = toGraph(adj)
    if (!src) return []
    const clones = new Map<number, GraphNode>()
    const dfs = (node: GraphNode): GraphNode => {
      const existing = clones.get(node.val)
      if (existing) return existing
      const copy = new GraphNode(node.val)
      clones.set(node.val, copy)
      copy.neighbors = node.neighbors.map(dfs)
      return copy
    }
    return fromGraph(dfs(src))
  },

  // Cross-check: normalize the original graph without cloning.
  brute(...input) {
    const [adj] = input as [number[][]]
    return fromGraph(toGraph(adj))
  },
}

export default cloneGraph
