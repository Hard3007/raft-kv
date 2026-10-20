# Chaos report: docker-like

- **Command:** `./gradlew chaos -Pruns=100 -Plabel=docker-like`
- **Date:** 2026-10-20T10:34:32.57675024Z
- **Git commit:** 920f53e (with uncommitted changes)
- **Machine:** Linux 6.8.0-1064-azure amd64, 2 cores (AMD EPYC 7763 64-Core Processor), Java 21, max heap 1024 MB
- **Config:** 5 nodes, 4 clients, 3 keys, 1000 ops per client, think time 0-50 ms, a fault action every 100-400 ms (virtual), client attempt timeout 1000 ms / give-up 4000 ms, checker timeout 10 s (wall); PreVote on, CheckQuorum on, a snapshot every 200 entries in 262144-byte InstallSnapshot chunks
- **Seeds:** 1..100 (100 runs)
- **Total wall time:** 22.5 s

## Verdicts

| Verdict | Runs |
|---|---|
| LINEARIZABLE | 100 |
| NOT_LINEARIZABLE | 0 |
| INCONCLUSIVE | 0 |
| runs where the system failed an internal check | 0 |

Only LINEARIZABLE runs count as passing. INCONCLUSIVE means the checker ran out of time. An internal-check failure (a safety assertion, or a node refusing to start because committed entries were lost) has no verdict and does not pass either.

## Per seed (min / mean / max)

| Measure | Min | Mean | Max |
|---|---|---|---|
| operations | 4000 | 4000.0 | 4000 |
| completed operations | 3500 | 3629.3 | 3760 |
| pending writes (unknown outcome) | 125 | 191.6 | 265 |
| reads left out (no definite answer) | 103 | 179.1 | 269 |
| node crashes (power loss) | 148 | 203.3 | 271 |
|   of which mid-I/O (crash at a random write/fsync/rename) | 79 | 105.8 | 140 |
| node pauses (frozen 0.5-2 s) | 132 | 190.3 | 266 |
| partitions | 299 | 447.1 | 597 |
| message-loss periods | 99 | 144.1 | 205 |
| messages lost | 2289 | 3314.3 | 4644 |
| long-delay periods | 98 | 143.7 | 183 |
| snapshots taken (all nodes) | 31 | 36.5 | 43 |
| InstallSnapshots completed (all nodes) | 12 | 17.3 | 22 |
| final term (highest across nodes) | 291 | 434.1 | 589 |
| virtual time (s) | 459.4 | 647.0 | 848.7 |
| wall time (ms) | 152 | 448.2 | 2901 |

## Runs that did not pass

None.

Per-seed numbers: `chaos-docker-like-100-seeds-920f53e-dirty-20261020T103409Z.csv`.
