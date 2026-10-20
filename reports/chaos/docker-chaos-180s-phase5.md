# Docker chaos report

- **Command:** `scripts/docker-chaos.sh 180`
- **Date:** 2026-10-20T08:58:40.346166481Z
- **Git commit:** 3efea60
- **Machine:** Linux 6.8.0-1064-azure amd64, 2 cores (AMD EPYC 7763 64-Core Processor), Java 21, max heap 512 MB, Docker 29.9.1-1
- **Setup:** 5 containers (docker-compose.yml), 4 client threads on the host, 3 shared keys; a fault every 1-3 s, each undone after 2-5 s, at most two nodes faulty at once

## Verdict

**LINEARIZABLE**, checked in 138 ms.

| Measure | Value |
|---|---|
| fault-injection window | 180 s (total wall time 187 s) |
| operations | 6455 |
| completed | 6430 |
| pending writes (unknown outcome) | 13 |
| reads left out (no definite answer) | 12 |
| `docker kill` | 19 |
| `docker pause` | 26 |
| `docker network disconnect` | 10 |

## Peak memory per container (docker stats, sampled every 5 s)

| Container | Peak | Limit |
|---|---|---|
| raftkv-node1-1 | 114 MiB | 384MiB |
| raftkv-node2-1 | 103 MiB | 384MiB |
| raftkv-node3-1 | 132 MiB | 384MiB |
| raftkv-node4-1 | 118 MiB | 384MiB |
| raftkv-node5-1 | 113 MiB | 384MiB |

`docker kill` is a process crash (SIGKILL): the host's page cache survives, so data written but not fsynced is not lost. Power loss is covered by the simulator (DESIGN.md).
