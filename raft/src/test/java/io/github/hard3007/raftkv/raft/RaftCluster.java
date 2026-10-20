package io.github.hard3007.raftkv.raft;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import io.github.hard3007.raftkv.proto.raft.EntryType;
import io.github.hard3007.raftkv.proto.raft.LogEntry;
import io.github.hard3007.raftkv.raft.sim.SimNetwork;
import io.github.hard3007.raftkv.raft.sim.Simulation;
import io.github.hard3007.raftkv.storage.SimulatedFileSystem;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

/**
 * Test harness for a simulated Raft cluster, modeled on the MIT 6.5840 Raft lab's config.go.
 * Nodes are numbered 1..n.
 *
 * <p>Three safety properties are checked continuously, not just at the end of a test:
 * <ul>
 *   <li><b>Election Safety</b>: at most one leader per term, checked after every simulated event;
 *   <li><b>State Machine Safety</b>: no two nodes ever apply different entries at the same index;
 *   <li><b>in-order apply</b>: each node applies indices 1, 2, 3, ... with no gaps or repeats.
 * </ul>
 * A violation throws immediately, out of whatever simulation step caused it, so the test fails
 * at the exact moment of the bug and the seed reproduces it.
 */
final class RaftCluster {

    /** The lab's "election timeout" for test pacing: well above our 150-300 ms. */
    static final int ELECTION_TIMEOUT_MS = 1000;

    final Simulation sim;
    final SimNetwork network;
    final int size;
    private final Random random;
    private final RaftConfig[] configs;
    private final SimulatedFileSystem[] disks;
    private final RaftNode[] nodes;
    private final Simulation.NodeScheduler[] schedulers;

    /** The first entry any node applied at each index. */
    private final Map<Long, LogEntry> committed = new HashMap<>();
    /** What each live node has applied (cleared when it crashes). */
    private final List<Map<Long, LogEntry>> applied = new ArrayList<>();
    /** The leader seen for each term. */
    private final Map<Long, Integer> leaderOfTerm = new HashMap<>();
    private int nextCommand;

    RaftCluster(int size, long seed) {
        this(size, seed, RaftConfig.defaults());
    }

    RaftCluster(int size, long seed, RaftConfig config) {
        this.size = size;
        this.sim = new Simulation(seed);
        this.random = sim.random();
        this.network = new SimNetwork(sim);
        this.configs = new RaftConfig[size + 1];
        this.disks = new SimulatedFileSystem[size + 1];
        this.nodes = new RaftNode[size + 1];
        this.schedulers = new Simulation.NodeScheduler[size + 1];
        applied.add(null); // nodes are 1-based
        for (int i = 1; i <= size; i++) {
            configs[i] = config;
            disks[i] = new SimulatedFileSystem(new Random(random.nextLong()));
            applied.add(new HashMap<>());
        }
        sim.setAfterEachEvent(this::checkElectionSafety);
    }

    // ------------------------------------------------------------------ setup

    /** Overrides one node's config. Call before {@link #startAll()}. */
    void setConfig(int node, RaftConfig config) {
        configs[node] = config;
    }

    /** Writes persistent state onto a node's disk before it first starts, as if from an earlier history. */
    void seedPersistentState(int node, long term, int votedFor, List<LogEntry> entries) {
        try (RaftStorage storage = RaftStorage.open(disks[node], "raft.wal")) {
            storage.save(term, votedFor, 0, entries);
        }
    }

    static LogEntry entry(long index, long term, String command) {
        return LogEntry.newBuilder().setIndex(index).setTerm(term).setType(EntryType.COMMAND)
                .setCommand(com.google.protobuf.ByteString.copyFromUtf8(command)).build();
    }

