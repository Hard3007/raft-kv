# Chaos report: mutation-follower-reads

- **Command:** `./gradlew chaos -Pruns=500 -Plabel=mutation-follower-reads`
- **Date:** 2026-10-20T09:42:16.916541272Z
- **Git commit:** 1c764a9 (with uncommitted changes)
- **Machine:** Linux 6.8.0-1064-azure amd64, 2 cores (AMD EPYC 7763 64-Core Processor), Java 21, max heap 1024 MB
- **Config:** 5 nodes, 5 clients, 3 keys, 60 ops per client, think time 0-300 ms, a fault action every 100-400 ms (virtual), client attempt timeout 1000 ms / give-up 4000 ms, checker timeout 10 s (wall); PreVote on, CheckQuorum on, a snapshot every 20 entries in 64-byte InstallSnapshot chunks
- **Seeds:** 1..500 (500 runs)
- **Total wall time:** 33.2 s

## Verdicts

| Verdict | Runs |
|---|---|
| LINEARIZABLE | 46 |
| NOT_LINEARIZABLE | 453 |
| INCONCLUSIVE | 1 |
| runs where the system failed an internal check | 0 |

Only LINEARIZABLE runs count as passing. INCONCLUSIVE means the checker ran out of time. An internal-check failure (a safety assertion, or a node refusing to start because committed entries were lost) has no verdict and does not pass either.

## Per seed (min / mean / max)

| Measure | Min | Mean | Max |
|---|---|---|---|
| operations | 300 | 300.0 | 300 |
| completed operations | 231 | 266.5 | 292 |
| pending writes (unknown outcome) | 6 | 26.9 | 60 |
| reads left out (no definite answer) | 0 | 6.6 | 18 |
| node crashes (power loss) | 13 | 26.4 | 50 |
|   of which mid-I/O (crash at a random write/fsync/rename) | 4 | 13.4 | 28 |
| partitions | 19 | 59.7 | 105 |
| message-loss periods | 8 | 18.5 | 35 |
| messages lost | 151 | 399.7 | 788 |
| long-delay periods | 8 | 18.7 | 32 |
| snapshots taken (all nodes) | 21 | 28.2 | 38 |
| InstallSnapshots completed (all nodes) | 4 | 11.1 | 16 |
| final term (highest across nodes) | 14 | 53.9 | 102 |
| virtual time (s) | 36.5 | 74.7 | 119.2 |
| wall time (ms) | 9 | 130.5 | 10093 |

## Runs that did not pass

