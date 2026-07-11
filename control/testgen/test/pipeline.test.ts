import { describe, expect, it } from 'vitest'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { dirname, resolve } from 'node:path'
import { gunzipSync } from 'node:zlib'
import { PILOT } from '../src/framework/registry.js'
import { buildBundle } from '../src/framework/bundle.js'
import { makeChecker } from '../src/framework/checkers.js'
import { totalCases } from '../src/framework/buckets.js'
import type { Case } from '../src/framework/types.js'

const HERE = dirname(fileURLToPath(import.meta.url))
const SEED = resolve(HERE, '../../src/main/resources/seed/leetcode150.json')
const seed: { slug: string; harness?: { tests?: { input: unknown[]; expected: unknown }[] } }[] = JSON.parse(
  readFileSync(SEED, 'utf8'),
)

describe('pipeline: every pilot bundle builds cleanly', () => {
  for (const mod of PILOT) {
    it(`${mod.meta.slug}: generate + validate + oracle cross-check`, () => {
      // buildBundle throws on any validator failure or brute/reference disagreement.
      const built = buildBundle(mod)
      expect(built.entry.caseCount).toBe(totalCases(mod.meta.budget))
      expect(built.entry.checksum).toMatch(/^[0-9a-f]{64}$/)
      // gz decompresses back to the exact canonical JSON.
      expect(gunzipSync(built.gz).toString()).toBe(built.json)
    })
  }
})

describe('pipeline: deterministic (same checksum on re-run)', () => {
  for (const mod of PILOT) {
    it(`${mod.meta.slug} is reproducible`, () => {
      expect(buildBundle(mod).entry.checksum).toBe(buildBundle(mod).entry.checksum)
    })
  }
})

describe('golden: reference reproduces the seed sample cases', () => {
  for (const mod of PILOT) {
    const problem = seed.find((p) => p.slug === mod.meta.slug)
    const samples = problem?.harness?.tests ?? []
    it(`${mod.meta.slug} matches its ${samples.length} seed sample(s)`, () => {
      expect(samples.length).toBeGreaterThan(0)
      const check = makeChecker(mod.meta.checker)
      for (const s of samples) {
        const got = mod.reference(...s.input)
        expect(check(s.expected, got), `input=${JSON.stringify(s.input)} expected=${JSON.stringify(s.expected)} got=${JSON.stringify(got)}`).toBe(true)
      }
    })
  }
})

describe('bundle cases are self-consistent under their checker', () => {
  for (const mod of PILOT) {
    it(`${mod.meta.slug}: every case's expected == reference(input)`, () => {
      const built = buildBundle(mod)
      const check = makeChecker(mod.meta.checker)
      for (const c of built.cases as Case[]) {
        expect(check(c.expected, mod.reference(...c.input))).toBe(true)
      }
    })
  }
})
