import { createHash } from 'node:crypto'
import { gzipSync } from 'node:zlib'
import { mkdirSync, readFileSync, writeFileSync, existsSync } from 'node:fs'
import { dirname, join } from 'node:path'
import type { Case, ManifestEntry, ProblemModule } from './types.js'
import { makeChecker } from './checkers.js'
import { Rng } from './rng.js'
import { totalCases } from './buckets.js'

/**
 * Storage abstraction for bundle artifacts. Mirrors the control plane's DocStore/PubSub
 * swappable pattern. `LocalBundleStore` is the zero-infra default; an S3 impl swaps in later.
 * The write side lives here (Node pipeline); the Java side has a mirror read-only interface.
 */
export interface BundleStore {
  write(key: string, bytes: Uint8Array): void
  read(key: string): Uint8Array
  exists(key: string): boolean
}

export class LocalBundleStore implements BundleStore {
  constructor(private readonly root: string) {}
  private path(key: string): string {
    return join(this.root, key)
  }
  write(key: string, bytes: Uint8Array): void {
    const p = this.path(key)
    mkdirSync(dirname(p), { recursive: true })
    writeFileSync(p, bytes)
  }
  read(key: string): Uint8Array {
    return readFileSync(this.path(key))
  }
  exists(key: string): boolean {
    return existsSync(this.path(key))
  }
}

export interface BuiltBundle {
  entry: ManifestEntry
  cases: Case[]
  /** Uncompressed canonical JSON bytes (checksum is over these). */
  json: string
  gz: Uint8Array
}

/**
 * Run the full pipeline for one problem module (spec §6):
 *   generate -> validate each -> cross-check brute==reference on the (edge+small) prefix
 *   -> reference on all -> assemble cases -> canonical JSON -> gzip -> sha256.
 * Throws on any validator failure or brute/reference disagreement — those are authoring bugs.
 */
export function buildBundle(mod: ProblemModule): BuiltBundle {
  const { meta } = mod
  const rng = new Rng(meta.rngSeed)
  const inputs = mod.generator(rng, meta.budget)

  const expectedTotal = totalCases(meta.budget)
  if (inputs.length !== expectedTotal) {
    throw new Error(`${meta.slug}: generator emitted ${inputs.length} cases, budget wants ${expectedTotal}`)
  }

  for (const input of inputs) mod.validator(input)

  // Cross-check the oracle on the deterministic edge + random-small prefix.
  if (mod.brute) {
    const check = makeChecker(meta.checker)
    const crossN = meta.budget.edge + meta.budget.randomSmall
    for (let i = 0; i < crossN; i++) {
      const input = inputs[i]
      const ref = mod.reference(...input)
      const bru = mod.brute(...input)
      if (!check(ref, bru)) {
        throw new Error(
          `${meta.slug}: oracle mismatch at case ${i}\n  input=${JSON.stringify(input)}\n  reference=${JSON.stringify(ref)}\n  brute=${JSON.stringify(bru)}`,
        )
      }
    }
  }

  const cases: Case[] = inputs.map((input) => ({ input, expected: mod.reference(...input) }))

  const json = JSON.stringify(cases)
  const checksum = createHash('sha256').update(json).digest('hex')
  const gz = gzipSync(Buffer.from(json), { level: 9 })
  const storageKey = `${meta.slug}.v${meta.version}.json.gz`

  return {
    entry: {
      problemSlug: meta.slug,
      version: meta.version,
      checksum,
      caseCount: cases.length,
      storageKey,
      timeLimitMs: meta.timeLimitMs,
      checkerType: meta.checker,
    },
    cases,
    json,
    gz,
  }
}
