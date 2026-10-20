# Final evidence for commit c23409c

All results below come from the same commit, `c23409c` ("Fix: Raft replies counted for the wrong
node"), on the same machine: GitHub Codespace, Linux 6.8.0-1064-azure amd64, 2 cores (AMD EPYC
7763), Java 21, Docker 29.9.1-1. These are Codespace numbers; benchmark numbers for the resume come
from the Oracle VM in Phase 7.

## 1. Raft gate

`./gradlew :raft:test -PraftSeeds=50`: **1,285 test runs, 0 failures** (every lab-style test
with 50 seeds, plus the unit, scenario, PreVote, ReadIndex and snapshot tests).

`./gradlew build`: **205 tests, 0 failures** (all modules, including the real-thread gRPC chaos
test).

## 2. Simulated chaos, 10,000 seeds

`./gradlew chaos -Pruns=10000` -> `reports/chaos/chaos-baseline-10000-seeds-c23409c-20261020T105232Z.md`

**10,000 / 10,000 LINEARIZABLE**, 0 NOT_LINEARIZABLE, 0 INCONCLUSIVE, 0 internal-check failures,
1 m 54 s. Faults in every run: crashes (power loss and mid-I/O), pauses, stale routes, partitions,
message loss, long delays. Snapshots every 20 entries in 64-byte InstallSnapshot chunks.

## 3. Docker chaos, five 180 s runs

`scripts/docker-chaos.sh 180` (5 containers, 384 MB each, `docker kill` / `pause` /
`network disconnect`, snapshot every 200 entries):

| Run | Report | Verdict | Operations | kill / pause / disconnect | Final term | Peak memory |
|---|---|---|---|---|---|---|
| 1 | `reports/chaos/docker-chaos-180s-c23409c-20261020T105538Z.md` | **LINEARIZABLE** | 10,968 | 15 / 22 / 18 | 24 | 134 MiB |
| 2 | `reports/chaos/docker-chaos-180s-c23409c-20261020T105918Z.md` | **LINEARIZABLE** | 5,148 | 20 / 15 / 21 | 19 | 125 MiB |
| 3 | `reports/chaos/docker-chaos-180s-c23409c-20261020T110254Z.md` | **LINEARIZABLE** | 9,115 | 17 / 22 / 18 | 23 | 120 MiB |
| 4 | `reports/chaos/docker-chaos-180s-c23409c-20261020T110631Z.md` | **LINEARIZABLE** | 8,632 | 17 / 23 / 21 | 23 | 123 MiB |
| 5 | `reports/chaos/docker-chaos-180s-c23409c-20261020T111009Z.md` | **LINEARIZABLE** | 6,640 | 30 / 9 / 18 | 20 | 118 MiB |

Node logs for every run sit next to each report (`*-node-logs.txt.gz`). They contain no
invariant failure, failed node task or failed gRPC handler.

## Direct check of the fix

For every election, every vote the winner counted must have been granted by that voter (an INFO
trace). Before the fix (run on `d63b882`): 14 of 29 elections counted a vote that was never granted.
After the fix (the five runs above): **0 such votes in 105 elections**.
