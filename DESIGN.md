# Design

This document records every key decision, the trade-off behind it, and how each failure
scenario is handled. It grows phase by phase. Sections not yet written are listed at the end.

## 1. Storage engine (`storage/`)

### 1.1 Files and the `FileSystem` abstraction
All storage code talks to a tiny `FileSystem` / `StorageFile` interface (open, read, write,
truncate, force, atomic replace). There are two implementations:

- `DiskFileSystem`: `FileChannel`. `force()` is `fdatasync`. Creating, deleting and renaming
  a file also fsyncs the directory, because a new directory entry is not durable until then.
- `SimulatedFileSystem`: in memory. It keeps a "current" image (what reads see, like the
  OS page cache) and a "durable" image (what survives power loss) for each file. It can simulate:
  - a **process crash**: all writes survive;
  - **power loss**: each un-fsynced write is independently lost, kept, or torn. Keeping a later
    write and losing an earlier one models the disk reordering writes;
  - a **crash at the Nth I/O operation**: that operation and every later one throw.

  Everything is driven by a seeded `Random`, so every failure is reproducible. The Raft tests in
  Phase 2 reuse it.

Storage I/O errors are unchecked and **fatal (fail-stop)**. After a failed write or fsync the
on-disk state is unknown, so the node restarts and recovers from what is durable. (This is the
"fsyncgate" lesson: retrying a failed fsync can falsely report success on Linux.)

### 1.2 Write-ahead log (`Wal`)
```
header:  magic | version | firstLsn | crc32(header)
record:  length (4) | crc32(length ++ payload) (4) | payload
```
- **LSNs.** Every record has a log sequence number (LSN). LSNs keep increasing across `reset()`,
  so "is LSN x durable?" is always a single comparison against `durableLsn()`.
- **The CRC covers the length field too.** A corrupted length is caught by the CRC, not trusted.
  A length above 32 MB is also rejected outright.
- **Recovery** scans from the start and stops at the first record that is incomplete, has an
  impossible length, or fails its CRC. It truncates the file there and fsyncs.
  - A crash during an append can only damage the tail, so this recovers exactly the records
    that were completely written.
  - *Trade-off:* corruption in the middle of the log (bit rot) is indistinguishable from a torn
    tail and loses everything after it. Telling them apart would need extra redundancy. In this
    system, a node that loses its log tail is repaired by its Raft peers.
- **The header is never modified in place.** A new or reset log is written to `<name>.tmp`,
  fsynced, and renamed over the old one (`rename` is atomic). So the header can never be torn.
- **Fsync policy** (`FsyncPolicy`):
  - `ALWAYS`: fsync on every append; an acknowledged record survives power loss.
  - `LAZY`: fsync only on `sync()`, at checkpoint, or when the write-ahead rule (§1.4) demands it.
  - Group commit (batching several appends into one fsync) comes in Phase 7.

### 1.3 B-Tree layout
- **Pages.** 4 KB pages in `<name>.db`.
  - Page 0 is the meta page: root page id, page count, applied index, CRC.
  - Every other page holds one node. Leaves hold sorted key/value pairs. Internal nodes hold
    n keys and n+1 child page ids, and child *i* holds keys in `[k[i-1], k[i])`.
- **Splits.** A node splits when its encoding exceeds 4096 bytes. The split point is chosen
  **by bytes, not by entry count**.
  - Keys are limited to 256 B and values to 1024 B, so a leaf entry is at most 1284 B, below a
    third of a page.
  - That bound guarantees both halves of an overfull node fit in a page. The argument: the left
    half is at most half the bytes, and the right half is less than half plus one entry. So no
    overflow pages are needed.
- **No merge on delete.** Deletes never merge or rebalance. A leaf may become empty, and pages
  are never freed. This is simpler and is what many production B-Trees do between vacuums.
  *Trade-off:* space is not reclaimed under heavy delete churn.
- **Page cache.** LRU of decoded nodes (`LinkedHashMap` in access order). **Eviction runs only
  between operations**, never in the middle of one. So every node an operation touches stays in
  memory until the operation's WAL record exists. That removes the need for page pinning.

