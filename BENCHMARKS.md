# Benchmarks

Every number here comes from a report in `reports/bench/`, written by a script in this repo that
can be rerun. The reports carry the commit, the exact command, the machine description (with an
fsync measurement taken just before the run), the node settings, and every single run.

**Final numbers come from the Oracle Cloud VM only** (section 4, filled in from its reports).
Numbers from the GitHub Codespace are sanity checks (section 5).

## 1. Setup, stated honestly

- **Everything on one machine.** The 5 nodes (Docker containers from `docker-compose.yml`) and
  the load generator share one VM. All traffic goes over loopback and the Docker bridge, so
  **there is no real network latency in any of these numbers**. On a real network every
  replication round trip would add the network's RTT.
- **The load generator competes with the nodes for CPU** (2 cores on both machines).
- **fsync is ON**, as Raft requires:
  - every node fsyncs its Raft log (term, vote, entries) before it answers any RPC;
  - the leader fsyncs before it counts its own copy (with group commit: one fsync per burst of
    proposals);
  - the B-Tree's own WAL is lazy and is fsynced every 1,000 applied entries (DESIGN.md §1.5).
- **Each node:** a 448 MB container limit, JVM heap 256 MB (Serial GC), election timeout
  150–300 ms, heartbeat 50 ms, PreVote and CheckQuorum on, snapshots every 10,000 entries.
- **The disk.** Each report says which block device holds Docker's data and how fast an fsync
  is on it, measured with `FsyncProbe` (200 fsyncs of a 256-byte append, one writer, then five
  at once) right before the benchmark.

## 2. Method

- **Closed loop.** Each client thread sends one operation, waits for the answer, and sends the
  next at once (no think time). Keys are chosen uniformly from 1,000; every key is written once
  before the first run.
- **Operations.**
  - Reads are linearizable Gets (ReadIndex on the leader: one heartbeat round to a majority).
  - Writes are Puts, committed on a majority and applied before the answer.
  - **Every result states its read/write ratio.** The fixed configuration is 50% / 50%.
- **Warm-up.** Each run starts its clients, runs 10 s without recording, then measures for
  30 s. Only operations that start and finish inside the window count.
- **Repetition.** At least 3 runs per configuration. Tables show the **median of the runs, with
  the min–max spread** in parentheses.
- **Latency.** Per operation, from the call to its answer, as the client sees it (retries
  included), in an HdrHistogram per thread, merged.
- **Sweep.** 1, 4, 16 and 64 clients at 100 B values show the throughput vs. latency curve,
  plus 16 clients at 1 KB values.
- **The resume number uses one fixed configuration: 16 clients, 50% reads / 50% writes, 100 B
  values, 1,000 keys.**
- **Elections under load.** Every run records the term and the leader (from every node's
  `/status`) just before and just after its window. A run where either changed is marked
  `(changed)` in its report. Each report also lists every node thread's longest task and its
  number of tasks of 50 ms or more.

### Coordinated omission
Closed-loop load slows down when the system does: while the cluster stalls, the clients wait
instead of sending, so the operations that would have arrived during the stall are never sent,
and never recorded as slow. **The percentiles here can therefore hide latency spikes.** They
describe what these clients saw, not the latency of an open-loop workload with a fixed arrival
rate. The `max` column and the per-node stall counters show stalls that the percentiles may
smooth over.

### Failover
`scripts/failover.sh 50` runs 50 trials on a fresh cluster with the default settings:
1. Wait until all 5 nodes agree on one leader and have caught up.
2. `docker kill` (SIGKILL) the leader.
3. **Failover time** runs from the old leader's last successful `/status` answer (polled in a
   tight loop: the last moment it was certainly alive) to the first successful write sent after
   the kill. That write went through the new leader and was committed by a majority of the 4
   survivors. Starting the clock at the last sign of life errs high, never low.
4. Restart the old leader and wait for the cluster to be stable again.

The report gives the median and p99 (with 50 trials, p99 is the largest value), the time until
a new leader shows up in `/status`, and, as an upper bound, the times measured from the start
of the `docker kill` command (which itself takes hundreds of ms). The probe client gives each
attempt 300 ms and retries after 5 ms. An attempt at the dead node's published port can hang for
those 300 ms, and that is included.

