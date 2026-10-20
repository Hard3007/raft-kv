package io.github.hard3007.raftkv.raft;

import static io.github.hard3007.raftkv.raft.RaftCluster.ELECTION_TIMEOUT_MS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Log replication, modeled on the MIT 6.5840 lab tests 3B. Index expectations differ slightly
 * from the lab because every new leader first commits a no-op entry.
 */
class RaftAgreementTest {

    private static final String SEEDS = "io.github.hard3007.raftkv.raft.Seeds#seeds";

    /** Lab: TestBasicAgree3B. */
    @ParameterizedTest(name = "seed {0}")
    @MethodSource(SEEDS)
    void basicAgreement(long seed) {
        RaftCluster cluster = new RaftCluster(3, seed).startAll();
        cluster.checkOneLeader();
        long previous = -1;
        for (int i = 0; i < 3; i++) {
            String command = cluster.newCommand();
            long index = cluster.one(command, 3, false);
            if (previous != -1) {
                assertEquals(previous + 1, index, "commands should land at consecutive indices");
            }
            previous = index;
        }
    }

    /** Lab: TestFollowerFailure3B. Agreement continues with one follower down; stops with two. */
    @ParameterizedTest(name = "seed {0}")
    @MethodSource(SEEDS)
    void agreementDespiteFollowerFailure(long seed) {
        RaftCluster cluster = new RaftCluster(3, seed).startAll();
        cluster.one(cluster.newCommand(), 3, false);

        int leader1 = cluster.checkOneLeader();
        cluster.disconnect(cluster.after(leader1, 1));
        cluster.one(cluster.newCommand(), 2, false);
        cluster.sim.runFor(ELECTION_TIMEOUT_MS);
        cluster.one(cluster.newCommand(), 2, false);

        int leader2 = cluster.checkOneLeader();
        cluster.disconnect(cluster.after(leader2, 1));
        cluster.disconnect(cluster.after(leader2, 2));
        Optional<RaftNode.Proposal> proposal = cluster.propose(leader2, cluster.newCommand());
        assertTrue(proposal.isPresent(), "leader rejected the proposal");
        cluster.sim.runFor(2 * ELECTION_TIMEOUT_MS);
        assertEquals(0, cluster.nCommitted(proposal.get().index()), "committed without a majority");
    }

    /** Lab: TestLeaderFailure3B. */
    @ParameterizedTest(name = "seed {0}")
    @MethodSource(SEEDS)
    void agreementDespiteLeaderFailure(long seed) {
        RaftCluster cluster = new RaftCluster(3, seed).startAll();
        cluster.one(cluster.newCommand(), 3, false);

        int leader1 = cluster.checkOneLeader();
        cluster.disconnect(leader1);
        cluster.one(cluster.newCommand(), 2, false);
        cluster.sim.runFor(ELECTION_TIMEOUT_MS);
        cluster.one(cluster.newCommand(), 2, false);

        int leader2 = cluster.checkOneLeader();
        cluster.disconnect(leader2);
        // Only one node is left connected: nothing new may commit, wherever the proposals land.
        int committedBefore = cluster.committedIndexCount();
        for (int i = 1; i <= 3; i++) {
            cluster.propose(i, cluster.newCommand());
        }
        cluster.sim.runFor(2 * ELECTION_TIMEOUT_MS);
        assertEquals(committedBefore, cluster.committedIndexCount(), "committed without a majority");
    }

    /** Lab: TestFailAgree3B. A disconnected follower catches up after rejoining. */
    @ParameterizedTest(name = "seed {0}")
    @MethodSource(SEEDS)
    void followerRejoinsAndCatchesUp(long seed) {
        RaftCluster cluster = new RaftCluster(3, seed).startAll();
        cluster.one(cluster.newCommand(), 3, false);

        int leader = cluster.checkOneLeader();
        cluster.disconnect(cluster.after(leader, 1));
        for (int i = 0; i < 4; i++) {
            cluster.one(cluster.newCommand(), 2, false);
        }
        cluster.sim.runFor(ELECTION_TIMEOUT_MS);

        cluster.connect(cluster.after(leader, 1));
        cluster.one(cluster.newCommand(), 3, true);
        cluster.sim.runFor(ELECTION_TIMEOUT_MS);
        cluster.one(cluster.newCommand(), 3, true);
    }

    /** Lab: TestFailNoAgree3B. No commit with 3 of 5 followers gone; recovery afterwards. */
    @ParameterizedTest(name = "seed {0}")
    @MethodSource(SEEDS)
    void noAgreementWithoutMajority(long seed) {
        RaftCluster cluster = new RaftCluster(5, seed).startAll();
        cluster.one(cluster.newCommand(), 5, false);

        int leader = cluster.checkOneLeader();
        cluster.disconnect(cluster.after(leader, 1));
        cluster.disconnect(cluster.after(leader, 2));
        cluster.disconnect(cluster.after(leader, 3));

        Optional<RaftNode.Proposal> proposal = cluster.propose(leader, cluster.newCommand());
        assertTrue(proposal.isPresent(), "leader rejected the proposal");
        cluster.sim.runFor(2 * ELECTION_TIMEOUT_MS);
        assertEquals(0, cluster.nCommitted(proposal.get().index()), "committed without a majority");

        cluster.connect(cluster.after(leader, 1));
        cluster.connect(cluster.after(leader, 2));
        cluster.connect(cluster.after(leader, 3));
        // The disconnected majority may have elected a new leader; either way, agreement resumes.
        int leader2 = cluster.checkOneLeader();
        assertTrue(cluster.propose(leader2, cluster.newCommand()).isPresent());
        cluster.one(cluster.newCommand(), 5, true);
    }