### 1.4 Durability: one WAL record per batch, full page images, and the write-ahead rule
- **One WAL record per batch.** `BTree.write(WriteBatch)` applies all of a batch's operations
  in memory. It then appends **one** WAL record holding the meta fields plus the **full new
  image of every page the batch changed**.
  - A WAL record is all-or-nothing (CRC), so the batch is too, even when it splits pages at
    several levels.
  - The KV state machine relies on this. A client write, its session-table update, and
    `lastApplied` go into the same batch (decision 10 in CLAUDE.md).
- **The write-ahead rule.** A dirty page may be written to the data file only after the WAL
  record holding its latest image is durable.
  - In code (`BTree.writeBack`): `if (frame.lsn > wal.durableLsn()) wal.sync();` runs before
    every page write, both on eviction and at checkpoint.
  - This holds **even under `LAZY` fsync**. Lazy fsync delays when the WAL is synced, never
    the order.
- **Why the rule matters.** Suppose the OS wrote an evicted page to disk before the WAL record
  that explains it, and then the power failed. The data file would contain a change the
  surviving WAL knows nothing about, for example the left half of a split whose parent update
  and right half were lost. Recovery replays the WAL and would never touch that page, so the
  tree would stay corrupt.
- **Proof that the tests check it.** Deleting the `wal.sync()` line makes
  `powerLossWithLazyFsyncRecoversAPrefixIncludingEverySyncedBatch` and
  `crashAtAnyIoOperationRecoversAConsistentPrefix` fail (wrong entry counts after recovery).
- **Recovery** (on open):
  1. Write every intact WAL record's page images into the data file and restore the meta fields
     from the last record.
  2. fsync the data file, write the meta page, and fsync again.
  3. Reset the WAL.

  Writing full images is idempotent, so a crash *during* recovery just means recovery runs again.
  Full images also repair torn data-page writes: every page written since the last checkpoint has
  its latest image in the WAL. This is the same idea as PostgreSQL's `full_page_writes`.
- **Checkpoint:**
  1. Sync the WAL.
  2. Write back every dirty page (obeying the rule).
  3. Write the meta page and fsync the data file.
  4. **Only then** reset the WAL.

  Resetting the WAL before the data fsync loses acknowledged writes; the crash-injection test
  catches this (219 batches acknowledged, 209 recovered). A checkpoint runs when the WAL exceeds
  a size threshold and on `close()`. It is skipped when the WAL is empty, so the meta page is
  never rewritten without a WAL to fall back on.
- **Torn meta page.** A checkpoint interrupted while writing the meta page leaves a bad CRC. The
  WAL still holds every record since the previous checkpoint, and its last record carries the
  meta fields. A bad meta page with an empty WAL is reported as corruption.
- **Initialization.** The data file is written and fsynced first, then the WAL is created
  (atomically). A missing WAL therefore means "never fully created", and the data file is
  re-initialized.

### 1.5 The lazy-fsync trade-off
Under Raft, the Raft log (fsynced before every RPC reply, per Figure 2) is the source of truth,
and the B-Tree is a cache of the state machine. So the B-Tree can run with `LAZY`:

- **Gain.** No fsync on the state-machine apply path. The only fsync per committed write is the
  Raft log's.
- **Cost.** After power loss the B-Tree may come back *behind*, at an earlier `appliedIndex`.
  That is safe: `appliedIndex` is stored atomically with the data in the same WAL record, so the
  tree is always exactly "the state after entry N" for some N. Raft re-applies entries
  N+1 onward from its own log. The tree can never come back *ahead* of its `appliedIndex`, or
  in a mixed state.
- **What must hold for this to be safe.** The write-ahead rule (§1.4). Lazy fsync is only
  correct because pages never reach disk ahead of their log records.
- **When it would be wrong.** If the B-Tree were the only copy of the data (no Raft log), `LAZY`
  would silently lose acknowledged writes on power loss. That is why `ALWAYS` is the default.
- **After snapshots (Phase 6)** the Raft log is truncated. The B-Tree must therefore be fsynced
  (checkpointed) before the log prefix it covers is discarded. Otherwise power loss could leave
  the tree behind a log that can no longer replay the gap.

