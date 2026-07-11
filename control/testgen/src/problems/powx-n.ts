import { DEFAULT_BUDGET } from '../framework/buckets.js'
import { fillBuckets } from '../framework/gen.js'
import type { ProblemModule } from '../framework/types.js'
import type { Rng } from '../framework/rng.js'

/**
 * powx-n: x:double, n:int -> x^n (double), checker float:1e-5.
 * Magnitudes are bounded (|x|<=2, |n|<=13) so the absolute 1e-5 tolerance always holds between
 * the two independent methods (fast exponentiation vs Math.pow).
 */

/** Random double in [lo,hi] rounded to 5 decimals (keeps the wire value tidy & finite). */
function randDouble(rng: Rng, lo: number, hi: number): number {
  return Math.round((lo + rng.next() * (hi - lo)) * 1e5) / 1e5
}

const powxN: ProblemModule = {
  meta: {
    slug: 'powx-n',
    version: 1,
    rngSeed: 20260717,
    budget: DEFAULT_BUDGET,
    timeLimitMs: 2000,
    checker: 'float:1e-5',
  },

  generator(rng, budget) {
    // Nonzero base when exponent may be negative; small magnitudes to bound the result.
    const nonzeroBase = (r: Rng) => {
      let x = randDouble(r, -2, 2)
      if (Math.abs(x) < 0.25) x = x < 0 ? -0.5 : 0.5
      return x
    }
    return fillBuckets(rng, budget, (kind, i, r) => {
      if (kind === 'edge') {
        const catalog: unknown[][] = [
          [2.0, 10],
          [2.1, 3],
          [2.0, -2],
          [1.0, 2147483647],
          [-1.0, 13],
          [5.0, 0],
          [0.0, 5],
          [1.5, -3],
        ]
        if (i < catalog.length) return catalog[i]
        return [nonzeroBase(r), r.int(-13, 13)]
      }
      if (kind === 'randomSmall') return [nonzeroBase(r), r.int(-13, 13)]
      if (kind === 'randomMedium') return [nonzeroBase(r), r.int(-10, 10)]
      return [nonzeroBase(r), r.int(-13, 13)]
    })
  },

  validator(input) {
    const [x, n] = input as [number, number]
    if (typeof x !== 'number' || !Number.isFinite(x)) throw new Error('powx-n: x must be finite')
    if (!Number.isInteger(n)) throw new Error('powx-n: n must be int')
    if (x === 0 && n < 0) throw new Error('powx-n: 0 to a negative power is undefined')
    const r = Math.pow(x, n)
    if (!Number.isFinite(r) || Math.abs(r) > 1e7) throw new Error('powx-n: result out of bounded range')
  },

  // Oracle: iterative fast exponentiation.
  reference(...input) {
    const [x, n] = input as [number, number]
    let base = x
    let exp = n
    if (exp < 0) {
      base = 1 / base
      exp = -exp
    }
    let result = 1
    while (exp > 0) {
      if (exp & 1) result *= base
      base *= base
      exp = Math.floor(exp / 2)
    }
    return result
  },

  // Cross-check: library pow.
  brute(...input) {
    const [x, n] = input as [number, number]
    return Math.pow(x, n)
  },
}

export default powxN
