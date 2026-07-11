/**
 * The weighted case budget from master spec §3. A generator receives this budget and is
 * expected to emit that many cases per bucket. Buckets are metadata for the generator's own
 * loops — the pipeline does not enforce per-bucket counts (generators know their own edge
 * catalog), but the default keeps every problem's suite in the same ~100-case shape.
 */
export interface BucketBudget {
  /** Deterministic per-problem edge catalog: empty, singleton, all-equal, min/max, etc. */
  edge: number
  /** Small inputs a brute solution can cross-check. */
  randomSmall: number
  /** Typical-size correctness. */
  randomMedium: number
  /** Constraint-ceiling adversarial inputs — the TLE detectors (exercised by SP4). */
  maxStress: number
}

/** ~100 cases: 30 / 35 / 20 / 15 (spec §3). */
export const DEFAULT_BUDGET: BucketBudget = {
  edge: 30,
  randomSmall: 35,
  randomMedium: 20,
  maxStress: 15,
}

export function totalCases(b: BucketBudget): number {
  return b.edge + b.randomSmall + b.randomMedium + b.maxStress
}
