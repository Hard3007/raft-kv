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
- **The setup is CPU-bound, not disk-bound.** On the Oracle VM, `vmstat` and `iostat` during the
  first smoke run showed:
  - the run queue far longer than the 2 cores, with idle time near zero and measurable **steal
    time** (the hypervisor running other guests on our cores);
  - the disk far from saturated.

  That first observation was by hand. From then on every load report measures it per run
  (columns `r`, `idle %` and `steal %`, plus the full `vmstat 1` log next to the report), and
  the numbers quoted in §4 come from those reports.
- **What "slow fsync" means here.** Nodes log any fsync that takes 50 ms or more of *wall-clock*
  time. On a CPU-starved machine most of that can be the thread waiting for a CPU after the disk
  has finished, not the disk itself. Read those logs together with the CPU columns.
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
- **Warm-up.** Each run starts its clients and runs without recording before it measures:
  - **60 s before the first run** on every fresh cluster. Until the JIT has compiled the hot
    code, compilation competes with the load for the 2 cores. On the Oracle VM a 5 s warm-up gave
    less than half the throughput of a 60 s one.
  - **10 s before each later run** on the same, already warm, nodes.

  Then it measures for 30 s. Only operations that start and finish inside the window count.
  Failover runs 60 s of load before its first trial.
- **Repetition.** At least 3 runs per configuration. Tables show the **median of the runs, with
  the min–max spread** in parentheses.
- **Latency.** Per operation, from the call to its answer, as the client sees it (retries
  included), in an HdrHistogram per thread, merged. Reads and writes are also reported
  separately (p50 and p99 each).
- **CPU.** For every window: r (threads running or waiting for a CPU, median of 1 s samples),
  and user / system / idle / iowait / steal as a share of all CPU time, from `/proc/stat` (what
  vmstat reads).
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

VM.Standard.A1.Flex, 2 OCPU (2 Ampere ARM cores), 11 GiB RAM, no swap, default boot volume,
Ubuntu 24.04 aarch64, Phoenix AD-1. Each report's header describes the machine and disk, and
gives the fsync probe result from just before the run.

### 4.1 JVM thread flags for the nodes (decision: keep the defaults)

`scripts/bench.sh fixed` on commit 2b008db, three settings, 3 runs each: 16 clients, 50% reads /
50% writes, 100 B values, 60 s cluster warm-up, 30 s measured. **0 of the 9 runs had a term or
leader change.** Median of 3 runs, min–max in parentheses:

| `NODE_JVM_FLAGS` | ops/s | p50 ms (read / write) | p99 ms | p99.9 ms | r | idle % | steal % |
|---|---|---|---|---|---|---|---|
| none (default) | 707 (641–730) | 20.88 (18.67 / 22.77) | 61.70 | 184.32 | 10 | 5 | 21 |
| (1) `-XX:ActiveProcessorCount=1` + (2) `-Dio.grpc.netty.shaded.io.netty.eventLoopThreads=1` | 733 (652–765) | 20.32 (18.08 / 22.24) | 75.84 | 161.79 | 10 | 5 | 20 |
| (1) + (2) + `-XX:TieredStopAtLevel=1` (C1 JIT only) | 529 (528–530) | 28.53 (25.98 / 30.93) | 72.32 | 147.84 | 9 | 5 | 19 |

Reports: `reports/bench/load-jvm-default-*`, `load-jvm-1cpu-*`, `load-jvm-c1-*`. *(These are
on the VM and still to be added to the repo; the figures above were sent from them and will be
checked against the files when they arrive.)*

- **Decision: keep the default JVM flags** (empty `NODE_JVM_FLAGS`).
  - (1) + (2) is within noise: its ops/s range (652–765) overlaps the default's (641–730), and
    its p99 is worse while its p99.9 is better.
  - C1-only costs about 25% of throughput. Without C2, the compiled code is slower, and on a
    CPU-bound machine that matters more than the CPU the C2 compiler saves.
- **The Codespace gain did not reproduce.** On the Codespace, (1) + (2) looked like +47% ops/s
  and 42% less node CPU per operation (§5). Those were single runs, each with an election in its
  window, on a noisier machine. Three runs on the Oracle VM without elections show no real
  difference. Fewer JVM threads is not where this setup's CPU goes.
