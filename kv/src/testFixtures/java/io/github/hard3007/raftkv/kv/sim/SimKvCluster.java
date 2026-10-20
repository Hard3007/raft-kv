package io.github.hard3007.raftkv.kv.sim;

import io.github.hard3007.raftkv.client.ClientTransport;
import io.github.hard3007.raftkv.client.KvClient;
import io.github.hard3007.raftkv.client.KvResult;
import io.github.hard3007.raftkv.kv.KvService;
import io.github.hard3007.raftkv.kv.KvStateMachine;
import io.github.hard3007.raftkv.proto.kv.DeleteReply;
import io.github.hard3007.raftkv.proto.kv.DeleteRequest;
import io.github.hard3007.raftkv.proto.kv.GetReply;
import io.github.hard3007.raftkv.proto.kv.GetRequest;
import io.github.hard3007.raftkv.proto.kv.PutReply;
import io.github.hard3007.raftkv.proto.kv.PutRequest;
import io.github.hard3007.raftkv.proto.raft.LogEntry;
import io.github.hard3007.raftkv.raft.RaftConfig;
import io.github.hard3007.raftkv.raft.RaftNode;
import io.github.hard3007.raftkv.raft.RaftStorage;
import io.github.hard3007.raftkv.raft.sim.SimNetwork;
import io.github.hard3007.raftkv.raft.sim.Simulation;
import io.github.hard3007.raftkv.storage.BTree;
import io.github.hard3007.raftkv.storage.FsyncPolicy;
import io.github.hard3007.raftkv.storage.SimulatedFileSystem;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Consumer;

/**
 * A complete simulated key-value cluster: real {@link RaftNode}s, {@link KvStateMachine}s on
 * B-Trees (lazy fsync, as in production), {@link KvService}s and {@link KvClient}s, all on one
 * deterministic {@link Simulation}. Client requests travel over the same faulty
 * {@link SimNetwork} as Raft messages. Servers are numbered 1..n and clients from 1001.
 *
 * <p>Like the Raft test harness, it checks Election Safety after every event, and State Machine
 * Safety plus in-order apply on every apply. A crash is a power loss on the node's disk.
 */
public final class SimKvCluster {

    /** Observes every applied entry on every node. */
    @FunctionalInterface
    public interface ApplyObserver {
        void onApplied(int node, LogEntry entry, KvStateMachine.WriteResult result);
    }

    private static final class Node {
        Simulation.NodeScheduler scheduler;
        RaftNode raft;
        KvStateMachine stateMachine;
        KvService service;
        long lastAppliedIndex;
    }

    public final Simulation sim;
    public final SimNetwork network;
    public final int size;
    private final Random random;
    private final RaftConfig config;
    private final KvService.Options serviceOptions;
    private final SimulatedFileSystem[] disks;
    private final Node[] nodes;
    private final Map<Integer, String> addresses = new HashMap<>();
    private final Map<Long, LogEntry> committed = new HashMap<>();
    private final Map<Long, Integer> leaderOfTerm = new HashMap<>();
    private final List<ApplyObserver> observers = new ArrayList<>();
    private int nextClientEndpoint = 1001;
    private long snapshotsTakenByDeadIncarnations;
    private long snapshotsInstalledByDeadIncarnations;

    public SimKvCluster(int size, long seed) {
        this(size, seed, RaftConfig.defaults(), KvService.Options.defaults());
    }

    public SimKvCluster(int size, long seed, RaftConfig config, KvService.Options serviceOptions) {
        this.size = size;
        this.sim = new Simulation(seed);
        this.random = sim.random();
        this.network = new SimNetwork(sim);
        this.config = config;
        this.serviceOptions = serviceOptions;
        this.disks = new SimulatedFileSystem[size + 1];
        this.nodes = new Node[size + 1];
        for (int i = 1; i <= size; i++) {
            disks[i] = new SimulatedFileSystem(new Random(random.nextLong()));
            addresses.put(i, "sim-node-" + i);
        }
        sim.setAfterEachEvent(this::checkElectionSafety);
    }

    public SimKvCluster startAll() {
        for (int i = 1; i <= size; i++) {
            start(i);
        }
        return this;
    }

