package io.github.hard3007.raftkv.bench;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Markdown (and CSV) reports for the benchmarks. Every number in them was measured by this run. */
final class Reports {

    private Reports() {
    }

    static void writeLoad(Path report, String label, String command, String setup, Duration clusterWarmup,
            Duration warmup, Duration measure, int runs, List<LoadBenchmark.RunResult> results,
            Map<Integer, ClusterProbe.NodeStatus> nodes) throws IOException {
        StringBuilder md = header("Load benchmark: " + label, command, setup);
        md.append("- **Method:** closed loop (each client sends its next operation as soon as the previous one is"
                + " answered, no think time). Warm-up, not recorded: ").append(clusterWarmup.toSeconds())
                .append(" s before the first run on the fresh cluster (the nodes' JIT), ").append(warmup.toSeconds())
                .append(" s before every later run. Then a ").append(measure.toSeconds())
                .append(" s measurement window; ").append(runs)
                .append(" runs per configuration; keys chosen uniformly at random; every key written once before the"
                + " first run. Latency is per operation, from the call to its answer, as the client sees it.\n");
        md.append("- **Reads** are linearizable (ReadIndex on the leader); **writes** are Puts committed on a majority"
                + " and applied before the answer.\n");
        md.append("- **CPU** (whole machine, from /proc/stat during each window, like vmstat): r = threads running or"
                + " waiting for a CPU (median of 1 s samples, including the sampler itself); us / sy / id / wa / st ="
                + " user, system, idle, I/O wait and **steal** (CPU time the hypervisor gave to other guests) as a"
                + " share of all CPU time. The raw `vmstat 1` log is kept next to this report.\n");
        md.append("- **Coordinated omission:** closed-loop load slows down when the system does, so a stall delays the"
                + " operations that would have been sent during it instead of recording them as slow. Percentiles"
                + " here can therefore hide latency spikes; they describe what these clients saw, not an open-loop"
                + " arrival rate.\n\n");

        Map<LoadBenchmark.Workload, List<LoadBenchmark.RunResult>> byWorkload = results.stream()
                .collect(Collectors.groupingBy(LoadBenchmark.RunResult::workload, LinkedHashMap::new,
                        Collectors.toList()));
        md.append("## Summary: median of ").append(runs).append(" runs (min–max in parentheses)\n\n");
        md.append("| Clients | Reads / writes | Value | ops/s | p50 ms | read p50 ms | write p50 ms | p99 ms | p99.9 ms"
                + " | Failed ops | Runs with a term or leader change | r | idle % | steal % |\n");
        md.append("|---|---|---|---|---|---|---|---|---|---|---|---|---|---|\n");
        for (var entry : byWorkload.entrySet()) {
            LoadBenchmark.Workload w = entry.getKey();
            List<LoadBenchmark.RunResult> rs = entry.getValue();
            md.append("| ").append(w.clients()).append(" | ").append(w.readPercent()).append("% / ")
                    .append(100 - w.readPercent()).append("% | ").append(w.valueBytes()).append(" B | ")
                    .append(Stats.medianAndSpread(column(rs, r -> r.opsPerSecond()), "%.0f")).append(" | ")
                    .append(Stats.medianAndSpread(column(rs, r -> Main.ms(r.all(), 50)), "%.2f")).append(" | ")
                    .append(Stats.medianAndSpread(column(rs, r -> Main.ms(r.readLatency(), 50)), "%.2f")).append(" | ")
                    .append(Stats.medianAndSpread(column(rs, r -> Main.ms(r.writeLatency(), 50)), "%.2f")).append(" | ")
                    .append(Stats.medianAndSpread(column(rs, r -> Main.ms(r.all(), 99)), "%.2f")).append(" | ")
                    .append(Stats.medianAndSpread(column(rs, r -> Main.ms(r.all(), 99.9)), "%.2f")).append(" | ")
                    .append(rs.stream().mapToLong(LoadBenchmark.RunResult::failures).sum()).append(" | ")
                    .append(rs.stream().filter(LoadBenchmark.RunResult::electionDuringRun).count()).append(" of ")
                    .append(rs.size()).append(" | ")
                    .append(Stats.medianAndSpread(column(rs, r -> r.cpu().runnableMedian()), "%.0f")).append(" | ")
                    .append(Stats.medianAndSpread(column(rs, r -> r.cpu().idle()), "%.0f")).append(" | ")
                    .append(Stats.medianAndSpread(column(rs, r -> r.cpu().steal()), "%.0f")).append(" |\n");
        }

        md.append("\n## Every run\n\n");
        md.append("| Clients | Reads / writes | Value | Run | Warm-up s | Operations | ops/s | p50 ms | read p50 ms"
                + " | write p50 ms | p99 ms | p99.9 ms | max ms | read p99 ms | write p99 ms | Failed"
                + " | Term, leader at start | at end | CPU (r, us, sy, id, wa, st) |\n");
        md.append("|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|\n");
        StringBuilder csv = new StringBuilder("label,clients,read_percent,value_bytes,run,warmup_s,operations,reads,"
                + "writes,ops_per_s,p50_ms,read_p50_ms,write_p50_ms,p99_ms,p999_ms,max_ms,read_p99_ms,write_p99_ms,"
                + "failures,term_start,leader_start,term_end,leader_end,runnable_median,runnable_max,cpu_user,"
                + "cpu_system,cpu_idle,cpu_iowait,cpu_steal\n");
        for (LoadBenchmark.RunResult r : results) {
            LoadBenchmark.Workload w = r.workload();
            CpuSampler.CpuStats c = r.cpu();
            md.append(String.format("| %d | %d%% / %d%% | %d B | %d | %d | %d | %.0f | %.2f | %.2f | %.2f | %.2f | %.2f"
                            + " | %.2f | %.2f | %.2f | %d | %s | %s%s | %s |%n", w.clients(), w.readPercent(),
                    100 - w.readPercent(), w.valueBytes(), r.run(), r.warmup().toSeconds(), r.operations(),
                    r.opsPerSecond(), Main.ms(r.all(), 50), Main.ms(r.readLatency(), 50), Main.ms(r.writeLatency(), 50),
                    Main.ms(r.all(), 99), Main.ms(r.all(), 99.9), r.all().getMaxValue() / 1000.0,
                    Main.ms(r.readLatency(), 99), Main.ms(r.writeLatency(), 99), r.failures(), r.termAtStart(),
                    r.termAtEnd(), r.electionDuringRun() ? " **(changed)**" : "", c.describe()));
            csv.append(String.format("%s,%d,%d,%d,%d,%d,%d,%d,%d,%.1f,%.3f,%.3f,%.3f,%.3f,%.3f,%.3f,%.3f,%.3f,%d,%d,%d,"
                            + "%d,%d,%.1f,%d,%.1f,%.1f,%.1f,%.1f,%.1f%n",
                    label, w.clients(), w.readPercent(), w.valueBytes(), r.run(), r.warmup().toSeconds(),
                    r.operations(), r.reads(), r.writes(), r.opsPerSecond(), Main.ms(r.all(), 50),
                    Main.ms(r.readLatency(), 50), Main.ms(r.writeLatency(), 50), Main.ms(r.all(), 99),
                    Main.ms(r.all(), 99.9), r.all().getMaxValue() / 1000.0, Main.ms(r.readLatency(), 99),
                    Main.ms(r.writeLatency(), 99), r.failures(), r.termAtStart().term(), r.termAtStart().leader(),
                    r.termAtEnd().term(), r.termAtEnd().leader(), c.runnableMedian(), c.runnableMax(), c.user(),
                    c.system(), c.idle(), c.iowait(), c.steal()));
        }
        md.append("\nThe term and leader come from every node's /status just before the window opens and just after"
                + " it closes. A change means an election happened under load.\n");
        nodeThreads(md, nodes);
        Files.writeString(report, md);
        Files.writeString(Path.of(report.toString().replaceFirst("\\.md$", ".csv")), csv);
    }

