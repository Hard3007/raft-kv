package io.github.hard3007.raftkv.raft;

import static io.github.hard3007.raftkv.raft.RaftCluster.ELECTION_TIMEOUT_MS;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Persistence, crashes and unreliable networks, modeled on the MIT 6.5840 lab tests 3C. A crash
 * is a simulated power loss: the node's disk keeps only what was fsynced.
 */
class RaftPersistenceTest {

    private static final String SEEDS = "io.github.hard3007.raftkv.raft.Seeds#seeds";

    /**
     * The Figure 8 tests send one entry per AppendEntries. With large batches, a new leader's
     * no-op travels together with older entries, which hides commit-rule bugs (see DESIGN.md);
     * one entry at a time creates far more states where older-term entries sit on a majority alone.
     */
    private static final RaftConfig ONE_ENTRY_PER_APPEND = RaftConfig.defaults().withMaxEntriesPerAppend(1);

    /** Lab: TestPersist13C. State survives crashes of every node, the leader, and followers. */
    @ParameterizedTest(name = "seed {0}")
    @MethodSource(SEEDS)
    void persistenceAcrossCrashes(long seed) {
        RaftCluster cluster = new RaftCluster(3, seed).startAll();
        cluster.one(cluster.newCommand(), 3, true);

        // Crash and restart all servers.
        for (int i = 1; i <= 3; i++) {
            cluster.restart(i);
        }
        cluster.one(cluster.newCommand(), 3, true);

        int leader1 = cluster.checkOneLeader();
        cluster.restart(leader1);
        cluster.one(cluster.newCommand(), 3, true);

        int leader2 = cluster.checkOneLeader();
        cluster.crash(leader2);
        long index = cluster.one(cluster.newCommand(), 2, true);
        cluster.restart(leader2);
        assertTrue(cluster.waitCommitted(index, 3, Long.MAX_VALUE).isPresent(), "restarted leader did not catch up");

        int follower = cluster.after(cluster.checkOneLeader(), 1);
        cluster.crash(follower);
        cluster.one(cluster.newCommand(), 2, true);
        cluster.restart(follower);
        cluster.one(cluster.newCommand(), 3, true);
    }

    /** Lab: TestPersist23C. Repeated partial failures with crashes of disconnected nodes. */
    @ParameterizedTest(name = "seed {0}")
    @MethodSource(SEEDS)
    void persistenceWithRepeatedPartialFailures(long seed) {
        RaftCluster cluster = new RaftCluster(5, seed).startAll();
        for (int iteration = 0; iteration < 5; iteration++) {
            cluster.one(cluster.newCommand(), 5, true);

            int leader1 = cluster.checkOneLeader();
            cluster.disconnect(cluster.after(leader1, 1));
            cluster.disconnect(cluster.after(leader1, 2));
            cluster.one(cluster.newCommand(), 3, true);

            cluster.disconnect(leader1);
            cluster.disconnect(cluster.after(leader1, 3));
            cluster.disconnect(cluster.after(leader1, 4));

            cluster.restart(cluster.after(leader1, 1));
            cluster.restart(cluster.after(leader1, 2));
            cluster.connect(cluster.after(leader1, 1));
            cluster.connect(cluster.after(leader1, 2));
            cluster.sim.runFor(ELECTION_TIMEOUT_MS);

            cluster.restart(cluster.after(leader1, 3));
            cluster.connect(cluster.after(leader1, 3));
            cluster.one(cluster.newCommand(), 3, true);

            cluster.connect(cluster.after(leader1, 4));
            cluster.connect(leader1);
        }
        cluster.one(cluster.newCommand(), 5, true);
    }

    /** Lab: TestPersist33C. A partitioned leader and a crashed follower; the leader restarts. */
    @ParameterizedTest(name = "seed {0}")
    @MethodSource(SEEDS)
    void partitionedLeaderAndCrashedFollower(long seed) {
        RaftCluster cluster = new RaftCluster(3, seed).startAll();
        cluster.one(cluster.newCommand(), 3, true);

        int leader = cluster.checkOneLeader();
        cluster.disconnect(cluster.after(leader, 2));
        cluster.one(cluster.newCommand(), 2, true);

        cluster.crash(leader);
        cluster.crash(cluster.after(leader, 1));
        cluster.connect(cluster.after(leader, 2));
        cluster.restart(leader);
        cluster.one(cluster.newCommand(), 2, true);

        cluster.restart(cluster.after(leader, 1));
        cluster.one(cluster.newCommand(), 3, true);
    }

