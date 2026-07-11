import { fileURLToPath } from 'node:url'
import { dirname, resolve } from 'node:path'
import { writeFileSync, mkdirSync } from 'node:fs'
import { PILOT } from '../framework/registry.js'
import { buildBundle, LocalBundleStore } from '../framework/bundle.js'
import type { Manifest, ProblemModule } from '../framework/types.js'

/**
 * SP3 pipeline entrypoint. Builds a gzipped bundle per registered problem into the committed
 * test-bundles resource dir and writes manifest.json alongside. Deterministic: re-running yields
 * identical checksums (seeded generators). The Java TestBundleSeeder reads manifest.json on boot.
 *
 * Output dir resolves to control/src/main/resources/seed/test-bundles (override with arg 1).
 */
const HERE = dirname(fileURLToPath(import.meta.url))
const DEFAULT_OUT = resolve(HERE, '../../..', 'src/main/resources/seed/test-bundles')

export function run(outDir: string, modules: ProblemModule[]): Manifest {
  mkdirSync(outDir, { recursive: true })
  const store = new LocalBundleStore(outDir)
  const manifest: Manifest = { generatedAt: new Date(0).toISOString(), bundles: [] }

  for (const mod of modules) {
    const built = buildBundle(mod)
    store.write(built.entry.storageKey, built.gz)
    manifest.bundles.push(built.entry)
    console.log(`  ${built.entry.problemSlug.padEnd(28)} ${built.entry.caseCount} cases  ${built.entry.checkerType.padEnd(11)} sha256:${built.entry.checksum.slice(0, 12)}…`)
  }

  // Stable manifest: sorted by slug, fixed generatedAt (reproducible artifact, no wall-clock noise).
  manifest.bundles.sort((a, b) => a.problemSlug.localeCompare(b.problemSlug))
  writeFileSync(resolve(outDir, 'manifest.json'), JSON.stringify(manifest, null, 2) + '\n')
  return manifest
}

// Executed directly (not imported by tests).
if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const out = process.argv[2] ? resolve(process.argv[2]) : DEFAULT_OUT
  console.log(`Building ${PILOT.length} bundles -> ${out}`)
  const m = run(out, PILOT)
  console.log(`Wrote ${m.bundles.length} bundles + manifest.json`)
}
