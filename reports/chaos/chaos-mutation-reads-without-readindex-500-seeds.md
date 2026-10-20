# Chaos report: mutation-reads-without-readindex

- **Command:** `./gradlew chaos -Pruns=500 -Plabel=mutation-reads-without-readindex`
- **Date:** 2026-10-20T08:19:21.160680819Z
- **Git commit:** 39e1934 (with uncommitted changes)
- **Machine:** Linux 6.8.0-1064-azure amd64, 2 cores (AMD EPYC 7763 64-Core Processor), Java 21, max heap 1024 MB
- **Config:** 5 nodes, 5 clients, 3 keys, 60 ops per client, think time 0-300 ms, a fault action every 100-400 ms (virtual), client attempt timeout 1000 ms / give-up 4000 ms, checker timeout 10 s (wall)
- **Seeds:** 1..500 (500 runs)
- **Total wall time:** 14.7 s

## Verdicts

| Verdict | Runs |
|---|---|
| LINEARIZABLE | 466 |
| NOT_LINEARIZABLE | 34 |
| INCONCLUSIVE | 0 |

Only LINEARIZABLE runs count as passing. INCONCLUSIVE means the checker ran out of time.

## Per seed (min / mean / max)

| Measure | Min | Mean | Max |
|---|---|---|---|
| operations | 300 | 300.0 | 300 |
| completed operations | 225 | 265.7 | 290 |
| pending writes (unknown outcome) | 5 | 20.3 | 45 |
| reads left out (no definite answer) | 3 | 14.0 | 31 |
| node crashes (power loss) | 7 | 25.7 | 45 |
| partitions | 30 | 77.8 | 139 |
| message-loss periods | 7 | 20.9 | 38 |
| messages lost | 128 | 421.3 | 743 |
| long-delay periods | 10 | 21.0 | 37 |
| virtual time (s) | 42.6 | 74.1 | 118.9 |
| wall time (ms) | 12 | 58.0 | 1384 |

## Runs that did not pass