### 1.6 Failure scenarios covered by tests
| Scenario | Handling | Test |
|---|---|---|
| Torn WAL append (cut at any byte of the last record) | tail truncated, earlier records intact | `WalTest.tornTailIsTruncatedAtEveryPossibleCutPoint` |
| Bit flip in a record's payload or length | CRC mismatch, record and tail dropped | `WalTest.corruptedCrcInTailRecordIsDropped`, `corruptedLengthFieldIsDetected` |
| Zero-filled tail (preallocation, file hole) | CRC mismatch, truncated | `WalTest.zeroFilledTailIsTruncated` |
| Corrupted WAL header | fails loudly, never guesses | `WalTest.corruptedHeaderFailsLoudly` |
| Process crash with lazy fsync | nothing lost (OS cache survives) | `BTreeRecoveryTest.processCrashLosesNothingEvenWithLazyFsync` |
| Power loss, `ALWAYS` | every acknowledged batch survives | `BTreeRecoveryTest.powerLossWithAlwaysFsyncKeepsEveryAcknowledgedBatch` |
| Power loss, `LAZY` | an exact prefix of batches, including every synced one | `BTreeRecoveryTest.powerLossWithLazyFsyncRecoversAPrefixIncludingEverySyncedBatch` |
| Crash at any I/O operation (append, eviction, checkpoint, WAL reset, recovery itself) | an exact prefix; the in-flight batch is all-or-nothing | `BTreeRecoveryTest.crashAtAnyIoOperationRecoversAConsistentPrefix` (600 seeded runs) |
| Real disk: crash without close plus a garbage WAL tail | all 500 batches recovered | `BTreeRecoveryTest.realDiskRecoversAfterCrashWithoutCloseAndATornWalTail` |
| B-Tree logic (splits, scans, unsigned key order, deletes) | matches a `TreeMap` | `BTreeOracleTest` (100,000 ops, 32-page cache, reopen midway) |

## 2. Raft (`raft/`)

### 2.1 Structure
- **`RaftNode`** implements Figure 2. It is purely event-driven: RPC handlers, reply callbacks,
  timer callbacks and `propose()` are its only entry points. All of them run on one thread, it
  never creates threads, and it never reads the clock. Time and timers come from an injected
  `Scheduler`; outgoing RPCs go through an injected `Transport`.
- **`RaftLog`** is the in-memory log. Indices start at 1, and index 0 is a sentinel with term 0.
- **`RaftStorage`** is the durable state. Every save appends one `RaftStateRecord` (term,
  votedFor, optional truncation point, new entries) to a `Wal` with `FsyncPolicy.ALWAYS`.
  - One record per change means a crash can never persist half a change, for example a
    truncation without the entries that replace it.
  - Recovery replays the records in order.
- **Simulation (`raft.sim`).** `Simulation` is a discrete-event loop on virtual time, with
  events ordered by (time, insertion order). `SimNetwork` implements `Transport` and can:
  disconnect nodes, partition the cluster into groups, drop 10% of requests and replies,
  delay messages 0–27 ms (which reorders them), occasionally delay one by 200–2000 ms, and run a
  test-supplied filter that observes or drops chosen messages.
  - All randomness comes from one seed, so **a failing seed replays exactly**.
  - Virtual time makes a 30-second scenario take about 0.1–0.5 s of wall time.
- **Crash = power loss.** A simulated crash kills the node's timers and in-flight messages and
  calls `SimulatedFileSystem.simulatePowerLoss()` on its disk. A restarted node runs the real
  recovery code on whatever was fsynced.

### 2.2 Decisions and trade-offs
- **No "check quorum".** Figure 2 does not make a leader step down when it cannot reach a
  majority, so a partitioned leader keeps believing it leads. That is safe: it cannot commit
  anything, and its first contact with a newer term demotes it. ReadIndex (Phase 3) will not
  rely on leases; it confirms leadership with a heartbeat quorum for each read.
- **The election timer is reset only** on: (a) an AppendEntries from the current-term leader,
  (b) granting a vote, (c) starting an election. It is *not* reset by rejected RequestVotes, or
  by RPCs from stale terms. Otherwise a node with a stale log could keep everyone from timing out.
- **Persist before replying, and before applying.** A follower saves term, vote and log changes
  before replying *and* before advancing commitIndex and applying. So the state machine can never
  get ahead of the durable log, which matters once the B-Tree persists `lastApplied` (Phase 3).
