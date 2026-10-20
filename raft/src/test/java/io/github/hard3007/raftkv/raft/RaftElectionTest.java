package io.github.hard3007.raftkv.raft;

import static io.github.hard3007.raftkv.raft.RaftCluster.ELECTION_TIMEOUT_MS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/** Leader election, modeled on the MIT 6.5840 lab tests 3A. */
class RaftElectionTest {

    private static final String SEEDS = "io.github.hard3007.raftkv.raft.Seeds#seeds";

    /** Lab: TestInitialElection3A. */
    @ParameterizedTest(name = "seed {0}")
    @MethodSource(SEEDS)
    void initialElection(long seed) {
        RaftCluster cluster = new RaftCluster(3, seed).startAll();
        cluster.checkOneLeader();

        cluster.sim.runFor(50);
        long term1 = cluster.checkTerms();
        assertTrue(term1 >= 1, "term is " + term1 + ", but should be at least 1");

        // With no failures, the leader keeps its job and nobody starts a new term.
        cluster.sim.runFor(2 * ELECTION_TIMEOUT_MS);
        assertEquals(term1, cluster.checkTerms(), "term changed even though there were no failures");
        cluster.checkOneLeader();
    }

    /** Lab: TestReElection3A. Re-election after leader failure; no leader without a quorum. */
    @ParameterizedTest(name = "seed {0}")
    @MethodSource(SEEDS)
    void reElectionAfterLeaderFailure(long seed) {
        RaftCluster cluster = new RaftCluster(3, seed).startAll();
        int leader1 = cluster.checkOneLeader();

        // If the leader disconnects, a new one should be elected.
        cluster.disconnect(leader1);
        cluster.checkOneLeader();

        // If the old leader rejoins, it must not disturb the new leader.
        cluster.connect(leader1);
        int leader2 = cluster.checkOneLeader();

        // With no quorum, no new leader should be elected.
        cluster.disconnect(leader2);
        cluster.disconnect(cluster.after(leader2, 1));
        cluster.sim.runFor(2 * ELECTION_TIMEOUT_MS);
        cluster.checkNoLeader();

        // If a quorum arises, it should elect a leader.
        cluster.connect(cluster.after(leader2, 1));
        cluster.checkOneLeader();

        // Re-joining the last node must not prevent a leader from existing.
        cluster.connect(leader2);
        cluster.checkOneLeader();
    }

    /** Lab: TestManyElections3A. 7 nodes, repeatedly losing 3 of them. */
    @ParameterizedTest(name = "seed {0}")
    @MethodSource(SEEDS)
    void manyElections(long seed) {
        RaftCluster cluster = new RaftCluster(7, seed).startAll();
        cluster.checkOneLeader();
        for (int iteration = 0; iteration < 10; iteration++) {
            int a = 1 + cluster.random().nextInt(7);
            int b = 1 + cluster.random().nextInt(7);
            int c = 1 + cluster.random().nextInt(7);
            cluster.disconnect(a);
            cluster.disconnect(b);
            cluster.disconnect(c);
            // Either the current leader is still alive, or the remaining four elect a new one.
            cluster.checkOneLeader();
            cluster.connect(a);
            cluster.connect(b);
            cluster.connect(c);
        }
        cluster.checkOneLeader();
    }

    /**
     * A minority can never elect a leader, no matter how many election timeouts pass. (A leader
     * that is cut off keeps believing it leads: Figure 2 has no "check quorum" rule, and that is
     * safe because it can no longer commit anything. So the old leader is disconnected too.)
     */
    @ParameterizedTest(name = "seed {0}")
    @MethodSource(SEEDS)
    void noLeaderWithoutQuorum(long seed) {
        RaftCluster cluster = new RaftCluster(5, seed).startAll();
        int leader = cluster.checkOneLeader();
        cluster.disconnect(leader);
        cluster.disconnect(cluster.after(leader, 1));
        cluster.disconnect(cluster.after(leader, 2));
        for (int i = 0; i < 5; i++) {
            cluster.sim.runFor(ELECTION_TIMEOUT_MS);
            cluster.checkNoLeader(); // only the two remaining nodes count as connected
        }
        cluster.connect(leader);
        cluster.connect(cluster.after(leader, 1));
        cluster.connect(cluster.after(leader, 2));
        cluster.checkOneLeader();
    }
}
