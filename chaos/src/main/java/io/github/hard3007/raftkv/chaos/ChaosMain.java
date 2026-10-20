package io.github.hard3007.raftkv.chaos;

import io.github.hard3007.raftkv.linearizability.Operation;
import io.github.hard3007.raftkv.linearizability.Verdict;
import io.github.hard3007.raftkv.linearizability.Witness;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.ToDoubleFunction;

/**
 * Runs many seeded chaos runs and writes a Markdown report plus a per-seed CSV.
 *
 * <pre>
 *   ./gradlew chaos -Pruns=500                  # seeds 1..500
 *   ./gradlew chaos -Pruns=1 -PstartSeed=137 -Pverbose   # replay one seed, print its history
 * </pre>
 *
 * Exits with status 1 if any run is NOT_LINEARIZABLE or INCONCLUSIVE (INCONCLUSIVE never passes).
 * Seeds run in parallel; each run is independent and deterministic, so parallelism does not
 * change any result.
 */
public final class ChaosMain {

    private ChaosMain() {
    }

    public static void main(String[] args) throws Exception {
        Map<String, String> options = parse(args);
        int runs = Integer.parseInt(options.getOrDefault("runs", "50"));
        long startSeed = Long.parseLong(options.getOrDefault("start-seed", "1"));
        String label = options.getOrDefault("label", "baseline");
        boolean verbose = options.containsKey("verbose");
        Path report = Path.of(options.getOrDefault("report", "reports/chaos/chaos-" + label + "-" + runs + "-seeds.md"));
        int threads = Integer.parseInt(options.getOrDefault("threads",
                Integer.toString(Math.max(1, Runtime.getRuntime().availableProcessors()))));
        ChaosConfig config = ChaosConfig.defaults();
        if (options.getOrDefault("check-quorum", "true").equals("false")) {
            // ReadIndex must be safe without CheckQuorum, whose timeouts otherwise shorten the time a
            // deposed leader stays unaware; this variant keeps that dangerous window open.
            config = config.withRaft(config.raft().withCheckQuorum(false));
        }

        System.out.printf("chaos: %d runs from seed %d (%s), %d threads%n", runs, startSeed, label, threads);
        long wallStart = System.currentTimeMillis();
        List<ChaosRun.Result> results = runAll(runs, startSeed, config, threads, verbose);
        long wallMillis = System.currentTimeMillis() - wallStart;

        Map<Verdict, Integer> counts = new EnumMap<>(Verdict.class);
        for (Verdict verdict : Verdict.values()) {
            counts.put(verdict, 0);
        }
        results.stream().filter(r -> r.error() == null).forEach(r -> counts.merge(r.verdict(), 1, Integer::sum));
        long errors = results.stream().filter(r -> r.error() != null).count();

        String command = "./gradlew chaos -Pruns=" + runs + (startSeed != 1 ? " -PstartSeed=" + startSeed : "")
                + (label.equals("baseline") ? "" : " -Plabel=" + label)
                + (config.raft().checkQuorum() ? "" : " -PcheckQuorum=false");
        writeReport(report, label, command, config, results, counts, errors, wallMillis);
        writeCsv(Path.of(report.toString().replaceAll("\\.md$", ".csv")), results);

        for (ChaosRun.Result r : results) {
            if (verbose || !r.passed()) {
                printDetails(r, verbose);
            }
        }
        System.out.printf("LINEARIZABLE=%d NOT_LINEARIZABLE=%d INCONCLUSIVE=%d INTERNAL_FAILURE=%d  wall=%.1fs  report=%s%n",
                counts.get(Verdict.LINEARIZABLE), counts.get(Verdict.NOT_LINEARIZABLE), counts.get(Verdict.INCONCLUSIVE),
                errors, wallMillis / 1000.0, report);
        System.exit(counts.get(Verdict.LINEARIZABLE) == runs && errors == 0 ? 0 : 1);
    }

    private static List<ChaosRun.Result> runAll(int runs, long startSeed, ChaosConfig config, int threads,
            boolean verbose) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Future<ChaosRun.Result>> futures = new ArrayList<>();
            for (long seed = startSeed; seed < startSeed + runs; seed++) {
                long s = seed;
                futures.add(pool.submit(() -> ChaosRun.run(s, config)));
            }
            List<ChaosRun.Result> results = new ArrayList<>();
            int done = 0;
            for (Future<ChaosRun.Result> future : futures) {
                ChaosRun.Result result = future.get();
                // Keep histories only where they explain something, so 10,000 runs fit in memory.
                results.add(verbose || !result.passed() ? result : result.withoutDetails());
                futures.set(done, null);
                if (++done % 50 == 0 || done == runs) {
                    System.out.printf("  %d/%d runs done%n", done, runs);
                }
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }

    // ------------------------------------------------------------------ report

