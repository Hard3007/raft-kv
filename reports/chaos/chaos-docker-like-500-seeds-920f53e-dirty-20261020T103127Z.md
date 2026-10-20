# Chaos report: docker-like

- **Command:** `./gradlew chaos -Pruns=500 -Plabel=docker-like`
- **Date:** 2026-10-20T10:31:50.874660552Z
- **Git commit:** 920f53e (with uncommitted changes)
- **Machine:** Linux 6.8.0-1064-azure amd64, 2 cores (AMD EPYC 7763 64-Core Processor), Java 21, max heap 1024 MB
- **Config:** 5 nodes, 4 clients, 3 keys, 200 ops per client, think time 0-50 ms, a fault action every 100-400 ms (virtual), client attempt timeout 1000 ms / give-up 4000 ms, checker timeout 10 s (wall); PreVote on, CheckQuorum on, a snapshot every 200 entries in 262144-byte InstallSnapshot chunks
- **Seeds:** 1..500 (500 runs)
- **Total wall time:** 23.3 s

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
| operations | 800 | 800.0 | 800 |
| completed operations | 661 | 729.1 | 783 |
| pending writes (unknown outcome) | 7 | 37.2 | 79 |
| reads left out (no definite answer) | 5 | 33.7 | 68 |
| node crashes (power loss) | 16 | 43.5 | 87 |
|   of which mid-I/O (crash at a random write/fsync/rename) | 5 | 22.6 | 52 |
| node pauses (frozen 0.5-2 s) | 14 | 40.3 | 81 |
| partitions | 35 | 95.1 | 189 |
| message-loss periods | 9 | 30.6 | 61 |
| messages lost | 187 | 695.5 | 1420 |
| long-delay periods | 10 | 30.9 | 64 |
| snapshots taken (all nodes) | 4 | 6.7 | 9 |
| InstallSnapshots completed (all nodes) | 0 | 3.1 | 5 |
| final term (highest across nodes) | 28 | 93.4 | 191 |
| virtual time (s) | 52.1 | 138.7 | 272.8 |
| wall time (ms) | 23 | 92.6 | 1935 |

## Runs that did not pass

None.

Per-seed numbers: `chaos-docker-like-500-seeds-920f53e-dirty-20261020T103127Z.csv`.
