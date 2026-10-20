package io.github.hard3007.raftkv.raft;

import static io.github.hard3007.raftkv.raft.RaftCluster.ELECTION_TIMEOUT_MS;
import static io.github.hard3007.raftkv.raft.RaftCluster.entry;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.hard3007.raftkv.proto.raft.AppendEntriesReply;
import io.github.hard3007.raftkv.proto.raft.AppendEntriesRequest;
import io.github.hard3007.raftkv.proto.raft.RequestVoteReply;
import io.github.hard3007.raftkv.proto.raft.RequestVoteRequest;
import io.github.hard3007.raftkv.raft.sim.Simulation;
import io.github.hard3007.raftkv.storage.SimulatedFileSystem;
import java.util.List;
import java.util.Random;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/** PreVote (thesis §9.6) and CheckQuorum (thesis §6.2). */
class RaftPreVoteTest {

    private static final String SEEDS = "io.github.hard3007.raftkv.raft.Seeds#seeds";

    private static final Transport SILENT = new Transport() {
        @Override
        public void requestVote(int peer, RequestVoteRequest request, Consumer<RequestVoteReply> onReply) {
        }

        @Override
        public void appendEntries(int peer, AppendEntriesRequest request, Consumer<AppendEntriesReply> onReply) {
        }
    };

    private final Simulation sim = new Simulation(1);

    private RaftNode node() {
        RaftNode node = new RaftNode(1, List.of(2, 3), RaftConfig.defaults(), sim.newNodeScheduler(), SILENT,
                RaftStorage.open(new SimulatedFileSystem(new Random(1)), "raft.wal"), entry -> { }, 0, new Random(1));
        node.start();
        return node;
    }

    private static RequestVoteRequest preVote(long term, long lastIndex, long lastTerm) {
        return RequestVoteRequest.newBuilder().setTerm(term).setCandidateId(2).setLastLogIndex(lastIndex)
                .setLastLogTerm(lastTerm).setPreVote(true).build();
    }

    private static AppendEntriesRequest heartbeat(long term, long prevIndex, long prevTerm) {
        return AppendEntriesRequest.newBuilder().setTerm(term).setLeaderId(3).setPrevLogIndex(prevIndex)
                .setPrevLogTerm(prevTerm).build();
    }

    // ------------------------------------------------------------------ receiver rules

    @Test
    void grantingAPreVoteChangesNoState() {
        RaftNode node = node();
        assertTrue(node.handleRequestVote(preVote(5, 0, 0)).getVoteGranted());
        assertEquals(0, node.status().term(), "a PreVote must not move our term");
        // Our vote in term 5 is still free: a real candidate other than 2 can get it.
        RequestVoteRequest real = RequestVoteRequest.newBuilder().setTerm(5).setCandidateId(3).build();
        assertTrue(node.handleRequestVote(real).getVoteGranted());
    }

    @Test
    void preVoteIsDeniedWhileALeaderIsAlive() {
        RaftNode node = node();
        assertTrue(node.handleAppendEntries(heartbeat(4, 0, 0)).getSuccess()); // leader 3 of term 4
        assertFalse(node.handleRequestVote(preVote(5, 0, 0)).getVoteGranted(), "heard from leader just now");
        sim.runFor(RaftConfig.defaults().electionTimeoutMinMs()); // ...but no further heartbeat arrives
        assertTrue(node.handleRequestVote(preVote(5, 0, 0)).getVoteGranted(), "the leader went quiet");
    }

    @Test
    void preVoteIsDeniedForAStaleLogOrAnOldTerm() {
        RaftNode node = node();
        node.handleAppendEntries(AppendEntriesRequest.newBuilder().setTerm(4).setLeaderId(3)
                .addEntries(entry(1, 4, "x")).build());
        sim.runFor(RaftConfig.defaults().electionTimeoutMinMs());
        assertFalse(node.handleRequestVote(preVote(5, 0, 0)).getVoteGranted(), "candidate's log is behind ours");
        assertFalse(node.handleRequestVote(preVote(4, 1, 4)).getVoteGranted(), "term 4 is not newer than ours");
        assertTrue(node.handleRequestVote(preVote(5, 1, 4)).getVoteGranted());
    }

    // ------------------------------------------------------------------ in a cluster

    /**
     * Without PreVote, a follower cut off from the cluster keeps timing out and incrementing its
     * term; when it returns, that higher term deposes the healthy leader. With PreVote it cannot
     * get a majority of PreVotes while away, so its term stays put and nothing is disrupted.
     */
    @ParameterizedTest(name = "seed {0}")
    @MethodSource(SEEDS)
    void aPartitionedFollowerNeitherInflatesItsTermNorDeposesTheLeader(long seed) {
        RaftCluster cluster = new RaftCluster(5, seed).startAll();
        int leader = cluster.checkOneLeader();
        long term = cluster.node(leader).status().term();
        int follower = cluster.after(leader, 1);

        cluster.disconnect(follower);
        cluster.sim.runFor(5 * ELECTION_TIMEOUT_MS); // dozens of election timeouts
        assertEquals(term, cluster.node(follower).status().term(), "the isolated follower inflated its term");

        cluster.connect(follower);
        cluster.sim.runFor(2 * ELECTION_TIMEOUT_MS);
        assertEquals(leader, cluster.checkOneLeader(), "the returning follower deposed the leader");
        assertEquals(term, cluster.node(leader).status().term());
    }

    /** CheckQuorum: a leader cut off from every follower steps down within about an election timeout. */
    @ParameterizedTest(name = "seed {0}")
    @MethodSource(SEEDS)
    void anIsolatedLeaderStepsDown(long seed) {
        RaftCluster cluster = new RaftCluster(5, seed).startAll();
        int leader = cluster.checkOneLeader();
        cluster.disconnect(leader);
        boolean steppedDown = cluster.sim.runUntil(
                () -> cluster.node(leader).status().role() != RaftNode.Role.LEADER, ELECTION_TIMEOUT_MS);
        assertTrue(steppedDown, "an isolated leader must not keep believing it leads");
        cluster.connect(leader);
        cluster.one(cluster.newCommand(), 5, true);
    }
}
