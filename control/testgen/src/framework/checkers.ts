/**
 * Checkers decide whether a produced output matches the expected one. Used by the pipeline to
 * cross-check `brute` against `reference` (the oracle proof, spec §6). The same checker *spec*
 * strings ("exact" | "unordered" | "float:<eps>") are what SP4's server judge will apply per
 * case, so the semantics defined here are the contract.
 */

export type Checker = (expected: unknown, actual: unknown) => boolean

/** JSON.stringify with sorted object keys — the canonical, no-space form the whole system uses. */
export function canonical(v: unknown): string {
  return JSON.stringify(v, (_k, val) =>
    val && typeof val === 'object' && !Array.isArray(val)
      ? Object.fromEntries(Object.keys(val as object).sort().map((k) => [k, (val as Record<string, unknown>)[k]]))
      : val,
  )
}

const exact: Checker = (e, a) => canonical(e) === canonical(a)

/** Deep multiset equality: arrays compare irrespective of order, recursively. */
const unordered: Checker = (e, a) => {
  const eq = (x: unknown, y: unknown): boolean => {
    if (Array.isArray(x) && Array.isArray(y)) {
      if (x.length !== y.length) return false
      const keyed = (arr: unknown[]) => arr.map(sortedKey).sort()
      const kx = keyed(x)
      const ky = keyed(y)
      return kx.every((k, i) => k === ky[i])
    }
    return canonical(x) === canonical(y)
  }
  // A canonical form that is itself order-insensitive for nested arrays.
  const sortedKey = (v: unknown): string => {
    if (Array.isArray(v)) return '[' + v.map(sortedKey).sort().join(',') + ']'
    return canonical(v)
  }
  return eq(e, a)
}

/** Elementwise numeric tolerance; structure (array shape) must still match in order. */
function floatChecker(eps: number): Checker {
  const eq = (x: unknown, y: unknown): boolean => {
    if (typeof x === 'number' && typeof y === 'number') return Math.abs(x - y) <= eps
    if (Array.isArray(x) && Array.isArray(y)) {
      return x.length === y.length && x.every((v, i) => eq(v, y[i]))
    }
    return canonical(x) === canonical(y)
  }
  return eq
}

/** Resolve a checker spec string to a Checker. Unknown/`custom:` specs throw (SP3 has no custom). */
export function makeChecker(spec: string | undefined): Checker {
  if (!spec || spec === 'exact') return exact
  if (spec === 'unordered') return unordered
  const m = /^float:(.+)$/.exec(spec)
  if (m) {
    const eps = Number(m[1])
    if (!Number.isFinite(eps)) throw new Error(`bad float eps: ${spec}`)
    return floatChecker(eps)
  }
  throw new Error(`unsupported checker spec for SP3 pilot: ${spec}`)
}
