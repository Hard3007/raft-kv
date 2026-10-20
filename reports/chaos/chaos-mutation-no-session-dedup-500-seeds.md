# Chaos report: mutation-no-session-dedup

- **Command:** `./gradlew chaos -Pruns=500 -Plabel=mutation-no-session-dedup`
- **Date:** 2026-10-20T09:41:40.707919972Z
- **Git commit:** 1c764a9 (with uncommitted changes)
- **Machine:** Linux 6.8.0-1064-azure amd64, 2 cores (AMD EPYC 7763 64-Core Processor), Java 21, max heap 1024 MB
- **Config:** 5 nodes, 5 clients, 3 keys, 60 ops per client, think time 0-300 ms, a fault action every 100-400 ms (virtual), client attempt timeout 1000 ms / give-up 4000 ms, checker timeout 10 s (wall); PreVote on, CheckQuorum on, a snapshot every 20 entries in 64-byte InstallSnapshot chunks
- **Seeds:** 1..500 (500 runs)
- **Total wall time:** 15.3 s

## Verdicts

| Verdict | Runs |
|---|---|
| LINEARIZABLE | 234 |
| NOT_LINEARIZABLE | 266 |
| INCONCLUSIVE | 0 |
| runs where the system failed an internal check | 0 |

Only LINEARIZABLE runs count as passing. INCONCLUSIVE means the checker ran out of time. An internal-check failure (a safety assertion, or a node refusing to start because committed entries were lost) has no verdict and does not pass either.

## Per seed (min / mean / max)

| Measure | Min | Mean | Max |
|---|---|---|---|
| operations | 300 | 300.0 | 300 |
| completed operations | 197 | 254.0 | 292 |
| pending writes (unknown outcome) | 4 | 23.4 | 62 |
| reads left out (no definite answer) | 2 | 22.6 | 52 |
| node crashes (power loss) | 13 | 29.3 | 49 |
|   of which mid-I/O (crash at a random write/fsync/rename) | 4 | 15.0 | 31 |
| partitions | 27 | 67.7 | 123 |
| message-loss periods | 8 | 21.1 | 46 |
| messages lost | 155 | 444.6 | 747 |
| long-delay periods | 9 | 20.9 | 40 |
| snapshots taken (all nodes) | 22 | 29.5 | 39 |
| InstallSnapshots completed (all nodes) | 6 | 11.9 | 19 |
| final term (highest across nodes) | 24 | 61.1 | 108 |
| virtual time (s) | 37.4 | 84.5 | 139.5 |
| wall time (ms) | 11 | 60.7 | 3753 |

## Runs that did not pass

