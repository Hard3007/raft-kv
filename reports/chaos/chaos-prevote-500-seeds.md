# Chaos report: prevote

- **Command:** `./gradlew chaos -Pruns=500 -Plabel=prevote`
- **Date:** 2026-10-20T09:14:26.491393814Z
- **Git commit:** 048124b (with uncommitted changes)
- **Machine:** Linux 6.8.0-1064-azure amd64, 2 cores (AMD EPYC 7763 64-Core Processor), Java 21, max heap 1024 MB
- **Config:** 5 nodes, 5 clients, 3 keys, 60 ops per client, think time 0-300 ms, a fault action every 100-400 ms (virtual), client attempt timeout 1000 ms / give-up 4000 ms, checker timeout 10 s (wall)
- **Seeds:** 1..500 (500 runs)
- **Total wall time:** 14.1 s

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
| completed operations | 222 | 261.8 | 290 |
| pending writes (unknown outcome) | 3 | 18.9 | 40 |
| reads left out (no definite answer) | 2 | 19.3 | 42 |
| node crashes (power loss) | 13 | 27.3 | 49 |
| partitions | 37 | 74.2 | 124 |
| message-loss periods | 9 | 22.3 | 37 |
| messages lost | 121 | 453.9 | 870 |
| long-delay periods | 12 | 22.2 | 38 |
| virtual time (s) | 44.7 | 78.1 | 116.1 |
| wall time (ms) | 13 | 55.7 | 1356 |

## Runs that did not pass

None.

Per-seed numbers: `chaos-prevote-500-seeds.csv`.