| Seed | Verdict | Key / failure | Ops on key | Reproduce |
|---|---|---|---|---|
| 1 | NOT_LINEARIZABLE | k0 | 107 | `./gradlew chaos -Pruns=1 -PstartSeed=1 -Plabel=mutation-follower-reads -Pverbose` |
| 2 | NOT_LINEARIZABLE | k0 | 106 | `./gradlew chaos -Pruns=1 -PstartSeed=2 -Plabel=mutation-follower-reads -Pverbose` |
| 3 | NOT_LINEARIZABLE | k2 | 95 | `./gradlew chaos -Pruns=1 -PstartSeed=3 -Plabel=mutation-follower-reads -Pverbose` |
| 4 | NOT_LINEARIZABLE | k0 | 94 | `./gradlew chaos -Pruns=1 -PstartSeed=4 -Plabel=mutation-follower-reads -Pverbose` |
| 5 | NOT_LINEARIZABLE | k0 | 106 | `./gradlew chaos -Pruns=1 -PstartSeed=5 -Plabel=mutation-follower-reads -Pverbose` |
| 6 | NOT_LINEARIZABLE | k0 | 112 | `./gradlew chaos -Pruns=1 -PstartSeed=6 -Plabel=mutation-follower-reads -Pverbose` |
| 7 | NOT_LINEARIZABLE | k1 | 94 | `./gradlew chaos -Pruns=1 -PstartSeed=7 -Plabel=mutation-follower-reads -Pverbose` |
| 8 | NOT_LINEARIZABLE | k1 | 92 | `./gradlew chaos -Pruns=1 -PstartSeed=8 -Plabel=mutation-follower-reads -Pverbose` |
| 9 | NOT_LINEARIZABLE | k2 | 94 | `./gradlew chaos -Pruns=1 -PstartSeed=9 -Plabel=mutation-follower-reads -Pverbose` |
| 10 | NOT_LINEARIZABLE | k1 | 105 | `./gradlew chaos -Pruns=1 -PstartSeed=10 -Plabel=mutation-follower-reads -Pverbose` |
| 12 | NOT_LINEARIZABLE | k0 | 105 | `./gradlew chaos -Pruns=1 -PstartSeed=12 -Plabel=mutation-follower-reads -Pverbose` |
| 13 | NOT_LINEARIZABLE | k0 | 91 | `./gradlew chaos -Pruns=1 -PstartSeed=13 -Plabel=mutation-follower-reads -Pverbose` |
| 14 | NOT_LINEARIZABLE | k1 | 96 | `./gradlew chaos -Pruns=1 -PstartSeed=14 -Plabel=mutation-follower-reads -Pverbose` |
| 15 | NOT_LINEARIZABLE | k1 | 101 | `./gradlew chaos -Pruns=1 -PstartSeed=15 -Plabel=mutation-follower-reads -Pverbose` |
| 16 | NOT_LINEARIZABLE | k0 | 87 | `./gradlew chaos -Pruns=1 -PstartSeed=16 -Plabel=mutation-follower-reads -Pverbose` |
| 17 | NOT_LINEARIZABLE | k2 | 96 | `./gradlew chaos -Pruns=1 -PstartSeed=17 -Plabel=mutation-follower-reads -Pverbose` |
| 18 | NOT_LINEARIZABLE | k0 | 98 | `./gradlew chaos -Pruns=1 -PstartSeed=18 -Plabel=mutation-follower-reads -Pverbose` |
| 19 | NOT_LINEARIZABLE | k0 | 104 | `./gradlew chaos -Pruns=1 -PstartSeed=19 -Plabel=mutation-follower-reads -Pverbose` |
| 20 | NOT_LINEARIZABLE | k1 | 93 | `./gradlew chaos -Pruns=1 -PstartSeed=20 -Plabel=mutation-follower-reads -Pverbose` |
| 21 | NOT_LINEARIZABLE | k0 | 101 | `./gradlew chaos -Pruns=1 -PstartSeed=21 -Plabel=mutation-follower-reads -Pverbose` |
| 22 | NOT_LINEARIZABLE | k0 | 89 | `./gradlew chaos -Pruns=1 -PstartSeed=22 -Plabel=mutation-follower-reads -Pverbose` |
| 23 | NOT_LINEARIZABLE | k0 | 95 | `./gradlew chaos -Pruns=1 -PstartSeed=23 -Plabel=mutation-follower-reads -Pverbose` |
| 24 | NOT_LINEARIZABLE | k0 | 101 | `./gradlew chaos -Pruns=1 -PstartSeed=24 -Plabel=mutation-follower-reads -Pverbose` |
| 25 | NOT_LINEARIZABLE | k0 | 103 | `./gradlew chaos -Pruns=1 -PstartSeed=25 -Plabel=mutation-follower-reads -Pverbose` |
| 27 | NOT_LINEARIZABLE | k0 | 103 | `./gradlew chaos -Pruns=1 -PstartSeed=27 -Plabel=mutation-follower-reads -Pverbose` |
| 28 | NOT_LINEARIZABLE | k0 | 95 | `./gradlew chaos -Pruns=1 -PstartSeed=28 -Plabel=mutation-follower-reads -Pverbose` |
| 29 | NOT_LINEARIZABLE | k0 | 103 | `./gradlew chaos -Pruns=1 -PstartSeed=29 -Plabel=mutation-follower-reads -Pverbose` |
| 30 | NOT_LINEARIZABLE | k0 | 89 | `./gradlew chaos -Pruns=1 -PstartSeed=30 -Plabel=mutation-follower-reads -Pverbose` |
| 31 | NOT_LINEARIZABLE | k0 | 100 | `./gradlew chaos -Pruns=1 -PstartSeed=31 -Plabel=mutation-follower-reads -Pverbose` |
| 32 | NOT_LINEARIZABLE | k0 | 93 | `./gradlew chaos -Pruns=1 -PstartSeed=32 -Plabel=mutation-follower-reads -Pverbose` |
| 33 | NOT_LINEARIZABLE | k1 | 90 | `./gradlew chaos -Pruns=1 -PstartSeed=33 -Plabel=mutation-follower-reads -Pverbose` |
| 34 | NOT_LINEARIZABLE | k0 | 108 | `./gradlew chaos -Pruns=1 -PstartSeed=34 -Plabel=mutation-follower-reads -Pverbose` |
| 35 | NOT_LINEARIZABLE | k0 | 95 | `./gradlew chaos -Pruns=1 -PstartSeed=35 -Plabel=mutation-follower-reads -Pverbose` |
| 36 | NOT_LINEARIZABLE | k0 | 100 | `./gradlew chaos -Pruns=1 -PstartSeed=36 -Plabel=mutation-follower-reads -Pverbose` |
| 37 | NOT_LINEARIZABLE | k0 | 95 | `./gradlew chaos -Pruns=1 -PstartSeed=37 -Plabel=mutation-follower-reads -Pverbose` |
| 38 | NOT_LINEARIZABLE | k0 | 109 | `./gradlew chaos -Pruns=1 -PstartSeed=38 -Plabel=mutation-follower-reads -Pverbose` |
| 39 | NOT_LINEARIZABLE | k0 | 92 | `./gradlew chaos -Pruns=1 -PstartSeed=39 -Plabel=mutation-follower-reads -Pverbose` |
| 40 | NOT_LINEARIZABLE | k1 | 107 | `./gradlew chaos -Pruns=1 -PstartSeed=40 -Plabel=mutation-follower-reads -Pverbose` |
| 41 | NOT_LINEARIZABLE | k1 | 97 | `./gradlew chaos -Pruns=1 -PstartSeed=41 -Plabel=mutation-follower-reads -Pverbose` |
| 42 | NOT_LINEARIZABLE | k0 | 101 | `./gradlew chaos -Pruns=1 -PstartSeed=42 -Plabel=mutation-follower-reads -Pverbose` |
| 43 | NOT_LINEARIZABLE | k0 | 96 | `./gradlew chaos -Pruns=1 -PstartSeed=43 -Plabel=mutation-follower-reads -Pverbose` |
| 44 | NOT_LINEARIZABLE | k0 | 107 | `./gradlew chaos -Pruns=1 -PstartSeed=44 -Plabel=mutation-follower-reads -Pverbose` |
| 45 | NOT_LINEARIZABLE | k1 | 89 | `./gradlew chaos -Pruns=1 -PstartSeed=45 -Plabel=mutation-follower-reads -Pverbose` |
| 46 | NOT_LINEARIZABLE | k1 | 112 | `./gradlew chaos -Pruns=1 -PstartSeed=46 -Plabel=mutation-follower-reads -Pverbose` |
| 47 | NOT_LINEARIZABLE | k1 | 118 | `./gradlew chaos -Pruns=1 -PstartSeed=47 -Plabel=mutation-follower-reads -Pverbose` |
| 48 | NOT_LINEARIZABLE | k0 | 95 | `./gradlew chaos -Pruns=1 -PstartSeed=48 -Plabel=mutation-follower-reads -Pverbose` |
| 49 | NOT_LINEARIZABLE | k0 | 95 | `./gradlew chaos -Pruns=1 -PstartSeed=49 -Plabel=mutation-follower-reads -Pverbose` |
| 50 | NOT_LINEARIZABLE | k0 | 109 | `./gradlew chaos -Pruns=1 -PstartSeed=50 -Plabel=mutation-follower-reads -Pverbose` |
| 51 | NOT_LINEARIZABLE | k2 | 99 | `./gradlew chaos -Pruns=1 -PstartSeed=51 -Plabel=mutation-follower-reads -Pverbose` |
| 52 | NOT_LINEARIZABLE | k1 | 94 | `./gradlew chaos -Pruns=1 -PstartSeed=52 -Plabel=mutation-follower-reads -Pverbose` |
| 53 | NOT_LINEARIZABLE | k2 | 94 | `./gradlew chaos -Pruns=1 -PstartSeed=53 -Plabel=mutation-follower-reads -Pverbose` |
| 54 | NOT_LINEARIZABLE | k1 | 95 | `./gradlew chaos -Pruns=1 -PstartSeed=54 -Plabel=mutation-follower-reads -Pverbose` |
| 55 | NOT_LINEARIZABLE | k0 | 100 | `./gradlew chaos -Pruns=1 -PstartSeed=55 -Plabel=mutation-follower-reads -Pverbose` |
| 56 | NOT_LINEARIZABLE | k0 | 85 | `./gradlew chaos -Pruns=1 -PstartSeed=56 -Plabel=mutation-follower-reads -Pverbose` |
| 57 | NOT_LINEARIZABLE | k1 | 97 | `./gradlew chaos -Pruns=1 -PstartSeed=57 -Plabel=mutation-follower-reads -Pverbose` |
| 59 | NOT_LINEARIZABLE | k0 | 93 | `./gradlew chaos -Pruns=1 -PstartSeed=59 -Plabel=mutation-follower-reads -Pverbose` |
| 60 | NOT_LINEARIZABLE | k0 | 82 | `./gradlew chaos -Pruns=1 -PstartSeed=60 -Plabel=mutation-follower-reads -Pverbose` |
| 61 | NOT_LINEARIZABLE | k2 | 107 | `./gradlew chaos -Pruns=1 -PstartSeed=61 -Plabel=mutation-follower-reads -Pverbose` |
| 62 | NOT_LINEARIZABLE | k0 | 108 | `./gradlew chaos -Pruns=1 -PstartSeed=62 -Plabel=mutation-follower-reads -Pverbose` |
| 63 | NOT_LINEARIZABLE | k2 | 89 | `./gradlew chaos -Pruns=1 -PstartSeed=63 -Plabel=mutation-follower-reads -Pverbose` |
| 64 | NOT_LINEARIZABLE | k1 | 104 | `./gradlew chaos -Pruns=1 -PstartSeed=64 -Plabel=mutation-follower-reads -Pverbose` |
| 65 | NOT_LINEARIZABLE | k0 | 98 | `./gradlew chaos -Pruns=1 -PstartSeed=65 -Plabel=mutation-follower-reads -Pverbose` |
| 66 | NOT_LINEARIZABLE | k0 | 112 | `./gradlew chaos -Pruns=1 -PstartSeed=66 -Plabel=mutation-follower-reads -Pverbose` |
| 67 | NOT_LINEARIZABLE | k0 | 87 | `./gradlew chaos -Pruns=1 -PstartSeed=67 -Plabel=mutation-follower-reads -Pverbose` |
| 68 | NOT_LINEARIZABLE | k0 | 107 | `./gradlew chaos -Pruns=1 -PstartSeed=68 -Plabel=mutation-follower-reads -Pverbose` |
| 69 | NOT_LINEARIZABLE | k0 | 93 | `./gradlew chaos -Pruns=1 -PstartSeed=69 -Plabel=mutation-follower-reads -Pverbose` |
| 70 | NOT_LINEARIZABLE | k0 | 99 | `./gradlew chaos -Pruns=1 -PstartSeed=70 -Plabel=mutation-follower-reads -Pverbose` |
| 71 | NOT_LINEARIZABLE | k0 | 94 | `./gradlew chaos -Pruns=1 -PstartSeed=71 -Plabel=mutation-follower-reads -Pverbose` |
| 72 | NOT_LINEARIZABLE | k0 | 95 | `./gradlew chaos -Pruns=1 -PstartSeed=72 -Plabel=mutation-follower-reads -Pverbose` |
| 73 | NOT_LINEARIZABLE | k1 | 87 | `./gradlew chaos -Pruns=1 -PstartSeed=73 -Plabel=mutation-follower-reads -Pverbose` |
| 74 | NOT_LINEARIZABLE | k0 | 97 | `./gradlew chaos -Pruns=1 -PstartSeed=74 -Plabel=mutation-follower-reads -Pverbose` |
| 75 | NOT_LINEARIZABLE | k1 | 102 | `./gradlew chaos -Pruns=1 -PstartSeed=75 -Plabel=mutation-follower-reads -Pverbose` |
| 76 | NOT_LINEARIZABLE | k0 | 104 | `./gradlew chaos -Pruns=1 -PstartSeed=76 -Plabel=mutation-follower-reads -Pverbose` |
| 77 | NOT_LINEARIZABLE | k0 | 116 | `./gradlew chaos -Pruns=1 -PstartSeed=77 -Plabel=mutation-follower-reads -Pverbose` |
| 78 | NOT_LINEARIZABLE | k1 | 109 | `./gradlew chaos -Pruns=1 -PstartSeed=78 -Plabel=mutation-follower-reads -Pverbose` |
| 79 | NOT_LINEARIZABLE | k0 | 99 | `./gradlew chaos -Pruns=1 -PstartSeed=79 -Plabel=mutation-follower-reads -Pverbose` |
| 80 | NOT_LINEARIZABLE | k0 | 92 | `./gradlew chaos -Pruns=1 -PstartSeed=80 -Plabel=mutation-follower-reads -Pverbose` |
| 81 | NOT_LINEARIZABLE | k2 | 115 | `./gradlew chaos -Pruns=1 -PstartSeed=81 -Plabel=mutation-follower-reads -Pverbose` |
| 82 | NOT_LINEARIZABLE | k0 | 93 | `./gradlew chaos -Pruns=1 -PstartSeed=82 -Plabel=mutation-follower-reads -Pverbose` |
| 83 | NOT_LINEARIZABLE | k2 | 103 | `./gradlew chaos -Pruns=1 -PstartSeed=83 -Plabel=mutation-follower-reads -Pverbose` |
| 84 | NOT_LINEARIZABLE | k0 | 106 | `./gradlew chaos -Pruns=1 -PstartSeed=84 -Plabel=mutation-follower-reads -Pverbose` |
| 85 | NOT_LINEARIZABLE | k0 | 99 | `./gradlew chaos -Pruns=1 -PstartSeed=85 -Plabel=mutation-follower-reads -Pverbose` |
| 86 | NOT_LINEARIZABLE | k0 | 96 | `./gradlew chaos -Pruns=1 -PstartSeed=86 -Plabel=mutation-follower-reads -Pverbose` |
| 88 | NOT_LINEARIZABLE | k0 | 106 | `./gradlew chaos -Pruns=1 -PstartSeed=88 -Plabel=mutation-follower-reads -Pverbose` |
| 89 | NOT_LINEARIZABLE | k1 | 113 | `./gradlew chaos -Pruns=1 -PstartSeed=89 -Plabel=mutation-follower-reads -Pverbose` |
| 90 | NOT_LINEARIZABLE | k0 | 104 | `./gradlew chaos -Pruns=1 -PstartSeed=90 -Plabel=mutation-follower-reads -Pverbose` |
| 91 | NOT_LINEARIZABLE | k0 | 91 | `./gradlew chaos -Pruns=1 -PstartSeed=91 -Plabel=mutation-follower-reads -Pverbose` |
| 92 | NOT_LINEARIZABLE | k0 | 95 | `./gradlew chaos -Pruns=1 -PstartSeed=92 -Plabel=mutation-follower-reads -Pverbose` |
| 93 | NOT_LINEARIZABLE | k1 | 90 | `./gradlew chaos -Pruns=1 -PstartSeed=93 -Plabel=mutation-follower-reads -Pverbose` |
| 94 | NOT_LINEARIZABLE | k1 | 105 | `./gradlew chaos -Pruns=1 -PstartSeed=94 -Plabel=mutation-follower-reads -Pverbose` |
| 95 | NOT_LINEARIZABLE | k0 | 106 | `./gradlew chaos -Pruns=1 -PstartSeed=95 -Plabel=mutation-follower-reads -Pverbose` |
| 96 | NOT_LINEARIZABLE | k0 | 95 | `./gradlew chaos -Pruns=1 -PstartSeed=96 -Plabel=mutation-follower-reads -Pverbose` |
| 97 | NOT_LINEARIZABLE | k0 | 104 | `./gradlew chaos -Pruns=1 -PstartSeed=97 -Plabel=mutation-follower-reads -Pverbose` |
| 98 | INCONCLUSIVE | k1 | 110 | `./gradlew chaos -Pruns=1 -PstartSeed=98 -Plabel=mutation-follower-reads -Pverbose` |
| 100 | NOT_LINEARIZABLE | k0 | 100 | `./gradlew chaos -Pruns=1 -PstartSeed=100 -Plabel=mutation-follower-reads -Pverbose` |
| 101 | NOT_LINEARIZABLE | k2 | 99 | `./gradlew chaos -Pruns=1 -PstartSeed=101 -Plabel=mutation-follower-reads -Pverbose` |
| 103 | NOT_LINEARIZABLE | k1 | 85 | `./gradlew chaos -Pruns=1 -PstartSeed=103 -Plabel=mutation-follower-reads -Pverbose` |
| 104 | NOT_LINEARIZABLE | k0 | 98 | `./gradlew chaos -Pruns=1 -PstartSeed=104 -Plabel=mutation-follower-reads -Pverbose` |
| 105 | NOT_LINEARIZABLE | k2 | 97 | `./gradlew chaos -Pruns=1 -PstartSeed=105 -Plabel=mutation-follower-reads -Pverbose` |
| 106 | NOT_LINEARIZABLE | k1 | 104 | `./gradlew chaos -Pruns=1 -PstartSeed=106 -Plabel=mutation-follower-reads -Pverbose` |
| 107 | NOT_LINEARIZABLE | k0 | 115 | `./gradlew chaos -Pruns=1 -PstartSeed=107 -Plabel=mutation-follower-reads -Pverbose` |
| 108 | NOT_LINEARIZABLE | k0 | 101 | `./gradlew chaos -Pruns=1 -PstartSeed=108 -Plabel=mutation-follower-reads -Pverbose` |
| 109 | NOT_LINEARIZABLE | k1 | 91 | `./gradlew chaos -Pruns=1 -PstartSeed=109 -Plabel=mutation-follower-reads -Pverbose` |
| 111 | NOT_LINEARIZABLE | k1 | 103 | `./gradlew chaos -Pruns=1 -PstartSeed=111 -Plabel=mutation-follower-reads -Pverbose` |
| 112 | NOT_LINEARIZABLE | k0 | 106 | `./gradlew chaos -Pruns=1 -PstartSeed=112 -Plabel=mutation-follower-reads -Pverbose` |
| 114 | NOT_LINEARIZABLE | k2 | 80 | `./gradlew chaos -Pruns=1 -PstartSeed=114 -Plabel=mutation-follower-reads -Pverbose` |
| 115 | NOT_LINEARIZABLE | k0 | 102 | `./gradlew chaos -Pruns=1 -PstartSeed=115 -Plabel=mutation-follower-reads -Pverbose` |
| 116 | NOT_LINEARIZABLE | k0 | 96 | `./gradlew chaos -Pruns=1 -PstartSeed=116 -Plabel=mutation-follower-reads -Pverbose` |
| 117 | NOT_LINEARIZABLE | k1 | 102 | `./gradlew chaos -Pruns=1 -PstartSeed=117 -Plabel=mutation-follower-reads -Pverbose` |
| 118 | NOT_LINEARIZABLE | k0 | 106 | `./gradlew chaos -Pruns=1 -PstartSeed=118 -Plabel=mutation-follower-reads -Pverbose` |
| 120 | NOT_LINEARIZABLE | k0 | 111 | `./gradlew chaos -Pruns=1 -PstartSeed=120 -Plabel=mutation-follower-reads -Pverbose` |
| 121 | NOT_LINEARIZABLE | k0 | 93 | `./gradlew chaos -Pruns=1 -PstartSeed=121 -Plabel=mutation-follower-reads -Pverbose` |
| 122 | NOT_LINEARIZABLE | k0 | 84 | `./gradlew chaos -Pruns=1 -PstartSeed=122 -Plabel=mutation-follower-reads -Pverbose` |
| 123 | NOT_LINEARIZABLE | k0 | 87 | `./gradlew chaos -Pruns=1 -PstartSeed=123 -Plabel=mutation-follower-reads -Pverbose` |
| 124 | NOT_LINEARIZABLE | k2 | 111 | `./gradlew chaos -Pruns=1 -PstartSeed=124 -Plabel=mutation-follower-reads -Pverbose` |
| 125 | NOT_LINEARIZABLE | k1 | 84 | `./gradlew chaos -Pruns=1 -PstartSeed=125 -Plabel=mutation-follower-reads -Pverbose` |
| 126 | NOT_LINEARIZABLE | k0 | 111 | `./gradlew chaos -Pruns=1 -PstartSeed=126 -Plabel=mutation-follower-reads -Pverbose` |
| 127 | NOT_LINEARIZABLE | k1 | 96 | `./gradlew chaos -Pruns=1 -PstartSeed=127 -Plabel=mutation-follower-reads -Pverbose` |
| 128 | NOT_LINEARIZABLE | k1 | 107 | `./gradlew chaos -Pruns=1 -PstartSeed=128 -Plabel=mutation-follower-reads -Pverbose` |
| 129 | NOT_LINEARIZABLE | k2 | 91 | `./gradlew chaos -Pruns=1 -PstartSeed=129 -Plabel=mutation-follower-reads -Pverbose` |
| 130 | NOT_LINEARIZABLE | k1 | 99 | `./gradlew chaos -Pruns=1 -PstartSeed=130 -Plabel=mutation-follower-reads -Pverbose` |
| 131 | NOT_LINEARIZABLE | k0 | 92 | `./gradlew chaos -Pruns=1 -PstartSeed=131 -Plabel=mutation-follower-reads -Pverbose` |
| 132 | NOT_LINEARIZABLE | k0 | 94 | `./gradlew chaos -Pruns=1 -PstartSeed=132 -Plabel=mutation-follower-reads -Pverbose` |
| 133 | NOT_LINEARIZABLE | k1 | 101 | `./gradlew chaos -Pruns=1 -PstartSeed=133 -Plabel=mutation-follower-reads -Pverbose` |
| 134 | NOT_LINEARIZABLE | k1 | 97 | `./gradlew chaos -Pruns=1 -PstartSeed=134 -Plabel=mutation-follower-reads -Pverbose` |
| 135 | NOT_LINEARIZABLE | k0 | 92 | `./gradlew chaos -Pruns=1 -PstartSeed=135 -Plabel=mutation-follower-reads -Pverbose` |
| 136 | NOT_LINEARIZABLE | k0 | 103 | `./gradlew chaos -Pruns=1 -PstartSeed=136 -Plabel=mutation-follower-reads -Pverbose` |
| 137 | NOT_LINEARIZABLE | k0 | 94 | `./gradlew chaos -Pruns=1 -PstartSeed=137 -Plabel=mutation-follower-reads -Pverbose` |
| 138 | NOT_LINEARIZABLE | k2 | 106 | `./gradlew chaos -Pruns=1 -PstartSeed=138 -Plabel=mutation-follower-reads -Pverbose` |
| 140 | NOT_LINEARIZABLE | k0 | 108 | `./gradlew chaos -Pruns=1 -PstartSeed=140 -Plabel=mutation-follower-reads -Pverbose` |
| 141 | NOT_LINEARIZABLE | k0 | 114 | `./gradlew chaos -Pruns=1 -PstartSeed=141 -Plabel=mutation-follower-reads -Pverbose` |
| 142 | NOT_LINEARIZABLE | k0 | 87 | `./gradlew chaos -Pruns=1 -PstartSeed=142 -Plabel=mutation-follower-reads -Pverbose` |
| 143 | NOT_LINEARIZABLE | k1 | 102 | `./gradlew chaos -Pruns=1 -PstartSeed=143 -Plabel=mutation-follower-reads -Pverbose` |
| 144 | NOT_LINEARIZABLE | k0 | 90 | `./gradlew chaos -Pruns=1 -PstartSeed=144 -Plabel=mutation-follower-reads -Pverbose` |
| 145 | NOT_LINEARIZABLE | k0 | 105 | `./gradlew chaos -Pruns=1 -PstartSeed=145 -Plabel=mutation-follower-reads -Pverbose` |
| 146 | NOT_LINEARIZABLE | k0 | 104 | `./gradlew chaos -Pruns=1 -PstartSeed=146 -Plabel=mutation-follower-reads -Pverbose` |
| 148 | NOT_LINEARIZABLE | k0 | 105 | `./gradlew chaos -Pruns=1 -PstartSeed=148 -Plabel=mutation-follower-reads -Pverbose` |
| 149 | NOT_LINEARIZABLE | k0 | 95 | `./gradlew chaos -Pruns=1 -PstartSeed=149 -Plabel=mutation-follower-reads -Pverbose` |
| 150 | NOT_LINEARIZABLE | k0 | 108 | `./gradlew chaos -Pruns=1 -PstartSeed=150 -Plabel=mutation-follower-reads -Pverbose` |
| 151 | NOT_LINEARIZABLE | k0 | 93 | `./gradlew chaos -Pruns=1 -PstartSeed=151 -Plabel=mutation-follower-reads -Pverbose` |
| 152 | NOT_LINEARIZABLE | k1 | 105 | `./gradlew chaos -Pruns=1 -PstartSeed=152 -Plabel=mutation-follower-reads -Pverbose` |
| 153 | NOT_LINEARIZABLE | k0 | 85 | `./gradlew chaos -Pruns=1 -PstartSeed=153 -Plabel=mutation-follower-reads -Pverbose` |
| 154 | NOT_LINEARIZABLE | k1 | 99 | `./gradlew chaos -Pruns=1 -PstartSeed=154 -Plabel=mutation-follower-reads -Pverbose` |
| 155 | NOT_LINEARIZABLE | k0 | 112 | `./gradlew chaos -Pruns=1 -PstartSeed=155 -Plabel=mutation-follower-reads -Pverbose` |
| 156 | NOT_LINEARIZABLE | k0 | 112 | `./gradlew chaos -Pruns=1 -PstartSeed=156 -Plabel=mutation-follower-reads -Pverbose` |
| 157 | NOT_LINEARIZABLE | k1 | 107 | `./gradlew chaos -Pruns=1 -PstartSeed=157 -Plabel=mutation-follower-reads -Pverbose` |
| 159 | NOT_LINEARIZABLE | k1 | 107 | `./gradlew chaos -Pruns=1 -PstartSeed=159 -Plabel=mutation-follower-reads -Pverbose` |
| 160 | NOT_LINEARIZABLE | k1 | 90 | `./gradlew chaos -Pruns=1 -PstartSeed=160 -Plabel=mutation-follower-reads -Pverbose` |
| 161 | NOT_LINEARIZABLE | k0 | 101 | `./gradlew chaos -Pruns=1 -PstartSeed=161 -Plabel=mutation-follower-reads -Pverbose` |
| 162 | NOT_LINEARIZABLE | k1 | 105 | `./gradlew chaos -Pruns=1 -PstartSeed=162 -Plabel=mutation-follower-reads -Pverbose` |
| 163 | NOT_LINEARIZABLE | k1 | 105 | `./gradlew chaos -Pruns=1 -PstartSeed=163 -Plabel=mutation-follower-reads -Pverbose` |
| 164 | NOT_LINEARIZABLE | k1 | 88 | `./gradlew chaos -Pruns=1 -PstartSeed=164 -Plabel=mutation-follower-reads -Pverbose` |
| 165 | NOT_LINEARIZABLE | k0 | 96 | `./gradlew chaos -Pruns=1 -PstartSeed=165 -Plabel=mutation-follower-reads -Pverbose` |
| 166 | NOT_LINEARIZABLE | k0 | 93 | `./gradlew chaos -Pruns=1 -PstartSeed=166 -Plabel=mutation-follower-reads -Pverbose` |
| 167 | NOT_LINEARIZABLE | k1 | 93 | `./gradlew chaos -Pruns=1 -PstartSeed=167 -Plabel=mutation-follower-reads -Pverbose` |
| 168 | NOT_LINEARIZABLE | k0 | 99 | `./gradlew chaos -Pruns=1 -PstartSeed=168 -Plabel=mutation-follower-reads -Pverbose` |
| 169 | NOT_LINEARIZABLE | k1 | 89 | `./gradlew chaos -Pruns=1 -PstartSeed=169 -Plabel=mutation-follower-reads -Pverbose` |
| 170 | NOT_LINEARIZABLE | k2 | 102 | `./gradlew chaos -Pruns=1 -PstartSeed=170 -Plabel=mutation-follower-reads -Pverbose` |
| 171 | NOT_LINEARIZABLE | k0 | 97 | `./gradlew chaos -Pruns=1 -PstartSeed=171 -Plabel=mutation-follower-reads -Pverbose` |
| 172 | NOT_LINEARIZABLE | k1 | 96 | `./gradlew chaos -Pruns=1 -PstartSeed=172 -Plabel=mutation-follower-reads -Pverbose` |
| 173 | NOT_LINEARIZABLE | k1 | 110 | `./gradlew chaos -Pruns=1 -PstartSeed=173 -Plabel=mutation-follower-reads -Pverbose` |
| 174 | NOT_LINEARIZABLE | k0 | 98 | `./gradlew chaos -Pruns=1 -PstartSeed=174 -Plabel=mutation-follower-reads -Pverbose` |
| 175 | NOT_LINEARIZABLE | k1 | 91 | `./gradlew chaos -Pruns=1 -PstartSeed=175 -Plabel=mutation-follower-reads -Pverbose` |
| 176 | NOT_LINEARIZABLE | k0 | 92 | `./gradlew chaos -Pruns=1 -PstartSeed=176 -Plabel=mutation-follower-reads -Pverbose` |
| 177 | NOT_LINEARIZABLE | k0 | 106 | `./gradlew chaos -Pruns=1 -PstartSeed=177 -Plabel=mutation-follower-reads -Pverbose` |
| 178 | NOT_LINEARIZABLE | k0 | 105 | `./gradlew chaos -Pruns=1 -PstartSeed=178 -Plabel=mutation-follower-reads -Pverbose` |
| 179 | NOT_LINEARIZABLE | k1 | 99 | `./gradlew chaos -Pruns=1 -PstartSeed=179 -Plabel=mutation-follower-reads -Pverbose` |
| 180 | NOT_LINEARIZABLE | k2 | 107 | `./gradlew chaos -Pruns=1 -PstartSeed=180 -Plabel=mutation-follower-reads -Pverbose` |
| 181 | NOT_LINEARIZABLE | k0 | 112 | `./gradlew chaos -Pruns=1 -PstartSeed=181 -Plabel=mutation-follower-reads -Pverbose` |
| 182 | NOT_LINEARIZABLE | k0 | 90 | `./gradlew chaos -Pruns=1 -PstartSeed=182 -Plabel=mutation-follower-reads -Pverbose` |
| 183 | NOT_LINEARIZABLE | k0 | 108 | `./gradlew chaos -Pruns=1 -PstartSeed=183 -Plabel=mutation-follower-reads -Pverbose` |
| 184 | NOT_LINEARIZABLE | k0 | 97 | `./gradlew chaos -Pruns=1 -PstartSeed=184 -Plabel=mutation-follower-reads -Pverbose` |
| 185 | NOT_LINEARIZABLE | k0 | 99 | `./gradlew chaos -Pruns=1 -PstartSeed=185 -Plabel=mutation-follower-reads -Pverbose` |
| 186 | NOT_LINEARIZABLE | k1 | 113 | `./gradlew chaos -Pruns=1 -PstartSeed=186 -Plabel=mutation-follower-reads -Pverbose` |
| 187 | NOT_LINEARIZABLE | k0 | 100 | `./gradlew chaos -Pruns=1 -PstartSeed=187 -Plabel=mutation-follower-reads -Pverbose` |
| 188 | NOT_LINEARIZABLE | k0 | 102 | `./gradlew chaos -Pruns=1 -PstartSeed=188 -Plabel=mutation-follower-reads -Pverbose` |
| 189 | NOT_LINEARIZABLE | k1 | 92 | `./gradlew chaos -Pruns=1 -PstartSeed=189 -Plabel=mutation-follower-reads -Pverbose` |
| 190 | NOT_LINEARIZABLE | k0 | 99 | `./gradlew chaos -Pruns=1 -PstartSeed=190 -Plabel=mutation-follower-reads -Pverbose` |
| 191 | NOT_LINEARIZABLE | k0 | 101 | `./gradlew chaos -Pruns=1 -PstartSeed=191 -Plabel=mutation-follower-reads -Pverbose` |
| 192 | NOT_LINEARIZABLE | k0 | 94 | `./gradlew chaos -Pruns=1 -PstartSeed=192 -Plabel=mutation-follower-reads -Pverbose` |
| 193 | NOT_LINEARIZABLE | k0 | 100 | `./gradlew chaos -Pruns=1 -PstartSeed=193 -Plabel=mutation-follower-reads -Pverbose` |
| 194 | NOT_LINEARIZABLE | k0 | 89 | `./gradlew chaos -Pruns=1 -PstartSeed=194 -Plabel=mutation-follower-reads -Pverbose` |
| 196 | NOT_LINEARIZABLE | k0 | 103 | `./gradlew chaos -Pruns=1 -PstartSeed=196 -Plabel=mutation-follower-reads -Pverbose` |
| 197 | NOT_LINEARIZABLE | k0 | 87 | `./gradlew chaos -Pruns=1 -PstartSeed=197 -Plabel=mutation-follower-reads -Pverbose` |
| 198 | NOT_LINEARIZABLE | k0 | 103 | `./gradlew chaos -Pruns=1 -PstartSeed=198 -Plabel=mutation-follower-reads -Pverbose` |
| 199 | NOT_LINEARIZABLE | k0 | 97 | `./gradlew chaos -Pruns=1 -PstartSeed=199 -Plabel=mutation-follower-reads -Pverbose` |
| 200 | NOT_LINEARIZABLE | k1 | 97 | `./gradlew chaos -Pruns=1 -PstartSeed=200 -Plabel=mutation-follower-reads -Pverbose` |
| 201 | NOT_LINEARIZABLE | k1 | 98 | `./gradlew chaos -Pruns=1 -PstartSeed=201 -Plabel=mutation-follower-reads -Pverbose` |
| 202 | NOT_LINEARIZABLE | k0 | 110 | `./gradlew chaos -Pruns=1 -PstartSeed=202 -Plabel=mutation-follower-reads -Pverbose` |
| 203 | NOT_LINEARIZABLE | k0 | 105 | `./gradlew chaos -Pruns=1 -PstartSeed=203 -Plabel=mutation-follower-reads -Pverbose` |
| 204 | NOT_LINEARIZABLE | k0 | 93 | `./gradlew chaos -Pruns=1 -PstartSeed=204 -Plabel=mutation-follower-reads -Pverbose` |
| 205 | NOT_LINEARIZABLE | k1 | 96 | `./gradlew chaos -Pruns=1 -PstartSeed=205 -Plabel=mutation-follower-reads -Pverbose` |
| 206 | NOT_LINEARIZABLE | k0 | 110 | `./gradlew chaos -Pruns=1 -PstartSeed=206 -Plabel=mutation-follower-reads -Pverbose` |
| 207 | NOT_LINEARIZABLE | k1 | 92 | `./gradlew chaos -Pruns=1 -PstartSeed=207 -Plabel=mutation-follower-reads -Pverbose` |
| 208 | NOT_LINEARIZABLE | k0 | 105 | `./gradlew chaos -Pruns=1 -PstartSeed=208 -Plabel=mutation-follower-reads -Pverbose` |
| 210 | NOT_LINEARIZABLE | k1 | 100 | `./gradlew chaos -Pruns=1 -PstartSeed=210 -Plabel=mutation-follower-reads -Pverbose` |
| 211 | NOT_LINEARIZABLE | k0 | 93 | `./gradlew chaos -Pruns=1 -PstartSeed=211 -Plabel=mutation-follower-reads -Pverbose` |
| 212 | NOT_LINEARIZABLE | k0 | 82 | `./gradlew chaos -Pruns=1 -PstartSeed=212 -Plabel=mutation-follower-reads -Pverbose` |
| 213 | NOT_LINEARIZABLE | k0 | 96 | `./gradlew chaos -Pruns=1 -PstartSeed=213 -Plabel=mutation-follower-reads -Pverbose` |
| 214 | NOT_LINEARIZABLE | k0 | 101 | `./gradlew chaos -Pruns=1 -PstartSeed=214 -Plabel=mutation-follower-reads -Pverbose` |
| 215 | NOT_LINEARIZABLE | k1 | 99 | `./gradlew chaos -Pruns=1 -PstartSeed=215 -Plabel=mutation-follower-reads -Pverbose` |
| 216 | NOT_LINEARIZABLE | k0 | 93 | `./gradlew chaos -Pruns=1 -PstartSeed=216 -Plabel=mutation-follower-reads -Pverbose` |
| 217 | NOT_LINEARIZABLE | k1 | 99 | `./gradlew chaos -Pruns=1 -PstartSeed=217 -Plabel=mutation-follower-reads -Pverbose` |
| 218 | NOT_LINEARIZABLE | k2 | 95 | `./gradlew chaos -Pruns=1 -PstartSeed=218 -Plabel=mutation-follower-reads -Pverbose` |
| 219 | NOT_LINEARIZABLE | k0 | 94 | `./gradlew chaos -Pruns=1 -PstartSeed=219 -Plabel=mutation-follower-reads -Pverbose` |
| 220 | NOT_LINEARIZABLE | k0 | 85 | `./gradlew chaos -Pruns=1 -PstartSeed=220 -Plabel=mutation-follower-reads -Pverbose` |
| 221 | NOT_LINEARIZABLE | k1 | 105 | `./gradlew chaos -Pruns=1 -PstartSeed=221 -Plabel=mutation-follower-reads -Pverbose` |
| 222 | NOT_LINEARIZABLE | k1 | 100 | `./gradlew chaos -Pruns=1 -PstartSeed=222 -Plabel=mutation-follower-reads -Pverbose` |
| 223 | NOT_LINEARIZABLE | k0 | 92 | `./gradlew chaos -Pruns=1 -PstartSeed=223 -Plabel=mutation-follower-reads -Pverbose` |
| 224 | NOT_LINEARIZABLE | k1 | 90 | `./gradlew chaos -Pruns=1 -PstartSeed=224 -Plabel=mutation-follower-reads -Pverbose` |
| 225 | NOT_LINEARIZABLE | k1 | 101 | `./gradlew chaos -Pruns=1 -PstartSeed=225 -Plabel=mutation-follower-reads -Pverbose` |
| 226 | NOT_LINEARIZABLE | k1 | 110 | `./gradlew chaos -Pruns=1 -PstartSeed=226 -Plabel=mutation-follower-reads -Pverbose` |
| 227 | NOT_LINEARIZABLE | k0 | 111 | `./gradlew chaos -Pruns=1 -PstartSeed=227 -Plabel=mutation-follower-reads -Pverbose` |
| 228 | NOT_LINEARIZABLE | k0 | 114 | `./gradlew chaos -Pruns=1 -PstartSeed=228 -Plabel=mutation-follower-reads -Pverbose` |
| 229 | NOT_LINEARIZABLE | k1 | 114 | `./gradlew chaos -Pruns=1 -PstartSeed=229 -Plabel=mutation-follower-reads -Pverbose` |
| 230 | NOT_LINEARIZABLE | k0 | 96 | `./gradlew chaos -Pruns=1 -PstartSeed=230 -Plabel=mutation-follower-reads -Pverbose` |
| 231 | NOT_LINEARIZABLE | k2 | 104 | `./gradlew chaos -Pruns=1 -PstartSeed=231 -Plabel=mutation-follower-reads -Pverbose` |
| 232 | NOT_LINEARIZABLE | k1 | 91 | `./gradlew chaos -Pruns=1 -PstartSeed=232 -Plabel=mutation-follower-reads -Pverbose` |
| 233 | NOT_LINEARIZABLE | k0 | 108 | `./gradlew chaos -Pruns=1 -PstartSeed=233 -Plabel=mutation-follower-reads -Pverbose` |
| 234 | NOT_LINEARIZABLE | k0 | 92 | `./gradlew chaos -Pruns=1 -PstartSeed=234 -Plabel=mutation-follower-reads -Pverbose` |
| 235 | NOT_LINEARIZABLE | k1 | 98 | `./gradlew chaos -Pruns=1 -PstartSeed=235 -Plabel=mutation-follower-reads -Pverbose` |
| 236 | NOT_LINEARIZABLE | k0 | 107 | `./gradlew chaos -Pruns=1 -PstartSeed=236 -Plabel=mutation-follower-reads -Pverbose` |
| 237 | NOT_LINEARIZABLE | k0 | 101 | `./gradlew chaos -Pruns=1 -PstartSeed=237 -Plabel=mutation-follower-reads -Pverbose` |
| 238 | NOT_LINEARIZABLE | k0 | 108 | `./gradlew chaos -Pruns=1 -PstartSeed=238 -Plabel=mutation-follower-reads -Pverbose` |
| 239 | NOT_LINEARIZABLE | k0 | 92 | `./gradlew chaos -Pruns=1 -PstartSeed=239 -Plabel=mutation-follower-reads -Pverbose` |
| 240 | NOT_LINEARIZABLE | k0 | 89 | `./gradlew chaos -Pruns=1 -PstartSeed=240 -Plabel=mutation-follower-reads -Pverbose` |
| 242 | NOT_LINEARIZABLE | k1 | 98 | `./gradlew chaos -Pruns=1 -PstartSeed=242 -Plabel=mutation-follower-reads -Pverbose` |
| 243 | NOT_LINEARIZABLE | k0 | 106 | `./gradlew chaos -Pruns=1 -PstartSeed=243 -Plabel=mutation-follower-reads -Pverbose` |
| 246 | NOT_LINEARIZABLE | k1 | 110 | `./gradlew chaos -Pruns=1 -PstartSeed=246 -Plabel=mutation-follower-reads -Pverbose` |
| 248 | NOT_LINEARIZABLE | k1 | 97 | `./gradlew chaos -Pruns=1 -PstartSeed=248 -Plabel=mutation-follower-reads -Pverbose` |
| 249 | NOT_LINEARIZABLE | k0 | 106 | `./gradlew chaos -Pruns=1 -PstartSeed=249 -Plabel=mutation-follower-reads -Pverbose` |
| 250 | NOT_LINEARIZABLE | k0 | 91 | `./gradlew chaos -Pruns=1 -PstartSeed=250 -Plabel=mutation-follower-reads -Pverbose` |
| 251 | NOT_LINEARIZABLE | k0 | 92 | `./gradlew chaos -Pruns=1 -PstartSeed=251 -Plabel=mutation-follower-reads -Pverbose` |
| 252 | NOT_LINEARIZABLE | k0 | 92 | `./gradlew chaos -Pruns=1 -PstartSeed=252 -Plabel=mutation-follower-reads -Pverbose` |
| 253 | NOT_LINEARIZABLE | k0 | 97 | `./gradlew chaos -Pruns=1 -PstartSeed=253 -Plabel=mutation-follower-reads -Pverbose` |
| 254 | NOT_LINEARIZABLE | k0 | 104 | `./gradlew chaos -Pruns=1 -PstartSeed=254 -Plabel=mutation-follower-reads -Pverbose` |
| 255 | NOT_LINEARIZABLE | k0 | 95 | `./gradlew chaos -Pruns=1 -PstartSeed=255 -Plabel=mutation-follower-reads -Pverbose` |
| 257 | NOT_LINEARIZABLE | k1 | 99 | `./gradlew chaos -Pruns=1 -PstartSeed=257 -Plabel=mutation-follower-reads -Pverbose` |
| 258 | NOT_LINEARIZABLE | k0 | 107 | `./gradlew chaos -Pruns=1 -PstartSeed=258 -Plabel=mutation-follower-reads -Pverbose` |
| 259 | NOT_LINEARIZABLE | k0 | 113 | `./gradlew chaos -Pruns=1 -PstartSeed=259 -Plabel=mutation-follower-reads -Pverbose` |
| 260 | NOT_LINEARIZABLE | k0 | 86 | `./gradlew chaos -Pruns=1 -PstartSeed=260 -Plabel=mutation-follower-reads -Pverbose` |
| 261 | NOT_LINEARIZABLE | k0 | 95 | `./gradlew chaos -Pruns=1 -PstartSeed=261 -Plabel=mutation-follower-reads -Pverbose` |
| 262 | NOT_LINEARIZABLE | k0 | 104 | `./gradlew chaos -Pruns=1 -PstartSeed=262 -Plabel=mutation-follower-reads -Pverbose` |
| 263 | NOT_LINEARIZABLE | k1 | 95 | `./gradlew chaos -Pruns=1 -PstartSeed=263 -Plabel=mutation-follower-reads -Pverbose` |
| 266 | NOT_LINEARIZABLE | k1 | 89 | `./gradlew chaos -Pruns=1 -PstartSeed=266 -Plabel=mutation-follower-reads -Pverbose` |
| 267 | NOT_LINEARIZABLE | k0 | 96 | `./gradlew chaos -Pruns=1 -PstartSeed=267 -Plabel=mutation-follower-reads -Pverbose` |
| 268 | NOT_LINEARIZABLE | k0 | 94 | `./gradlew chaos -Pruns=1 -PstartSeed=268 -Plabel=mutation-follower-reads -Pverbose` |
| 269 | NOT_LINEARIZABLE | k1 | 116 | `./gradlew chaos -Pruns=1 -PstartSeed=269 -Plabel=mutation-follower-reads -Pverbose` |
| 270 | NOT_LINEARIZABLE | k0 | 84 | `./gradlew chaos -Pruns=1 -PstartSeed=270 -Plabel=mutation-follower-reads -Pverbose` |
| 271 | NOT_LINEARIZABLE | k0 | 89 | `./gradlew chaos -Pruns=1 -PstartSeed=271 -Plabel=mutation-follower-reads -Pverbose` |
| 272 | NOT_LINEARIZABLE | k0 | 91 | `./gradlew chaos -Pruns=1 -PstartSeed=272 -Plabel=mutation-follower-reads -Pverbose` |
| 273 | NOT_LINEARIZABLE | k1 | 90 | `./gradlew chaos -Pruns=1 -PstartSeed=273 -Plabel=mutation-follower-reads -Pverbose` |
| 274 | NOT_LINEARIZABLE | k0 | 104 | `./gradlew chaos -Pruns=1 -PstartSeed=274 -Plabel=mutation-follower-reads -Pverbose` |
| 275 | NOT_LINEARIZABLE | k1 | 91 | `./gradlew chaos -Pruns=1 -PstartSeed=275 -Plabel=mutation-follower-reads -Pverbose` |
| 276 | NOT_LINEARIZABLE | k0 | 95 | `./gradlew chaos -Pruns=1 -PstartSeed=276 -Plabel=mutation-follower-reads -Pverbose` |
| 277 | NOT_LINEARIZABLE | k1 | 102 | `./gradlew chaos -Pruns=1 -PstartSeed=277 -Plabel=mutation-follower-reads -Pverbose` |
| 278 | NOT_LINEARIZABLE | k0 | 95 | `./gradlew chaos -Pruns=1 -PstartSeed=278 -Plabel=mutation-follower-reads -Pverbose` |
| 279 | NOT_LINEARIZABLE | k1 | 107 | `./gradlew chaos -Pruns=1 -PstartSeed=279 -Plabel=mutation-follower-reads -Pverbose` |
| 280 | NOT_LINEARIZABLE | k0 | 94 | `./gradlew chaos -Pruns=1 -PstartSeed=280 -Plabel=mutation-follower-reads -Pverbose` |
| 281 | NOT_LINEARIZABLE | k2 | 89 | `./gradlew chaos -Pruns=1 -PstartSeed=281 -Plabel=mutation-follower-reads -Pverbose` |
| 282 | NOT_LINEARIZABLE | k2 | 98 | `./gradlew chaos -Pruns=1 -PstartSeed=282 -Plabel=mutation-follower-reads -Pverbose` |
| 283 | NOT_LINEARIZABLE | k1 | 99 | `./gradlew chaos -Pruns=1 -PstartSeed=283 -Plabel=mutation-follower-reads -Pverbose` |
| 284 | NOT_LINEARIZABLE | k1 | 87 | `./gradlew chaos -Pruns=1 -PstartSeed=284 -Plabel=mutation-follower-reads -Pverbose` |
| 285 | NOT_LINEARIZABLE | k2 | 97 | `./gradlew chaos -Pruns=1 -PstartSeed=285 -Plabel=mutation-follower-reads -Pverbose` |
| 286 | NOT_LINEARIZABLE | k0 | 105 | `./gradlew chaos -Pruns=1 -PstartSeed=286 -Plabel=mutation-follower-reads -Pverbose` |
| 287 | NOT_LINEARIZABLE | k0 | 94 | `./gradlew chaos -Pruns=1 -PstartSeed=287 -Plabel=mutation-follower-reads -Pverbose` |
| 289 | NOT_LINEARIZABLE | k2 | 97 | `./gradlew chaos -Pruns=1 -PstartSeed=289 -Plabel=mutation-follower-reads -Pverbose` |
| 291 | NOT_LINEARIZABLE | k2 | 112 | `./gradlew chaos -Pruns=1 -PstartSeed=291 -Plabel=mutation-follower-reads -Pverbose` |
| 292 | NOT_LINEARIZABLE | k0 | 96 | `./gradlew chaos -Pruns=1 -PstartSeed=292 -Plabel=mutation-follower-reads -Pverbose` |
| 293 | NOT_LINEARIZABLE | k0 | 94 | `./gradlew chaos -Pruns=1 -PstartSeed=293 -Plabel=mutation-follower-reads -Pverbose` |
| 294 | NOT_LINEARIZABLE | k1 | 94 | `./gradlew chaos -Pruns=1 -PstartSeed=294 -Plabel=mutation-follower-reads -Pverbose` |
| 295 | NOT_LINEARIZABLE | k1 | 105 | `./gradlew chaos -Pruns=1 -PstartSeed=295 -Plabel=mutation-follower-reads -Pverbose` |
| 296 | NOT_LINEARIZABLE | k0 | 103 | `./gradlew chaos -Pruns=1 -PstartSeed=296 -Plabel=mutation-follower-reads -Pverbose` |
| 297 | NOT_LINEARIZABLE | k1 | 90 | `./gradlew chaos -Pruns=1 -PstartSeed=297 -Plabel=mutation-follower-reads -Pverbose` |
| 298 | NOT_LINEARIZABLE | k0 | 105 | `./gradlew chaos -Pruns=1 -PstartSeed=298 -Plabel=mutation-follower-reads -Pverbose` |
| 299 | NOT_LINEARIZABLE | k0 | 105 | `./gradlew chaos -Pruns=1 -PstartSeed=299 -Plabel=mutation-follower-reads -Pverbose` |
| 300 | NOT_LINEARIZABLE | k1 | 89 | `./gradlew chaos -Pruns=1 -PstartSeed=300 -Plabel=mutation-follower-reads -Pverbose` |
| 301 | NOT_LINEARIZABLE | k2 | 104 | `./gradlew chaos -Pruns=1 -PstartSeed=301 -Plabel=mutation-follower-reads -Pverbose` |
| 302 | NOT_LINEARIZABLE | k1 | 98 | `./gradlew chaos -Pruns=1 -PstartSeed=302 -Plabel=mutation-follower-reads -Pverbose` |
| 303 | NOT_LINEARIZABLE | k1 | 108 | `./gradlew chaos -Pruns=1 -PstartSeed=303 -Plabel=mutation-follower-reads -Pverbose` |
| 304 | NOT_LINEARIZABLE | k0 | 95 | `./gradlew chaos -Pruns=1 -PstartSeed=304 -Plabel=mutation-follower-reads -Pverbose` |
| 305 | NOT_LINEARIZABLE | k1 | 96 | `./gradlew chaos -Pruns=1 -PstartSeed=305 -Plabel=mutation-follower-reads -Pverbose` |
| 306 | NOT_LINEARIZABLE | k0 | 106 | `./gradlew chaos -Pruns=1 -PstartSeed=306 -Plabel=mutation-follower-reads -Pverbose` |
| 307 | NOT_LINEARIZABLE | k0 | 96 | `./gradlew chaos -Pruns=1 -PstartSeed=307 -Plabel=mutation-follower-reads -Pverbose` |
| 308 | NOT_LINEARIZABLE | k1 | 98 | `./gradlew chaos -Pruns=1 -PstartSeed=308 -Plabel=mutation-follower-reads -Pverbose` |
| 310 | NOT_LINEARIZABLE | k0 | 104 | `./gradlew chaos -Pruns=1 -PstartSeed=310 -Plabel=mutation-follower-reads -Pverbose` |
| 311 | NOT_LINEARIZABLE | k1 | 102 | `./gradlew chaos -Pruns=1 -PstartSeed=311 -Plabel=mutation-follower-reads -Pverbose` |
| 312 | NOT_LINEARIZABLE | k1 | 87 | `./gradlew chaos -Pruns=1 -PstartSeed=312 -Plabel=mutation-follower-reads -Pverbose` |
| 313 | NOT_LINEARIZABLE | k0 | 89 | `./gradlew chaos -Pruns=1 -PstartSeed=313 -Plabel=mutation-follower-reads -Pverbose` |
| 315 | NOT_LINEARIZABLE | k0 | 99 | `./gradlew chaos -Pruns=1 -PstartSeed=315 -Plabel=mutation-follower-reads -Pverbose` |
| 316 | NOT_LINEARIZABLE | k0 | 99 | `./gradlew chaos -Pruns=1 -PstartSeed=316 -Plabel=mutation-follower-reads -Pverbose` |
| 317 | NOT_LINEARIZABLE | k0 | 91 | `./gradlew chaos -Pruns=1 -PstartSeed=317 -Plabel=mutation-follower-reads -Pverbose` |
| 318 | NOT_LINEARIZABLE | k0 | 88 | `./gradlew chaos -Pruns=1 -PstartSeed=318 -Plabel=mutation-follower-reads -Pverbose` |
| 319 | NOT_LINEARIZABLE | k0 | 92 | `./gradlew chaos -Pruns=1 -PstartSeed=319 -Plabel=mutation-follower-reads -Pverbose` |
| 320 | NOT_LINEARIZABLE | k0 | 97 | `./gradlew chaos -Pruns=1 -PstartSeed=320 -Plabel=mutation-follower-reads -Pverbose` |
| 321 | NOT_LINEARIZABLE | k2 | 92 | `./gradlew chaos -Pruns=1 -PstartSeed=321 -Plabel=mutation-follower-reads -Pverbose` |
| 322 | NOT_LINEARIZABLE | k0 | 99 | `./gradlew chaos -Pruns=1 -PstartSeed=322 -Plabel=mutation-follower-reads -Pverbose` |
| 323 | NOT_LINEARIZABLE | k0 | 105 | `./gradlew chaos -Pruns=1 -PstartSeed=323 -Plabel=mutation-follower-reads -Pverbose` |
| 324 | NOT_LINEARIZABLE | k0 | 103 | `./gradlew chaos -Pruns=1 -PstartSeed=324 -Plabel=mutation-follower-reads -Pverbose` |
| 325 | NOT_LINEARIZABLE | k1 | 105 | `./gradlew chaos -Pruns=1 -PstartSeed=325 -Plabel=mutation-follower-reads -Pverbose` |
| 326 | NOT_LINEARIZABLE | k0 | 96 | `./gradlew chaos -Pruns=1 -PstartSeed=326 -Plabel=mutation-follower-reads -Pverbose` |
| 328 | NOT_LINEARIZABLE | k0 | 110 | `./gradlew chaos -Pruns=1 -PstartSeed=328 -Plabel=mutation-follower-reads -Pverbose` |
| 329 | NOT_LINEARIZABLE | k0 | 116 | `./gradlew chaos -Pruns=1 -PstartSeed=329 -Plabel=mutation-follower-reads -Pverbose` |
| 330 | NOT_LINEARIZABLE | k0 | 89 | `./gradlew chaos -Pruns=1 -PstartSeed=330 -Plabel=mutation-follower-reads -Pverbose` |
| 331 | NOT_LINEARIZABLE | k1 | 98 | `./gradlew chaos -Pruns=1 -PstartSeed=331 -Plabel=mutation-follower-reads -Pverbose` |
| 332 | NOT_LINEARIZABLE | k0 | 100 | `./gradlew chaos -Pruns=1 -PstartSeed=332 -Plabel=mutation-follower-reads -Pverbose` |
| 334 | NOT_LINEARIZABLE | k0 | 102 | `./gradlew chaos -Pruns=1 -PstartSeed=334 -Plabel=mutation-follower-reads -Pverbose` |
| 335 | NOT_LINEARIZABLE | k2 | 93 | `./gradlew chaos -Pruns=1 -PstartSeed=335 -Plabel=mutation-follower-reads -Pverbose` |
| 336 | NOT_LINEARIZABLE | k0 | 109 | `./gradlew chaos -Pruns=1 -PstartSeed=336 -Plabel=mutation-follower-reads -Pverbose` |
| 338 | NOT_LINEARIZABLE | k0 | 96 | `./gradlew chaos -Pruns=1 -PstartSeed=338 -Plabel=mutation-follower-reads -Pverbose` |
| 339 | NOT_LINEARIZABLE | k0 | 98 | `./gradlew chaos -Pruns=1 -PstartSeed=339 -Plabel=mutation-follower-reads -Pverbose` |
| 340 | NOT_LINEARIZABLE | k0 | 102 | `./gradlew chaos -Pruns=1 -PstartSeed=340 -Plabel=mutation-follower-reads -Pverbose` |
| 341 | NOT_LINEARIZABLE | k0 | 98 | `./gradlew chaos -Pruns=1 -PstartSeed=341 -Plabel=mutation-follower-reads -Pverbose` |
| 342 | NOT_LINEARIZABLE | k2 | 100 | `./gradlew chaos -Pruns=1 -PstartSeed=342 -Plabel=mutation-follower-reads -Pverbose` |
| 345 | NOT_LINEARIZABLE | k0 | 99 | `./gradlew chaos -Pruns=1 -PstartSeed=345 -Plabel=mutation-follower-reads -Pverbose` |
| 346 | NOT_LINEARIZABLE | k0 | 97 | `./gradlew chaos -Pruns=1 -PstartSeed=346 -Plabel=mutation-follower-reads -Pverbose` |
| 347 | NOT_LINEARIZABLE | k2 | 84 | `./gradlew chaos -Pruns=1 -PstartSeed=347 -Plabel=mutation-follower-reads -Pverbose` |
| 348 | NOT_LINEARIZABLE | k0 | 104 | `./gradlew chaos -Pruns=1 -PstartSeed=348 -Plabel=mutation-follower-reads -Pverbose` |
| 349 | NOT_LINEARIZABLE | k0 | 99 | `./gradlew chaos -Pruns=1 -PstartSeed=349 -Plabel=mutation-follower-reads -Pverbose` |
| 350 | NOT_LINEARIZABLE | k1 | 101 | `./gradlew chaos -Pruns=1 -PstartSeed=350 -Plabel=mutation-follower-reads -Pverbose` |
| 351 | NOT_LINEARIZABLE | k0 | 87 | `./gradlew chaos -Pruns=1 -PstartSeed=351 -Plabel=mutation-follower-reads -Pverbose` |
| 352 | NOT_LINEARIZABLE | k0 | 94 | `./gradlew chaos -Pruns=1 -PstartSeed=352 -Plabel=mutation-follower-reads -Pverbose` |
| 353 | NOT_LINEARIZABLE | k0 | 110 | `./gradlew chaos -Pruns=1 -PstartSeed=353 -Plabel=mutation-follower-reads -Pverbose` |
| 354 | NOT_LINEARIZABLE | k0 | 96 | `./gradlew chaos -Pruns=1 -PstartSeed=354 -Plabel=mutation-follower-reads -Pverbose` |
| 355 | NOT_LINEARIZABLE | k1 | 113 | `./gradlew chaos -Pruns=1 -PstartSeed=355 -Plabel=mutation-follower-reads -Pverbose` |
| 356 | NOT_LINEARIZABLE | k0 | 98 | `./gradlew chaos -Pruns=1 -PstartSeed=356 -Plabel=mutation-follower-reads -Pverbose` |
| 357 | NOT_LINEARIZABLE | k1 | 110 | `./gradlew chaos -Pruns=1 -PstartSeed=357 -Plabel=mutation-follower-reads -Pverbose` |
| 358 | NOT_LINEARIZABLE | k1 | 103 | `./gradlew chaos -Pruns=1 -PstartSeed=358 -Plabel=mutation-follower-reads -Pverbose` |
| 359 | NOT_LINEARIZABLE | k0 | 108 | `./gradlew chaos -Pruns=1 -PstartSeed=359 -Plabel=mutation-follower-reads -Pverbose` |
| 360 | NOT_LINEARIZABLE | k0 | 91 | `./gradlew chaos -Pruns=1 -PstartSeed=360 -Plabel=mutation-follower-reads -Pverbose` |
| 361 | NOT_LINEARIZABLE | k0 | 91 | `./gradlew chaos -Pruns=1 -PstartSeed=361 -Plabel=mutation-follower-reads -Pverbose` |
| 363 | NOT_LINEARIZABLE | k0 | 110 | `./gradlew chaos -Pruns=1 -PstartSeed=363 -Plabel=mutation-follower-reads -Pverbose` |
| 364 | NOT_LINEARIZABLE | k0 | 92 | `./gradlew chaos -Pruns=1 -PstartSeed=364 -Plabel=mutation-follower-reads -Pverbose` |
| 365 | NOT_LINEARIZABLE | k1 | 95 | `./gradlew chaos -Pruns=1 -PstartSeed=365 -Plabel=mutation-follower-reads -Pverbose` |
| 366 | NOT_LINEARIZABLE | k0 | 115 | `./gradlew chaos -Pruns=1 -PstartSeed=366 -Plabel=mutation-follower-reads -Pverbose` |
| 367 | NOT_LINEARIZABLE | k0 | 103 | `./gradlew chaos -Pruns=1 -PstartSeed=367 -Plabel=mutation-follower-reads -Pverbose` |
| 368 | NOT_LINEARIZABLE | k1 | 92 | `./gradlew chaos -Pruns=1 -PstartSeed=368 -Plabel=mutation-follower-reads -Pverbose` |
| 369 | NOT_LINEARIZABLE | k0 | 108 | `./gradlew chaos -Pruns=1 -PstartSeed=369 -Plabel=mutation-follower-reads -Pverbose` |
| 370 | NOT_LINEARIZABLE | k0 | 127 | `./gradlew chaos -Pruns=1 -PstartSeed=370 -Plabel=mutation-follower-reads -Pverbose` |
| 371 | NOT_LINEARIZABLE | k1 | 102 | `./gradlew chaos -Pruns=1 -PstartSeed=371 -Plabel=mutation-follower-reads -Pverbose` |
| 372 | NOT_LINEARIZABLE | k0 | 100 | `./gradlew chaos -Pruns=1 -PstartSeed=372 -Plabel=mutation-follower-reads -Pverbose` |
| 373 | NOT_LINEARIZABLE | k0 | 97 | `./gradlew chaos -Pruns=1 -PstartSeed=373 -Plabel=mutation-follower-reads -Pverbose` |
| 374 | NOT_LINEARIZABLE | k0 | 89 | `./gradlew chaos -Pruns=1 -PstartSeed=374 -Plabel=mutation-follower-reads -Pverbose` |
| 375 | NOT_LINEARIZABLE | k0 | 91 | `./gradlew chaos -Pruns=1 -PstartSeed=375 -Plabel=mutation-follower-reads -Pverbose` |
| 376 | NOT_LINEARIZABLE | k0 | 98 | `./gradlew chaos -Pruns=1 -PstartSeed=376 -Plabel=mutation-follower-reads -Pverbose` |
| 377 | NOT_LINEARIZABLE | k1 | 85 | `./gradlew chaos -Pruns=1 -PstartSeed=377 -Plabel=mutation-follower-reads -Pverbose` |
| 378 | NOT_LINEARIZABLE | k2 | 89 | `./gradlew chaos -Pruns=1 -PstartSeed=378 -Plabel=mutation-follower-reads -Pverbose` |
| 379 | NOT_LINEARIZABLE | k2 | 105 | `./gradlew chaos -Pruns=1 -PstartSeed=379 -Plabel=mutation-follower-reads -Pverbose` |
| 380 | NOT_LINEARIZABLE | k1 | 86 | `./gradlew chaos -Pruns=1 -PstartSeed=380 -Plabel=mutation-follower-reads -Pverbose` |
| 381 | NOT_LINEARIZABLE | k2 | 101 | `./gradlew chaos -Pruns=1 -PstartSeed=381 -Plabel=mutation-follower-reads -Pverbose` |
| 382 | NOT_LINEARIZABLE | k0 | 98 | `./gradlew chaos -Pruns=1 -PstartSeed=382 -Plabel=mutation-follower-reads -Pverbose` |
| 383 | NOT_LINEARIZABLE | k0 | 95 | `./gradlew chaos -Pruns=1 -PstartSeed=383 -Plabel=mutation-follower-reads -Pverbose` |
| 384 | NOT_LINEARIZABLE | k0 | 89 | `./gradlew chaos -Pruns=1 -PstartSeed=384 -Plabel=mutation-follower-reads -Pverbose` |
| 385 | NOT_LINEARIZABLE | k1 | 104 | `./gradlew chaos -Pruns=1 -PstartSeed=385 -Plabel=mutation-follower-reads -Pverbose` |
| 386 | NOT_LINEARIZABLE | k0 | 99 | `./gradlew chaos -Pruns=1 -PstartSeed=386 -Plabel=mutation-follower-reads -Pverbose` |
| 387 | NOT_LINEARIZABLE | k1 | 115 | `./gradlew chaos -Pruns=1 -PstartSeed=387 -Plabel=mutation-follower-reads -Pverbose` |
| 388 | NOT_LINEARIZABLE | k1 | 120 | `./gradlew chaos -Pruns=1 -PstartSeed=388 -Plabel=mutation-follower-reads -Pverbose` |
| 389 | NOT_LINEARIZABLE | k0 | 105 | `./gradlew chaos -Pruns=1 -PstartSeed=389 -Plabel=mutation-follower-reads -Pverbose` |
| 390 | NOT_LINEARIZABLE | k0 | 88 | `./gradlew chaos -Pruns=1 -PstartSeed=390 -Plabel=mutation-follower-reads -Pverbose` |
| 391 | NOT_LINEARIZABLE | k0 | 107 | `./gradlew chaos -Pruns=1 -PstartSeed=391 -Plabel=mutation-follower-reads -Pverbose` |
| 392 | NOT_LINEARIZABLE | k0 | 99 | `./gradlew chaos -Pruns=1 -PstartSeed=392 -Plabel=mutation-follower-reads -Pverbose` |
| 393 | NOT_LINEARIZABLE | k1 | 89 | `./gradlew chaos -Pruns=1 -PstartSeed=393 -Plabel=mutation-follower-reads -Pverbose` |
| 394 | NOT_LINEARIZABLE | k0 | 91 | `./gradlew chaos -Pruns=1 -PstartSeed=394 -Plabel=mutation-follower-reads -Pverbose` |
| 395 | NOT_LINEARIZABLE | k0 | 99 | `./gradlew chaos -Pruns=1 -PstartSeed=395 -Plabel=mutation-follower-reads -Pverbose` |
| 397 | NOT_LINEARIZABLE | k2 | 103 | `./gradlew chaos -Pruns=1 -PstartSeed=397 -Plabel=mutation-follower-reads -Pverbose` |
| 398 | NOT_LINEARIZABLE | k0 | 111 | `./gradlew chaos -Pruns=1 -PstartSeed=398 -Plabel=mutation-follower-reads -Pverbose` |
| 399 | NOT_LINEARIZABLE | k1 | 106 | `./gradlew chaos -Pruns=1 -PstartSeed=399 -Plabel=mutation-follower-reads -Pverbose` |
| 400 | NOT_LINEARIZABLE | k0 | 103 | `./gradlew chaos -Pruns=1 -PstartSeed=400 -Plabel=mutation-follower-reads -Pverbose` |
| 401 | NOT_LINEARIZABLE | k0 | 103 | `./gradlew chaos -Pruns=1 -PstartSeed=401 -Plabel=mutation-follower-reads -Pverbose` |
| 402 | NOT_LINEARIZABLE | k0 | 106 | `./gradlew chaos -Pruns=1 -PstartSeed=402 -Plabel=mutation-follower-reads -Pverbose` |
| 403 | NOT_LINEARIZABLE | k0 | 102 | `./gradlew chaos -Pruns=1 -PstartSeed=403 -Plabel=mutation-follower-reads -Pverbose` |
| 404 | NOT_LINEARIZABLE | k2 | 95 | `./gradlew chaos -Pruns=1 -PstartSeed=404 -Plabel=mutation-follower-reads -Pverbose` |
| 405 | NOT_LINEARIZABLE | k0 | 99 | `./gradlew chaos -Pruns=1 -PstartSeed=405 -Plabel=mutation-follower-reads -Pverbose` |
| 406 | NOT_LINEARIZABLE | k2 | 100 | `./gradlew chaos -Pruns=1 -PstartSeed=406 -Plabel=mutation-follower-reads -Pverbose` |
| 407 | NOT_LINEARIZABLE | k0 | 95 | `./gradlew chaos -Pruns=1 -PstartSeed=407 -Plabel=mutation-follower-reads -Pverbose` |
| 408 | NOT_LINEARIZABLE | k0 | 91 | `./gradlew chaos -Pruns=1 -PstartSeed=408 -Plabel=mutation-follower-reads -Pverbose` |
| 409 | NOT_LINEARIZABLE | k0 | 111 | `./gradlew chaos -Pruns=1 -PstartSeed=409 -Plabel=mutation-follower-reads -Pverbose` |
| 411 | NOT_LINEARIZABLE | k0 | 98 | `./gradlew chaos -Pruns=1 -PstartSeed=411 -Plabel=mutation-follower-reads -Pverbose` |
| 413 | NOT_LINEARIZABLE | k0 | 96 | `./gradlew chaos -Pruns=1 -PstartSeed=413 -Plabel=mutation-follower-reads -Pverbose` |
| 414 | NOT_LINEARIZABLE | k0 | 99 | `./gradlew chaos -Pruns=1 -PstartSeed=414 -Plabel=mutation-follower-reads -Pverbose` |
| 415 | NOT_LINEARIZABLE | k2 | 92 | `./gradlew chaos -Pruns=1 -PstartSeed=415 -Plabel=mutation-follower-reads -Pverbose` |
| 417 | NOT_LINEARIZABLE | k1 | 117 | `./gradlew chaos -Pruns=1 -PstartSeed=417 -Plabel=mutation-follower-reads -Pverbose` |
| 418 | NOT_LINEARIZABLE | k0 | 109 | `./gradlew chaos -Pruns=1 -PstartSeed=418 -Plabel=mutation-follower-reads -Pverbose` |
| 419 | NOT_LINEARIZABLE | k1 | 102 | `./gradlew chaos -Pruns=1 -PstartSeed=419 -Plabel=mutation-follower-reads -Pverbose` |
| 420 | NOT_LINEARIZABLE | k1 | 93 | `./gradlew chaos -Pruns=1 -PstartSeed=420 -Plabel=mutation-follower-reads -Pverbose` |
| 421 | NOT_LINEARIZABLE | k0 | 96 | `./gradlew chaos -Pruns=1 -PstartSeed=421 -Plabel=mutation-follower-reads -Pverbose` |
| 422 | NOT_LINEARIZABLE | k0 | 112 | `./gradlew chaos -Pruns=1 -PstartSeed=422 -Plabel=mutation-follower-reads -Pverbose` |
| 423 | NOT_LINEARIZABLE | k0 | 106 | `./gradlew chaos -Pruns=1 -PstartSeed=423 -Plabel=mutation-follower-reads -Pverbose` |
| 424 | NOT_LINEARIZABLE | k0 | 91 | `./gradlew chaos -Pruns=1 -PstartSeed=424 -Plabel=mutation-follower-reads -Pverbose` |
| 426 | NOT_LINEARIZABLE | k0 | 102 | `./gradlew chaos -Pruns=1 -PstartSeed=426 -Plabel=mutation-follower-reads -Pverbose` |
| 427 | NOT_LINEARIZABLE | k0 | 96 | `./gradlew chaos -Pruns=1 -PstartSeed=427 -Plabel=mutation-follower-reads -Pverbose` |
| 428 | NOT_LINEARIZABLE | k0 | 100 | `./gradlew chaos -Pruns=1 -PstartSeed=428 -Plabel=mutation-follower-reads -Pverbose` |
| 429 | NOT_LINEARIZABLE | k1 | 103 | `./gradlew chaos -Pruns=1 -PstartSeed=429 -Plabel=mutation-follower-reads -Pverbose` |
| 430 | NOT_LINEARIZABLE | k1 | 102 | `./gradlew chaos -Pruns=1 -PstartSeed=430 -Plabel=mutation-follower-reads -Pverbose` |
| 431 | NOT_LINEARIZABLE | k0 | 107 | `./gradlew chaos -Pruns=1 -PstartSeed=431 -Plabel=mutation-follower-reads -Pverbose` |
| 432 | NOT_LINEARIZABLE | k2 | 116 | `./gradlew chaos -Pruns=1 -PstartSeed=432 -Plabel=mutation-follower-reads -Pverbose` |
| 434 | NOT_LINEARIZABLE | k1 | 96 | `./gradlew chaos -Pruns=1 -PstartSeed=434 -Plabel=mutation-follower-reads -Pverbose` |
| 435 | NOT_LINEARIZABLE | k0 | 100 | `./gradlew chaos -Pruns=1 -PstartSeed=435 -Plabel=mutation-follower-reads -Pverbose` |
| 436 | NOT_LINEARIZABLE | k2 | 90 | `./gradlew chaos -Pruns=1 -PstartSeed=436 -Plabel=mutation-follower-reads -Pverbose` |
| 438 | NOT_LINEARIZABLE | k0 | 99 | `./gradlew chaos -Pruns=1 -PstartSeed=438 -Plabel=mutation-follower-reads -Pverbose` |
| 439 | NOT_LINEARIZABLE | k2 | 96 | `./gradlew chaos -Pruns=1 -PstartSeed=439 -Plabel=mutation-follower-reads -Pverbose` |
| 440 | NOT_LINEARIZABLE | k0 | 89 | `./gradlew chaos -Pruns=1 -PstartSeed=440 -Plabel=mutation-follower-reads -Pverbose` |
| 441 | NOT_LINEARIZABLE | k2 | 97 | `./gradlew chaos -Pruns=1 -PstartSeed=441 -Plabel=mutation-follower-reads -Pverbose` |
| 442 | NOT_LINEARIZABLE | k0 | 98 | `./gradlew chaos -Pruns=1 -PstartSeed=442 -Plabel=mutation-follower-reads -Pverbose` |
| 443 | NOT_LINEARIZABLE | k0 | 100 | `./gradlew chaos -Pruns=1 -PstartSeed=443 -Plabel=mutation-follower-reads -Pverbose` |
| 444 | NOT_LINEARIZABLE | k0 | 109 | `./gradlew chaos -Pruns=1 -PstartSeed=444 -Plabel=mutation-follower-reads -Pverbose` |
| 445 | NOT_LINEARIZABLE | k0 | 99 | `./gradlew chaos -Pruns=1 -PstartSeed=445 -Plabel=mutation-follower-reads -Pverbose` |
| 446 | NOT_LINEARIZABLE | k1 | 103 | `./gradlew chaos -Pruns=1 -PstartSeed=446 -Plabel=mutation-follower-reads -Pverbose` |
| 447 | NOT_LINEARIZABLE | k1 | 87 | `./gradlew chaos -Pruns=1 -PstartSeed=447 -Plabel=mutation-follower-reads -Pverbose` |
| 448 | NOT_LINEARIZABLE | k0 | 98 | `./gradlew chaos -Pruns=1 -PstartSeed=448 -Plabel=mutation-follower-reads -Pverbose` |
| 449 | NOT_LINEARIZABLE | k0 | 88 | `./gradlew chaos -Pruns=1 -PstartSeed=449 -Plabel=mutation-follower-reads -Pverbose` |
| 450 | NOT_LINEARIZABLE | k2 | 103 | `./gradlew chaos -Pruns=1 -PstartSeed=450 -Plabel=mutation-follower-reads -Pverbose` |
| 451 | NOT_LINEARIZABLE | k0 | 91 | `./gradlew chaos -Pruns=1 -PstartSeed=451 -Plabel=mutation-follower-reads -Pverbose` |
| 452 | NOT_LINEARIZABLE | k1 | 106 | `./gradlew chaos -Pruns=1 -PstartSeed=452 -Plabel=mutation-follower-reads -Pverbose` |
| 453 | NOT_LINEARIZABLE | k1 | 103 | `./gradlew chaos -Pruns=1 -PstartSeed=453 -Plabel=mutation-follower-reads -Pverbose` |
| 454 | NOT_LINEARIZABLE | k0 | 99 | `./gradlew chaos -Pruns=1 -PstartSeed=454 -Plabel=mutation-follower-reads -Pverbose` |
| 455 | NOT_LINEARIZABLE | k0 | 113 | `./gradlew chaos -Pruns=1 -PstartSeed=455 -Plabel=mutation-follower-reads -Pverbose` |
| 456 | NOT_LINEARIZABLE | k0 | 122 | `./gradlew chaos -Pruns=1 -PstartSeed=456 -Plabel=mutation-follower-reads -Pverbose` |
| 457 | NOT_LINEARIZABLE | k0 | 80 | `./gradlew chaos -Pruns=1 -PstartSeed=457 -Plabel=mutation-follower-reads -Pverbose` |
| 458 | NOT_LINEARIZABLE | k0 | 94 | `./gradlew chaos -Pruns=1 -PstartSeed=458 -Plabel=mutation-follower-reads -Pverbose` |
| 461 | NOT_LINEARIZABLE | k0 | 111 | `./gradlew chaos -Pruns=1 -PstartSeed=461 -Plabel=mutation-follower-reads -Pverbose` |
| 462 | NOT_LINEARIZABLE | k1 | 100 | `./gradlew chaos -Pruns=1 -PstartSeed=462 -Plabel=mutation-follower-reads -Pverbose` |
| 463 | NOT_LINEARIZABLE | k0 | 97 | `./gradlew chaos -Pruns=1 -PstartSeed=463 -Plabel=mutation-follower-reads -Pverbose` |
| 464 | NOT_LINEARIZABLE | k0 | 112 | `./gradlew chaos -Pruns=1 -PstartSeed=464 -Plabel=mutation-follower-reads -Pverbose` |
| 465 | NOT_LINEARIZABLE | k2 | 106 | `./gradlew chaos -Pruns=1 -PstartSeed=465 -Plabel=mutation-follower-reads -Pverbose` |
| 466 | NOT_LINEARIZABLE | k0 | 79 | `./gradlew chaos -Pruns=1 -PstartSeed=466 -Plabel=mutation-follower-reads -Pverbose` |
| 467 | NOT_LINEARIZABLE | k1 | 101 | `./gradlew chaos -Pruns=1 -PstartSeed=467 -Plabel=mutation-follower-reads -Pverbose` |
| 468 | NOT_LINEARIZABLE | k1 | 94 | `./gradlew chaos -Pruns=1 -PstartSeed=468 -Plabel=mutation-follower-reads -Pverbose` |
| 469 | NOT_LINEARIZABLE | k2 | 98 | `./gradlew chaos -Pruns=1 -PstartSeed=469 -Plabel=mutation-follower-reads -Pverbose` |
| 470 | NOT_LINEARIZABLE | k0 | 100 | `./gradlew chaos -Pruns=1 -PstartSeed=470 -Plabel=mutation-follower-reads -Pverbose` |
| 472 | NOT_LINEARIZABLE | k1 | 99 | `./gradlew chaos -Pruns=1 -PstartSeed=472 -Plabel=mutation-follower-reads -Pverbose` |
| 474 | NOT_LINEARIZABLE | k0 | 92 | `./gradlew chaos -Pruns=1 -PstartSeed=474 -Plabel=mutation-follower-reads -Pverbose` |
| 476 | NOT_LINEARIZABLE | k1 | 118 | `./gradlew chaos -Pruns=1 -PstartSeed=476 -Plabel=mutation-follower-reads -Pverbose` |
| 477 | NOT_LINEARIZABLE | k1 | 102 | `./gradlew chaos -Pruns=1 -PstartSeed=477 -Plabel=mutation-follower-reads -Pverbose` |
| 478 | NOT_LINEARIZABLE | k0 | 105 | `./gradlew chaos -Pruns=1 -PstartSeed=478 -Plabel=mutation-follower-reads -Pverbose` |
| 479 | NOT_LINEARIZABLE | k2 | 98 | `./gradlew chaos -Pruns=1 -PstartSeed=479 -Plabel=mutation-follower-reads -Pverbose` |
| 480 | NOT_LINEARIZABLE | k1 | 102 | `./gradlew chaos -Pruns=1 -PstartSeed=480 -Plabel=mutation-follower-reads -Pverbose` |
| 481 | NOT_LINEARIZABLE | k1 | 92 | `./gradlew chaos -Pruns=1 -PstartSeed=481 -Plabel=mutation-follower-reads -Pverbose` |
| 482 | NOT_LINEARIZABLE | k0 | 89 | `./gradlew chaos -Pruns=1 -PstartSeed=482 -Plabel=mutation-follower-reads -Pverbose` |
| 483 | NOT_LINEARIZABLE | k2 | 100 | `./gradlew chaos -Pruns=1 -PstartSeed=483 -Plabel=mutation-follower-reads -Pverbose` |
| 485 | NOT_LINEARIZABLE | k0 | 94 | `./gradlew chaos -Pruns=1 -PstartSeed=485 -Plabel=mutation-follower-reads -Pverbose` |
| 486 | NOT_LINEARIZABLE | k0 | 89 | `./gradlew chaos -Pruns=1 -PstartSeed=486 -Plabel=mutation-follower-reads -Pverbose` |
| 487 | NOT_LINEARIZABLE | k0 | 97 | `./gradlew chaos -Pruns=1 -PstartSeed=487 -Plabel=mutation-follower-reads -Pverbose` |
| 488 | NOT_LINEARIZABLE | k2 | 99 | `./gradlew chaos -Pruns=1 -PstartSeed=488 -Plabel=mutation-follower-reads -Pverbose` |
| 489 | NOT_LINEARIZABLE | k0 | 100 | `./gradlew chaos -Pruns=1 -PstartSeed=489 -Plabel=mutation-follower-reads -Pverbose` |
| 490 | NOT_LINEARIZABLE | k0 | 94 | `./gradlew chaos -Pruns=1 -PstartSeed=490 -Plabel=mutation-follower-reads -Pverbose` |
| 491 | NOT_LINEARIZABLE | k0 | 99 | `./gradlew chaos -Pruns=1 -PstartSeed=491 -Plabel=mutation-follower-reads -Pverbose` |
| 493 | NOT_LINEARIZABLE | k0 | 97 | `./gradlew chaos -Pruns=1 -PstartSeed=493 -Plabel=mutation-follower-reads -Pverbose` |
| 495 | NOT_LINEARIZABLE | k0 | 96 | `./gradlew chaos -Pruns=1 -PstartSeed=495 -Plabel=mutation-follower-reads -Pverbose` |
| 496 | NOT_LINEARIZABLE | k0 | 108 | `./gradlew chaos -Pruns=1 -PstartSeed=496 -Plabel=mutation-follower-reads -Pverbose` |
| 497 | NOT_LINEARIZABLE | k1 | 104 | `./gradlew chaos -Pruns=1 -PstartSeed=497 -Plabel=mutation-follower-reads -Pverbose` |
| 498 | NOT_LINEARIZABLE | k2 | 90 | `./gradlew chaos -Pruns=1 -PstartSeed=498 -Plabel=mutation-follower-reads -Pverbose` |
| 499 | NOT_LINEARIZABLE | k0 | 93 | `./gradlew chaos -Pruns=1 -PstartSeed=499 -Plabel=mutation-follower-reads -Pverbose` |
| 500 | NOT_LINEARIZABLE | k0 | 97 | `./gradlew chaos -Pruns=1 -PstartSeed=500 -Plabel=mutation-follower-reads -Pverbose` |

Per-seed numbers: `chaos-mutation-follower-reads-500-seeds.csv`.

## Mutation applied

This run used a deliberately broken build (`scripts/chaos-mutation.sh follower-reads 500`).
Change to `kv/src/main/java/io/github/hard3007/raftkv/kv/KvService.java`:

```diff
@@ -88,6 +88,15 @@
                 reply.accept(GetReply.newBuilder().setStatus(Status.UNKNOWN).setLeaderHint(leaderHint()).build());
             }
         });
+        if (raft.status().role() != RaftNode.Role.LEADER) {
+            answered[0] = true;
+            timeout.cancel();
+            Optional<byte[]> local = stateMachine.read(request.getKey().toByteArray());
+            GetReply.Builder builder = GetReply.newBuilder().setStatus(Status.OK).setFound(local.isPresent());
+            local.ifPresent(v -> builder.setValue(ByteString.copyFrom(v)));
+            reply.accept(builder.build());
+            return;
+        }
         raft.readIndex(new RaftNode.ReadIndexCallback() {
             @Override
             public void onReady() {
```
