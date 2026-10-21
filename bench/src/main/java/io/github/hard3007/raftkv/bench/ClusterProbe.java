package io.github.hard3007.raftkv.bench;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Talks to the 5-container cluster from docker-compose.yml the way a benchmark needs: the client
 * addresses (gRPC on localhost:7001-7005), each node's /status (localhost:8081-8085), and docker
 * commands (kill, start).
 */
final class ClusterProbe {

    static final int NODES = 5;

    /** One node's /status, reduced to what the benchmarks look at. */
    record NodeStatus(int id, String role, long term, int leaderId, long commitIndex, long lastApplied,
            long slowestTaskMs, long latestTaskStartMs, long slowTasks) {
    }

    private static final Pattern NUMBER = Pattern.compile("\"(\\w+)\":(-?\\d+)");
    private static final Pattern ROLE = Pattern.compile("\"role\":\"(\\w+)\"");

    private final String project;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofMillis(500)).build();

    ClusterProbe(String project) {
        this.project = project;
    }

    /** Every node's client address by node id. */
    Map<Integer, String> servers() {
        Map<Integer, String> servers = new TreeMap<>();
        for (int node = 1; node <= NODES; node++) {
            servers.put(node, "localhost:" + (7000 + node));
        }
        return servers;
    }

    String container(int node) {
        return project + "-node" + node + "-1";
    }

    /** The node's /status, or null if it did not answer within half a second. */
    NodeStatus status(int node) {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + (8080 + node) + "/status"))
                .timeout(Duration.ofMillis(500)).build();
        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return null;
            }
            Map<String, Long> numbers = new TreeMap<>();
            Matcher m = NUMBER.matcher(response.body());
            while (m.find()) {
                numbers.put(m.group(1), Long.parseLong(m.group(2)));
            }
            Matcher role = ROLE.matcher(response.body());
            return new NodeStatus(node, role.find() ? role.group(1) : "?", numbers.getOrDefault("term", -1L),
                    numbers.getOrDefault("leaderId", 0L).intValue(), numbers.getOrDefault("commitIndex", -1L),
                    numbers.getOrDefault("lastApplied", -1L), numbers.getOrDefault("slowestTaskMs", -1L),
                    numbers.getOrDefault("latestTaskStartMs", -1L), numbers.getOrDefault("slowTasks", -1L));
        } catch (IOException e) {
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    /** Every node that answered, by id. */
    Map<Integer, NodeStatus> statusOfAll() {
        Map<Integer, NodeStatus> all = new TreeMap<>();
        for (int node = 1; node <= NODES; node++) {
            NodeStatus s = status(node);
            if (s != null) {
                all.put(node, s);
            }
        }
        return all;
    }

    /** The cluster's term and leader at one moment: the highest term any node reports, and its leader. */
    record TermSnapshot(long term, int leader, int nodesAnswering) {
        @Override
        public String toString() {
            return "term " + term + ", leader " + (leader == 0 ? "?" : "node" + leader);
        }
    }

    TermSnapshot termSnapshot() {
        Map<Integer, NodeStatus> all = statusOfAll();
        long term = all.values().stream().mapToLong(NodeStatus::term).max().orElse(-1);
        int leader = all.values().stream().filter(s -> s.term() == term && s.leaderId() > 0)
                .mapToInt(NodeStatus::leaderId).findFirst().orElse(0);
        return new TermSnapshot(term, leader, all.size());
    }

    /**
     * Stable means: all 5 nodes answer, all are in the same term, all name the same leader, that
     * leader says it is LEADER, and every node has applied everything the leader had committed.
     */
    boolean isStable() {
        Map<Integer, NodeStatus> all = statusOfAll();
        if (all.size() != NODES) {
            return false;
        }
        NodeStatus any = all.values().iterator().next();
        NodeStatus leader = all.get(any.leaderId());
        if (leader == null || !leader.role().equals("LEADER")) {
            return false;
        }
        return all.values().stream().allMatch(s -> s.term() == leader.term() && s.leaderId() == leader.id()
                && s.lastApplied() >= leader.commitIndex());
    }

    /** Waits until {@link #isStable()}; throws after {@code timeout}. */
    void awaitStable(Duration timeout) throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (!isStable()) {
            if (System.nanoTime() > deadline) {
                throw new IllegalStateException("cluster not stable after " + timeout + ": " + statusOfAll());
            }
            Thread.sleep(100);
        }
    }

    /** Runs {@code docker <args>}; returns its output, or throws if it failed. */
    static String docker(String... args) {
        String[] command = new String[args.length + 1];
        command[0] = "docker";
        System.arraycopy(args, 0, command, 1, args.length);
        return run(command);
    }

    /** Runs a command; returns its trimmed output, or throws if it exits non-zero. */
    static String run(String... command) {
        try {
            Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            if (process.waitFor() != 0) {
                throw new IllegalStateException(String.join(" ", command) + " failed: " + output);
            }
            return output;
        } catch (IOException e) {
            throw new IllegalStateException(String.join(" ", command) + " failed", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted", e);
        }
    }

    /** Like {@link #run}, but returns "" instead of throwing (for optional facts in reports). */
    static String tryRun(String... command) {
        try {
            return run(command);
        } catch (IllegalStateException e) {
            return "";
        }
    }
}
