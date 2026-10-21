# Leader failover: failover-sanity

- **Commit:** f790266 (with uncommitted changes)
- **Finished:** 2026-10-21T00:12:03Z
- **Command:** `scripts/failover.sh 10 failover-sanity`
- **Cluster:** 5 containers from docker-compose.yml (448 MB each, JVM heap 256 MB) and the load generator on the SAME machine: all traffic goes over loopback/the Docker bridge, so there is no real network latency in these numbers.

## Machine and configuration

- **Node settings for this run:** `MAX_APPENDS_IN_FLIGHT=4 GROUP_COMMIT=true ELECTION_TIMEOUT_MS=150-300`
- **VM shape:** not an Oracle Cloud VM (no instance metadata)
- **CPU:** AMD EPYC 7763 64-Core Processor, 2 cores (x86_64); **memory:** 7942 MB total, 2920 MB available
- **OS:** Ubuntu 24.04.3 LTS, kernel 6.8.0-1064-azure; **Docker** 29.9.1-1
- **Disk:** Docker data in /var/lib/docker on /dev/loop4 (ext4); block device: loop4  32G    0  (NAME SIZE ROTA MODEL)
- **fsync latency now (FsyncProbe, idle disk):** 1 writer(s) x 200 fsyncs of a 256-byte append: p50 0.8 ms, p99 2.0 ms, max 2.8 ms; 5 writer(s) x 200 fsyncs of a 256-byte append: p50 1.9 ms, p99 7.5 ms, max 29.6 ms
- **fsync: ON.** Every node fsyncs its Raft log (term, vote, entries) before answering any RPC and before the
  leader counts its own copy (Figure 2). With group commit the leader writes all entries proposed in one burst
  with one fsync. The B-Tree's own WAL is lazy and is fsynced every 1,000 applied entries (DESIGN.md §1,
  decision 8). Snapshots every 10,000 entries.
- **Nodes:** 448 MB container limit, JVM heap 256 MB (Serial GC), election timeout 150–300 ms, heartbeat 50 ms,
  PreVote and CheckQuorum on.
- **Load generator:** on the same machine as the 5 nodes, so it competes with them for the 2 cores.

## Method

- **Method:** 10 trials. Each waits until all 5 nodes agree on one leader and have caught up, then `docker kill`s the leader (SIGKILL). A watcher asks the old leader's /status over and over; **the clock starts at its last successful answer** (the last moment it was certainly alive, so the failover time errs high, never low). **Failover time** = from then until the first write sent after the kill succeeds: it went through the new leader and was committed by a majority of the 4 survivors. **New leader visible** = until a survivor's /status (polled every 5 ms) reports itself LEADER in a newer term. The probe client gives each attempt 300 ms (an attempt at the dead node's published port can hang until then) and retries after 5 ms. The `docker kill` command itself takes hundreds of ms, so times measured from its start are an upper bound only.
- With 10 trials, p99 (nearest rank) is the largest value.

## Summary (ms)

| Measure | Median | p99 | Min | Max |
|---|---|---|---|---|
| **Old leader last seen alive → first successful write (failover time)** | 560 | 881 | 515 | 881 |
| Old leader last seen alive → new leader visible in /status | 314 | 625 | 278 | 625 |
| `docker kill` started → first successful write (upper bound) | 694 | 1039 | 623 | 1039 |
| `docker kill` command duration | 330 | 657 | 284 | 657 |

## Every trial (ms since the `docker kill` command started)

| Trial | Killed | Old term | New leader | New term | kill command done | last seen alive | new leader visible | first write OK | **failover** |
|---|---|---|---|---|---|---|---|---|---|
| 1 | node1 | 1 | node3 | 2 | 657 | 158 | 782 | 1039 | **881** |
| 2 | node3 | 2 | node4 | 3 | 614 | 173 | 681 | 952 | **779** |
| 3 | node4 | 3 | node5 | 4 | 285 | 108 | 391 | 623 | **515** |
| 4 | node5 | 4 | node3 | 5 | 405 | 205 | 503 | 755 | **550** |
| 5 | node3 | 5 | node5 | 6 | 330 | 108 | 464 | 694 | **586** |
| 6 | node5 | 6 | node3 | 7 | 286 | 90 | 368 | 649 | **560** |
| 7 | node3 | 7 | node2 | 8 | 323 | 125 | 412 | 680 | **554** |
| 8 | node2 | 8 | node1 | 9 | 382 | 158 | 472 | 721 | **562** |
| 9 | node1 | 9 | node2 | 11 | 284 | 90 | 587 | 634 | **544** |
| 10 | node2 | 11 | node3 | 13 | 386 | 99 | 651 | 715 | **616** |

## Node threads (from /status at the end; counted since each node process started)

| Node | Role | Term | Longest single task ms | Latest task start ms | Tasks >= 50 ms |
|---|---|---|---|---|---|
| node1 | FOLLOWER | 13 | 166 | 28 | 1 |
| node2 | FOLLOWER | 13 | 138 | 31 | 1 |
| node3 | LEADER | 13 | 187 | 63 | 3 |
| node4 | FOLLOWER | 13 | 181 | 59 | 3 |
| node5 | FOLLOWER | 13 | 121 | 46 | 1 |

Every task on a node thread (fsync included) delays the heartbeats queued behind it; the node logs (`event=slow_task`, `event=slow_fsync`) say which.
