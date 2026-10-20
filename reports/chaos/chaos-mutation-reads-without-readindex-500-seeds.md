# Chaos report: mutation-reads-without-readindex

- **Command:** `./gradlew chaos -Pruns=500 -Plabel=mutation-reads-without-readindex`
- **Date:** 2026-10-20T09:41:22.731325579Z
- **Git commit:** 1c764a9 (with uncommitted changes)
- **Machine:** Linux 6.8.0-1064-azure amd64, 2 cores (AMD EPYC 7763 64-Core Processor), Java 21, max heap 1024 MB
- **Config:** 5 nodes, 5 clients, 3 keys, 60 ops per client, think time 0-300 ms, a fault action every 100-400 ms (virtual), client attempt timeout 1000 ms / give-up 4000 ms, checker timeout 10 s (wall); PreVote on, CheckQuorum on, a snapshot every 20 entries in 64-byte InstallSnapshot chunks
- **Seeds:** 1..500 (500 runs)
- **Total wall time:** 12.8 s

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
| completed operations | 213 | 256.4 | 291 |
| pending writes (unknown outcome) | 4 | 25.1 | 54 |
| reads left out (no definite answer) | 3 | 18.6 | 42 |
| node crashes (power loss) | 8 | 28.4 | 48 |
|   of which mid-I/O (crash at a random write/fsync/rename) | 3 | 14.1 | 30 |
| partitions | 24 | 65.1 | 112 |
| message-loss periods | 5 | 20.2 | 37 |
| messages lost | 141 | 429.5 | 757 |
| long-delay periods | 6 | 19.7 | 41 |
| snapshots taken (all nodes) | 20 | 29.0 | 38 |
| InstallSnapshots completed (all nodes) | 4 | 11.6 | 18 |
| final term (highest across nodes) | 17 | 58.4 | 105 |
| virtual time (s) | 32.0 | 80.6 | 136.3 |
| wall time (ms) | 13 | 50.4 | 1187 |

## Runs that did not pass

| Seed | Verdict | Key / failure | Ops on key | Reproduce |
|---|---|---|---|---|
| 42 | NOT_LINEARIZABLE | k1 | 103 | `./gradlew chaos -Pruns=1 -PstartSeed=42 -Plabel=mutation-reads-without-readindex -Pverbose` |
| 174 | NOT_LINEARIZABLE | k0 | 115 | `./gradlew chaos -Pruns=1 -PstartSeed=174 -Plabel=mutation-reads-without-readindex -Pverbose` |

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
