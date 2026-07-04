# Collide `collab` — Real-Time Collaboration Backend

**Production design for concurrent collaborative editing at scale.**

This service is the authoritative real-time layer behind Collide's code editor and
notes canvas. It is designed for thousands of concurrent users, guaranteed
convergence, offline tolerance, and recovery from every class of failure listed in
the requirements.

---

## 0. The single most important decision: CRDT (Yjs), not hand-rolled OT

> **Decision:** Use a **CRDT** — specifically **Yjs** (YATA algorithm) — as the
> convergence core. Do **not** implement Operational Transformation, and do **not**
> use last-write-wins.

**Why CRDT over OT.**
- **OT requires a central transformation authority** and a correct transform
  function for *every pair* of operation types. Getting `transform(insert, delete)`,
  `transform(delete, delete)`, replace, and paste correct under arbitrary
  interleaving and out-of-order arrival is the single most bug-prone thing in this
  domain. Production OT systems (Google Docs) took years to harden.
- **CRDTs converge by construction.** Yjs operations are **commutative,
  associative, and idempotent** — applying them in any order, or applying the same
  one twice, yields the identical document. That property *directly* satisfies the
  hardest requirements: out-of-order delivery, duplicate operations, delayed
  packets, and "always converge to a single correct state."
- **Offline is native.** A CRDT client edits locally and merges on reconnect with
  no central replay — exactly the offline requirement.
- **Yjs is the fastest, most battle-tested CRDT implementation**, already used by
  the Collide frontend (`y-monaco`, tldraw binding). Reusing it means the same
  proven convergence engine runs on both ends. This is a *non-negotiable* production
  advantage over rebuilding the algorithm.

**Why not last-write-wins:** LWW silently discards concurrent edits. Explicitly
forbidden, and CRDT makes it unnecessary.

**What we build:** Yjs gives us the convergence *algorithm*. Production readiness —
authorization, persistence, horizontal scale, presence, reconnection, offline sync,
recovery, and observability — is the infrastructure this service adds around it.

---

## 1. Overall architecture & workflow

```
                            ┌──────────────── clients (browsers, multi-tab) ─────────────────┐
                            │  Monaco/tldraw ── Yjs doc ── y-websocket provider ── IndexedDB  │
                            └───────────────────────────────┬────────────────────────────────┘
                                                 wss:// (binary Yjs protocol + app envelope)
                                                            │
                                        ┌───────────────────▼───────────────────┐
                                        │        Load balancer (WS-aware)        │
                                        └───┬───────────────┬───────────────┬────┘
                                            │               │               │
                                   ┌────────▼───┐   ┌────────▼───┐   ┌────────▼───┐   (N stateless
                                   │ collab node│   │ collab node│   │ collab node│    sync nodes)
                                   │  ┌───────┐ │   │            │   │            │
                                   │  │DocMgr │ │   │            │   │            │
                                   │  │ Y.Doc │ │   │            │   │            │
                                   │  └───────┘ │   │            │   │            │
                                   └───┬────┬───┘   └───┬────┬───┘   └───┬────┬───┘
                                       │    │           │    │           │    │
                        Redis pub/sub  │    │ persist   │    │           │    │
                    (cross-node fan-out)◄───┼───────────┼────┼───────────┼────┘
                                            │           │    │
                                   ┌────────▼───────────▼────▼─────────┐
                                   │           PostgreSQL              │
                                   │  document snapshots + update log  │
                                   └───────────────────────────────────┘
```

**Workflow (happy path):**
1. Client authenticates with the Control Plane (separate service), receives a **JWT**.
2. Client opens `wss://collab/doc/:docId?token=…`. The node runs the **auth hook**
   (verify JWT signature) and the **RBAC check** (is this user a member of the room,
   and with what role?).
3. Node loads the doc: from the in-memory `DocManager` if already resident, else
   from **Postgres** (latest snapshot + any newer updates).
4. **Sync handshake** (Yjs sync protocol): client and server exchange *state
   vectors* and send only the **missing updates** in each direction — never the
   whole document.
