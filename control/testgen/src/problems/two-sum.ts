import { DEFAULT_BUDGET } from '../framework/buckets.js'
import { fillBuckets } from '../framework/gen.js'
import type { ProblemModule } from '../framework/types.js'
import type { Rng } from '../framework/rng.js'

/**
 * two-sum: nums:int[], target:int -> [i,j] with nums[i]+nums[j]==target, i!=j.
 * LeetCode guarantees exactly one solution — the generator enforces uniqueness so the
 * `unordered` checker (index multiset) has a single correct answer to compare against.
 */

function countSolutions(nums: number[], target: number): number {
  let c = 0
  for (let i = 0; i < nums.length; i++)
    for (let j = i + 1; j < nums.length; j++) if (nums[i] + nums[j] === target) c++
  return c
}

/** Build an array of `n` values in [lo,hi] with exactly one pair summing to a chosen target. */
function uniquePairInput(rng: Rng, n: number, lo: number, hi: number): unknown[] {
  for (let attempt = 0; attempt < 200; attempt++) {
    const nums = rng.ints(n, lo, hi)
    const i = rng.int(0, n - 1)
    let j = rng.int(0, n - 1)
    while (j === i) j = rng.int(0, n - 1)
    const target = nums[i] + nums[j]
    if (countSolutions(nums, target) === 1) return [nums, target]
  }
  // Fallback that is guaranteed unique: two anchors far outside the filler range.
  const nums = rng.ints(n - 2, lo / 2, hi / 2)
  nums.push(hi, hi - 1)
  return [nums, 2 * hi - 1]
}

const twoSum: ProblemModule = {
  meta: {
    slug: 'two-sum',
    version: 1,
    rngSeed: 20260711,
    budget: DEFAULT_BUDGET,
    timeLimitMs: 2000,
    checker: 'unordered',
  },

  generator(rng, budget) {
    return fillBuckets(rng, budget, (kind, i, r) => {
      if (kind === 'edge') {
        const catalog: unknown[][] = [
          [[2, 7, 11, 15], 9],
          [[3, 2, 4], 6],
          [[3, 3], 6],
          [[-1, -2, -3, -4], -7],
          [[0, 4, 3, 0], 0],
          [[1000000000, -1000000000, 5], 0],
          [[-5, 5], 0],
          [[1, 2], 3],
        ]
        if (i < catalog.length) return catalog[i]
        return uniquePairInput(r, r.int(2, 6), -20, 20)
      }
      if (kind === 'randomSmall') return uniquePairInput(r, r.int(2, 20), -100, 100)
      if (kind === 'randomMedium') return uniquePairInput(r, r.int(20, 120), -100000, 100000)
      return uniquePairInput(r, r.int(200, 500), -1000000000, 1000000000)
    })
  },

  validator(input) {
    const [nums, target] = input as [number[], number]
    if (!Array.isArray(nums) || nums.length < 2) throw new Error('two-sum: n>=2 required')
    for (const x of nums) if (!Number.isInteger(x) || Math.abs(x) > 1e9) throw new Error('two-sum: value out of range')
    if (!Number.isInteger(target)) throw new Error('two-sum: target must be int')
    if (countSolutions(nums, target) !== 1) throw new Error('two-sum: input must have exactly one solution')
  },

  // Oracle: one-pass hash map.
  reference(...input) {
    const [nums, target] = input as [number[], number]
    const seen = new Map<number, number>()
    for (let i = 0; i < nums.length; i++) {
      const need = target - nums[i]
      if (seen.has(need)) return [seen.get(need), i]
      seen.set(nums[i], i)
    }
    throw new Error('two-sum: no solution (generator bug)')
  },

  // Cross-check: obvious O(n^2).
  brute(...input) {
    const [nums, target] = input as [number[], number]
    for (let i = 0; i < nums.length; i++)
      for (let j = i + 1; j < nums.length; j++) if (nums[i] + nums[j] === target) return [i, j]
    throw new Error('two-sum: no solution (generator bug)')
  },
}

export default twoSum