- **The leader persists its own entries before counting itself** toward a majority.
- **Fast backtracking.** The follower replies `conflict_term` and `conflict_index`. The leader then:
  - jumps to `conflict_index` if the follower's log is too short (`conflict_term = 0`);
  - otherwise, if it has entries from `conflict_term`, jumps to just after its last such entry;
  - otherwise skips the follower's whole conflicting term.

  Stale or reordered replies never move `nextIndex` or `matchIndex` backwards.
- **No-op on election** (§5.4.2, §8). A new leader appends a `NOOP` entry for its term
  immediately, so earlier-term entries commit without waiting for a client write. Phase 3's
  ReadIndex will not serve reads until that no-op is committed.
- **Randomized election timeouts in [150, 300) ms, heartbeats every 50 ms.** These are the
  paper's numbers. Heartbeats are well below the minimum timeout, so a healthy leader is never
  deposed.
- **Simplicity over throughput (for now).** AppendEntries carries up to 64 entries. It is sent
  on every proposal, every heartbeat, and right after a reply that leaves the follower behind.
  There is no pipelining or in-flight limit yet; Phase 7 adds batching and pipelining and
  measures the difference.

### 2.3 Figure 2 rules → code → tests
Tags in the code look like `// [Fig2 AppendEntries-3]`. "Rules for Servers" are numbered in
the order the paper lists them. All code is in `RaftNode` unless noted. "Cluster tests" means
the always-on harness checks (Election Safety after every event, State Machine Safety and
in-order apply on every apply) that every simulated-cluster test runs.