## 3. The optimizations, before and after

`scripts/bench.sh compare` runs the fixed configuration on a fresh cluster for each step. Each
step adds one optimization to the one before; the settings are environment variables of the same
image:

| Step | Settings | What changes |
|---|---|---|
| 1. baseline | `MAX_APPENDS_IN_FLIGHT=0 GROUP_COMMIT=false` | Phase 2–6 replication: every proposal fsyncs on its own and re-sends everything from `nextIndex` |
| 2. + batching | `MAX_APPENDS_IN_FLIGHT=1 GROUP_COMMIT=false` | one AppendEntries in flight per follower; entries proposed meanwhile go in the next one, each sent once |
| 3. + pipelining | `MAX_APPENDS_IN_FLIGHT=4 GROUP_COMMIT=false` | up to 4 in flight per follower |
| 4. + group commit | `MAX_APPENDS_IN_FLIGHT=4 GROUP_COMMIT=true` | the leader writes a burst of proposals with one fsync (the default) |

How they work and why they stay safe: DESIGN.md §7.

## 4. Oracle Cloud VM (final numbers)

To be filled in from the reports in `reports/bench/` once they have been run on the VM.

## 5. Codespace sanity checks (not resume numbers)

GitHub Codespace: 2 cores (AMD EPYC 7763), 8 GB RAM, Docker-in-Docker with all data on one loop
device. The probe measured fsync p50 0.8 ms and p99 4.1 ms (one writer, idle), and p50 1.7 ms and
p99 8.4 ms (five writers). Under load the nodes logged hundreds of fsyncs of 50 ms or more (up to
about 0.5 s).

**One run per setting** (`RUNS=1 scripts/bench.sh compare sanity`, uncommitted tree on top of
f790266, so the reports are stamped `-dirty`). This is far too few to rank the optimizations.

| Step (16 clients, 50% reads / 50% writes, 100 B) | ops/s | p50 ms | p99 ms | Term or leader changed during the window |
|---|---|---|---|---|
| 1. baseline | 109 | 79.6 | 835 | no |
| 2. + batching | 181 | 71.6 | 424 | **yes** (term 4 → 5) |
| 3. + pipelining | 178 | 75.1 | 283 | no |
| 4. + group commit | 173 | 72.4 | 461 | **yes** (term 1 → 8) |

Reports: `reports/bench/load-sanity-{1-baseline,2-batching,3-pipelining,4-group-commit}-f790266-dirty-*.md`.

- **What this machine can show:** batching removes the duplicate sends of the baseline.
  Throughput went up by about two thirds and p99 roughly halved.
- **What it cannot show:** whether pipelining and group commit help. Every node thread still had
  single tasks of 0.5–1.4 s, almost all of them slow fsyncs on the shared loop device, and two
  of the four runs had elections inside the measurement window. DESIGN.md §7.4 has the
  investigation.

**Failover, 10 trials** (`scripts/failover.sh 10 failover-sanity`): median **560 ms**, max
881 ms from the old leader's last sign of life to the first successful write. A new leader was
visible in `/status` after a median of 314 ms; the rest is the client finding it. Measured from
the start of `docker kill` instead: median 694 ms (the command itself took a median of 330 ms).

Earlier baseline runs on f790266 before any Phase 7 change (`load-baseline-sanity-*`,
`failover-baseline-sanity-*`):
- about 160 ops/s at both 16 and 64 clients (fsync-bound);
- one 1 KB run with an election, which started the investigation;
- a failover clock that started at the `docker kill` command and was replaced because of that.

The `-dirty` in two of those file names comes from my editing `RaftConfig.java` while they ran.
The image and the bench binary they used were built from f790266.

## 6. How to rerun

```bash
scripts/docker-build.sh --local     # the image for this machine
scripts/bench.sh compare            # each optimization, before vs after (about 12 min)
scripts/bench.sh sweep              # 1/4/16/64 clients at 100 B, 16 clients at 1 KB (about 12 min)
scripts/failover.sh 50              # 50 leader kills (about 8 min)
```
`RUNS`, `WARMUP_S`, `MEASURE_S` and `READ_PERCENT` override the method for quick checks, and
every report records them. `ELECTION_TIMEOUT_MS` (default `150-300`) is passed to the nodes and
recorded too.
