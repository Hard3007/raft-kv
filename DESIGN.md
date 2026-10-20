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
- **PreVote (thesis §9.6), added after Phase 5.** When its election timer fires, a node first
  asks "would you vote for me in term currentTerm + 1?" (`RequestVoteRequest.pre_vote`).
  - It changes neither its own term nor its vote. Receivers change nothing either: no term
    update, no vote, no timer reset, nothing to persist.
  - A peer grants a PreVote only if three things hold: that term is newer than its own, the
    candidate's log is at least as up-to-date (§5.4.1), and it has **not heard from a current
    leader within the minimum election timeout** (`heardFromLeaderRecently`).
  - Only after a majority of PreVotes does the node become a candidate and run Figure 2's real
    election.
  - Effect: a node that is cut off can no longer inflate its term while it is away, and cannot
    depose a healthy leader when it returns. Tests: `RaftPreVoteTest`, which fails 3/3 seeds
    with PreVote off.
- **CheckQuorum (thesis §6.2), added after Phase 5.** A leader that has not heard any reply
  from a majority (counting itself) within the maximum election timeout steps down. It checks
  at every heartbeat (`heardFromMajorityRecently`).
  - Before this, a partitioned leader kept believing it led forever. That was safe (it cannot
    commit), but clients stuck with it saw only timeouts. Test:
    `RaftPreVoteTest.anIsolatedLeaderStepsDown`, which fails 3/3 seeds with CheckQuorum off.
  - **ReadIndex does not rely on CheckQuorum.** It still confirms leadership with a fresh
    heartbeat round for every read. CheckQuorum depends on timeouts, so a deposed leader can
    still be unaware for up to about an election timeout. The ReadIndex tests in `KvClusterTest`
    therefore run with CheckQuorum off, to keep that dangerous window open indefinitely.
- **Both are on by default** (`RaftConfig.preVote`, `checkQuorum`). Tests that drive Figure 2's
  election rules by hand (`RaftNodeTest`, `RaftReadIndexTest`) and the hand-built Figure 8
  scenario turn PreVote off, because they need one exact sequence of elections. The randomized
  lab-style and chaos tests run with both on.
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
| Beyond Figure 2: PreVote (thesis §9.6) | `startPreVote`, `handlePreVote`, `onPreVoteReply`, `heardFromLeaderRecently` | `RaftPreVoteTest` (receiver rules, partitioned follower) |
| Beyond Figure 2: CheckQuorum (thesis §6.2) | heartbeat timer, `heardFromMajorityRecently` | `RaftPreVoteTest.anIsolatedLeaderStepsDown` |
| Figure 13: InstallSnapshot receiver, steps 1-8 (`[Fig13 n]`) | `handleInstallSnapshot`, `receiveSnapshotChunk`, `installSnapshot`; `SnapshotStore` | `KvSnapshotTest.*`, `SnapshotStoreTest.*` (§2.6) |
| Figure 13: leader sends chunks; snapshot before compaction | `sendSnapshot`, `sendSnapshotChunk`, `onInstallSnapshotReply`, `maybeTakeSnapshot` | `KvSnapshotTest.laggingFollowerCatchesUpThroughInstallSnapshot`, `crashAtEveryIoStepOfSnapshotCreation` |

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

### 2.6 Snapshots and log compaction (Figure 13)
**What a snapshot is.** The state machine's whole state after applying entry N (for the KV store:
every B-Tree row, data and sessions), plus N and its term. `SnapshotStore` keeps the latest one
in one file:
`magic | version | lastIncludedIndex | lastIncludedTerm | length | crc32 | data`.

**Taking one** (`RaftNode.maybeTakeSnapshot`, after applying): once `snapshotThreshold` entries
(production: 10,000) have been applied since the last snapshot, three steps run **in this
order**:
1. **Save the snapshot durably:** write `raft.snapshot.tmp`, fsync, rename over
   `raft.snapshot` (the directory is fsynced too).
2. **Only then drop the prefix in memory:** `RaftLog` now starts after N and remembers only N and
   its term, which is all AppendEntries' consistency check needs there.
3. **Rewrite the WAL** (`Wal.rewrite`: temporary file, fsync, rename). The new file holds one
   record: the compaction point, the term and vote, and the entries after N. **So the WAL file
   stops growing**: `SnapshotStoreTest.compactionRewritesTheWalSoItStopsGrowing` shrinks it more
   than 10×.

A crash between steps 1 and 3 leaves an uncompacted log next to the snapshot, and restart
finishes the job. The other order could lose committed entries.
- **Mutation `compact-before-snapshot`** (steps 2–3 before step 1):
  - `KvSnapshotTest.crashAtEveryIoStepOfSnapshotCreation` fails with `log compacted through 10
    but the state only covers 1: committed entries are lost`.
  - Chaos catches it in **82/500 seeds**, thanks to crashes at random I/O operations (§4.8).