5. Edits flow as **incremental binary updates**. The node applies each to the shared
   `Y.Doc`, broadcasts to other local connections, and **publishes to Redis** so
   other nodes holding the same doc apply and fan out to their clients.
6. **Awareness** (cursors, selection, name, typing) flows on a separate ephemeral
   channel — never persisted.
7. The node **debounce-persists** snapshots to Postgres and on last-client-leave.
8. On disconnect the provider **queues** edits locally (and in IndexedDB); on
   reconnect the sync handshake replays only what the server is missing.

---

## 2. Synchronization algorithm design

**Core:** Yjs YATA CRDT. Each character/struct has a unique, immutable ID
`(clientID, clock)`; the structure is a doubly-linked list with deterministic
integration order, so all replicas resolve concurrent inserts identically.

**The Yjs sync protocol (two steps + live updates):**
- **Step 1 (SV):** peer A sends its **state vector** — a compact map
  `clientID → highest clock seen`. This says "here's everything I already have."
- **Step 2 (diff):** peer B replies with `Y.encodeStateAsUpdate(doc, remoteSV)` —
  *only the operations A is missing*. A does the same for B. After this exchange
  both sides are equal.
- **Live updates:** thereafter each local change emits a small binary update
  (`doc.on('update')`) that is broadcast and applied remotely.

**How this satisfies each hard case (by CRDT property, not special-casing):**

| Requirement | How Yjs handles it |
|---|---|
| Two users typing at same position | Distinct `(clientID, clock)` IDs; YATA integration orders them deterministically → same result everywhere |
| Overlapping deletes | Deletes are tombstone markers by ID; deleting an already-deleted range is a no-op; commutative |
| Insert while another deletes | Insert has its own ID and is anchored to a left-origin; survives independent of the delete |
| Simultaneous replace | Replace = delete range + insert; both are ID-based and merge deterministically |
| Simultaneous paste (large) | A paste is just a bulk insert of structs; same rules, one update |
| Multiple users on one line | No line-level locking; character-level IDs |
| Rapid consecutive edits | Coalesced into compact updates; still ordered by clock |
| Out-of-order arrival | Updates are **commutative** — any order converges |
| Duplicate operations | Updates are **idempotent** — re-applying is a no-op (struct already integrated) |
| Delayed packets | State vector on reconnect requests exactly the gap; nothing lost |

**Determinism / convergence guarantee:** given the same *set* of updates (order and
multiplicity irrelevant), every replica computes the identical document. This is the
mathematical CRDT convergence property and is the backbone of the whole system.

---

## 3. Operation management (IDs, versioning, idempotency)

Yjs already stamps every struct with `(clientID, clock)` and tracks per-client
clocks in the state vector — this *is* the unique op ID + sequence number +
document-version machinery, at the granularity that actually matters for
convergence. We add an **application-level envelope** for auth, routing, observ-
ability, and the non-CRDT operations (file switching, presence):

```
Envelope {
  v:        protocol version (int)
  type:     SYNC | AWARENESS | AUTH | FILE_OP | PING | ERROR
  docId:    string            // room+file scope
  msgId:    uuid              // app-level idempotency key for FILE_OP/control msgs
  userId:   string            // from verified JWT (server-authoritative, not trusted from client)
  sessionId:string            // per-connection (distinguishes multi-tab)
  seq:      int               // per-connection monotonic (gap detection, metrics)
  ts:       epoch-ms          // observability only, NOT used for conflict resolution
  payload:  bytes | json
}
```

- **Idempotency / apply-once:** for CRDT `SYNC` messages, idempotency is intrinsic
  (re-applying a Yjs update is a no-op). For control messages (`FILE_OP`), the
  server keeps a short-lived **seen-`msgId` set in Redis** (TTL) and drops
  duplicates — covers duplicate reconnect attempts and packet duplication.
- **Out-of-order / delayed:** handled by CRDT for edits; for control messages we act
  on current server state, not on client-assumed ordering.
- **`ts` is never used to resolve conflicts** (that would be LWW). It is metadata for
  logs and metrics only.

