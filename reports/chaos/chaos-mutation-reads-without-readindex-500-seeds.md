# Chaos report: mutation-reads-without-readindex

- **Command:** `./gradlew chaos -Pruns=500 -Plabel=mutation-reads-without-readindex`
- **Date:** 2026-10-20T09:50:31.217929455Z
- **Git commit:** 61dda62 (with uncommitted changes)
- **Machine:** Linux 6.8.0-1064-azure amd64, 2 cores (AMD EPYC 7763 64-Core Processor), Java 21, max heap 1024 MB
- **Config:** 5 nodes, 5 clients, 3 keys, 60 ops per client, think time 0-300 ms, a fault action every 100-400 ms (virtual), client attempt timeout 1000 ms / give-up 4000 ms, checker timeout 10 s (wall); PreVote on, CheckQuorum on, a snapshot every 20 entries in 64-byte InstallSnapshot chunks
- **Seeds:** 1..500 (500 runs)
- **Total wall time:** 16.1 s

## Verdicts

| Verdict | Runs |
|---|---|
| LINEARIZABLE | 498 |
| NOT_LINEARIZABLE | 2 |
| INCONCLUSIVE | 0 |
| runs where the system failed an internal check | 0 |

Only LINEARIZABLE runs count as passing. INCONCLUSIVE means the checker ran out of time. An internal-check failure (a safety assertion, or a node refusing to start because committed entries were lost) has no verdict and does not pass either.

## Per seed (min / mean / max)

| Measure | Min | Mean | Max |
|---|---|---|---|
| operations | 300 | 300.0 | 300 |
| completed operations | 192 | 244.0 | 286 |
| pending writes (unknown outcome) | 6 | 32.5 | 61 |
| reads left out (no definite answer) | 3 | 23.5 | 49 |
| node crashes (power loss) | 12 | 29.2 | 45 |
|   of which mid-I/O (crash at a random write/fsync/rename) | 3 | 15.0 | 29 |
| node pauses (frozen 0.5-2 s) | 9 | 27.5 | 50 |
| partitions | 24 | 65.1 | 113 |
| message-loss periods | 8 | 20.7 | 35 |
| messages lost | 170 | 459.8 | 849 |
| long-delay periods | 7 | 20.6 | 37 |
| snapshots taken (all nodes) | 20 | 28.7 | 38 |
| InstallSnapshots completed (all nodes) | 6 | 11.6 | 18 |
| final term (highest across nodes) | 19 | 62.7 | 99 |
| virtual time (s) | 41.3 | 93.7 | 140.7 |
| wall time (ms) | 12 | 63.7 | 5184 |

## Runs that did not pass

| Seed | Verdict | Key / failure | Ops on key | Reproduce |
|---|---|---|---|---|
| 164 | NOT_LINEARIZABLE | k1 | 92 | `./gradlew chaos -Pruns=1 -PstartSeed=164 -Plabel=mutation-reads-without-readindex -Pverbose` |
| 428 | NOT_LINEARIZABLE | k2 | 85 | `./gradlew chaos -Pruns=1 -PstartSeed=428 -Plabel=mutation-reads-without-readindex -Pverbose` |

Per-seed numbers: `chaos-mutation-reads-without-readindex-500-seeds.csv`.

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
