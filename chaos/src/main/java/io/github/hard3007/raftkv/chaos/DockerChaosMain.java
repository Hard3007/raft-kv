package io.github.hard3007.raftkv.chaos;

import io.github.hard3007.raftkv.client.BlockingKvClient;
import io.github.hard3007.raftkv.client.KvClient;
import io.github.hard3007.raftkv.client.KvResult;
import io.github.hard3007.raftkv.linearizability.CheckResult;
import io.github.hard3007.raftkv.linearizability.History;
import io.github.hard3007.raftkv.linearizability.LinearizabilityChecker;
import io.github.hard3007.raftkv.linearizability.Operation;
import io.github.hard3007.raftkv.linearizability.Verdict;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;

/**
 * Chaos against the real 5-container cluster from docker-compose.yml. Client threads run the
 * same workload as the in-process test against the published gRPC ports, while a nemesis uses:
 * <ul>
 *   <li>{@code docker kill} (SIGKILL: a process crash; the container restarts from its volume),
 *   <li>{@code docker pause} (freezes the process, like a long GC pause; a paused leader wakes up
 *       deposed), and
 *   <li>{@code docker network disconnect} (cuts the container off from peers and clients).
 * </ul>
 * Each fault is undone 2-5 s later, with at most two nodes faulty at once. Then the history,
 * timed with System.nanoTime, goes through the same linearizability checker. Peak memory per
 * container (from docker stats) goes into the report.
 *
 * <pre>  scripts/docker-chaos.sh 120     (starts the cluster, runs this for 120 s, writes the report)</pre>
 */
public final class DockerChaosMain {

    private enum Fault { KILL, PAUSE, DISCONNECT }

    /** A fault in effect, and when to undo it. */
    private record ActiveFault(int node, Fault fault, long undoAtNanos) {
    }

    private final String project;
    private final String network;
    private final Random random = new Random();
    private final List<ActiveFault> active = new ArrayList<>();
    private final Map<Fault, Integer> injected = new TreeMap<>();
    private final Map<String, Double> peakMemoryMib = new TreeMap<>();
    private String memoryLimit = "?";

    private DockerChaosMain(String project) {
        this.project = project;
        this.network = project + "_default";
    }

    public static void main(String[] args) throws Exception {
        Map<String, String> options = new HashMap<>();
        for (int i = 0; i + 1 < args.length; i += 2) {
            options.put(args[i].replaceFirst("^--", ""), args[i + 1]);
        }
        int seconds = Integer.parseInt(options.getOrDefault("duration", "60"));
        int clients = Integer.parseInt(options.getOrDefault("clients", "4"));
        String project = options.getOrDefault("project", "raftkv");
        Path report = Path.of(options.getOrDefault("report", "reports/chaos/docker-chaos-" + seconds + "s.md"));
        System.exit(new DockerChaosMain(project).run(seconds, clients, report) ? 0 : 1);
    }

    private String container(int node) {
        return project + "-node" + node + "-1";
    }

