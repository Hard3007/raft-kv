# Chaos report: baseline

- **Command:** `./gradlew chaos -Pruns=500`
- **Date:** 2026-10-20T09:49:56.627512467Z
- **Git commit:** 61dda62 (with uncommitted changes)
- **Machine:** Linux 6.8.0-1064-azure amd64, 2 cores (AMD EPYC 7763 64-Core Processor), Java 21, max heap 1024 MB
- **Config:** 5 nodes, 5 clients, 3 keys, 60 ops per client, think time 0-300 ms, a fault action every 100-400 ms (virtual), client attempt timeout 1000 ms / give-up 4000 ms, checker timeout 10 s (wall); PreVote on, CheckQuorum on, a snapshot every 20 entries in 64-byte InstallSnapshot chunks
- **Seeds:** 1..500 (500 runs)
- **Total wall time:** 12.9 s

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
| completed operations | 193 | 240.0 | 289 |
| pending writes (unknown outcome) | 6 | 30.7 | 59 |
| reads left out (no definite answer) | 5 | 29.3 | 64 |
| node crashes (power loss) | 13 | 30.5 | 46 |
|   of which mid-I/O (crash at a random write/fsync/rename) | 4 | 15.6 | 29 |
| node pauses (frozen 0.5-2 s) | 7 | 28.8 | 52 |
| partitions | 16 | 66.9 | 110 |
| message-loss periods | 9 | 21.8 | 42 |
| messages lost | 140 | 469.5 | 918 |
| long-delay periods | 9 | 21.7 | 38 |
| snapshots taken (all nodes) | 22 | 29.3 | 39 |
| InstallSnapshots completed (all nodes) | 4 | 11.5 | 19 |
| final term (highest across nodes) | 16 | 66.5 | 109 |
| virtual time (s) | 38.2 | 98.2 | 152.6 |
| wall time (ms) | 13 | 51.2 | 1063 |

## Runs that did not pass

None.

Per-seed numbers: `chaos-baseline-500-seeds.csv`.
