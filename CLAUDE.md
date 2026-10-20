# CLAUDE.md — raft-kv

Raft-replicated, linearizable key-value store in Java 21. A resume project for a CS master's student,
who must be able to explain every line in interviews. **Readable code over clever code.**

## Working agreement
- Work in phases (listed below). At the end of each phase: run all tests and show the results, summarize
  what was built and the key decisions in plain language, commit and push, update this file,
  then **STOP and wait for "continue"**.
- **Never invent benchmark numbers.** Every number must come from a script in this repo that can be rerun.
  Final resume numbers come from the Oracle Cloud ARM VM only. Codespaces numbers are sanity checks.
- Flaky test → fix the root cause. Never add sleeps or weaken assertions.
- No Raft/consensus libraries. Allowed deps: gRPC, protobuf, JUnit 5, HdrHistogram, SLF4J/Logback.
  Everything free/open source.
- Small commits with clear messages, at least one per phase.
- Warn the user before anything that takes more than 10 minutes (Codespaces hours are limited).

## Environment
- Dev: GitHub Codespaces, 2 cores, ~8 GB RAM, no swap, Ubuntu 24.04, Java 21, Docker-in-Docker.
- Always use `./gradlew` (wrapper pinned to Gradle 9.8.1). Gradle daemon is capped at `-Xmx1g`.
- Each node runs with `-Xmx256m`; the 5-node cluster must fit in about 2.5 GB.
- Deploy target: Oracle Always Free ARM VM (aarch64). Docker images are multi-arch (amd64 + arm64).

## Module layout
| Module | Contents |
|---|---|
| `proto/` | `raft.proto` (RequestVote, AppendEntries, InstallSnapshot) and `kv.proto` (Get/Put/Delete, `KvCommand` log payload) |
| `storage/` | `Wal` (CRC32 records, configurable fsync, torn-tail truncation), pager + LRU page cache (4 KB pages), B-Tree (get/put/delete/range scan) |
| `raft/` | `RaftNode` (Figure 2), Raft log, persistent state, `Transport`/`Scheduler`/`Clock` interfaces, `sim/` deterministic simulated network |
| `kv/` | KV state machine on the B-Tree, session table, ReadIndex reads |
| `client/` | `KvClient`: leader discovery, redirects, retries with the same sequence number |
| `server/` | node `Main`: gRPC transport, config, HTTP `/status`, Logback |
| `linearizability/` | history recorder + Wing-Gong-Lowe checker |
| `chaos/` | seeded chaos harness (`./gradlew chaos -Pruns=N`), Docker chaos driver |
| `bench/` | closed-loop HdrHistogram load generator, failover trials |

Package root: `io.github.hard3007.raftkv.<module>`. Generated protos: `io.github.hard3007.raftkv.proto.{raft,kv}`.
Build config: `buildSrc/src/main/kotlin/raftkv.java-conventions.gradle.kts` (shared), `gradle/libs.versions.toml` (versions).

## Design decisions (binding)
1. **Deterministic simulation.** `RaftNode` is event-driven and never creates threads or reads the wall clock.
   It runs on an injected `Scheduler` + `Clock`. In simulation, all nodes share one single-threaded
   discrete-event loop on virtual time, so a seed reproduces a run exactly.
2. **Production threading.** Each node has ONE single-threaded executor. All gRPC callbacks (incoming RPCs and
   replies) and timer events are posted onto it. **No Raft state is touched from gRPC threads.** Before the
   Docker work in Phase 5, an in-process integration test runs 5 nodes with real threads and real gRPC.
3. **Figure 2 exactly.** Persist currentTerm, votedFor, and log entries durably BEFORE replying to any RPC.
   Election timeout randomized in [150, 300) ms; regular heartbeats. Comments name the Figure 2 rule they implement.
4. **Fast backtracking** via `conflict_term` / `conflict_index` in `AppendEntriesReply` (0 term = log too short).
5. **Commit rule.** The leader advances commitIndex only by counting replicas of an entry from its CURRENT term
   (Figure 2 leader rule; §5.4.2). Entries are applied strictly in index order.
6. **No-op on election.** A new leader immediately appends a `NOOP` entry for its term (§5.4.2, §8).
   ReadIndex reads are NOT served until the leader has committed an entry in its current term.
7. **ReadIndex.** readIndex = commitIndex; leader confirms leadership with a heartbeat quorum, waits until
   lastApplied ≥ readIndex, then serves the read locally.
8. **Durability layering.** The Raft log is built on the storage `Wal`. The B-Tree is the KV state machine and
   stores `lastApplied` in its meta page. After a crash, Raft re-applies from `lastApplied + 1`, so the B-Tree's
   WAL may use lazy fsync (configurable).
9. **Write-ahead rule.** Even with lazy fsync, a dirty B-Tree page is NEVER written to disk before the WAL
   record that last modified it has been flushed (page LSN ≤ flushed LSN). Document this and the lazy-fsync
   trade-off in DESIGN.md.
10. **Session table durability.** The session table (clientId → lastSeq + cached reply) lives in the B-Tree and
    is updated atomically with `lastApplied` and the data change, in the same write batch. Snapshots include it.
11. **Indeterminate operations.** In the linearizability checker, an operation whose client timed out or got an
    unknown result is recorded as pending, with no return time. It may take effect at any point after its
    invocation. The checker has a self-test for this case.
12. **Persistence in tests.** `Persister` has an in-memory implementation (models "fsynced vs. not", so a crash
    loses unsynced data) for the simulator, and a file-backed implementation on the `Wal`.
13. **/status** uses the JDK's `com.sun.net.httpserver` (no extra dependency). The public status page and a
    `/cluster` aggregate are served from the VM itself (avoids mixed content and CORS). HTTPS is decided in Phase 8.