| Seed | Verdict | Key / failure | Ops on key | Reproduce |
|---|---|---|---|---|
| 1 | NOT_LINEARIZABLE | k0 | 101 | `./gradlew chaos -Pruns=1 -PstartSeed=1 -Plabel=mutation-no-session-dedup -Pverbose` |
| 8 | NOT_LINEARIZABLE | k2 | 95 | `./gradlew chaos -Pruns=1 -PstartSeed=8 -Plabel=mutation-no-session-dedup -Pverbose` |
| 10 | NOT_LINEARIZABLE | k0 | 91 | `./gradlew chaos -Pruns=1 -PstartSeed=10 -Plabel=mutation-no-session-dedup -Pverbose` |
| 11 | NOT_LINEARIZABLE | k0 | 89 | `./gradlew chaos -Pruns=1 -PstartSeed=11 -Plabel=mutation-no-session-dedup -Pverbose` |
| 12 | NOT_LINEARIZABLE | k1 | 106 | `./gradlew chaos -Pruns=1 -PstartSeed=12 -Plabel=mutation-no-session-dedup -Pverbose` |
| 13 | NOT_LINEARIZABLE | k0 | 86 | `./gradlew chaos -Pruns=1 -PstartSeed=13 -Plabel=mutation-no-session-dedup -Pverbose` |
| 17 | NOT_LINEARIZABLE | k0 | 84 | `./gradlew chaos -Pruns=1 -PstartSeed=17 -Plabel=mutation-no-session-dedup -Pverbose` |
| 18 | NOT_LINEARIZABLE | k2 | 83 | `./gradlew chaos -Pruns=1 -PstartSeed=18 -Plabel=mutation-no-session-dedup -Pverbose` |
| 22 | NOT_LINEARIZABLE | k0 | 103 | `./gradlew chaos -Pruns=1 -PstartSeed=22 -Plabel=mutation-no-session-dedup -Pverbose` |
| 24 | NOT_LINEARIZABLE | k0 | 102 | `./gradlew chaos -Pruns=1 -PstartSeed=24 -Plabel=mutation-no-session-dedup -Pverbose` |
| 27 | NOT_LINEARIZABLE | k2 | 78 | `./gradlew chaos -Pruns=1 -PstartSeed=27 -Plabel=mutation-no-session-dedup -Pverbose` |
| 29 | NOT_LINEARIZABLE | k0 | 96 | `./gradlew chaos -Pruns=1 -PstartSeed=29 -Plabel=mutation-no-session-dedup -Pverbose` |
| 31 | NOT_LINEARIZABLE | k2 | 87 | `./gradlew chaos -Pruns=1 -PstartSeed=31 -Plabel=mutation-no-session-dedup -Pverbose` |
| 32 | NOT_LINEARIZABLE | k0 | 84 | `./gradlew chaos -Pruns=1 -PstartSeed=32 -Plabel=mutation-no-session-dedup -Pverbose` |
| 34 | NOT_LINEARIZABLE | k0 | 91 | `./gradlew chaos -Pruns=1 -PstartSeed=34 -Plabel=mutation-no-session-dedup -Pverbose` |
| 36 | NOT_LINEARIZABLE | k0 | 102 | `./gradlew chaos -Pruns=1 -PstartSeed=36 -Plabel=mutation-no-session-dedup -Pverbose` |
| 39 | NOT_LINEARIZABLE | k1 | 102 | `./gradlew chaos -Pruns=1 -PstartSeed=39 -Plabel=mutation-no-session-dedup -Pverbose` |
| 40 | NOT_LINEARIZABLE | k1 | 97 | `./gradlew chaos -Pruns=1 -PstartSeed=40 -Plabel=mutation-no-session-dedup -Pverbose` |
| 41 | NOT_LINEARIZABLE | k2 | 93 | `./gradlew chaos -Pruns=1 -PstartSeed=41 -Plabel=mutation-no-session-dedup -Pverbose` |
| 42 | NOT_LINEARIZABLE | k0 | 99 | `./gradlew chaos -Pruns=1 -PstartSeed=42 -Plabel=mutation-no-session-dedup -Pverbose` |
| 46 | NOT_LINEARIZABLE | k2 | 88 | `./gradlew chaos -Pruns=1 -PstartSeed=46 -Plabel=mutation-no-session-dedup -Pverbose` |
| 50 | NOT_LINEARIZABLE | k0 | 85 | `./gradlew chaos -Pruns=1 -PstartSeed=50 -Plabel=mutation-no-session-dedup -Pverbose` |
| 51 | NOT_LINEARIZABLE | k1 | 104 | `./gradlew chaos -Pruns=1 -PstartSeed=51 -Plabel=mutation-no-session-dedup -Pverbose` |
| 58 | NOT_LINEARIZABLE | k2 | 76 | `./gradlew chaos -Pruns=1 -PstartSeed=58 -Plabel=mutation-no-session-dedup -Pverbose` |
| 60 | NOT_LINEARIZABLE | k2 | 96 | `./gradlew chaos -Pruns=1 -PstartSeed=60 -Plabel=mutation-no-session-dedup -Pverbose` |
| 66 | NOT_LINEARIZABLE | k1 | 91 | `./gradlew chaos -Pruns=1 -PstartSeed=66 -Plabel=mutation-no-session-dedup -Pverbose` |
| 67 | NOT_LINEARIZABLE | k1 | 87 | `./gradlew chaos -Pruns=1 -PstartSeed=67 -Plabel=mutation-no-session-dedup -Pverbose` |
| 69 | NOT_LINEARIZABLE | k0 | 101 | `./gradlew chaos -Pruns=1 -PstartSeed=69 -Plabel=mutation-no-session-dedup -Pverbose` |
| 70 | NOT_LINEARIZABLE | k2 | 101 | `./gradlew chaos -Pruns=1 -PstartSeed=70 -Plabel=mutation-no-session-dedup -Pverbose` |
| 73 | NOT_LINEARIZABLE | k1 | 78 | `./gradlew chaos -Pruns=1 -PstartSeed=73 -Plabel=mutation-no-session-dedup -Pverbose` |
| 75 | NOT_LINEARIZABLE | k0 | 104 | `./gradlew chaos -Pruns=1 -PstartSeed=75 -Plabel=mutation-no-session-dedup -Pverbose` |
| 78 | NOT_LINEARIZABLE | k2 | 90 | `./gradlew chaos -Pruns=1 -PstartSeed=78 -Plabel=mutation-no-session-dedup -Pverbose` |
| 80 | NOT_LINEARIZABLE | k0 | 101 | `./gradlew chaos -Pruns=1 -PstartSeed=80 -Plabel=mutation-no-session-dedup -Pverbose` |
| 83 | NOT_LINEARIZABLE | k1 | 84 | `./gradlew chaos -Pruns=1 -PstartSeed=83 -Plabel=mutation-no-session-dedup -Pverbose` |
| 87 | NOT_LINEARIZABLE | k1 | 92 | `./gradlew chaos -Pruns=1 -PstartSeed=87 -Plabel=mutation-no-session-dedup -Pverbose` |
| 88 | NOT_LINEARIZABLE | k1 | 98 | `./gradlew chaos -Pruns=1 -PstartSeed=88 -Plabel=mutation-no-session-dedup -Pverbose` |
| 91 | NOT_LINEARIZABLE | k2 | 79 | `./gradlew chaos -Pruns=1 -PstartSeed=91 -Plabel=mutation-no-session-dedup -Pverbose` |
| 93 | NOT_LINEARIZABLE | k1 | 83 | `./gradlew chaos -Pruns=1 -PstartSeed=93 -Plabel=mutation-no-session-dedup -Pverbose` |
| 95 | NOT_LINEARIZABLE | k0 | 111 | `./gradlew chaos -Pruns=1 -PstartSeed=95 -Plabel=mutation-no-session-dedup -Pverbose` |
| 96 | NOT_LINEARIZABLE | k1 | 108 | `./gradlew chaos -Pruns=1 -PstartSeed=96 -Plabel=mutation-no-session-dedup -Pverbose` |
| 97 | NOT_LINEARIZABLE | k1 | 97 | `./gradlew chaos -Pruns=1 -PstartSeed=97 -Plabel=mutation-no-session-dedup -Pverbose` |
| 98 | NOT_LINEARIZABLE | k2 | 89 | `./gradlew chaos -Pruns=1 -PstartSeed=98 -Plabel=mutation-no-session-dedup -Pverbose` |
| 102 | NOT_LINEARIZABLE | k0 | 85 | `./gradlew chaos -Pruns=1 -PstartSeed=102 -Plabel=mutation-no-session-dedup -Pverbose` |
| 104 | NOT_LINEARIZABLE | k2 | 102 | `./gradlew chaos -Pruns=1 -PstartSeed=104 -Plabel=mutation-no-session-dedup -Pverbose` |
| 105 | NOT_LINEARIZABLE | k2 | 101 | `./gradlew chaos -Pruns=1 -PstartSeed=105 -Plabel=mutation-no-session-dedup -Pverbose` |
| 106 | NOT_LINEARIZABLE | k1 | 77 | `./gradlew chaos -Pruns=1 -PstartSeed=106 -Plabel=mutation-no-session-dedup -Pverbose` |
| 107 | NOT_LINEARIZABLE | k0 | 81 | `./gradlew chaos -Pruns=1 -PstartSeed=107 -Plabel=mutation-no-session-dedup -Pverbose` |
| 112 | NOT_LINEARIZABLE | k0 | 91 | `./gradlew chaos -Pruns=1 -PstartSeed=112 -Plabel=mutation-no-session-dedup -Pverbose` |
| 114 | NOT_LINEARIZABLE | k2 | 87 | `./gradlew chaos -Pruns=1 -PstartSeed=114 -Plabel=mutation-no-session-dedup -Pverbose` |
| 115 | NOT_LINEARIZABLE | k0 | 99 | `./gradlew chaos -Pruns=1 -PstartSeed=115 -Plabel=mutation-no-session-dedup -Pverbose` |
| 117 | NOT_LINEARIZABLE | k1 | 83 | `./gradlew chaos -Pruns=1 -PstartSeed=117 -Plabel=mutation-no-session-dedup -Pverbose` |
| 118 | NOT_LINEARIZABLE | k1 | 90 | `./gradlew chaos -Pruns=1 -PstartSeed=118 -Plabel=mutation-no-session-dedup -Pverbose` |
| 119 | NOT_LINEARIZABLE | k0 | 96 | `./gradlew chaos -Pruns=1 -PstartSeed=119 -Plabel=mutation-no-session-dedup -Pverbose` |
| 124 | NOT_LINEARIZABLE | k1 | 79 | `./gradlew chaos -Pruns=1 -PstartSeed=124 -Plabel=mutation-no-session-dedup -Pverbose` |
| 127 | NOT_LINEARIZABLE | k1 | 96 | `./gradlew chaos -Pruns=1 -PstartSeed=127 -Plabel=mutation-no-session-dedup -Pverbose` |
| 130 | NOT_LINEARIZABLE | k0 | 73 | `./gradlew chaos -Pruns=1 -PstartSeed=130 -Plabel=mutation-no-session-dedup -Pverbose` |
| 131 | NOT_LINEARIZABLE | k2 | 85 | `./gradlew chaos -Pruns=1 -PstartSeed=131 -Plabel=mutation-no-session-dedup -Pverbose` |
| 134 | NOT_LINEARIZABLE | k2 | 107 | `./gradlew chaos -Pruns=1 -PstartSeed=134 -Plabel=mutation-no-session-dedup -Pverbose` |
| 138 | NOT_LINEARIZABLE | k1 | 108 | `./gradlew chaos -Pruns=1 -PstartSeed=138 -Plabel=mutation-no-session-dedup -Pverbose` |
| 141 | NOT_LINEARIZABLE | k0 | 84 | `./gradlew chaos -Pruns=1 -PstartSeed=141 -Plabel=mutation-no-session-dedup -Pverbose` |
| 142 | NOT_LINEARIZABLE | k0 | 88 | `./gradlew chaos -Pruns=1 -PstartSeed=142 -Plabel=mutation-no-session-dedup -Pverbose` |
| 147 | NOT_LINEARIZABLE | k1 | 99 | `./gradlew chaos -Pruns=1 -PstartSeed=147 -Plabel=mutation-no-session-dedup -Pverbose` |
| 148 | NOT_LINEARIZABLE | k0 | 90 | `./gradlew chaos -Pruns=1 -PstartSeed=148 -Plabel=mutation-no-session-dedup -Pverbose` |
| 149 | NOT_LINEARIZABLE | k1 | 87 | `./gradlew chaos -Pruns=1 -PstartSeed=149 -Plabel=mutation-no-session-dedup -Pverbose` |
| 151 | NOT_LINEARIZABLE | k1 | 88 | `./gradlew chaos -Pruns=1 -PstartSeed=151 -Plabel=mutation-no-session-dedup -Pverbose` |
| 152 | NOT_LINEARIZABLE | k1 | 95 | `./gradlew chaos -Pruns=1 -PstartSeed=152 -Plabel=mutation-no-session-dedup -Pverbose` |
| 153 | NOT_LINEARIZABLE | k1 | 96 | `./gradlew chaos -Pruns=1 -PstartSeed=153 -Plabel=mutation-no-session-dedup -Pverbose` |
| 154 | NOT_LINEARIZABLE | k0 | 95 | `./gradlew chaos -Pruns=1 -PstartSeed=154 -Plabel=mutation-no-session-dedup -Pverbose` |
| 155 | NOT_LINEARIZABLE | k1 | 83 | `./gradlew chaos -Pruns=1 -PstartSeed=155 -Plabel=mutation-no-session-dedup -Pverbose` |
| 156 | NOT_LINEARIZABLE | k0 | 92 | `./gradlew chaos -Pruns=1 -PstartSeed=156 -Plabel=mutation-no-session-dedup -Pverbose` |
| 157 | NOT_LINEARIZABLE | k0 | 99 | `./gradlew chaos -Pruns=1 -PstartSeed=157 -Plabel=mutation-no-session-dedup -Pverbose` |
| 159 | NOT_LINEARIZABLE | k0 | 108 | `./gradlew chaos -Pruns=1 -PstartSeed=159 -Plabel=mutation-no-session-dedup -Pverbose` |
| 161 | NOT_LINEARIZABLE | k1 | 94 | `./gradlew chaos -Pruns=1 -PstartSeed=161 -Plabel=mutation-no-session-dedup -Pverbose` |
| 162 | NOT_LINEARIZABLE | k0 | 89 | `./gradlew chaos -Pruns=1 -PstartSeed=162 -Plabel=mutation-no-session-dedup -Pverbose` |
| 163 | NOT_LINEARIZABLE | k0 | 78 | `./gradlew chaos -Pruns=1 -PstartSeed=163 -Plabel=mutation-no-session-dedup -Pverbose` |
| 164 | NOT_LINEARIZABLE | k1 | 95 | `./gradlew chaos -Pruns=1 -PstartSeed=164 -Plabel=mutation-no-session-dedup -Pverbose` |
| 166 | NOT_LINEARIZABLE | k1 | 95 | `./gradlew chaos -Pruns=1 -PstartSeed=166 -Plabel=mutation-no-session-dedup -Pverbose` |
| 167 | NOT_LINEARIZABLE | k1 | 103 | `./gradlew chaos -Pruns=1 -PstartSeed=167 -Plabel=mutation-no-session-dedup -Pverbose` |
| 169 | NOT_LINEARIZABLE | k1 | 104 | `./gradlew chaos -Pruns=1 -PstartSeed=169 -Plabel=mutation-no-session-dedup -Pverbose` |
| 170 | NOT_LINEARIZABLE | k0 | 107 | `./gradlew chaos -Pruns=1 -PstartSeed=170 -Plabel=mutation-no-session-dedup -Pverbose` |
| 171 | NOT_LINEARIZABLE | k1 | 93 | `./gradlew chaos -Pruns=1 -PstartSeed=171 -Plabel=mutation-no-session-dedup -Pverbose` |
| 172 | NOT_LINEARIZABLE | k2 | 103 | `./gradlew chaos -Pruns=1 -PstartSeed=172 -Plabel=mutation-no-session-dedup -Pverbose` |
| 174 | NOT_LINEARIZABLE | k0 | 92 | `./gradlew chaos -Pruns=1 -PstartSeed=174 -Plabel=mutation-no-session-dedup -Pverbose` |
| 175 | NOT_LINEARIZABLE | k1 | 100 | `./gradlew chaos -Pruns=1 -PstartSeed=175 -Plabel=mutation-no-session-dedup -Pverbose` |
| 178 | NOT_LINEARIZABLE | k2 | 76 | `./gradlew chaos -Pruns=1 -PstartSeed=178 -Plabel=mutation-no-session-dedup -Pverbose` |
| 180 | NOT_LINEARIZABLE | k0 | 95 | `./gradlew chaos -Pruns=1 -PstartSeed=180 -Plabel=mutation-no-session-dedup -Pverbose` |
| 182 | NOT_LINEARIZABLE | k2 | 86 | `./gradlew chaos -Pruns=1 -PstartSeed=182 -Plabel=mutation-no-session-dedup -Pverbose` |
| 184 | NOT_LINEARIZABLE | k1 | 95 | `./gradlew chaos -Pruns=1 -PstartSeed=184 -Plabel=mutation-no-session-dedup -Pverbose` |
| 185 | NOT_LINEARIZABLE | k1 | 79 | `./gradlew chaos -Pruns=1 -PstartSeed=185 -Plabel=mutation-no-session-dedup -Pverbose` |
| 186 | NOT_LINEARIZABLE | k0 | 93 | `./gradlew chaos -Pruns=1 -PstartSeed=186 -Plabel=mutation-no-session-dedup -Pverbose` |
| 187 | NOT_LINEARIZABLE | k1 | 98 | `./gradlew chaos -Pruns=1 -PstartSeed=187 -Plabel=mutation-no-session-dedup -Pverbose` |
| 188 | NOT_LINEARIZABLE | k0 | 96 | `./gradlew chaos -Pruns=1 -PstartSeed=188 -Plabel=mutation-no-session-dedup -Pverbose` |
| 191 | NOT_LINEARIZABLE | k0 | 78 | `./gradlew chaos -Pruns=1 -PstartSeed=191 -Plabel=mutation-no-session-dedup -Pverbose` |
| 194 | NOT_LINEARIZABLE | k0 | 93 | `./gradlew chaos -Pruns=1 -PstartSeed=194 -Plabel=mutation-no-session-dedup -Pverbose` |
| 195 | NOT_LINEARIZABLE | k0 | 86 | `./gradlew chaos -Pruns=1 -PstartSeed=195 -Plabel=mutation-no-session-dedup -Pverbose` |
| 196 | NOT_LINEARIZABLE | k0 | 92 | `./gradlew chaos -Pruns=1 -PstartSeed=196 -Plabel=mutation-no-session-dedup -Pverbose` |
| 197 | NOT_LINEARIZABLE | k2 | 103 | `./gradlew chaos -Pruns=1 -PstartSeed=197 -Plabel=mutation-no-session-dedup -Pverbose` |
| 202 | NOT_LINEARIZABLE | k0 | 92 | `./gradlew chaos -Pruns=1 -PstartSeed=202 -Plabel=mutation-no-session-dedup -Pverbose` |
| 204 | NOT_LINEARIZABLE | k2 | 99 | `./gradlew chaos -Pruns=1 -PstartSeed=204 -Plabel=mutation-no-session-dedup -Pverbose` |
| 205 | NOT_LINEARIZABLE | k2 | 90 | `./gradlew chaos -Pruns=1 -PstartSeed=205 -Plabel=mutation-no-session-dedup -Pverbose` |
| 206 | NOT_LINEARIZABLE | k1 | 89 | `./gradlew chaos -Pruns=1 -PstartSeed=206 -Plabel=mutation-no-session-dedup -Pverbose` |
| 209 | NOT_LINEARIZABLE | k1 | 93 | `./gradlew chaos -Pruns=1 -PstartSeed=209 -Plabel=mutation-no-session-dedup -Pverbose` |
| 210 | NOT_LINEARIZABLE | k0 | 99 | `./gradlew chaos -Pruns=1 -PstartSeed=210 -Plabel=mutation-no-session-dedup -Pverbose` |
| 211 | NOT_LINEARIZABLE | k2 | 94 | `./gradlew chaos -Pruns=1 -PstartSeed=211 -Plabel=mutation-no-session-dedup -Pverbose` |
| 213 | NOT_LINEARIZABLE | k1 | 84 | `./gradlew chaos -Pruns=1 -PstartSeed=213 -Plabel=mutation-no-session-dedup -Pverbose` |
| 214 | NOT_LINEARIZABLE | k0 | 89 | `./gradlew chaos -Pruns=1 -PstartSeed=214 -Plabel=mutation-no-session-dedup -Pverbose` |
| 215 | NOT_LINEARIZABLE | k1 | 110 | `./gradlew chaos -Pruns=1 -PstartSeed=215 -Plabel=mutation-no-session-dedup -Pverbose` |
| 217 | NOT_LINEARIZABLE | k0 | 104 | `./gradlew chaos -Pruns=1 -PstartSeed=217 -Plabel=mutation-no-session-dedup -Pverbose` |
| 218 | NOT_LINEARIZABLE | k1 | 100 | `./gradlew chaos -Pruns=1 -PstartSeed=218 -Plabel=mutation-no-session-dedup -Pverbose` |
| 220 | NOT_LINEARIZABLE | k1 | 82 | `./gradlew chaos -Pruns=1 -PstartSeed=220 -Plabel=mutation-no-session-dedup -Pverbose` |
| 221 | NOT_LINEARIZABLE | k2 | 107 | `./gradlew chaos -Pruns=1 -PstartSeed=221 -Plabel=mutation-no-session-dedup -Pverbose` |
| 223 | NOT_LINEARIZABLE | k2 | 96 | `./gradlew chaos -Pruns=1 -PstartSeed=223 -Plabel=mutation-no-session-dedup -Pverbose` |
| 224 | NOT_LINEARIZABLE | k1 | 91 | `./gradlew chaos -Pruns=1 -PstartSeed=224 -Plabel=mutation-no-session-dedup -Pverbose` |
| 226 | NOT_LINEARIZABLE | k2 | 93 | `./gradlew chaos -Pruns=1 -PstartSeed=226 -Plabel=mutation-no-session-dedup -Pverbose` |
| 227 | NOT_LINEARIZABLE | k0 | 101 | `./gradlew chaos -Pruns=1 -PstartSeed=227 -Plabel=mutation-no-session-dedup -Pverbose` |
| 229 | NOT_LINEARIZABLE | k1 | 101 | `./gradlew chaos -Pruns=1 -PstartSeed=229 -Plabel=mutation-no-session-dedup -Pverbose` |
| 233 | NOT_LINEARIZABLE | k0 | 97 | `./gradlew chaos -Pruns=1 -PstartSeed=233 -Plabel=mutation-no-session-dedup -Pverbose` |
| 234 | NOT_LINEARIZABLE | k0 | 90 | `./gradlew chaos -Pruns=1 -PstartSeed=234 -Plabel=mutation-no-session-dedup -Pverbose` |
| 235 | NOT_LINEARIZABLE | k2 | 102 | `./gradlew chaos -Pruns=1 -PstartSeed=235 -Plabel=mutation-no-session-dedup -Pverbose` |
| 236 | NOT_LINEARIZABLE | k0 | 86 | `./gradlew chaos -Pruns=1 -PstartSeed=236 -Plabel=mutation-no-session-dedup -Pverbose` |
| 237 | NOT_LINEARIZABLE | k1 | 84 | `./gradlew chaos -Pruns=1 -PstartSeed=237 -Plabel=mutation-no-session-dedup -Pverbose` |
| 238 | NOT_LINEARIZABLE | k1 | 93 | `./gradlew chaos -Pruns=1 -PstartSeed=238 -Plabel=mutation-no-session-dedup -Pverbose` |
| 240 | NOT_LINEARIZABLE | k1 | 99 | `./gradlew chaos -Pruns=1 -PstartSeed=240 -Plabel=mutation-no-session-dedup -Pverbose` |
| 241 | NOT_LINEARIZABLE | k1 | 87 | `./gradlew chaos -Pruns=1 -PstartSeed=241 -Plabel=mutation-no-session-dedup -Pverbose` |
| 242 | NOT_LINEARIZABLE | k2 | 88 | `./gradlew chaos -Pruns=1 -PstartSeed=242 -Plabel=mutation-no-session-dedup -Pverbose` |
| 243 | NOT_LINEARIZABLE | k1 | 93 | `./gradlew chaos -Pruns=1 -PstartSeed=243 -Plabel=mutation-no-session-dedup -Pverbose` |
| 245 | NOT_LINEARIZABLE | k0 | 103 | `./gradlew chaos -Pruns=1 -PstartSeed=245 -Plabel=mutation-no-session-dedup -Pverbose` |
| 246 | NOT_LINEARIZABLE | k2 | 87 | `./gradlew chaos -Pruns=1 -PstartSeed=246 -Plabel=mutation-no-session-dedup -Pverbose` |
| 248 | NOT_LINEARIZABLE | k2 | 94 | `./gradlew chaos -Pruns=1 -PstartSeed=248 -Plabel=mutation-no-session-dedup -Pverbose` |
| 249 | NOT_LINEARIZABLE | k0 | 96 | `./gradlew chaos -Pruns=1 -PstartSeed=249 -Plabel=mutation-no-session-dedup -Pverbose` |
| 254 | NOT_LINEARIZABLE | k1 | 105 | `./gradlew chaos -Pruns=1 -PstartSeed=254 -Plabel=mutation-no-session-dedup -Pverbose` |
| 257 | NOT_LINEARIZABLE | k0 | 90 | `./gradlew chaos -Pruns=1 -PstartSeed=257 -Plabel=mutation-no-session-dedup -Pverbose` |
| 258 | NOT_LINEARIZABLE | k1 | 103 | `./gradlew chaos -Pruns=1 -PstartSeed=258 -Plabel=mutation-no-session-dedup -Pverbose` |
| 259 | NOT_LINEARIZABLE | k2 | 110 | `./gradlew chaos -Pruns=1 -PstartSeed=259 -Plabel=mutation-no-session-dedup -Pverbose` |
| 262 | NOT_LINEARIZABLE | k1 | 97 | `./gradlew chaos -Pruns=1 -PstartSeed=262 -Plabel=mutation-no-session-dedup -Pverbose` |
| 264 | NOT_LINEARIZABLE | k0 | 93 | `./gradlew chaos -Pruns=1 -PstartSeed=264 -Plabel=mutation-no-session-dedup -Pverbose` |
| 267 | NOT_LINEARIZABLE | k1 | 92 | `./gradlew chaos -Pruns=1 -PstartSeed=267 -Plabel=mutation-no-session-dedup -Pverbose` |
| 268 | NOT_LINEARIZABLE | k0 | 78 | `./gradlew chaos -Pruns=1 -PstartSeed=268 -Plabel=mutation-no-session-dedup -Pverbose` |
| 271 | NOT_LINEARIZABLE | k1 | 102 | `./gradlew chaos -Pruns=1 -PstartSeed=271 -Plabel=mutation-no-session-dedup -Pverbose` |
| 274 | NOT_LINEARIZABLE | k1 | 96 | `./gradlew chaos -Pruns=1 -PstartSeed=274 -Plabel=mutation-no-session-dedup -Pverbose` |
| 275 | NOT_LINEARIZABLE | k0 | 84 | `./gradlew chaos -Pruns=1 -PstartSeed=275 -Plabel=mutation-no-session-dedup -Pverbose` |
| 276 | NOT_LINEARIZABLE | k1 | 88 | `./gradlew chaos -Pruns=1 -PstartSeed=276 -Plabel=mutation-no-session-dedup -Pverbose` |
| 278 | NOT_LINEARIZABLE | k1 | 103 | `./gradlew chaos -Pruns=1 -PstartSeed=278 -Plabel=mutation-no-session-dedup -Pverbose` |
| 280 | NOT_LINEARIZABLE | k2 | 87 | `./gradlew chaos -Pruns=1 -PstartSeed=280 -Plabel=mutation-no-session-dedup -Pverbose` |
| 282 | NOT_LINEARIZABLE | k0 | 82 | `./gradlew chaos -Pruns=1 -PstartSeed=282 -Plabel=mutation-no-session-dedup -Pverbose` |
| 283 | NOT_LINEARIZABLE | k0 | 87 | `./gradlew chaos -Pruns=1 -PstartSeed=283 -Plabel=mutation-no-session-dedup -Pverbose` |
| 284 | NOT_LINEARIZABLE | k1 | 87 | `./gradlew chaos -Pruns=1 -PstartSeed=284 -Plabel=mutation-no-session-dedup -Pverbose` |
| 286 | NOT_LINEARIZABLE | k0 | 88 | `./gradlew chaos -Pruns=1 -PstartSeed=286 -Plabel=mutation-no-session-dedup -Pverbose` |
| 287 | NOT_LINEARIZABLE | k1 | 89 | `./gradlew chaos -Pruns=1 -PstartSeed=287 -Plabel=mutation-no-session-dedup -Pverbose` |
| 288 | NOT_LINEARIZABLE | k0 | 86 | `./gradlew chaos -Pruns=1 -PstartSeed=288 -Plabel=mutation-no-session-dedup -Pverbose` |
| 289 | NOT_LINEARIZABLE | k1 | 95 | `./gradlew chaos -Pruns=1 -PstartSeed=289 -Plabel=mutation-no-session-dedup -Pverbose` |
| 292 | NOT_LINEARIZABLE | k1 | 88 | `./gradlew chaos -Pruns=1 -PstartSeed=292 -Plabel=mutation-no-session-dedup -Pverbose` |
| 294 | NOT_LINEARIZABLE | k1 | 90 | `./gradlew chaos -Pruns=1 -PstartSeed=294 -Plabel=mutation-no-session-dedup -Pverbose` |
| 295 | NOT_LINEARIZABLE | k0 | 95 | `./gradlew chaos -Pruns=1 -PstartSeed=295 -Plabel=mutation-no-session-dedup -Pverbose` |
| 300 | NOT_LINEARIZABLE | k2 | 102 | `./gradlew chaos -Pruns=1 -PstartSeed=300 -Plabel=mutation-no-session-dedup -Pverbose` |
| 302 | NOT_LINEARIZABLE | k2 | 101 | `./gradlew chaos -Pruns=1 -PstartSeed=302 -Plabel=mutation-no-session-dedup -Pverbose` |
| 306 | NOT_LINEARIZABLE | k1 | 89 | `./gradlew chaos -Pruns=1 -PstartSeed=306 -Plabel=mutation-no-session-dedup -Pverbose` |
| 307 | NOT_LINEARIZABLE | k0 | 102 | `./gradlew chaos -Pruns=1 -PstartSeed=307 -Plabel=mutation-no-session-dedup -Pverbose` |
| 311 | NOT_LINEARIZABLE | k0 | 96 | `./gradlew chaos -Pruns=1 -PstartSeed=311 -Plabel=mutation-no-session-dedup -Pverbose` |
| 313 | NOT_LINEARIZABLE | k0 | 101 | `./gradlew chaos -Pruns=1 -PstartSeed=313 -Plabel=mutation-no-session-dedup -Pverbose` |
| 314 | NOT_LINEARIZABLE | k0 | 98 | `./gradlew chaos -Pruns=1 -PstartSeed=314 -Plabel=mutation-no-session-dedup -Pverbose` |
| 315 | NOT_LINEARIZABLE | k0 | 100 | `./gradlew chaos -Pruns=1 -PstartSeed=315 -Plabel=mutation-no-session-dedup -Pverbose` |
| 316 | NOT_LINEARIZABLE | k0 | 90 | `./gradlew chaos -Pruns=1 -PstartSeed=316 -Plabel=mutation-no-session-dedup -Pverbose` |
| 317 | NOT_LINEARIZABLE | k1 | 94 | `./gradlew chaos -Pruns=1 -PstartSeed=317 -Plabel=mutation-no-session-dedup -Pverbose` |
| 319 | NOT_LINEARIZABLE | k2 | 99 | `./gradlew chaos -Pruns=1 -PstartSeed=319 -Plabel=mutation-no-session-dedup -Pverbose` |
| 320 | NOT_LINEARIZABLE | k1 | 102 | `./gradlew chaos -Pruns=1 -PstartSeed=320 -Plabel=mutation-no-session-dedup -Pverbose` |
| 321 | NOT_LINEARIZABLE | k0 | 101 | `./gradlew chaos -Pruns=1 -PstartSeed=321 -Plabel=mutation-no-session-dedup -Pverbose` |
| 322 | NOT_LINEARIZABLE | k0 | 101 | `./gradlew chaos -Pruns=1 -PstartSeed=322 -Plabel=mutation-no-session-dedup -Pverbose` |
| 326 | NOT_LINEARIZABLE | k0 | 102 | `./gradlew chaos -Pruns=1 -PstartSeed=326 -Plabel=mutation-no-session-dedup -Pverbose` |
| 327 | NOT_LINEARIZABLE | k2 | 86 | `./gradlew chaos -Pruns=1 -PstartSeed=327 -Plabel=mutation-no-session-dedup -Pverbose` |
| 329 | NOT_LINEARIZABLE | k2 | 101 | `./gradlew chaos -Pruns=1 -PstartSeed=329 -Plabel=mutation-no-session-dedup -Pverbose` |
| 330 | NOT_LINEARIZABLE | k0 | 87 | `./gradlew chaos -Pruns=1 -PstartSeed=330 -Plabel=mutation-no-session-dedup -Pverbose` |
| 332 | NOT_LINEARIZABLE | k0 | 93 | `./gradlew chaos -Pruns=1 -PstartSeed=332 -Plabel=mutation-no-session-dedup -Pverbose` |
| 333 | NOT_LINEARIZABLE | k1 | 86 | `./gradlew chaos -Pruns=1 -PstartSeed=333 -Plabel=mutation-no-session-dedup -Pverbose` |
| 340 | NOT_LINEARIZABLE | k0 | 88 | `./gradlew chaos -Pruns=1 -PstartSeed=340 -Plabel=mutation-no-session-dedup -Pverbose` |
| 341 | NOT_LINEARIZABLE | k0 | 92 | `./gradlew chaos -Pruns=1 -PstartSeed=341 -Plabel=mutation-no-session-dedup -Pverbose` |
| 342 | NOT_LINEARIZABLE | k0 | 89 | `./gradlew chaos -Pruns=1 -PstartSeed=342 -Plabel=mutation-no-session-dedup -Pverbose` |
| 344 | NOT_LINEARIZABLE | k0 | 96 | `./gradlew chaos -Pruns=1 -PstartSeed=344 -Plabel=mutation-no-session-dedup -Pverbose` |
| 345 | NOT_LINEARIZABLE | k2 | 99 | `./gradlew chaos -Pruns=1 -PstartSeed=345 -Plabel=mutation-no-session-dedup -Pverbose` |
| 346 | NOT_LINEARIZABLE | k1 | 89 | `./gradlew chaos -Pruns=1 -PstartSeed=346 -Plabel=mutation-no-session-dedup -Pverbose` |
| 349 | NOT_LINEARIZABLE | k1 | 90 | `./gradlew chaos -Pruns=1 -PstartSeed=349 -Plabel=mutation-no-session-dedup -Pverbose` |
| 350 | NOT_LINEARIZABLE | k0 | 102 | `./gradlew chaos -Pruns=1 -PstartSeed=350 -Plabel=mutation-no-session-dedup -Pverbose` |
| 351 | NOT_LINEARIZABLE | k0 | 90 | `./gradlew chaos -Pruns=1 -PstartSeed=351 -Plabel=mutation-no-session-dedup -Pverbose` |
| 352 | NOT_LINEARIZABLE | k1 | 106 | `./gradlew chaos -Pruns=1 -PstartSeed=352 -Plabel=mutation-no-session-dedup -Pverbose` |
| 353 | NOT_LINEARIZABLE | k2 | 100 | `./gradlew chaos -Pruns=1 -PstartSeed=353 -Plabel=mutation-no-session-dedup -Pverbose` |
| 356 | NOT_LINEARIZABLE | k0 | 93 | `./gradlew chaos -Pruns=1 -PstartSeed=356 -Plabel=mutation-no-session-dedup -Pverbose` |
| 357 | NOT_LINEARIZABLE | k1 | 97 | `./gradlew chaos -Pruns=1 -PstartSeed=357 -Plabel=mutation-no-session-dedup -Pverbose` |
| 358 | NOT_LINEARIZABLE | k1 | 111 | `./gradlew chaos -Pruns=1 -PstartSeed=358 -Plabel=mutation-no-session-dedup -Pverbose` |
| 359 | NOT_LINEARIZABLE | k1 | 102 | `./gradlew chaos -Pruns=1 -PstartSeed=359 -Plabel=mutation-no-session-dedup -Pverbose` |
| 363 | NOT_LINEARIZABLE | k0 | 91 | `./gradlew chaos -Pruns=1 -PstartSeed=363 -Plabel=mutation-no-session-dedup -Pverbose` |
| 364 | NOT_LINEARIZABLE | k0 | 99 | `./gradlew chaos -Pruns=1 -PstartSeed=364 -Plabel=mutation-no-session-dedup -Pverbose` |
| 365 | NOT_LINEARIZABLE | k2 | 78 | `./gradlew chaos -Pruns=1 -PstartSeed=365 -Plabel=mutation-no-session-dedup -Pverbose` |
| 366 | NOT_LINEARIZABLE | k2 | 85 | `./gradlew chaos -Pruns=1 -PstartSeed=366 -Plabel=mutation-no-session-dedup -Pverbose` |
| 368 | NOT_LINEARIZABLE | k2 | 95 | `./gradlew chaos -Pruns=1 -PstartSeed=368 -Plabel=mutation-no-session-dedup -Pverbose` |
| 369 | NOT_LINEARIZABLE | k2 | 104 | `./gradlew chaos -Pruns=1 -PstartSeed=369 -Plabel=mutation-no-session-dedup -Pverbose` |
| 370 | NOT_LINEARIZABLE | k1 | 89 | `./gradlew chaos -Pruns=1 -PstartSeed=370 -Plabel=mutation-no-session-dedup -Pverbose` |
| 371 | NOT_LINEARIZABLE | k2 | 101 | `./gradlew chaos -Pruns=1 -PstartSeed=371 -Plabel=mutation-no-session-dedup -Pverbose` |
| 372 | NOT_LINEARIZABLE | k1 | 100 | `./gradlew chaos -Pruns=1 -PstartSeed=372 -Plabel=mutation-no-session-dedup -Pverbose` |
| 373 | NOT_LINEARIZABLE | k1 | 97 | `./gradlew chaos -Pruns=1 -PstartSeed=373 -Plabel=mutation-no-session-dedup -Pverbose` |
| 379 | NOT_LINEARIZABLE | k0 | 100 | `./gradlew chaos -Pruns=1 -PstartSeed=379 -Plabel=mutation-no-session-dedup -Pverbose` |
| 380 | NOT_LINEARIZABLE | k0 | 78 | `./gradlew chaos -Pruns=1 -PstartSeed=380 -Plabel=mutation-no-session-dedup -Pverbose` |
| 381 | NOT_LINEARIZABLE | k0 | 92 | `./gradlew chaos -Pruns=1 -PstartSeed=381 -Plabel=mutation-no-session-dedup -Pverbose` |
| 384 | NOT_LINEARIZABLE | k0 | 92 | `./gradlew chaos -Pruns=1 -PstartSeed=384 -Plabel=mutation-no-session-dedup -Pverbose` |
| 385 | NOT_LINEARIZABLE | k2 | 95 | `./gradlew chaos -Pruns=1 -PstartSeed=385 -Plabel=mutation-no-session-dedup -Pverbose` |
| 386 | NOT_LINEARIZABLE | k0 | 99 | `./gradlew chaos -Pruns=1 -PstartSeed=386 -Plabel=mutation-no-session-dedup -Pverbose` |
| 390 | NOT_LINEARIZABLE | k2 | 101 | `./gradlew chaos -Pruns=1 -PstartSeed=390 -Plabel=mutation-no-session-dedup -Pverbose` |
| 392 | NOT_LINEARIZABLE | k0 | 86 | `./gradlew chaos -Pruns=1 -PstartSeed=392 -Plabel=mutation-no-session-dedup -Pverbose` |
| 394 | NOT_LINEARIZABLE | k1 | 113 | `./gradlew chaos -Pruns=1 -PstartSeed=394 -Plabel=mutation-no-session-dedup -Pverbose` |
| 395 | NOT_LINEARIZABLE | k0 | 80 | `./gradlew chaos -Pruns=1 -PstartSeed=395 -Plabel=mutation-no-session-dedup -Pverbose` |
| 396 | NOT_LINEARIZABLE | k0 | 94 | `./gradlew chaos -Pruns=1 -PstartSeed=396 -Plabel=mutation-no-session-dedup -Pverbose` |
| 397 | NOT_LINEARIZABLE | k0 | 100 | `./gradlew chaos -Pruns=1 -PstartSeed=397 -Plabel=mutation-no-session-dedup -Pverbose` |
| 403 | NOT_LINEARIZABLE | k0 | 92 | `./gradlew chaos -Pruns=1 -PstartSeed=403 -Plabel=mutation-no-session-dedup -Pverbose` |
| 404 | NOT_LINEARIZABLE | k0 | 104 | `./gradlew chaos -Pruns=1 -PstartSeed=404 -Plabel=mutation-no-session-dedup -Pverbose` |
| 405 | NOT_LINEARIZABLE | k1 | 106 | `./gradlew chaos -Pruns=1 -PstartSeed=405 -Plabel=mutation-no-session-dedup -Pverbose` |
| 406 | NOT_LINEARIZABLE | k2 | 100 | `./gradlew chaos -Pruns=1 -PstartSeed=406 -Plabel=mutation-no-session-dedup -Pverbose` |
| 411 | NOT_LINEARIZABLE | k0 | 101 | `./gradlew chaos -Pruns=1 -PstartSeed=411 -Plabel=mutation-no-session-dedup -Pverbose` |
| 412 | NOT_LINEARIZABLE | k0 | 100 | `./gradlew chaos -Pruns=1 -PstartSeed=412 -Plabel=mutation-no-session-dedup -Pverbose` |
| 413 | NOT_LINEARIZABLE | k2 | 100 | `./gradlew chaos -Pruns=1 -PstartSeed=413 -Plabel=mutation-no-session-dedup -Pverbose` |
| 414 | NOT_LINEARIZABLE | k0 | 100 | `./gradlew chaos -Pruns=1 -PstartSeed=414 -Plabel=mutation-no-session-dedup -Pverbose` |
| 415 | NOT_LINEARIZABLE | k1 | 87 | `./gradlew chaos -Pruns=1 -PstartSeed=415 -Plabel=mutation-no-session-dedup -Pverbose` |
| 417 | NOT_LINEARIZABLE | k1 | 77 | `./gradlew chaos -Pruns=1 -PstartSeed=417 -Plabel=mutation-no-session-dedup -Pverbose` |
| 418 | NOT_LINEARIZABLE | k1 | 88 | `./gradlew chaos -Pruns=1 -PstartSeed=418 -Plabel=mutation-no-session-dedup -Pverbose` |
| 419 | NOT_LINEARIZABLE | k2 | 98 | `./gradlew chaos -Pruns=1 -PstartSeed=419 -Plabel=mutation-no-session-dedup -Pverbose` |
| 420 | NOT_LINEARIZABLE | k1 | 88 | `./gradlew chaos -Pruns=1 -PstartSeed=420 -Plabel=mutation-no-session-dedup -Pverbose` |
| 421 | NOT_LINEARIZABLE | k0 | 100 | `./gradlew chaos -Pruns=1 -PstartSeed=421 -Plabel=mutation-no-session-dedup -Pverbose` |
| 423 | NOT_LINEARIZABLE | k0 | 92 | `./gradlew chaos -Pruns=1 -PstartSeed=423 -Plabel=mutation-no-session-dedup -Pverbose` |
| 424 | NOT_LINEARIZABLE | k1 | 108 | `./gradlew chaos -Pruns=1 -PstartSeed=424 -Plabel=mutation-no-session-dedup -Pverbose` |
| 426 | NOT_LINEARIZABLE | k2 | 110 | `./gradlew chaos -Pruns=1 -PstartSeed=426 -Plabel=mutation-no-session-dedup -Pverbose` |
| 427 | NOT_LINEARIZABLE | k0 | 89 | `./gradlew chaos -Pruns=1 -PstartSeed=427 -Plabel=mutation-no-session-dedup -Pverbose` |
| 428 | NOT_LINEARIZABLE | k0 | 100 | `./gradlew chaos -Pruns=1 -PstartSeed=428 -Plabel=mutation-no-session-dedup -Pverbose` |
| 430 | NOT_LINEARIZABLE | k1 | 105 | `./gradlew chaos -Pruns=1 -PstartSeed=430 -Plabel=mutation-no-session-dedup -Pverbose` |
| 431 | NOT_LINEARIZABLE | k0 | 94 | `./gradlew chaos -Pruns=1 -PstartSeed=431 -Plabel=mutation-no-session-dedup -Pverbose` |
| 434 | NOT_LINEARIZABLE | k1 | 104 | `./gradlew chaos -Pruns=1 -PstartSeed=434 -Plabel=mutation-no-session-dedup -Pverbose` |
| 435 | NOT_LINEARIZABLE | k1 | 103 | `./gradlew chaos -Pruns=1 -PstartSeed=435 -Plabel=mutation-no-session-dedup -Pverbose` |
| 436 | NOT_LINEARIZABLE | k0 | 98 | `./gradlew chaos -Pruns=1 -PstartSeed=436 -Plabel=mutation-no-session-dedup -Pverbose` |
| 437 | NOT_LINEARIZABLE | k0 | 99 | `./gradlew chaos -Pruns=1 -PstartSeed=437 -Plabel=mutation-no-session-dedup -Pverbose` |
| 442 | NOT_LINEARIZABLE | k1 | 85 | `./gradlew chaos -Pruns=1 -PstartSeed=442 -Plabel=mutation-no-session-dedup -Pverbose` |
| 443 | NOT_LINEARIZABLE | k0 | 80 | `./gradlew chaos -Pruns=1 -PstartSeed=443 -Plabel=mutation-no-session-dedup -Pverbose` |
| 445 | NOT_LINEARIZABLE | k0 | 93 | `./gradlew chaos -Pruns=1 -PstartSeed=445 -Plabel=mutation-no-session-dedup -Pverbose` |
| 448 | NOT_LINEARIZABLE | k1 | 96 | `./gradlew chaos -Pruns=1 -PstartSeed=448 -Plabel=mutation-no-session-dedup -Pverbose` |
| 452 | NOT_LINEARIZABLE | k0 | 84 | `./gradlew chaos -Pruns=1 -PstartSeed=452 -Plabel=mutation-no-session-dedup -Pverbose` |
| 453 | NOT_LINEARIZABLE | k0 | 84 | `./gradlew chaos -Pruns=1 -PstartSeed=453 -Plabel=mutation-no-session-dedup -Pverbose` |
| 455 | NOT_LINEARIZABLE | k2 | 85 | `./gradlew chaos -Pruns=1 -PstartSeed=455 -Plabel=mutation-no-session-dedup -Pverbose` |
| 457 | NOT_LINEARIZABLE | k2 | 103 | `./gradlew chaos -Pruns=1 -PstartSeed=457 -Plabel=mutation-no-session-dedup -Pverbose` |
| 460 | NOT_LINEARIZABLE | k0 | 107 | `./gradlew chaos -Pruns=1 -PstartSeed=460 -Plabel=mutation-no-session-dedup -Pverbose` |
| 463 | NOT_LINEARIZABLE | k0 | 92 | `./gradlew chaos -Pruns=1 -PstartSeed=463 -Plabel=mutation-no-session-dedup -Pverbose` |
| 464 | NOT_LINEARIZABLE | k0 | 99 | `./gradlew chaos -Pruns=1 -PstartSeed=464 -Plabel=mutation-no-session-dedup -Pverbose` |
| 465 | NOT_LINEARIZABLE | k0 | 86 | `./gradlew chaos -Pruns=1 -PstartSeed=465 -Plabel=mutation-no-session-dedup -Pverbose` |
| 469 | NOT_LINEARIZABLE | k1 | 97 | `./gradlew chaos -Pruns=1 -PstartSeed=469 -Plabel=mutation-no-session-dedup -Pverbose` |
| 470 | NOT_LINEARIZABLE | k1 | 104 | `./gradlew chaos -Pruns=1 -PstartSeed=470 -Plabel=mutation-no-session-dedup -Pverbose` |
| 472 | NOT_LINEARIZABLE | k0 | 86 | `./gradlew chaos -Pruns=1 -PstartSeed=472 -Plabel=mutation-no-session-dedup -Pverbose` |
| 473 | NOT_LINEARIZABLE | k1 | 100 | `./gradlew chaos -Pruns=1 -PstartSeed=473 -Plabel=mutation-no-session-dedup -Pverbose` |
| 474 | NOT_LINEARIZABLE | k0 | 95 | `./gradlew chaos -Pruns=1 -PstartSeed=474 -Plabel=mutation-no-session-dedup -Pverbose` |
| 476 | NOT_LINEARIZABLE | k2 | 90 | `./gradlew chaos -Pruns=1 -PstartSeed=476 -Plabel=mutation-no-session-dedup -Pverbose` |
| 477 | NOT_LINEARIZABLE | k2 | 105 | `./gradlew chaos -Pruns=1 -PstartSeed=477 -Plabel=mutation-no-session-dedup -Pverbose` |
| 478 | NOT_LINEARIZABLE | k1 | 105 | `./gradlew chaos -Pruns=1 -PstartSeed=478 -Plabel=mutation-no-session-dedup -Pverbose` |
| 480 | NOT_LINEARIZABLE | k1 | 98 | `./gradlew chaos -Pruns=1 -PstartSeed=480 -Plabel=mutation-no-session-dedup -Pverbose` |
| 481 | NOT_LINEARIZABLE | k1 | 94 | `./gradlew chaos -Pruns=1 -PstartSeed=481 -Plabel=mutation-no-session-dedup -Pverbose` |
| 487 | NOT_LINEARIZABLE | k0 | 103 | `./gradlew chaos -Pruns=1 -PstartSeed=487 -Plabel=mutation-no-session-dedup -Pverbose` |
| 488 | NOT_LINEARIZABLE | k1 | 92 | `./gradlew chaos -Pruns=1 -PstartSeed=488 -Plabel=mutation-no-session-dedup -Pverbose` |
| 490 | NOT_LINEARIZABLE | k0 | 82 | `./gradlew chaos -Pruns=1 -PstartSeed=490 -Plabel=mutation-no-session-dedup -Pverbose` |
| 492 | NOT_LINEARIZABLE | k0 | 91 | `./gradlew chaos -Pruns=1 -PstartSeed=492 -Plabel=mutation-no-session-dedup -Pverbose` |
| 493 | NOT_LINEARIZABLE | k1 | 78 | `./gradlew chaos -Pruns=1 -PstartSeed=493 -Plabel=mutation-no-session-dedup -Pverbose` |
| 495 | NOT_LINEARIZABLE | k1 | 93 | `./gradlew chaos -Pruns=1 -PstartSeed=495 -Plabel=mutation-no-session-dedup -Pverbose` |
| 496 | NOT_LINEARIZABLE | k0 | 95 | `./gradlew chaos -Pruns=1 -PstartSeed=496 -Plabel=mutation-no-session-dedup -Pverbose` |
| 498 | NOT_LINEARIZABLE | k0 | 83 | `./gradlew chaos -Pruns=1 -PstartSeed=498 -Plabel=mutation-no-session-dedup -Pverbose` |

Per-seed numbers: `chaos-mutation-no-session-dedup-500-seeds.csv`.

## Mutation applied

This run used a deliberately broken build (`scripts/chaos-mutation.sh no-session-dedup 500`).
Change to `kv/src/main/java/io/github/hard3007/raftkv/kv/KvStateMachine.java`:

```diff
@@ -159,16 +159,6 @@
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