**Sending one** (leader, `sendSnapshot`). If a follower's `nextIndex` is at or below the leader's
snapshot point, the entries it needs are gone, so the leader sends the snapshot file instead.
- **Chunks** of `snapshotChunkBytes`: 256 KB in production, well under gRPC's 4 MB message
  limit; tests and chaos use 64–512 B to force multi-chunk transfers.
- **One chunk in flight.** If its reply has not arrived within two heartbeats, it is resent.
- **The bytes come from memory**: an immutable copy of the snapshot file, so a newer snapshot
  taken mid-transfer cannot corrupt it.
- **matchIndex is never set from an InstallSnapshot reply.** Figure 13's reply carries only the
  term, so it does not prove the follower installed anything (the transfer may have been damaged).
  The leader only moves `nextIndex` past the snapshot; the next AppendEntries' consistency check
  establishes `matchIndex`. If the install failed, that check fails and the transfer starts over.

**Receiving one** (follower, `handleInstallSnapshot`, Figure 13 receiver steps 1–8, tagged
`[Fig13 n]` in the code):
1. Reply at once if term < currentTerm. From the current leader, reset the election timer.
   Persist any term change before replying.
2. Ignore snapshots that cover nothing new (index ≤ commitIndex).
3. Write chunks to `raft.snapshot.recv`, in order only: offset 0 starts a new file, and a
   duplicate or a gap is ignored.
4. On the last chunk: fsync, **read the file back and check its CRC**, then rename it over the
   snapshot. A damaged transfer is discarded and the old snapshot stays.
5. If the log has an entry matching (lastIncludedIndex, lastIncludedTerm), keep the entries
   after it. Otherwise discard the whole log.
6. Rebuild the state machine from the snapshot (durable on return: §3.7), then rewrite the WAL.

**AppendEntries after compaction.** A follower whose log starts after its snapshot may get
AppendEntries with `prevLogIndex` below its snapshot point (a delayed message). Everything up to
the snapshot point is committed, so it matches the leader's log; the follower skips that part
and checks consistency at its snapshot point.

**Restart** (`RaftNode` constructor):
1. Load the snapshot.
2. If the snapshot is newer than the log's compaction point (we crashed between saving and
   compacting), compact or reset the log by the rules above and rewrite the WAL.
3. If the state machine is behind the snapshot (its lazily synced B-Tree lost a tail),
   restore it from the snapshot.
4. Replay only the entries after `max(snapshot index, state machine's applied index)`.
5. If the log was compacted past both the snapshot and the state, refuse to start: committed
   entries are gone.

| Failure scenario | Handling | Test |
|---|---|---|
| Crash at each of save()'s I/O steps | old snapshot or new, never a mix | `SnapshotStoreTest.crashAtEveryStepOfSaveLeavesTheOldOrTheNewSnapshot` |
| A chunk lost in transit | CRC check fails; transfer discarded; leader retries | `SnapshotStoreTest.receivedChunksAreCheckedBeforeTheyReplaceTheSnapshot` |
| Crash at each of the 14 I/O steps around taking a snapshot | every acknowledged write survives; the in-flight one completes once | `KvSnapshotTest.crashAtEveryIoStepOfSnapshotCreation` |
| Crash at each of the 113 I/O steps of receiving and installing a snapshot | the follower restarts, catches up, ends identical to the leader | `KvSnapshotTest.crashAtEveryIoStepOfSnapshotInstallation` |
| Crash at each step of a B-Tree restore | entirely old state or entirely new | `KvSnapshotTest.crashAtEveryStepOfRestoreLeavesTheOldOrTheNewState` |
| A follower misses entries the leader has compacted away | multi-chunk InstallSnapshot, then AppendEntries | `KvSnapshotTest.laggingFollowerCatchesUpThroughInstallSnapshot` |
| Restart after power loss | starts at the snapshot, replays only later entries | `KvSnapshotTest.restartStartsFromTheSnapshotAndReplaysOnlyLaterEntries` |
| Snapshots over real gRPC with crashes | LINEARIZABLE; snapshots taken | `GrpcClusterTest.chaosOnRealThreadsIsLinearizable` (threshold 50, 512-byte chunks) |

**Bug found while writing these tests (and fixed).** A single-node cluster committed and applied
an entry *inside* `propose()`, before `KvService` had recorded the proposal. So the client never
got its reply, and every write was retried, and committed again, until the client gave up.
Multi-node clusters never hit it, because commit waits for a network round trip. Now a
single-node commit runs on the next task. Regression test:
`KvClusterTest.singleNodeClusterAnswersEachWriteOnce`, which fails without the fix.

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
- **Never evicted.** Session rows are never removed (see §6).

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

