import { describe, expect, it } from 'vitest'
import { DEFAULT_BUDGET, totalCases } from '../src/framework/buckets.js'
import { fillBuckets } from '../src/framework/gen.js'
import { Rng } from '../src/framework/rng.js'

describe('buckets', () => {
  it('default budget totals ~100', () => {
    expect(totalCases(DEFAULT_BUDGET)).toBe(100)
  })
  it('fillBuckets emits exactly the budgeted count, in bucket order', () => {
    const seen: string[] = []
    const cases = fillBuckets(new Rng(1), DEFAULT_BUDGET, (kind) => {
      seen.push(kind)
      return [kind]
    })
    expect(cases.length).toBe(100)
    expect(seen.slice(0, DEFAULT_BUDGET.edge).every((k) => k === 'edge')).toBe(true)
    expect(seen[seen.length - 1]).toBe('maxStress')
  })
})

describe('rng determinism', () => {
  it('same seed => same stream', () => {
    const a = new Rng(42).ints(5, 0, 1000)
    const b = new Rng(42).ints(5, 0, 1000)
    expect(a).toEqual(b)
  })
  it('different seed => different stream', () => {
    expect(new Rng(1).ints(5, 0, 1e9)).not.toEqual(new Rng(2).ints(5, 0, 1e9))
  })
})
