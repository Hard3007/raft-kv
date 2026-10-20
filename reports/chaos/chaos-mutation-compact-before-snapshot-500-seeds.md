# Chaos report: mutation-compact-before-snapshot

- **Command:** `./gradlew chaos -Pruns=500 -Plabel=mutation-compact-before-snapshot`
- **Date:** 2026-10-20T09:52:42.163647321Z
- **Git commit:** 61dda62 (with uncommitted changes)
- **Machine:** Linux 6.8.0-1064-azure amd64, 2 cores (AMD EPYC 7763 64-Core Processor), Java 21, max heap 1024 MB
- **Config:** 5 nodes, 5 clients, 3 keys, 60 ops per client, think time 0-300 ms, a fault action every 100-400 ms (virtual), client attempt timeout 1000 ms / give-up 4000 ms, checker timeout 10 s (wall); PreVote on, CheckQuorum on, a snapshot every 20 entries in 64-byte InstallSnapshot chunks
- **Seeds:** 1..500 (500 runs)
- **Total wall time:** 13.3 s

## Verdicts

| Verdict | Runs |
|---|---|
| LINEARIZABLE | 418 |
| NOT_LINEARIZABLE | 0 |
| INCONCLUSIVE | 0 |
| runs where the system failed an internal check | 82 |

Only LINEARIZABLE runs count as passing. INCONCLUSIVE means the checker ran out of time. An internal-check failure (a safety assertion, or a node refusing to start because committed entries were lost) has no verdict and does not pass either.

## Per seed (min / mean / max)

| Measure | Min | Mean | Max |
|---|---|---|---|
| operations | 300 | 300.0 | 300 |
| completed operations | 193 | 240.5 | 289 |
| pending writes (unknown outcome) | 6 | 30.4 | 59 |
| reads left out (no definite answer) | 5 | 29.1 | 64 |
| node crashes (power loss) | 13 | 30.3 | 46 |
|   of which mid-I/O (crash at a random write/fsync/rename) | 4 | 15.5 | 29 |
| node pauses (frozen 0.5-2 s) | 7 | 28.4 | 47 |
| partitions | 16 | 66.3 | 110 |
| message-loss periods | 9 | 21.8 | 35 |
| messages lost | 140 | 465.9 | 918 |
| long-delay periods | 9 | 21.6 | 38 |
| snapshots taken (all nodes) | 22 | 29.3 | 39 |
| InstallSnapshots completed (all nodes) | 4 | 11.5 | 19 |
| final term (highest across nodes) | 16 | 65.9 | 107 |
| virtual time (s) | 38.2 | 97.8 | 152.6 |
| wall time (ms) | 14 | 57.6 | 1365 |

## Runs that did not pass