### 3.7 KV snapshots and restore (`KvStateMachine`)
- **Format.** `takeSnapshot()` writes every row in key order:
  `count | (keyLength | key | valueLength | value)*`. It includes the session rows, so
  exactly-once survives a snapshot install: `snapshotRoundTripsEveryRowIncludingSessions` checks
  that a retried write is still recognized as a duplicate.
- **`restoreSnapshot` never modifies the live tree.**
  1. Build generation g+1 (`kv-<g+1>.db/.wal`) from the snapshot, in batches, ending with
     `setAppliedIndex(N)`.
  2. Checkpoint and close it, so it is complete and durable on its own.
  3. Atomically switch the pointer file `kv.current` to g+1 (temporary file, fsync, rename).
  4. Reopen the tree and delete generation g.
- **On open**, leftovers of an interrupted restore (g+1) or cleanup (g−1) are deleted. A crash at
  any step leaves the pointer on a complete tree.

## 4. Linearizability checking and chaos testing (`linearizability/`, `chaos/`)

### 4.1 The checker (`LinearizabilityChecker`)
- **Algorithm.** Wing-Gong-Lowe search with Lowe's memoization, as in the Porcupine checker.
  - Call and return events of one key go into a doubly linked list in time order.
  - At a call, the search tries to linearize that operation now. If the model accepts the
    observed result, the operation's call and return are unlinked, the choice is pushed, and
    the search restarts at the front.
  - At a return, an already-returned operation was not placed in time, so the last choice is
    undone.
  - An empty list means linearizable. Hitting a return with nothing to undo means not
    linearizable.
  - Memoization: configurations of (set of linearized operations, model state) are recorded,
    and repeats are skipped.
- **Per key.** Linearizability is compositional (Herlihy and Wing), and every operation touches
  one key. So each key's sub-history is checked on its own, which shrinks the search
  exponentially.
- **Three verdicts, never two.** `LINEARIZABLE`, `NOT_LINEARIZABLE`, and `INCONCLUSIVE` (the
  wall-clock budget ran out). INCONCLUSIVE never counts as a pass. The chaos report counts it
  separately, and the chaos task fails the build on it.
- **Pending writes** (unknown outcome) have return time = ∞, and their results are not checked.
  - With return = ∞, they never force a backtrack, so they may take effect at any point after
    invocation.
  - Placing one at the very end is the same as "never took effect".
  - They still take effect at most once. The self-test `pendingWriteTakesEffectAtMostOnce`
    shows the checker rejects a history that needs a pending write twice.
- **Equal timestamps count as concurrent** (calls sort before returns). That can only permit
  more orderings, never invent a violation.
- **Witness** (`Witness.earliestViolation`). For a failing history, a binary search finds the
  earliest moment T at which the history *as it looked at T* was already broken.
  - At the cut, still-running writes become pending and still-running reads are dropped.
  - Cutting is sound: if the full history is linearizable, every cut is too.
  - The last operations before T tell the story (examples in §4.5).

### 4.2 Validating the checker itself
- **Hand-written histories (15 tests).** Basics, stale reads, reads that go backwards, delete
  `existed` flags, ties, per-key reporting, each pending-write rule, and a search that must time
  out (24 concurrent puts plus an impossible read) and return INCONCLUSIVE. Each history is
  checked by both checkers.
- **Differential testing (`DifferentialCheckerTest`).** A brute-force checker
  (`BruteForceChecker`, test code) tries every ordering that respects real time, over the
  *whole* multi-key history. It shares no logic with the real checker: no per-key split, no
  event list, no memo. Both run on 15,000 random histories (up to 8 operations, 1–2 keys, ties,
  pending writes) from three generators, and must agree on every one:

  | Generator | Linearizable | Not |
  |---|---|---|
  | valid by construction (ops run sequentially at random points inside their intervals) | 5,000 | 0 |
  | one result corrupted | 3,398 | 1,602 |
  | random results | 1,357 | 3,643 |
  | **total** | **9,755** | **5,245** |

- **The differential test has teeth.** Each of these breaks of the real checker made it fail:
  - sorting returns before calls on ties;
  - backtracking only one level;
  - ignoring pending writes.

### 4.3 History rules (`History`, `ChaosRun`)
- **Invoke** is recorded just before the client sends; **return** when the client accepts the
  reply. Both use the simulation clock.
  - Each timestamp is `virtual ms × 10⁶ + event counter`.
  - The simulation is single-threaded, so this is the exact real-time order, even within one
    millisecond.