    private void start(int i) {
        List<Integer> peers = new ArrayList<>();
        for (int p = 1; p <= size; p++) {
            if (p != i) {
                peers.add(p);
            }
        }
        Node node = new Node();
        node.scheduler = sim.newNodeScheduler();
        RaftStorage storage = RaftStorage.open(disks[i], "raft.wal");
        // Sync the tree often, so restarts resume mid-log rather than from an empty state.
        node.stateMachine = KvStateMachine.open(disks[i], "kv", new BTree.Options(64, FsyncPolicy.LAZY, 1 << 20), 20);
        int nodeId = i;
        node.stateMachine.addListener((entry, result) -> onApply(nodeId, entry, result));
        // A snapshot restore legitimately jumps the applied index forward.
        node.stateMachine.addRestoreListener(index -> node.lastAppliedIndex = index);
        node.raft = new RaftNode(i, peers, config, node.scheduler, network.transportFor(i), storage,
                node.stateMachine, node.stateMachine.appliedIndex(), new Random(random.nextLong()));
        node.lastAppliedIndex = node.stateMachine.appliedIndex(); // after a possible restore at startup
        node.service = new KvService(node.raft, node.stateMachine, node.scheduler, addresses, serviceOptions);
        nodes[i] = node;
        network.register(i, node.raft);
        node.raft.start();
    }

    // ------------------------------------------------------------------ faults

    /** Power loss: timers and in-flight messages die; the disk keeps only fsynced data. */
    public void crash(int i) {
        crash(i, true);
    }

    /**
     * Crashes a node. With {@code powerLoss}, its disk keeps only fsynced data (lost, torn and
     * reordered writes); without it, a process crash (like {@code docker kill}): every write the
     * process made survives in the OS page cache.
     */
    public void crash(int i, boolean powerLoss) {
        if (nodes[i] == null) {
            return;
        }
        RaftNode.Status last = nodes[i].raft.status();
        snapshotsTakenByDeadIncarnations += last.snapshotsTaken();
        snapshotsInstalledByDeadIncarnations += last.snapshotsInstalled();
        nodes[i].scheduler.kill();
        network.resume(i); // a crash ends any pause; the restarted process is not frozen
        nodes[i].raft.stop();
        network.unregister(i);
        if (powerLoss) {
            disks[i].simulatePowerLoss();
        } else {
            disks[i].simulateProcessCrash();
        }
        nodes[i] = null;
    }

    /**
     * Freezes a node, like a long GC pause or {@code docker pause}: its timers stop and messages
     * for it wait until {@link #resume}. A paused leader cannot notice it lost its majority, so it
     * wakes up still believing it leads: the case ReadIndex must handle.
     */
    public void pause(int i) {
        if (nodes[i] != null) {
            nodes[i].scheduler.pause();
            network.pause(i);
        }
    }

    public void resume(int i) {
        network.resume(i);
        if (nodes[i] != null) {
            nodes[i].scheduler.resume();
        }
    }

    public void restart(int i) {
        crash(i);
        start(i);
    }

    public boolean isAlive(int i) {
        return nodes[i] != null;
    }

    // ------------------------------------------------------------------ inspection

    public RaftNode raft(int i) {
        return nodes[i] == null ? null : nodes[i].raft;
    }

    public KvStateMachine stateMachine(int i) {
        return nodes[i] == null ? null : nodes[i].stateMachine;
    }

    /** Node id {@code offset} places after {@code i}, wrapping around (1-based). */
    public int after(int i, int offset) {
        return ((i - 1 + offset) % size + size) % size + 1;
    }

    /** The live, connected leader with the newest term, or -1. */
    public int leader() {
        int best = -1;
        long bestTerm = -1;
        for (int i = 1; i <= size; i++) {
            if (nodes[i] != null && network.isConnected(i)) {
                RaftNode.Status status = nodes[i].raft.status();
                if (status.role() == RaftNode.Role.LEADER && status.term() > bestTerm) {
                    best = i;
                    bestTerm = status.term();
                }
            }
        }
        return best;
    }

    /** Runs until some connected node leads, for at most {@code maxMillis}; returns it or -1. */
    public int awaitLeader(long maxMillis) {
        sim.runUntil(() -> leader() != -1, maxMillis);
        return leader();
    }

    /** Snapshots taken by every node so far, including incarnations that have since crashed. */
    public long snapshotsTaken() {
        long total = snapshotsTakenByDeadIncarnations;
        for (int i = 1; i <= size; i++) {
            total += nodes[i] == null ? 0 : nodes[i].raft.status().snapshotsTaken();
        }
        return total;
    }

