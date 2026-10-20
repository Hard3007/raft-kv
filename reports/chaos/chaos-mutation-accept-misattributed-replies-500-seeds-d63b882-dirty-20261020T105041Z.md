# Chaos report: mutation-accept-misattributed-replies

- **Command:** `./gradlew chaos -Pruns=500 -Plabel=mutation-accept-misattributed-replies`
- **Date:** 2026-10-20T10:50:56.015752206Z
- **Git commit:** d63b882 (with uncommitted changes)
- **Machine:** Linux 6.8.0-1064-azure amd64, 2 cores (AMD EPYC 7763 64-Core Processor), Java 21, max heap 1024 MB
- **Config:** 5 nodes, 5 clients, 3 keys, 60 ops per client, think time 0-300 ms, a fault action every 100-400 ms (virtual), client attempt timeout 1000 ms / give-up 4000 ms, checker timeout 10 s (wall); PreVote on, CheckQuorum on, a snapshot every 20 entries in 64-byte InstallSnapshot chunks
- **Seeds:** 1..500 (500 runs)
- **Total wall time:** 14.1 s

## Verdicts

| Verdict | Runs |
|---|---|
| LINEARIZABLE | 309 |
| NOT_LINEARIZABLE | 0 |
| INCONCLUSIVE | 0 |
| runs where the system failed an internal check | 191 |

Only LINEARIZABLE runs count as passing. INCONCLUSIVE means the checker ran out of time. An internal-check failure (a safety assertion, or a node refusing to start because committed entries were lost) has no verdict and does not pass either.

## Per seed (min / mean / max)

