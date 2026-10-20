# Chaos report: mutation-no-session-dedup

- **Command:** `./gradlew chaos -Pruns=500 -Plabel=mutation-no-session-dedup`
- **Date:** 2026-10-20T08:19:37.362041312Z
- **Git commit:** 39e1934 (with uncommitted changes)
- **Machine:** Linux 6.8.0-1064-azure amd64, 2 cores (AMD EPYC 7763 64-Core Processor), Java 21, max heap 1024 MB
- **Config:** 5 nodes, 5 clients, 3 keys, 60 ops per client, think time 0-300 ms, a fault action every 100-400 ms (virtual), client attempt timeout 1000 ms / give-up 4000 ms, checker timeout 10 s (wall)
- **Seeds:** 1..500 (500 runs)
- **Total wall time:** 14.0 s

## Verdicts

| Verdict | Runs |
|---|---|
| LINEARIZABLE | 204 |
| NOT_LINEARIZABLE | 296 |
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
| wall time (ms) | 12 | 55.4 | 1318 |

## Runs that did not pass

| Seed | Verdict | Key | Ops on key | Reproduce |
|---|---|---|---|---|
| 2 | NOT_LINEARIZABLE | k1 | 94 | `./gradlew chaos -Pruns=1 -PstartSeed=2 -Plabel=mutation-no-session-dedup -Pverbose` |
| 7 | NOT_LINEARIZABLE | k1 | 91 | `./gradlew chaos -Pruns=1 -PstartSeed=7 -Plabel=mutation-no-session-dedup -Pverbose` |
| 9 | NOT_LINEARIZABLE | k1 | 109 | `./gradlew chaos -Pruns=1 -PstartSeed=9 -Plabel=mutation-no-session-dedup -Pverbose` |
| 11 | NOT_LINEARIZABLE | k2 | 101 | `./gradlew chaos -Pruns=1 -PstartSeed=11 -Plabel=mutation-no-session-dedup -Pverbose` |
| 13 | NOT_LINEARIZABLE | k2 | 108 | `./gradlew chaos -Pruns=1 -PstartSeed=13 -Plabel=mutation-no-session-dedup -Pverbose` |
| 14 | NOT_LINEARIZABLE | k1 | 103 | `./gradlew chaos -Pruns=1 -PstartSeed=14 -Plabel=mutation-no-session-dedup -Pverbose` |
| 15 | NOT_LINEARIZABLE | k1 | 89 | `./gradlew chaos -Pruns=1 -PstartSeed=15 -Plabel=mutation-no-session-dedup -Pverbose` |
| 16 | NOT_LINEARIZABLE | k0 | 90 | `./gradlew chaos -Pruns=1 -PstartSeed=16 -Plabel=mutation-no-session-dedup -Pverbose` |
| 17 | NOT_LINEARIZABLE | k0 | 92 | `./gradlew chaos -Pruns=1 -PstartSeed=17 -Plabel=mutation-no-session-dedup -Pverbose` |
| 19 | NOT_LINEARIZABLE | k0 | 88 | `./gradlew chaos -Pruns=1 -PstartSeed=19 -Plabel=mutation-no-session-dedup -Pverbose` |
| 21 | NOT_LINEARIZABLE | k2 | 86 | `./gradlew chaos -Pruns=1 -PstartSeed=21 -Plabel=mutation-no-session-dedup -Pverbose` |
| 28 | NOT_LINEARIZABLE | k2 | 87 | `./gradlew chaos -Pruns=1 -PstartSeed=28 -Plabel=mutation-no-session-dedup -Pverbose` |
| 29 | NOT_LINEARIZABLE | k0 | 94 | `./gradlew chaos -Pruns=1 -PstartSeed=29 -Plabel=mutation-no-session-dedup -Pverbose` |
| 32 | NOT_LINEARIZABLE | k1 | 79 | `./gradlew chaos -Pruns=1 -PstartSeed=32 -Plabel=mutation-no-session-dedup -Pverbose` |
| 33 | NOT_LINEARIZABLE | k1 | 90 | `./gradlew chaos -Pruns=1 -PstartSeed=33 -Plabel=mutation-no-session-dedup -Pverbose` |
| 34 | NOT_LINEARIZABLE | k0 | 101 | `./gradlew chaos -Pruns=1 -PstartSeed=34 -Plabel=mutation-no-session-dedup -Pverbose` |
| 35 | NOT_LINEARIZABLE | k0 | 88 | `./gradlew chaos -Pruns=1 -PstartSeed=35 -Plabel=mutation-no-session-dedup -Pverbose` |
| 38 | NOT_LINEARIZABLE | k2 | 93 | `./gradlew chaos -Pruns=1 -PstartSeed=38 -Plabel=mutation-no-session-dedup -Pverbose` |
| 40 | NOT_LINEARIZABLE | k2 | 106 | `./gradlew chaos -Pruns=1 -PstartSeed=40 -Plabel=mutation-no-session-dedup -Pverbose` |
| 43 | NOT_LINEARIZABLE | k1 | 90 | `./gradlew chaos -Pruns=1 -PstartSeed=43 -Plabel=mutation-no-session-dedup -Pverbose` |
| 44 | NOT_LINEARIZABLE | k0 | 88 | `./gradlew chaos -Pruns=1 -PstartSeed=44 -Plabel=mutation-no-session-dedup -Pverbose` |
| 47 | NOT_LINEARIZABLE | k1 | 97 | `./gradlew chaos -Pruns=1 -PstartSeed=47 -Plabel=mutation-no-session-dedup -Pverbose` |
| 49 | NOT_LINEARIZABLE | k2 | 93 | `./gradlew chaos -Pruns=1 -PstartSeed=49 -Plabel=mutation-no-session-dedup -Pverbose` |
| 50 | NOT_LINEARIZABLE | k1 | 89 | `./gradlew chaos -Pruns=1 -PstartSeed=50 -Plabel=mutation-no-session-dedup -Pverbose` |
| 54 | NOT_LINEARIZABLE | k2 | 102 | `./gradlew chaos -Pruns=1 -PstartSeed=54 -Plabel=mutation-no-session-dedup -Pverbose` |
| 56 | NOT_LINEARIZABLE | k1 | 83 | `./gradlew chaos -Pruns=1 -PstartSeed=56 -Plabel=mutation-no-session-dedup -Pverbose` |
| 58 | NOT_LINEARIZABLE | k2 | 98 | `./gradlew chaos -Pruns=1 -PstartSeed=58 -Plabel=mutation-no-session-dedup -Pverbose` |
| 60 | NOT_LINEARIZABLE | k1 | 92 | `./gradlew chaos -Pruns=1 -PstartSeed=60 -Plabel=mutation-no-session-dedup -Pverbose` |
| 63 | NOT_LINEARIZABLE | k0 | 103 | `./gradlew chaos -Pruns=1 -PstartSeed=63 -Plabel=mutation-no-session-dedup -Pverbose` |
| 65 | NOT_LINEARIZABLE | k1 | 101 | `./gradlew chaos -Pruns=1 -PstartSeed=65 -Plabel=mutation-no-session-dedup -Pverbose` |
| 68 | NOT_LINEARIZABLE | k2 | 82 | `./gradlew chaos -Pruns=1 -PstartSeed=68 -Plabel=mutation-no-session-dedup -Pverbose` |
| 69 | NOT_LINEARIZABLE | k0 | 91 | `./gradlew chaos -Pruns=1 -PstartSeed=69 -Plabel=mutation-no-session-dedup -Pverbose` |
| 70 | NOT_LINEARIZABLE | k2 | 99 | `./gradlew chaos -Pruns=1 -PstartSeed=70 -Plabel=mutation-no-session-dedup -Pverbose` |
| 71 | NOT_LINEARIZABLE | k0 | 96 | `./gradlew chaos -Pruns=1 -PstartSeed=71 -Plabel=mutation-no-session-dedup -Pverbose` |
| 73 | NOT_LINEARIZABLE | k0 | 83 | `./gradlew chaos -Pruns=1 -PstartSeed=73 -Plabel=mutation-no-session-dedup -Pverbose` |
| 74 | NOT_LINEARIZABLE | k1 | 99 | `./gradlew chaos -Pruns=1 -PstartSeed=74 -Plabel=mutation-no-session-dedup -Pverbose` |
| 76 | NOT_LINEARIZABLE | k0 | 95 | `./gradlew chaos -Pruns=1 -PstartSeed=76 -Plabel=mutation-no-session-dedup -Pverbose` |
| 80 | NOT_LINEARIZABLE | k2 | 93 | `./gradlew chaos -Pruns=1 -PstartSeed=80 -Plabel=mutation-no-session-dedup -Pverbose` |
| 82 | NOT_LINEARIZABLE | k2 | 100 | `./gradlew chaos -Pruns=1 -PstartSeed=82 -Plabel=mutation-no-session-dedup -Pverbose` |
| 83 | NOT_LINEARIZABLE | k2 | 101 | `./gradlew chaos -Pruns=1 -PstartSeed=83 -Plabel=mutation-no-session-dedup -Pverbose` |
| 85 | NOT_LINEARIZABLE | k2 | 95 | `./gradlew chaos -Pruns=1 -PstartSeed=85 -Plabel=mutation-no-session-dedup -Pverbose` |
| 86 | NOT_LINEARIZABLE | k0 | 97 | `./gradlew chaos -Pruns=1 -PstartSeed=86 -Plabel=mutation-no-session-dedup -Pverbose` |
| 87 | NOT_LINEARIZABLE | k2 | 93 | `./gradlew chaos -Pruns=1 -PstartSeed=87 -Plabel=mutation-no-session-dedup -Pverbose` |
| 88 | NOT_LINEARIZABLE | k1 | 100 | `./gradlew chaos -Pruns=1 -PstartSeed=88 -Plabel=mutation-no-session-dedup -Pverbose` |
| 89 | NOT_LINEARIZABLE | k1 | 96 | `./gradlew chaos -Pruns=1 -PstartSeed=89 -Plabel=mutation-no-session-dedup -Pverbose` |
| 91 | NOT_LINEARIZABLE | k1 | 97 | `./gradlew chaos -Pruns=1 -PstartSeed=91 -Plabel=mutation-no-session-dedup -Pverbose` |
| 92 | NOT_LINEARIZABLE | k1 | 82 | `./gradlew chaos -Pruns=1 -PstartSeed=92 -Plabel=mutation-no-session-dedup -Pverbose` |
| 94 | NOT_LINEARIZABLE | k0 | 88 | `./gradlew chaos -Pruns=1 -PstartSeed=94 -Plabel=mutation-no-session-dedup -Pverbose` |
| 95 | NOT_LINEARIZABLE | k2 | 105 | `./gradlew chaos -Pruns=1 -PstartSeed=95 -Plabel=mutation-no-session-dedup -Pverbose` |
| 97 | NOT_LINEARIZABLE | k0 | 109 | `./gradlew chaos -Pruns=1 -PstartSeed=97 -Plabel=mutation-no-session-dedup -Pverbose` |
| 98 | NOT_LINEARIZABLE | k0 | 97 | `./gradlew chaos -Pruns=1 -PstartSeed=98 -Plabel=mutation-no-session-dedup -Pverbose` |
| 101 | NOT_LINEARIZABLE | k1 | 90 | `./gradlew chaos -Pruns=1 -PstartSeed=101 -Plabel=mutation-no-session-dedup -Pverbose` |
| 103 | NOT_LINEARIZABLE | k2 | 84 | `./gradlew chaos -Pruns=1 -PstartSeed=103 -Plabel=mutation-no-session-dedup -Pverbose` |
| 105 | NOT_LINEARIZABLE | k1 | 94 | `./gradlew chaos -Pruns=1 -PstartSeed=105 -Plabel=mutation-no-session-dedup -Pverbose` |
| 106 | NOT_LINEARIZABLE | k1 | 107 | `./gradlew chaos -Pruns=1 -PstartSeed=106 -Plabel=mutation-no-session-dedup -Pverbose` |
| 107 | NOT_LINEARIZABLE | k1 | 93 | `./gradlew chaos -Pruns=1 -PstartSeed=107 -Plabel=mutation-no-session-dedup -Pverbose` |
| 109 | NOT_LINEARIZABLE | k0 | 93 | `./gradlew chaos -Pruns=1 -PstartSeed=109 -Plabel=mutation-no-session-dedup -Pverbose` |
| 112 | NOT_LINEARIZABLE | k2 | 88 | `./gradlew chaos -Pruns=1 -PstartSeed=112 -Plabel=mutation-no-session-dedup -Pverbose` |
| 113 | NOT_LINEARIZABLE | k0 | 87 | `./gradlew chaos -Pruns=1 -PstartSeed=113 -Plabel=mutation-no-session-dedup -Pverbose` |
| 114 | NOT_LINEARIZABLE | k1 | 92 | `./gradlew chaos -Pruns=1 -PstartSeed=114 -Plabel=mutation-no-session-dedup -Pverbose` |
| 115 | NOT_LINEARIZABLE | k2 | 92 | `./gradlew chaos -Pruns=1 -PstartSeed=115 -Plabel=mutation-no-session-dedup -Pverbose` |
| 117 | NOT_LINEARIZABLE | k1 | 86 | `./gradlew chaos -Pruns=1 -PstartSeed=117 -Plabel=mutation-no-session-dedup -Pverbose` |
| 122 | NOT_LINEARIZABLE | k1 | 98 | `./gradlew chaos -Pruns=1 -PstartSeed=122 -Plabel=mutation-no-session-dedup -Pverbose` |
| 128 | NOT_LINEARIZABLE | k2 | 98 | `./gradlew chaos -Pruns=1 -PstartSeed=128 -Plabel=mutation-no-session-dedup -Pverbose` |
| 130 | NOT_LINEARIZABLE | k0 | 92 | `./gradlew chaos -Pruns=1 -PstartSeed=130 -Plabel=mutation-no-session-dedup -Pverbose` |
| 131 | NOT_LINEARIZABLE | k0 | 101 | `./gradlew chaos -Pruns=1 -PstartSeed=131 -Plabel=mutation-no-session-dedup -Pverbose` |
| 134 | NOT_LINEARIZABLE | k1 | 100 | `./gradlew chaos -Pruns=1 -PstartSeed=134 -Plabel=mutation-no-session-dedup -Pverbose` |
| 135 | NOT_LINEARIZABLE | k0 | 92 | `./gradlew chaos -Pruns=1 -PstartSeed=135 -Plabel=mutation-no-session-dedup -Pverbose` |
| 138 | NOT_LINEARIZABLE | k0 | 95 | `./gradlew chaos -Pruns=1 -PstartSeed=138 -Plabel=mutation-no-session-dedup -Pverbose` |
| 140 | NOT_LINEARIZABLE | k2 | 93 | `./gradlew chaos -Pruns=1 -PstartSeed=140 -Plabel=mutation-no-session-dedup -Pverbose` |
| 141 | NOT_LINEARIZABLE | k0 | 92 | `./gradlew chaos -Pruns=1 -PstartSeed=141 -Plabel=mutation-no-session-dedup -Pverbose` |
| 143 | NOT_LINEARIZABLE | k0 | 84 | `./gradlew chaos -Pruns=1 -PstartSeed=143 -Plabel=mutation-no-session-dedup -Pverbose` |
| 145 | NOT_LINEARIZABLE | k0 | 86 | `./gradlew chaos -Pruns=1 -PstartSeed=145 -Plabel=mutation-no-session-dedup -Pverbose` |
| 146 | NOT_LINEARIZABLE | k0 | 72 | `./gradlew chaos -Pruns=1 -PstartSeed=146 -Plabel=mutation-no-session-dedup -Pverbose` |
| 149 | NOT_LINEARIZABLE | k0 | 86 | `./gradlew chaos -Pruns=1 -PstartSeed=149 -Plabel=mutation-no-session-dedup -Pverbose` |
| 150 | NOT_LINEARIZABLE | k2 | 87 | `./gradlew chaos -Pruns=1 -PstartSeed=150 -Plabel=mutation-no-session-dedup -Pverbose` |
| 151 | NOT_LINEARIZABLE | k0 | 92 | `./gradlew chaos -Pruns=1 -PstartSeed=151 -Plabel=mutation-no-session-dedup -Pverbose` |
| 154 | NOT_LINEARIZABLE | k2 | 95 | `./gradlew chaos -Pruns=1 -PstartSeed=154 -Plabel=mutation-no-session-dedup -Pverbose` |
| 155 | NOT_LINEARIZABLE | k0 | 99 | `./gradlew chaos -Pruns=1 -PstartSeed=155 -Plabel=mutation-no-session-dedup -Pverbose` |
| 156 | NOT_LINEARIZABLE | k0 | 95 | `./gradlew chaos -Pruns=1 -PstartSeed=156 -Plabel=mutation-no-session-dedup -Pverbose` |
| 158 | NOT_LINEARIZABLE | k0 | 84 | `./gradlew chaos -Pruns=1 -PstartSeed=158 -Plabel=mutation-no-session-dedup -Pverbose` |
| 161 | NOT_LINEARIZABLE | k1 | 98 | `./gradlew chaos -Pruns=1 -PstartSeed=161 -Plabel=mutation-no-session-dedup -Pverbose` |
| 162 | NOT_LINEARIZABLE | k2 | 85 | `./gradlew chaos -Pruns=1 -PstartSeed=162 -Plabel=mutation-no-session-dedup -Pverbose` |
| 164 | NOT_LINEARIZABLE | k0 | 94 | `./gradlew chaos -Pruns=1 -PstartSeed=164 -Plabel=mutation-no-session-dedup -Pverbose` |
| 165 | NOT_LINEARIZABLE | k0 | 98 | `./gradlew chaos -Pruns=1 -PstartSeed=165 -Plabel=mutation-no-session-dedup -Pverbose` |
| 169 | NOT_LINEARIZABLE | k0 | 95 | `./gradlew chaos -Pruns=1 -PstartSeed=169 -Plabel=mutation-no-session-dedup -Pverbose` |
| 170 | NOT_LINEARIZABLE | k0 | 80 | `./gradlew chaos -Pruns=1 -PstartSeed=170 -Plabel=mutation-no-session-dedup -Pverbose` |
| 175 | NOT_LINEARIZABLE | k0 | 87 | `./gradlew chaos -Pruns=1 -PstartSeed=175 -Plabel=mutation-no-session-dedup -Pverbose` |
| 177 | NOT_LINEARIZABLE | k2 | 92 | `./gradlew chaos -Pruns=1 -PstartSeed=177 -Plabel=mutation-no-session-dedup -Pverbose` |
| 178 | NOT_LINEARIZABLE | k0 | 96 | `./gradlew chaos -Pruns=1 -PstartSeed=178 -Plabel=mutation-no-session-dedup -Pverbose` |
| 180 | NOT_LINEARIZABLE | k1 | 100 | `./gradlew chaos -Pruns=1 -PstartSeed=180 -Plabel=mutation-no-session-dedup -Pverbose` |
| 181 | NOT_LINEARIZABLE | k0 | 90 | `./gradlew chaos -Pruns=1 -PstartSeed=181 -Plabel=mutation-no-session-dedup -Pverbose` |
| 182 | NOT_LINEARIZABLE | k2 | 99 | `./gradlew chaos -Pruns=1 -PstartSeed=182 -Plabel=mutation-no-session-dedup -Pverbose` |
| 184 | NOT_LINEARIZABLE | k1 | 108 | `./gradlew chaos -Pruns=1 -PstartSeed=184 -Plabel=mutation-no-session-dedup -Pverbose` |
| 185 | NOT_LINEARIZABLE | k1 | 103 | `./gradlew chaos -Pruns=1 -PstartSeed=185 -Plabel=mutation-no-session-dedup -Pverbose` |
| 187 | NOT_LINEARIZABLE | k2 | 102 | `./gradlew chaos -Pruns=1 -PstartSeed=187 -Plabel=mutation-no-session-dedup -Pverbose` |
| 190 | NOT_LINEARIZABLE | k0 | 96 | `./gradlew chaos -Pruns=1 -PstartSeed=190 -Plabel=mutation-no-session-dedup -Pverbose` |
| 192 | NOT_LINEARIZABLE | k1 | 95 | `./gradlew chaos -Pruns=1 -PstartSeed=192 -Plabel=mutation-no-session-dedup -Pverbose` |
| 194 | NOT_LINEARIZABLE | k0 | 108 | `./gradlew chaos -Pruns=1 -PstartSeed=194 -Plabel=mutation-no-session-dedup -Pverbose` |
| 196 | NOT_LINEARIZABLE | k2 | 108 | `./gradlew chaos -Pruns=1 -PstartSeed=196 -Plabel=mutation-no-session-dedup -Pverbose` |
| 197 | NOT_LINEARIZABLE | k0 | 102 | `./gradlew chaos -Pruns=1 -PstartSeed=197 -Plabel=mutation-no-session-dedup -Pverbose` |
| 199 | NOT_LINEARIZABLE | k1 | 101 | `./gradlew chaos -Pruns=1 -PstartSeed=199 -Plabel=mutation-no-session-dedup -Pverbose` |
| 201 | NOT_LINEARIZABLE | k2 | 100 | `./gradlew chaos -Pruns=1 -PstartSeed=201 -Plabel=mutation-no-session-dedup -Pverbose` |
| 202 | NOT_LINEARIZABLE | k2 | 87 | `./gradlew chaos -Pruns=1 -PstartSeed=202 -Plabel=mutation-no-session-dedup -Pverbose` |
| 204 | NOT_LINEARIZABLE | k0 | 111 | `./gradlew chaos -Pruns=1 -PstartSeed=204 -Plabel=mutation-no-session-dedup -Pverbose` |
| 209 | NOT_LINEARIZABLE | k0 | 97 | `./gradlew chaos -Pruns=1 -PstartSeed=209 -Plabel=mutation-no-session-dedup -Pverbose` |
| 210 | NOT_LINEARIZABLE | k1 | 116 | `./gradlew chaos -Pruns=1 -PstartSeed=210 -Plabel=mutation-no-session-dedup -Pverbose` |
| 212 | NOT_LINEARIZABLE | k0 | 105 | `./gradlew chaos -Pruns=1 -PstartSeed=212 -Plabel=mutation-no-session-dedup -Pverbose` |
| 213 | NOT_LINEARIZABLE | k0 | 105 | `./gradlew chaos -Pruns=1 -PstartSeed=213 -Plabel=mutation-no-session-dedup -Pverbose` |
| 214 | NOT_LINEARIZABLE | k1 | 86 | `./gradlew chaos -Pruns=1 -PstartSeed=214 -Plabel=mutation-no-session-dedup -Pverbose` |
| 216 | NOT_LINEARIZABLE | k2 | 89 | `./gradlew chaos -Pruns=1 -PstartSeed=216 -Plabel=mutation-no-session-dedup -Pverbose` |
| 217 | NOT_LINEARIZABLE | k0 | 105 | `./gradlew chaos -Pruns=1 -PstartSeed=217 -Plabel=mutation-no-session-dedup -Pverbose` |
| 218 | NOT_LINEARIZABLE | k2 | 92 | `./gradlew chaos -Pruns=1 -PstartSeed=218 -Plabel=mutation-no-session-dedup -Pverbose` |
| 219 | NOT_LINEARIZABLE | k0 | 101 | `./gradlew chaos -Pruns=1 -PstartSeed=219 -Plabel=mutation-no-session-dedup -Pverbose` |
| 220 | NOT_LINEARIZABLE | k1 | 87 | `./gradlew chaos -Pruns=1 -PstartSeed=220 -Plabel=mutation-no-session-dedup -Pverbose` |
| 221 | NOT_LINEARIZABLE | k0 | 79 | `./gradlew chaos -Pruns=1 -PstartSeed=221 -Plabel=mutation-no-session-dedup -Pverbose` |
| 223 | NOT_LINEARIZABLE | k1 | 94 | `./gradlew chaos -Pruns=1 -PstartSeed=223 -Plabel=mutation-no-session-dedup -Pverbose` |
| 224 | NOT_LINEARIZABLE | k2 | 98 | `./gradlew chaos -Pruns=1 -PstartSeed=224 -Plabel=mutation-no-session-dedup -Pverbose` |
| 225 | NOT_LINEARIZABLE | k1 | 83 | `./gradlew chaos -Pruns=1 -PstartSeed=225 -Plabel=mutation-no-session-dedup -Pverbose` |
| 227 | NOT_LINEARIZABLE | k0 | 102 | `./gradlew chaos -Pruns=1 -PstartSeed=227 -Plabel=mutation-no-session-dedup -Pverbose` |
| 229 | NOT_LINEARIZABLE | k2 | 92 | `./gradlew chaos -Pruns=1 -PstartSeed=229 -Plabel=mutation-no-session-dedup -Pverbose` |
| 230 | NOT_LINEARIZABLE | k2 | 106 | `./gradlew chaos -Pruns=1 -PstartSeed=230 -Plabel=mutation-no-session-dedup -Pverbose` |
| 231 | NOT_LINEARIZABLE | k1 | 106 | `./gradlew chaos -Pruns=1 -PstartSeed=231 -Plabel=mutation-no-session-dedup -Pverbose` |
| 232 | NOT_LINEARIZABLE | k0 | 77 | `./gradlew chaos -Pruns=1 -PstartSeed=232 -Plabel=mutation-no-session-dedup -Pverbose` |
| 234 | NOT_LINEARIZABLE | k1 | 95 | `./gradlew chaos -Pruns=1 -PstartSeed=234 -Plabel=mutation-no-session-dedup -Pverbose` |
| 236 | NOT_LINEARIZABLE | k2 | 91 | `./gradlew chaos -Pruns=1 -PstartSeed=236 -Plabel=mutation-no-session-dedup -Pverbose` |
| 238 | NOT_LINEARIZABLE | k1 | 97 | `./gradlew chaos -Pruns=1 -PstartSeed=238 -Plabel=mutation-no-session-dedup -Pverbose` |
| 240 | NOT_LINEARIZABLE | k2 | 90 | `./gradlew chaos -Pruns=1 -PstartSeed=240 -Plabel=mutation-no-session-dedup -Pverbose` |
| 242 | NOT_LINEARIZABLE | k0 | 94 | `./gradlew chaos -Pruns=1 -PstartSeed=242 -Plabel=mutation-no-session-dedup -Pverbose` |
| 243 | NOT_LINEARIZABLE | k2 | 92 | `./gradlew chaos -Pruns=1 -PstartSeed=243 -Plabel=mutation-no-session-dedup -Pverbose` |
| 244 | NOT_LINEARIZABLE | k0 | 111 | `./gradlew chaos -Pruns=1 -PstartSeed=244 -Plabel=mutation-no-session-dedup -Pverbose` |
| 245 | NOT_LINEARIZABLE | k1 | 81 | `./gradlew chaos -Pruns=1 -PstartSeed=245 -Plabel=mutation-no-session-dedup -Pverbose` |
| 246 | NOT_LINEARIZABLE | k2 | 94 | `./gradlew chaos -Pruns=1 -PstartSeed=246 -Plabel=mutation-no-session-dedup -Pverbose` |
| 248 | NOT_LINEARIZABLE | k0 | 113 | `./gradlew chaos -Pruns=1 -PstartSeed=248 -Plabel=mutation-no-session-dedup -Pverbose` |
| 250 | NOT_LINEARIZABLE | k2 | 90 | `./gradlew chaos -Pruns=1 -PstartSeed=250 -Plabel=mutation-no-session-dedup -Pverbose` |
| 251 | NOT_LINEARIZABLE | k0 | 99 | `./gradlew chaos -Pruns=1 -PstartSeed=251 -Plabel=mutation-no-session-dedup -Pverbose` |
| 252 | NOT_LINEARIZABLE | k0 | 97 | `./gradlew chaos -Pruns=1 -PstartSeed=252 -Plabel=mutation-no-session-dedup -Pverbose` |
| 253 | NOT_LINEARIZABLE | k1 | 83 | `./gradlew chaos -Pruns=1 -PstartSeed=253 -Plabel=mutation-no-session-dedup -Pverbose` |
| 254 | NOT_LINEARIZABLE | k0 | 97 | `./gradlew chaos -Pruns=1 -PstartSeed=254 -Plabel=mutation-no-session-dedup -Pverbose` |
| 255 | NOT_LINEARIZABLE | k1 | 102 | `./gradlew chaos -Pruns=1 -PstartSeed=255 -Plabel=mutation-no-session-dedup -Pverbose` |
| 258 | NOT_LINEARIZABLE | k0 | 93 | `./gradlew chaos -Pruns=1 -PstartSeed=258 -Plabel=mutation-no-session-dedup -Pverbose` |
| 259 | NOT_LINEARIZABLE | k2 | 87 | `./gradlew chaos -Pruns=1 -PstartSeed=259 -Plabel=mutation-no-session-dedup -Pverbose` |
| 261 | NOT_LINEARIZABLE | k0 | 95 | `./gradlew chaos -Pruns=1 -PstartSeed=261 -Plabel=mutation-no-session-dedup -Pverbose` |
| 262 | NOT_LINEARIZABLE | k0 | 92 | `./gradlew chaos -Pruns=1 -PstartSeed=262 -Plabel=mutation-no-session-dedup -Pverbose` |
| 263 | NOT_LINEARIZABLE | k0 | 98 | `./gradlew chaos -Pruns=1 -PstartSeed=263 -Plabel=mutation-no-session-dedup -Pverbose` |
| 264 | NOT_LINEARIZABLE | k0 | 92 | `./gradlew chaos -Pruns=1 -PstartSeed=264 -Plabel=mutation-no-session-dedup -Pverbose` |
| 265 | NOT_LINEARIZABLE | k0 | 95 | `./gradlew chaos -Pruns=1 -PstartSeed=265 -Plabel=mutation-no-session-dedup -Pverbose` |
| 267 | NOT_LINEARIZABLE | k2 | 101 | `./gradlew chaos -Pruns=1 -PstartSeed=267 -Plabel=mutation-no-session-dedup -Pverbose` |
| 270 | NOT_LINEARIZABLE | k0 | 92 | `./gradlew chaos -Pruns=1 -PstartSeed=270 -Plabel=mutation-no-session-dedup -Pverbose` |
| 271 | NOT_LINEARIZABLE | k0 | 87 | `./gradlew chaos -Pruns=1 -PstartSeed=271 -Plabel=mutation-no-session-dedup -Pverbose` |
| 274 | NOT_LINEARIZABLE | k0 | 88 | `./gradlew chaos -Pruns=1 -PstartSeed=274 -Plabel=mutation-no-session-dedup -Pverbose` |
| 276 | NOT_LINEARIZABLE | k2 | 90 | `./gradlew chaos -Pruns=1 -PstartSeed=276 -Plabel=mutation-no-session-dedup -Pverbose` |
| 277 | NOT_LINEARIZABLE | k0 | 92 | `./gradlew chaos -Pruns=1 -PstartSeed=277 -Plabel=mutation-no-session-dedup -Pverbose` |
| 278 | NOT_LINEARIZABLE | k0 | 106 | `./gradlew chaos -Pruns=1 -PstartSeed=278 -Plabel=mutation-no-session-dedup -Pverbose` |
| 280 | NOT_LINEARIZABLE | k1 | 87 | `./gradlew chaos -Pruns=1 -PstartSeed=280 -Plabel=mutation-no-session-dedup -Pverbose` |
| 281 | NOT_LINEARIZABLE | k0 | 94 | `./gradlew chaos -Pruns=1 -PstartSeed=281 -Plabel=mutation-no-session-dedup -Pverbose` |
| 282 | NOT_LINEARIZABLE | k0 | 99 | `./gradlew chaos -Pruns=1 -PstartSeed=282 -Plabel=mutation-no-session-dedup -Pverbose` |
| 283 | NOT_LINEARIZABLE | k2 | 102 | `./gradlew chaos -Pruns=1 -PstartSeed=283 -Plabel=mutation-no-session-dedup -Pverbose` |
| 284 | NOT_LINEARIZABLE | k2 | 87 | `./gradlew chaos -Pruns=1 -PstartSeed=284 -Plabel=mutation-no-session-dedup -Pverbose` |
| 285 | NOT_LINEARIZABLE | k0 | 105 | `./gradlew chaos -Pruns=1 -PstartSeed=285 -Plabel=mutation-no-session-dedup -Pverbose` |
| 286 | NOT_LINEARIZABLE | k0 | 84 | `./gradlew chaos -Pruns=1 -PstartSeed=286 -Plabel=mutation-no-session-dedup -Pverbose` |
| 287 | NOT_LINEARIZABLE | k0 | 92 | `./gradlew chaos -Pruns=1 -PstartSeed=287 -Plabel=mutation-no-session-dedup -Pverbose` |
| 288 | NOT_LINEARIZABLE | k0 | 106 | `./gradlew chaos -Pruns=1 -PstartSeed=288 -Plabel=mutation-no-session-dedup -Pverbose` |
| 289 | NOT_LINEARIZABLE | k0 | 99 | `./gradlew chaos -Pruns=1 -PstartSeed=289 -Plabel=mutation-no-session-dedup -Pverbose` |
| 290 | NOT_LINEARIZABLE | k2 | 96 | `./gradlew chaos -Pruns=1 -PstartSeed=290 -Plabel=mutation-no-session-dedup -Pverbose` |
| 297 | NOT_LINEARIZABLE | k1 | 92 | `./gradlew chaos -Pruns=1 -PstartSeed=297 -Plabel=mutation-no-session-dedup -Pverbose` |
| 298 | NOT_LINEARIZABLE | k0 | 105 | `./gradlew chaos -Pruns=1 -PstartSeed=298 -Plabel=mutation-no-session-dedup -Pverbose` |
| 299 | NOT_LINEARIZABLE | k1 | 84 | `./gradlew chaos -Pruns=1 -PstartSeed=299 -Plabel=mutation-no-session-dedup -Pverbose` |
| 300 | NOT_LINEARIZABLE | k0 | 93 | `./gradlew chaos -Pruns=1 -PstartSeed=300 -Plabel=mutation-no-session-dedup -Pverbose` |
| 302 | NOT_LINEARIZABLE | k0 | 89 | `./gradlew chaos -Pruns=1 -PstartSeed=302 -Plabel=mutation-no-session-dedup -Pverbose` |
| 304 | NOT_LINEARIZABLE | k0 | 92 | `./gradlew chaos -Pruns=1 -PstartSeed=304 -Plabel=mutation-no-session-dedup -Pverbose` |
| 305 | NOT_LINEARIZABLE | k0 | 118 | `./gradlew chaos -Pruns=1 -PstartSeed=305 -Plabel=mutation-no-session-dedup -Pverbose` |
| 307 | NOT_LINEARIZABLE | k0 | 98 | `./gradlew chaos -Pruns=1 -PstartSeed=307 -Plabel=mutation-no-session-dedup -Pverbose` |
| 308 | NOT_LINEARIZABLE | k0 | 102 | `./gradlew chaos -Pruns=1 -PstartSeed=308 -Plabel=mutation-no-session-dedup -Pverbose` |
| 310 | NOT_LINEARIZABLE | k2 | 95 | `./gradlew chaos -Pruns=1 -PstartSeed=310 -Plabel=mutation-no-session-dedup -Pverbose` |
| 313 | NOT_LINEARIZABLE | k0 | 91 | `./gradlew chaos -Pruns=1 -PstartSeed=313 -Plabel=mutation-no-session-dedup -Pverbose` |
| 315 | NOT_LINEARIZABLE | k1 | 99 | `./gradlew chaos -Pruns=1 -PstartSeed=315 -Plabel=mutation-no-session-dedup -Pverbose` |
| 316 | NOT_LINEARIZABLE | k1 | 108 | `./gradlew chaos -Pruns=1 -PstartSeed=316 -Plabel=mutation-no-session-dedup -Pverbose` |
| 323 | NOT_LINEARIZABLE | k1 | 90 | `./gradlew chaos -Pruns=1 -PstartSeed=323 -Plabel=mutation-no-session-dedup -Pverbose` |
| 324 | NOT_LINEARIZABLE | k0 | 92 | `./gradlew chaos -Pruns=1 -PstartSeed=324 -Plabel=mutation-no-session-dedup -Pverbose` |
| 325 | NOT_LINEARIZABLE | k2 | 95 | `./gradlew chaos -Pruns=1 -PstartSeed=325 -Plabel=mutation-no-session-dedup -Pverbose` |
| 326 | NOT_LINEARIZABLE | k0 | 83 | `./gradlew chaos -Pruns=1 -PstartSeed=326 -Plabel=mutation-no-session-dedup -Pverbose` |
| 327 | NOT_LINEARIZABLE | k0 | 80 | `./gradlew chaos -Pruns=1 -PstartSeed=327 -Plabel=mutation-no-session-dedup -Pverbose` |
| 328 | NOT_LINEARIZABLE | k0 | 101 | `./gradlew chaos -Pruns=1 -PstartSeed=328 -Plabel=mutation-no-session-dedup -Pverbose` |
| 331 | NOT_LINEARIZABLE | k1 | 92 | `./gradlew chaos -Pruns=1 -PstartSeed=331 -Plabel=mutation-no-session-dedup -Pverbose` |
| 332 | NOT_LINEARIZABLE | k1 | 109 | `./gradlew chaos -Pruns=1 -PstartSeed=332 -Plabel=mutation-no-session-dedup -Pverbose` |
| 335 | NOT_LINEARIZABLE | k2 | 109 | `./gradlew chaos -Pruns=1 -PstartSeed=335 -Plabel=mutation-no-session-dedup -Pverbose` |
| 336 | NOT_LINEARIZABLE | k0 | 86 | `./gradlew chaos -Pruns=1 -PstartSeed=336 -Plabel=mutation-no-session-dedup -Pverbose` |
| 337 | NOT_LINEARIZABLE | k0 | 109 | `./gradlew chaos -Pruns=1 -PstartSeed=337 -Plabel=mutation-no-session-dedup -Pverbose` |
| 339 | NOT_LINEARIZABLE | k1 | 86 | `./gradlew chaos -Pruns=1 -PstartSeed=339 -Plabel=mutation-no-session-dedup -Pverbose` |
| 341 | NOT_LINEARIZABLE | k0 | 99 | `./gradlew chaos -Pruns=1 -PstartSeed=341 -Plabel=mutation-no-session-dedup -Pverbose` |
| 343 | NOT_LINEARIZABLE | k0 | 92 | `./gradlew chaos -Pruns=1 -PstartSeed=343 -Plabel=mutation-no-session-dedup -Pverbose` |
| 344 | NOT_LINEARIZABLE | k1 | 86 | `./gradlew chaos -Pruns=1 -PstartSeed=344 -Plabel=mutation-no-session-dedup -Pverbose` |
| 345 | NOT_LINEARIZABLE | k0 | 94 | `./gradlew chaos -Pruns=1 -PstartSeed=345 -Plabel=mutation-no-session-dedup -Pverbose` |
| 346 | NOT_LINEARIZABLE | k1 | 92 | `./gradlew chaos -Pruns=1 -PstartSeed=346 -Plabel=mutation-no-session-dedup -Pverbose` |
| 348 | NOT_LINEARIZABLE | k0 | 85 | `./gradlew chaos -Pruns=1 -PstartSeed=348 -Plabel=mutation-no-session-dedup -Pverbose` |
| 350 | NOT_LINEARIZABLE | k0 | 107 | `./gradlew chaos -Pruns=1 -PstartSeed=350 -Plabel=mutation-no-session-dedup -Pverbose` |
| 352 | NOT_LINEARIZABLE | k2 | 97 | `./gradlew chaos -Pruns=1 -PstartSeed=352 -Plabel=mutation-no-session-dedup -Pverbose` |
| 355 | NOT_LINEARIZABLE | k0 | 91 | `./gradlew chaos -Pruns=1 -PstartSeed=355 -Plabel=mutation-no-session-dedup -Pverbose` |
| 356 | NOT_LINEARIZABLE | k2 | 89 | `./gradlew chaos -Pruns=1 -PstartSeed=356 -Plabel=mutation-no-session-dedup -Pverbose` |
| 358 | NOT_LINEARIZABLE | k1 | 92 | `./gradlew chaos -Pruns=1 -PstartSeed=358 -Plabel=mutation-no-session-dedup -Pverbose` |
| 360 | NOT_LINEARIZABLE | k0 | 98 | `./gradlew chaos -Pruns=1 -PstartSeed=360 -Plabel=mutation-no-session-dedup -Pverbose` |
| 363 | NOT_LINEARIZABLE | k0 | 97 | `./gradlew chaos -Pruns=1 -PstartSeed=363 -Plabel=mutation-no-session-dedup -Pverbose` |
| 369 | NOT_LINEARIZABLE | k2 | 99 | `./gradlew chaos -Pruns=1 -PstartSeed=369 -Plabel=mutation-no-session-dedup -Pverbose` |
| 370 | NOT_LINEARIZABLE | k1 | 100 | `./gradlew chaos -Pruns=1 -PstartSeed=370 -Plabel=mutation-no-session-dedup -Pverbose` |
| 371 | NOT_LINEARIZABLE | k1 | 89 | `./gradlew chaos -Pruns=1 -PstartSeed=371 -Plabel=mutation-no-session-dedup -Pverbose` |
| 372 | NOT_LINEARIZABLE | k1 | 93 | `./gradlew chaos -Pruns=1 -PstartSeed=372 -Plabel=mutation-no-session-dedup -Pverbose` |
| 373 | NOT_LINEARIZABLE | k1 | 89 | `./gradlew chaos -Pruns=1 -PstartSeed=373 -Plabel=mutation-no-session-dedup -Pverbose` |
| 374 | NOT_LINEARIZABLE | k0 | 94 | `./gradlew chaos -Pruns=1 -PstartSeed=374 -Plabel=mutation-no-session-dedup -Pverbose` |
| 375 | NOT_LINEARIZABLE | k1 | 93 | `./gradlew chaos -Pruns=1 -PstartSeed=375 -Plabel=mutation-no-session-dedup -Pverbose` |
| 377 | NOT_LINEARIZABLE | k0 | 101 | `./gradlew chaos -Pruns=1 -PstartSeed=377 -Plabel=mutation-no-session-dedup -Pverbose` |
| 378 | NOT_LINEARIZABLE | k1 | 98 | `./gradlew chaos -Pruns=1 -PstartSeed=378 -Plabel=mutation-no-session-dedup -Pverbose` |
| 379 | NOT_LINEARIZABLE | k0 | 88 | `./gradlew chaos -Pruns=1 -PstartSeed=379 -Plabel=mutation-no-session-dedup -Pverbose` |
| 380 | NOT_LINEARIZABLE | k0 | 84 | `./gradlew chaos -Pruns=1 -PstartSeed=380 -Plabel=mutation-no-session-dedup -Pverbose` |
| 381 | NOT_LINEARIZABLE | k1 | 94 | `./gradlew chaos -Pruns=1 -PstartSeed=381 -Plabel=mutation-no-session-dedup -Pverbose` |
| 382 | NOT_LINEARIZABLE | k2 | 86 | `./gradlew chaos -Pruns=1 -PstartSeed=382 -Plabel=mutation-no-session-dedup -Pverbose` |
| 384 | NOT_LINEARIZABLE | k1 | 97 | `./gradlew chaos -Pruns=1 -PstartSeed=384 -Plabel=mutation-no-session-dedup -Pverbose` |
| 385 | NOT_LINEARIZABLE | k0 | 100 | `./gradlew chaos -Pruns=1 -PstartSeed=385 -Plabel=mutation-no-session-dedup -Pverbose` |
| 386 | NOT_LINEARIZABLE | k0 | 106 | `./gradlew chaos -Pruns=1 -PstartSeed=386 -Plabel=mutation-no-session-dedup -Pverbose` |
| 387 | NOT_LINEARIZABLE | k0 | 88 | `./gradlew chaos -Pruns=1 -PstartSeed=387 -Plabel=mutation-no-session-dedup -Pverbose` |
| 388 | NOT_LINEARIZABLE | k0 | 94 | `./gradlew chaos -Pruns=1 -PstartSeed=388 -Plabel=mutation-no-session-dedup -Pverbose` |
| 390 | NOT_LINEARIZABLE | k2 | 98 | `./gradlew chaos -Pruns=1 -PstartSeed=390 -Plabel=mutation-no-session-dedup -Pverbose` |
| 392 | NOT_LINEARIZABLE | k1 | 94 | `./gradlew chaos -Pruns=1 -PstartSeed=392 -Plabel=mutation-no-session-dedup -Pverbose` |
| 393 | NOT_LINEARIZABLE | k2 | 97 | `./gradlew chaos -Pruns=1 -PstartSeed=393 -Plabel=mutation-no-session-dedup -Pverbose` |
| 394 | NOT_LINEARIZABLE | k0 | 93 | `./gradlew chaos -Pruns=1 -PstartSeed=394 -Plabel=mutation-no-session-dedup -Pverbose` |
| 395 | NOT_LINEARIZABLE | k1 | 93 | `./gradlew chaos -Pruns=1 -PstartSeed=395 -Plabel=mutation-no-session-dedup -Pverbose` |
| 397 | NOT_LINEARIZABLE | k2 | 95 | `./gradlew chaos -Pruns=1 -PstartSeed=397 -Plabel=mutation-no-session-dedup -Pverbose` |
| 399 | NOT_LINEARIZABLE | k1 | 93 | `./gradlew chaos -Pruns=1 -PstartSeed=399 -Plabel=mutation-no-session-dedup -Pverbose` |
| 400 | NOT_LINEARIZABLE | k1 | 98 | `./gradlew chaos -Pruns=1 -PstartSeed=400 -Plabel=mutation-no-session-dedup -Pverbose` |
| 401 | NOT_LINEARIZABLE | k1 | 92 | `./gradlew chaos -Pruns=1 -PstartSeed=401 -Plabel=mutation-no-session-dedup -Pverbose` |
| 403 | NOT_LINEARIZABLE | k0 | 96 | `./gradlew chaos -Pruns=1 -PstartSeed=403 -Plabel=mutation-no-session-dedup -Pverbose` |
| 404 | NOT_LINEARIZABLE | k2 | 80 | `./gradlew chaos -Pruns=1 -PstartSeed=404 -Plabel=mutation-no-session-dedup -Pverbose` |
| 406 | NOT_LINEARIZABLE | k0 | 92 | `./gradlew chaos -Pruns=1 -PstartSeed=406 -Plabel=mutation-no-session-dedup -Pverbose` |
| 407 | NOT_LINEARIZABLE | k2 | 97 | `./gradlew chaos -Pruns=1 -PstartSeed=407 -Plabel=mutation-no-session-dedup -Pverbose` |
| 410 | NOT_LINEARIZABLE | k0 | 111 | `./gradlew chaos -Pruns=1 -PstartSeed=410 -Plabel=mutation-no-session-dedup -Pverbose` |
| 412 | NOT_LINEARIZABLE | k0 | 90 | `./gradlew chaos -Pruns=1 -PstartSeed=412 -Plabel=mutation-no-session-dedup -Pverbose` |
| 413 | NOT_LINEARIZABLE | k0 | 97 | `./gradlew chaos -Pruns=1 -PstartSeed=413 -Plabel=mutation-no-session-dedup -Pverbose` |
| 415 | NOT_LINEARIZABLE | k1 | 84 | `./gradlew chaos -Pruns=1 -PstartSeed=415 -Plabel=mutation-no-session-dedup -Pverbose` |
| 416 | NOT_LINEARIZABLE | k0 | 84 | `./gradlew chaos -Pruns=1 -PstartSeed=416 -Plabel=mutation-no-session-dedup -Pverbose` |
| 417 | NOT_LINEARIZABLE | k0 | 83 | `./gradlew chaos -Pruns=1 -PstartSeed=417 -Plabel=mutation-no-session-dedup -Pverbose` |
| 418 | NOT_LINEARIZABLE | k2 | 92 | `./gradlew chaos -Pruns=1 -PstartSeed=418 -Plabel=mutation-no-session-dedup -Pverbose` |
| 420 | NOT_LINEARIZABLE | k0 | 95 | `./gradlew chaos -Pruns=1 -PstartSeed=420 -Plabel=mutation-no-session-dedup -Pverbose` |
| 422 | NOT_LINEARIZABLE | k1 | 99 | `./gradlew chaos -Pruns=1 -PstartSeed=422 -Plabel=mutation-no-session-dedup -Pverbose` |
| 423 | NOT_LINEARIZABLE | k2 | 106 | `./gradlew chaos -Pruns=1 -PstartSeed=423 -Plabel=mutation-no-session-dedup -Pverbose` |
| 424 | NOT_LINEARIZABLE | k0 | 95 | `./gradlew chaos -Pruns=1 -PstartSeed=424 -Plabel=mutation-no-session-dedup -Pverbose` |
| 425 | NOT_LINEARIZABLE | k0 | 95 | `./gradlew chaos -Pruns=1 -PstartSeed=425 -Plabel=mutation-no-session-dedup -Pverbose` |
| 429 | NOT_LINEARIZABLE | k2 | 97 | `./gradlew chaos -Pruns=1 -PstartSeed=429 -Plabel=mutation-no-session-dedup -Pverbose` |
| 430 | NOT_LINEARIZABLE | k2 | 100 | `./gradlew chaos -Pruns=1 -PstartSeed=430 -Plabel=mutation-no-session-dedup -Pverbose` |
| 431 | NOT_LINEARIZABLE | k0 | 92 | `./gradlew chaos -Pruns=1 -PstartSeed=431 -Plabel=mutation-no-session-dedup -Pverbose` |
| 432 | NOT_LINEARIZABLE | k1 | 91 | `./gradlew chaos -Pruns=1 -PstartSeed=432 -Plabel=mutation-no-session-dedup -Pverbose` |
| 434 | NOT_LINEARIZABLE | k2 | 89 | `./gradlew chaos -Pruns=1 -PstartSeed=434 -Plabel=mutation-no-session-dedup -Pverbose` |
| 435 | NOT_LINEARIZABLE | k0 | 93 | `./gradlew chaos -Pruns=1 -PstartSeed=435 -Plabel=mutation-no-session-dedup -Pverbose` |
| 437 | NOT_LINEARIZABLE | k0 | 91 | `./gradlew chaos -Pruns=1 -PstartSeed=437 -Plabel=mutation-no-session-dedup -Pverbose` |
| 438 | NOT_LINEARIZABLE | k2 | 103 | `./gradlew chaos -Pruns=1 -PstartSeed=438 -Plabel=mutation-no-session-dedup -Pverbose` |
| 439 | NOT_LINEARIZABLE | k0 | 92 | `./gradlew chaos -Pruns=1 -PstartSeed=439 -Plabel=mutation-no-session-dedup -Pverbose` |
| 440 | NOT_LINEARIZABLE | k2 | 93 | `./gradlew chaos -Pruns=1 -PstartSeed=440 -Plabel=mutation-no-session-dedup -Pverbose` |
| 441 | NOT_LINEARIZABLE | k1 | 94 | `./gradlew chaos -Pruns=1 -PstartSeed=441 -Plabel=mutation-no-session-dedup -Pverbose` |
| 442 | NOT_LINEARIZABLE | k2 | 105 | `./gradlew chaos -Pruns=1 -PstartSeed=442 -Plabel=mutation-no-session-dedup -Pverbose` |
| 443 | NOT_LINEARIZABLE | k2 | 86 | `./gradlew chaos -Pruns=1 -PstartSeed=443 -Plabel=mutation-no-session-dedup -Pverbose` |
| 445 | NOT_LINEARIZABLE | k2 | 93 | `./gradlew chaos -Pruns=1 -PstartSeed=445 -Plabel=mutation-no-session-dedup -Pverbose` |
| 446 | NOT_LINEARIZABLE | k0 | 121 | `./gradlew chaos -Pruns=1 -PstartSeed=446 -Plabel=mutation-no-session-dedup -Pverbose` |
| 447 | NOT_LINEARIZABLE | k0 | 98 | `./gradlew chaos -Pruns=1 -PstartSeed=447 -Plabel=mutation-no-session-dedup -Pverbose` |
| 448 | NOT_LINEARIZABLE | k1 | 101 | `./gradlew chaos -Pruns=1 -PstartSeed=448 -Plabel=mutation-no-session-dedup -Pverbose` |
| 449 | NOT_LINEARIZABLE | k0 | 80 | `./gradlew chaos -Pruns=1 -PstartSeed=449 -Plabel=mutation-no-session-dedup -Pverbose` |
| 450 | NOT_LINEARIZABLE | k2 | 90 | `./gradlew chaos -Pruns=1 -PstartSeed=450 -Plabel=mutation-no-session-dedup -Pverbose` |
| 451 | NOT_LINEARIZABLE | k2 | 91 | `./gradlew chaos -Pruns=1 -PstartSeed=451 -Plabel=mutation-no-session-dedup -Pverbose` |
| 452 | NOT_LINEARIZABLE | k1 | 93 | `./gradlew chaos -Pruns=1 -PstartSeed=452 -Plabel=mutation-no-session-dedup -Pverbose` |
| 453 | NOT_LINEARIZABLE | k2 | 86 | `./gradlew chaos -Pruns=1 -PstartSeed=453 -Plabel=mutation-no-session-dedup -Pverbose` |
| 454 | NOT_LINEARIZABLE | k2 | 87 | `./gradlew chaos -Pruns=1 -PstartSeed=454 -Plabel=mutation-no-session-dedup -Pverbose` |
| 456 | NOT_LINEARIZABLE | k0 | 102 | `./gradlew chaos -Pruns=1 -PstartSeed=456 -Plabel=mutation-no-session-dedup -Pverbose` |
| 458 | NOT_LINEARIZABLE | k2 | 97 | `./gradlew chaos -Pruns=1 -PstartSeed=458 -Plabel=mutation-no-session-dedup -Pverbose` |
| 460 | NOT_LINEARIZABLE | k1 | 96 | `./gradlew chaos -Pruns=1 -PstartSeed=460 -Plabel=mutation-no-session-dedup -Pverbose` |
| 461 | NOT_LINEARIZABLE | k0 | 86 | `./gradlew chaos -Pruns=1 -PstartSeed=461 -Plabel=mutation-no-session-dedup -Pverbose` |
| 465 | NOT_LINEARIZABLE | k0 | 96 | `./gradlew chaos -Pruns=1 -PstartSeed=465 -Plabel=mutation-no-session-dedup -Pverbose` |
| 466 | NOT_LINEARIZABLE | k2 | 107 | `./gradlew chaos -Pruns=1 -PstartSeed=466 -Plabel=mutation-no-session-dedup -Pverbose` |
| 468 | NOT_LINEARIZABLE | k0 | 110 | `./gradlew chaos -Pruns=1 -PstartSeed=468 -Plabel=mutation-no-session-dedup -Pverbose` |
| 470 | NOT_LINEARIZABLE | k0 | 106 | `./gradlew chaos -Pruns=1 -PstartSeed=470 -Plabel=mutation-no-session-dedup -Pverbose` |
| 473 | NOT_LINEARIZABLE | k1 | 95 | `./gradlew chaos -Pruns=1 -PstartSeed=473 -Plabel=mutation-no-session-dedup -Pverbose` |
| 474 | NOT_LINEARIZABLE | k1 | 107 | `./gradlew chaos -Pruns=1 -PstartSeed=474 -Plabel=mutation-no-session-dedup -Pverbose` |
| 475 | NOT_LINEARIZABLE | k1 | 91 | `./gradlew chaos -Pruns=1 -PstartSeed=475 -Plabel=mutation-no-session-dedup -Pverbose` |
| 476 | NOT_LINEARIZABLE | k0 | 93 | `./gradlew chaos -Pruns=1 -PstartSeed=476 -Plabel=mutation-no-session-dedup -Pverbose` |
| 477 | NOT_LINEARIZABLE | k1 | 106 | `./gradlew chaos -Pruns=1 -PstartSeed=477 -Plabel=mutation-no-session-dedup -Pverbose` |
| 478 | NOT_LINEARIZABLE | k2 | 83 | `./gradlew chaos -Pruns=1 -PstartSeed=478 -Plabel=mutation-no-session-dedup -Pverbose` |
| 479 | NOT_LINEARIZABLE | k0 | 83 | `./gradlew chaos -Pruns=1 -PstartSeed=479 -Plabel=mutation-no-session-dedup -Pverbose` |
| 480 | NOT_LINEARIZABLE | k0 | 94 | `./gradlew chaos -Pruns=1 -PstartSeed=480 -Plabel=mutation-no-session-dedup -Pverbose` |
| 481 | NOT_LINEARIZABLE | k1 | 89 | `./gradlew chaos -Pruns=1 -PstartSeed=481 -Plabel=mutation-no-session-dedup -Pverbose` |
| 482 | NOT_LINEARIZABLE | k0 | 103 | `./gradlew chaos -Pruns=1 -PstartSeed=482 -Plabel=mutation-no-session-dedup -Pverbose` |
| 487 | NOT_LINEARIZABLE | k2 | 101 | `./gradlew chaos -Pruns=1 -PstartSeed=487 -Plabel=mutation-no-session-dedup -Pverbose` |
| 490 | NOT_LINEARIZABLE | k2 | 95 | `./gradlew chaos -Pruns=1 -PstartSeed=490 -Plabel=mutation-no-session-dedup -Pverbose` |
| 491 | NOT_LINEARIZABLE | k1 | 96 | `./gradlew chaos -Pruns=1 -PstartSeed=491 -Plabel=mutation-no-session-dedup -Pverbose` |
| 492 | NOT_LINEARIZABLE | k2 | 92 | `./gradlew chaos -Pruns=1 -PstartSeed=492 -Plabel=mutation-no-session-dedup -Pverbose` |
| 494 | NOT_LINEARIZABLE | k2 | 102 | `./gradlew chaos -Pruns=1 -PstartSeed=494 -Plabel=mutation-no-session-dedup -Pverbose` |
| 495 | NOT_LINEARIZABLE | k1 | 87 | `./gradlew chaos -Pruns=1 -PstartSeed=495 -Plabel=mutation-no-session-dedup -Pverbose` |
| 496 | NOT_LINEARIZABLE | k1 | 91 | `./gradlew chaos -Pruns=1 -PstartSeed=496 -Plabel=mutation-no-session-dedup -Pverbose` |
| 497 | NOT_LINEARIZABLE | k1 | 99 | `./gradlew chaos -Pruns=1 -PstartSeed=497 -Plabel=mutation-no-session-dedup -Pverbose` |
| 500 | NOT_LINEARIZABLE | k1 | 103 | `./gradlew chaos -Pruns=1 -PstartSeed=500 -Plabel=mutation-no-session-dedup -Pverbose` |

Per-seed numbers: `chaos-mutation-no-session-dedup-500-seeds.csv`.

## Mutation applied

This run used a deliberately broken build (`scripts/chaos-mutation.sh no-session-dedup 500`).
Change to `kv/src/main/java/io/github/hard3007/raftkv/kv/KvStateMachine.java`:

```diff
@@ -106,16 +106,6 @@
     private WriteResult applyCommand(KvCommand command, WriteBatch batch) {
         byte[] sessionKey = KvKeys.sessionKey(command.getClientId());
         Optional<KvKeys.Session> session = tree.get(sessionKey).map(KvKeys.Session::decode);
-        if (session.isPresent()) {
-            long last = session.get().lastSequence();
-            if (command.getSequence() == last) {
-                return new WriteResult(Outcome.DUPLICATE, session.get().existed());
-            }
-            if (command.getSequence() < last) {
-                return new WriteResult(Outcome.STALE, false);
-            }
-        }
-
         boolean existed = false;
         switch (command.getOpCase()) {
             case PUT -> batch.put(KvKeys.dataKey(command.getPut().getKey().toByteArray()),
```