## Phases
- [x] **0. Scaffold** — Gradle wrapper, modules, CLAUDE.md, CI skeleton, proto files. Build passes.
- [x] **1. Storage** — WAL, B-Tree, recovery. 100k-op TreeMap oracle test and crash-recovery tests pass.
  The recovery tests were shown to catch three deliberate bugs (see DESIGN.md §1.4).
- [x] **2. Raft core on the simulated network** — election, replication, persistence. All lab-style tests pass with
  50 seeds (1,121 runs; a 500-seed sweep of 11,021 runs also passed). Mutations M1–M4 are all caught (DESIGN.md §2.5).
- [ ] **3. KV state machine** — sessions, ReadIndex reads (gated on the leader's no-op, which exists since Phase 2), client library.
- [ ] **4. Linearizability checker + chaos harness** — checker self-tests (incl. pending ops), 500 chaos runs,
  deliberately break the system (e.g., follower reads), show it is caught, then revert. Add 50-seed chaos to CI.
- [ ] **5. gRPC transport** — GATE: before any Docker work, an in-process integration test runs 5 nodes with real
  threads (one executor per node) and the real gRPC transport, and passes. Then the Docker 5-node cluster and Docker-level chaos.
- [ ] **6. Snapshots + log compaction** (InstallSnapshot, session table included). Rerun the full chaos suite.
- [ ] **7. Benchmarks + failover + optimizations** (AppendEntries batching/pipelining, group-commit fsync; before/after).
  Sanity runs in Codespaces; give exact commands for the Oracle VM.
- [ ] **8. Docs + deploy** — README, DESIGN.md, INTERVIEW_NOTES.md, DEPLOY.md, status page, Oracle deploy.
- At the very end: print resume numbers (chaos runs, ops/sec, p99, failover time) with the exact command and machine for each.

## Status
- Done: Phases 0–2.
- Next: Phase 3 (KV state machine, sessions, ReadIndex, client library). Waiting for the user to say "continue".
- Phase 3 constraints (decided now):
  - **Stay within the B-Tree limits** (key ≤ 256 B, value ≤ 1024 B). Data keys are a 1-byte prefix plus the user key,
    so the API caps user keys at 255 B and values at 1024 B and rejects larger ones before proposing.
    Session entries use the key `prefix + clientId (8 B)`, and their value is `lastSeq (8 B) + cached reply`.
    The cached reply holds only a write's status and the `existed` flag, never a value, so it stays tiny.
  - One B-Tree `WriteBatch` per applied entry: the data change, the session update, and `setAppliedIndex(index)`.
  - On restart, Raft must resume with lastApplied = commitIndex = the B-Tree's appliedIndex
    (RaftNode currently starts at 0; add a constructor parameter).
  - ReadIndex waits for the leader's no-op to commit; reads never go through the log.

## Known build warnings
- `./gradlew build --warning-mode all` reports `Configuration.setVisible(boolean)` deprecations (removal in Gradle 11).
  All come from the third-party protobuf Gradle plugin 0.10.0 (`ProtobufPlugin.groovy`, `ToolsLocator.groovy`),
  not from our build scripts. Revisit when a newer plugin release fixes them. Our own scripts must stay warning-free.

## Storage module map (Phase 1)
- `FileSystem` / `StorageFile` interfaces; `DiskFileSystem` (real, fsyncs directory on create/rename);
  `SimulatedFileSystem` (seeded; process crash, power loss with lost/torn/reordered writes, crash at Nth op).
- `Wal`: CRC32 records, LSNs, torn-tail truncation, `FsyncPolicy` ALWAYS/LAZY, atomic `reset()` via tmp + rename.
- `BTree`: public API (get/put/delete/write(WriteBatch)/scan/appliedIndex/sync/checkpoint).
  One WAL record per batch with full page images + meta. Write-ahead rule in `BTree.writeBack`.
  Internals: `PageFile` (4 KB pages), `PageCache` (LRU, eviction only between ops), `Node`/`LeafNode`/`InternalNode`.
- Limits: key ≤ 256 B, value ≤ 1024 B (any entry < 1/3 page, so a split always fits). No merge on delete.
- DESIGN.md §1 explains all of it, including the lazy-fsync trade-off.

## Raft module map (Phase 2)
- `RaftNode`: Figure 2, tagged `[Fig2 <rule>]`; `RaftLog` (in memory); `RaftStorage` (one `RaftStateRecord` per
  save on a `Wal` with ALWAYS fsync); interfaces `Scheduler`, `Transport`, `RaftRpcHandler`, `StateMachine`; `RaftConfig`.
- `raft.sim`: `Simulation` (virtual-time event loop, one seed), `SimNetwork` (disconnect, partition, 10% loss, delays,
  long delays, message filter).
- Tests: `RaftCluster` harness (Election Safety after every event, State Machine Safety + in-order apply on every apply),
  `RaftNodeTest` (receiver rules), `RaftElectionTest`/`RaftAgreementTest`/`RaftPersistenceTest` (MIT 6.5840 3A–3C),
  `RaftScenarioTest` (deterministic Figure 8, fast backup). Seeds: `./gradlew :raft:test -PraftSeeds=50`.
- Mutation testing (DESIGN.md §2.5): the no-op on election masks the Figure 8 bug under large batches, so the
  Figure 8 tests run with `maxEntriesPerAppend = 1`. Keep that when changing replication (Phase 7).

## Commands
- Build + all tests: `./gradlew build`
- One module's tests: `./gradlew :storage:test`
- Raft tests with N seeds each: `./gradlew :raft:test -PraftSeeds=50`