    RaftCluster startAll() {
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
        RaftStorage storage = RaftStorage.open(disks[i], "raft.wal");
        schedulers[i] = sim.newNodeScheduler();
        int nodeId = i;
        nodes[i] = new RaftNode(i, peers, configs[i], schedulers[i], network.transportFor(i), storage,
                entry -> onApply(nodeId, entry), 0, new Random(random.nextLong()));
        network.register(i, nodes[i]);
        nodes[i].start();
    }

    // ------------------------------------------------------------------ faults

    /** Crashes a node: its timers and in-flight messages die, and its disk loses un-fsynced writes. */
    void crash(int i) {
        if (nodes[i] == null) {
            return;
        }
        schedulers[i].kill();
        nodes[i].stop();
        network.unregister(i);
        disks[i].simulatePowerLoss();
        nodes[i] = null;
        applied.get(i).clear(); // its in-memory state machine is gone; it will re-apply from index 1
    }

    void restart(int i) {
        crash(i);
        start(i);
    }

    boolean isAlive(int i) {
        return nodes[i] != null;
    }

    void disconnect(int i) {
        network.disconnect(i);
    }

    void connect(int i) {
        network.connect(i);
    }

    RaftNode node(int i) {
        return nodes[i];
    }

    /** Node id {@code offset} places after {@code i}, wrapping around (1-based). */
    int after(int i, int offset) {
        return ((i - 1 + offset) % size + size) % size + 1;
    }

    // ------------------------------------------------------------------ safety checks

    private void checkElectionSafety() {
        for (int i = 1; i <= size; i++) {
            if (nodes[i] != null && nodes[i].status().role() == RaftNode.Role.LEADER) {
                long term = nodes[i].status().term();
                Integer previous = leaderOfTerm.putIfAbsent(term, i);
                if (previous != null && previous != i) {
                    throw new AssertionError("Election Safety violated: nodes " + previous + " and " + i
                            + " were both leader in term " + term);
                }
            }
        }
    }

    private void onApply(int node, LogEntry entry) {
        Map<Long, LogEntry> mine = applied.get(node);
        if (entry.getIndex() != mine.size() + 1) {
            throw new AssertionError("node " + node + " applied index " + entry.getIndex()
                    + " out of order (expected " + (mine.size() + 1) + ")");
        }
        LogEntry earlier = committed.putIfAbsent(entry.getIndex(), entry);
        if (earlier != null && !earlier.equals(entry)) {
            throw new AssertionError("State Machine Safety violated at index " + entry.getIndex() + ": node " + node
                    + " applied " + describe(entry) + " but another node applied " + describe(earlier));
        }
        mine.put(entry.getIndex(), entry);
    }

    private static String describe(LogEntry entry) {
        return "(term " + entry.getTerm() + ", " + entry.getType() + " '" + entry.getCommand().toStringUtf8() + "')";
    }

    // ------------------------------------------------------------------ lab-style helpers

    /** A fresh command string. */
    String newCommand() {
        return "cmd-" + (++nextCommand);
    }

    /**
     * Waits for a single leader among connected live nodes and returns it; fails if there are two
     * leaders in one term or no leader after several tries (lab: checkOneLeader).
     */
    int checkOneLeader() {
        for (int attempt = 0; attempt < 10; attempt++) {
            sim.runFor(450 + random.nextInt(100));
            Map<Long, List<Integer>> leadersByTerm = new HashMap<>();
            for (int i = 1; i <= size; i++) {
                if (nodes[i] != null && network.isConnected(i) && nodes[i].status().role() == RaftNode.Role.LEADER) {
                    leadersByTerm.computeIfAbsent(nodes[i].status().term(), t -> new ArrayList<>()).add(i);
                }
            }
            long newestTerm = -1;
            for (Map.Entry<Long, List<Integer>> e : leadersByTerm.entrySet()) {
                if (e.getValue().size() > 1) {
                    fail("term " + e.getKey() + " has " + e.getValue().size() + " leaders: " + e.getValue());
                }
                newestTerm = Math.max(newestTerm, e.getKey());
            }
            if (newestTerm != -1) {
                return leadersByTerm.get(newestTerm).get(0);
            }
        }
        return fail("expected one leader, got none");
    }

