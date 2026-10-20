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

## To be written
- Raft (Phase 2), the KV state machine and ReadIndex (Phase 3), the linearizability checker and
  chaos testing (Phase 4), the transport and threading model (Phase 5), snapshots (Phase 6),
  and performance (Phase 7).
