# Chaos report: baseline

- **Command:** `./gradlew chaos -Pruns=10000`
- **Date:** 2026-10-20T09:57:07.655826859Z
- **Git commit:** 43a3adb
- **Machine:** Linux 6.8.0-1064-azure amd64, 2 cores (AMD EPYC 7763 64-Core Processor), Java 21, max heap 1024 MB
- **Config:** 5 nodes, 5 clients, 3 keys, 60 ops per client, think time 0-300 ms, a fault action every 100-400 ms (virtual), client attempt timeout 1000 ms / give-up 4000 ms, checker timeout 10 s (wall); PreVote on, CheckQuorum on, a snapshot every 20 entries in 64-byte InstallSnapshot chunks
- **Seeds:** 1..10000 (10000 runs)
- **Total wall time:** 96.0 s

## Verdicts

| Verdict | Runs |
|---|---|
| LINEARIZABLE | 10000 |
| NOT_LINEARIZABLE | 0 |
| INCONCLUSIVE | 0 |
| runs where the system failed an internal check | 0 |

Only LINEARIZABLE runs count as passing. INCONCLUSIVE means the checker ran out of time. An internal-check failure (a safety assertion, or a node refusing to start because committed entries were lost) has no verdict and does not pass either.

## Per seed (min / mean / max)

| Measure | Min | Mean | Max |
|---|---|---|---|
| operations | 300 | 300.0 | 300 |
| completed operations | 164 | 240.1 | 290 |
| pending writes (unknown outcome) | 5 | 30.7 | 70 |
| reads left out (no definite answer) | 2 | 29.2 | 68 |
| node crashes (power loss) | 11 | 30.5 | 56 |
|   of which mid-I/O (crash at a random write/fsync/rename) | 2 | 15.7 | 35 |
| node pauses (frozen 0.5-2 s) | 6 | 28.7 | 60 |
| partitions | 16 | 67.3 | 124 |
| message-loss periods | 6 | 21.6 | 43 |
| messages lost | 87 | 474.9 | 974 |
| long-delay periods | 6 | 21.8 | 43 |
| snapshots taken (all nodes) | 19 | 29.3 | 41 |
| InstallSnapshots completed (all nodes) | 4 | 11.7 | 20 |
| final term (highest across nodes) | 16 | 66.4 | 150 |
| virtual time (s) | 37.5 | 98.1 | 179.7 |
| wall time (ms) | 8 | 19.1 | 1187 |

## Runs that did not pass

None.

Per-seed numbers: `chaos-baseline-10000-seeds.csv`.
