# Chaos report: baseline

- **Command:** `./gradlew chaos -Pruns=500`
- **Date:** 2026-10-20T08:17:38.405577599Z
- **Git commit:** 39e1934
- **Machine:** Linux 6.8.0-1064-azure amd64, 2 cores (AMD EPYC 7763 64-Core Processor), Java 21, max heap 1024 MB
- **Config:** 5 nodes, 5 clients, 3 keys, 60 ops per client, think time 0-300 ms, a fault action every 100-400 ms (virtual), client attempt timeout 1000 ms / give-up 4000 ms, checker timeout 10 s (wall)
- **Seeds:** 1..500 (500 runs)
- **Total wall time:** 14.6 s

## Verdicts

| Verdict | Runs |
|---|---|
| LINEARIZABLE | 500 |
| NOT_LINEARIZABLE | 0 |
| INCONCLUSIVE | 0 |

Only LINEARIZABLE runs count as passing. INCONCLUSIVE means the checker ran out of time.

## Per seed (min / mean / max)

| Measure | Min | Mean | Max |
|---|---|---|---|
| operations | 300 | 300.0 | 300 |
| completed operations | 222 | 262.1 | 291 |
| pending writes (unknown outcome) | 3 | 18.6 | 40 |
| reads left out (no definite answer) | 3 | 19.3 | 45 |
| node crashes (power loss) | 14 | 27.5 | 49 |
| partitions | 37 | 82.9 | 144 |
| message-loss periods | 10 | 22.2 | 37 |
| messages lost | 156 | 438.6 | 774 |
| long-delay periods | 10 | 22.4 | 40 |
| virtual time (s) | 44.8 | 78.7 | 124.5 |
| wall time (ms) | 11 | 57.8 | 1232 |

## Runs that did not pass

None.

Per-seed numbers: `chaos-baseline-500-seeds.csv`.
