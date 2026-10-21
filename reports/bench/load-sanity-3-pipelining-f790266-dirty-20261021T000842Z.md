# Load benchmark: sanity-3-pipelining

- **Commit:** f790266 (with uncommitted changes)
- **Finished:** 2026-10-21T00:09:42Z
- **Command:** `scripts/bench.sh compare sanity (WARMUP_S=10 MEASURE_S=30 RUNS=1 READ_PERCENT=50)`
- **Cluster:** 5 containers from docker-compose.yml (448 MB each, JVM heap 256 MB) and the load generator on the SAME machine: all traffic goes over loopback/the Docker bridge, so there is no real network latency in these numbers.

## Machine and configuration

- **Node settings for this run:** `MAX_APPENDS_IN_FLIGHT=4 GROUP_COMMIT=false ELECTION_TIMEOUT_MS=150-300`
- **VM shape:** not an Oracle Cloud VM (no instance metadata)
- **CPU:** AMD EPYC 7763 64-Core Processor, 2 cores (x86_64); **memory:** 7942 MB total, 2981 MB available
- **OS:** Ubuntu 24.04.3 LTS, kernel 6.8.0-1064-azure; **Docker** 29.9.1-1
- **Disk:** Docker data in /var/lib/docker on /dev/loop4 (ext4); block device: loop4  32G    0  (NAME SIZE ROTA MODEL)
- **fsync latency now (FsyncProbe, idle disk):** 1 writer(s) x 200 fsyncs of a 256-byte append: p50 0.8 ms, p99 2.4 ms, max 2.5 ms; 5 writer(s) x 200 fsyncs of a 256-byte append: p50 1.8 ms, p99 4.8 ms, max 6.5 ms
- **fsync: ON.** Every node fsyncs its Raft log (term, vote, entries) before answering any RPC and before the
  leader counts its own copy (Figure 2). With group commit the leader writes all entries proposed in one burst
  with one fsync. The B-Tree's own WAL is lazy and is fsynced every 1,000 applied entries (DESIGN.md §1,
  decision 8). Snapshots every 10,000 entries.
- **Nodes:** 448 MB container limit, JVM heap 256 MB (Serial GC), election timeout 150–300 ms, heartbeat 50 ms,
  PreVote and CheckQuorum on.
- **Load generator:** on the same machine as the 5 nodes, so it competes with them for the 2 cores.

## Method

- **Method:** closed loop (each client sends its next operation as soon as the previous one is answered, no think time); 10 s warm-up (not recorded), then a 30 s measurement window; 1 runs per configuration; keys chosen uniformly at random; every key written once before the first run. Latency is per operation, from the call to its answer, as the client sees it.
- **Reads** are linearizable (ReadIndex on the leader); **writes** are Puts committed on a majority and applied before the answer.
- **Coordinated omission:** closed-loop load slows down when the system does, so a stall delays the operations that would have been sent during it instead of recording them as slow. Percentiles here can therefore hide latency spikes; they describe what these clients saw, not an open-loop arrival rate.

## Summary: median of 1 runs (min–max in parentheses)

| Clients | Reads / writes | Value | ops/s | p50 ms | p99 ms | p99.9 ms | Failed ops | Runs with a term or leader change |
|---|---|---|---|---|---|---|---|---|
| 16 | 50% / 50% | 100 B | 178 (178–178) | 75.07 (75.07–75.07) | 282.62 (282.62–282.62) | 370.69 (370.69–370.69) | 0 | 0 of 1 |

## Every run

| Clients | Reads / writes | Value | Run | Operations | ops/s | p50 ms | p99 ms | p99.9 ms | max ms | read p99 ms | write p99 ms | Failed | Term, leader at start | at end |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 16 | 50% / 50% | 100 B | 1 | 5343 | 178 | 75.07 | 282.62 | 370.69 | 393.98 | 254.59 | 302.34 | 0 | term 1, leader node1 | term 1, leader node1 |

The term and leader come from every node's /status just before the window opens and just after it closes. A change means an election happened under load.

## Node threads (from /status at the end; counted since each node process started)

| Node | Role | Term | Longest single task ms | Latest task start ms | Tasks >= 50 ms |
|---|---|---|---|---|---|
| node1 | LEADER | 1 | 1221 | 959 | 54 |
| node2 | FOLLOWER | 1 | 255 | 261 | 38 |
| node3 | FOLLOWER | 1 | 526 | 320 | 40 |
| node4 | FOLLOWER | 1 | 568 | 343 | 45 |
| node5 | FOLLOWER | 1 | 790 | 642 | 45 |

Every task on a node thread (fsync included) delays the heartbeats queued behind it; the node logs (`event=slow_task`, `event=slow_fsync`) say which.