| Seed | Verdict | Key / failure | Ops on key | Reproduce |
|---|---|---|---|---|
| 7 | INTERNAL_FAILURE | java.lang.IllegalStateException: log compacted through 83 but the state only covers 81: committed entries are lost | - | `./gradlew chaos -Pruns=1 -PstartSeed=7 -Plabel=mutation-compact-before-snapshot -Pverbose` |
| 8 | INTERNAL_FAILURE | java.lang.IllegalStateException: log compacted through 61 but the state only covers 43: committed entries are lost | - | `./gradlew chaos -Pruns=1 -PstartSeed=8 -Plabel=mutation-compact-before-snapshot -Pverbose` |
| 16 | INTERNAL_FAILURE | java.lang.IllegalStateException: log compacted through 40 but the state only covers 28: committed entries are lost | - | `./gradlew chaos -Pruns=1 -PstartSeed=16 -Plabel=mutation-compact-before-snapshot -Pverbose` |
| 19 | INTERNAL_FAILURE | java.lang.IllegalStateException: log compacted through 163 but the state only covers 145: committed entries are lost | - | `./gradlew chaos -Pruns=1 -PstartSeed=19 -Plabel=mutation-compact-before-snapshot -Pverbose` |
| 30 | INTERNAL_FAILURE | java.lang.IllegalStateException: log compacted through 22 but the state only covers 21: committed entries are lost | - | `./gradlew chaos -Pruns=1 -PstartSeed=30 -Plabel=mutation-compact-before-snapshot -Pverbose` |
| 31 | INTERNAL_FAILURE | java.lang.IllegalStateException: log compacted through 101 but the state only covers 90: committed entries are lost | - | `./gradlew chaos -Pruns=1 -PstartSeed=31 -Plabel=mutation-compact-before-snapshot -Pverbose` |
| 44 | INTERNAL_FAILURE | java.lang.IllegalStateException: log compacted through 122 but the state only covers 103: committed entries are lost | - | `./gradlew chaos -Pruns=1 -PstartSeed=44 -Plabel=mutation-compact-before-snapshot -Pverbose` |
| 47 | INTERNAL_FAILURE | java.lang.IllegalStateException: log compacted through 160 but the state only covers 143: committed entries are lost | - | `./gradlew chaos -Pruns=1 -PstartSeed=47 -Plabel=mutation-compact-before-snapshot -Pverbose` |
| 55 | INTERNAL_FAILURE | java.lang.IllegalStateException: log compacted through 41 but the state only covers 40: committed entries are lost | - | `./gradlew chaos -Pruns=1 -PstartSeed=55 -Plabel=mutation-compact-before-snapshot -Pverbose` |
| 61 | INTERNAL_FAILURE | java.util.NoSuchElementException: No value present | - | `./gradlew chaos -Pruns=1 -PstartSeed=61 -Plabel=mutation-compact-before-snapshot -Pverbose` |
| 67 | INTERNAL_FAILURE | java.lang.IllegalStateException: log compacted through 93 but the state only covers 84: committed entries are lost | - | `./gradlew chaos -Pruns=1 -PstartSeed=67 -Plabel=mutation-compact-before-snapshot -Pverbose` |
| 72 | INTERNAL_FAILURE | java.lang.IllegalStateException: log compacted through 164 but the state only covers 163: committed entries are lost | - | `./gradlew chaos -Pruns=1 -PstartSeed=72 -Plabel=mutation-compact-before-snapshot -Pverbose` |
| 73 | INTERNAL_FAILURE | java.util.NoSuchElementException: No value present | - | `./gradlew chaos -Pruns=1 -PstartSeed=73 -Plabel=mutation-compact-before-snapshot -Pverbose` |
| 84 | INTERNAL_FAILURE | java.lang.IllegalStateException: log compacted through 124 but the state only covers 123: committed entries are lost | - | `./gradlew chaos -Pruns=1 -PstartSeed=84 -Plabel=mutation-compact-before-snapshot -Pverbose` |
| 86 | INTERNAL_FAILURE | java.lang.IllegalStateException: log compacted through 163 but the state only covers 161: committed entries are lost | - | `./gradlew chaos -Pruns=1 -PstartSeed=86 -Plabel=mutation-compact-before-snapshot -Pverbose` |
| 89 | INTERNAL_FAILURE | java.lang.IllegalStateException: log compacted through 165 but the state only covers 164: committed entries are lost | - | `./gradlew chaos -Pruns=1 -PstartSeed=89 -Plabel=mutation-compact-before-snapshot -Pverbose` |
| 92 | INTERNAL_FAILURE | java.lang.IllegalStateException: log compacted through 83 but the state only covers 80: committed entries are lost | - | `./gradlew chaos -Pruns=1 -PstartSeed=92 -Plabel=mutation-compact-before-snapshot -Pverbose` |
| 106 | INTERNAL_FAILURE | java.lang.IllegalStateException: log compacted through 160 but the state only covers 142: committed entries are lost | - | `./gradlew chaos -Pruns=1 -PstartSeed=106 -Plabel=mutation-compact-before-snapshot -Pverbose` |
| 117 | INTERNAL_FAILURE | java.lang.IllegalStateException: log compacted through 40 but the state only covers 21: committed entries are lost | - | `./gradlew chaos -Pruns=1 -PstartSeed=117 -Plabel=mutation-compact-before-snapshot -Pverbose` |
| 122 | INTERNAL_FAILURE | java.lang.IllegalStateException: log compacted through 62 but the state only covers 60: committed entries are lost | - | `./gradlew chaos -Pruns=1 -PstartSeed=122 -Plabel=mutation-compact-before-snapshot -Pverbose` |
| 123 | INTERNAL_FAILURE | java.lang.IllegalStateException: log compacted through 68 but the state only covers 61: committed entries are lost | - | `./gradlew chaos -Pruns=1 -PstartSeed=123 -Plabel=mutation-compact-before-snapshot -Pverbose` |
| 124 | INTERNAL_FAILURE | java.lang.IllegalStateException: log compacted through 101 but the state only covers 100: committed entries are lost | - | `./gradlew chaos -Pruns=1 -PstartSeed=124 -Plabel=mutation-compact-before-snapshot -Pverbose` |
| 147 | INTERNAL_FAILURE | java.util.NoSuchElementException: No value present | - | `./gradlew chaos -Pruns=1 -PstartSeed=147 -Plabel=mutation-compact-before-snapshot -Pverbose` |
| 151 | INTERNAL_FAILURE | java.lang.IllegalStateException: log compacted through 81 but the state only covers 63: committed entries are lost | - | `./gradlew chaos -Pruns=1 -PstartSeed=151 -Plabel=mutation-compact-before-snapshot -Pverbose` |
| 164 | INTERNAL_FAILURE | java.lang.IllegalStateException: log compacted through 183 but the state only covers 182: committed entries are lost | - | `./gradlew chaos -Pruns=1 -PstartSeed=164 -Plabel=mutation-compact-before-snapshot -Pverbose` |
| 166 | INTERNAL_FAILURE | java.lang.IllegalStateException: log compacted through 61 but the state only covers 60: committed entries are lost | - | `./gradlew chaos -Pruns=1 -PstartSeed=166 -Plabel=mutation-compact-before-snapshot -Pverbose` |
| 167 | INTERNAL_FAILURE | java.lang.IllegalStateException: log compacted through 61 but the state only covers 60: committed entries are lost | - | `./gradlew chaos -Pruns=1 -PstartSeed=167 -Plabel=mutation-compact-before-snapshot -Pverbose` |
| 178 | INTERNAL_FAILURE | java.lang.IllegalStateException: log compacted through 60 but the state only covers 43: committed entries are lost | - | `./gradlew chaos -Pruns=1 -PstartSeed=178 -Plabel=mutation-compact-before-snapshot -Pverbose` |
| 179 | INTERNAL_FAILURE | java.lang.IllegalStateException: log compacted through 162 but the state only covers 161: committed entries are lost | - | `./gradlew chaos -Pruns=1 -PstartSeed=179 -Plabel=mutation-compact-before-snapshot -Pverbose` |
| 187 | INTERNAL_FAILURE | java.util.NoSuchElementException: No value present | - | `./gradlew chaos -Pruns=1 -PstartSeed=187 -Plabel=mutation-compact-before-snapshot -Pverbose` |
| 197 | INTERNAL_FAILURE | java.lang.IllegalStateException: log compacted through 43 but the state only covers 40: committed entries are lost | - | `./gradlew chaos -Pruns=1 -PstartSeed=197 -Plabel=mutation-compact-before-snapshot -Pverbose` |
| 203 | INTERNAL_FAILURE | java.lang.IllegalStateException: log compacted through 65 but the state only covers 62: committed entries are lost | - | `./gradlew chaos -Pruns=1 -PstartSeed=203 -Plabel=mutation-compact-before-snapshot -Pverbose` |
| 227 | INTERNAL_FAILURE | java.lang.IllegalStateException: log compacted through 145 but the state only covers 144: committed entries are lost | - | `./gradlew chaos -Pruns=1 -PstartSeed=227 -Plabel=mutation-compact-before-snapshot -Pverbose` |
| 234 | INTERNAL_FAILURE | java.lang.IllegalStateException: log compacted through 40 but the state only covers 21: committed entries are lost | - | `./gradlew chaos -Pruns=1 -PstartSeed=234 -Plabel=mutation-compact-before-snapshot -Pverbose` |
| 246 | INTERNAL_FAILURE | java.lang.IllegalStateException: log compacted through 81 but the state only covers 67: committed entries are lost | - | `./gradlew chaos -Pruns=1 -PstartSeed=246 -Plabel=mutation-compact-before-snapshot -Pverbose` |
| 248 | INTERNAL_FAILURE | java.lang.IllegalStateException: log compacted through 161 but the state only covers 145: committed entries are lost | - | `./gradlew chaos -Pruns=1 -PstartSeed=248 -Plabel=mutation-compact-before-snapshot -Pverbose` |
| 265 | INTERNAL_FAILURE | java.lang.IllegalStateException: log compacted through 81 but the state only covers 80: committed entries are lost | - | `./gradlew chaos -Pruns=1 -PstartSeed=265 -Plabel=mutation-compact-before-snapshot -Pverbose` |
| 269 | INTERNAL_FAILURE | java.lang.IllegalStateException: log compacted through 60 but the state only covers 41: committed entries are lost | - | `./gradlew chaos -Pruns=1 -PstartSeed=269 -Plabel=mutation-compact-before-snapshot -Pverbose` |
| 283 | INTERNAL_FAILURE | java.lang.IllegalStateException: log compacted through 62 but the state only covers 61: committed entries are lost | - | `./gradlew chaos -Pruns=1 -PstartSeed=283 -Plabel=mutation-compact-before-snapshot -Pverbose` |
| 285 | INTERNAL_FAILURE | java.lang.IllegalStateException: log compacted through 166 but the state only covers 161: committed entries are lost | - | `./gradlew chaos -Pruns=1 -PstartSeed=285 -Plabel=mutation-compact-before-snapshot -Pverbose` |
| 294 | INTERNAL_FAILURE | java.lang.IllegalStateException: log compacted through 142 but the state only covers 123: committed entries are lost | - | `./gradlew chaos -Pruns=1 -PstartSeed=294 -Plabel=mutation-compact-before-snapshot -Pverbose` |
| 295 | INTERNAL_FAILURE | java.lang.IllegalStateException: log compacted through 141 but the state only covers 140: committed entries are lost | - | `./gradlew chaos -Pruns=1 -PstartSeed=295 -Plabel=mutation-compact-before-snapshot -Pverbose` |
| 317 | INTERNAL_FAILURE | java.lang.IllegalStateException: log compacted through 62 but the state only covers 61: committed entries are lost | - | `./gradlew chaos -Pruns=1 -PstartSeed=317 -Plabel=mutation-compact-before-snapshot -Pverbose` |
| 323 | INTERNAL_FAILURE | java.lang.IllegalStateException: log compacted through 102 but the state only covers 100: committed entries are lost | - | `./gradlew chaos -Pruns=1 -PstartSeed=323 -Plabel=mutation-compact-before-snapshot -Pverbose` |
| 325 | INTERNAL_FAILURE | java.lang.IllegalStateException: log compacted through 122 but the state only covers 121: committed entries are lost | - | `./gradlew chaos -Pruns=1 -PstartSeed=325 -Plabel=mutation-compact-before-snapshot -Pverbose` |
| 329 | INTERNAL_FAILURE | java.lang.IllegalStateException: log compacted through 122 but the state only covers 104: committed entries are lost | - | `./gradlew chaos -Pruns=1 -PstartSeed=329 -Plabel=mutation-compact-before-snapshot -Pverbose` |
| 339 | INTERNAL_FAILURE | java.lang.IllegalStateException: log compacted through 81 but the state only covers 62: committed entries are lost | - | `./gradlew chaos -Pruns=1 -PstartSeed=339 -Plabel=mutation-compact-before-snapshot -Pverbose` |
| 340 | INTERNAL_FAILURE | java.lang.IllegalStateException: log compacted through 161 but the state only covers 160: committed entries are lost | - | `./gradlew chaos -Pruns=1 -PstartSeed=340 -Plabel=mutation-compact-before-snapshot -Pverbose` |
| 344 | INTERNAL_FAILURE | java.lang.IllegalStateException: log compacted through 181 but the state only covers 163: committed entries are lost | - | `./gradlew chaos -Pruns=1 -PstartSeed=344 -Plabel=mutation-compact-before-snapshot -Pverbose` |
| 345 | INTERNAL_FAILURE | java.lang.IllegalStateException: log compacted through 148 but the state only covers 146: committed entries are lost | - | `./gradlew chaos -Pruns=1 -PstartSeed=345 -Plabel=mutation-compact-before-snapshot -Pverbose` |
| 352 | INTERNAL_FAILURE | java.lang.IllegalStateException: log compacted through 144 but the state only covers 143: committed entries are lost | - | `./gradlew chaos -Pruns=1 -PstartSeed=352 -Plabel=mutation-compact-before-snapshot -Pverbose` |
| 354 | INTERNAL_FAILURE | java.lang.IllegalStateException: log compacted through 61 but the state only covers 60: committed entries are lost | - | `./gradlew chaos -Pruns=1 -PstartSeed=354 -Plabel=mutation-compact-before-snapshot -Pverbose` |
| 356 | INTERNAL_FAILURE | java.lang.IllegalStateException: log compacted through 41 but the state only covers 40: committed entries are lost | - | `./gradlew chaos -Pruns=1 -PstartSeed=356 -Plabel=mutation-compact-before-snapshot -Pverbose` |
| 357 | INTERNAL_FAILURE | java.lang.IllegalStateException: log compacted through 176 but the state only covers 174: committed entries are lost | - | `./gradlew chaos -Pruns=1 -PstartSeed=357 -Plabel=mutation-compact-before-snapshot -Pverbose` |
| 361 | INTERNAL_FAILURE | java.lang.IllegalStateException: log compacted through 40 but the state only covers 24: committed entries are lost | - | `./gradlew chaos -Pruns=1 -PstartSeed=361 -Plabel=mutation-compact-before-snapshot -Pverbose` |
| 362 | INTERNAL_FAILURE | java.lang.IllegalStateException: log compacted through 121 but the state only covers 106: committed entries are lost | - | `./gradlew chaos -Pruns=1 -PstartSeed=362 -Plabel=mutation-compact-before-snapshot -Pverbose` |
| 363 | INTERNAL_FAILURE | java.lang.IllegalStateException: log compacted through 120 but the state only covers 101: committed entries are lost | - | `./gradlew chaos -Pruns=1 -PstartSeed=363 -Plabel=mutation-compact-before-snapshot -Pverbose` |
| 369 | INTERNAL_FAILURE | java.lang.IllegalStateException: log compacted through 60 but the state only covers 44: committed entries are lost | - | `./gradlew chaos -Pruns=1 -PstartSeed=369 -Plabel=mutation-compact-before-snapshot -Pverbose` |
| 372 | INTERNAL_FAILURE | java.lang.IllegalStateException: log compacted through 60 but the state only covers 42: committed entries are lost | - | `./gradlew chaos -Pruns=1 -PstartSeed=372 -Plabel=mutation-compact-before-snapshot -Pverbose` |
| 382 | INTERNAL_FAILURE | java.lang.IllegalStateException: log compacted through 60 but the state only covers 43: committed entries are lost | - | `./gradlew chaos -Pruns=1 -PstartSeed=382 -Plabel=mutation-compact-before-snapshot -Pverbose` |
| 384 | INTERNAL_FAILURE | java.lang.IllegalStateException: log compacted through 182 but the state only covers 163: committed entries are lost | - | `./gradlew chaos -Pruns=1 -PstartSeed=384 -Plabel=mutation-compact-before-snapshot -Pverbose` |
| 403 | INTERNAL_FAILURE | java.lang.IllegalStateException: log compacted through 60 but the state only covers 42: committed entries are lost | - | `./gradlew chaos -Pruns=1 -PstartSeed=403 -Plabel=mutation-compact-before-snapshot -Pverbose` |
| 415 | INTERNAL_FAILURE | java.lang.IllegalStateException: log compacted through 20 but the state only covers 1: committed entries are lost | - | `./gradlew chaos -Pruns=1 -PstartSeed=415 -Plabel=mutation-compact-before-snapshot -Pverbose` |
| 420 | INTERNAL_FAILURE | java.lang.IllegalStateException: log compacted through 40 but the state only covers 21: committed entries are lost | - | `./gradlew chaos -Pruns=1 -PstartSeed=420 -Plabel=mutation-compact-before-snapshot -Pverbose` |
| 421 | INTERNAL_FAILURE | java.lang.IllegalStateException: log compacted through 141 but the state only covers 122: committed entries are lost | - | `./gradlew chaos -Pruns=1 -PstartSeed=421 -Plabel=mutation-compact-before-snapshot -Pverbose` |
| 423 | INTERNAL_FAILURE | java.lang.IllegalStateException: log compacted through 20 but the state only covers 1: committed entries are lost | - | `./gradlew chaos -Pruns=1 -PstartSeed=423 -Plabel=mutation-compact-before-snapshot -Pverbose` |
| 425 | INTERNAL_FAILURE | java.lang.IllegalStateException: log compacted through 143 but the state only covers 125: committed entries are lost | - | `./gradlew chaos -Pruns=1 -PstartSeed=425 -Plabel=mutation-compact-before-snapshot -Pverbose` |
| 426 | INTERNAL_FAILURE | java.lang.IllegalStateException: log compacted through 183 but the state only covers 165: committed entries are lost | - | `./gradlew chaos -Pruns=1 -PstartSeed=426 -Plabel=mutation-compact-before-snapshot -Pverbose` |
| 433 | INTERNAL_FAILURE | java.lang.IllegalStateException: log compacted through 122 but the state only covers 121: committed entries are lost | - | `./gradlew chaos -Pruns=1 -PstartSeed=433 -Plabel=mutation-compact-before-snapshot -Pverbose` |
| 437 | INTERNAL_FAILURE | java.lang.IllegalStateException: log compacted through 181 but the state only covers 162: committed entries are lost | - | `./gradlew chaos -Pruns=1 -PstartSeed=437 -Plabel=mutation-compact-before-snapshot -Pverbose` |
| 446 | INTERNAL_FAILURE | java.lang.IllegalStateException: log compacted through 178 but the state only covers 162: committed entries are lost | - | `./gradlew chaos -Pruns=1 -PstartSeed=446 -Plabel=mutation-compact-before-snapshot -Pverbose` |
| 447 | INTERNAL_FAILURE | java.lang.IllegalStateException: log compacted through 140 but the state only covers 124: committed entries are lost | - | `./gradlew chaos -Pruns=1 -PstartSeed=447 -Plabel=mutation-compact-before-snapshot -Pverbose` |
| 456 | INTERNAL_FAILURE | java.lang.IllegalStateException: log compacted through 101 but the state only covers 88: committed entries are lost | - | `./gradlew chaos -Pruns=1 -PstartSeed=456 -Plabel=mutation-compact-before-snapshot -Pverbose` |
| 473 | INTERNAL_FAILURE | java.lang.IllegalStateException: log compacted through 60 but the state only covers 43: committed entries are lost | - | `./gradlew chaos -Pruns=1 -PstartSeed=473 -Plabel=mutation-compact-before-snapshot -Pverbose` |
| 476 | INTERNAL_FAILURE | java.lang.IllegalStateException: log compacted through 100 but the state only covers 84: committed entries are lost | - | `./gradlew chaos -Pruns=1 -PstartSeed=476 -Plabel=mutation-compact-before-snapshot -Pverbose` |
| 485 | INTERNAL_FAILURE | java.lang.IllegalStateException: log compacted through 183 but the state only covers 164: committed entries are lost | - | `./gradlew chaos -Pruns=1 -PstartSeed=485 -Plabel=mutation-compact-before-snapshot -Pverbose` |
| 487 | INTERNAL_FAILURE | java.lang.IllegalStateException: log compacted through 62 but the state only covers 45: committed entries are lost | - | `./gradlew chaos -Pruns=1 -PstartSeed=487 -Plabel=mutation-compact-before-snapshot -Pverbose` |
| 490 | INTERNAL_FAILURE | java.lang.IllegalStateException: log compacted through 146 but the state only covers 142: committed entries are lost | - | `./gradlew chaos -Pruns=1 -PstartSeed=490 -Plabel=mutation-compact-before-snapshot -Pverbose` |
| 495 | INTERNAL_FAILURE | java.lang.IllegalStateException: log compacted through 162 but the state only covers 144: committed entries are lost | - | `./gradlew chaos -Pruns=1 -PstartSeed=495 -Plabel=mutation-compact-before-snapshot -Pverbose` |
| 496 | INTERNAL_FAILURE | java.lang.IllegalStateException: log compacted through 41 but the state only covers 40: committed entries are lost | - | `./gradlew chaos -Pruns=1 -PstartSeed=496 -Plabel=mutation-compact-before-snapshot -Pverbose` |
| 498 | INTERNAL_FAILURE | java.lang.IllegalStateException: log compacted through 67 but the state only covers 52: committed entries are lost | - | `./gradlew chaos -Pruns=1 -PstartSeed=498 -Plabel=mutation-compact-before-snapshot -Pverbose` |
| 499 | INTERNAL_FAILURE | java.lang.IllegalStateException: log compacted through 122 but the state only covers 103: committed entries are lost | - | `./gradlew chaos -Pruns=1 -PstartSeed=499 -Plabel=mutation-compact-before-snapshot -Pverbose` |

Per-seed numbers: `chaos-mutation-compact-before-snapshot-500-seeds.csv`.

## Mutation applied

This run used a deliberately broken build (`scripts/chaos-mutation.sh compact-before-snapshot 500`).
Change to `raft/src/main/java/io/github/hard3007/raftkv/raft/RaftNode.java`:

```diff
@@ -1032,9 +1032,10 @@
         }
         long index = lastApplied;
         long term = log.termAt(index);
-        storage.snapshots().save(index, term, stateMachine.takeSnapshot());
+        byte[] data = stateMachine.takeSnapshot();
         log.compactThrough(index);
         storage.rewrite(log.snapshotIndex(), log.snapshotTerm(), currentTerm, votedFor, log.entriesAfterSnapshot());
+        storage.snapshots().save(index, term, data);
         snapshotsTaken++;
         logger.debug("node={} event=snapshot_taken index={} term={}", id, index, term);
     }
```