- **Writes** whose result is `Unknown` stay **pending forever**.
- **Reads** without a definite answer (`Unknown`; the client never surfaces `NOT_LEADER`) are
  **left out**, since reads have no side effects.
- **Unique values.** Every put writes a unique value (`c<client>-<n>`), so each read names the
  write it observed.

### 4.4 The chaos harness
- **One run (`ChaosRun`, one seed):** a 5-node `SimKvCluster`, with 5 concurrent clients doing
  60 random operations each (50% get, 40% put, 10% delete) on **3 shared keys**. Think time is
  0–300 ms. Client attempts time out after 1 s, and clients give up (Unknown) after 4 s.
- **Nemesis.** A random fault action every 100–400 ms of virtual time:
  - crash a node (power loss; at most two down at once);
  - restart a crashed node;
  - isolate the leader;
  - split off a random minority of 1–2 nodes;
  - heal partitions;
  - toggle 10% message loss;
  - toggle long delays (about 1 in 10 messages delayed 0.2–2 s, which makes clients retry while
    old copies of their requests are still in flight).

  Each partition puts every client on a random side, so clients get stuck with deposed leaders
  too. At the end everything is healed and restarted.
- **Determinism.** Same seed, same run, byte for byte. Runs execute two at a time in parallel,
  and the counts reproduce exactly. For example, seed 17 of the ReadIndex mutation fails the same
  way alone and in the 500-seed batch.
- **Reports.** `./gradlew chaos -Pruns=N` writes `reports/chaos/chaos-<label>-<N>-seeds.md`
  (command, commit, machine, config, verdict counts, per-seed min/mean/max of operations and
  every fault type, wall time) plus a per-seed `.csv`. It exits non-zero unless every run is
  LINEARIZABLE. `-Pruns=1 -PstartSeed=S -Pverbose` replays one seed with its fault log and
  witness.

### 4.5 Results
The numbers in this section are from Phase 4 (commit `39e1934`). Those report files were later
regenerated for Phase 6 (§4.8); the Phase 4 versions remain in git history (commit `1fd789c`).

**Baseline:** `reports/chaos/chaos-baseline-500-seeds.md`, commit `39e1934`.
- **500 / 500 LINEARIZABLE**, 0 NOT_LINEARIZABLE, 0 INCONCLUSIVE, 14.6 s wall time on the
  2-core Codespace.
- Per seed: 300 operations; 14–49 crashes (mean 27.5); 37–144 partitions (mean 82.9); 10–37
  message-loss periods; 156–774 messages lost; 10–40 long-delay periods; 45–125 s of virtual
  time.

**Mutations** (`scripts/chaos-mutation.sh <name> 500`; each report includes the exact diff):

| Mutation | Seeds caught (NOT_LINEARIZABLE) | INCONCLUSIVE |
|---|---|---|
| Reads served without the ReadIndex confirmation round | **34 / 500** (6.8%) | 0 |
| No session dedup (every copy of a write applies) | **296 / 500** (59.2%) | 0 |
| Followers serve reads from their own state machine | **391 / 500** (78.2%) | 0 |

**Example witnesses** (`-Pverbose` replays; times are `ms#event`):
- **Reads without confirmation, seed 17.** A delete of `k0` returns `existed=true` at
  13769ms. A get invoked at 13788ms, after that delete returned, still reads the deleted value
  `c3-6`. A leader that had been deposed answered from its stale state.
- **No dedup, seed 2.** A client reads `c3-30` from `k1`, then deletes `k1` and is told
  `existed=false`. Its delete was applied twice: the first copy removed the value, and the
  retry was re-executed instead of answered from the session cache.

**Why the ReadIndex mutation is caught least often.** It takes four things at once:
1. the leader is isolated *with* a client on its side (each client has a 1/3 chance);
2. the majority side completes a write to the same key;
3. that stranded client then reads that key;
4. all of this happens before the next fault action reshapes the partition, 100–400 ms later.

Follower reads are caught far more often, because any lagging follower can serve a stale value
at any time. All three bugs are caught many times in 500 seeds. Higher sensitivity for the
ReadIndex case would need longer-lived leader isolations. That is a possible tuning, but we have
not done it, so the published numbers come from the configuration above.

### 4.6 Bugs the chaos harness found
- **`KvClient` completed an operation twice.**
  - The sequence: an attempt timed out *after* the overall deadline, so the client finished the
    operation as Unknown, without retiring that attempt's number. The attempt's late reply then
    completed the operation a second time.
  - The fix: `finish()` retires the current attempt.
  - Regression test: `KvClientTest.aLateReplyAfterGivingUpDoesNotCompleteTheOperationTwice`. It
    fails without the fix (2 completions instead of 1).