    /** All connected live nodes agree on the term; returns it (lab: checkTerms). */
    long checkTerms() {
        long term = -1;
        for (int i = 1; i <= size; i++) {
            if (nodes[i] != null && network.isConnected(i)) {
                long t = nodes[i].status().term();
                if (term == -1) {
                    term = t;
                } else if (t != term) {
                    fail("servers disagree on term: " + term + " vs " + t);
                }
            }
        }
        return term;
    }

    /** No connected live node thinks it is leader (lab: checkNoLeader). */
    void checkNoLeader() {
        for (int i = 1; i <= size; i++) {
            if (nodes[i] != null && network.isConnected(i)) {
                assertTrue(nodes[i].status().role() != RaftNode.Role.LEADER,
                        "expected no leader among connected servers, but " + i + " claims to be one");
            }
        }
    }

    /** How many live nodes have applied {@code index} (lab: nCommitted). Agreement is checked on apply. */
    int nCommitted(long index) {
        int count = 0;
        for (int i = 1; i <= size; i++) {
            if (nodes[i] != null && applied.get(i).containsKey(index)) {
                count++;
            }
        }
        return count;
    }

    /** How many distinct log indices have been applied by at least one node. */
    int committedIndexCount() {
        return committed.size();
    }

    /** The command committed at {@code index}, if any node has applied it. */
    Optional<String> committedCommand(long index) {
        LogEntry entry = committed.get(index);
        return entry == null ? Optional.empty() : Optional.of(entry.getCommand().toString(StandardCharsets.UTF_8));
    }

    /** Proposes on node i, if it is alive; returns the proposal if it is the leader. */
    Optional<RaftNode.Proposal> propose(int i, String command) {
        if (nodes[i] == null) {
            return Optional.empty();
        }
        return nodes[i].propose(command.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Gets {@code command} committed by at least {@code expectedServers} nodes, trying every node
     * as a possible leader, for up to 10 seconds; returns its index (lab: one). With
     * {@code retry = false}, a proposal that does not commit within 2 seconds fails the test
     * immediately instead of being re-proposed.
     */
    long one(String command, int expectedServers, boolean retry) {
        long deadline = sim.now() + 10_000;
        int startAt = 1;
        while (sim.now() < deadline) {
            long index = -1;
            for (int k = 0; k < size; k++) {
                startAt = after(startAt, 1);
                if (!network.isConnected(startAt)) {
                    continue;
                }
                Optional<RaftNode.Proposal> proposal = propose(startAt, command);
                if (proposal.isPresent()) {
                    index = proposal.get().index();
                    break;
                }
            }
            if (index != -1) {
                long proposedIndex = index;
                boolean agreed = sim.runUntil(() -> nCommitted(proposedIndex) >= expectedServers
                        && committedCommand(proposedIndex).equals(Optional.of(command)), 2_000);
                if (agreed) {
                    return index;
                }
                if (!retry) {
                    fail("one(" + command + ") failed to reach agreement at index " + index);
                }
            } else {
                sim.runFor(50);
            }
        }
        return fail("one(" + command + ") failed to reach agreement within 10 s");
    }

    /**
     * Waits for {@code index} to be committed by at least {@code n} nodes; gives up (returning
     * empty) if some node's term moves past {@code startTerm}, since then the entry may never
     * commit (lab: wait).
     */
    Optional<String> waitCommitted(long index, int n, long startTerm) {
        sim.runUntil(() -> nCommitted(index) >= n || termMovedPast(startTerm), 10_000);
        return nCommitted(index) >= n ? committedCommand(index) : Optional.empty();
    }

    private boolean termMovedPast(long term) {
        for (int i = 1; i <= size; i++) {
            if (nodes[i] != null && nodes[i].status().term() > term) {
                return true;
            }
        }
        return false;
    }

    Random random() {
        return random;
    }
}
