# Chaos report: mutation-reads-without-readindex

- **Command:** `./gradlew chaos -Pruns=200 -Plabel=mutation-reads-without-readindex`
- **Date:** 2026-10-20T09:47:02.395856114Z
- **Git commit:** 1c764a9 (with uncommitted changes)
- **Machine:** Linux 6.8.0-1064-azure amd64, 2 cores (AMD EPYC 7763 64-Core Processor), Java 21, max heap 1024 MB
- **Config:** 5 nodes, 5 clients, 3 keys, 60 ops per client, think time 0-300 ms, a fault action every 100-400 ms (virtual), client attempt timeout 2000 ms / give-up 6000 ms, checker timeout 10 s (wall); PreVote on, CheckQuorum on, a snapshot every 20 entries in 64-byte InstallSnapshot chunks
- **Seeds:** 1..200 (200 runs)
- **Total wall time:** 9.0 s

## Verdicts

| Verdict | Runs |
|---|---|
| LINEARIZABLE | 200 |
| NOT_LINEARIZABLE | 0 |
| INCONCLUSIVE | 0 |
| runs where the system failed an internal check | 0 |

Only LINEARIZABLE runs count as passing. INCONCLUSIVE means the checker ran out of time. An internal-check failure (a safety assertion, or a node refusing to start because committed entries were lost) has no verdict and does not pass either.

## Per seed (min / mean / max)

| Measure | Min | Mean | Max |
|---|---|---|---|
| operations | 300 | 300.0 | 300 |
| completed operations | 194 | 236.2 | 277 |
| pending writes (unknown outcome) | 12 | 36.9 | 59 |
| reads left out (no definite answer) | 6 | 26.9 | 52 |
| node crashes (power loss) | 21 | 44.2 | 72 |
|   of which mid-I/O (crash at a random write/fsync/rename) | 10 | 23.6 | 43 |
| node pauses (frozen 0.5-2 s) | 18 | 42.8 | 69 |
| partitions | 42 | 101.8 | 165 |
| message-loss periods | 10 | 32.2 | 51 |
| messages lost | 296 | 660.4 | 1129 |
| long-delay periods | 17 | 32.3 | 55 |
| snapshots taken (all nodes) | 23 | 31.7 | 43 |
| InstallSnapshots completed (all nodes) | 7 | 13.3 | 18 |
| final term (highest across nodes) | 41 | 99.4 | 146 |
| virtual time (s) | 67.7 | 146.1 | 209.4 |
| wall time (ms) | 22 | 88.8 | 1357 |

## Runs that did not pass

None.

Per-seed numbers: `chaos-mutation-reads-without-readindex-200-seeds.csv`.

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
