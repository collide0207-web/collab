# Collide `collab` — Real-Time Collaboration Backend (Node/TypeScript)

The authoritative real-time sync server behind Collide's editor and notes canvas.
Built on **Yjs (CRDT)** for guaranteed convergence, wrapped with production concerns:
auth, persistence, horizontal scale, presence, offline tolerance, and recovery.

> **Why Node, not Java?** The convergence engine is Yjs, a JavaScript CRDT library the
> Collide frontend already uses. The sync server must speak the same binary protocol
> and manipulate the same document structures, so it is Node by necessity. The Java /
> Spring Boot **Control Plane** (auth, rooms, roles, invites) is a separate service;
> the two share a JWT and Redis. See `docs/ARCHITECTURE.md`.

## Design

Full design — algorithm, message formats, state management, conflict resolution,
edge cases, testing, performance, security, and the rationale for every major
decision — is in **[`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md)**.

Headline: **CRDT (Yjs), not OT, and never last-write-wins.** CRDT updates are
commutative + idempotent, which is *why* out-of-order delivery, duplicates, delayed
packets, and concurrent edits all converge without special-casing.

## Run (local, no infra required)

```bash
cp .env.example .env      # DEV_ALLOW_ANON=true lets you connect without the Control Plane
npm install
npm run dev               # ws://localhost:4000/doc/<docId>?token=<jwt>
```

With no `DATABASE_URL`/`REDIS_URL` it falls back to an in-memory store and in-process
pub/sub (single node, non-durable) — fine for local dev. Set both for production
(Postgres durability + multi-node fan-out).

```bash
npm test                  # CRDT convergence + idempotency + ordering + undo tests
npm run typecheck
```

## What's implemented (build-order steps 1–4 core)

- **Sync core** — `Y.Doc` per file, Yjs sync + awareness protocol over WebSocket.
- **Convergence** — proven by the test suite (concurrent typing, overlapping delete,
  insert-during-delete, out-of-order, duplicates, Unicode/emoji, per-user undo).
- **Auth** — JWT verification (HS256/RS256), impersonation-safe (`userId` from token).
- **RBAC** — role gate; **viewers are read-only enforced on the server** (their edits
  are dropped, not just hidden).
- **Persistence** — Postgres snapshot + durable update tail (in-memory fallback).
- **Horizontal scale** — Redis pub/sub fan-out between nodes (in-process fallback).
- **Presence** — awareness (cursors/selection/name/typing), cross-node.
- **Lifecycle** — doc load/evict, heartbeats, graceful shutdown.

## Remaining (next steps)

- **Client integration** — swap frontend `y-webrtc` → `y-websocket` provider + add
  `y-indexeddb` (offline) + per-origin `Y.UndoManager`.
- **File-doc model** — per-file sub-docs + shared file-tree; rename/delete/restore.
- **HttpRoleResolver** — call the Spring Boot Control Plane for real membership/roles.
- **Hardening** — rate limits, backpressure high-water marks, snapshot compaction,
  metrics/telemetry, and the integration/fault-injection/soak/load test suites.

## Layout

```
docs/ARCHITECTURE.md     full design & decision rationale
src/config.ts            env config
src/auth/jwt.ts          JWT verification → Identity
src/rbac.ts              roles + read-only gate
src/persistence/         DocStore interface, Memory + Postgres
src/scaling/pubsub.ts    PubSub interface, InProcess + Redis
src/sync/sharedDoc.ts    per-document Yjs state, connections, fan-out, persistence
src/sync/docManager.ts   doc lifecycle + eviction + pub/sub routing
src/sync/wsConn.ts       WebSocket connection adapter
src/server.ts            HTTP health + WS upgrade + auth/RBAC wiring
test/convergence.test.ts CRDT convergence guarantees
```