- **CPU-bound, with steal.** Across these runs about 10 threads were runnable for 2 cores, idle
  was about 5%, and **steal was 19–21%**: the hypervisor gave about a fifth of our CPU time to
  other guests. Every number in this section was measured under that.
- Reads are faster than writes (p50 18.7 vs 22.8 ms with the defaults). A read costs one
  heartbeat round and no fsync; a write needs an fsync on the leader and on a majority of
  followers. ReadIndex starts its confirmation round at once, without waiting for the 50 ms
  heartbeat timer (DESIGN.md §3.4).

### 4.2 The optimizations, before and after

To be filled in from `scripts/bench.sh compare`, run with the default JVM flags.

### 4.3 Throughput vs. latency, and 1 KB values

To be filled in from `scripts/bench.sh sweep`.

### 4.4 Failover

To be filled in from `scripts/failover.sh 50`.


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
  single tasks of 0.5–1.4 s, logged as slow fsyncs, mostly time spent waiting for a CPU (see below), and two
  of the four runs had elections inside the measurement window. DESIGN.md §7.4 has the
  investigation.

**Failover, 10 trials** (`scripts/failover.sh 10 failover-sanity`): median **560 ms**, max
881 ms from the old leader's last sign of life to the first successful write. A new leader was
visible in `/status` after a median of 314 ms; the rest is the client finding it. Measured from
the start of `docker kill` instead: median 694 ms (the command itself took a median of 330 ms).

**CPU and JVM threads** (one sanity run each; 16 clients, 50% / 50%, 100 B, 60 s warm-up, 30 s
measured; a scratch script, so treat these as direction only). The machine is CPU-bound: during
every window r was 10-13 (peaks 23-32) on 2 cores, idle 0-2%, iowait 0-3%, steal 0%. Fewer JVM
threads per node made each operation cheaper. Every run had an election in its window.

| Node JVM flags (`NODE_JVM_FLAGS`) | ops/s | read p50 ms | write p50 ms | p99 ms | node CPU per op |
|---|---|---|---|---|---|
| none (default) | 279 | 38.8 | 48.1 | 284 | 3.8 ms |
| `-XX:ActiveProcessorCount=1 -Dio.grpc.netty.shaded.io.netty.eventLoopThreads=1` | 411 | 27.0 | 33.7 | 194 | 2.2 ms |
| the same plus `-XX:TieredStopAtLevel=1` (C1 JIT only) | 438 | 26.4 | 31.6 | 175 | 1.7 ms |
| the first line's flags on the load generator too (`BENCH_JVM_FLAGS`) | 399 | 27.4 | 33.3 | 274 | 2.5 ms |

**This did not hold up on the Oracle VM.** Three runs per setting there showed no real gain for
the first flags and a 25% loss for C1-only, so the defaults stay (§4.1).

Earlier baseline runs on f790266 before any Phase 7 change
 (`load-baseline-sanity-*`,
`failover-baseline-sanity-*`):
- about 160 ops/s at both 16 and 64 clients (fsync-bound);
- one 1 KB run with an election, which started the investigation;
- a failover clock that started at the `docker kill` command and was replaced because of that.

The `-dirty` in two of those file names comes from my editing `RaftConfig.java` while they ran.
The image and the bench binary they used were built from f790266.

## 6. How to rerun

```bash
scripts/docker-build.sh --local     # the image for this machine
scripts/bench.sh compare            # each optimization, before vs after (about 15 min)
scripts/bench.sh sweep              # 1/4/16/64 clients at 100 B, 16 clients at 1 KB (about 14 min)
scripts/failover.sh 50              # 50 leader kills (about 9 min)
```
`RUNS`, `CLUSTER_WARMUP_S`, `WARMUP_S`, `MEASURE_S` and `READ_PERCENT` override the method for
quick checks, and every report records them. Also passed to the nodes and recorded:
`ELECTION_TIMEOUT_MS` (default `150-300`) and `NODE_JVM_FLAGS` (extra JVM flags, default none).
`BENCH_JVM_FLAGS` go to the load generator.
