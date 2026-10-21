package io.github.hard3007.raftkv.bench;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Benchmarks against the running 5-container cluster (start it with docker compose; the scripts
 * scripts/bench.sh and scripts/failover.sh do everything, including the machine description).
 * <pre>
 *   load     --label L --clients 1,4,16,64 --read-percent 50 --value-bytes 100 --keys 1000
 *            --warmup-s 10 --measure-s 30 --runs 3 [--setup-file F] [--command C]
 *   failover --label L --trials 50 [--setup-file F] [--command C]
 * </pre>
 * Every report goes to reports/bench/, named with the commit and a UTC timestamp, and is never
 * overwritten. Each load report also has a CSV twin with one row per run.
 */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            throw new IllegalArgumentException("usage: load|failover [--option value ...]");
        }
        Map<String, String> options = parse(Arrays.copyOfRange(args, 1, args.length));
        ClusterProbe cluster = new ClusterProbe(options.getOrDefault("project", "raftkv"));
        String setup = options.containsKey("setup-file") ? Files.readString(Path.of(options.get("setup-file"))) : "";
        String command = options.getOrDefault("command", "(not given)");
        // The node settings this cluster was started with (the scripts pass them), e.g.
        // "MAX_APPENDS_IN_FLIGHT=4 GROUP_COMMIT=true ELECTION_TIMEOUT_MS=150-300".
        setup = "- **Node settings for this run:** `" + options.getOrDefault("config", "(not given)") + "`\n" + setup;
        String label = options.getOrDefault("label", "unlabeled");
        switch (args[0]) {
            case "load" -> load(cluster, options, label, setup, command);
            case "failover" -> failover(cluster, options, label, setup, command);
            default -> throw new IllegalArgumentException("unknown benchmark " + args[0]);
        }
        System.exit(0); // gRPC's non-daemon threads would otherwise keep the JVM alive
    }

    private static void load(ClusterProbe cluster, Map<String, String> options, String label, String setup,
            String command) throws Exception {
        int[] clientCounts = Arrays.stream(options.getOrDefault("clients", "16").split(","))
                .mapToInt(Integer::parseInt).toArray();
        int readPercent = Integer.parseInt(options.getOrDefault("read-percent", "50"));
        int valueBytes = Integer.parseInt(options.getOrDefault("value-bytes", "100"));
        int keys = Integer.parseInt(options.getOrDefault("keys", "1000"));
        int runs = Integer.parseInt(options.getOrDefault("runs", "3"));
        Duration warmup = Duration.ofSeconds(Long.parseLong(options.getOrDefault("warmup-s", "10")));
        Duration measure = Duration.ofSeconds(Long.parseLong(options.getOrDefault("measure-s", "30")));
        Path report = newReport("load-" + label, ".md");

        LoadBenchmark bench = new LoadBenchmark(cluster, warmup, measure);
        List<LoadBenchmark.RunResult> results = new ArrayList<>();
        cluster.awaitStable(Duration.ofSeconds(60));
        bench.preload(new LoadBenchmark.Workload(1, readPercent, valueBytes, keys));
        for (int clients : clientCounts) {
            LoadBenchmark.Workload workload = new LoadBenchmark.Workload(clients, readPercent, valueBytes, keys);
            for (int run = 1; run <= runs; run++) {
                LoadBenchmark.RunResult r = bench.run(workload, run);
                results.add(r);
                System.out.printf("%s | run %d: %.0f ops/s, p50 %.2f ms, p99 %.2f ms, p999 %.2f ms, failures %d, %s -> %s%s%n",
                        workload.describe(), run, r.opsPerSecond(), ms(r.all(), 50), ms(r.all(), 99), ms(r.all(), 99.9),
                        r.failures(), r.termAtStart(), r.termAtEnd(),
                        r.electionDuringRun() ? "  ** LEADER OR TERM CHANGED DURING THE RUN **" : "");
            }
        }
        Reports.writeLoad(report, label, command, setup, warmup, measure, runs, results, cluster.statusOfAll());
        System.out.println("report: " + report);
    }

    private static void failover(ClusterProbe cluster, Map<String, String> options, String label, String setup,
            String command) throws Exception {
        int trials = Integer.parseInt(options.getOrDefault("trials", "50"));
        Path report = newReport("failover-" + label, ".md");
        FailoverBenchmark bench = new FailoverBenchmark(cluster);
        List<FailoverBenchmark.Trial> results = new ArrayList<>();
        for (int t = 1; t <= trials; t++) {
            FailoverBenchmark.Trial trial = bench.trial(t);
            results.add(trial);
            System.out.printf("trial %d: killed node%d (term %d); node%d leader in term %d; failover %.0f ms"
                            + " (last seen alive %.0f ms, first write OK %.0f ms after the kill command started)%n", t,
                    trial.killedNode(), trial.oldTerm(), trial.newLeader(), trial.newTerm(),
                    trial.firstWriteMs() - trial.lastAliveMs(), trial.lastAliveMs(), trial.firstWriteMs());
        }
        cluster.awaitStable(Duration.ofSeconds(60));
        Reports.writeFailover(report, label, command, setup, results, cluster.statusOfAll());
        System.out.println("report: " + report);
    }

    static double ms(org.HdrHistogram.Histogram h, double percentile) {
        return h.getValueAtPercentile(percentile) / 1000.0;
    }

    /** reports/bench/{name}-{commit}[-dirty]-{UTC time}{suffix}; refuses to reuse a name. */
    private static Path newReport(String name, String suffix) throws IOException {
        String commit = ClusterProbe.tryRun("git", "rev-parse", "--short", "HEAD");
        String dirty = ClusterProbe.tryRun("git", "status", "--porcelain", "--untracked-files=no");
        String stamp = (commit.isEmpty() ? "unknown" : commit) + (dirty.isEmpty() ? "" : "-dirty") + "-"
                + DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").format(ZonedDateTime.now(ZoneOffset.UTC));
        Path report = Path.of("reports", "bench", name + "-" + stamp + suffix);
        if (Files.exists(report)) {
            throw new IllegalStateException("refusing to overwrite " + report); // reports are evidence
        }
        Files.createDirectories(report.getParent());
        return report;
    }

    /** Parses "--name value" pairs. */
    private static Map<String, String> parse(String[] args) {
        Map<String, String> options = new HashMap<>();
        for (int i = 0; i + 1 < args.length; i += 2) {
            options.put(args[i].replaceFirst("^--", ""), args[i + 1]);
        }
        return options;
    }
}
