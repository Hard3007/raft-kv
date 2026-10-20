# Chaos report: mutation-reads-without-readindex-no-checkquorum

- **Command:** `./gradlew chaos -Pruns=200 -Plabel=mutation-reads-without-readindex-no-checkquorum -PcheckQuorum=false`
- **Date:** 2026-10-20T09:48:28.813569951Z
- **Git commit:** 1c764a9 (with uncommitted changes)
- **Machine:** Linux 6.8.0-1064-azure amd64, 2 cores (AMD EPYC 7763 64-Core Processor), Java 21, max heap 1024 MB
- **Config:** 5 nodes, 5 clients, 3 keys, 60 ops per client, think time 0-300 ms, a fault action every 100-400 ms (virtual), client attempt timeout 1000 ms / give-up 4000 ms, checker timeout 10 s (wall); PreVote on, CheckQuorum off, a snapshot every 20 entries in 64-byte InstallSnapshot chunks
- **Seeds:** 1..200 (200 runs)
- **Total wall time:** 11.3 s

## Verdicts

| Verdict | Runs |
|---|---|
| LINEARIZABLE | 178 |
| NOT_LINEARIZABLE | 22 |
| INCONCLUSIVE | 0 |
| runs where the system failed an internal check | 0 |

Only LINEARIZABLE runs count as passing. INCONCLUSIVE means the checker ran out of time. An internal-check failure (a safety assertion, or a node refusing to start because committed entries were lost) has no verdict and does not pass either.

## Per seed (min / mean / max)

| Measure | Min | Mean | Max |
|---|---|---|---|
| operations | 300 | 300.0 | 300 |
| completed operations | 222 | 256.3 | 289 |
| pending writes (unknown outcome) | 9 | 26.7 | 50 |
| reads left out (no definite answer) | 2 | 17.0 | 38 |
| node crashes (power loss) | 9 | 25.5 | 40 |
|   of which mid-I/O (crash at a random write/fsync/rename) | 5 | 13.3 | 26 |
| node pauses (frozen 0.5-2 s) | 7 | 23.7 | 38 |
| partitions | 35 | 69.0 | 103 |
| message-loss periods | 9 | 18.2 | 32 |
| messages lost | 135 | 445.0 | 817 |
| long-delay periods | 8 | 18.1 | 32 |
| snapshots taken (all nodes) | 21 | 26.9 | 35 |
| InstallSnapshots completed (all nodes) | 6 | 11.0 | 16 |
| final term (highest across nodes) | 10 | 36.9 | 65 |
| virtual time (s) | 44.6 | 82.1 | 115.7 |
| wall time (ms) | 14 | 110.2 | 4810 |

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

Per-seed numbers: `chaos-mutation-reads-without-readindex-no-checkquorum-200-seeds.csv`.

## Mutation applied

This run used a deliberately broken build (`scripts/chaos-mutation.sh reads-without-readindex 200`).
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
