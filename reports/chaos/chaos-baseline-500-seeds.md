# Chaos report: baseline

- **Command:** `./gradlew chaos -Pruns=500`
- **Date:** 2026-10-20T09:41:07.252636258Z
- **Git commit:** 1c764a9
- **Machine:** Linux 6.8.0-1064-azure amd64, 2 cores (AMD EPYC 7763 64-Core Processor), Java 21, max heap 1024 MB
- **Config:** 5 nodes, 5 clients, 3 keys, 60 ops per client, think time 0-300 ms, a fault action every 100-400 ms (virtual), client attempt timeout 1000 ms / give-up 4000 ms, checker timeout 10 s (wall); PreVote on, CheckQuorum on, a snapshot every 20 entries in 64-byte InstallSnapshot chunks
- **Seeds:** 1..500 (500 runs)
- **Total wall time:** 12.7 s

## Verdicts

| Verdict | Runs |
|---|---|
| LINEARIZABLE | 500 |
| NOT_LINEARIZABLE | 0 |
| INCONCLUSIVE | 0 |
| runs where the system failed an internal check | 0 |

Only LINEARIZABLE runs count as passing. INCONCLUSIVE means the checker ran out of time. An internal-check failure (a safety assertion, or a node refusing to start because committed entries were lost) has no verdict and does not pass either.

## Per seed (min / mean / max)

| Measure | Min | Mean | Max |
|---|---|---|---|
| operations | 300 | 300.0 | 300 |
| completed operations | 197 | 253.8 | 292 |
| pending writes (unknown outcome) | 4 | 23.5 | 62 |
| reads left out (no definite answer) | 2 | 22.7 | 52 |
| node crashes (power loss) | 13 | 29.3 | 49 |
|   of which mid-I/O (crash at a random write/fsync/rename) | 4 | 15.1 | 31 |
| partitions | 27 | 67.8 | 123 |
| message-loss periods | 8 | 21.2 | 46 |
| messages lost | 155 | 446.6 | 747 |
| long-delay periods | 9 | 21.0 | 40 |
| snapshots taken (all nodes) | 22 | 29.5 | 39 |
| InstallSnapshots completed (all nodes) | 6 | 11.9 | 19 |
| final term (highest across nodes) | 24 | 61.2 | 108 |
| virtual time (s) | 37.4 | 84.7 | 139.5 |
| wall time (ms) | 12 | 49.9 | 1148 |

## Runs that did not pass

None.

Per-seed numbers: `chaos-baseline-500-seeds.csv`.