- **The first fault schedule was too weak.** With faults every 200–1000 ms and little think
  time, runs ended in about 12 s of virtual time, and some seeds saw zero crashes or partitions.
  The schedule was densified until every seed sees at least 14 crashes and 37 partitions.

### 4.8 Chaos after PreVote, CheckQuorum and snapshots
**What changed in the harness:**
- **Snapshots in every run.** A snapshot every 20 entries, in 64-byte InstallSnapshot chunks, so
  snapshotting, multi-chunk transfers and compaction happen constantly.
- **Crash at a random upcoming I/O operation** (new fault). The nemesis arms a node's disk to
  fail at one of its next 0–30 writes, fsyncs or renames. The crash then lands *inside* an
  operation: mid-append, mid-snapshot, mid-install. The harness catches it and power-cycles that
  node. Before this, crashes only fell between simulated events, so chaos could not catch the
  compact-before-snapshot bug at all.
- **Pause** (new fault). A node is frozen for 0.5–2 s, like a GC pause or `docker pause`: its
  timers are held, and so are messages for it. On resume, the held messages arrive in random
  order, because nothing orders one connection relative to another, and overdue timers fire
  5–25 ms later.
- **Internal-check failures are reported per seed** instead of aborting the run. Examples: a
  safety assertion, or a node refusing to start because committed entries are lost. Such runs
  never pass.
- **`-PcheckQuorum=false`** runs the same chaos without CheckQuorum.

**Results** on commit `61dda62` (`reports/chaos/`), 500 seeds each:

| Run | LINEARIZABLE | Caught | INCONCLUSIVE |
|---|---|---|---|
| **Baseline** (`./gradlew chaos -Pruns=500`) | **500** | 0 | 0 |
| **Baseline, CheckQuorum off** | **500** | 0 | 0 |
| Mutation: reads without ReadIndex confirmation | 498 | 2 | 0 |
| Same mutation, CheckQuorum off | 450 | **50** | 0 |
| Mutation: no session dedup | 263 | **236** | 1 |
| Mutation: followers serve reads | 50 | **446** | 4 |
| Mutation: compact before snapshot | 418 | **82** (internal failure) | 0 |

**Per seed in the baseline:**
- 13–49 crashes, of which 4–31 were mid-I/O;
- 22–39 snapshots taken and **6–19 InstallSnapshots completed** (no seed had zero, so no extra
  pressure was needed);
- final term 24–108.

**Why the ReadIndex mutation is now caught so rarely** (2/500, against 34/500 in Phase 4):
- The bug only shows when a *deposed leader that does not know it* serves a read that starts
  after a newer write finished elsewhere.
- CheckQuorum now makes an isolated leader step down within about 300 ms.
- A paused leader cannot be caught either. A client stuck on it is blocked: one operation at a
  time, and it is waiting in the frozen node's buffer. So its only read was invoked before the
  pause, it is concurrent with the newer write, and returning the old value is linearizable.
- That is a real reduction in exposure, not a weaker checker. ReadIndex must be correct without
  CheckQuorum's timing, and with CheckQuorum off the same mutation is caught in **50/500**. The
  deterministic `KvClusterTest.deposedLeaderNeverServesAStaleRead` (CheckQuorum off) catches it
  every time.

**10,000-seed sweep** (`reports/chaos/chaos-baseline-10000-seeds.md`, commit `43a3adb`, 1 m 38 s on the
2-core Codespace): **10,000 / 10,000 LINEARIZABLE**, 0 NOT_LINEARIZABLE, 0 INCONCLUSIVE, 0 internal-check
failures. Every seed had at least 11 crashes (2 mid-I/O), 6 pauses, 16 partitions, 19 snapshots and 4
InstallSnapshots.

**Docker chaos after Phase 6: a real violation, now explained and fixed** (§5.8). The first 180 s
run on commit `43a3adb` was NOT_LINEARIZABLE. It was reproduced on `920f53e` and `d63b882` (with
witness and node logs) and traced to replies being attributed to the wrong node. The simulator now
injects that fault (the "stale route", above) in every run.

**INCONCLUSIVE runs.** 5 in total, all in mutation suites (broken builds produce histories that
are expensive to refute), none in either baseline. They never count as passes. The fix, if
needed, is shorter histories for those suites, not a longer timeout.

### 4.7 Testing summary
- Storage: §1.6.
- Raft: §2.3–2.5.
- KV: §3.
- Checker: §4.2. Chaos: §4.5.
- CI runs `./gradlew build` (all unit and simulation tests) and `./gradlew chaos -Pruns=50` on
  every push.

## 5. Production runtime: threads, gRPC, Docker (`server/`, `common/`, Docker files)