    static void writeFailover(Path report, String label, String command, String setup, Duration warmup,
            List<FailoverBenchmark.Trial> trials, Map<Integer, ClusterProbe.NodeStatus> nodes) throws IOException {
        StringBuilder md = header("Leader failover: " + label, command, setup);
        md.append("- **Warm-up:** ").append(warmup.toSeconds()).append(" s of load (16 clients, 50% reads, 100 B;"
                + " not recorded) before the first trial, so the nodes' JIT is warm. A killed leader restarts as a"
                + " fresh JVM, so every later trial has one cold node (a follower by then).\n");
        md.append("- **Method:** ").append(trials.size()).append(" trials. Each waits until all 5 nodes agree on one"
                + " leader and have caught up, then `docker kill`s the leader (SIGKILL). A watcher asks the old"
                + " leader's /status over and over; **the clock starts at its last successful answer** (the last moment"
                + " it was certainly alive, so the failover time errs high, never low). **Failover time** = from then"
                + " until the first write sent after the kill succeeds: it went through the new leader and was"
                + " committed by a majority of the 4 survivors. **New leader visible** = until a survivor's /status"
                + " (polled every 5 ms) reports itself LEADER in a newer term. The probe client gives each attempt"
                + " 300 ms (an attempt at the dead node's published port can hang until then) and retries after 5 ms."
                + " The `docker kill` command itself takes hundreds of ms, so times measured from its start are an"
                + " upper bound only.\n");
        md.append("- With ").append(trials.size()).append(" trials, p99 (nearest rank) is the ")
                .append(trials.size() <= 100 ? "largest value" : "99th-percentile value").append(".\n\n");
        md.append("## Summary (ms)\n\n| Measure | Median | p99 | Min | Max |\n|---|---|---|---|---|\n");
        summaryRow(md, "**Old leader last seen alive → first successful write (failover time)**",
                column(trials, t -> t.firstWriteMs() - t.lastAliveMs()));
        summaryRow(md, "Old leader last seen alive → new leader visible in /status",
                column(trials, t -> t.electedMs() - t.lastAliveMs()));
        summaryRow(md, "`docker kill` started → first successful write (upper bound)",
                column(trials, FailoverBenchmark.Trial::firstWriteMs));
        summaryRow(md, "`docker kill` command duration", column(trials, FailoverBenchmark.Trial::killCommandMs));
        md.append("\n## Every trial (ms since the `docker kill` command started)\n\n"
                + "| Trial | Killed | Old term | New leader | New term | kill command done | last seen alive"
                + " | new leader visible | first write OK | **failover** |\n|---|---|---|---|---|---|---|---|---|---|\n");
        for (FailoverBenchmark.Trial t : trials) {
            md.append(String.format("| %d | node%d | %d | node%d | %d | %.0f | %.0f | %.0f | %.0f | **%.0f** |%n",
                    t.number(), t.killedNode(), t.oldTerm(), t.newLeader(), t.newTerm(), t.killCommandMs(),
                    t.lastAliveMs(), t.electedMs(), t.firstWriteMs(), t.firstWriteMs() - t.lastAliveMs()));
        }
        nodeThreads(md, nodes);
        Files.writeString(report, md);
    }