    private boolean run(int seconds, int clientCount, Path report) throws Exception {
        Map<Integer, String> servers = new TreeMap<>();
        for (int node = 1; node <= 5; node++) {
            servers.put(node, "localhost:" + (7000 + node));
        }
        System.out.printf("docker chaos: %d s, %d clients, containers %s..%s%n", seconds, clientCount, container(1),
                container(5));
        try (BlockingKvClient warmup = new BlockingKvClient(1, servers, new KvClient.Options(1000, 100, 60_000))) {
            if (!(warmup.put("warmup".getBytes(StandardCharsets.UTF_8), new byte[] {1}) instanceof KvResult.Ok<?>)) {
                throw new IllegalStateException("cluster did not accept a write within 60 s; is it up?");
            }
        }

        History history = History.withNanoTime();
        List<BlockingKvClient> clients = new ArrayList<>();
        List<BlockingWorkload> workloads = new ArrayList<>();
        List<Thread> threads = new ArrayList<>();
        for (int c = 0; c < clientCount; c++) {
            BlockingKvClient client = new BlockingKvClient(random.nextLong() & Long.MAX_VALUE | 1, servers,
                    new KvClient.Options(1000, 50, 4000));
            BlockingWorkload workload = new BlockingWorkload(c, client, history, new Random(), Integer.MAX_VALUE, 3, 50);
            clients.add(client);
            workloads.add(workload);
            Thread thread = new Thread(workload, "docker-workload-" + c);
            threads.add(thread);
            thread.start();
        }

        long start = System.nanoTime();
        long end = start + Duration.ofSeconds(seconds).toNanos();
        long nextFault = start;
        long nextMemorySample = start;
        while (System.nanoTime() < end) {
            long now = System.nanoTime();
            undoDueFaults(now);
            if (now >= nextFault) {
                injectFault(now);
                nextFault = now + Duration.ofMillis(1000 + random.nextInt(2000)).toNanos();
            }
            if (now >= nextMemorySample) {
                sampleMemory();
                nextMemorySample = now + Duration.ofSeconds(5).toNanos();
            }
            Thread.sleep(100); // nemesis tick
        }
        undoDueFaults(Long.MAX_VALUE);
        sampleMemory();
        Thread.sleep(5000); // let the cluster settle so in-flight operations can finish normally
        workloads.forEach(BlockingWorkload::stop);
        for (Thread thread : threads) {
            thread.join();
        }
        clients.forEach(BlockingKvClient::close);
        long wallMillis = (System.nanoTime() - start) / 1_000_000;

        List<Operation> operations = history.operations();
        long checkStart = System.nanoTime();
        CheckResult result = new LinearizabilityChecker(Duration.ofSeconds(60)).check(operations);
        long checkMillis = (System.nanoTime() - checkStart) / 1_000_000;
        int completed = workloads.stream().mapToInt(BlockingWorkload::completed).sum();
        int pending = workloads.stream().mapToInt(BlockingWorkload::pendingWrites).sum();
        int discarded = workloads.stream().mapToInt(BlockingWorkload::discardedReads).sum();
        writeReport(report, seconds, clientCount, operations.size() + discarded, completed, pending, discarded, result,
                wallMillis, checkMillis);
        System.out.printf("verdict %s: %d operations (%d completed, %d pending writes, %d reads left out); faults %s;"
                + " peak memory %s (limit %s); report %s%n", result.verdict(), operations.size() + discarded, completed,
                pending, discarded, injected, peakMemoryMib, memoryLimit, report);
        return result.verdict() == Verdict.LINEARIZABLE;
    }

    // ------------------------------------------------------------------ nemesis

    private void injectFault(long now) {
        if (active.size() >= 2) {
            return; // keep a majority healthy
        }
        int node = 1 + random.nextInt(5);
        if (active.stream().anyMatch(f -> f.node() == node)) {
            return;
        }
        Fault fault = Fault.values()[random.nextInt(Fault.values().length)];
        boolean ok = switch (fault) {
            case KILL -> docker("kill", container(node));
            case PAUSE -> docker("pause", container(node));
            case DISCONNECT -> docker("network", "disconnect", network, container(node));
        };
        if (ok) {
            active.add(new ActiveFault(node, fault, now + Duration.ofMillis(2000 + random.nextInt(3000)).toNanos()));
            injected.merge(fault, 1, Integer::sum);
            System.out.printf("  %s node%d%n", fault, node);
        }
    }

    private void undoDueFaults(long now) {
        active.removeIf(f -> {
            if (f.undoAtNanos() > now) {
                return false;
            }
            switch (f.fault()) {
                case KILL -> docker("start", container(f.node())); // a no-op if Docker already restarted it
                case PAUSE -> docker("unpause", container(f.node()));
                case DISCONNECT -> docker("network", "connect", "--alias", "node" + f.node(), network, container(f.node()));
            }
            return true;
        });
    }

    private void sampleMemory() {
        String stats = run("docker", "stats", "--no-stream", "--format", "{{.Name}} {{.MemUsage}}");
        for (String line : stats.split("\n")) {
            String[] parts = line.trim().split("\\s+"); // name used / limit
            if (parts.length < 4 || !parts[0].startsWith(project + "-node")) {
                continue;
            }
            peakMemoryMib.merge(parts[0], toMib(parts[1]), Math::max);
            memoryLimit = parts[3];
        }
    }

