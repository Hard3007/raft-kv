# Chaos report: mutation-compact-before-snapshot

- **Command:** `./gradlew chaos -Pruns=500 -Plabel=mutation-compact-before-snapshot`
- **Date:** 2026-10-20T09:42:31.863921015Z
- **Git commit:** 1c764a9 (with uncommitted changes)
- **Machine:** Linux 6.8.0-1064-azure amd64, 2 cores (AMD EPYC 7763 64-Core Processor), Java 21, max heap 1024 MB
- **Config:** 5 nodes, 5 clients, 3 keys, 60 ops per client, think time 0-300 ms, a fault action every 100-400 ms (virtual), client attempt timeout 1000 ms / give-up 4000 ms, checker timeout 10 s (wall); PreVote on, CheckQuorum on, a snapshot every 20 entries in 64-byte InstallSnapshot chunks
- **Seeds:** 1..500 (500 runs)
- **Total wall time:** 12.3 s

## Verdicts

| Verdict | Runs |
|---|---|
| LINEARIZABLE | 435 |
| NOT_LINEARIZABLE | 0 |
| INCONCLUSIVE | 0 |
| runs where the system failed an internal check | 65 |

Only LINEARIZABLE runs count as passing. INCONCLUSIVE means the checker ran out of time. An internal-check failure (a safety assertion, or a node refusing to start because committed entries were lost) has no verdict and does not pass either.

## Per seed (min / mean / max)

| Measure | Min | Mean | Max |
|---|---|---|---|
| operations | 300 | 300.0 | 300 |
| completed operations | 197 | 254.0 | 291 |
| pending writes (unknown outcome) | 4 | 23.3 | 62 |
| reads left out (no definite answer) | 5 | 22.7 | 52 |
| node crashes (power loss) | 13 | 29.2 | 49 |
|   of which mid-I/O (crash at a random write/fsync/rename) | 4 | 14.8 | 30 |
| partitions | 27 | 67.8 | 123 |
| message-loss periods | 8 | 21.0 | 46 |
| messages lost | 155 | 441.2 | 747 |
| long-delay periods | 9 | 21.0 | 40 |
| snapshots taken (all nodes) | 22 | 29.6 | 39 |
| InstallSnapshots completed (all nodes) | 6 | 11.9 | 18 |
| final term (highest across nodes) | 24 | 61.0 | 105 |
| virtual time (s) | 37.4 | 84.5 | 139.5 |
| wall time (ms) | 12 | 52.3 | 1551 |

## Runs that did not pass