    private static void writeReport(Path path, String label, String command, ChaosConfig config,
            List<ChaosRun.Result> results, Map<Verdict, Integer> counts, long errors, long wallMillis)
            throws IOException {
        StringBuilder md = new StringBuilder();
        md.append("# Chaos report: ").append(label).append("\n\n");
        md.append("- **Command:** `").append(command).append("`\n");
        md.append("- **Date:** ").append(ZonedDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)).append("\n");
        md.append("- **Git commit:** ").append(gitCommit()).append("\n");
        md.append("- **Machine:** ").append(machine()).append("\n");
        md.append("- **Config:** ").append(config.describe()).append("\n");
        md.append("- **Seeds:** ").append(results.get(0).seed()).append("..")
                .append(results.get(results.size() - 1).seed()).append(" (").append(results.size()).append(" runs)\n");
        md.append("- **Total wall time:** ").append(String.format("%.1f s", wallMillis / 1000.0)).append("\n\n");

        md.append("## Verdicts\n\n| Verdict | Runs |\n|---|---|\n");
        for (Verdict verdict : Verdict.values()) {
            md.append("| ").append(verdict).append(" | ").append(counts.get(verdict)).append(" |\n");
        }
        md.append("| runs where the system failed an internal check | ").append(errors).append(" |\n");
        md.append("\nOnly LINEARIZABLE runs count as passing. INCONCLUSIVE means the checker ran out of time. An"
                + " internal-check failure (a safety assertion, or a node refusing to start because committed"
                + " entries were lost) has no verdict and does not pass either.\n\n");
        List<ChaosRun.Result> measured = results.stream().filter(r -> r.stats() != null).toList();

        md.append("## Per seed (min / mean / max)\n\n| Measure | Min | Mean | Max |\n|---|---|---|---|\n");
        row(md, "operations", measured, r -> r.stats().operations());
        row(md, "completed operations", measured, r -> r.stats().completed());
        row(md, "pending writes (unknown outcome)", measured, r -> r.stats().pendingWrites());
        row(md, "reads left out (no definite answer)", measured, r -> r.stats().discardedReads());
        row(md, "node crashes (power loss)", measured, r -> r.stats().crashes());
        row(md, "  of which mid-I/O (crash at a random write/fsync/rename)", measured, r -> r.stats().midIoCrashes());
        row(md, "node pauses (frozen 0.5-2 s)", measured, r -> r.stats().pauses());
        row(md, "partitions", measured, r -> r.stats().partitions());
        row(md, "message-loss periods", measured, r -> r.stats().lossPeriods());
        row(md, "messages lost", measured, r -> r.stats().messagesLost());
        row(md, "long-delay periods", measured, r -> r.stats().longDelayPeriods());
        row(md, "snapshots taken (all nodes)", measured, r -> r.stats().snapshotsTaken());
        row(md, "InstallSnapshots completed (all nodes)", measured, r -> r.stats().snapshotsInstalled());
        row(md, "final term (highest across nodes)", measured, r -> r.stats().finalTerm());
        row(md, "virtual time (s)", measured, r -> r.stats().virtualMillis() / 1000.0);
        row(md, "wall time (ms)", measured, r -> r.wallMillis());