    /** Lab: TestConcurrentStarts3B. Several proposals in the same instant all commit, once each. */
    @ParameterizedTest(name = "seed {0}")
    @MethodSource(SEEDS)
    void concurrentProposals(long seed) {
        RaftCluster cluster = new RaftCluster(3, seed).startAll();
        for (int attempt = 0; attempt < 5; attempt++) {
            int leader = cluster.checkOneLeader();
            Optional<RaftNode.Proposal> first = cluster.propose(leader, cluster.newCommand());
            if (first.isEmpty()) {
                continue; // leader moved on already
            }
            long term = first.get().term();
            List<String> commands = new ArrayList<>();
            List<Long> indices = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                String command = cluster.newCommand();
                Optional<RaftNode.Proposal> p = cluster.propose(leader, command);
                if (p.isEmpty() || p.get().term() != term) {
                    break;
                }
                commands.add(command);
                indices.add(p.get().index());
            }
            Set<String> committed = new HashSet<>();
            boolean termChanged = false;
            for (long index : indices) {
                Optional<String> value = cluster.waitCommitted(index, 3, term);
                if (value.isEmpty()) {
                    termChanged = true;
                    break;
                }
                committed.add(value.get());
            }
            if (termChanged) {
                continue; // a leader change can drop uncommitted proposals; try again
            }
            assertEquals(new HashSet<>(commands), committed, "every concurrent proposal must commit");
            return;
        }
        throw new AssertionError("term changed too often");
    }

    /** Lab: TestRejoin3B. A partitioned leader with uncommitted entries rejoins and is overwritten. */
    @ParameterizedTest(name = "seed {0}")
    @MethodSource(SEEDS)
    void partitionedLeaderRejoins(long seed) {
        RaftCluster cluster = new RaftCluster(3, seed).startAll();
        cluster.one(cluster.newCommand(), 3, true);

        // The leader is partitioned away and accepts entries that can never commit.
        int leader1 = cluster.checkOneLeader();
        cluster.disconnect(leader1);
        cluster.propose(leader1, cluster.newCommand());
        cluster.propose(leader1, cluster.newCommand());
        cluster.propose(leader1, cluster.newCommand());

        // The rest elect a new leader and commit a new entry at the same position.
        cluster.one(cluster.newCommand(), 2, true);

        // The new leader is partitioned and the old one rejoins. Its three uncommitted entries
        // conflict with the committed log and must be discarded, not committed.
        int leader2 = cluster.checkOneLeader();
        cluster.disconnect(leader2);
        cluster.connect(leader1);
        cluster.one(cluster.newCommand(), 2, true);

        cluster.connect(leader2);
        cluster.one(cluster.newCommand(), 3, true);
    }

    /**
     * Lab: TestBackup3B. A leader with 50 uncommitted entries in a minority, then a new majority
     * that commits 50 different entries, then more of the same; logs must be repaired quickly.
     */
    @ParameterizedTest(name = "seed {0}")
    @MethodSource(SEEDS)
    void fastLogBackup(long seed) {
        RaftCluster cluster = new RaftCluster(5, seed).startAll();
        cluster.one(cluster.newCommand(), 5, true);

        // Put the leader and one follower in a partition.
        int leader1 = cluster.checkOneLeader();
        cluster.disconnect(cluster.after(leader1, 2));
        cluster.disconnect(cluster.after(leader1, 3));
        cluster.disconnect(cluster.after(leader1, 4));

        // Submit lots of commands that won't commit.
        for (int i = 0; i < 50; i++) {
            cluster.propose(leader1, cluster.newCommand());
        }
        cluster.sim.runFor(ELECTION_TIMEOUT_MS / 2);

        cluster.disconnect(leader1);
        cluster.disconnect(cluster.after(leader1, 1));

        // Allow the other partition to recover and commit lots of new commands.
        cluster.connect(cluster.after(leader1, 2));
        cluster.connect(cluster.after(leader1, 3));
        cluster.connect(cluster.after(leader1, 4));
        for (int i = 0; i < 50; i++) {
            cluster.one(cluster.newCommand(), 3, true);
        }

        // Now another partitioned leader and one follower.
        int leader2 = cluster.checkOneLeader();
        int other = cluster.after(leader1, 2);
        if (leader2 == other) {
            other = cluster.after(leader2, 1);
        }
        cluster.disconnect(other);

        // Lots more commands that won't commit.
        for (int i = 0; i < 50; i++) {
            cluster.propose(leader2, cluster.newCommand());
        }
        cluster.sim.runFor(ELECTION_TIMEOUT_MS / 2);

        // Bring the original leader back to life.
        for (int i = 1; i <= 5; i++) {
            cluster.disconnect(i);
        }
        cluster.connect(leader1);
        cluster.connect(cluster.after(leader1, 1));
        cluster.connect(other);

        // Lots of new commands.
        for (int i = 0; i < 50; i++) {
            cluster.one(cluster.newCommand(), 3, true);
        }

        // Now everyone.
        for (int i = 1; i <= 5; i++) {
            cluster.connect(i);
        }
        cluster.one(cluster.newCommand(), 5, true);
    }
}
