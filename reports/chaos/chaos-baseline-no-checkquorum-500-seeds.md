# Chaos report: baseline-no-checkquorum

- **Command:** `./gradlew chaos -Pruns=500 -Plabel=baseline-no-checkquorum -PcheckQuorum=false`
- **Date:** 2026-10-20T09:50:12.630394809Z
- **Git commit:** 61dda62 (with uncommitted changes)
- **Machine:** Linux 6.8.0-1064-azure amd64, 2 cores (AMD EPYC 7763 64-Core Processor), Java 21, max heap 1024 MB
- **Config:** 5 nodes, 5 clients, 3 keys, 60 ops per client, think time 0-300 ms, a fault action every 100-400 ms (virtual), client attempt timeout 1000 ms / give-up 4000 ms, checker timeout 10 s (wall); PreVote on, CheckQuorum off, a snapshot every 20 entries in 64-byte InstallSnapshot chunks
- **Seeds:** 1..500 (500 runs)
- **Total wall time:** 13.6 s

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
| completed operations | 203 | 251.6 | 288 |
| pending writes (unknown outcome) | 5 | 24.9 | 53 |
| reads left out (no definite answer) | 5 | 23.5 | 51 |
| node crashes (power loss) | 11 | 27.1 | 44 |
|   of which mid-I/O (crash at a random write/fsync/rename) | 2 | 13.7 | 26 |
| node pauses (frozen 0.5-2 s) | 6 | 25.6 | 46 |
| partitions | 30 | 73.3 | 136 |
| message-loss periods | 6 | 19.4 | 40 |
| messages lost | 87 | 452.7 | 875 |
| long-delay periods | 8 | 19.5 | 33 |
| snapshots taken (all nodes) | 20 | 27.3 | 41 |
| InstallSnapshots completed (all nodes) | 6 | 11.4 | 17 |
| final term (highest across nodes) | 13 | 40.4 | 89 |
| virtual time (s) | 34.6 | 87.4 | 146.0 |
| wall time (ms) | 13 | 53.9 | 1320 |

## Runs that did not pass

None.

Per-seed numbers: `chaos-baseline-no-checkquorum-500-seeds.csv`.
