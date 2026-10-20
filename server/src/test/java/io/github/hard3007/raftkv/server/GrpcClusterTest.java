package io.github.hard3007.raftkv.server;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.hard3007.raftkv.chaos.BlockingWorkload;
import io.github.hard3007.raftkv.client.BlockingKvClient;
import io.github.hard3007.raftkv.client.KvClient;
import io.github.hard3007.raftkv.client.KvResult;
import io.github.hard3007.raftkv.linearizability.CheckResult;
import io.github.hard3007.raftkv.linearizability.History;
import io.github.hard3007.raftkv.linearizability.LinearizabilityChecker;
import io.github.hard3007.raftkv.linearizability.Operation;
import io.github.hard3007.raftkv.linearizability.Verdict;
import io.github.hard3007.raftkv.raft.RaftConfig;
import io.github.hard3007.raftkv.raft.RaftNode;
import java.io.IOException;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The Phase 5 gate: five real nodes in one JVM, each with its own executor thread, talking real
 * gRPC over localhost, with data on real disk. Unlike the simulator, this exercises real
 * concurrency: gRPC threads handing work to node threads, client threads, timers racing with
 * replies. The chaos test checks the resulting history with the same linearizability checker.
 */
class GrpcClusterTest {

    private static final int NODES = 5;

    @TempDir
    Path dataRoot;

    private final Map<Integer, String> cluster = new TreeMap<>();
    private final Map<Integer, Integer> ports = new TreeMap<>();
    private final RaftKvNode[] nodes = new RaftKvNode[NODES + 1];
    private final List<RaftKvNode> everyIncarnation = new ArrayList<>();
    private final List<BlockingKvClient> clients = new ArrayList<>();

    private void startCluster() throws IOException {
        for (int id = 1; id <= NODES; id++) {
            int port = freePort();
            ports.put(id, port);
            cluster.put(id, "127.0.0.1:" + port);
        }
        for (int id = 1; id <= NODES; id++) {
            start(id);
        }
    }

    private void start(int id) throws IOException {
        nodes[id] = RaftKvNode.start(new NodeConfig(id, cluster, ports.get(id), 0, dataRoot.resolve("node-" + id),
                RaftConfig.defaults(), 300));
        everyIncarnation.add(nodes[id]);
    }

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private BlockingKvClient newClient(long id, KvClient.Options options) {
        BlockingKvClient client = new BlockingKvClient(id, cluster, options);
        clients.add(client);
        return client;
    }

    @AfterEach
    void stopEverything() {
        clients.forEach(BlockingKvClient::close);
        for (int id = 1; id <= NODES; id++) {
            if (nodes[id] != null) {
                nodes[id].crash();
            }
        }
    }

