import { describe, expect, it } from 'vitest'
import { makeChecker, canonical } from '../src/framework/checkers.js'

describe('exact checker', () => {
  const c = makeChecker('exact')
  it('canonical key-order independence', () => {
    expect(canonical({ b: 1, a: 2 })).toBe(canonical({ a: 2, b: 1 }))
  })
  it('order-sensitive for arrays', () => {
    expect(c([0, 1], [0, 1])).toBe(true)
    expect(c([0, 1], [1, 0])).toBe(false)
  })
})

describe('unordered checker', () => {
  const c = makeChecker('unordered')
  it('index pair order does not matter', () => {
    expect(c([0, 1], [1, 0])).toBe(true)
    expect(c([0, 1], [0, 2])).toBe(false)
  })
  it('nested group-anagram style (inner + outer order-insensitive)', () => {
    const a = [['eat', 'tea'], ['bat']]
    const b = [['bat'], ['tea', 'eat']]
    expect(c(a, b)).toBe(true)
  })
})

describe('float checker', () => {
  const c = makeChecker('float:1e-5')
  it('within and outside tolerance', () => {
    expect(c(1.0, 1.000001)).toBe(true)
    expect(c(1.0, 1.001)).toBe(false)
  })
  it('elementwise on arrays', () => {
    expect(c([1.0, 2.0], [1.000001, 1.999999])).toBe(true)
  })
})

describe('makeChecker', () => {
  it('rejects unsupported specs', () => {
    expect(() => makeChecker('custom:foo')).toThrow()
  })
})
