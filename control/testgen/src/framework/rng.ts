/**
 * Deterministic seeded PRNG (mulberry32). A fixed seed => a reproducible case stream, which is
 * what makes generated bundles reproducible from git (master spec §6). Never use Math.random in
 * generators.
 */
export class Rng {
  private state: number

  constructor(seed: number) {
    // Force a non-zero 32-bit state.
    this.state = (seed >>> 0) || 0x9e3779b9
  }

  /** Next float in [0, 1). */
  next(): number {
    this.state = (this.state + 0x6d2b79f5) | 0
    let t = Math.imul(this.state ^ (this.state >>> 15), 1 | this.state)
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296
  }

  /** Integer in [min, max] inclusive. */
  int(min: number, max: number): number {
    return min + Math.floor(this.next() * (max - min + 1))
  }

  /** Random element of a non-empty array. */
  pick<T>(arr: readonly T[]): T {
    return arr[this.int(0, arr.length - 1)]
  }

  /** In-place Fisher–Yates shuffle; returns the same array. */
  shuffle<T>(arr: T[]): T[] {
    for (let i = arr.length - 1; i > 0; i--) {
      const j = this.int(0, i)
      ;[arr[i], arr[j]] = [arr[j], arr[i]]
    }
    return arr
  }

  /** `n` integers in [min, max]. */
  ints(n: number, min: number, max: number): number[] {
    const r: number[] = []
    for (let i = 0; i < n; i++) r.push(this.int(min, max))
    return r
  }
}
