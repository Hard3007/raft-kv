# Chaos report: mutation-no-session-dedup

- **Command:** `./gradlew chaos -Pruns=500 -Plabel=mutation-no-session-dedup`
- **Date:** 2026-10-20T09:51:00.124143892Z
- **Git commit:** 61dda62 (with uncommitted changes)
- **Machine:** Linux 6.8.0-1064-azure amd64, 2 cores (AMD EPYC 7763 64-Core Processor), Java 21, max heap 1024 MB
- **Config:** 5 nodes, 5 clients, 3 keys, 60 ops per client, think time 0-300 ms, a fault action every 100-400 ms (virtual), client attempt timeout 1000 ms / give-up 4000 ms, checker timeout 10 s (wall); PreVote on, CheckQuorum on, a snapshot every 20 entries in 64-byte InstallSnapshot chunks
- **Seeds:** 1..500 (500 runs)
- **Total wall time:** 26.4 s

## Verdicts

| Verdict | Runs |
|---|---|
| LINEARIZABLE | 263 |
| NOT_LINEARIZABLE | 236 |
| INCONCLUSIVE | 1 |
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
| node pauses (frozen 0.5-2 s) | 7 | 28.9 | 52 |
| partitions | 16 | 66.9 | 110 |
| message-loss periods | 9 | 21.9 | 42 |
| messages lost | 140 | 472.5 | 918 |
| long-delay periods | 9 | 21.7 | 38 |
| snapshots taken (all nodes) | 22 | 29.4 | 39 |
| InstallSnapshots completed (all nodes) | 4 | 11.5 | 19 |
| final term (highest across nodes) | 16 | 66.6 | 109 |
| virtual time (s) | 38.2 | 98.4 | 152.6 |
| wall time (ms) | 10 | 103.5 | 10035 |

## Runs that did not pass