### 5.1 Threading model
- **One thread per node.** Each node has exactly one `ExecutorScheduler` thread, which owns all
  Raft and KV state.
  - Everything is *posted* onto it: gRPC request handlers (`RaftGrpcService`, `KvGrpcService`),
    gRPC reply callbacks (`GrpcRaftTransport`), timers, and `/status` reads.
  - So `RaftNode`, `KvService` and `KvStateMachine` need no locks, and the code is exactly the
    code that runs single-threaded in the simulator.
- **Thread confinement is asserted, not assumed.** `Scheduler.checkThread()` throws unless
  called on the owning thread. It is called at every `RaftNode` entry point (12: start, stop,
  propose, readIndex, both RPC handlers, both reply callbacks, the election timer, the heartbeat
  timer, the ReadIndex round, status), at `KvService.get/put/delete`, and at `KvClient`'s entry
  points. The simulator's scheduler enforces the same rule (the thread that created the
  simulation). The checks are always on, in tests and in production.
- **Failures are counted, so they are loud.**
  - `ExecutorScheduler` counts tasks that threw. `FailureCountingInterceptor` counts gRPC
    handlers that threw: gRPC would otherwise turn an exception on its own thread into an error
    reply that the client quietly retries.
  - `RaftKvNode.failedTasks()` sums both, and the tests require 0.
  - **Shown to work.** I made `KvGrpcService.get` call the service on the gRPC thread instead
    of posting. The in-process chaos test failed with `a task on node 1 threw ==> expected: <0>
    but was: <4>`.
  - The checker alone would **not** have caught that bug: the failed reads timed out, were left
    out of the history, and the run was still LINEARIZABLE. The confinement assertions catch a
    class of bug that linearizability checking cannot see.
- **`ExecutorScheduler.close()` waits for the thread to stop.** A crashed-and-restarted node
  can never have two incarnations writing the same files.

### 5.2 gRPC rules
- **Async stubs only.** A send returns immediately, and the node thread never blocks on the
  network. The one blocking call in the system is on *client* threads, in `BlockingKvClient`.
- **A deadline on every RPC:** 300–500 ms for Raft RPCs (`rpcTimeoutMs`), and the client's
  attempt timeout for KV calls.
  - It is set per call on a fresh stub (stubs are cheap) over a shared channel.
  - A failed or expired call produces no callback: Raft retries by itself (heartbeats resend,
    candidates time out), and `KvClient`'s attempt timer moves on.
- **One channel per peer**, created on first use and reused for every call. gRPC multiplexes
  calls over HTTP/2 and reconnects by itself.
- **One port per node** serves both `RaftService` and `KvService`. `/status` is a separate HTTP
  port (JDK `HttpServer`), and it reads the state on the node thread.

### 5.3 The gate: 5 real nodes, real threads, real gRPC (`GrpcClusterTest`)
- **Setup.** Five `RaftKvNode`s in one JVM on localhost, each with its own executor thread and
  its own data directory on real disk. Four client threads use `BlockingKvClient`.
- **Faults** (one every 300–700 ms):
  - crash a node (gRPC server killed, executor stopped, files not closed) and restart it from
    its data directory;
  - isolate a node (both directions, filtered in the gRPC layer);
  - kill all live connections of a node (in-flight calls fail, and the next call reconnects).
- **Histories use `System.nanoTime`**; equal readings count as concurrent.
- **Result:** LINEARIZABLE. The run before the gate commit saw 600 operations (597 completed),
  4 crashes, 6 isolations and 6 connection drops, with 0 failed tasks. The test asserts every
  fault type happened, that more than 200 operations completed, and that no node task failed.
- **What it can catch that the simulator cannot:** races between gRPC threads and node threads,
  missing hand-offs, executor lifecycle bugs, and real socket behavior.

### 5.4 Docker image (`Dockerfile`)
- **Build stage on `$BUILDPLATFORM`.** Gradle, javac and protoc run **once, natively**, even
  for arm64. Java bytecode and the gRPC jars (which bundle native transports for both
  architectures) are architecture-independent.
- **Runtime stage per architecture** (`eclipse-temurin:21-jre`) **only copies files**: no
  `RUN`, so nothing executes under QEMU. The multi-arch build log confirms 10 build-stage steps
  for amd64, 0 for arm64, and 0 `RUN` steps on arm64.
- **Measured build times** on the 2-core Codespace:
  - first multi-arch build (amd64 + arm64), cold caches: **4 m 23 s** (Gradle inside: 3 m 6 s);
  - rebuild after a source change, warm Gradle cache mount: **38–48 s**.