| Measure | Min | Mean | Max |
|---|---|---|---|
| operations | 300 | 300.0 | 300 |
| completed operations | 184 | 237.8 | 285 |
| pending writes (unknown outcome) | 6 | 31.9 | 62 |
| reads left out (no definite answer) | 6 | 30.3 | 61 |
| node crashes (power loss) | 13 | 28.3 | 44 |
|   of which mid-I/O (crash at a random write/fsync/rename) | 5 | 14.6 | 27 |
| node pauses (frozen 0.5-2 s) | 9 | 26.2 | 47 |
| stale routes (one node's traffic reaches another) | 7 | 22.8 | 42 |
| partitions | 22 | 60.8 | 104 |
| message-loss periods | 7 | 19.8 | 35 |
| messages lost | 187 | 494.7 | 980 |
| long-delay periods | 7 | 20.0 | 33 |
| snapshots taken (all nodes) | 20 | 28.5 | 38 |
| InstallSnapshots completed (all nodes) | 5 | 12.8 | 18 |
| final term (highest across nodes) | 37 | 74.0 | 130 |
| virtual time (s) | 50.1 | 99.6 | 153.3 |
| wall time (ms) | 15 | 68.3 | 1285 |

## Runs that did not pass

| Seed | Verdict | Key / failure | Ops on key | Reproduce |
|---|---|---|---|---|
| 1 | INTERNAL_FAILURE | java.lang.AssertionError: State Machine Safety violated at index 50 on node 1 | - | `./gradlew chaos -Pruns=1 -PstartSeed=1 -Plabel=mutation-accept-misattributed-replies -Pverbose` |
| 9 | INTERNAL_FAILURE | java.lang.AssertionError: State Machine Safety violated at index 24 on node 3 | - | `./gradlew chaos -Pruns=1 -PstartSeed=9 -Plabel=mutation-accept-misattributed-replies -Pverbose` |
| 10 | INTERNAL_FAILURE | java.lang.AssertionError: Election Safety violated: nodes 5 and 3 were both leader in term 37 | - | `./gradlew chaos -Pruns=1 -PstartSeed=10 -Plabel=mutation-accept-misattributed-replies -Pverbose` |
| 12 | INTERNAL_FAILURE | java.lang.AssertionError: State Machine Safety violated at index 25 on node 3 | - | `./gradlew chaos -Pruns=1 -PstartSeed=12 -Plabel=mutation-accept-misattributed-replies -Pverbose` |
| 14 | INTERNAL_FAILURE | java.lang.IllegalStateException: node 3 asked to overwrite committed entry 173 (commitIndex 175) by leader 4 of term 63 | - | `./gradlew chaos -Pruns=1 -PstartSeed=14 -Plabel=mutation-accept-misattributed-replies -Pverbose` |
| 15 | INTERNAL_FAILURE | java.lang.AssertionError: Election Safety violated: nodes 3 and 4 were both leader in term 5 | - | `./gradlew chaos -Pruns=1 -PstartSeed=15 -Plabel=mutation-accept-misattributed-replies -Pverbose` |
| 18 | INTERNAL_FAILURE | java.lang.AssertionError: Election Safety violated: nodes 2 and 5 were both leader in term 4 | - | `./gradlew chaos -Pruns=1 -PstartSeed=18 -Plabel=mutation-accept-misattributed-replies -Pverbose` |
| 21 | INTERNAL_FAILURE | java.lang.IllegalStateException: node 2 asked to overwrite committed entry 175 (commitIndex 175) by leader 5 of term 73 | - | `./gradlew chaos -Pruns=1 -PstartSeed=21 -Plabel=mutation-accept-misattributed-replies -Pverbose` |
| 23 | INTERNAL_FAILURE | java.lang.IllegalStateException: node 3 asked to overwrite committed entry 29 (commitIndex 30) by leader 1 of term 8 | - | `./gradlew chaos -Pruns=1 -PstartSeed=23 -Plabel=mutation-accept-misattributed-replies -Pverbose` |
| 24 | INTERNAL_FAILURE | java.lang.IllegalStateException: node 1 asked to overwrite committed entry 61 (commitIndex 66) by leader 5 of term 26 | - | `./gradlew chaos -Pruns=1 -PstartSeed=24 -Plabel=mutation-accept-misattributed-replies -Pverbose` |
| 25 | INTERNAL_FAILURE | java.lang.AssertionError: Election Safety violated: nodes 3 and 5 were both leader in term 7 | - | `./gradlew chaos -Pruns=1 -PstartSeed=25 -Plabel=mutation-accept-misattributed-replies -Pverbose` |
| 26 | INTERNAL_FAILURE | java.lang.AssertionError: State Machine Safety violated at index 130 on node 4 | - | `./gradlew chaos -Pruns=1 -PstartSeed=26 -Plabel=mutation-accept-misattributed-replies -Pverbose` |
| 29 | INTERNAL_FAILURE | java.lang.AssertionError: Election Safety violated: nodes 3 and 1 were both leader in term 54 | - | `./gradlew chaos -Pruns=1 -PstartSeed=29 -Plabel=mutation-accept-misattributed-replies -Pverbose` |
| 30 | INTERNAL_FAILURE | java.lang.AssertionError: Election Safety violated: nodes 5 and 1 were both leader in term 23 | - | `./gradlew chaos -Pruns=1 -PstartSeed=30 -Plabel=mutation-accept-misattributed-replies -Pverbose` |
| 33 | INTERNAL_FAILURE | java.lang.IllegalStateException: node 2 asked to overwrite committed entry 41 (commitIndex 44) by leader 1 of term 19 | - | `./gradlew chaos -Pruns=1 -PstartSeed=33 -Plabel=mutation-accept-misattributed-replies -Pverbose` |
| 34 | INTERNAL_FAILURE | java.lang.AssertionError: State Machine Safety violated at index 51 on node 2 | - | `./gradlew chaos -Pruns=1 -PstartSeed=34 -Plabel=mutation-accept-misattributed-replies -Pverbose` |
| 35 | INTERNAL_FAILURE | java.lang.AssertionError: Election Safety violated: nodes 5 and 2 were both leader in term 87 | - | `./gradlew chaos -Pruns=1 -PstartSeed=35 -Plabel=mutation-accept-misattributed-replies -Pverbose` |
| 37 | INTERNAL_FAILURE | java.lang.AssertionError: State Machine Safety violated at index 167 on node 5 | - | `./gradlew chaos -Pruns=1 -PstartSeed=37 -Plabel=mutation-accept-misattributed-replies -Pverbose` |
| 39 | INTERNAL_FAILURE | java.lang.AssertionError: Election Safety violated: nodes 4 and 2 were both leader in term 26 | - | `./gradlew chaos -Pruns=1 -PstartSeed=39 -Plabel=mutation-accept-misattributed-replies -Pverbose` |
| 41 | INTERNAL_FAILURE | java.lang.AssertionError: State Machine Safety violated at index 169 on node 2 | - | `./gradlew chaos -Pruns=1 -PstartSeed=41 -Plabel=mutation-accept-misattributed-replies -Pverbose` |
| 45 | INTERNAL_FAILURE | java.lang.AssertionError: Election Safety violated: nodes 4 and 3 were both leader in term 83 | - | `./gradlew chaos -Pruns=1 -PstartSeed=45 -Plabel=mutation-accept-misattributed-replies -Pverbose` |
| 48 | INTERNAL_FAILURE | java.lang.IllegalStateException: node 4 asked to overwrite committed entry 58 (commitIndex 58) by leader 5 of term 27 | - | `./gradlew chaos -Pruns=1 -PstartSeed=48 -Plabel=mutation-accept-misattributed-replies -Pverbose` |
| 51 | INTERNAL_FAILURE | java.lang.AssertionError: State Machine Safety violated at index 83 on node 4 | - | `./gradlew chaos -Pruns=1 -PstartSeed=51 -Plabel=mutation-accept-misattributed-replies -Pverbose` |
| 55 | INTERNAL_FAILURE | java.lang.AssertionError: State Machine Safety violated at index 101 on node 2 | - | `./gradlew chaos -Pruns=1 -PstartSeed=55 -Plabel=mutation-accept-misattributed-replies -Pverbose` |
| 56 | INTERNAL_FAILURE | java.lang.AssertionError: Election Safety violated: nodes 3 and 1 were both leader in term 50 | - | `./gradlew chaos -Pruns=1 -PstartSeed=56 -Plabel=mutation-accept-misattributed-replies -Pverbose` |
| 59 | INTERNAL_FAILURE | java.lang.AssertionError: State Machine Safety violated at index 167 on node 4 | - | `./gradlew chaos -Pruns=1 -PstartSeed=59 -Plabel=mutation-accept-misattributed-replies -Pverbose` |
| 60 | INTERNAL_FAILURE | java.lang.AssertionError: Election Safety violated: nodes 1 and 4 were both leader in term 41 | - | `./gradlew chaos -Pruns=1 -PstartSeed=60 -Plabel=mutation-accept-misattributed-replies -Pverbose` |
| 63 | INTERNAL_FAILURE | java.lang.AssertionError: State Machine Safety violated at index 165 on node 2 | - | `./gradlew chaos -Pruns=1 -PstartSeed=63 -Plabel=mutation-accept-misattributed-replies -Pverbose` |
| 64 | INTERNAL_FAILURE | java.lang.AssertionError: State Machine Safety violated at index 49 on node 1 | - | `./gradlew chaos -Pruns=1 -PstartSeed=64 -Plabel=mutation-accept-misattributed-replies -Pverbose` |
| 65 | INTERNAL_FAILURE | java.lang.IllegalStateException: node 1 asked to overwrite committed entry 131 (commitIndex 131) by leader 2 of term 86 | - | `./gradlew chaos -Pruns=1 -PstartSeed=65 -Plabel=mutation-accept-misattributed-replies -Pverbose` |
| 68 | INTERNAL_FAILURE | java.lang.AssertionError: State Machine Safety violated at index 56 on node 3 | - | `./gradlew chaos -Pruns=1 -PstartSeed=68 -Plabel=mutation-accept-misattributed-replies -Pverbose` |
| 75 | INTERNAL_FAILURE | java.lang.AssertionError: Election Safety violated: nodes 4 and 1 were both leader in term 79 | - | `./gradlew chaos -Pruns=1 -PstartSeed=75 -Plabel=mutation-accept-misattributed-replies -Pverbose` |
| 78 | INTERNAL_FAILURE | java.lang.AssertionError: State Machine Safety violated at index 31 on node 4 | - | `./gradlew chaos -Pruns=1 -PstartSeed=78 -Plabel=mutation-accept-misattributed-replies -Pverbose` |
| 80 | INTERNAL_FAILURE | java.lang.AssertionError: State Machine Safety violated at index 75 on node 3 | - | `./gradlew chaos -Pruns=1 -PstartSeed=80 -Plabel=mutation-accept-misattributed-replies -Pverbose` |
| 83 | INTERNAL_FAILURE | java.lang.IllegalStateException: node 5 asked to overwrite committed entry 138 (commitIndex 138) by leader 4 of term 61 | - | `./gradlew chaos -Pruns=1 -PstartSeed=83 -Plabel=mutation-accept-misattributed-replies -Pverbose` |
| 86 | INTERNAL_FAILURE | java.lang.AssertionError: Election Safety violated: nodes 5 and 3 were both leader in term 53 | - | `./gradlew chaos -Pruns=1 -PstartSeed=86 -Plabel=mutation-accept-misattributed-replies -Pverbose` |
| 89 | INTERNAL_FAILURE | java.lang.AssertionError: State Machine Safety violated at index 7 on node 5 | - | `./gradlew chaos -Pruns=1 -PstartSeed=89 -Plabel=mutation-accept-misattributed-replies -Pverbose` |
| 94 | INTERNAL_FAILURE | java.lang.AssertionError: Election Safety violated: nodes 2 and 1 were both leader in term 3 | - | `./gradlew chaos -Pruns=1 -PstartSeed=94 -Plabel=mutation-accept-misattributed-replies -Pverbose` |
| 97 | INTERNAL_FAILURE | java.lang.IllegalStateException: node 1 asked to overwrite committed entry 18 (commitIndex 19) by leader 5 of term 7 | - | `./gradlew chaos -Pruns=1 -PstartSeed=97 -Plabel=mutation-accept-misattributed-replies -Pverbose` |
| 99 | INTERNAL_FAILURE | java.lang.AssertionError: Election Safety violated: nodes 5 and 3 were both leader in term 4 | - | `./gradlew chaos -Pruns=1 -PstartSeed=99 -Plabel=mutation-accept-misattributed-replies -Pverbose` |
| 101 | INTERNAL_FAILURE | java.lang.IllegalStateException: node 5 asked to overwrite committed entry 69 (commitIndex 70) by leader 1 of term 29 | - | `./gradlew chaos -Pruns=1 -PstartSeed=101 -Plabel=mutation-accept-misattributed-replies -Pverbose` |
| 102 | INTERNAL_FAILURE | java.lang.AssertionError: Election Safety violated: nodes 5 and 3 were both leader in term 2 | - | `./gradlew chaos -Pruns=1 -PstartSeed=102 -Plabel=mutation-accept-misattributed-replies -Pverbose` |
| 104 | INTERNAL_FAILURE | java.lang.AssertionError: State Machine Safety violated at index 13 on node 2 | - | `./gradlew chaos -Pruns=1 -PstartSeed=104 -Plabel=mutation-accept-misattributed-replies -Pverbose` |
| 105 | INTERNAL_FAILURE | java.lang.AssertionError: Election Safety violated: nodes 2 and 3 were both leader in term 63 | - | `./gradlew chaos -Pruns=1 -PstartSeed=105 -Plabel=mutation-accept-misattributed-replies -Pverbose` |
| 106 | INTERNAL_FAILURE | java.lang.AssertionError: Election Safety violated: nodes 3 and 1 were both leader in term 5 | - | `./gradlew chaos -Pruns=1 -PstartSeed=106 -Plabel=mutation-accept-misattributed-replies -Pverbose` |
| 107 | INTERNAL_FAILURE | java.lang.IllegalStateException: node 4 asked to overwrite committed entry 84 (commitIndex 84) by leader 1 of term 32 | - | `./gradlew chaos -Pruns=1 -PstartSeed=107 -Plabel=mutation-accept-misattributed-replies -Pverbose` |
| 108 | INTERNAL_FAILURE | java.lang.IllegalStateException: node 1 asked to overwrite committed entry 47 (commitIndex 48) by leader 5 of term 29 | - | `./gradlew chaos -Pruns=1 -PstartSeed=108 -Plabel=mutation-accept-misattributed-replies -Pverbose` |
| 114 | INTERNAL_FAILURE | java.lang.IllegalStateException: node 1 asked to overwrite committed entry 67 (commitIndex 67) by leader 4 of term 30 | - | `./gradlew chaos -Pruns=1 -PstartSeed=114 -Plabel=mutation-accept-misattributed-replies -Pverbose` |
| 116 | INTERNAL_FAILURE | java.lang.IllegalStateException: node 4 asked to overwrite committed entry 154 (commitIndex 155) by leader 2 of term 59 | - | `./gradlew chaos -Pruns=1 -PstartSeed=116 -Plabel=mutation-accept-misattributed-replies -Pverbose` |
| 117 | INTERNAL_FAILURE | java.lang.AssertionError: State Machine Safety violated at index 79 on node 3 | - | `./gradlew chaos -Pruns=1 -PstartSeed=117 -Plabel=mutation-accept-misattributed-replies -Pverbose` |
| 118 | INTERNAL_FAILURE | java.lang.AssertionError: State Machine Safety violated at index 176 on node 1 | - | `./gradlew chaos -Pruns=1 -PstartSeed=118 -Plabel=mutation-accept-misattributed-replies -Pverbose` |
| 119 | INTERNAL_FAILURE | java.lang.IllegalStateException: node 2 asked to overwrite committed entry 43 (commitIndex 44) by leader 3 of term 15 | - | `./gradlew chaos -Pruns=1 -PstartSeed=119 -Plabel=mutation-accept-misattributed-replies -Pverbose` |
| 120 | INTERNAL_FAILURE | java.lang.AssertionError: Election Safety violated: nodes 3 and 1 were both leader in term 12 | - | `./gradlew chaos -Pruns=1 -PstartSeed=120 -Plabel=mutation-accept-misattributed-replies -Pverbose` |
| 122 | INTERNAL_FAILURE | java.lang.AssertionError: Election Safety violated: nodes 3 and 1 were both leader in term 5 | - | `./gradlew chaos -Pruns=1 -PstartSeed=122 -Plabel=mutation-accept-misattributed-replies -Pverbose` |
| 130 | INTERNAL_FAILURE | java.lang.AssertionError: State Machine Safety violated at index 171 on node 1 | - | `./gradlew chaos -Pruns=1 -PstartSeed=130 -Plabel=mutation-accept-misattributed-replies -Pverbose` |
| 131 | INTERNAL_FAILURE | java.lang.AssertionError: State Machine Safety violated at index 103 on node 2 | - | `./gradlew chaos -Pruns=1 -PstartSeed=131 -Plabel=mutation-accept-misattributed-replies -Pverbose` |
| 132 | INTERNAL_FAILURE | java.lang.AssertionError: State Machine Safety violated at index 90 on node 5 | - | `./gradlew chaos -Pruns=1 -PstartSeed=132 -Plabel=mutation-accept-misattributed-replies -Pverbose` |
| 135 | INTERNAL_FAILURE | java.lang.IllegalStateException: node 3 asked to overwrite committed entry 43 (commitIndex 43) by leader 2 of term 8 | - | `./gradlew chaos -Pruns=1 -PstartSeed=135 -Plabel=mutation-accept-misattributed-replies -Pverbose` |
| 145 | INTERNAL_FAILURE | java.lang.AssertionError: Election Safety violated: nodes 1 and 5 were both leader in term 58 | - | `./gradlew chaos -Pruns=1 -PstartSeed=145 -Plabel=mutation-accept-misattributed-replies -Pverbose` |
| 146 | INTERNAL_FAILURE | java.lang.AssertionError: State Machine Safety violated at index 84 on node 4 | - | `./gradlew chaos -Pruns=1 -PstartSeed=146 -Plabel=mutation-accept-misattributed-replies -Pverbose` |
| 147 | INTERNAL_FAILURE | java.lang.IllegalStateException: node 5 asked to overwrite committed entry 184 (commitIndex 186) by leader 1 of term 69 | - | `./gradlew chaos -Pruns=1 -PstartSeed=147 -Plabel=mutation-accept-misattributed-replies -Pverbose` |
| 148 | INTERNAL_FAILURE | java.lang.AssertionError: Election Safety violated: nodes 4 and 3 were both leader in term 28 | - | `./gradlew chaos -Pruns=1 -PstartSeed=148 -Plabel=mutation-accept-misattributed-replies -Pverbose` |
| 151 | INTERNAL_FAILURE | java.lang.IllegalStateException: node 3 asked to overwrite committed entry 172 (commitIndex 172) by leader 4 of term 88 | - | `./gradlew chaos -Pruns=1 -PstartSeed=151 -Plabel=mutation-accept-misattributed-replies -Pverbose` |
| 153 | INTERNAL_FAILURE | java.lang.IllegalStateException: node 1 asked to overwrite committed entry 32 (commitIndex 35) by leader 2 of term 3 | - | `./gradlew chaos -Pruns=1 -PstartSeed=153 -Plabel=mutation-accept-misattributed-replies -Pverbose` |
| 155 | INTERNAL_FAILURE | java.lang.AssertionError: Election Safety violated: nodes 1 and 5 were both leader in term 34 | - | `./gradlew chaos -Pruns=1 -PstartSeed=155 -Plabel=mutation-accept-misattributed-replies -Pverbose` |
| 156 | INTERNAL_FAILURE | java.lang.AssertionError: Election Safety violated: nodes 1 and 5 were both leader in term 57 | - | `./gradlew chaos -Pruns=1 -PstartSeed=156 -Plabel=mutation-accept-misattributed-replies -Pverbose` |
| 165 | INTERNAL_FAILURE | java.lang.AssertionError: State Machine Safety violated at index 23 on node 4 | - | `./gradlew chaos -Pruns=1 -PstartSeed=165 -Plabel=mutation-accept-misattributed-replies -Pverbose` |
| 172 | INTERNAL_FAILURE | java.lang.IllegalStateException: node 4 asked to overwrite committed entry 145 (commitIndex 146) by leader 2 of term 51 | - | `./gradlew chaos -Pruns=1 -PstartSeed=172 -Plabel=mutation-accept-misattributed-replies -Pverbose` |
| 176 | INTERNAL_FAILURE | java.lang.AssertionError: Election Safety violated: nodes 4 and 2 were both leader in term 20 | - | `./gradlew chaos -Pruns=1 -PstartSeed=176 -Plabel=mutation-accept-misattributed-replies -Pverbose` |
| 178 | INTERNAL_FAILURE | java.lang.AssertionError: Election Safety violated: nodes 5 and 2 were both leader in term 62 | - | `./gradlew chaos -Pruns=1 -PstartSeed=178 -Plabel=mutation-accept-misattributed-replies -Pverbose` |
| 181 | INTERNAL_FAILURE | java.lang.AssertionError: State Machine Safety violated at index 113 on node 2 | - | `./gradlew chaos -Pruns=1 -PstartSeed=181 -Plabel=mutation-accept-misattributed-replies -Pverbose` |
| 185 | INTERNAL_FAILURE | java.lang.AssertionError: Election Safety violated: nodes 2 and 1 were both leader in term 23 | - | `./gradlew chaos -Pruns=1 -PstartSeed=185 -Plabel=mutation-accept-misattributed-replies -Pverbose` |
| 190 | INTERNAL_FAILURE | java.lang.IllegalStateException: node 2 asked to overwrite committed entry 174 (commitIndex 174) by leader 4 of term 80 | - | `./gradlew chaos -Pruns=1 -PstartSeed=190 -Plabel=mutation-accept-misattributed-replies -Pverbose` |
| 193 | INTERNAL_FAILURE | java.lang.IllegalStateException: node 2 asked to overwrite committed entry 115 (commitIndex 115) by leader 3 of term 27 | - | `./gradlew chaos -Pruns=1 -PstartSeed=193 -Plabel=mutation-accept-misattributed-replies -Pverbose` |
| 196 | INTERNAL_FAILURE | java.lang.AssertionError: Election Safety violated: nodes 3 and 2 were both leader in term 20 | - | `./gradlew chaos -Pruns=1 -PstartSeed=196 -Plabel=mutation-accept-misattributed-replies -Pverbose` |
| 201 | INTERNAL_FAILURE | java.lang.AssertionError: Election Safety violated: nodes 1 and 5 were both leader in term 34 | - | `./gradlew chaos -Pruns=1 -PstartSeed=201 -Plabel=mutation-accept-misattributed-replies -Pverbose` |
| 203 | INTERNAL_FAILURE | java.lang.AssertionError: Election Safety violated: nodes 4 and 3 were both leader in term 28 | - | `./gradlew chaos -Pruns=1 -PstartSeed=203 -Plabel=mutation-accept-misattributed-replies -Pverbose` |
| 206 | INTERNAL_FAILURE | java.lang.AssertionError: State Machine Safety violated at index 163 on node 4 | - | `./gradlew chaos -Pruns=1 -PstartSeed=206 -Plabel=mutation-accept-misattributed-replies -Pverbose` |
| 207 | INTERNAL_FAILURE | java.lang.IllegalStateException: node 4 asked to overwrite committed entry 73 (commitIndex 73) by leader 3 of term 31 | - | `./gradlew chaos -Pruns=1 -PstartSeed=207 -Plabel=mutation-accept-misattributed-replies -Pverbose` |
| 208 | INTERNAL_FAILURE | java.lang.AssertionError: Election Safety violated: nodes 2 and 1 were both leader in term 78 | - | `./gradlew chaos -Pruns=1 -PstartSeed=208 -Plabel=mutation-accept-misattributed-replies -Pverbose` |
| 212 | INTERNAL_FAILURE | java.lang.AssertionError: Election Safety violated: nodes 5 and 3 were both leader in term 52 | - | `./gradlew chaos -Pruns=1 -PstartSeed=212 -Plabel=mutation-accept-misattributed-replies -Pverbose` |
| 215 | INTERNAL_FAILURE | java.lang.AssertionError: Election Safety violated: nodes 1 and 5 were both leader in term 82 | - | `./gradlew chaos -Pruns=1 -PstartSeed=215 -Plabel=mutation-accept-misattributed-replies -Pverbose` |
| 217 | INTERNAL_FAILURE | java.lang.AssertionError: Election Safety violated: nodes 3 and 5 were both leader in term 53 | - | `./gradlew chaos -Pruns=1 -PstartSeed=217 -Plabel=mutation-accept-misattributed-replies -Pverbose` |
| 218 | INTERNAL_FAILURE | java.lang.IllegalStateException: node 5 asked to overwrite committed entry 109 (commitIndex 110) by leader 1 of term 48 | - | `./gradlew chaos -Pruns=1 -PstartSeed=218 -Plabel=mutation-accept-misattributed-replies -Pverbose` |
| 219 | INTERNAL_FAILURE | java.lang.AssertionError: Election Safety violated: nodes 1 and 5 were both leader in term 67 | - | `./gradlew chaos -Pruns=1 -PstartSeed=219 -Plabel=mutation-accept-misattributed-replies -Pverbose` |
| 220 | INTERNAL_FAILURE | java.lang.AssertionError: Election Safety violated: nodes 4 and 3 were both leader in term 86 | - | `./gradlew chaos -Pruns=1 -PstartSeed=220 -Plabel=mutation-accept-misattributed-replies -Pverbose` |
| 223 | INTERNAL_FAILURE | java.lang.IllegalStateException: node 2 asked to overwrite committed entry 112 (commitIndex 112) by leader 4 of term 48 | - | `./gradlew chaos -Pruns=1 -PstartSeed=223 -Plabel=mutation-accept-misattributed-replies -Pverbose` |
| 224 | INTERNAL_FAILURE | java.lang.AssertionError: Election Safety violated: nodes 1 and 2 were both leader in term 17 | - | `./gradlew chaos -Pruns=1 -PstartSeed=224 -Plabel=mutation-accept-misattributed-replies -Pverbose` |
| 226 | INTERNAL_FAILURE | java.lang.AssertionError: Election Safety violated: nodes 2 and 5 were both leader in term 53 | - | `./gradlew chaos -Pruns=1 -PstartSeed=226 -Plabel=mutation-accept-misattributed-replies -Pverbose` |
| 227 | INTERNAL_FAILURE | java.lang.IllegalStateException: node 5 asked to overwrite committed entry 84 (commitIndex 85) by leader 4 of term 42 | - | `./gradlew chaos -Pruns=1 -PstartSeed=227 -Plabel=mutation-accept-misattributed-replies -Pverbose` |
| 228 | INTERNAL_FAILURE | java.lang.AssertionError: State Machine Safety violated at index 24 on node 2 | - | `./gradlew chaos -Pruns=1 -PstartSeed=228 -Plabel=mutation-accept-misattributed-replies -Pverbose` |
| 240 | INTERNAL_FAILURE | java.lang.IllegalStateException: node 5 asked to overwrite committed entry 52 (commitIndex 55) by leader 2 of term 24 | - | `./gradlew chaos -Pruns=1 -PstartSeed=240 -Plabel=mutation-accept-misattributed-replies -Pverbose` |
| 242 | INTERNAL_FAILURE | java.lang.AssertionError: State Machine Safety violated at index 49 on node 5 | - | `./gradlew chaos -Pruns=1 -PstartSeed=242 -Plabel=mutation-accept-misattributed-replies -Pverbose` |
| 244 | INTERNAL_FAILURE | java.lang.AssertionError: State Machine Safety violated at index 159 on node 1 | - | `./gradlew chaos -Pruns=1 -PstartSeed=244 -Plabel=mutation-accept-misattributed-replies -Pverbose` |
| 245 | INTERNAL_FAILURE | java.lang.AssertionError: State Machine Safety violated at index 165 on node 2 | - | `./gradlew chaos -Pruns=1 -PstartSeed=245 -Plabel=mutation-accept-misattributed-replies -Pverbose` |
| 249 | INTERNAL_FAILURE | java.lang.AssertionError: State Machine Safety violated at index 119 on node 1 | - | `./gradlew chaos -Pruns=1 -PstartSeed=249 -Plabel=mutation-accept-misattributed-replies -Pverbose` |
| 252 | INTERNAL_FAILURE | java.lang.AssertionError: State Machine Safety violated at index 10 on node 3 | - | `./gradlew chaos -Pruns=1 -PstartSeed=252 -Plabel=mutation-accept-misattributed-replies -Pverbose` |
| 257 | INTERNAL_FAILURE | java.lang.IllegalStateException: node 3 asked to overwrite committed entry 68 (commitIndex 68) by leader 4 of term 25 | - | `./gradlew chaos -Pruns=1 -PstartSeed=257 -Plabel=mutation-accept-misattributed-replies -Pverbose` |
| 263 | INTERNAL_FAILURE | java.lang.AssertionError: State Machine Safety violated at index 114 on node 3 | - | `./gradlew chaos -Pruns=1 -PstartSeed=263 -Plabel=mutation-accept-misattributed-replies -Pverbose` |
| 265 | INTERNAL_FAILURE | java.lang.AssertionError: State Machine Safety violated at index 166 on node 5 | - | `./gradlew chaos -Pruns=1 -PstartSeed=265 -Plabel=mutation-accept-misattributed-replies -Pverbose` |
| 271 | INTERNAL_FAILURE | java.lang.AssertionError: State Machine Safety violated at index 193 on node 2 | - | `./gradlew chaos -Pruns=1 -PstartSeed=271 -Plabel=mutation-accept-misattributed-replies -Pverbose` |
| 272 | INTERNAL_FAILURE | java.lang.AssertionError: Election Safety violated: nodes 1 and 2 were both leader in term 21 | - | `./gradlew chaos -Pruns=1 -PstartSeed=272 -Plabel=mutation-accept-misattributed-replies -Pverbose` |
| 273 | INTERNAL_FAILURE | java.lang.AssertionError: Election Safety violated: nodes 4 and 1 were both leader in term 19 | - | `./gradlew chaos -Pruns=1 -PstartSeed=273 -Plabel=mutation-accept-misattributed-replies -Pverbose` |
| 274 | INTERNAL_FAILURE | java.lang.AssertionError: State Machine Safety violated at index 7 on node 4 | - | `./gradlew chaos -Pruns=1 -PstartSeed=274 -Plabel=mutation-accept-misattributed-replies -Pverbose` |
| 275 | INTERNAL_FAILURE | java.lang.AssertionError: Election Safety violated: nodes 2 and 1 were both leader in term 24 | - | `./gradlew chaos -Pruns=1 -PstartSeed=275 -Plabel=mutation-accept-misattributed-replies -Pverbose` |
| 280 | INTERNAL_FAILURE | java.lang.AssertionError: State Machine Safety violated at index 5 on node 3 | - | `./gradlew chaos -Pruns=1 -PstartSeed=280 -Plabel=mutation-accept-misattributed-replies -Pverbose` |
| 283 | INTERNAL_FAILURE | java.lang.IllegalStateException: node 4 asked to overwrite committed entry 151 (commitIndex 152) by leader 5 of term 47 | - | `./gradlew chaos -Pruns=1 -PstartSeed=283 -Plabel=mutation-accept-misattributed-replies -Pverbose` |
| 284 | INTERNAL_FAILURE | java.lang.IllegalStateException: node 3 asked to overwrite committed entry 75 (commitIndex 77) by leader 1 of term 19 | - | `./gradlew chaos -Pruns=1 -PstartSeed=284 -Plabel=mutation-accept-misattributed-replies -Pverbose` |
| 289 | INTERNAL_FAILURE | java.lang.AssertionError: State Machine Safety violated at index 17 on node 2 | - | `./gradlew chaos -Pruns=1 -PstartSeed=289 -Plabel=mutation-accept-misattributed-replies -Pverbose` |
| 291 | INTERNAL_FAILURE | java.lang.AssertionError: Election Safety violated: nodes 4 and 3 were both leader in term 44 | - | `./gradlew chaos -Pruns=1 -PstartSeed=291 -Plabel=mutation-accept-misattributed-replies -Pverbose` |
| 293 | INTERNAL_FAILURE | java.lang.AssertionError: Election Safety violated: nodes 5 and 3 were both leader in term 35 | - | `./gradlew chaos -Pruns=1 -PstartSeed=293 -Plabel=mutation-accept-misattributed-replies -Pverbose` |
| 295 | INTERNAL_FAILURE | java.lang.AssertionError: State Machine Safety violated at index 170 on node 3 | - | `./gradlew chaos -Pruns=1 -PstartSeed=295 -Plabel=mutation-accept-misattributed-replies -Pverbose` |
| 296 | INTERNAL_FAILURE | java.lang.AssertionError: Election Safety violated: nodes 1 and 5 were both leader in term 99 | - | `./gradlew chaos -Pruns=1 -PstartSeed=296 -Plabel=mutation-accept-misattributed-replies -Pverbose` |
| 297 | INTERNAL_FAILURE | java.lang.AssertionError: Election Safety violated: nodes 5 and 2 were both leader in term 51 | - | `./gradlew chaos -Pruns=1 -PstartSeed=297 -Plabel=mutation-accept-misattributed-replies -Pverbose` |
| 298 | INTERNAL_FAILURE | java.lang.IllegalStateException: node 2 asked to overwrite committed entry 46 (commitIndex 48) by leader 1 of term 20 | - | `./gradlew chaos -Pruns=1 -PstartSeed=298 -Plabel=mutation-accept-misattributed-replies -Pverbose` |
| 299 | INTERNAL_FAILURE | java.lang.IllegalStateException: node 4 asked to overwrite committed entry 136 (commitIndex 139) by leader 2 of term 60 | - | `./gradlew chaos -Pruns=1 -PstartSeed=299 -Plabel=mutation-accept-misattributed-replies -Pverbose` |
| 302 | INTERNAL_FAILURE | java.lang.IllegalStateException: node 2 asked to overwrite committed entry 4 (commitIndex 4) by leader 5 of term 4 | - | `./gradlew chaos -Pruns=1 -PstartSeed=302 -Plabel=mutation-accept-misattributed-replies -Pverbose` |
| 303 | INTERNAL_FAILURE | java.lang.AssertionError: Election Safety violated: nodes 4 and 1 were both leader in term 32 | - | `./gradlew chaos -Pruns=1 -PstartSeed=303 -Plabel=mutation-accept-misattributed-replies -Pverbose` |
| 304 | INTERNAL_FAILURE | java.lang.AssertionError: Election Safety violated: nodes 2 and 4 were both leader in term 30 | - | `./gradlew chaos -Pruns=1 -PstartSeed=304 -Plabel=mutation-accept-misattributed-replies -Pverbose` |
| 305 | INTERNAL_FAILURE | java.lang.AssertionError: State Machine Safety violated at index 74 on node 5 | - | `./gradlew chaos -Pruns=1 -PstartSeed=305 -Plabel=mutation-accept-misattributed-replies -Pverbose` |
| 311 | INTERNAL_FAILURE | java.lang.AssertionError: Election Safety violated: nodes 2 and 1 were both leader in term 17 | - | `./gradlew chaos -Pruns=1 -PstartSeed=311 -Plabel=mutation-accept-misattributed-replies -Pverbose` |
| 313 | INTERNAL_FAILURE | java.lang.AssertionError: Election Safety violated: nodes 1 and 2 were both leader in term 3 | - | `./gradlew chaos -Pruns=1 -PstartSeed=313 -Plabel=mutation-accept-misattributed-replies -Pverbose` |
| 314 | INTERNAL_FAILURE | java.lang.AssertionError: Election Safety violated: nodes 3 and 5 were both leader in term 66 | - | `./gradlew chaos -Pruns=1 -PstartSeed=314 -Plabel=mutation-accept-misattributed-replies -Pverbose` |
| 316 | INTERNAL_FAILURE | java.lang.AssertionError: Election Safety violated: nodes 3 and 1 were both leader in term 5 | - | `./gradlew chaos -Pruns=1 -PstartSeed=316 -Plabel=mutation-accept-misattributed-replies -Pverbose` |
| 318 | INTERNAL_FAILURE | java.lang.AssertionError: State Machine Safety violated at index 156 on node 3 | - | `./gradlew chaos -Pruns=1 -PstartSeed=318 -Plabel=mutation-accept-misattributed-replies -Pverbose` |
| 319 | INTERNAL_FAILURE | java.lang.IllegalStateException: node 3 asked to overwrite committed entry 59 (commitIndex 59) by leader 1 of term 13 | - | `./gradlew chaos -Pruns=1 -PstartSeed=319 -Plabel=mutation-accept-misattributed-replies -Pverbose` |
| 324 | INTERNAL_FAILURE | java.lang.AssertionError: Election Safety violated: nodes 2 and 4 were both leader in term 38 | - | `./gradlew chaos -Pruns=1 -PstartSeed=324 -Plabel=mutation-accept-misattributed-replies -Pverbose` |
| 325 | INTERNAL_FAILURE | java.lang.IllegalStateException: node 4 asked to overwrite committed entry 37 (commitIndex 38) by leader 5 of term 11 | - | `./gradlew chaos -Pruns=1 -PstartSeed=325 -Plabel=mutation-accept-misattributed-replies -Pverbose` |
| 328 | INTERNAL_FAILURE | java.lang.AssertionError: State Machine Safety violated at index 156 on node 5 | - | `./gradlew chaos -Pruns=1 -PstartSeed=328 -Plabel=mutation-accept-misattributed-replies -Pverbose` |
| 329 | INTERNAL_FAILURE | java.lang.IllegalStateException: node 1 asked to overwrite committed entry 17 (commitIndex 19) by leader 3 of term 2 | - | `./gradlew chaos -Pruns=1 -PstartSeed=329 -Plabel=mutation-accept-misattributed-replies -Pverbose` |
| 330 | INTERNAL_FAILURE | java.lang.AssertionError: State Machine Safety violated at index 190 on node 4 | - | `./gradlew chaos -Pruns=1 -PstartSeed=330 -Plabel=mutation-accept-misattributed-replies -Pverbose` |
| 331 | INTERNAL_FAILURE | java.lang.IllegalStateException: node 2 asked to overwrite committed entry 35 (commitIndex 36) by leader 3 of term 18 | - | `./gradlew chaos -Pruns=1 -PstartSeed=331 -Plabel=mutation-accept-misattributed-replies -Pverbose` |
| 332 | INTERNAL_FAILURE | java.lang.AssertionError: Election Safety violated: nodes 1 and 3 were both leader in term 22 | - | `./gradlew chaos -Pruns=1 -PstartSeed=332 -Plabel=mutation-accept-misattributed-replies -Pverbose` |
| 335 | INTERNAL_FAILURE | java.lang.AssertionError: Election Safety violated: nodes 4 and 2 were both leader in term 39 | - | `./gradlew chaos -Pruns=1 -PstartSeed=335 -Plabel=mutation-accept-misattributed-replies -Pverbose` |
| 336 | INTERNAL_FAILURE | java.lang.AssertionError: Election Safety violated: nodes 1 and 4 were both leader in term 4 | - | `./gradlew chaos -Pruns=1 -PstartSeed=336 -Plabel=mutation-accept-misattributed-replies -Pverbose` |
| 338 | INTERNAL_FAILURE | java.lang.IllegalStateException: node 3 asked to overwrite committed entry 55 (commitIndex 59) by leader 5 of term 38 | - | `./gradlew chaos -Pruns=1 -PstartSeed=338 -Plabel=mutation-accept-misattributed-replies -Pverbose` |
| 340 | INTERNAL_FAILURE | java.lang.AssertionError: State Machine Safety violated at index 12 on node 2 | - | `./gradlew chaos -Pruns=1 -PstartSeed=340 -Plabel=mutation-accept-misattributed-replies -Pverbose` |
| 346 | INTERNAL_FAILURE | java.lang.AssertionError: Election Safety violated: nodes 5 and 3 were both leader in term 4 | - | `./gradlew chaos -Pruns=1 -PstartSeed=346 -Plabel=mutation-accept-misattributed-replies -Pverbose` |
| 347 | INTERNAL_FAILURE | java.lang.AssertionError: Election Safety violated: nodes 5 and 2 were both leader in term 16 | - | `./gradlew chaos -Pruns=1 -PstartSeed=347 -Plabel=mutation-accept-misattributed-replies -Pverbose` |
| 348 | INTERNAL_FAILURE | java.lang.IllegalStateException: node 3 asked to overwrite committed entry 81 (commitIndex 81) by leader 2 of term 26 | - | `./gradlew chaos -Pruns=1 -PstartSeed=348 -Plabel=mutation-accept-misattributed-replies -Pverbose` |
| 350 | INTERNAL_FAILURE | java.lang.AssertionError: Election Safety violated: nodes 3 and 2 were both leader in term 6 | - | `./gradlew chaos -Pruns=1 -PstartSeed=350 -Plabel=mutation-accept-misattributed-replies -Pverbose` |
| 351 | INTERNAL_FAILURE | java.lang.IllegalStateException: node 1 asked to overwrite committed entry 41 (commitIndex 41) by leader 2 of term 32 | - | `./gradlew chaos -Pruns=1 -PstartSeed=351 -Plabel=mutation-accept-misattributed-replies -Pverbose` |
| 353 | INTERNAL_FAILURE | java.lang.AssertionError: Election Safety violated: nodes 2 and 5 were both leader in term 88 | - | `./gradlew chaos -Pruns=1 -PstartSeed=353 -Plabel=mutation-accept-misattributed-replies -Pverbose` |
| 358 | INTERNAL_FAILURE | java.lang.IllegalStateException: node 5 asked to overwrite committed entry 107 (commitIndex 108) by leader 2 of term 32 | - | `./gradlew chaos -Pruns=1 -PstartSeed=358 -Plabel=mutation-accept-misattributed-replies -Pverbose` |
| 369 | INTERNAL_FAILURE | java.lang.AssertionError: Election Safety violated: nodes 5 and 4 were both leader in term 59 | - | `./gradlew chaos -Pruns=1 -PstartSeed=369 -Plabel=mutation-accept-misattributed-replies -Pverbose` |
| 371 | INTERNAL_FAILURE | java.lang.AssertionError: Election Safety violated: nodes 3 and 4 were both leader in term 44 | - | `./gradlew chaos -Pruns=1 -PstartSeed=371 -Plabel=mutation-accept-misattributed-replies -Pverbose` |
| 372 | INTERNAL_FAILURE | java.lang.AssertionError: State Machine Safety violated at index 146 on node 3 | - | `./gradlew chaos -Pruns=1 -PstartSeed=372 -Plabel=mutation-accept-misattributed-replies -Pverbose` |
| 373 | INTERNAL_FAILURE | java.lang.AssertionError: Election Safety violated: nodes 2 and 4 were both leader in term 27 | - | `./gradlew chaos -Pruns=1 -PstartSeed=373 -Plabel=mutation-accept-misattributed-replies -Pverbose` |
| 374 | INTERNAL_FAILURE | java.lang.AssertionError: Election Safety violated: nodes 5 and 1 were both leader in term 22 | - | `./gradlew chaos -Pruns=1 -PstartSeed=374 -Plabel=mutation-accept-misattributed-replies -Pverbose` |
| 385 | INTERNAL_FAILURE | java.lang.AssertionError: Election Safety violated: nodes 5 and 4 were both leader in term 55 | - | `./gradlew chaos -Pruns=1 -PstartSeed=385 -Plabel=mutation-accept-misattributed-replies -Pverbose` |
| 387 | INTERNAL_FAILURE | java.lang.AssertionError: Election Safety violated: nodes 4 and 3 were both leader in term 28 | - | `./gradlew chaos -Pruns=1 -PstartSeed=387 -Plabel=mutation-accept-misattributed-replies -Pverbose` |
| 389 | INTERNAL_FAILURE | java.lang.AssertionError: Election Safety violated: nodes 3 and 5 were both leader in term 48 | - | `./gradlew chaos -Pruns=1 -PstartSeed=389 -Plabel=mutation-accept-misattributed-replies -Pverbose` |
| 392 | INTERNAL_FAILURE | java.lang.AssertionError: State Machine Safety violated at index 36 on node 2 | - | `./gradlew chaos -Pruns=1 -PstartSeed=392 -Plabel=mutation-accept-misattributed-replies -Pverbose` |
| 397 | INTERNAL_FAILURE | java.lang.AssertionError: Election Safety violated: nodes 1 and 5 were both leader in term 4 | - | `./gradlew chaos -Pruns=1 -PstartSeed=397 -Plabel=mutation-accept-misattributed-replies -Pverbose` |
| 399 | INTERNAL_FAILURE | java.lang.AssertionError: Election Safety violated: nodes 5 and 2 were both leader in term 57 | - | `./gradlew chaos -Pruns=1 -PstartSeed=399 -Plabel=mutation-accept-misattributed-replies -Pverbose` |
| 401 | INTERNAL_FAILURE | java.lang.AssertionError: State Machine Safety violated at index 138 on node 5 | - | `./gradlew chaos -Pruns=1 -PstartSeed=401 -Plabel=mutation-accept-misattributed-replies -Pverbose` |
| 403 | INTERNAL_FAILURE | java.lang.AssertionError: Election Safety violated: nodes 1 and 2 were both leader in term 2 | - | `./gradlew chaos -Pruns=1 -PstartSeed=403 -Plabel=mutation-accept-misattributed-replies -Pverbose` |
| 412 | INTERNAL_FAILURE | java.lang.IllegalStateException: node 2 asked to overwrite committed entry 63 (commitIndex 63) by leader 1 of term 28 | - | `./gradlew chaos -Pruns=1 -PstartSeed=412 -Plabel=mutation-accept-misattributed-replies -Pverbose` |
| 413 | INTERNAL_FAILURE | java.lang.AssertionError: Election Safety violated: nodes 5 and 2 were both leader in term 54 | - | `./gradlew chaos -Pruns=1 -PstartSeed=413 -Plabel=mutation-accept-misattributed-replies -Pverbose` |
| 414 | INTERNAL_FAILURE | java.lang.AssertionError: State Machine Safety violated at index 198 on node 3 | - | `./gradlew chaos -Pruns=1 -PstartSeed=414 -Plabel=mutation-accept-misattributed-replies -Pverbose` |
| 416 | INTERNAL_FAILURE | java.lang.IllegalStateException: node 2 asked to overwrite committed entry 57 (commitIndex 59) by leader 4 of term 27 | - | `./gradlew chaos -Pruns=1 -PstartSeed=416 -Plabel=mutation-accept-misattributed-replies -Pverbose` |
| 417 | INTERNAL_FAILURE | java.lang.IllegalStateException: node 3 asked to overwrite committed entry 94 (commitIndex 94) by leader 4 of term 42 | - | `./gradlew chaos -Pruns=1 -PstartSeed=417 -Plabel=mutation-accept-misattributed-replies -Pverbose` |
| 421 | INTERNAL_FAILURE | java.lang.IllegalStateException: node 2 asked to overwrite committed entry 81 (commitIndex 93) by leader 3 of term 22 | - | `./gradlew chaos -Pruns=1 -PstartSeed=421 -Plabel=mutation-accept-misattributed-replies -Pverbose` |
| 425 | INTERNAL_FAILURE | java.lang.AssertionError: Election Safety violated: nodes 2 and 3 were both leader in term 67 | - | `./gradlew chaos -Pruns=1 -PstartSeed=425 -Plabel=mutation-accept-misattributed-replies -Pverbose` |
| 431 | INTERNAL_FAILURE | java.lang.AssertionError: Election Safety violated: nodes 1 and 3 were both leader in term 21 | - | `./gradlew chaos -Pruns=1 -PstartSeed=431 -Plabel=mutation-accept-misattributed-replies -Pverbose` |
| 437 | INTERNAL_FAILURE | java.lang.AssertionError: Election Safety violated: nodes 5 and 1 were both leader in term 54 | - | `./gradlew chaos -Pruns=1 -PstartSeed=437 -Plabel=mutation-accept-misattributed-replies -Pverbose` |
| 439 | INTERNAL_FAILURE | java.lang.AssertionError: State Machine Safety violated at index 62 on node 4 | - | `./gradlew chaos -Pruns=1 -PstartSeed=439 -Plabel=mutation-accept-misattributed-replies -Pverbose` |
| 440 | INTERNAL_FAILURE | java.lang.AssertionError: Election Safety violated: nodes 1 and 3 were both leader in term 2 | - | `./gradlew chaos -Pruns=1 -PstartSeed=440 -Plabel=mutation-accept-misattributed-replies -Pverbose` |
| 442 | INTERNAL_FAILURE | java.lang.IllegalStateException: node 1 asked to overwrite committed entry 126 (commitIndex 129) by leader 5 of term 27 | - | `./gradlew chaos -Pruns=1 -PstartSeed=442 -Plabel=mutation-accept-misattributed-replies -Pverbose` |
| 444 | INTERNAL_FAILURE | java.lang.AssertionError: Election Safety violated: nodes 1 and 3 were both leader in term 38 | - | `./gradlew chaos -Pruns=1 -PstartSeed=444 -Plabel=mutation-accept-misattributed-replies -Pverbose` |
| 449 | INTERNAL_FAILURE | java.lang.IllegalStateException: node 5 asked to overwrite committed entry 55 (commitIndex 55) by leader 2 of term 34 | - | `./gradlew chaos -Pruns=1 -PstartSeed=449 -Plabel=mutation-accept-misattributed-replies -Pverbose` |
| 454 | INTERNAL_FAILURE | java.lang.AssertionError: State Machine Safety violated at index 170 on node 1 | - | `./gradlew chaos -Pruns=1 -PstartSeed=454 -Plabel=mutation-accept-misattributed-replies -Pverbose` |
| 458 | INTERNAL_FAILURE | java.lang.AssertionError: State Machine Safety violated at index 192 on node 3 | - | `./gradlew chaos -Pruns=1 -PstartSeed=458 -Plabel=mutation-accept-misattributed-replies -Pverbose` |
| 459 | INTERNAL_FAILURE | java.lang.AssertionError: State Machine Safety violated at index 20 on node 3 | - | `./gradlew chaos -Pruns=1 -PstartSeed=459 -Plabel=mutation-accept-misattributed-replies -Pverbose` |
| 460 | INTERNAL_FAILURE | java.lang.AssertionError: Election Safety violated: nodes 4 and 1 were both leader in term 40 | - | `./gradlew chaos -Pruns=1 -PstartSeed=460 -Plabel=mutation-accept-misattributed-replies -Pverbose` |
| 462 | INTERNAL_FAILURE | java.lang.AssertionError: Election Safety violated: nodes 1 and 2 were both leader in term 45 | - | `./gradlew chaos -Pruns=1 -PstartSeed=462 -Plabel=mutation-accept-misattributed-replies -Pverbose` |
| 463 | INTERNAL_FAILURE | java.lang.AssertionError: State Machine Safety violated at index 13 on node 5 | - | `./gradlew chaos -Pruns=1 -PstartSeed=463 -Plabel=mutation-accept-misattributed-replies -Pverbose` |
| 464 | INTERNAL_FAILURE | java.lang.AssertionError: Election Safety violated: nodes 3 and 4 were both leader in term 31 | - | `./gradlew chaos -Pruns=1 -PstartSeed=464 -Plabel=mutation-accept-misattributed-replies -Pverbose` |
| 466 | INTERNAL_FAILURE | java.lang.AssertionError: Election Safety violated: nodes 2 and 1 were both leader in term 26 | - | `./gradlew chaos -Pruns=1 -PstartSeed=466 -Plabel=mutation-accept-misattributed-replies -Pverbose` |
| 471 | INTERNAL_FAILURE | java.lang.IllegalStateException: node 1 asked to overwrite committed entry 106 (commitIndex 108) by leader 3 of term 36 | - | `./gradlew chaos -Pruns=1 -PstartSeed=471 -Plabel=mutation-accept-misattributed-replies -Pverbose` |
| 473 | INTERNAL_FAILURE | java.lang.AssertionError: Election Safety violated: nodes 1 and 5 were both leader in term 22 | - | `./gradlew chaos -Pruns=1 -PstartSeed=473 -Plabel=mutation-accept-misattributed-replies -Pverbose` |
| 474 | INTERNAL_FAILURE | java.lang.AssertionError: State Machine Safety violated at index 45 on node 5 | - | `./gradlew chaos -Pruns=1 -PstartSeed=474 -Plabel=mutation-accept-misattributed-replies -Pverbose` |
| 484 | INTERNAL_FAILURE | java.lang.IllegalStateException: node 3 asked to overwrite committed entry 54 (commitIndex 54) by leader 1 of term 15 | - | `./gradlew chaos -Pruns=1 -PstartSeed=484 -Plabel=mutation-accept-misattributed-replies -Pverbose` |
| 487 | INTERNAL_FAILURE | java.lang.IllegalStateException: node 5 asked to overwrite committed entry 93 (commitIndex 93) by leader 4 of term 32 | - | `./gradlew chaos -Pruns=1 -PstartSeed=487 -Plabel=mutation-accept-misattributed-replies -Pverbose` |
| 489 | INTERNAL_FAILURE | java.lang.AssertionError: State Machine Safety violated at index 153 on node 1 | - | `./gradlew chaos -Pruns=1 -PstartSeed=489 -Plabel=mutation-accept-misattributed-replies -Pverbose` |
| 490 | INTERNAL_FAILURE | java.lang.AssertionError: Election Safety violated: nodes 5 and 2 were both leader in term 13 | - | `./gradlew chaos -Pruns=1 -PstartSeed=490 -Plabel=mutation-accept-misattributed-replies -Pverbose` |
| 492 | INTERNAL_FAILURE | java.lang.AssertionError: Election Safety violated: nodes 3 and 4 were both leader in term 13 | - | `./gradlew chaos -Pruns=1 -PstartSeed=492 -Plabel=mutation-accept-misattributed-replies -Pverbose` |
| 493 | INTERNAL_FAILURE | java.lang.AssertionError: Election Safety violated: nodes 5 and 1 were both leader in term 95 | - | `./gradlew chaos -Pruns=1 -PstartSeed=493 -Plabel=mutation-accept-misattributed-replies -Pverbose` |
| 494 | INTERNAL_FAILURE | java.lang.IllegalStateException: node 5 asked to overwrite committed entry 61 (commitIndex 66) by leader 1 of term 22 | - | `./gradlew chaos -Pruns=1 -PstartSeed=494 -Plabel=mutation-accept-misattributed-replies -Pverbose` |
| 497 | INTERNAL_FAILURE | java.lang.IllegalStateException: node 2 asked to overwrite committed entry 138 (commitIndex 138) by leader 5 of term 51 | - | `./gradlew chaos -Pruns=1 -PstartSeed=497 -Plabel=mutation-accept-misattributed-replies -Pverbose` |
| 498 | INTERNAL_FAILURE | java.lang.AssertionError: Election Safety violated: nodes 5 and 1 were both leader in term 11 | - | `./gradlew chaos -Pruns=1 -PstartSeed=498 -Plabel=mutation-accept-misattributed-replies -Pverbose` |

Per-seed numbers: `chaos-mutation-accept-misattributed-replies-500-seeds-d63b882-dirty-20261020T105041Z.csv`.