| Seed | Verdict | Key / failure | Ops on key | Reproduce |
|---|---|---|---|---|
| 1 | NOT_LINEARIZABLE | k2 | 100 | `./gradlew chaos -Pruns=1 -PstartSeed=1 -Plabel=mutation-no-session-dedup -Pverbose` |
| 5 | NOT_LINEARIZABLE | k1 | 90 | `./gradlew chaos -Pruns=1 -PstartSeed=5 -Plabel=mutation-no-session-dedup -Pverbose` |
| 6 | NOT_LINEARIZABLE | k2 | 92 | `./gradlew chaos -Pruns=1 -PstartSeed=6 -Plabel=mutation-no-session-dedup -Pverbose` |
| 7 | NOT_LINEARIZABLE | k1 | 83 | `./gradlew chaos -Pruns=1 -PstartSeed=7 -Plabel=mutation-no-session-dedup -Pverbose` |
| 8 | NOT_LINEARIZABLE | k1 | 80 | `./gradlew chaos -Pruns=1 -PstartSeed=8 -Plabel=mutation-no-session-dedup -Pverbose` |
| 10 | NOT_LINEARIZABLE | k1 | 99 | `./gradlew chaos -Pruns=1 -PstartSeed=10 -Plabel=mutation-no-session-dedup -Pverbose` |
| 11 | NOT_LINEARIZABLE | k2 | 88 | `./gradlew chaos -Pruns=1 -PstartSeed=11 -Plabel=mutation-no-session-dedup -Pverbose` |
| 13 | NOT_LINEARIZABLE | k2 | 72 | `./gradlew chaos -Pruns=1 -PstartSeed=13 -Plabel=mutation-no-session-dedup -Pverbose` |
| 14 | NOT_LINEARIZABLE | k2 | 94 | `./gradlew chaos -Pruns=1 -PstartSeed=14 -Plabel=mutation-no-session-dedup -Pverbose` |
| 15 | NOT_LINEARIZABLE | k1 | 108 | `./gradlew chaos -Pruns=1 -PstartSeed=15 -Plabel=mutation-no-session-dedup -Pverbose` |
| 16 | NOT_LINEARIZABLE | k1 | 82 | `./gradlew chaos -Pruns=1 -PstartSeed=16 -Plabel=mutation-no-session-dedup -Pverbose` |
| 17 | NOT_LINEARIZABLE | k1 | 86 | `./gradlew chaos -Pruns=1 -PstartSeed=17 -Plabel=mutation-no-session-dedup -Pverbose` |
| 20 | NOT_LINEARIZABLE | k2 | 94 | `./gradlew chaos -Pruns=1 -PstartSeed=20 -Plabel=mutation-no-session-dedup -Pverbose` |
| 22 | NOT_LINEARIZABLE | k2 | 91 | `./gradlew chaos -Pruns=1 -PstartSeed=22 -Plabel=mutation-no-session-dedup -Pverbose` |
| 23 | NOT_LINEARIZABLE | k0 | 70 | `./gradlew chaos -Pruns=1 -PstartSeed=23 -Plabel=mutation-no-session-dedup -Pverbose` |
| 25 | NOT_LINEARIZABLE | k1 | 98 | `./gradlew chaos -Pruns=1 -PstartSeed=25 -Plabel=mutation-no-session-dedup -Pverbose` |
| 26 | NOT_LINEARIZABLE | k0 | 93 | `./gradlew chaos -Pruns=1 -PstartSeed=26 -Plabel=mutation-no-session-dedup -Pverbose` |
| 28 | NOT_LINEARIZABLE | k1 | 91 | `./gradlew chaos -Pruns=1 -PstartSeed=28 -Plabel=mutation-no-session-dedup -Pverbose` |
| 29 | NOT_LINEARIZABLE | k0 | 98 | `./gradlew chaos -Pruns=1 -PstartSeed=29 -Plabel=mutation-no-session-dedup -Pverbose` |
| 30 | NOT_LINEARIZABLE | k0 | 87 | `./gradlew chaos -Pruns=1 -PstartSeed=30 -Plabel=mutation-no-session-dedup -Pverbose` |
| 31 | NOT_LINEARIZABLE | k2 | 84 | `./gradlew chaos -Pruns=1 -PstartSeed=31 -Plabel=mutation-no-session-dedup -Pverbose` |
| 33 | NOT_LINEARIZABLE | k0 | 86 | `./gradlew chaos -Pruns=1 -PstartSeed=33 -Plabel=mutation-no-session-dedup -Pverbose` |
| 35 | NOT_LINEARIZABLE | k1 | 95 | `./gradlew chaos -Pruns=1 -PstartSeed=35 -Plabel=mutation-no-session-dedup -Pverbose` |
| 38 | NOT_LINEARIZABLE | k0 | 102 | `./gradlew chaos -Pruns=1 -PstartSeed=38 -Plabel=mutation-no-session-dedup -Pverbose` |
| 42 | NOT_LINEARIZABLE | k0 | 91 | `./gradlew chaos -Pruns=1 -PstartSeed=42 -Plabel=mutation-no-session-dedup -Pverbose` |
| 43 | NOT_LINEARIZABLE | k1 | 87 | `./gradlew chaos -Pruns=1 -PstartSeed=43 -Plabel=mutation-no-session-dedup -Pverbose` |
| 44 | NOT_LINEARIZABLE | k1 | 84 | `./gradlew chaos -Pruns=1 -PstartSeed=44 -Plabel=mutation-no-session-dedup -Pverbose` |
| 45 | NOT_LINEARIZABLE | k1 | 101 | `./gradlew chaos -Pruns=1 -PstartSeed=45 -Plabel=mutation-no-session-dedup -Pverbose` |
| 46 | NOT_LINEARIZABLE | k2 | 95 | `./gradlew chaos -Pruns=1 -PstartSeed=46 -Plabel=mutation-no-session-dedup -Pverbose` |
| 47 | NOT_LINEARIZABLE | k0 | 82 | `./gradlew chaos -Pruns=1 -PstartSeed=47 -Plabel=mutation-no-session-dedup -Pverbose` |
| 51 | NOT_LINEARIZABLE | k0 | 88 | `./gradlew chaos -Pruns=1 -PstartSeed=51 -Plabel=mutation-no-session-dedup -Pverbose` |
| 52 | NOT_LINEARIZABLE | k1 | 90 | `./gradlew chaos -Pruns=1 -PstartSeed=52 -Plabel=mutation-no-session-dedup -Pverbose` |
| 54 | NOT_LINEARIZABLE | k2 | 100 | `./gradlew chaos -Pruns=1 -PstartSeed=54 -Plabel=mutation-no-session-dedup -Pverbose` |
| 56 | NOT_LINEARIZABLE | k0 | 89 | `./gradlew chaos -Pruns=1 -PstartSeed=56 -Plabel=mutation-no-session-dedup -Pverbose` |
| 57 | NOT_LINEARIZABLE | k2 | 96 | `./gradlew chaos -Pruns=1 -PstartSeed=57 -Plabel=mutation-no-session-dedup -Pverbose` |
| 58 | NOT_LINEARIZABLE | k0 | 100 | `./gradlew chaos -Pruns=1 -PstartSeed=58 -Plabel=mutation-no-session-dedup -Pverbose` |
| 60 | NOT_LINEARIZABLE | k0 | 104 | `./gradlew chaos -Pruns=1 -PstartSeed=60 -Plabel=mutation-no-session-dedup -Pverbose` |
| 64 | NOT_LINEARIZABLE | k1 | 79 | `./gradlew chaos -Pruns=1 -PstartSeed=64 -Plabel=mutation-no-session-dedup -Pverbose` |
| 65 | NOT_LINEARIZABLE | k2 | 94 | `./gradlew chaos -Pruns=1 -PstartSeed=65 -Plabel=mutation-no-session-dedup -Pverbose` |
| 66 | NOT_LINEARIZABLE | k0 | 92 | `./gradlew chaos -Pruns=1 -PstartSeed=66 -Plabel=mutation-no-session-dedup -Pverbose` |
| 67 | NOT_LINEARIZABLE | k1 | 83 | `./gradlew chaos -Pruns=1 -PstartSeed=67 -Plabel=mutation-no-session-dedup -Pverbose` |
| 69 | NOT_LINEARIZABLE | k1 | 104 | `./gradlew chaos -Pruns=1 -PstartSeed=69 -Plabel=mutation-no-session-dedup -Pverbose` |
| 71 | NOT_LINEARIZABLE | k2 | 99 | `./gradlew chaos -Pruns=1 -PstartSeed=71 -Plabel=mutation-no-session-dedup -Pverbose` |
| 72 | NOT_LINEARIZABLE | k1 | 84 | `./gradlew chaos -Pruns=1 -PstartSeed=72 -Plabel=mutation-no-session-dedup -Pverbose` |
| 73 | NOT_LINEARIZABLE | k0 | 95 | `./gradlew chaos -Pruns=1 -PstartSeed=73 -Plabel=mutation-no-session-dedup -Pverbose` |
| 75 | NOT_LINEARIZABLE | k2 | 88 | `./gradlew chaos -Pruns=1 -PstartSeed=75 -Plabel=mutation-no-session-dedup -Pverbose` |
| 76 | NOT_LINEARIZABLE | k0 | 92 | `./gradlew chaos -Pruns=1 -PstartSeed=76 -Plabel=mutation-no-session-dedup -Pverbose` |
| 78 | NOT_LINEARIZABLE | k0 | 91 | `./gradlew chaos -Pruns=1 -PstartSeed=78 -Plabel=mutation-no-session-dedup -Pverbose` |
| 82 | NOT_LINEARIZABLE | k2 | 96 | `./gradlew chaos -Pruns=1 -PstartSeed=82 -Plabel=mutation-no-session-dedup -Pverbose` |
| 84 | NOT_LINEARIZABLE | k0 | 71 | `./gradlew chaos -Pruns=1 -PstartSeed=84 -Plabel=mutation-no-session-dedup -Pverbose` |
| 86 | NOT_LINEARIZABLE | k1 | 87 | `./gradlew chaos -Pruns=1 -PstartSeed=86 -Plabel=mutation-no-session-dedup -Pverbose` |
| 89 | NOT_LINEARIZABLE | k2 | 88 | `./gradlew chaos -Pruns=1 -PstartSeed=89 -Plabel=mutation-no-session-dedup -Pverbose` |
| 91 | NOT_LINEARIZABLE | k1 | 99 | `./gradlew chaos -Pruns=1 -PstartSeed=91 -Plabel=mutation-no-session-dedup -Pverbose` |
| 96 | NOT_LINEARIZABLE | k0 | 88 | `./gradlew chaos -Pruns=1 -PstartSeed=96 -Plabel=mutation-no-session-dedup -Pverbose` |
| 97 | NOT_LINEARIZABLE | k1 | 91 | `./gradlew chaos -Pruns=1 -PstartSeed=97 -Plabel=mutation-no-session-dedup -Pverbose` |
| 99 | NOT_LINEARIZABLE | k0 | 90 | `./gradlew chaos -Pruns=1 -PstartSeed=99 -Plabel=mutation-no-session-dedup -Pverbose` |
| 100 | NOT_LINEARIZABLE | k1 | 83 | `./gradlew chaos -Pruns=1 -PstartSeed=100 -Plabel=mutation-no-session-dedup -Pverbose` |
| 101 | NOT_LINEARIZABLE | k0 | 80 | `./gradlew chaos -Pruns=1 -PstartSeed=101 -Plabel=mutation-no-session-dedup -Pverbose` |
| 102 | NOT_LINEARIZABLE | k0 | 83 | `./gradlew chaos -Pruns=1 -PstartSeed=102 -Plabel=mutation-no-session-dedup -Pverbose` |
| 105 | NOT_LINEARIZABLE | k1 | 104 | `./gradlew chaos -Pruns=1 -PstartSeed=105 -Plabel=mutation-no-session-dedup -Pverbose` |
| 108 | NOT_LINEARIZABLE | k1 | 81 | `./gradlew chaos -Pruns=1 -PstartSeed=108 -Plabel=mutation-no-session-dedup -Pverbose` |
| 109 | NOT_LINEARIZABLE | k1 | 87 | `./gradlew chaos -Pruns=1 -PstartSeed=109 -Plabel=mutation-no-session-dedup -Pverbose` |
| 110 | NOT_LINEARIZABLE | k1 | 94 | `./gradlew chaos -Pruns=1 -PstartSeed=110 -Plabel=mutation-no-session-dedup -Pverbose` |
| 111 | NOT_LINEARIZABLE | k2 | 103 | `./gradlew chaos -Pruns=1 -PstartSeed=111 -Plabel=mutation-no-session-dedup -Pverbose` |
| 115 | NOT_LINEARIZABLE | k2 | 84 | `./gradlew chaos -Pruns=1 -PstartSeed=115 -Plabel=mutation-no-session-dedup -Pverbose` |
| 117 | NOT_LINEARIZABLE | k0 | 101 | `./gradlew chaos -Pruns=1 -PstartSeed=117 -Plabel=mutation-no-session-dedup -Pverbose` |
| 119 | NOT_LINEARIZABLE | k0 | 96 | `./gradlew chaos -Pruns=1 -PstartSeed=119 -Plabel=mutation-no-session-dedup -Pverbose` |
| 121 | NOT_LINEARIZABLE | k0 | 90 | `./gradlew chaos -Pruns=1 -PstartSeed=121 -Plabel=mutation-no-session-dedup -Pverbose` |
| 122 | NOT_LINEARIZABLE | k2 | 96 | `./gradlew chaos -Pruns=1 -PstartSeed=122 -Plabel=mutation-no-session-dedup -Pverbose` |
| 123 | NOT_LINEARIZABLE | k1 | 90 | `./gradlew chaos -Pruns=1 -PstartSeed=123 -Plabel=mutation-no-session-dedup -Pverbose` |
| 129 | NOT_LINEARIZABLE | k1 | 96 | `./gradlew chaos -Pruns=1 -PstartSeed=129 -Plabel=mutation-no-session-dedup -Pverbose` |
| 132 | NOT_LINEARIZABLE | k0 | 95 | `./gradlew chaos -Pruns=1 -PstartSeed=132 -Plabel=mutation-no-session-dedup -Pverbose` |
| 133 | NOT_LINEARIZABLE | k2 | 91 | `./gradlew chaos -Pruns=1 -PstartSeed=133 -Plabel=mutation-no-session-dedup -Pverbose` |
| 134 | NOT_LINEARIZABLE | k2 | 97 | `./gradlew chaos -Pruns=1 -PstartSeed=134 -Plabel=mutation-no-session-dedup -Pverbose` |
| 136 | NOT_LINEARIZABLE | k0 | 95 | `./gradlew chaos -Pruns=1 -PstartSeed=136 -Plabel=mutation-no-session-dedup -Pverbose` |
| 141 | NOT_LINEARIZABLE | k2 | 93 | `./gradlew chaos -Pruns=1 -PstartSeed=141 -Plabel=mutation-no-session-dedup -Pverbose` |
| 143 | NOT_LINEARIZABLE | k0 | 100 | `./gradlew chaos -Pruns=1 -PstartSeed=143 -Plabel=mutation-no-session-dedup -Pverbose` |
| 148 | NOT_LINEARIZABLE | k0 | 93 | `./gradlew chaos -Pruns=1 -PstartSeed=148 -Plabel=mutation-no-session-dedup -Pverbose` |
| 150 | NOT_LINEARIZABLE | k2 | 89 | `./gradlew chaos -Pruns=1 -PstartSeed=150 -Plabel=mutation-no-session-dedup -Pverbose` |
| 151 | NOT_LINEARIZABLE | k0 | 99 | `./gradlew chaos -Pruns=1 -PstartSeed=151 -Plabel=mutation-no-session-dedup -Pverbose` |
| 152 | NOT_LINEARIZABLE | k0 | 94 | `./gradlew chaos -Pruns=1 -PstartSeed=152 -Plabel=mutation-no-session-dedup -Pverbose` |
| 154 | NOT_LINEARIZABLE | k0 | 86 | `./gradlew chaos -Pruns=1 -PstartSeed=154 -Plabel=mutation-no-session-dedup -Pverbose` |
| 164 | NOT_LINEARIZABLE | k0 | 88 | `./gradlew chaos -Pruns=1 -PstartSeed=164 -Plabel=mutation-no-session-dedup -Pverbose` |
| 165 | NOT_LINEARIZABLE | k1 | 101 | `./gradlew chaos -Pruns=1 -PstartSeed=165 -Plabel=mutation-no-session-dedup -Pverbose` |
| 167 | NOT_LINEARIZABLE | k1 | 95 | `./gradlew chaos -Pruns=1 -PstartSeed=167 -Plabel=mutation-no-session-dedup -Pverbose` |
| 170 | NOT_LINEARIZABLE | k0 | 84 | `./gradlew chaos -Pruns=1 -PstartSeed=170 -Plabel=mutation-no-session-dedup -Pverbose` |
| 171 | NOT_LINEARIZABLE | k0 | 86 | `./gradlew chaos -Pruns=1 -PstartSeed=171 -Plabel=mutation-no-session-dedup -Pverbose` |
| 172 | NOT_LINEARIZABLE | k2 | 98 | `./gradlew chaos -Pruns=1 -PstartSeed=172 -Plabel=mutation-no-session-dedup -Pverbose` |
| 173 | NOT_LINEARIZABLE | k0 | 81 | `./gradlew chaos -Pruns=1 -PstartSeed=173 -Plabel=mutation-no-session-dedup -Pverbose` |
| 177 | NOT_LINEARIZABLE | k0 | 98 | `./gradlew chaos -Pruns=1 -PstartSeed=177 -Plabel=mutation-no-session-dedup -Pverbose` |
| 178 | NOT_LINEARIZABLE | k0 | 100 | `./gradlew chaos -Pruns=1 -PstartSeed=178 -Plabel=mutation-no-session-dedup -Pverbose` |
| 181 | NOT_LINEARIZABLE | k0 | 75 | `./gradlew chaos -Pruns=1 -PstartSeed=181 -Plabel=mutation-no-session-dedup -Pverbose` |
| 183 | NOT_LINEARIZABLE | k2 | 91 | `./gradlew chaos -Pruns=1 -PstartSeed=183 -Plabel=mutation-no-session-dedup -Pverbose` |
| 187 | NOT_LINEARIZABLE | k0 | 84 | `./gradlew chaos -Pruns=1 -PstartSeed=187 -Plabel=mutation-no-session-dedup -Pverbose` |
| 188 | NOT_LINEARIZABLE | k2 | 81 | `./gradlew chaos -Pruns=1 -PstartSeed=188 -Plabel=mutation-no-session-dedup -Pverbose` |
| 191 | NOT_LINEARIZABLE | k1 | 98 | `./gradlew chaos -Pruns=1 -PstartSeed=191 -Plabel=mutation-no-session-dedup -Pverbose` |
| 194 | NOT_LINEARIZABLE | k2 | 90 | `./gradlew chaos -Pruns=1 -PstartSeed=194 -Plabel=mutation-no-session-dedup -Pverbose` |
| 195 | NOT_LINEARIZABLE | k0 | 95 | `./gradlew chaos -Pruns=1 -PstartSeed=195 -Plabel=mutation-no-session-dedup -Pverbose` |
| 198 | NOT_LINEARIZABLE | k2 | 90 | `./gradlew chaos -Pruns=1 -PstartSeed=198 -Plabel=mutation-no-session-dedup -Pverbose` |
| 200 | NOT_LINEARIZABLE | k0 | 90 | `./gradlew chaos -Pruns=1 -PstartSeed=200 -Plabel=mutation-no-session-dedup -Pverbose` |
| 202 | NOT_LINEARIZABLE | k0 | 86 | `./gradlew chaos -Pruns=1 -PstartSeed=202 -Plabel=mutation-no-session-dedup -Pverbose` |
| 203 | NOT_LINEARIZABLE | k0 | 66 | `./gradlew chaos -Pruns=1 -PstartSeed=203 -Plabel=mutation-no-session-dedup -Pverbose` |
| 204 | NOT_LINEARIZABLE | k0 | 63 | `./gradlew chaos -Pruns=1 -PstartSeed=204 -Plabel=mutation-no-session-dedup -Pverbose` |
| 210 | NOT_LINEARIZABLE | k0 | 88 | `./gradlew chaos -Pruns=1 -PstartSeed=210 -Plabel=mutation-no-session-dedup -Pverbose` |
| 212 | NOT_LINEARIZABLE | k1 | 105 | `./gradlew chaos -Pruns=1 -PstartSeed=212 -Plabel=mutation-no-session-dedup -Pverbose` |
| 219 | NOT_LINEARIZABLE | k1 | 92 | `./gradlew chaos -Pruns=1 -PstartSeed=219 -Plabel=mutation-no-session-dedup -Pverbose` |
| 221 | NOT_LINEARIZABLE | k1 | 103 | `./gradlew chaos -Pruns=1 -PstartSeed=221 -Plabel=mutation-no-session-dedup -Pverbose` |
| 223 | NOT_LINEARIZABLE | k1 | 84 | `./gradlew chaos -Pruns=1 -PstartSeed=223 -Plabel=mutation-no-session-dedup -Pverbose` |
| 224 | NOT_LINEARIZABLE | k2 | 88 | `./gradlew chaos -Pruns=1 -PstartSeed=224 -Plabel=mutation-no-session-dedup -Pverbose` |
| 225 | NOT_LINEARIZABLE | k0 | 95 | `./gradlew chaos -Pruns=1 -PstartSeed=225 -Plabel=mutation-no-session-dedup -Pverbose` |
| 229 | NOT_LINEARIZABLE | k1 | 92 | `./gradlew chaos -Pruns=1 -PstartSeed=229 -Plabel=mutation-no-session-dedup -Pverbose` |
| 239 | NOT_LINEARIZABLE | k0 | 83 | `./gradlew chaos -Pruns=1 -PstartSeed=239 -Plabel=mutation-no-session-dedup -Pverbose` |
| 241 | NOT_LINEARIZABLE | k0 | 95 | `./gradlew chaos -Pruns=1 -PstartSeed=241 -Plabel=mutation-no-session-dedup -Pverbose` |
| 243 | NOT_LINEARIZABLE | k2 | 84 | `./gradlew chaos -Pruns=1 -PstartSeed=243 -Plabel=mutation-no-session-dedup -Pverbose` |
| 244 | NOT_LINEARIZABLE | k2 | 82 | `./gradlew chaos -Pruns=1 -PstartSeed=244 -Plabel=mutation-no-session-dedup -Pverbose` |
| 249 | NOT_LINEARIZABLE | k2 | 97 | `./gradlew chaos -Pruns=1 -PstartSeed=249 -Plabel=mutation-no-session-dedup -Pverbose` |
| 252 | INCONCLUSIVE | k2 | 89 | `./gradlew chaos -Pruns=1 -PstartSeed=252 -Plabel=mutation-no-session-dedup -Pverbose` |
| 256 | NOT_LINEARIZABLE | k2 | 85 | `./gradlew chaos -Pruns=1 -PstartSeed=256 -Plabel=mutation-no-session-dedup -Pverbose` |
| 258 | NOT_LINEARIZABLE | k2 | 90 | `./gradlew chaos -Pruns=1 -PstartSeed=258 -Plabel=mutation-no-session-dedup -Pverbose` |
| 261 | NOT_LINEARIZABLE | k0 | 92 | `./gradlew chaos -Pruns=1 -PstartSeed=261 -Plabel=mutation-no-session-dedup -Pverbose` |
| 262 | NOT_LINEARIZABLE | k0 | 90 | `./gradlew chaos -Pruns=1 -PstartSeed=262 -Plabel=mutation-no-session-dedup -Pverbose` |
| 265 | NOT_LINEARIZABLE | k2 | 82 | `./gradlew chaos -Pruns=1 -PstartSeed=265 -Plabel=mutation-no-session-dedup -Pverbose` |
| 266 | NOT_LINEARIZABLE | k0 | 88 | `./gradlew chaos -Pruns=1 -PstartSeed=266 -Plabel=mutation-no-session-dedup -Pverbose` |
| 268 | NOT_LINEARIZABLE | k0 | 83 | `./gradlew chaos -Pruns=1 -PstartSeed=268 -Plabel=mutation-no-session-dedup -Pverbose` |
| 269 | NOT_LINEARIZABLE | k0 | 99 | `./gradlew chaos -Pruns=1 -PstartSeed=269 -Plabel=mutation-no-session-dedup -Pverbose` |
| 270 | NOT_LINEARIZABLE | k0 | 91 | `./gradlew chaos -Pruns=1 -PstartSeed=270 -Plabel=mutation-no-session-dedup -Pverbose` |
| 272 | NOT_LINEARIZABLE | k0 | 96 | `./gradlew chaos -Pruns=1 -PstartSeed=272 -Plabel=mutation-no-session-dedup -Pverbose` |
| 274 | NOT_LINEARIZABLE | k0 | 96 | `./gradlew chaos -Pruns=1 -PstartSeed=274 -Plabel=mutation-no-session-dedup -Pverbose` |
| 278 | NOT_LINEARIZABLE | k0 | 81 | `./gradlew chaos -Pruns=1 -PstartSeed=278 -Plabel=mutation-no-session-dedup -Pverbose` |
| 284 | NOT_LINEARIZABLE | k0 | 86 | `./gradlew chaos -Pruns=1 -PstartSeed=284 -Plabel=mutation-no-session-dedup -Pverbose` |
| 291 | NOT_LINEARIZABLE | k1 | 106 | `./gradlew chaos -Pruns=1 -PstartSeed=291 -Plabel=mutation-no-session-dedup -Pverbose` |
| 294 | NOT_LINEARIZABLE | k0 | 90 | `./gradlew chaos -Pruns=1 -PstartSeed=294 -Plabel=mutation-no-session-dedup -Pverbose` |
| 296 | NOT_LINEARIZABLE | k0 | 95 | `./gradlew chaos -Pruns=1 -PstartSeed=296 -Plabel=mutation-no-session-dedup -Pverbose` |
| 297 | NOT_LINEARIZABLE | k1 | 93 | `./gradlew chaos -Pruns=1 -PstartSeed=297 -Plabel=mutation-no-session-dedup -Pverbose` |
| 298 | NOT_LINEARIZABLE | k0 | 80 | `./gradlew chaos -Pruns=1 -PstartSeed=298 -Plabel=mutation-no-session-dedup -Pverbose` |
| 301 | NOT_LINEARIZABLE | k2 | 111 | `./gradlew chaos -Pruns=1 -PstartSeed=301 -Plabel=mutation-no-session-dedup -Pverbose` |
| 303 | NOT_LINEARIZABLE | k0 | 105 | `./gradlew chaos -Pruns=1 -PstartSeed=303 -Plabel=mutation-no-session-dedup -Pverbose` |
| 304 | NOT_LINEARIZABLE | k0 | 73 | `./gradlew chaos -Pruns=1 -PstartSeed=304 -Plabel=mutation-no-session-dedup -Pverbose` |
| 305 | NOT_LINEARIZABLE | k2 | 108 | `./gradlew chaos -Pruns=1 -PstartSeed=305 -Plabel=mutation-no-session-dedup -Pverbose` |
| 309 | NOT_LINEARIZABLE | k1 | 77 | `./gradlew chaos -Pruns=1 -PstartSeed=309 -Plabel=mutation-no-session-dedup -Pverbose` |
| 323 | NOT_LINEARIZABLE | k2 | 98 | `./gradlew chaos -Pruns=1 -PstartSeed=323 -Plabel=mutation-no-session-dedup -Pverbose` |
| 324 | NOT_LINEARIZABLE | k0 | 74 | `./gradlew chaos -Pruns=1 -PstartSeed=324 -Plabel=mutation-no-session-dedup -Pverbose` |
| 326 | NOT_LINEARIZABLE | k0 | 103 | `./gradlew chaos -Pruns=1 -PstartSeed=326 -Plabel=mutation-no-session-dedup -Pverbose` |
| 327 | NOT_LINEARIZABLE | k1 | 95 | `./gradlew chaos -Pruns=1 -PstartSeed=327 -Plabel=mutation-no-session-dedup -Pverbose` |
| 328 | NOT_LINEARIZABLE | k0 | 76 | `./gradlew chaos -Pruns=1 -PstartSeed=328 -Plabel=mutation-no-session-dedup -Pverbose` |
| 329 | NOT_LINEARIZABLE | k0 | 84 | `./gradlew chaos -Pruns=1 -PstartSeed=329 -Plabel=mutation-no-session-dedup -Pverbose` |
| 332 | NOT_LINEARIZABLE | k2 | 82 | `./gradlew chaos -Pruns=1 -PstartSeed=332 -Plabel=mutation-no-session-dedup -Pverbose` |
| 333 | NOT_LINEARIZABLE | k1 | 79 | `./gradlew chaos -Pruns=1 -PstartSeed=333 -Plabel=mutation-no-session-dedup -Pverbose` |
| 334 | NOT_LINEARIZABLE | k2 | 87 | `./gradlew chaos -Pruns=1 -PstartSeed=334 -Plabel=mutation-no-session-dedup -Pverbose` |
| 335 | NOT_LINEARIZABLE | k1 | 85 | `./gradlew chaos -Pruns=1 -PstartSeed=335 -Plabel=mutation-no-session-dedup -Pverbose` |
| 337 | NOT_LINEARIZABLE | k0 | 79 | `./gradlew chaos -Pruns=1 -PstartSeed=337 -Plabel=mutation-no-session-dedup -Pverbose` |
| 340 | NOT_LINEARIZABLE | k2 | 105 | `./gradlew chaos -Pruns=1 -PstartSeed=340 -Plabel=mutation-no-session-dedup -Pverbose` |
| 344 | NOT_LINEARIZABLE | k2 | 88 | `./gradlew chaos -Pruns=1 -PstartSeed=344 -Plabel=mutation-no-session-dedup -Pverbose` |
| 345 | NOT_LINEARIZABLE | k2 | 81 | `./gradlew chaos -Pruns=1 -PstartSeed=345 -Plabel=mutation-no-session-dedup -Pverbose` |
| 350 | NOT_LINEARIZABLE | k0 | 82 | `./gradlew chaos -Pruns=1 -PstartSeed=350 -Plabel=mutation-no-session-dedup -Pverbose` |
| 351 | NOT_LINEARIZABLE | k2 | 93 | `./gradlew chaos -Pruns=1 -PstartSeed=351 -Plabel=mutation-no-session-dedup -Pverbose` |
| 354 | NOT_LINEARIZABLE | k0 | 102 | `./gradlew chaos -Pruns=1 -PstartSeed=354 -Plabel=mutation-no-session-dedup -Pverbose` |
| 356 | NOT_LINEARIZABLE | k1 | 83 | `./gradlew chaos -Pruns=1 -PstartSeed=356 -Plabel=mutation-no-session-dedup -Pverbose` |
| 357 | NOT_LINEARIZABLE | k0 | 88 | `./gradlew chaos -Pruns=1 -PstartSeed=357 -Plabel=mutation-no-session-dedup -Pverbose` |
| 360 | NOT_LINEARIZABLE | k1 | 104 | `./gradlew chaos -Pruns=1 -PstartSeed=360 -Plabel=mutation-no-session-dedup -Pverbose` |
| 361 | NOT_LINEARIZABLE | k2 | 87 | `./gradlew chaos -Pruns=1 -PstartSeed=361 -Plabel=mutation-no-session-dedup -Pverbose` |
| 362 | NOT_LINEARIZABLE | k2 | 94 | `./gradlew chaos -Pruns=1 -PstartSeed=362 -Plabel=mutation-no-session-dedup -Pverbose` |
| 363 | NOT_LINEARIZABLE | k2 | 92 | `./gradlew chaos -Pruns=1 -PstartSeed=363 -Plabel=mutation-no-session-dedup -Pverbose` |
| 367 | NOT_LINEARIZABLE | k0 | 104 | `./gradlew chaos -Pruns=1 -PstartSeed=367 -Plabel=mutation-no-session-dedup -Pverbose` |
| 368 | NOT_LINEARIZABLE | k2 | 93 | `./gradlew chaos -Pruns=1 -PstartSeed=368 -Plabel=mutation-no-session-dedup -Pverbose` |
| 369 | NOT_LINEARIZABLE | k2 | 91 | `./gradlew chaos -Pruns=1 -PstartSeed=369 -Plabel=mutation-no-session-dedup -Pverbose` |
| 371 | NOT_LINEARIZABLE | k0 | 77 | `./gradlew chaos -Pruns=1 -PstartSeed=371 -Plabel=mutation-no-session-dedup -Pverbose` |
| 373 | NOT_LINEARIZABLE | k1 | 102 | `./gradlew chaos -Pruns=1 -PstartSeed=373 -Plabel=mutation-no-session-dedup -Pverbose` |
| 375 | NOT_LINEARIZABLE | k0 | 93 | `./gradlew chaos -Pruns=1 -PstartSeed=375 -Plabel=mutation-no-session-dedup -Pverbose` |
| 376 | NOT_LINEARIZABLE | k0 | 89 | `./gradlew chaos -Pruns=1 -PstartSeed=376 -Plabel=mutation-no-session-dedup -Pverbose` |
| 377 | NOT_LINEARIZABLE | k1 | 93 | `./gradlew chaos -Pruns=1 -PstartSeed=377 -Plabel=mutation-no-session-dedup -Pverbose` |
| 378 | NOT_LINEARIZABLE | k0 | 89 | `./gradlew chaos -Pruns=1 -PstartSeed=378 -Plabel=mutation-no-session-dedup -Pverbose` |
| 379 | NOT_LINEARIZABLE | k0 | 94 | `./gradlew chaos -Pruns=1 -PstartSeed=379 -Plabel=mutation-no-session-dedup -Pverbose` |
| 380 | NOT_LINEARIZABLE | k0 | 100 | `./gradlew chaos -Pruns=1 -PstartSeed=380 -Plabel=mutation-no-session-dedup -Pverbose` |
| 381 | NOT_LINEARIZABLE | k0 | 100 | `./gradlew chaos -Pruns=1 -PstartSeed=381 -Plabel=mutation-no-session-dedup -Pverbose` |
| 383 | NOT_LINEARIZABLE | k0 | 89 | `./gradlew chaos -Pruns=1 -PstartSeed=383 -Plabel=mutation-no-session-dedup -Pverbose` |
| 387 | NOT_LINEARIZABLE | k0 | 90 | `./gradlew chaos -Pruns=1 -PstartSeed=387 -Plabel=mutation-no-session-dedup -Pverbose` |
| 388 | NOT_LINEARIZABLE | k1 | 84 | `./gradlew chaos -Pruns=1 -PstartSeed=388 -Plabel=mutation-no-session-dedup -Pverbose` |
| 389 | NOT_LINEARIZABLE | k2 | 99 | `./gradlew chaos -Pruns=1 -PstartSeed=389 -Plabel=mutation-no-session-dedup -Pverbose` |
| 391 | NOT_LINEARIZABLE | k1 | 103 | `./gradlew chaos -Pruns=1 -PstartSeed=391 -Plabel=mutation-no-session-dedup -Pverbose` |
| 392 | NOT_LINEARIZABLE | k1 | 90 | `./gradlew chaos -Pruns=1 -PstartSeed=392 -Plabel=mutation-no-session-dedup -Pverbose` |
| 393 | NOT_LINEARIZABLE | k1 | 85 | `./gradlew chaos -Pruns=1 -PstartSeed=393 -Plabel=mutation-no-session-dedup -Pverbose` |
| 395 | NOT_LINEARIZABLE | k0 | 100 | `./gradlew chaos -Pruns=1 -PstartSeed=395 -Plabel=mutation-no-session-dedup -Pverbose` |
| 397 | NOT_LINEARIZABLE | k1 | 85 | `./gradlew chaos -Pruns=1 -PstartSeed=397 -Plabel=mutation-no-session-dedup -Pverbose` |
| 398 | NOT_LINEARIZABLE | k0 | 81 | `./gradlew chaos -Pruns=1 -PstartSeed=398 -Plabel=mutation-no-session-dedup -Pverbose` |
| 400 | NOT_LINEARIZABLE | k1 | 84 | `./gradlew chaos -Pruns=1 -PstartSeed=400 -Plabel=mutation-no-session-dedup -Pverbose` |
| 401 | NOT_LINEARIZABLE | k0 | 84 | `./gradlew chaos -Pruns=1 -PstartSeed=401 -Plabel=mutation-no-session-dedup -Pverbose` |
| 402 | NOT_LINEARIZABLE | k2 | 102 | `./gradlew chaos -Pruns=1 -PstartSeed=402 -Plabel=mutation-no-session-dedup -Pverbose` |
| 405 | NOT_LINEARIZABLE | k1 | 97 | `./gradlew chaos -Pruns=1 -PstartSeed=405 -Plabel=mutation-no-session-dedup -Pverbose` |
| 408 | NOT_LINEARIZABLE | k1 | 88 | `./gradlew chaos -Pruns=1 -PstartSeed=408 -Plabel=mutation-no-session-dedup -Pverbose` |
| 409 | NOT_LINEARIZABLE | k1 | 83 | `./gradlew chaos -Pruns=1 -PstartSeed=409 -Plabel=mutation-no-session-dedup -Pverbose` |
| 410 | NOT_LINEARIZABLE | k0 | 93 | `./gradlew chaos -Pruns=1 -PstartSeed=410 -Plabel=mutation-no-session-dedup -Pverbose` |
| 411 | NOT_LINEARIZABLE | k2 | 98 | `./gradlew chaos -Pruns=1 -PstartSeed=411 -Plabel=mutation-no-session-dedup -Pverbose` |
| 412 | NOT_LINEARIZABLE | k1 | 81 | `./gradlew chaos -Pruns=1 -PstartSeed=412 -Plabel=mutation-no-session-dedup -Pverbose` |
| 417 | NOT_LINEARIZABLE | k1 | 83 | `./gradlew chaos -Pruns=1 -PstartSeed=417 -Plabel=mutation-no-session-dedup -Pverbose` |
| 418 | NOT_LINEARIZABLE | k2 | 92 | `./gradlew chaos -Pruns=1 -PstartSeed=418 -Plabel=mutation-no-session-dedup -Pverbose` |
| 420 | NOT_LINEARIZABLE | k2 | 89 | `./gradlew chaos -Pruns=1 -PstartSeed=420 -Plabel=mutation-no-session-dedup -Pverbose` |
| 421 | NOT_LINEARIZABLE | k0 | 93 | `./gradlew chaos -Pruns=1 -PstartSeed=421 -Plabel=mutation-no-session-dedup -Pverbose` |
| 422 | NOT_LINEARIZABLE | k1 | 81 | `./gradlew chaos -Pruns=1 -PstartSeed=422 -Plabel=mutation-no-session-dedup -Pverbose` |
| 427 | NOT_LINEARIZABLE | k2 | 95 | `./gradlew chaos -Pruns=1 -PstartSeed=427 -Plabel=mutation-no-session-dedup -Pverbose` |
| 430 | NOT_LINEARIZABLE | k0 | 79 | `./gradlew chaos -Pruns=1 -PstartSeed=430 -Plabel=mutation-no-session-dedup -Pverbose` |
| 431 | NOT_LINEARIZABLE | k1 | 98 | `./gradlew chaos -Pruns=1 -PstartSeed=431 -Plabel=mutation-no-session-dedup -Pverbose` |
| 435 | NOT_LINEARIZABLE | k2 | 81 | `./gradlew chaos -Pruns=1 -PstartSeed=435 -Plabel=mutation-no-session-dedup -Pverbose` |
| 436 | NOT_LINEARIZABLE | k1 | 91 | `./gradlew chaos -Pruns=1 -PstartSeed=436 -Plabel=mutation-no-session-dedup -Pverbose` |
| 437 | NOT_LINEARIZABLE | k0 | 92 | `./gradlew chaos -Pruns=1 -PstartSeed=437 -Plabel=mutation-no-session-dedup -Pverbose` |
| 438 | NOT_LINEARIZABLE | k2 | 99 | `./gradlew chaos -Pruns=1 -PstartSeed=438 -Plabel=mutation-no-session-dedup -Pverbose` |
| 441 | NOT_LINEARIZABLE | k2 | 92 | `./gradlew chaos -Pruns=1 -PstartSeed=441 -Plabel=mutation-no-session-dedup -Pverbose` |
| 442 | NOT_LINEARIZABLE | k1 | 95 | `./gradlew chaos -Pruns=1 -PstartSeed=442 -Plabel=mutation-no-session-dedup -Pverbose` |
| 443 | NOT_LINEARIZABLE | k0 | 86 | `./gradlew chaos -Pruns=1 -PstartSeed=443 -Plabel=mutation-no-session-dedup -Pverbose` |
| 444 | NOT_LINEARIZABLE | k0 | 73 | `./gradlew chaos -Pruns=1 -PstartSeed=444 -Plabel=mutation-no-session-dedup -Pverbose` |
| 445 | NOT_LINEARIZABLE | k0 | 101 | `./gradlew chaos -Pruns=1 -PstartSeed=445 -Plabel=mutation-no-session-dedup -Pverbose` |
| 449 | NOT_LINEARIZABLE | k1 | 82 | `./gradlew chaos -Pruns=1 -PstartSeed=449 -Plabel=mutation-no-session-dedup -Pverbose` |
| 451 | NOT_LINEARIZABLE | k2 | 93 | `./gradlew chaos -Pruns=1 -PstartSeed=451 -Plabel=mutation-no-session-dedup -Pverbose` |
| 452 | NOT_LINEARIZABLE | k2 | 93 | `./gradlew chaos -Pruns=1 -PstartSeed=452 -Plabel=mutation-no-session-dedup -Pverbose` |
| 453 | NOT_LINEARIZABLE | k1 | 93 | `./gradlew chaos -Pruns=1 -PstartSeed=453 -Plabel=mutation-no-session-dedup -Pverbose` |
| 456 | NOT_LINEARIZABLE | k2 | 79 | `./gradlew chaos -Pruns=1 -PstartSeed=456 -Plabel=mutation-no-session-dedup -Pverbose` |
| 460 | NOT_LINEARIZABLE | k1 | 81 | `./gradlew chaos -Pruns=1 -PstartSeed=460 -Plabel=mutation-no-session-dedup -Pverbose` |
| 464 | NOT_LINEARIZABLE | k0 | 94 | `./gradlew chaos -Pruns=1 -PstartSeed=464 -Plabel=mutation-no-session-dedup -Pverbose` |
| 465 | NOT_LINEARIZABLE | k2 | 84 | `./gradlew chaos -Pruns=1 -PstartSeed=465 -Plabel=mutation-no-session-dedup -Pverbose` |
| 466 | NOT_LINEARIZABLE | k2 | 100 | `./gradlew chaos -Pruns=1 -PstartSeed=466 -Plabel=mutation-no-session-dedup -Pverbose` |
| 467 | NOT_LINEARIZABLE | k0 | 104 | `./gradlew chaos -Pruns=1 -PstartSeed=467 -Plabel=mutation-no-session-dedup -Pverbose` |
| 468 | NOT_LINEARIZABLE | k2 | 104 | `./gradlew chaos -Pruns=1 -PstartSeed=468 -Plabel=mutation-no-session-dedup -Pverbose` |
| 470 | NOT_LINEARIZABLE | k0 | 77 | `./gradlew chaos -Pruns=1 -PstartSeed=470 -Plabel=mutation-no-session-dedup -Pverbose` |
| 473 | NOT_LINEARIZABLE | k2 | 99 | `./gradlew chaos -Pruns=1 -PstartSeed=473 -Plabel=mutation-no-session-dedup -Pverbose` |
| 475 | NOT_LINEARIZABLE | k2 | 94 | `./gradlew chaos -Pruns=1 -PstartSeed=475 -Plabel=mutation-no-session-dedup -Pverbose` |
| 479 | NOT_LINEARIZABLE | k1 | 100 | `./gradlew chaos -Pruns=1 -PstartSeed=479 -Plabel=mutation-no-session-dedup -Pverbose` |
| 480 | NOT_LINEARIZABLE | k1 | 92 | `./gradlew chaos -Pruns=1 -PstartSeed=480 -Plabel=mutation-no-session-dedup -Pverbose` |
| 481 | NOT_LINEARIZABLE | k0 | 95 | `./gradlew chaos -Pruns=1 -PstartSeed=481 -Plabel=mutation-no-session-dedup -Pverbose` |
| 483 | NOT_LINEARIZABLE | k0 | 82 | `./gradlew chaos -Pruns=1 -PstartSeed=483 -Plabel=mutation-no-session-dedup -Pverbose` |
| 484 | NOT_LINEARIZABLE | k0 | 102 | `./gradlew chaos -Pruns=1 -PstartSeed=484 -Plabel=mutation-no-session-dedup -Pverbose` |
| 488 | NOT_LINEARIZABLE | k0 | 95 | `./gradlew chaos -Pruns=1 -PstartSeed=488 -Plabel=mutation-no-session-dedup -Pverbose` |
| 489 | NOT_LINEARIZABLE | k2 | 100 | `./gradlew chaos -Pruns=1 -PstartSeed=489 -Plabel=mutation-no-session-dedup -Pverbose` |
| 490 | NOT_LINEARIZABLE | k0 | 96 | `./gradlew chaos -Pruns=1 -PstartSeed=490 -Plabel=mutation-no-session-dedup -Pverbose` |
| 492 | NOT_LINEARIZABLE | k1 | 91 | `./gradlew chaos -Pruns=1 -PstartSeed=492 -Plabel=mutation-no-session-dedup -Pverbose` |
| 493 | NOT_LINEARIZABLE | k0 | 88 | `./gradlew chaos -Pruns=1 -PstartSeed=493 -Plabel=mutation-no-session-dedup -Pverbose` |
| 496 | NOT_LINEARIZABLE | k2 | 94 | `./gradlew chaos -Pruns=1 -PstartSeed=496 -Plabel=mutation-no-session-dedup -Pverbose` |
| 497 | NOT_LINEARIZABLE | k2 | 90 | `./gradlew chaos -Pruns=1 -PstartSeed=497 -Plabel=mutation-no-session-dedup -Pverbose` |

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