    /**
     * Lab: TestFigure83C. Leaders crash constantly, often right after accepting a proposal, which
     * creates the Figure 8 situations where entries from older terms are present on a majority.
     */
    @ParameterizedTest(name = "seed {0}")
    @MethodSource(SEEDS)
    void figure8CrashingLeaders(long seed) {
        RaftCluster cluster = new RaftCluster(5, seed, ONE_ENTRY_PER_APPEND).startAll();
        Random random = cluster.random();
        cluster.one(cluster.newCommand(), 1, true);

        int alive = 5;
        for (int iteration = 0; iteration < 1000; iteration++) {
            int leader = -1;
            for (int i = 1; i <= 5; i++) {
                if (cluster.propose(i, cluster.newCommand()).isPresent()) {
                    leader = i;
                }
            }
            cluster.sim.runFor(random.nextInt(10) == 0 ? random.nextInt(ELECTION_TIMEOUT_MS / 2) : random.nextInt(13));

            if (leader != -1) {
                cluster.crash(leader);
                alive--;
            }
            if (alive < 3) {
                int s = 1 + random.nextInt(5);
                if (!cluster.isAlive(s)) {
                    cluster.restart(s);
                    alive++;
                }
            }
        }
        for (int i = 1; i <= 5; i++) {
            if (!cluster.isAlive(i)) {
                cluster.restart(i);
            }
        }
        cluster.one(cluster.newCommand(), 5, true);
    }

    /** Lab: TestUnreliableAgree3C. Agreement on a lossy, reordering network. */
    @ParameterizedTest(name = "seed {0}")
    @MethodSource(SEEDS)
    void agreementOnUnreliableNetwork(long seed) {
        RaftCluster cluster = new RaftCluster(5, seed).startAll();
        cluster.network.setUnreliable(true);
        for (int iteration = 1; iteration < 50; iteration++) {
            List<String> batch = new ArrayList<>();
            for (int j = 0; j < 4; j++) {
                batch.add(cluster.newCommand());
            }
            proposeUntilCommitted(cluster, batch, 1);
            cluster.one(cluster.newCommand(), 1, true);
        }
        cluster.network.setUnreliable(false);
        cluster.one(cluster.newCommand(), 5, true);
    }

    /** Lab: TestFigure8Unreliable3C. Figure 8 situations plus loss, reordering and long delays. */
    @ParameterizedTest(name = "seed {0}")
    @MethodSource(SEEDS)
    void figure8OnUnreliableNetwork(long seed) {
        RaftCluster cluster = new RaftCluster(5, seed, ONE_ENTRY_PER_APPEND).startAll();
        Random random = cluster.random();
        cluster.network.setUnreliable(true);
        cluster.one(cluster.newCommand(), 1, true);

        int connected = 5;
        for (int iteration = 0; iteration < 1000; iteration++) {
            if (iteration == 200) {
                cluster.network.setLongDelays(true);
            }
            int leader = -1;
            for (int i = 1; i <= 5; i++) {
                if (cluster.network.isConnected(i) && cluster.propose(i, cluster.newCommand()).isPresent()) {
                    leader = i;
                }
            }
            cluster.sim.runFor(random.nextInt(10) == 0 ? random.nextInt(ELECTION_TIMEOUT_MS / 2) : random.nextInt(13));

            if (leader != -1 && random.nextInt(1000) < ELECTION_TIMEOUT_MS / 2) {
                cluster.disconnect(leader);
                connected--;
            }
            if (connected < 3) {
                int s = 1 + random.nextInt(5);
                if (!cluster.network.isConnected(s)) {
                    cluster.connect(s);
                    connected++;
                }
            }
        }
        for (int i = 1; i <= 5; i++) {
            cluster.connect(i);
        }
        cluster.network.setUnreliable(false);
        cluster.network.setLongDelays(false);
        cluster.one(cluster.newCommand(), 5, true);
    }

    /** Lab: TestReliableChurn3C. */
    @ParameterizedTest(name = "seed {0}")
    @MethodSource(SEEDS)
    void churn(long seed) {
        runChurn(seed, false);
    }

    /** Lab: TestUnreliableChurn3C. */
    @ParameterizedTest(name = "seed {0}")
    @MethodSource(SEEDS)
    void churnOnUnreliableNetwork(long seed) {
        runChurn(seed, true);
    }