- **Base images are pinned by multi-arch digest**, so rebuilds are reproducible.
- **Runtime details.**
  - It runs as uid 1000. `/data` is a volume owned by that user, copied from the build stage so
    the final stage needs no `RUN`.
  - The health check is `curl -fsS http://localhost:8080/status` (curl ships in the base image).
  - The JVM gets `-Xmx256m` plus caps on metaspace (64 MB), direct memory (32 MB, used by
    Netty) and the code cache (32 MB). `SerialGC` suits a small heap on 2 cores, and the
    process exits on OOM rather than limping along.
- **`docker stop`** sends SIGTERM to the JVM (PID 1, through `exec` in the start script). The
  shutdown hook checkpoints the B-Tree and closes cleanly. **`docker kill`** is SIGKILL.

### 5.5 The 5-node cluster (`docker-compose.yml`)
- **Per node:** a 384 MB memory limit, a named volume `nodeN-data:/data`, a health check, and
  `restart: unless-stopped`.
- **Ports.** gRPC 7001–7005 and `/status` 8081–8085 are published on the host.
  `CLUSTER=1=node1:7000,...` uses compose DNS names between containers. Leader hints carry node
  ids, so host-side clients map them to their own `localhost` addresses.
- **Memory, measured with `docker stats`:**
  - about 77–90 MiB per container idle;
  - a peak of **103–132 MiB per container** during the 180 s chaos run (limit 384 MiB);
  - 5 containers at peak use under 600 MiB in total;
  - with the cluster, Gradle and the IDE all running, the Codespace still had about 2.7 GB
    available.
- **Recovery from the volume, observed.** After `docker kill` and restart, node logs show
  e.g. `replayed 818 WAL records ... event=started ... applied_index=818`: the node resumed from
  its own disk, not from an empty state.

### 5.6 Docker-level chaos (`scripts/docker-chaos.sh`, `DockerChaosMain`)
- **Setup.** Four client threads on the host run the same `BlockingWorkload` as the in-process
  test against the published ports. A fault fires every 1–3 s, each undone 2–5 s later, with at
  most two nodes faulty at once.
- **The three faults:**
  - **`docker kill`** (SIGKILL), then `docker start`: a process crash; the node recovers from
    its volume.
  - **`docker pause` / `unpause`**: freezes the process, like a very long GC pause. A paused
    leader wakes up deposed without knowing it: exactly the case ReadIndex protects against.
  - **`docker network disconnect` / `connect`**: cuts the node off from peers *and* clients
    (published ports go through that network).
- **Result** (`reports/chaos/docker-chaos-180s-phase5.md`, commit `3efea60`): **LINEARIZABLE**.
  6,455 operations (6,430 completed, 13 pending writes, 12 reads left out), 19 kills, 26
  pauses, 10 disconnects, peak 132 MiB per container. The check took 138 ms.
- **Honesty about what `docker kill` tests.** `docker kill` is a **process crash, not a power
  loss**. The host keeps running, so its page cache, holding writes the process made but never
  fsynced, survives and reaches the disk later. The B-Tree's lazily synced tail therefore always
  survives a `docker kill`; the 818 replayed records above include un-fsynced ones.
  - Power loss, where un-fsynced writes are lost, torn or reordered, is covered by the
    simulator: `SimulatedFileSystem.simulatePowerLoss` in the storage recovery tests and in
    every simulated crash in the Raft, KV and chaos suites (§1.6, §2, §4).
  - Testing real power loss would need a VM or a fault-injecting block device.

### 5.7 PreVote under Docker chaos: the term, before and after
Without PreVote, every paused or disconnected node keeps timing out and incrementing its term,
and each rejoin with a higher term forces a new election. The highest term across the nodes
after a 60 s Docker chaos run (`scripts/docker-chaos.sh 60`; random faults, so counts vary):

| Build | Run 1 | Run 2 | Completed ops (run 1 / run 2) | Verdict |
|---|---|---|---|---|
| Before PreVote (commit `3efea60` server) | 193 | 152 | 1,886 / 3,542 | LINEARIZABLE, both |
| With PreVote + CheckQuorum | **19** | **12** | 3,831 / 3,022 | LINEARIZABLE, both |

An earlier run before PreVote had reached 236. With PreVote, the term only moves when an
election is actually needed (a killed, paused or disconnected leader), so about 10× fewer
elections happen in the same fault schedule.

### 5.8 The bug Docker chaos found: replies counted for the wrong node
**Final evidence after the fix** (all from commit `c23409c`, `reports/final-evidence-c23409c.md`): the Raft gate
(1,285 runs), 10,000/10,000 simulated chaos runs LINEARIZABLE, and five 180 s Docker chaos runs, all
LINEARIZABLE, with 0 phantom votes in 105 elections (14 of 29 before the fix).