| Seed | Verdict | Key | Ops on key | Reproduce |
|---|---|---|---|---|
| 17 | NOT_LINEARIZABLE | k0 | 100 | `./gradlew chaos -Pruns=1 -PstartSeed=17 -Plabel=mutation-reads-without-readindex -Pverbose` |
| 26 | NOT_LINEARIZABLE | k0 | 95 | `./gradlew chaos -Pruns=1 -PstartSeed=26 -Plabel=mutation-reads-without-readindex -Pverbose` |
| 34 | NOT_LINEARIZABLE | k0 | 91 | `./gradlew chaos -Pruns=1 -PstartSeed=34 -Plabel=mutation-reads-without-readindex -Pverbose` |
| 71 | NOT_LINEARIZABLE | k2 | 104 | `./gradlew chaos -Pruns=1 -PstartSeed=71 -Plabel=mutation-reads-without-readindex -Pverbose` |
| 76 | NOT_LINEARIZABLE | k2 | 98 | `./gradlew chaos -Pruns=1 -PstartSeed=76 -Plabel=mutation-reads-without-readindex -Pverbose` |
| 86 | NOT_LINEARIZABLE | k1 | 95 | `./gradlew chaos -Pruns=1 -PstartSeed=86 -Plabel=mutation-reads-without-readindex -Pverbose` |
| 99 | NOT_LINEARIZABLE | k1 | 81 | `./gradlew chaos -Pruns=1 -PstartSeed=99 -Plabel=mutation-reads-without-readindex -Pverbose` |
| 108 | NOT_LINEARIZABLE | k2 | 99 | `./gradlew chaos -Pruns=1 -PstartSeed=108 -Plabel=mutation-reads-without-readindex -Pverbose` |
| 133 | NOT_LINEARIZABLE | k2 | 103 | `./gradlew chaos -Pruns=1 -PstartSeed=133 -Plabel=mutation-reads-without-readindex -Pverbose` |
| 140 | NOT_LINEARIZABLE | k0 | 94 | `./gradlew chaos -Pruns=1 -PstartSeed=140 -Plabel=mutation-reads-without-readindex -Pverbose` |
| 181 | NOT_LINEARIZABLE | k2 | 98 | `./gradlew chaos -Pruns=1 -PstartSeed=181 -Plabel=mutation-reads-without-readindex -Pverbose` |
| 187 | NOT_LINEARIZABLE | k1 | 100 | `./gradlew chaos -Pruns=1 -PstartSeed=187 -Plabel=mutation-reads-without-readindex -Pverbose` |
| 215 | NOT_LINEARIZABLE | k2 | 88 | `./gradlew chaos -Pruns=1 -PstartSeed=215 -Plabel=mutation-reads-without-readindex -Pverbose` |
| 228 | NOT_LINEARIZABLE | k0 | 97 | `./gradlew chaos -Pruns=1 -PstartSeed=228 -Plabel=mutation-reads-without-readindex -Pverbose` |
| 237 | NOT_LINEARIZABLE | k0 | 101 | `./gradlew chaos -Pruns=1 -PstartSeed=237 -Plabel=mutation-reads-without-readindex -Pverbose` |
| 241 | NOT_LINEARIZABLE | k2 | 105 | `./gradlew chaos -Pruns=1 -PstartSeed=241 -Plabel=mutation-reads-without-readindex -Pverbose` |
| 247 | NOT_LINEARIZABLE | k2 | 105 | `./gradlew chaos -Pruns=1 -PstartSeed=247 -Plabel=mutation-reads-without-readindex -Pverbose` |
| 250 | NOT_LINEARIZABLE | k1 | 83 | `./gradlew chaos -Pruns=1 -PstartSeed=250 -Plabel=mutation-reads-without-readindex -Pverbose` |
| 262 | NOT_LINEARIZABLE | k2 | 96 | `./gradlew chaos -Pruns=1 -PstartSeed=262 -Plabel=mutation-reads-without-readindex -Pverbose` |
| 277 | NOT_LINEARIZABLE | k2 | 101 | `./gradlew chaos -Pruns=1 -PstartSeed=277 -Plabel=mutation-reads-without-readindex -Pverbose` |
| 313 | NOT_LINEARIZABLE | k1 | 96 | `./gradlew chaos -Pruns=1 -PstartSeed=313 -Plabel=mutation-reads-without-readindex -Pverbose` |
| 316 | NOT_LINEARIZABLE | k0 | 85 | `./gradlew chaos -Pruns=1 -PstartSeed=316 -Plabel=mutation-reads-without-readindex -Pverbose` |
| 326 | NOT_LINEARIZABLE | k0 | 96 | `./gradlew chaos -Pruns=1 -PstartSeed=326 -Plabel=mutation-reads-without-readindex -Pverbose` |
| 344 | NOT_LINEARIZABLE | k0 | 114 | `./gradlew chaos -Pruns=1 -PstartSeed=344 -Plabel=mutation-reads-without-readindex -Pverbose` |
| 368 | NOT_LINEARIZABLE | k1 | 85 | `./gradlew chaos -Pruns=1 -PstartSeed=368 -Plabel=mutation-reads-without-readindex -Pverbose` |
| 374 | NOT_LINEARIZABLE | k2 | 78 | `./gradlew chaos -Pruns=1 -PstartSeed=374 -Plabel=mutation-reads-without-readindex -Pverbose` |
| 401 | NOT_LINEARIZABLE | k0 | 108 | `./gradlew chaos -Pruns=1 -PstartSeed=401 -Plabel=mutation-reads-without-readindex -Pverbose` |
| 402 | NOT_LINEARIZABLE | k1 | 105 | `./gradlew chaos -Pruns=1 -PstartSeed=402 -Plabel=mutation-reads-without-readindex -Pverbose` |
| 409 | NOT_LINEARIZABLE | k0 | 101 | `./gradlew chaos -Pruns=1 -PstartSeed=409 -Plabel=mutation-reads-without-readindex -Pverbose` |
| 428 | NOT_LINEARIZABLE | k0 | 81 | `./gradlew chaos -Pruns=1 -PstartSeed=428 -Plabel=mutation-reads-without-readindex -Pverbose` |
| 432 | NOT_LINEARIZABLE | k1 | 103 | `./gradlew chaos -Pruns=1 -PstartSeed=432 -Plabel=mutation-reads-without-readindex -Pverbose` |
| 462 | NOT_LINEARIZABLE | k0 | 85 | `./gradlew chaos -Pruns=1 -PstartSeed=462 -Plabel=mutation-reads-without-readindex -Pverbose` |
| 467 | NOT_LINEARIZABLE | k0 | 97 | `./gradlew chaos -Pruns=1 -PstartSeed=467 -Plabel=mutation-reads-without-readindex -Pverbose` |
| 477 | NOT_LINEARIZABLE | k0 | 86 | `./gradlew chaos -Pruns=1 -PstartSeed=477 -Plabel=mutation-reads-without-readindex -Pverbose` |

Per-seed numbers: `chaos-mutation-reads-without-readindex-500-seeds.csv`.

## Mutation applied

This run used a deliberately broken build (`scripts/chaos-mutation.sh reads-without-readindex 500`).
Change to `raft/src/main/java/io/github/hard3007/raftkv/raft/RaftNode.java`:

```diff
@@ -200,7 +200,7 @@
 
     private void startRead(ReadIndexCallback callback) {
         // Step 1: the read index. Step 2 needs a round numbered after every round started so far.
-        readsAwaitingConfirmation.add(new PendingRead(commitIndex, heartbeatRound + 1, callback));
+        readsAwaitingConfirmation.add(new PendingRead(commitIndex, 0, callback));
         scheduleHeartbeatRound();
     }
 
```