---

## 4. Message formats

**Binary edit/awareness frames** (efficient — this is 99% of traffic), following the
Yjs protocol so we stay compatible with the standard `y-websocket` provider:

```
byte 0: messageType   (0 = SYNC, 1 = AWARENESS, 2 = AUTH, 3 = CONTROL, 8 = PING)
SYNC:      [0][syncStep: 0=SV | 1=update | 2=updateReply][varUint8Array payload]
AWARENESS: [1][varUint8Array awarenessUpdate]
CONTROL:   [3][varString JSON]   // file open/switch/rename/delete, errors, acks
```

**Control JSON payloads** (human-readable, low-volume) — e.g.:
```json
{ "op": "OPEN_FILE",   "docId": "room_88/src/index.js", "msgId": "…" }
{ "op": "RENAME_FILE", "from": "…/a.js", "to": "…/b.js", "msgId": "…" }
{ "op": "DELETE_FILE", "docId": "…", "msgId": "…" }
{ "op": "ERROR", "code": "FORBIDDEN|STALE_VERSION|RATE_LIMIT", "detail": "…" }
{ "op": "PRESENCE_JOIN" | "PRESENCE_LEAVE", "userId": "…", "name": "…" }
```

Rationale: binary for high-frequency edit/cursor traffic (compactness = lower
latency and bandwidth at scale); JSON for rare control ops (debuggability).

---

## 5. State management strategy

**Three tiers, each with a clear owner:**

1. **Live state — in memory (per node), authoritative during a session.**
   `DocManager` holds one `Y.Doc` per active `docId`, plus its `Awareness`. All edits
   apply here first. Reference-counted by connections; evicted after idle with a
   final persist.

2. **Durable state — PostgreSQL, the source of truth across restarts.**
   - `documents(doc_id, snapshot BYTEA, state_vector BYTEA, version, updated_at)` —
     the merged Yjs snapshot (`Y.encodeStateAsUpdate`).
   - `document_updates(doc_id, seq, update BYTEA, created_at)` — append-only log of
     recent updates for crash-safe durability between snapshots (optional but
     enabled in prod). Compacted into the snapshot periodically.
   - Persistence is **debounced** (e.g. 2 s quiescence) and forced on last-leave and
     graceful shutdown — never per keystroke.

