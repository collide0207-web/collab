import type { ProblemModule } from './types.js'
import twoSum from '../problems/two-sum.js'
import majorityElement from '../problems/majority-element.js'
import mergeTwoSortedLists from '../problems/merge-two-sorted-lists.js'
import invertBinaryTree from '../problems/invert-binary-tree.js'
import cloneGraph from '../problems/clone-graph.js'
import minStack from '../problems/min-stack.js'
import powxN from '../problems/powx-n.js'

/**
 * The SP3 pilot set. Spans every checker (exact/unordered/float) and every wire type
 * (scalar/array/list-node/tree-node/graph-node/operations). Adding a problem = author a module
 * and register it here; the pipeline and registry scale to all 149 the same way.
 */
export const PILOT: ProblemModule[] = [
  twoSum,
  majorityElement,
  mergeTwoSortedLists,
  invertBinaryTree,
  cloneGraph,
  minStack,
  powxN,
]
