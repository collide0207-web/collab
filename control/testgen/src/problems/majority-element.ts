import { DEFAULT_BUDGET } from '../framework/buckets.js'
import { fillBuckets } from '../framework/gen.js'
import type { ProblemModule } from '../framework/types.js'
import type { Rng } from '../framework/rng.js'

/**
 * majority-element: nums:int[] -> the element appearing > n/2 times (guaranteed to exist).
 * Generator constructs arrays with a guaranteed strict majority so reference == brute.
 */

/** Array of length n where `maj` occupies > n/2 positions; rest are random non-maj fillers. */
function withMajority(rng: Rng, n: number, lo: number, hi: number): unknown[] {
  const maj = rng.int(lo, hi)
  const majCount = Math.floor(n / 2) + 1
  const arr: number[] = new Array(majCount).fill(maj)
  while (arr.length < n) {
    let x = rng.int(lo, hi)
    if (x === maj) x = maj + 1 // ensure filler differs from majority
    arr.push(x)
  }
  return [rng.shuffle(arr)]
}

function count(nums: number[], v: number): number {
  return nums.reduce((c, x) => c + (x === v ? 1 : 0), 0)
}

const majorityElement: ProblemModule = {
  meta: {
    slug: 'majority-element',
    version: 1,
    rngSeed: 20260712,
    budget: DEFAULT_BUDGET,
    timeLimitMs: 2000,
    checker: 'exact',
  },

  generator(rng, budget) {
    return fillBuckets(rng, budget, (kind, i, r) => {
      if (kind === 'edge') {
        const catalog: unknown[][] = [
          [[3, 2, 3]],
          [[2, 2, 1, 1, 1, 2, 2]],
          [[1]],
          [[7, 7]],
          [[-1, -1, -1, 2, 3]],
          [[1000000000, 1000000000, 1]],
          [[-1000000000, -1000000000, -1000000000, 6]],
        ]
        if (i < catalog.length) return catalog[i]
        return withMajority(r, 2 * r.int(1, 4) + 1, -10, 10)
      }
      if (kind === 'randomSmall') return withMajority(r, r.int(1, 21), -50, 50)
      if (kind === 'randomMedium') return withMajority(r, r.int(21, 201), -100000, 100000)
      return withMajority(r, r.int(1001, 5001), -1000000000, 1000000000)
    })
  },

  validator(input) {
    const [nums] = input as [number[]]
    if (!Array.isArray(nums) || nums.length < 1) throw new Error('majority: n>=1 required')
    for (const x of nums) if (!Number.isInteger(x) || Math.abs(x) > 1e9) throw new Error('majority: value out of range')
    const uniq = new Set(nums)
    let hasMaj = false
    for (const v of uniq) if (count(nums, v) > nums.length / 2) hasMaj = true
    if (!hasMaj) throw new Error('majority: no strict majority (generator bug)')
  },

  // Oracle: Boyer–Moore voting.
  reference(...input) {
    const [nums] = input as [number[]]
    let candidate = 0
    let votes = 0
    for (const x of nums) {
      if (votes === 0) candidate = x
      votes += x === candidate ? 1 : -1
    }
    return candidate
  },

  // Cross-check: sort and take the middle (majority always covers the median).
  brute(...input) {
    const [nums] = input as [number[]]
    const s = [...nums].sort((a, b) => a - b)
    return s[Math.floor(s.length / 2)]
  },
}

export default majorityElement