3. **Ephemeral / cross-node state — Redis.**
   - **Pub/sub** fan-out of updates + awareness between nodes holding the same doc.
   - **Presence** registry (who's connected where) with TTL heartbeats.
   - **Idempotency** seen-`msgId` sets and rate-limit counters.

**Multi-node consistency:** all nodes serving a doc subscribe to `doc:{id}` on Redis.
An update integrated on node A is published; nodes B/C apply it with a dedicated
`origin` so they don't republish (loop prevention), then fan out to their own
sockets. Because updates are CRDT, cross-node ordering is irrelevant.

---

## 6. Live presence (awareness)

Built on `y-protocols/awareness`. Each connection publishes an ephemeral state map:
```json
{ "user": {"id","name","color"}, "cursor": {...}, "selection": {...}, "typing": true }
```
- **Distinct identity:** stable per-user color derived from userId; name from JWT.
- **Join/leave:** awareness add/remove events → `PRESENCE_JOIN/LEAVE`; also a TTL so
  a crashed client's presence expires.
- **Multi-tab:** each tab is a separate `sessionId`/clientID, shown as one user with
  multiple cursors (deduped by userId in the "active users" list).
- Awareness is **never persisted** and is fanned out over Redis like edits.

---

## 7. Network reliability & recovery

- **Automatic reconnection:** the `y-websocket` provider reconnects with exponential
  backoff + jitter (thundering-herd safe).
- **Queuing while offline:** local edits keep applying to the in-memory `Y.Doc` and
  persist to **IndexedDB** (`y-indexeddb`). Nothing is lost.
- **Sync after reconnect:** the sync handshake (§2) exchanges state vectors and
  transfers only the missing delta in both directions — pending local edits go up,
  missed remote edits come down. No full-document transfer, no lost edits.
- **Recovery from failures** (browser refresh/crash, app restart, server restart,
  node failover): the client rehydrates from IndexedDB instantly, reconnects to *any*
  node (nodes are stateless — they rebuild the doc from Postgres), and re-syncs.
  Server restart loses only in-memory state, which is rebuilt from snapshot+updates.
- **Duplicate/lost/delayed/reordered messages:** covered by CRDT idempotency +
  commutativity and the control-message idempotency set.

---

## 8. Offline editing

`y-indexeddb` persists the doc locally; edits continue offline against the local
CRDT. On reconnect, Yjs merges local and remote via state vectors:
- **No overwrite of newer remote edits** — both sides' updates are integrated by ID;
  concurrent changes coexist, they don't clobber.
- **Safe conflict resolution** — same CRDT convergence; deterministic result.

---

## 9. File management during collaboration

**Model:** each *file* is its own Yjs sub-document, `docId = roomId + "/" + path`.
This isolates files so a stale update for file A can never touch file B.

- **Open/switch files:** open/close the sub-doc's provider; the file tree is itself a
  small shared Yjs map (`roomId/__tree`) so creates/renames/deletes replicate.
- **Rename while editing:** rename is a control op on the shared tree; the doc's
  *content* keeps its identity (we rename the logical path, keep the same underlying
  doc key via an id-indirection: `path → docKey`), so in-flight edits are preserved
  and simply reassociated. Simultaneous renames converge on the tree CRDT; the losing
  name is superseded deterministically and both users are notified.
- **Delete while open:** soft-delete (tombstone in the tree + `DELETE_FILE` control
  msg). Open editors get a "file deleted" banner; edits are blocked (RBAC-style gate)
  but the doc/snapshot is retained for **restore**. Restore flips the tombstone.
- **Simultaneous delete/rename:** tree CRDT converges; idempotent (deleting twice is
  a no-op). Stale updates to a deleted doc are dropped by the server gate.
- **Stale updates isolated:** because each file is a separate `docId`, updates are
  routed only to that doc's subscribers.

---

## 10. Undo / redo

Use **`Y.UndoManager` scoped by origin/tracked-origins**:
- Each client tags its edits with a client-specific `origin`; its `UndoManager`
  tracks **only that origin**, so undo affects **only the user's own operations** and
  never a collaborator's work.
- History stays consistent because undo/redo generate *new* CRDT updates (inverse
  operations), which propagate and converge like any edit.
- Continues to work after sync/reconnect because the UndoManager operates on the
  live CRDT state, not on a transient buffer.
- **Simultaneous undo/redo** by different users are just concurrent inverse ops →
  converge normally.

---

## 11. Performance

- **Incremental only:** never send the whole document — sync sends state-vector
  diffs; live traffic is small binary updates. (Explicit requirement met.)
- **Large files / long sessions:** Yjs stores structs compactly; periodic snapshot
  **compaction** collapses tombstones and the update log so memory/storage don't grow
  unbounded.
- **High typing speed:** provider-side update coalescing batches rapid keystrokes
  into fewer frames; awareness is throttled (e.g. 50 ms).
- **Hundreds of collaborators per doc:** node fans out via a single Redis subscription
  per doc, not per pair; awareness throttled; optional interest-based cursor culling.
- **Thousands of concurrent users:** horizontal scale-out of stateless nodes behind a
  WS-aware LB; docs shard naturally across nodes; Redis handles cross-node fan-out.
- **Backpressure:** per-connection send queue with high-water mark; slow clients get
  coalesced/full-resynced rather than blocking the doc.

---

## 12. Security

- **AuthN:** every connection must present a valid **JWT** (verified by signature
  against the Control Plane's key). `userId` is taken from the token, **never** from
  client-supplied fields — prevents impersonation.
- **AuthZ (per-operation):** on connect and on each control op, the node checks the
  user's **role** for the room (owner/editor/viewer) via the Membership service
  (cached in Redis). **Viewers receive updates but their inbound edits are dropped at
  the server** — the real enforcement, independent of UI.
- **Validation:** every frame is size-capped and structurally validated before apply;
  malformed/oversized frames are rejected and the connection is scored (repeat
  offenders dropped).
- **Isolation:** doc access is scoped by `docId`; a token for room A cannot open room
  B's docs.
- **Rate limiting:** per-connection and per-user token buckets in Redis.
- **Transport:** WSS/TLS only; origin checks; short-lived tokens with reconnect
  re-auth.

---

## 13. Error handling & recovery strategy (summary)

| Failure | Handling |
|---|---|
| Browser refresh / crash | IndexedDB rehydrate + reconnect + resync |
| App restart | Same; provider re-establishes from local state |
| Server / node restart | Stateless node rebuilds doc from Postgres; client resyncs |
| Node failover | LB routes to another node; identical behavior |
| Network interruption | Backoff reconnect; offline queue; delta resync |
| Duplicate messages | CRDT idempotency + control-msg dedup set |
| Lost messages | State-vector diff refills the gap on next sync |
| Delayed / out-of-order | CRDT commutativity |
| Malicious/invalid op | Rejected at validation/RBAC gate |
| Stale doc version on reconnect | Handshake computes exact delta; no special path needed |

**No corruption / no permanent loss:** durability = snapshot + update log in
Postgres, plus client IndexedDB; convergence = CRDT.

---

## 14. Edge-case handling index

Every listed edge case maps to a mechanism above: same-position typing, overlapping
delete, insert-during-delete, multi-line, huge paste, long sessions, multi-tab,
duplicate reconnect, disconnect-mid-edit, outdated-version reconnect, simultaneous
undo/redo, simultaneous save, delete/rename-while-open, session timeout, empty & huge
docs, **Unicode/emoji** (Yjs is codepoint-aware; we count in UTF-16 units consistently
across Monaco and the CRDT), **newline formats** (normalized to `\n` on ingest, editor
renders per-platform), rapid cursor movement (throttled awareness), high latency /
dup / reorder / loss (CRDT + reconnect). Each has an explicit test (§15).

---

## 15. Testing strategy

- **Unit — convergence properties:** property-based tests generating random
  concurrent op sets, asserting all replicas converge (order & duplication
  permutations). Covers same-position, overlap, insert/delete, replace, paste.
- **Idempotency/order:** apply updates twice / shuffled → identical state.
- **Integration — multi-client over real WS:** N simulated clients edit, assert equal
  final docs and correct presence.
- **Network fault injection:** drop/delay/duplicate/reorder frames; kill & reconnect
  mid-edit; assert no loss and convergence.
- **Offline:** partition a client, edit both sides, rejoin, assert merge without
  overwrite.
- **Recovery:** kill node / restart server between edits; assert rebuild from
  Postgres and resync.
- **Undo/redo:** interleaved multi-user undo; assert user-scoped correctness.
- **File ops:** concurrent rename/delete/restore; assert tree convergence and no
  cross-file bleed.
- **Stress/soak:** hundreds of clients on one doc; multi-hour session; assert latency
  budget and bounded memory (compaction).
- **Security:** forged token, wrong-room token, viewer edit attempt, oversized frame
  → all rejected.

---

## 16. Build order (incremental, each step demoable)

1. **Core sync node** — Y.Doc DocManager + WS + Yjs sync/awareness protocol (single node).
2. **Persistence** — Postgres snapshot + update log; load/rebuild.
3. **Auth + RBAC gate** — JWT verify, membership/role, viewer read-only enforcement.
4. **Horizontal scale** — Redis pub/sub fan-out + presence registry + idempotency.
5. **Client integration** — swap frontend `y-webrtc` → `y-websocket` + `y-indexeddb`;
   wire `Y.UndoManager` (per-origin) and awareness UI.
6. **File-doc model** — per-file sub-docs + shared tree; rename/delete/restore.
7. **Hardening** — rate limits, backpressure, compaction, metrics/telemetry, load tests.

Directory layout of this service is under `src/` following these blocks.
