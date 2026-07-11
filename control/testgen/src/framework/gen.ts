import type { Rng } from './rng.js'
import type { BucketBudget } from './buckets.js'

export type BucketKind = 'edge' | 'randomSmall' | 'randomMedium' | 'maxStress'

/**
 * Fill a full case list from a per-case `sampler`, calling it the budgeted number of times for
 * each bucket, in order edge -> randomSmall -> randomMedium -> maxStress. The sampler decides the
 * concrete input given the bucket kind and an index within that bucket (so it can emit a fixed
 * edge catalog for low indices and randomize the rest). Returns exactly totalCases(budget) inputs.
 */
export function fillBuckets(
  rng: Rng,
  budget: BucketBudget,
  sampler: (kind: BucketKind, indexInBucket: number, rng: Rng) => unknown[],
): unknown[][] {
  const out: unknown[][] = []
  const order: [BucketKind, number][] = [
    ['edge', budget.edge],
    ['randomSmall', budget.randomSmall],
    ['randomMedium', budget.randomMedium],
    ['maxStress', budget.maxStress],
  ]
  for (const [kind, n] of order) {
    for (let i = 0; i < n; i++) out.push(sampler(kind, i, rng))
  }
  return out
}
