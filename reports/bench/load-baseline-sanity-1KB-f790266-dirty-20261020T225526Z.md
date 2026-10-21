# Load benchmark: baseline-sanity-1KB

- **Commit:** f790266 (with uncommitted changes)
- **Finished:** 2026-10-20T22:56:46Z
- **Command:** `scripts/bench.sh sweep baseline-sanity (WARMUP_S=10 MEASURE_S=30 RUNS=1 READ_PERCENT=50)`
- **Cluster:** 5 containers from docker-compose.yml (384 MB each, JVM heap 256 MB) and the load generator on the SAME machine: all traffic goes over loopback/the Docker bridge, so there is no real network latency in these numbers.

## Machine and configuration

- **VM shape:** not an Oracle Cloud VM (no instance metadata)
- **CPU:** AMD EPYC 7763 64-Core Processor, 2 cores (x86_64); **memory:** 7942 MB total, 4160 MB available
- **OS:** Ubuntu 24.04.3 LTS, kernel 6.8.0-1064-azure; **Docker** 29.9.1-1
- **Disk:** Docker data in /var/lib/docker on /dev/loop4 (ext4); block device: loop4  32G    0  (NAME SIZE ROTA MODEL)
- **fsync: ON.** Every node fsyncs its Raft log (term, vote, entries) before answering any RPC and before the
  leader counts its own copy (Figure 2). The B-Tree's own WAL is lazy and is fsynced every 1,000 applied entries
  (DESIGN.md §1, decision 8). Snapshots every 10,000 entries.
- **Load generator:** on the same machine as the 5 nodes, so it competes with them for the 2 cores.

## Method

- **Method:** closed loop (each client sends its next operation as soon as the previous one is answered, no think time); 10 s warm-up (not recorded), then a 30 s measurement window; 1 runs per configuration; keys chosen uniformly at random; every key written once before the first run. Latency is per operation, from the call to its answer, as the client sees it.
- **Reads** are linearizable (ReadIndex on the leader); **writes** are Puts committed on a majority and applied before the answer.
- **Coordinated omission:** closed-loop load slows down when the system does, so a stall delays the operations that would have been sent during it instead of recording them as slow. Percentiles here can therefore hide latency spikes; they describe what these clients saw, not an open-loop arrival rate.

## Summary: median of 1 runs (min–max in parentheses)

| Clients | Reads / writes | Value | ops/s | p50 ms | p99 ms | p99.9 ms | Failed ops | Runs with a term or leader change |
|---|---|---|---|---|---|---|---|---|
| 16 | 50% / 50% | 1024 B | 98 (98–98) | 125.06 (125.06–125.06) | 618.50 (618.50–618.50) | 971.26 (971.26–971.26) | 0 | 1 of 1 |

## Every run

| Clients | Reads / writes | Value | Run | Operations | ops/s | p50 ms | p99 ms | p99.9 ms | max ms | read p99 ms | write p99 ms | Failed | Term, leader at start | at end |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 16 | 50% / 50% | 1024 B | 1 | 2934 | 98 | 125.06 | 618.50 | 971.26 | 1113.09 | 614.91 | 621.57 | 0 | term 2, leader node3 | term 3, leader node1 **(changed)** |

The term and leader come from every node's /status just before the window opens and just after it closes. A change means an election happened under load.
