# Chaos report: mutation-reads-without-readindex-no-checkquorum

- **Command:** `./gradlew chaos -Pruns=500 -Plabel=mutation-reads-without-readindex-no-checkquorum -PcheckQuorum=false`
- **Date:** 2026-10-20T09:53:00.014268421Z
- **Git commit:** 61dda62 (with uncommitted changes)
- **Machine:** Linux 6.8.0-1064-azure amd64, 2 cores (AMD EPYC 7763 64-Core Processor), Java 21, max heap 1024 MB
- **Config:** 5 nodes, 5 clients, 3 keys, 60 ops per client, think time 0-300 ms, a fault action every 100-400 ms (virtual), client attempt timeout 1000 ms / give-up 4000 ms, checker timeout 10 s (wall); PreVote on, CheckQuorum off, a snapshot every 20 entries in 64-byte InstallSnapshot chunks
- **Seeds:** 1..500 (500 runs)
- **Total wall time:** 15.4 s

## Verdicts

| Verdict | Runs |
|---|---|
| LINEARIZABLE | 450 |
| NOT_LINEARIZABLE | 50 |
| INCONCLUSIVE | 0 |
| runs where the system failed an internal check | 0 |

Only LINEARIZABLE runs count as passing. INCONCLUSIVE means the checker ran out of time. An internal-check failure (a safety assertion, or a node refusing to start because committed entries were lost) has no verdict and does not pass either.

## Per seed (min / mean / max)

| Measure | Min | Mean | Max |
|---|---|---|---|
| operations | 300 | 300.0 | 300 |
| completed operations | 212 | 257.1 | 289 |
| pending writes (unknown outcome) | 4 | 26.3 | 56 |
| reads left out (no definite answer) | 2 | 16.7 | 38 |
| node crashes (power loss) | 9 | 25.4 | 41 |
|   of which mid-I/O (crash at a random write/fsync/rename) | 4 | 12.9 | 27 |
| node pauses (frozen 0.5-2 s) | 6 | 23.3 | 44 |
| partitions | 28 | 68.3 | 120 |
| message-loss periods | 8 | 17.9 | 32 |
| messages lost | 135 | 431.3 | 817 |
| long-delay periods | 8 | 18.0 | 32 |
| snapshots taken (all nodes) | 20 | 26.8 | 37 |
| InstallSnapshots completed (all nodes) | 4 | 10.9 | 17 |
| final term (highest across nodes) | 10 | 37.1 | 81 |
| virtual time (s) | 44.6 | 81.0 | 124.7 |
| wall time (ms) | 10 | 61.0 | 4065 |

## Runs that did not pass

