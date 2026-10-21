# Leader failover: baseline-sanity

- **Commit:** f790266 (with uncommitted changes)
- **Finished:** 2026-10-20T22:57:52Z
- **Command:** `scripts/failover.sh 10 baseline-sanity`
- **Cluster:** 5 containers from docker-compose.yml (384 MB each, JVM heap 256 MB) and the load generator on the SAME machine: all traffic goes over loopback/the Docker bridge, so there is no real network latency in these numbers.

## Machine and configuration

- **VM shape:** not an Oracle Cloud VM (no instance metadata)
- **CPU:** AMD EPYC 7763 64-Core Processor, 2 cores (x86_64); **memory:** 7942 MB total, 4942 MB available
- **OS:** Ubuntu 24.04.3 LTS, kernel 6.8.0-1064-azure; **Docker** 29.9.1-1
- **Disk:** Docker data in /var/lib/docker on /dev/loop4 (ext4); block device: loop4  32G    0  (NAME SIZE ROTA MODEL)
- **fsync: ON.** Every node fsyncs its Raft log (term, vote, entries) before answering any RPC and before the
  leader counts its own copy (Figure 2). The B-Tree's own WAL is lazy and is fsynced every 1,000 applied entries
  (DESIGN.md §1, decision 8). Snapshots every 10,000 entries.
- **Load generator:** on the same machine as the 5 nodes, so it competes with them for the 2 cores.

## Method

- **Method:** 10 trials. Each waits until all 5 nodes agree on one leader and have caught up, then `docker kill`s the leader (SIGKILL). The clock starts just before the `docker kill` command is started, so the command's own time is included (the numbers err high). **Failover time** = until the first write sent after the kill succeeds: it went through the new leader and was committed by a majority of the 4 survivors. **Election** = until a survivor's /status (polled every 5 ms) reports itself LEADER in a newer term. The probe client tries each node for at most 300 ms and retries after 5 ms. Election timeout 150–300 ms, heartbeat 50 ms, PreVote and CheckQuorum on.
- With 10 trials, p99 (nearest rank) is the largest value.

## Summary (ms)

| Measure | Median | p99 | Min | Max |
|---|---|---|---|---|
| **Kill → first successful write (failover time)** | 685 | 911 | 586 | 911 |
| Kill → new leader visible in /status | 406 | 566 | 307 | 566 |
| `docker kill` command duration (included above) | 323 | 530 | 251 | 530 |

## Every trial (ms since the kill command started)

| Trial | Killed | Old term | New leader | New term | Elected | First write OK | kill command |
|---|---|---|---|---|---|---|---|
| 1 | node4 | 1 | node5 | 2 | 559 | 911 | 530 |
| 2 | node5 | 2 | node2 | 3 | 323 | 645 | 299 |
| 3 | node2 | 3 | node5 | 4 | 478 | 781 | 435 |
| 4 | node5 | 4 | node3 | 5 | 311 | 769 | 432 |
| 5 | node3 | 5 | node4 | 6 | 307 | 586 | 251 |
| 6 | node4 | 6 | node2 | 7 | 566 | 754 | 395 |
| 7 | node2 | 7 | node5 | 9 | 559 | 637 | 288 |
| 8 | node5 | 9 | node2 | 11 | 538 | 613 | 264 |
| 9 | node2 | 11 | node1 | 12 | 406 | 699 | 372 |
| 10 | node1 | 12 | node5 | 13 | 390 | 685 | 323 |
