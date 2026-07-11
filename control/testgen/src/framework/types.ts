import type { Rng } from './rng.js'
import type { BucketBudget } from './buckets.js'

/** One generated test case: `input` in param order, `expected` return value — canonical JSON. */
export interface Case {
  input: unknown[]
  expected: unknown
}

/** Per-problem authoring metadata. */
export interface Meta {
  slug: string
  /** Bundle version — bump when generator/reference changes; checksum self-invalidates caches. */
  version: number
  /** Fixed RNG seed => reproducible case stream. */
  rngSeed: number
  /** Case budget (defaults to DEFAULT_BUDGET). */
  budget: BucketBudget
  /** Per-case wall-clock limit (ms) recorded in the registry for SP4. */
  timeLimitMs: number
  /** Checker spec: "exact" | "unordered" | "float:<eps>". */
  checker: string
}

/**
 * A problem's authoring module. `reference` is the oracle. `brute`, when present, cross-checks
 * the oracle on the small bucket. `generator` emits candidate inputs; `validator` guards them.
 * Inputs/outputs are the *native* forms — the pipeline handles wire (de)serialization so that
 * generated cases hold canonical JSON.
 */
export interface ProblemModule {
  meta: Meta
  generator(rng: Rng, budget: BucketBudget): unknown[][]
  validator(input: unknown[]): void
  reference(...input: unknown[]): unknown
  brute?(...input: unknown[]): unknown
}

/** One row of the manifest the Java seeder reads to register a bundle. */
export interface ManifestEntry {
  problemSlug: string
  version: number
  checksum: string
  caseCount: number
  storageKey: string
  timeLimitMs: number
  checkerType: string
}

export interface Manifest {
  generatedAt: string
  bundles: ManifestEntry[]
}