| Seed | Verdict | Key / failure | Ops on key | Reproduce |
|---|---|---|---|---|
| 5 | NOT_LINEARIZABLE | k2 | 101 | `./gradlew chaos -Pruns=1 -PstartSeed=5 -Plabel=mutation-reads-without-readindex-no-checkquorum -Pverbose` |
| 15 | NOT_LINEARIZABLE | k2 | 108 | `./gradlew chaos -Pruns=1 -PstartSeed=15 -Plabel=mutation-reads-without-readindex-no-checkquorum -Pverbose` |
| 24 | NOT_LINEARIZABLE | k0 | 98 | `./gradlew chaos -Pruns=1 -PstartSeed=24 -Plabel=mutation-reads-without-readindex-no-checkquorum -Pverbose` |
| 26 | NOT_LINEARIZABLE | k1 | 95 | `./gradlew chaos -Pruns=1 -PstartSeed=26 -Plabel=mutation-reads-without-readindex-no-checkquorum -Pverbose` |
| 36 | NOT_LINEARIZABLE | k0 | 91 | `./gradlew chaos -Pruns=1 -PstartSeed=36 -Plabel=mutation-reads-without-readindex-no-checkquorum -Pverbose` |
| 39 | NOT_LINEARIZABLE | k1 | 91 | `./gradlew chaos -Pruns=1 -PstartSeed=39 -Plabel=mutation-reads-without-readindex-no-checkquorum -Pverbose` |
| 56 | NOT_LINEARIZABLE | k0 | 94 | `./gradlew chaos -Pruns=1 -PstartSeed=56 -Plabel=mutation-reads-without-readindex-no-checkquorum -Pverbose` |
| 61 | NOT_LINEARIZABLE | k0 | 96 | `./gradlew chaos -Pruns=1 -PstartSeed=61 -Plabel=mutation-reads-without-readindex-no-checkquorum -Pverbose` |
| 96 | NOT_LINEARIZABLE | k0 | 115 | `./gradlew chaos -Pruns=1 -PstartSeed=96 -Plabel=mutation-reads-without-readindex-no-checkquorum -Pverbose` |
| 100 | NOT_LINEARIZABLE | k2 | 98 | `./gradlew chaos -Pruns=1 -PstartSeed=100 -Plabel=mutation-reads-without-readindex-no-checkquorum -Pverbose` |
| 106 | NOT_LINEARIZABLE | k1 | 94 | `./gradlew chaos -Pruns=1 -PstartSeed=106 -Plabel=mutation-reads-without-readindex-no-checkquorum -Pverbose` |
| 109 | NOT_LINEARIZABLE | k1 | 90 | `./gradlew chaos -Pruns=1 -PstartSeed=109 -Plabel=mutation-reads-without-readindex-no-checkquorum -Pverbose` |
| 125 | NOT_LINEARIZABLE | k0 | 89 | `./gradlew chaos -Pruns=1 -PstartSeed=125 -Plabel=mutation-reads-without-readindex-no-checkquorum -Pverbose` |
| 126 | NOT_LINEARIZABLE | k1 | 105 | `./gradlew chaos -Pruns=1 -PstartSeed=126 -Plabel=mutation-reads-without-readindex-no-checkquorum -Pverbose` |
| 128 | NOT_LINEARIZABLE | k0 | 99 | `./gradlew chaos -Pruns=1 -PstartSeed=128 -Plabel=mutation-reads-without-readindex-no-checkquorum -Pverbose` |
| 130 | NOT_LINEARIZABLE | k2 | 101 | `./gradlew chaos -Pruns=1 -PstartSeed=130 -Plabel=mutation-reads-without-readindex-no-checkquorum -Pverbose` |
| 138 | NOT_LINEARIZABLE | k1 | 87 | `./gradlew chaos -Pruns=1 -PstartSeed=138 -Plabel=mutation-reads-without-readindex-no-checkquorum -Pverbose` |
| 153 | NOT_LINEARIZABLE | k1 | 81 | `./gradlew chaos -Pruns=1 -PstartSeed=153 -Plabel=mutation-reads-without-readindex-no-checkquorum -Pverbose` |
| 170 | NOT_LINEARIZABLE | k0 | 119 | `./gradlew chaos -Pruns=1 -PstartSeed=170 -Plabel=mutation-reads-without-readindex-no-checkquorum -Pverbose` |
| 176 | NOT_LINEARIZABLE | k1 | 99 | `./gradlew chaos -Pruns=1 -PstartSeed=176 -Plabel=mutation-reads-without-readindex-no-checkquorum -Pverbose` |
| 181 | NOT_LINEARIZABLE | k1 | 104 | `./gradlew chaos -Pruns=1 -PstartSeed=181 -Plabel=mutation-reads-without-readindex-no-checkquorum -Pverbose` |
| 196 | NOT_LINEARIZABLE | k0 | 94 | `./gradlew chaos -Pruns=1 -PstartSeed=196 -Plabel=mutation-reads-without-readindex-no-checkquorum -Pverbose` |
| 211 | NOT_LINEARIZABLE | k0 | 98 | `./gradlew chaos -Pruns=1 -PstartSeed=211 -Plabel=mutation-reads-without-readindex-no-checkquorum -Pverbose` |
| 217 | NOT_LINEARIZABLE | k1 | 85 | `./gradlew chaos -Pruns=1 -PstartSeed=217 -Plabel=mutation-reads-without-readindex-no-checkquorum -Pverbose` |
| 220 | NOT_LINEARIZABLE | k1 | 104 | `./gradlew chaos -Pruns=1 -PstartSeed=220 -Plabel=mutation-reads-without-readindex-no-checkquorum -Pverbose` |
| 222 | NOT_LINEARIZABLE | k0 | 89 | `./gradlew chaos -Pruns=1 -PstartSeed=222 -Plabel=mutation-reads-without-readindex-no-checkquorum -Pverbose` |
| 228 | NOT_LINEARIZABLE | k2 | 98 | `./gradlew chaos -Pruns=1 -PstartSeed=228 -Plabel=mutation-reads-without-readindex-no-checkquorum -Pverbose` |
| 239 | NOT_LINEARIZABLE | k1 | 97 | `./gradlew chaos -Pruns=1 -PstartSeed=239 -Plabel=mutation-reads-without-readindex-no-checkquorum -Pverbose` |
| 261 | NOT_LINEARIZABLE | k2 | 96 | `./gradlew chaos -Pruns=1 -PstartSeed=261 -Plabel=mutation-reads-without-readindex-no-checkquorum -Pverbose` |
| 271 | NOT_LINEARIZABLE | k1 | 99 | `./gradlew chaos -Pruns=1 -PstartSeed=271 -Plabel=mutation-reads-without-readindex-no-checkquorum -Pverbose` |
| 288 | NOT_LINEARIZABLE | k1 | 94 | `./gradlew chaos -Pruns=1 -PstartSeed=288 -Plabel=mutation-reads-without-readindex-no-checkquorum -Pverbose` |
| 296 | NOT_LINEARIZABLE | k2 | 90 | `./gradlew chaos -Pruns=1 -PstartSeed=296 -Plabel=mutation-reads-without-readindex-no-checkquorum -Pverbose` |
| 326 | NOT_LINEARIZABLE | k0 | 81 | `./gradlew chaos -Pruns=1 -PstartSeed=326 -Plabel=mutation-reads-without-readindex-no-checkquorum -Pverbose` |
| 352 | NOT_LINEARIZABLE | k1 | 93 | `./gradlew chaos -Pruns=1 -PstartSeed=352 -Plabel=mutation-reads-without-readindex-no-checkquorum -Pverbose` |
| 361 | NOT_LINEARIZABLE | k0 | 83 | `./gradlew chaos -Pruns=1 -PstartSeed=361 -Plabel=mutation-reads-without-readindex-no-checkquorum -Pverbose` |
| 363 | NOT_LINEARIZABLE | k1 | 100 | `./gradlew chaos -Pruns=1 -PstartSeed=363 -Plabel=mutation-reads-without-readindex-no-checkquorum -Pverbose` |
| 376 | NOT_LINEARIZABLE | k1 | 96 | `./gradlew chaos -Pruns=1 -PstartSeed=376 -Plabel=mutation-reads-without-readindex-no-checkquorum -Pverbose` |
| 381 | NOT_LINEARIZABLE | k1 | 90 | `./gradlew chaos -Pruns=1 -PstartSeed=381 -Plabel=mutation-reads-without-readindex-no-checkquorum -Pverbose` |
| 384 | NOT_LINEARIZABLE | k0 | 98 | `./gradlew chaos -Pruns=1 -PstartSeed=384 -Plabel=mutation-reads-without-readindex-no-checkquorum -Pverbose` |
| 391 | NOT_LINEARIZABLE | k2 | 111 | `./gradlew chaos -Pruns=1 -PstartSeed=391 -Plabel=mutation-reads-without-readindex-no-checkquorum -Pverbose` |
| 393 | NOT_LINEARIZABLE | k0 | 81 | `./gradlew chaos -Pruns=1 -PstartSeed=393 -Plabel=mutation-reads-without-readindex-no-checkquorum -Pverbose` |
| 417 | NOT_LINEARIZABLE | k2 | 110 | `./gradlew chaos -Pruns=1 -PstartSeed=417 -Plabel=mutation-reads-without-readindex-no-checkquorum -Pverbose` |
| 420 | NOT_LINEARIZABLE | k1 | 106 | `./gradlew chaos -Pruns=1 -PstartSeed=420 -Plabel=mutation-reads-without-readindex-no-checkquorum -Pverbose` |
| 425 | NOT_LINEARIZABLE | k1 | 88 | `./gradlew chaos -Pruns=1 -PstartSeed=425 -Plabel=mutation-reads-without-readindex-no-checkquorum -Pverbose` |
| 428 | NOT_LINEARIZABLE | k0 | 95 | `./gradlew chaos -Pruns=1 -PstartSeed=428 -Plabel=mutation-reads-without-readindex-no-checkquorum -Pverbose` |
| 439 | NOT_LINEARIZABLE | k0 | 90 | `./gradlew chaos -Pruns=1 -PstartSeed=439 -Plabel=mutation-reads-without-readindex-no-checkquorum -Pverbose` |
| 442 | NOT_LINEARIZABLE | k2 | 94 | `./gradlew chaos -Pruns=1 -PstartSeed=442 -Plabel=mutation-reads-without-readindex-no-checkquorum -Pverbose` |
| 456 | NOT_LINEARIZABLE | k1 | 89 | `./gradlew chaos -Pruns=1 -PstartSeed=456 -Plabel=mutation-reads-without-readindex-no-checkquorum -Pverbose` |
| 468 | NOT_LINEARIZABLE | k0 | 87 | `./gradlew chaos -Pruns=1 -PstartSeed=468 -Plabel=mutation-reads-without-readindex-no-checkquorum -Pverbose` |
| 473 | NOT_LINEARIZABLE | k0 | 90 | `./gradlew chaos -Pruns=1 -PstartSeed=473 -Plabel=mutation-reads-without-readindex-no-checkquorum -Pverbose` |

Per-seed numbers: `chaos-mutation-reads-without-readindex-no-checkquorum-500-seeds.csv`.

## Mutation applied

This run used a deliberately broken build (`scripts/chaos-mutation.sh reads-without-readindex 500`).
Change to `raft/src/main/java/io/github/hard3007/raftkv/raft/RaftNode.java`:

```diff
@@ -257,7 +257,7 @@
 
     private void startRead(ReadIndexCallback callback) {
         // Step 1: the read index. Step 2 needs a round numbered after every round started so far.
-        readsAwaitingConfirmation.add(new PendingRead(commitIndex, heartbeatRound + 1, callback));
+        readsAwaitingConfirmation.add(new PendingRead(commitIndex, 0, callback));
         scheduleHeartbeatRound();
     }
 
```
