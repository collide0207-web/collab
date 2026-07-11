/**
 * Wire (de)serializers for object node types, mirroring SP2's Run-codegen preludes in
 * `collide/src/run/harness.ts` exactly. These turn a canonical JSON wire value (what a test
 * case's `input`/`expected` holds) into a native structure a reference solution operates on,
 * and back. Keeping these byte-for-byte compatible with SP2 is what guarantees a bundle's
 * `expected` equals what a language driver will print.
 *
 * Canonical output is plain JSON.stringify (no spaces) — Node emits that natively.
 */

// --- singly linked list: wire form [1,2,3] <-> ListNode ------------------------------------

export class ListNode {
  val: number
  next: ListNode | null = null
  constructor(val: number) {
    this.val = val
  }
}

/** [1,2,3] -> ListNode chain (empty [] -> null). Mirrors __toList. */
export function toList(a: number[]): ListNode | null {
  const dummy = new ListNode(0)
  let c = dummy
  for (const x of a) {
    c.next = new ListNode(x)
    c = c.next
  }
  return dummy.next
}

/** ListNode chain -> [1,2,3]. Mirrors __fromList. */
export function fromList(n: ListNode | null): number[] {
  const r: number[] = []
  while (n) {
    r.push(n.val)
    n = n.next
  }
  return r
}

// --- binary tree: LeetCode level-order-with-nulls [1,null,2,3] <-> TreeNode -----------------

export class TreeNode {
  val: number
  left: TreeNode | null = null
  right: TreeNode | null = null
  constructor(val: number) {
    this.val = val
  }
}

/** Level-order array (nulls for absent) -> TreeNode (empty/[null] -> null). Mirrors __toTree. */
export function toTree(a: (number | null)[]): TreeNode | null {
  if (!a.length || a[0] == null) return null
  const root = new TreeNode(a[0])
  const q: TreeNode[] = [root]
  let i = 1
  while (q.length && i < a.length) {
    const n = q.shift()!
    if (i < a.length) {
      const v = a[i++]
      if (v != null) {
        n.left = new TreeNode(v)
        q.push(n.left)
      }
    }
    if (i < a.length) {
      const v = a[i++]
      if (v != null) {
        n.right = new TreeNode(v)
        q.push(n.right)
      }
    }
  }
  return root
}

/** TreeNode -> level-order array with trailing nulls trimmed. Mirrors __fromTree. */
export function fromTree(root: TreeNode | null): (number | null)[] {
  const out: (number | null)[] = []
  const q: (TreeNode | null)[] = [root]
  while (q.length) {
    const n = q.shift()!
    if (n) {
      out.push(n.val)
      q.push(n.left, n.right)
    } else {
      out.push(null)
    }
  }
  while (out.length && out[out.length - 1] == null) out.pop()
  return out
}

// --- graph: Clone-Graph adjacency [[2,4],[1,3],...] <-> Node (1-indexed values) -------------

export class GraphNode {
  val: number
  neighbors: GraphNode[] = []
  constructor(val: number) {
    this.val = val
  }
}

/** Adjacency list (entry i = neighbour values of node i+1) -> Node graph. Mirrors __toGraph. */
export function toGraph(adj: number[][]): GraphNode | null {
  if (!adj.length) return null
  const nodes = adj.map((_, i) => new GraphNode(i + 1))
  adj.forEach((nb, i) => {
    nodes[i].neighbors = nb.map((v) => nodes[v - 1])
  })
  return nodes[0]
}

/** Node graph -> adjacency list, BFS from the given node, neighbours sorted. Mirrors __fromGraph. */
export function fromGraph(node: GraphNode | null): number[][] {
  if (!node) return []
  const seen = new Map<number, GraphNode>()
  const q: GraphNode[] = [node]
  seen.set(node.val, node)
  while (q.length) {
    const n = q.shift()!
    for (const m of n.neighbors) {
      if (!seen.has(m.val)) {
        seen.set(m.val, m)
        q.push(m)
      }
    }
  }
  const vals = [...seen.keys()].sort((a, b) => a - b)
  return vals.map((v) => seen.get(v)!.neighbors.map((m) => m.val).sort((a, b) => a - b))
}
