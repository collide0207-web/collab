# testgen — SP3 test-data pipeline

Generates the reproducible **hidden test suites** the SP4 judge will consume. For each registered
problem it runs `generator → validator → (brute vs reference cross-check) → reference → bundle`,
emitting a gzipped `{input, expected}[]` artifact plus a `manifest.json` the control plane's
`TestBundleSeeder` registers on boot.

See `../../../collide/docs/superpowers/specs/2026-07-11-sp3-test-data-pipeline-design.md` for the
design. Node/TypeScript is used instead of the master spec's Python because the canonical output
format is `JSON.stringify` (which Node emits natively) and Python is not available in the target
environment — the wire/canonical contract is unchanged.

## Commands

```bash
npm install
npm test              # framework units + per-problem golden + determinism (Vitest)
npm run typecheck     # tsc --noEmit
npm run build-bundles # writes <slug>.v<n>.json.gz + manifest.json into
                      #   ../src/main/resources/seed/test-bundles/  (committed, source of truth)
```

`build-bundles` is deterministic: seeded generators mean a re-run produces byte-identical
artifacts and checksums. Regenerate and commit after changing any problem module, and bump that
problem's `meta.version` so the checksum self-invalidates stale judge caches.

## Layout

- `src/framework/` — `wire` (list/tree/graph (de)serializers matching SP2), `rng` (seeded),
  `buckets` (weighted budget), `checkers` (exact/unordered/float), `bundle` (assemble+gzip+sha256
  + `BundleStore`), `gen`, `registry`.
- `src/problems/<slug>.ts` — one authoring module per problem (`meta`, `generator`, `validator`,
  `reference`, optional `brute`). Add a module and register it in `framework/registry.ts` to scale
  to all 149.
- `src/cli/build-bundles.ts` — pipeline entrypoint.

## Pilot set

`two-sum` (unordered) · `majority-element` · `merge-two-sorted-lists` (list-node) ·
`invert-binary-tree` (tree-node) · `clone-graph` (graph-node) · `min-stack` (operations) ·
`powx-n` (float) — spanning every checker and wire type.