    private static double toMib(String amount) {
        double value = Double.parseDouble(amount.replaceAll("[^0-9.]", ""));
        if (amount.endsWith("GiB")) {
            return value * 1024;
        }
        if (amount.endsWith("KiB")) {
            return value / 1024;
        }
        return amount.endsWith("MiB") ? value : value / (1024 * 1024);
    }

    private static boolean docker(String... args) {
        String[] command = new String[args.length + 1];
        command[0] = "docker";
        System.arraycopy(args, 0, command, 1, args.length);
        return run(command) != null;
    }

    /** Runs a command; returns its output, or null if it failed. */
    private static String run(String... command) {
        try {
            Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (process.waitFor() != 0) {
                System.out.println("  command failed: " + String.join(" ", command) + ": " + output.trim());
                return null;
            }
            return output;
        } catch (IOException e) {
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    // ------------------------------------------------------------------ report

    private void writeReport(Path path, int seconds, int clientCount, int operations, int completed, int pending,
            int discarded, CheckResult result, long wallMillis, long checkMillis) throws IOException {
        String commit = run("git", "rev-parse", "--short", "HEAD");
        String dirty = run("git", "status", "--porcelain", "--untracked-files=no");
        StringBuilder md = new StringBuilder("# Docker chaos report\n\n");
        md.append("- **Command:** `scripts/docker-chaos.sh ").append(seconds).append("`\n");
        md.append("- **Date:** ").append(ZonedDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)).append("\n");
        md.append("- **Git commit:** ").append(commit == null ? "unknown" : commit.trim())
                .append(dirty != null && !dirty.isBlank() ? " (with uncommitted changes)" : "").append("\n");
        md.append("- **Machine:** ").append(ChaosMain.machine()).append(", Docker ")
                .append(String.valueOf(run("docker", "version", "--format", "{{.Server.Version}}")).trim()).append("\n");
        md.append("- **Setup:** 5 containers (docker-compose.yml), ").append(clientCount)
                .append(" client threads on the host, 3 shared keys; a fault every 1-3 s, each undone after 2-5 s,"
                        + " at most two nodes faulty at once\n\n");
        md.append("## Verdict\n\n**").append(result.verdict()).append("**")
                .append(result.key() == null ? "" : " (key " + result.key() + ")").append(", checked in ")
                .append(checkMillis).append(" ms.\n\n");
        md.append("| Measure | Value |\n|---|---|\n");
        md.append("| fault-injection window | ").append(seconds).append(" s (total wall time ")
                .append(wallMillis / 1000).append(" s) |\n");
        md.append("| operations | ").append(operations).append(" |\n");
        md.append("| completed | ").append(completed).append(" |\n");
        md.append("| pending writes (unknown outcome) | ").append(pending).append(" |\n");
        md.append("| reads left out (no definite answer) | ").append(discarded).append(" |\n");
        for (Fault fault : Fault.values()) {
            md.append("| `docker ").append(switch (fault) {
                case KILL -> "kill";
                case PAUSE -> "pause";
                case DISCONNECT -> "network disconnect";
            }).append("` | ").append(injected.getOrDefault(fault, 0)).append(" |\n");
        }
        md.append("\n## Peak memory per container (docker stats, sampled every 5 s)\n\n| Container | Peak | Limit |\n|---|---|---|\n");
        peakMemoryMib.forEach((name, mib) -> md.append("| ").append(name).append(" | ")
                .append(String.format("%.0f MiB", mib)).append(" | ").append(memoryLimit).append(" |\n"));
        md.append("\n`docker kill` is a process crash (SIGKILL): the host's page cache survives, so data written but not"
                + " fsynced is not lost. Power loss is covered by the simulator (DESIGN.md).\n");
        Files.createDirectories(path.toAbsolutePath().getParent());
        Files.writeString(path, md.toString(), StandardCharsets.UTF_8);
    }
}