    /**
     * Three simulated clients keep proposing to random nodes while nodes are randomly
     * disconnected, crashed, restarted and reconnected. Every proposal a client saw commit must
     * still be in the log at the end; the harness checks agreement on every apply.
     */
    private static void runChurn(long seed, boolean unreliable) {
        RaftCluster cluster = new RaftCluster(5, seed).startAll();
        Random random = cluster.random();
        cluster.network.setUnreliable(unreliable);

        // Client "processes": every 0-20 ms, each proposes to a random node. Proposals that land
        // are remembered as (index -> command) and later checked against what committed.
        Map<Long, String> acceptedProposals = new HashMap<>();
        List<String> sawCommitted = new ArrayList<>();
        boolean[] running = {true};
        for (int client = 0; client < 3; client++) {
            scheduleClient(cluster, random, acceptedProposals, sawCommitted, running);
        }

        for (int iteration = 0; iteration < 20; iteration++) {
            if (random.nextInt(1000) < 200) {
                cluster.disconnect(1 + random.nextInt(5));
            }
            if (random.nextInt(1000) < 500) {
                int i = 1 + random.nextInt(5);
                if (!cluster.isAlive(i)) {
                    cluster.restart(i);
                }
                cluster.connect(i);
            }
            if (random.nextInt(1000) < 200) {
                int i = 1 + random.nextInt(5);
                if (cluster.isAlive(i)) {
                    cluster.crash(i);
                }
            }
            // Heal often enough that progress is possible.
            cluster.sim.runFor(ELECTION_TIMEOUT_MS * 7 / 10);
        }
        cluster.sim.runFor(ELECTION_TIMEOUT_MS);

        cluster.network.setUnreliable(false);
        for (int i = 1; i <= 5; i++) {
            if (!cluster.isAlive(i)) {
                cluster.restart(i);
            }
            cluster.connect(i);
        }
        running[0] = false;
        long last = cluster.one(cluster.newCommand(), 5, true);

        // Every command that a client saw committed must be in the final committed log.
        List<String> finalLog = new ArrayList<>();
        for (long index = 1; index <= last; index++) {
            cluster.committedCommand(index).ifPresent(finalLog::add);
        }
        for (String command : sawCommitted) {
            assertTrue(finalLog.contains(command), "committed command " + command + " is missing from the final log");
        }
        assertTrue(sawCommitted.size() > 0, "churn made no progress at all");
    }

    private static void scheduleClient(RaftCluster cluster, Random random, Map<Long, String> acceptedProposals,
            List<String> sawCommitted, boolean[] running) {
        cluster.sim.schedule(random.nextInt(20), () -> {
            if (!running[0]) {
                return;
            }
            // Check earlier proposals: which ones committed with the command we proposed?
            acceptedProposals.entrySet().removeIf(entry -> {
                Optional<String> committed = cluster.committedCommand(entry.getKey());
                if (committed.isEmpty()) {
                    return false;
                }
                if (committed.get().equals(entry.getValue())) {
                    sawCommitted.add(entry.getValue());
                }
                return true; // decided either way
            });
            String command = cluster.newCommand();
            int target = 1 + random.nextInt(5);
            cluster.propose(target, command).ifPresent(p -> acceptedProposals.put(p.index(), command));
            scheduleClient(cluster, random, acceptedProposals, sawCommitted, running);
        });
    }

    /**
     * The lab runs four one() calls concurrently. Here: propose every pending command at once to
     * whichever nodes accept, wait up to 2 s, keep the ones that committed, and retry the rest.
     */
    private static void proposeUntilCommitted(RaftCluster cluster, List<String> commands, int expected) {
        List<String> pending = new ArrayList<>(commands);
        long deadline = cluster.sim.now() + 10_000;
        while (!pending.isEmpty() && cluster.sim.now() < deadline) {
            Map<Long, String> placed = new HashMap<>();
            for (int i = 1; i <= cluster.size; i++) {
                for (String command : pending) {
                    cluster.propose(i, command).ifPresent(p -> placed.put(p.index(), command));
                }
            }
            if (placed.isEmpty()) {
                cluster.sim.runFor(50);
                continue;
            }
            cluster.sim.runUntil(() -> placed.keySet().stream().allMatch(i -> cluster.nCommitted(i) >= expected), 2_000);
            placed.forEach((index, command) -> {
                if (cluster.nCommitted(index) >= expected && cluster.committedCommand(index).equals(Optional.of(command))) {
                    pending.remove(command);
                }
            });
        }
        assertTrue(pending.isEmpty(), "commands never committed: " + pending);
    }
}