| Seed | Verdict | Key / failure | Ops on key | Reproduce |
|---|---|---|---|---|
| 8 | INTERNAL_FAILURE | java.lang.IllegalStateException: log compacted through 41 but the state only covers 40: committed entries are lost | - | `./gradlew chaos -Pruns=1 -PstartSeed=8 -Plabel=mutation-compact-before-snapshot -Pverbose` |
| 16 | INTERNAL_FAILURE | java.lang.IllegalStateException: log compacted through 201 but the state only covers 182: committed entries are lost | - | `./gradlew chaos -Pruns=1 -PstartSeed=16 -Plabel=mutation-compact-before-snapshot -Pverbose` |
| 32 | INTERNAL_FAILURE | java.lang.IllegalStateException: log compacted through 100 but the state only covers 85: committed entries are lost | - | `./gradlew chaos -Pruns=1 -PstartSeed=32 -Plabel=mutation-compact-before-snapshot -Pverbose` |
| 42 | INTERNAL_FAILURE | java.lang.IllegalStateException: log compacted through 161 but the state only covers 143: committed entries are lost | - | `./gradlew chaos -Pruns=1 -PstartSeed=42 -Plabel=mutation-compact-before-snapshot -Pverbose` |
| 50 | INTERNAL_FAILURE | java.lang.IllegalStateException: log compacted through 161 but the state only covers 142: committed entries are lost | - | `./gradlew chaos -Pruns=1 -PstartSeed=50 -Plabel=mutation-compact-before-snapshot -Pverbose` |
| 78 | INTERNAL_FAILURE | java.lang.IllegalStateException: log compacted through 42 but the state only covers 41: committed entries are lost | - | `./gradlew chaos -Pruns=1 -PstartSeed=78 -Plabel=mutation-compact-before-snapshot -Pverbose` |
| 85 | INTERNAL_FAILURE | java.lang.IllegalStateException: log compacted through 82 but the state only covers 81: committed entries are lost | - | `./gradlew chaos -Pruns=1 -PstartSeed=85 -Plabel=mutation-compact-before-snapshot -Pverbose` |
| 97 | INTERNAL_FAILURE | java.util.NoSuchElementException: No value present | - | `./gradlew chaos -Pruns=1 -PstartSeed=97 -Plabel=mutation-compact-before-snapshot -Pverbose` |
| 103 | INTERNAL_FAILURE | java.lang.IllegalStateException: log compacted through 61 but the state only covers 60: committed entries are lost | - | `./gradlew chaos -Pruns=1 -PstartSeed=103 -Plabel=mutation-compact-before-snapshot -Pverbose` |
| 111 | INTERNAL_FAILURE | java.lang.IllegalStateException: log compacted through 60 but the state only covers 47: committed entries are lost | - | `./gradlew chaos -Pruns=1 -PstartSeed=111 -Plabel=mutation-compact-before-snapshot -Pverbose` |
| 115 | INTERNAL_FAILURE | java.lang.IllegalStateException: log compacted through 41 but the state only covers 40: committed entries are lost | - | `./gradlew chaos -Pruns=1 -PstartSeed=115 -Plabel=mutation-compact-before-snapshot -Pverbose` |
| 118 | INTERNAL_FAILURE | java.lang.IllegalStateException: log compacted through 161 but the state only covers 144: committed entries are lost | - | `./gradlew chaos -Pruns=1 -PstartSeed=118 -Plabel=mutation-compact-before-snapshot -Pverbose` |
| 123 | INTERNAL_FAILURE | java.lang.IllegalStateException: log compacted through 101 but the state only covers 85: committed entries are lost | - | `./gradlew chaos -Pruns=1 -PstartSeed=123 -Plabel=mutation-compact-before-snapshot -Pverbose` |
| 125 | INTERNAL_FAILURE | java.lang.IllegalStateException: log compacted through 127 but the state only covers 121: committed entries are lost | - | `./gradlew chaos -Pruns=1 -PstartSeed=125 -Plabel=mutation-compact-before-snapshot -Pverbose` |
| 133 | INTERNAL_FAILURE | java.lang.IllegalStateException: log compacted through 82 but the state only covers 80: committed entries are lost | - | `./gradlew chaos -Pruns=1 -PstartSeed=133 -Plabel=mutation-compact-before-snapshot -Pverbose` |
| 135 | INTERNAL_FAILURE | java.lang.IllegalStateException: log compacted through 20 but the state only covers 2: committed entries are lost | - | `./gradlew chaos -Pruns=1 -PstartSeed=135 -Plabel=mutation-compact-before-snapshot -Pverbose` |
| 136 | INTERNAL_FAILURE | java.lang.IllegalStateException: log compacted through 80 but the state only covers 61: committed entries are lost | - | `./gradlew chaos -Pruns=1 -PstartSeed=136 -Plabel=mutation-compact-before-snapshot -Pverbose` |
| 140 | INTERNAL_FAILURE | java.lang.IllegalStateException: log compacted through 120 but the state only covers 102: committed entries are lost | - | `./gradlew chaos -Pruns=1 -PstartSeed=140 -Plabel=mutation-compact-before-snapshot -Pverbose` |
| 143 | INTERNAL_FAILURE | java.lang.IllegalStateException: log compacted through 61 but the state only covers 60: committed entries are lost | - | `./gradlew chaos -Pruns=1 -PstartSeed=143 -Plabel=mutation-compact-before-snapshot -Pverbose` |
| 150 | INTERNAL_FAILURE | java.lang.IllegalStateException: log compacted through 126 but the state only covers 107: committed entries are lost | - | `./gradlew chaos -Pruns=1 -PstartSeed=150 -Plabel=mutation-compact-before-snapshot -Pverbose` |
| 151 | INTERNAL_FAILURE | java.util.NoSuchElementException: No value present | - | `./gradlew chaos -Pruns=1 -PstartSeed=151 -Plabel=mutation-compact-before-snapshot -Pverbose` |
| 152 | INTERNAL_FAILURE | java.lang.IllegalStateException: log compacted through 101 but the state only covers 90: committed entries are lost | - | `./gradlew chaos -Pruns=1 -PstartSeed=152 -Plabel=mutation-compact-before-snapshot -Pverbose` |
| 154 | INTERNAL_FAILURE | java.lang.IllegalStateException: log compacted through 181 but the state only covers 162: committed entries are lost | - | `./gradlew chaos -Pruns=1 -PstartSeed=154 -Plabel=mutation-compact-before-snapshot -Pverbose` |
| 159 | INTERNAL_FAILURE | java.lang.IllegalStateException: log compacted through 160 but the state only covers 143: committed entries are lost | - | `./gradlew chaos -Pruns=1 -PstartSeed=159 -Plabel=mutation-compact-before-snapshot -Pverbose` |
| 169 | INTERNAL_FAILURE | java.lang.IllegalStateException: log compacted through 183 but the state only covers 182: committed entries are lost | - | `./gradlew chaos -Pruns=1 -PstartSeed=169 -Plabel=mutation-compact-before-snapshot -Pverbose` |
| 182 | INTERNAL_FAILURE | java.lang.IllegalStateException: log compacted through 102 but the state only covers 84: committed entries are lost | - | `./gradlew chaos -Pruns=1 -PstartSeed=182 -Plabel=mutation-compact-before-snapshot -Pverbose` |
| 184 | INTERNAL_FAILURE | java.lang.IllegalStateException: log compacted through 160 but the state only covers 141: committed entries are lost | - | `./gradlew chaos -Pruns=1 -PstartSeed=184 -Plabel=mutation-compact-before-snapshot -Pverbose` |
| 191 | INTERNAL_FAILURE | java.lang.IllegalStateException: log compacted through 80 but the state only covers 61: committed entries are lost | - | `./gradlew chaos -Pruns=1 -PstartSeed=191 -Plabel=mutation-compact-before-snapshot -Pverbose` |
| 195 | INTERNAL_FAILURE | java.lang.IllegalStateException: log compacted through 182 but the state only covers 168: committed entries are lost | - | `./gradlew chaos -Pruns=1 -PstartSeed=195 -Plabel=mutation-compact-before-snapshot -Pverbose` |
| 200 | INTERNAL_FAILURE | java.util.NoSuchElementException: No value present | - | `./gradlew chaos -Pruns=1 -PstartSeed=200 -Plabel=mutation-compact-before-snapshot -Pverbose` |
| 216 | INTERNAL_FAILURE | java.lang.IllegalStateException: log compacted through 40 but the state only covers 21: committed entries are lost | - | `./gradlew chaos -Pruns=1 -PstartSeed=216 -Plabel=mutation-compact-before-snapshot -Pverbose` |
| 234 | INTERNAL_FAILURE | java.lang.IllegalStateException: log compacted through 162 but the state only covers 161: committed entries are lost | - | `./gradlew chaos -Pruns=1 -PstartSeed=234 -Plabel=mutation-compact-before-snapshot -Pverbose` |
| 252 | INTERNAL_FAILURE | java.lang.IllegalStateException: log compacted through 102 but the state only covers 101: committed entries are lost | - | `./gradlew chaos -Pruns=1 -PstartSeed=252 -Plabel=mutation-compact-before-snapshot -Pverbose` |
| 265 | INTERNAL_FAILURE | java.lang.IllegalStateException: log compacted through 60 but the state only covers 43: committed entries are lost | - | `./gradlew chaos -Pruns=1 -PstartSeed=265 -Plabel=mutation-compact-before-snapshot -Pverbose` |
| 269 | INTERNAL_FAILURE | java.lang.IllegalStateException: log compacted through 80 but the state only covers 63: committed entries are lost | - | `./gradlew chaos -Pruns=1 -PstartSeed=269 -Plabel=mutation-compact-before-snapshot -Pverbose` |
| 273 | INTERNAL_FAILURE | java.lang.IllegalStateException: log compacted through 42 but the state only covers 40: committed entries are lost | - | `./gradlew chaos -Pruns=1 -PstartSeed=273 -Plabel=mutation-compact-before-snapshot -Pverbose` |
| 278 | INTERNAL_FAILURE | java.lang.IllegalStateException: log compacted through 187 but the state only covers 169: committed entries are lost | - | `./gradlew chaos -Pruns=1 -PstartSeed=278 -Plabel=mutation-compact-before-snapshot -Pverbose` |
| 287 | INTERNAL_FAILURE | java.lang.IllegalStateException: log compacted through 121 but the state only covers 103: committed entries are lost | - | `./gradlew chaos -Pruns=1 -PstartSeed=287 -Plabel=mutation-compact-before-snapshot -Pverbose` |
| 305 | INTERNAL_FAILURE | java.lang.IllegalStateException: log compacted through 81 but the state only covers 64: committed entries are lost | - | `./gradlew chaos -Pruns=1 -PstartSeed=305 -Plabel=mutation-compact-before-snapshot -Pverbose` |
| 310 | INTERNAL_FAILURE | java.lang.IllegalStateException: log compacted through 41 but the state only covers 40: committed entries are lost | - | `./gradlew chaos -Pruns=1 -PstartSeed=310 -Plabel=mutation-compact-before-snapshot -Pverbose` |
| 311 | INTERNAL_FAILURE | java.lang.IllegalStateException: log compacted through 120 but the state only covers 102: committed entries are lost | - | `./gradlew chaos -Pruns=1 -PstartSeed=311 -Plabel=mutation-compact-before-snapshot -Pverbose` |
| 326 | INTERNAL_FAILURE | java.util.NoSuchElementException: No value present | - | `./gradlew chaos -Pruns=1 -PstartSeed=326 -Plabel=mutation-compact-before-snapshot -Pverbose` |
| 330 | INTERNAL_FAILURE | java.lang.IllegalStateException: log compacted through 60 but the state only covers 42: committed entries are lost | - | `./gradlew chaos -Pruns=1 -PstartSeed=330 -Plabel=mutation-compact-before-snapshot -Pverbose` |
| 332 | INTERNAL_FAILURE | java.lang.IllegalStateException: log compacted through 100 but the state only covers 81: committed entries are lost | - | `./gradlew chaos -Pruns=1 -PstartSeed=332 -Plabel=mutation-compact-before-snapshot -Pverbose` |
| 333 | INTERNAL_FAILURE | java.lang.IllegalStateException: log compacted through 124 but the state only covers 122: committed entries are lost | - | `./gradlew chaos -Pruns=1 -PstartSeed=333 -Plabel=mutation-compact-before-snapshot -Pverbose` |
| 334 | INTERNAL_FAILURE | java.lang.IllegalStateException: log compacted through 147 but the state only covers 145: committed entries are lost | - | `./gradlew chaos -Pruns=1 -PstartSeed=334 -Plabel=mutation-compact-before-snapshot -Pverbose` |
| 358 | INTERNAL_FAILURE | java.lang.IllegalStateException: log compacted through 169 but the state only covers 164: committed entries are lost | - | `./gradlew chaos -Pruns=1 -PstartSeed=358 -Plabel=mutation-compact-before-snapshot -Pverbose` |
| 360 | INTERNAL_FAILURE | java.lang.IllegalStateException: log compacted through 142 but the state only covers 124: committed entries are lost | - | `./gradlew chaos -Pruns=1 -PstartSeed=360 -Plabel=mutation-compact-before-snapshot -Pverbose` |
| 372 | INTERNAL_FAILURE | java.lang.IllegalStateException: log compacted through 140 but the state only covers 123: committed entries are lost | - | `./gradlew chaos -Pruns=1 -PstartSeed=372 -Plabel=mutation-compact-before-snapshot -Pverbose` |
| 380 | INTERNAL_FAILURE | java.lang.IllegalStateException: log compacted through 122 but the state only covers 121: committed entries are lost | - | `./gradlew chaos -Pruns=1 -PstartSeed=380 -Plabel=mutation-compact-before-snapshot -Pverbose` |
| 382 | INTERNAL_FAILURE | java.lang.IllegalStateException: log compacted through 62 but the state only covers 60: committed entries are lost | - | `./gradlew chaos -Pruns=1 -PstartSeed=382 -Plabel=mutation-compact-before-snapshot -Pverbose` |
| 401 | INTERNAL_FAILURE | java.lang.IllegalStateException: log compacted through 40 but the state only covers 21: committed entries are lost | - | `./gradlew chaos -Pruns=1 -PstartSeed=401 -Plabel=mutation-compact-before-snapshot -Pverbose` |
| 402 | INTERNAL_FAILURE | java.lang.IllegalStateException: log compacted through 20 but the state only covers 1: committed entries are lost | - | `./gradlew chaos -Pruns=1 -PstartSeed=402 -Plabel=mutation-compact-before-snapshot -Pverbose` |
| 415 | INTERNAL_FAILURE | java.lang.IllegalStateException: log compacted through 20 but the state only covers 1: committed entries are lost | - | `./gradlew chaos -Pruns=1 -PstartSeed=415 -Plabel=mutation-compact-before-snapshot -Pverbose` |
| 419 | INTERNAL_FAILURE | java.lang.IllegalStateException: log compacted through 122 but the state only covers 120: committed entries are lost | - | `./gradlew chaos -Pruns=1 -PstartSeed=419 -Plabel=mutation-compact-before-snapshot -Pverbose` |
| 429 | INTERNAL_FAILURE | java.lang.IllegalStateException: log compacted through 80 but the state only covers 61: committed entries are lost | - | `./gradlew chaos -Pruns=1 -PstartSeed=429 -Plabel=mutation-compact-before-snapshot -Pverbose` |
| 434 | INTERNAL_FAILURE | java.lang.IllegalStateException: log compacted through 63 but the state only covers 61: committed entries are lost | - | `./gradlew chaos -Pruns=1 -PstartSeed=434 -Plabel=mutation-compact-before-snapshot -Pverbose` |
| 441 | INTERNAL_FAILURE | java.lang.IllegalStateException: log compacted through 80 but the state only covers 64: committed entries are lost | - | `./gradlew chaos -Pruns=1 -PstartSeed=441 -Plabel=mutation-compact-before-snapshot -Pverbose` |
| 443 | INTERNAL_FAILURE | java.lang.IllegalStateException: log compacted through 160 but the state only covers 141: committed entries are lost | - | `./gradlew chaos -Pruns=1 -PstartSeed=443 -Plabel=mutation-compact-before-snapshot -Pverbose` |
| 450 | INTERNAL_FAILURE | java.lang.IllegalStateException: log compacted through 121 but the state only covers 102: committed entries are lost | - | `./gradlew chaos -Pruns=1 -PstartSeed=450 -Plabel=mutation-compact-before-snapshot -Pverbose` |
| 451 | INTERNAL_FAILURE | java.lang.IllegalStateException: log compacted through 144 but the state only covers 129: committed entries are lost | - | `./gradlew chaos -Pruns=1 -PstartSeed=451 -Plabel=mutation-compact-before-snapshot -Pverbose` |
| 454 | INTERNAL_FAILURE | java.lang.IllegalStateException: log compacted through 122 but the state only covers 120: committed entries are lost | - | `./gradlew chaos -Pruns=1 -PstartSeed=454 -Plabel=mutation-compact-before-snapshot -Pverbose` |
| 455 | INTERNAL_FAILURE | java.lang.IllegalStateException: log compacted through 60 but the state only covers 42: committed entries are lost | - | `./gradlew chaos -Pruns=1 -PstartSeed=455 -Plabel=mutation-compact-before-snapshot -Pverbose` |
| 490 | INTERNAL_FAILURE | java.lang.IllegalStateException: log compacted through 181 but the state only covers 163: committed entries are lost | - | `./gradlew chaos -Pruns=1 -PstartSeed=490 -Plabel=mutation-compact-before-snapshot -Pverbose` |
| 495 | INTERNAL_FAILURE | java.lang.IllegalStateException: log compacted through 161 but the state only covers 142: committed entries are lost | - | `./gradlew chaos -Pruns=1 -PstartSeed=495 -Plabel=mutation-compact-before-snapshot -Pverbose` |

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