**Symptom.** A 180 s Docker chaos run was NOT_LINEARIZABLE. The witness
(`reports/chaos/docker-chaos-180s-920f53e-20261020T102536Z-violation.txt`) ends with a read of
`k2` returning `c0-410`, a value overwritten many times by writes that had completed seconds
earlier: a node served a state far in the past. The node logs showed the mechanism:
- node 1 later recovered with applied index 879;
- but node 5 had won term 6 with `last_log_index=800`.

So a leader was elected without entries that were committed: Leader Completeness was violated.

**Diagnosis.** I added an INFO trace for every vote granted and logged the votes each new leader
had counted. The next reproduction (commit `d63b882`) showed that in 14 of 29 elections, the
winner had counted a vote from a node that never granted one. In term 6, for example, node 1
counted node 4, while the node that actually voted was node 5.

**Root cause.**
1. Raft replies did not say who sent them. The sender attributed each reply to the peer it had
   *addressed*.
2. `docker network disconnect`/`connect` gives a container a new IP address, and its old address
   can later go to another container.
3. A peer's cached gRPC connection to `nodeX:7000` could therefore reach a *different* node,
   whose vote or acknowledgement was then counted twice.

The result: elections won without a real majority of votes, and entries committed without a real
majority of copies.
- Only Docker hits this, because it reuses addresses.
- The simulator and the localhost gRPC test never reuse addresses, which is why 10,000 simulated
  seeds and the real-thread tests stayed clean.

**Regression test (written before the fix).**
`RaftScenarioTest.aReplyFromTheWrongNodeIsNeverCountedAsTheAddressedPeer`.
- **Setup.** `SimNetwork.misdeliver(a, b)` makes traffic for node A reach node B. The leader can
  then reach only B, plus "A" (which is really B): 2 of 5 nodes.
- **On the old code** it failed 3/3 seeds with `committed with real acknowledgements from only 2
  of 5 nodes`. Without that assertion, the harness then reports `State Machine Safety violated at
  index 3`, which is the Docker symptom.

**Fix.** Every reply carries `responder_id`, and every reply handler (votes, PreVotes,
AppendEntries, InstallSnapshot) first drops a reply whose responder is not the peer it asked,
before looking at anything else in it (even the term). A vote or acknowledgement now counts only
for the node that actually gave it, whatever route it took.

**Coverage from now on.**
- Chaos injects a "stale route" fault in every run (§4.8).
- The mutation `accept-misattributed-replies` (`scripts/chaos-mutation.sh`) removes the checks.
  It is caught in **191/500** chaos seeds as an internal failure (a safety assertion).

**Lesson.** A protocol that identifies peers by "the connection I used" rather than by what the
message says it is trusts the network's addressing. Real networks, especially container
networks, do not guarantee it.

## 6. Known limitations
- **PreVote and CheckQuorum are implemented** (§2.2), so the earlier "no PreVote" limitation is
  gone. What remains:
  - **No leadership transfer.** A healthy leader cannot hand off before maintenance; it has to
    be stopped, which costs an election timeout of unavailability. With PreVote, the
    replacement still needs a PreVote majority first: peers grant it only once they have not
    heard from a leader for the minimum election timeout.
  - **CheckQuorum is timer-based.** A deposed leader can stay unaware for up to about an
    election timeout. That is safe because ReadIndex never trusts it (§2.2), but clients may
    wait that long before being redirected.
- **Simulated partitions in the in-process test are filtered in the gRPC layer**, not at the
  network. Docker chaos uses real network disconnects.
- **`docker kill` is a process crash, not a power loss** (§5.6); power loss is covered only by
  the simulator.
- **Session rows are never evicted.** Every client that ever wrote keeps an 18-byte row
  forever. Long-lived deployments with many short-lived clients would need expiry, and expiry
  must be decided *in the log* (for example a leader-proposed "expire sessions older than log
  time T" entry), so that every replica evicts at the same point. Evicting by local wall-clock
  time would break determinism.
- **One outstanding request per client.** A client that wants concurrency uses several client
  ids.
- **Each read costs a heartbeat round to a majority** (no leader leases). A lease would save a
  round trip but would depend on bounded clock drift.
- **Snapshots are taken synchronously on the node thread, and held in memory.** Serializing the state
  blocks the node for that long, and a leader keeps the latest snapshot file in memory for sending.
  That is fine for this store's small states; large states would need copy-on-write snapshots and
  streaming the file from disk.
- **The leader learns nothing from an InstallSnapshot reply** (Figure 13 has no success flag), so a
  damaged transfer costs one failed AppendEntries before it restarts.
- **Deletes don't reclaim B-Tree space** (§1.3).

## To be written
- Performance (Phase 7).