| Rule (Figure 2) | Code | Tests that cover it |
|---|---|---|
| State: persistent `currentTerm`, `votedFor`, `log[]` | fields; `RaftStorage` | `RaftStorageTest`, `RaftNodeTest.persist_*` |
| State: volatile `commitIndex`, `lastApplied`; leader `nextIndex[]`, `matchIndex[]` (reinitialized after election) | fields; `becomeLeader` | all cluster tests |
| Persist: update stable storage before responding to RPCs | `handleRequestVote`, `handleAppendEntries`, `startElection` (self-vote before RequestVotes go out), `appendToOwnLog` | `RaftNodeTest.persist_voteAndTermSurviveACrash`, `persist_termLearnedFromARejectedRequestSurvivesACrash`, `persist_logSurvivesACrashIncludingTruncation`, `persist_candidatesSelfVoteSurvivesACrash`; `RaftPersistenceTest.*` |
| RequestVote-1: reply false if term < currentTerm | `handleRequestVote` | `RaftNodeTest.requestVote1_rejectsCandidateFromAnOlderTerm` |
| RequestVote-2: grant if votedFor is null or candidateId, and the candidate's log is at least as up-to-date | `handleRequestVote`, `candidateLogIsUpToDate` | `RaftNodeTest.requestVote2_*` (4 tests); `RaftAgreementTest.partitionedLeaderRejoins`; `RaftPersistenceTest.figure8OnUnreliableNetwork` |
| AppendEntries-1: reply false if term < currentTerm | `processAppendEntries` | `RaftNodeTest.appendEntries1_rejectsLeaderFromAnOlderTerm` |
| AppendEntries-2: reply false if no entry at prevLogIndex with prevLogTerm (+ conflict hints) | `processAppendEntries` | `RaftNodeTest.appendEntries2_*` (2 tests); `RaftScenarioTest.fastBackup_*` |
| AppendEntries-3: delete a conflicting entry and all that follow it | `processAppendEntries` | `RaftNodeTest.appendEntries3_*` (2 tests); `RaftAgreementTest.fastLogBackup`, `partitionedLeaderRejoins` |
| AppendEntries-4: append any new entries not already in the log | `processAppendEntries` | `RaftNodeTest.appendEntries4_newEntriesAreAppended`; all agreement tests |
| AppendEntries-5: commitIndex = min(leaderCommit, index of last new entry) | `handleAppendEntries` | `RaftNodeTest.appendEntries5_*` (2 tests) |
| All-1: if commitIndex > lastApplied, apply log[lastApplied] in order | `applyCommittedEntries` | `RaftNodeTest.appendEntries5_commitIndexIsTheSmallerOf...`; the harness's in-order check in every cluster test |
| All-2: term T > currentTerm in a request or response → adopt T, become follower | `handleRequestVote`, `processAppendEntries`, `onRequestVoteReply`, `onAppendEntriesReply`, `stepDown` | `RaftNodeTest.all2_*`; `RaftElectionTest.reElectionAfterLeaderFailure` |
| Follower-1: respond to RPCs from candidates and leaders | `handleRequestVote`, `handleAppendEntries` | all tests |
| Follower-2: election timeout without hearing from the leader or granting a vote → candidate | `resetElectionTimer`, `onElectionTimeout` | `RaftElectionTest.*`; `RaftNodeTest.candidate3_*` |
| Candidate-1: increment term, vote for self, reset timer, send RequestVote to all | `startElection` | `RaftElectionTest.*`; `RaftNodeTest.persist_candidatesSelfVoteSurvivesACrash` |
| Candidate-2: votes from a majority → leader | `onRequestVoteReply` | `RaftElectionTest.initialElection`, `manyElections`, `noLeaderWithoutQuorum` |
| Candidate-3: AppendEntries from the new leader → follower | `processAppendEntries` | `RaftNodeTest.candidate3_candidateStepsDownOnAppendEntriesFromLeaderOfSameTerm` |
| Candidate-4: election timeout → new election | `onElectionTimeout` | `RaftElectionTest.reElectionAfterLeaderFailure`, `noLeaderWithoutQuorum` |
| Leader-1: initial heartbeat on election; repeat during idle periods | `becomeLeader`, `scheduleHeartbeat` | `RaftElectionTest.initialElection` (term must not change for 2 s) |
| Leader-2: append client command to the local log; respond after it is applied | `propose` (responding comes in Phase 3) | `RaftAgreementTest.*` |
| Leader-3: send entries from nextIndex; on success update nextIndex/matchIndex; on inconsistency back up and retry | `sendAppendEntries`, `onAppendEntriesReply`, `fastBackupNextIndex` | `RaftAgreementTest.fastLogBackup`, `followerRejoinsAndCatchesUp`; `RaftScenarioTest.fastBackup_skipsAWholeConflictingTermPerRoundTrip` |
| Leader-4: commit N if a majority has matchIndex ≥ N **and log[N].term == currentTerm** | `advanceCommitIndex` | `RaftScenarioTest.figure8_leaderDoesNotCommitPreviousTermEntryByCountingReplicas`; `RaftPersistenceTest.figure8OnUnreliableNetwork` |
| Beyond Figure 2: leader no-op (§5.4.2, §8) | `becomeLeader` | all cluster tests (each leader's first entry) |

### 2.4 The lab-style tests
These are modeled on the MIT 6.5840 Raft lab (3A–3C). Every one runs per seed:
`./gradlew :raft:test -PraftSeeds=N` (the default is 3, for fast builds).

| Requirement | Tests |
|---|---|
| Initial election | `RaftElectionTest.initialElection` |
| Re-election after leader failure | `RaftElectionTest.reElectionAfterLeaderFailure`, `manyElections` |
| No leader without quorum | `RaftElectionTest.noLeaderWithoutQuorum` (and inside `reElectionAfterLeaderFailure`) |
| Basic agreement | `RaftAgreementTest.basicAgreement`, `concurrentProposals` |
| Agreement despite failures | `RaftAgreementTest.agreementDespiteFollowerFailure`, `agreementDespiteLeaderFailure`, `followerRejoinsAndCatchesUp`, `noAgreementWithoutMajority` |
| Rejoin of a partitioned leader | `RaftAgreementTest.partitionedLeaderRejoins` |
| Fast log backup | `RaftAgreementTest.fastLogBackup`, `RaftScenarioTest.fastBackup_skipsAWholeConflictingTermPerRoundTrip` (at most 2 rejections instead of 50) |
| Persistence across crashes | `RaftPersistenceTest.persistenceAcrossCrashes`, `persistenceWithRepeatedPartialFailures`, `partitionedLeaderAndCrashedFollower` |
| Unreliable network (Figure 8) | `RaftPersistenceTest.agreementOnUnreliableNetwork`, `figure8CrashingLeaders`, `figure8OnUnreliableNetwork`; `RaftScenarioTest.figure8_*` |
| Churn | `RaftPersistenceTest.churn`, `churnOnUnreliableNetwork` |

**Phase 2 gate.** All tests pass with 50 seeds each (1,121 test runs, about 10 s of wall time).
A wider sweep, 500 seeds each (11,021 runs), also passed.

### 2.5 Mutation testing: does the suite catch real Raft bugs?
Each bug was injected into `RaftNode`, the full suite was run with 10 seeds, and the bug was
reverted.

| Injected bug | Caught by | Example failure |
|---|---|---|
| **M1.** Voters ignore `last_log_term` in the up-to-date check (compare lengths only) | `RaftNodeTest.requestVote2_*` (2 tests); `RaftAgreementTest.partitionedLeaderRejoins` (10/10 seeds), `fastLogBackup` (6/10); `RaftPersistenceTest.figure8OnUnreliableNetwork` (10/10) | `State Machine Safety violated at index 10: node 3 applied (term 4, COMMAND 'cmd-99') but another node applied (term 5, NOOP '')` |
| **M2.** Leader commits previous-term entries by counting replicas (the Figure 8 bug) | `RaftScenarioTest.figure8_*` (10/10 seeds); `RaftPersistenceTest.figure8OnUnreliableNetwork` (5/10 seeds; 129/200 in a longer run) | `S1 must not commit its term-2 entry by counting replicas ==> expected: <0> but was: <2>` |
| **M3.** `currentTerm`/`votedFor` not persisted before replying to RequestVote | `RaftNodeTest.persist_voteAndTermSurviveACrash`, `persist_termLearnedFromARejectedRequestSurvivesACrash` | `currentTerm must be durable before replying ==> expected: <7> but was: <0>` |
| **M4.** Follower does not truncate conflicting entries (keeps whatever it has at an index) | 11 tests across all suites, including `RaftNodeTest.appendEntries3_*`, `RaftScenarioTest.fastBackup_*`, `RaftAgreementTest.partitionedLeaderRejoins`, `RaftPersistenceTest.persistenceWithRepeatedPartialFailures` (76 failed runs) | `State Machine Safety violated at index 2: node 2 applied (term 2, COMMAND 'stale-2') but another node applied (term 3, COMMAND 'leader-2')` |

**What the mutation runs taught us.**
- **The no-op hides the Figure 8 bug (M2).** With the default batch of 64 entries per
  AppendEntries, the randomized tests did not catch M2 in 200 seeds. A new leader's no-op is in
  its log from the start, and every AppendEntries runs to the end of the log. So an older-term
  entry essentially never reaches a majority without the current-term no-op arriving alongside
  it, and the buggy rule commits the same thing the correct rule does.
  - The bug is still real. A follower more than one batch behind, or Phase 7's pipelining,
    could expose it.
  - That is why there is a deterministic scenario test (`maxEntriesPerAppend = 1`, a message
    filter, and seeded logs that recreate Figure 8 exactly).
  - The randomized Figure 8 tests now also run with one entry per AppendEntries, which makes
    them catch M2 too.
- **The missing persist (M3) has a tiny window.** The next save, triggered by any
  AppendEntries or election, writes the term and vote anyway. A random crash would have to land
  in exactly that window. The randomized suites did not catch it in 200 seeds; the direct
  crash-and-restart unit tests catch it every time. Phase 4's chaos harness adds more crash
  points, but this rule is guarded by its unit tests.

## 3. Key-value service (`kv/`, `client/`)

### 3.1 State machine layout
`KvStateMachine` stores everything in one B-Tree, in two key spaces:
```
'D' + user key            -> user value                     (data)
'S' + clientId (8 bytes)  -> lastSequence (8) | existed (1)   (session table)
```
- **Limits.** User keys are capped at 255 B (one below the B-Tree's 256, for the prefix) and
  values at 1024 B. Larger ones are rejected with `INVALID_ARGUMENT` before anything is proposed.
  A session row is 9 B of key and 9 B of value.
- **One log entry = one atomic B-Tree batch.** Applying an entry writes the data change, the
  client's session row, and `setAppliedIndex(entry.index)` in a single `WriteBatch`, so a single
  WAL record (§1.4).
  - After any crash the tree is exactly "the state after entry N, sessions included".
  - Raft resumes with `lastApplied = commitIndex = N` (the new `appliedIndex` constructor
    argument of `RaftNode`) and re-applies from N + 1. That re-application cannot double-apply
    anything, because the session rows rolled back together with the data.
- **Bounded recovery.** The tree runs with lazy fsync (§1.5). Left alone it would fsync only on
  page eviction or a 16 MB checkpoint, so after a power loss Raft could have to re-apply a very
  long stretch of log. `KvStateMachine` syncs the tree every `syncEveryEntries` applied entries
  to bound that work.
  - A 200-seed test run showed why this matters: with a small dataset, nothing was ever
    evicted, and a restarted node sometimes came back with an empty tree.
  - The sync point is count-based, so it is deterministic.
- **Determinism.** `apply` depends only on the entry and the tree's current contents: no
  clocks, no randomness, no iteration over hash-ordered collections. Every replica therefore
  reaches the same state. `KvStateMachineTest.applyIsDeterministic` checks byte-identical trees
  and identical replies on two replicas after 2,000 random entries.

### 3.2 Client sessions (exactly-once writes)
- **Numbering.** Each client has a random 64-bit id and numbers its writes 1, 2, 3, ... A retry
  reuses the number. A client has **at most one outstanding request** (`KvClient` enforces this),
  which is why the server needs only one cached reply per client.
- **Rules when applying a write with sequence s** for a client whose session says `lastSequence = L`:
  - `s > L`: apply it, and store `(s, reply)`.
  - `s == L`: a retry of the latest write. Return the **cached reply** and change nothing. This
    matters for deletes: re-executing would report `existed = false`, the cached reply says `true`.
  - `s < L`: a long-delayed copy of an older write the client stopped waiting for. Change
    nothing; this keeps an old put from overwriting a newer value.
- **Why this preserves linearizability.** A write proposed twice shows up in the log twice, but
  only the first copy takes effect, at one point in the log order. Each copy that applies is a
  commit point, and the session rule makes all but the first one no-ops. So there is a single
  linearization point between the client's first send and the reply it finally accepts.
- **Never evicted.** Session rows are never removed (see §5).

### 3.3 Write path and the "unknown" outcome
- **Write path.** `KvService.put/delete` validates, proposes to Raft, and remembers
  `(index, term)`. When the entry at that index is applied, the reply goes out.
  This is Figure 2's Leader-2: "respond after entry applied to state machine".
- **Status rule: never report "failed" for a write that might still commit.**

  | Status | Meaning | When |
  |---|---|---|
  | `OK` | applied (or a duplicate of an applied write) | our entry applied at our index |
  | `NOT_LEADER` | **definite**: nothing was proposed | `propose` refused because this node is not the leader |
  | `INVALID_ARGUMENT` | **definite**: nothing was proposed | key or value over the limit |
  | `UNKNOWN` | may or may not take effect | the leader stepped down after proposing, the 1 s request timeout fired, or a different entry ended up at our index |

  - On step-down, `RaftNode` calls `setOnLeadershipLost`, and `KvService` answers every pending
    write with `UNKNOWN`. A new leader may still commit the entry.
  - `KvClient` retries `UNKNOWN` with the **same sequence number**, and the session table turns
    that into exactly-once.
  - If the client gives up (default 30 s), the result is `KvResult.Unknown`, never a failure.
- **Tests:**
  - `KvClusterTest.leaderThatStepsDownAfterProposingAnswersUnknown` and
    `serverTimeoutAfterProposingAnswersUnknown` check the status rule.
  - `retriedWriteIsAppliedOnceAndReturnsTheOriginalReply` checks exactly-once delivery of a retry.
  - Changing the step-down reply to `NOT_LEADER` makes the first test fail (3 of 3 seeds tried).

### 3.4 Linearizable reads: ReadIndex (`RaftNode.readIndex`)
Reads never go through the log. The steps for one read:
1. **Fix the read index.** Only once the leader has committed an entry from its current term
   (its no-op), it records `readIndex = commitIndex`.
   - Why wait: before that, the leader may not know which entries are committed, because a
     previous leader may have committed entries this leader holds but has not yet seen as
     committed.
   - Reads that arrive earlier are queued, not rejected.
2. **Confirm leadership with a heartbeat round that starts after the read arrived.**
   - The leader numbers its rounds, and every AppendEntries carries the round number current
     when it was sent. A read needs round `heartbeatRound + 1`.
   - Any reply in the leader's term (success or not) from a peer counts as that peer's
     acknowledgement of that round. The read waits for a majority (counting the leader).
   - Why "after the read arrived": a reply to a message sent *before* the read proves only
     that we were leader back then. A newer leader may have committed writes since.
   - Reads that arrive together share one round.
3. **Wait** until `lastApplied >= readIndex`.
4. **Serve** the read from the state machine.

Stepping down fails pending reads with `NOT_LEADER` (reads are safe to retry), and a 1 s
timeout answers `UNKNOWN`.

- `RaftReadIndexTest` drives a leader's network by hand to check each step:
  - the no-op gate;
  - that a reply to a pre-read AppendEntries does not confirm;
  - that 1 of 4 peers is not a majority of 5;
  - that stepping down fails the read;
  - that concurrent reads share one round.
- `KvClusterTest.deposedLeaderNeverServesAStaleRead` checks the end-to-end case. A leader is
  partitioned with a reader while the majority elects a new leader and commits a newer value.
  The old leader still believes it leads, but it cannot confirm, so the reader gets `Unknown`.
  After the heal, the reader sees the new value. Making `readIndex` serve immediately makes this
  test fail (3 of 3 seeds tried).

### 3.5 Client library (`KvClient`)
- **Async and single-threaded.** Callbacks run on the client's `Scheduler`, so the same client
  runs inside the deterministic simulator (Phase 4's chaos harness) and in production.
- **Redirects.** `NOT_LEADER` with a hint goes straight to the hinted leader. Without a hint, it
  tries the next server after a short pause.
- **No reply within 1.5 s:** try the next server, same sequence number.
- **Late replies.** Replies to an abandoned attempt are ignored (attempt numbers).
- **`common` module.** `Scheduler` moved into it so the client library depends only on
  `common` and `proto`, not on `raft`.

### 3.6 Simulated KV cluster (`kv` test fixtures: `SimKvCluster`)
- **What it runs.** Real `RaftNode` + `KvStateMachine` (B-Tree with lazy fsync) + `KvService`
  on each node, and real `KvClient`s, all on one seeded `Simulation`.
- **Client traffic is simulated too.** Client requests and replies travel over `SimNetwork` via
  its generic async `call()`, so they are dropped, delayed and partitioned like Raft messages.
  Clients are network endpoints that can be partitioned along with servers.
- **Always-on checks:** Election Safety after every event; State Machine Safety and in-order
  apply on every apply.
- **Sweep results.** All KV tests pass at 500 seeds (3,008 runs).

## 4. Testing summary
See §1.6 (storage), §2.3–2.5 (Raft), and §3 (KV). Phase 4 adds the linearizability checker
and the chaos harness.

## 5. Known limitations
- **No PreVote and no CheckQuorum.**
  - A node that was partitioned away keeps timing out and incrementing its term. When it
    rejoins, its higher term forces the current leader to step down and an election to run,
    even though the cluster was healthy.
  - This is safe: the rejoining node cannot win unless its log is up to date. But it costs
    availability: a few hundred milliseconds with no leader.
  - In the other direction, without CheckQuorum a leader that loses its majority keeps
    believing it leads. That is also safe: it cannot commit writes, and ReadIndex refuses to
    serve reads without a confirming majority. Clients talking to it see timeouts until they
    try another node.
  - PreVote (Raft thesis §9.6) would fix the first issue, CheckQuorum (§6.2) the second.
- **Session rows are never evicted.** Every client that ever wrote keeps an 18-byte row
  forever. Long-lived deployments with many short-lived clients would need expiry, and expiry
  must be decided *in the log* (for example a leader-proposed "expire sessions older than log
  time T" entry), so that every replica evicts at the same point. Evicting by local wall-clock
  time would break determinism.
- **One outstanding request per client.** A client that wants concurrency uses several client
  ids.
- **Each read costs a heartbeat round to a majority** (no leader leases). A lease would save a
  round trip but would depend on bounded clock drift.
- **Deletes don't reclaim B-Tree space** (§1.3).

## To be written
- The linearizability checker and chaos testing (Phase 4), the gRPC transport and production
  threading model (Phase 5), snapshots (Phase 6), and performance (Phase 7).