    /**
     * Every node's thread counters at the end. A task that runs longer than the minimum election
     * timeout (150 ms by default) stops that node's heartbeats long enough for an election.
     */
    private static void nodeThreads(StringBuilder md, Map<Integer, ClusterProbe.NodeStatus> nodes) {
        md.append("\n## Node threads (from /status at the end; counted since each node process started)\n\n"
                + "| Node | Role | Term | Longest single task ms | Latest task start ms | Tasks >= 50 ms |\n"
                + "|---|---|---|---|---|---|\n");
        for (ClusterProbe.NodeStatus s : nodes.values()) {
            md.append(String.format("| node%d | %s | %d | %d | %d | %d |%n", s.id(), s.role(), s.term(),
                    s.slowestTaskMs(), s.latestTaskStartMs(), s.slowTasks()));
        }
        md.append("\nEvery task on a node thread (fsync included) delays the heartbeats queued behind it; the"
                + " node logs (`event=slow_task`, `event=slow_fsync`) say which.\n");
    }

    private static void summaryRow(StringBuilder md, String name, double[] values) {
        md.append(String.format("| %s | %.0f | %.0f | %.0f | %.0f |%n", name, Stats.median(values),
                Stats.percentile(values, 99), Stats.min(values), Stats.max(values)));
    }

    private static StringBuilder header(String title, String command, String setup) {
        String commit = ClusterProbe.tryRun("git", "rev-parse", "--short", "HEAD");
        String dirty = ClusterProbe.tryRun("git", "status", "--porcelain", "--untracked-files=no");
        StringBuilder md = new StringBuilder("# ").append(title).append("\n\n");
        md.append("- **Commit:** ").append(commit.isEmpty() ? "unknown" : commit)
                .append(dirty.isEmpty() ? "" : " (with uncommitted changes)").append("\n");
        md.append("- **Finished:** ").append(ZonedDateTime.now(ZoneOffset.UTC).withNano(0)).append("\n");
        md.append("- **Command:** `").append(command).append("`\n");
        md.append("- **Cluster:** 5 containers from docker-compose.yml (448 MB each, JVM heap 256 MB) and the load"
                + " generator on the SAME machine: all traffic goes over loopback/the Docker bridge, so there is no"
                + " real network latency in these numbers.\n");
        if (!setup.isBlank()) {
            md.append("\n## Machine and configuration\n\n").append(setup.strip()).append("\n\n## Method\n\n");
        }
        return md;
    }

    /** One number per run or trial. */
    private static <T> double[] column(List<T> rows, java.util.function.ToDoubleFunction<T> f) {
        return rows.stream().mapToDouble(f).toArray();
    }
}