    private static byte[] b(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    @Test
    void putGetAndOneLeaderOverRealGrpc() throws Exception {
        startCluster();
        BlockingKvClient client = newClient(1, new KvClient.Options(1000, 50, 15_000));
        assertInstanceOf(KvResult.Ok.class, client.put(b("k"), b("v")));
        KvResult<Optional<byte[]>> read = client.get(b("k"));
        assertArrayEquals(b("v"), ((KvResult.Ok<Optional<byte[]>>) read).value().orElseThrow());

        int leaders = 0;
        for (int id = 1; id <= NODES; id++) {
            leaders += nodes[id].status().role() == RaftNode.Role.LEADER ? 1 : 0;
        }
        assertEquals(1, leaders);
        everyIncarnation.forEach(node -> assertEquals(0, node.failedTasks(), "a node task threw"));
    }

    /**
     * Four client threads run random operations on three shared keys while a nemesis crashes and
     * restarts nodes (from their on-disk data), isolates nodes, and kills live gRPC connections.
     * Times come from System.nanoTime. The history must be linearizable, and no task on any
     * node thread may have failed (a thread-confinement violation would show up there).
     */
    @Test
    void chaosOnRealThreadsIsLinearizable() throws Exception {
        startCluster();
        Random random = new Random(20261020);
        KvClient.Options options = new KvClient.Options(1000, 50, 4000);
        assertInstanceOf(KvResult.Ok.class, newClient(99, new KvClient.Options(1000, 50, 15_000)).put(b("warmup"), b("1")));

        History history = History.withNanoTime();
        AtomicInteger finishedClients = new AtomicInteger();
        List<Thread> clientThreads = new ArrayList<>();
        for (int c = 0; c < 4; c++) {
            int clientNumber = c;
            BlockingKvClient client = newClient(1000 + c, options);
            BlockingWorkload workload = new BlockingWorkload(clientNumber, client, history, new Random(clientNumber), 150,
                    3, 60);
            Thread thread = new Thread(() -> {
                workload.run();
                finishedClients.incrementAndGet();
            }, "workload-" + c);
            clientThreads.add(thread);
            thread.start();
        }

        // The nemesis acts every 300-700 ms until every client has finished.
        int crashes = 0;
        int isolations = 0;
        int connectionDrops = 0;
        while (finishedClients.get() < clientThreads.size()) {
            Thread.sleep(300 + random.nextInt(400)); // pacing of fault injection, not a correctness wait
            int id = 1 + random.nextInt(NODES);
            switch (random.nextInt(4)) {
                case 0 -> {
                    if (nodes[id] != null && down() < 2) {
                        nodes[id].crash();
                        nodes[id] = null;
                        crashes++;
                    } else if (nodes[id] == null) {
                        start(id); // recovers from its data directory
                    }
                }
                case 1 -> {
                    isolate(id);
                    isolations++;
                }
                case 2 -> {
                    if (nodes[id] != null) {
                        nodes[id].transport().dropConnections();
                        connectionDrops++;
                    }
                }
                default -> healPartitions();
            }
        }
        for (Thread thread : clientThreads) {
            thread.join();
        }
        healPartitions();
        for (int id = 1; id <= NODES; id++) {
            if (nodes[id] == null) {
                start(id);
            }
        }

        List<Operation> operations = history.operations();
        CheckResult result = new LinearizabilityChecker(Duration.ofSeconds(30)).check(operations);
        long completed = operations.stream().filter(op -> !op.isPending()).count();
        System.out.printf("grpc chaos: %d operations (%d completed), %d crashes, %d isolations, %d connection drops,"
                + " verdict %s%n", operations.size(), completed, crashes, isolations, connectionDrops, result.verdict());
        assertEquals(Verdict.LINEARIZABLE, result.verdict(), "key " + result.key() + ": " + result.operations());
        assertTrue(completed > 200, "too little progress: " + completed);
        assertTrue(crashes > 0 && isolations > 0 && connectionDrops > 0, "every fault type should have happened");
        everyIncarnation.forEach(node -> assertEquals(0, node.failedTasks(), "a task on node " + node.id() + " threw"));
    }

    private int down() {
        int down = 0;
        for (int id = 1; id <= NODES; id++) {
            down += nodes[id] == null ? 1 : 0;
        }
        return down;
    }

    /** Cuts node {@code isolated} off from every other node, in both directions. */
    private void isolate(int isolated) {
        healPartitions();
        for (int id = 1; id <= NODES; id++) {
            if (nodes[id] == null) {
                continue;
            }
            Set<Integer> blocked = new HashSet<>();
            if (id == isolated) {
                for (int other = 1; other <= NODES; other++) {
                    if (other != id) {
                        blocked.add(other);
                    }
                }
            } else {
                blocked.add(isolated);
            }
            nodes[id].transport().setBlockedPeers(blocked);
        }
    }

    private void healPartitions() {
        for (int id = 1; id <= NODES; id++) {
            if (nodes[id] != null) {
                nodes[id].transport().setBlockedPeers(Set.of());
            }
        }
    }
}