    /** InstallSnapshots completed by every node so far, including crashed incarnations. */
    public long snapshotsInstalled() {
        long total = snapshotsInstalledByDeadIncarnations;
        for (int i = 1; i <= size; i++) {
            total += nodes[i] == null ? 0 : nodes[i].raft.status().snapshotsInstalled();
        }
        return total;
    }

    /** A node's simulated disk, for crash-injection tests. */
    public SimulatedFileSystem disk(int i) {
        return disks[i];
    }

    public void addObserver(ApplyObserver observer) {
        observers.add(observer);
    }

    // ------------------------------------------------------------------ clients

    /** A new client with its own network endpoint (which can be partitioned like a server). */
    public KvClient newClient(KvClient.Options options) {
        int endpoint = nextClientEndpoint++;
        network.registerEndpoint(endpoint);
        // Each client starts at a random server (and walks the others in order from there), the way
        // real clients spread their first requests. Followers redirect them to the leader.
        int first = 1 + random.nextInt(size);
        List<Integer> servers = new ArrayList<>();
        for (int k = 0; k < size; k++) {
            servers.add(after(first, k));
        }
        long clientId = (random.nextLong() & Long.MAX_VALUE) | 1; // positive and non-zero
        return new KvClient(clientId, servers, new SimClientTransport(endpoint), sim.newNodeScheduler(), options);
    }

    /** The network endpoint id of the most recently created client. */
    public int lastClientEndpoint() {
        return nextClientEndpoint - 1;
    }

    /**
     * Starts an operation and runs the simulation until it completes, for at most
     * {@code maxMillis}. Returns null if it did not complete in time.
     */
    public <T> KvResult<T> await(Consumer<Consumer<KvResult<T>>> operation, long maxMillis) {
        List<KvResult<T>> result = new ArrayList<>();
        operation.accept(result::add);
        sim.runUntil(() -> !result.isEmpty(), maxMillis);
        return result.isEmpty() ? null : result.get(0);
    }

    /** Client requests over the simulated network, to whatever KvService runs on the target now. */
    private final class SimClientTransport implements ClientTransport {
        private final int self;

        SimClientTransport(int self) {
            this.self = self;
        }

        @Override
        public void get(int server, GetRequest request, Consumer<GetReply> onReply) {
            network.call(self, server, request, id -> nodes[id] == null ? null : nodes[id].service::get, onReply);
        }

        @Override
        public void put(int server, PutRequest request, Consumer<PutReply> onReply) {
            network.call(self, server, request, id -> nodes[id] == null ? null : nodes[id].service::put, onReply);
        }

        @Override
        public void delete(int server, DeleteRequest request, Consumer<DeleteReply> onReply) {
            network.call(self, server, request, id -> nodes[id] == null ? null : nodes[id].service::delete, onReply);
        }
    }

    // ------------------------------------------------------------------ safety checks

    private void checkElectionSafety() {
        for (int i = 1; i <= size; i++) {
            if (nodes[i] != null && nodes[i].raft.status().role() == RaftNode.Role.LEADER) {
                long term = nodes[i].raft.status().term();
                Integer previous = leaderOfTerm.putIfAbsent(term, i);
                if (previous != null && previous != i) {
                    throw new AssertionError("Election Safety violated: nodes " + previous + " and " + i
                            + " were both leader in term " + term);
                }
            }
        }
    }

    private void onApply(int nodeId, LogEntry entry, KvStateMachine.WriteResult result) {
        Node node = nodes[nodeId];
        if (node != null) {
            if (entry.getIndex() != node.lastAppliedIndex + 1) {
                throw new AssertionError("node " + nodeId + " applied index " + entry.getIndex()
                        + " out of order (expected " + (node.lastAppliedIndex + 1) + ")");
            }
            node.lastAppliedIndex = entry.getIndex();
        }
        LogEntry earlier = committed.putIfAbsent(entry.getIndex(), entry);
        if (earlier != null && !earlier.equals(entry)) {
            throw new AssertionError("State Machine Safety violated at index " + entry.getIndex() + " on node " + nodeId);
        }
        for (ApplyObserver observer : observers) {
            observer.onApplied(nodeId, entry, result);
        }
    }
}