        List<ChaosRun.Result> failing = results.stream().filter(r -> !r.passed()).toList();
        md.append("\n## Runs that did not pass\n\n");
        if (failing.isEmpty()) {
            md.append("None.\n");
        } else {
            md.append("| Seed | Verdict | Key / failure | Ops on key | Reproduce |\n|---|---|---|---|---|\n");
            for (ChaosRun.Result r : failing) {
                boolean error = r.error() != null;
                md.append("| ").append(r.seed()).append(" | ").append(error ? "INTERNAL_FAILURE" : r.verdict()).append(" | ")
                        .append(error ? r.error().replace("|", "/") : r.check().key())
                        .append(" | ").append(error ? "-" : r.check().operations().size())
                        .append(" | `./gradlew chaos -Pruns=1 -PstartSeed=")
                        .append(r.seed()).append(label.equals("baseline") ? "" : " -Plabel=" + label)
                        .append(" -Pverbose` |\n");
            }
        }
        md.append("\nPer-seed numbers: `").append(path.getFileName().toString().replaceAll("\\.md$", ".csv")).append("`.\n");
        Files.createDirectories(path.toAbsolutePath().getParent());
        Files.writeString(path, md.toString(), StandardCharsets.UTF_8);
    }

    private static void row(StringBuilder md, String name, List<ChaosRun.Result> results,
            ToDoubleFunction<ChaosRun.Result> metric) {
        double min = results.stream().mapToDouble(metric).min().orElse(0);
        double max = results.stream().mapToDouble(metric).max().orElse(0);
        double mean = results.stream().mapToDouble(metric).average().orElse(0);
        md.append(String.format("| %s | %s | %.1f | %s |%n", name, trim(min), mean, trim(max)));
    }

    private static String trim(double value) {
        return value == Math.rint(value) ? Long.toString((long) value) : String.format("%.1f", value);
    }

    private static void writeCsv(Path path, List<ChaosRun.Result> results) throws IOException {
        StringBuilder csv = new StringBuilder("seed,verdict,operations,completed,pending_writes,discarded_reads,"
                + "crashes,mid_io_crashes,pauses,partitions,loss_periods,messages_lost,long_delay_periods,snapshots_taken,"
                + "snapshots_installed,final_term,virtual_ms,wall_ms\n");
        for (ChaosRun.Result r : results) {
            ChaosRun.Stats s = r.stats();
            if (s == null) {
                csv.append(r.seed()).append(",INTERNAL_FAILURE\n");
                continue;
            }
            csv.append(r.seed()).append(',').append(r.verdict()).append(',').append(s.operations()).append(',')
                    .append(s.completed()).append(',').append(s.pendingWrites()).append(',').append(s.discardedReads())
                    .append(',').append(s.crashes()).append(',').append(s.midIoCrashes()).append(',').append(s.pauses()).append(',').append(s.partitions()).append(',')
                    .append(s.lossPeriods()).append(',').append(s.messagesLost()).append(',')
                    .append(s.longDelayPeriods()).append(',').append(s.snapshotsTaken()).append(',')
                    .append(s.snapshotsInstalled()).append(',').append(s.finalTerm()).append(',')
                    .append(s.virtualMillis()).append(',')
                    .append(r.wallMillis()).append('\n');
        }
        Files.writeString(path, csv.toString(), StandardCharsets.UTF_8);
    }

    private static void printDetails(ChaosRun.Result r, boolean verbose) {
        if (r.error() != null) {
            System.out.printf("%nseed %d: INTERNAL_FAILURE %s%n", r.seed(), r.error());
            if (verbose) {
                r.faultLog().forEach(line -> System.out.println("    " + line));
            }
            return;
        }
        System.out.printf("%nseed %d: %s%s%n", r.seed(), r.verdict(),
                r.check().key() == null ? "" : " (key " + r.check().key() + ", " + r.check().operations().size() + " ops)");
        if (verbose) {
            System.out.println("  faults:");
            r.faultLog().forEach(line -> System.out.println("    " + line));
            if (r.verdict() == Verdict.NOT_LINEARIZABLE) {
                // The shortest story: the history cut at the earliest moment it was already broken.
                List<Operation> cut = Witness.earliestViolation(r.check().operations(), Duration.ofSeconds(10));
                List<Operation> sorted = cut.stream().sorted(Comparator.comparingLong(Operation::invokeTime)).toList();
                System.out.println("  earliest violation on key " + r.check().key() + " (last 15 of " + cut.size()
                        + " operations; times are virtual ms#event-order):");
                sorted.subList(Math.max(0, sorted.size() - 15), sorted.size())
                        .forEach(op -> System.out.println("    " + op.describe(ChaosMain::virtualTime)));
            } else {
                System.out.println("  history:");
                r.history().stream().sorted(Comparator.comparingLong(Operation::invokeTime))
                        .forEach(op -> System.out.println("    " + op.describe(ChaosMain::virtualTime)));
            }
        }
    }

    /** History times are virtual milliseconds * 1e6 + the order of events within that millisecond. */
    private static String virtualTime(long time) {
        return (time / 1_000_000) + "ms#" + (time % 1_000_000);
    }

    // ------------------------------------------------------------------ environment

    private static String gitCommit() {
        String commit = run("git", "rev-parse", "--short", "HEAD");
        String dirty = run("git", "status", "--porcelain", "--untracked-files=no");
        if (commit.isEmpty()) {
            return "unknown";
        }
        return commit + (dirty.isEmpty() ? "" : " (with uncommitted changes)");
    }

    static String machine() {
        String cpu = "";
        try {
            cpu = Files.readAllLines(Path.of("/proc/cpuinfo")).stream()
                    .filter(line -> line.startsWith("model name") || line.startsWith("CPU part"))
                    .findFirst().map(line -> line.substring(line.indexOf(':') + 1).trim()).orElse("");
        } catch (IOException | UncheckedIOException e) {
            // not Linux; leave it out
        }
        return System.getProperty("os.name") + " " + System.getProperty("os.version") + " "
                + System.getProperty("os.arch") + ", " + Runtime.getRuntime().availableProcessors() + " cores"
                + (cpu.isEmpty() ? "" : " (" + cpu + ")") + ", Java " + System.getProperty("java.version")
                + ", max heap " + Runtime.getRuntime().maxMemory() / (1 << 20) + " MB";
    }

    private static String run(String... command) {
        try {
            Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            return process.waitFor() == 0 ? output : "";
        } catch (IOException e) {
            return "";
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "";
        }
    }

    /** Parses "--name value" and bare "--flag" arguments. */
    private static Map<String, String> parse(String[] args) {
        Map<String, String> options = new java.util.HashMap<>();
        for (int i = 0; i < args.length; i++) {
            String name = args[i].replaceFirst("^--", "");
            if (i + 1 < args.length && !args[i + 1].startsWith("--")) {
                options.put(name, args[++i]);
            } else {
                options.put(name, "true");
            }
        }
        return options;
    }
}
