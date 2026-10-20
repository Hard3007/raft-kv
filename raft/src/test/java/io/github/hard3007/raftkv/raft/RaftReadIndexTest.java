package io.github.hard3007.raftkv.raft;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.hard3007.raftkv.proto.raft.AppendEntriesReply;
import io.github.hard3007.raftkv.proto.raft.AppendEntriesRequest;
import io.github.hard3007.raftkv.proto.raft.EntryType;
import io.github.hard3007.raftkv.proto.raft.RequestVoteReply;
import io.github.hard3007.raftkv.proto.raft.RequestVoteRequest;
import io.github.hard3007.raftkv.raft.sim.Simulation;
import io.github.hard3007.raftkv.storage.SimulatedFileSystem;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * ReadIndex, step by step, on a leader (node 1 of 3) whose network is driven by hand: the test
 * decides which AppendEntries get answered and when.
 */
class RaftReadIndexTest {

    private record SentAppend(int peer, AppendEntriesRequest request, Consumer<AppendEntriesReply> onReply) {
        void answer(long term, boolean success) {
            onReply.accept(AppendEntriesReply.newBuilder().setTerm(term).setSuccess(success).build());
        }
    }

    private record SentVote(int peer, RequestVoteRequest request, Consumer<RequestVoteReply> onReply) {
    }

    private final Simulation sim = new Simulation(1);
    private final List<SentAppend> appends = new ArrayList<>();
    private final List<SentVote> votes = new ArrayList<>();
    private RaftNode leader;

    /** Records the outcome of one read. */
    private static final class Read implements RaftNode.ReadIndexCallback {
        boolean ready;
        boolean notLeader;

        @Override
        public void onReady() {
            ready = true;
        }

        @Override
        public void onNotLeader() {
            notLeader = true;
        }
    }

    @BeforeEach
    void electNodeOneOfThree() {
        elect(List.of(2, 3));
    }

    /** Makes node 1 the leader of a cluster with the given peers, granting it peer 2's vote. */
    private void elect(List<Integer> peers) {
        if (leader != null) {
            leader.stop(); // a test that re-elects must not hear the previous leader's heartbeats
        }
        appends.clear();
        votes.clear();
        Transport manual = new Transport() {
            @Override
            public void requestVote(int peer, RequestVoteRequest request, Consumer<RequestVoteReply> onReply) {
                votes.add(new SentVote(peer, request, onReply));
            }

            @Override
            public void appendEntries(int peer, AppendEntriesRequest request, Consumer<AppendEntriesReply> onReply) {
                appends.add(new SentAppend(peer, request, onReply));
            }
        };
        leader = new RaftNode(1, peers, RaftNodeTest.FIGURE_2_ONLY, sim.newNodeScheduler(), manual,
                RaftStorage.open(new SimulatedFileSystem(new Random(1)), "raft.wal"), entry -> { }, 0, new Random(1));
        leader.start();
        sim.runFor(300); // the election timer (150-300 ms) fires once
        for (int peer : peers.subList(0, peers.size() / 2)) { // enough votes for a majority
            SentVote vote = lastVoteFrom(peer);
            vote.onReply().accept(RequestVoteReply.newBuilder().setTerm(vote.request().getTerm()).setVoteGranted(true).build());
        }
        assertEquals(RaftNode.Role.LEADER, leader.status().role());
    }

    private SentVote lastVoteFrom(int peer) {
        for (int i = votes.size() - 1; i >= 0; i--) {
            if (votes.get(i).peer() == peer) {
                return votes.get(i);
            }
        }
        throw new AssertionError("no RequestVote to " + peer);
    }

    private long term() {
        return leader.status().term();
    }

    /** The latest AppendEntries sent to {@code peer}. */
    private SentAppend lastAppendTo(int peer) {
        for (int i = appends.size() - 1; i >= 0; i--) {
            if (appends.get(i).peer() == peer) {
                return appends.get(i);
            }
        }
        throw new AssertionError("nothing sent to " + peer);
    }

    @Test
    void followersAnswerNotLeaderImmediately() {
        Read read = new Read();
        RaftNode follower = new RaftNode(9, List.of(1), RaftConfig.defaults(), sim.newNodeScheduler(), null,
                RaftStorage.open(new SimulatedFileSystem(new Random(2)), "raft.wal"), entry -> { }, 0, new Random(2));
        follower.readIndex(read); // never started, so it is a follower
        assertTrue(read.notLeader);
        assertFalse(read.ready);
    }

    @Test
    void step1_readWaitsUntilTheLeadersNoopCommits() {
        SentAppend noopToPeer2 = lastAppendTo(2);
        assertEquals(EntryType.NOOP, noopToPeer2.request().getEntries(0).getType());

        Read read = new Read();
        leader.readIndex(read);
        // Even fresh heartbeat acknowledgements cannot serve it while the no-op is uncommitted.
        sim.runFor(0);
        lastAppendTo(3).answer(term(), false); // peer 3 accepts our term but rejects the entries
        assertFalse(read.ready, "served a read before the no-op committed");

        noopToPeer2.answer(term(), true); // the no-op is now on a majority (leader + peer 2): committed
        assertEquals(1, leader.status().commitIndex());
        assertFalse(read.ready, "the confirming round must start after the read's index is fixed");

        sim.runFor(0); // the heartbeat round for the read starts
        lastAppendTo(2).answer(term(), true);
        assertTrue(read.ready);
    }

    @Test
    void step2_aReplyToAnAppendSentBeforeTheReadDoesNotConfirmIt() {
        lastAppendTo(2).answer(term(), true); // commit the no-op
        sim.runFor(60); // a regular heartbeat goes out to both peers...
        SentAppend oldHeartbeatTo2 = lastAppendTo(2);
        SentAppend oldHeartbeatTo3 = lastAppendTo(3);

        Read read = new Read();
        leader.readIndex(read); // ...and only then does the read arrive

        oldHeartbeatTo2.answer(term(), true);
        oldHeartbeatTo3.answer(term(), true);
        assertFalse(read.ready, "acknowledgements of messages sent before the read prove nothing about now");

        sim.runFor(0); // the round started for this read
        SentAppend fresh = lastAppendTo(3);
        assertTrue(appends.indexOf(fresh) > appends.indexOf(oldHeartbeatTo3));
        fresh.answer(term(), true);
        assertTrue(read.ready, "leader + peer 3 is a majority");
    }

    @Test
    void step2_oneAcknowledgementIsNotAMajorityOfFive() {
        elect(List.of(2, 3, 4, 5));
        lastAppendTo(2).answer(term(), true);
        lastAppendTo(3).answer(term(), true); // no-op on leader + 2 peers: committed
        Read read = new Read();
        leader.readIndex(read);
        sim.runFor(0);
        lastAppendTo(4).answer(term(), true);
        assertFalse(read.ready, "leader + 1 of 4 peers is not a majority of 5");
        lastAppendTo(5).answer(term(), true);
        assertTrue(read.ready);
    }

    @Test
    void steppingDownFailsPendingReads() {
        lastAppendTo(2).answer(term(), true); // commit the no-op
        Read read = new Read();
        leader.readIndex(read);
        sim.runFor(0);
        // A peer has moved on to a newer term: we are deposed before confirmation.
        lastAppendTo(2).answer(term() + 1, false);
        assertEquals(RaftNode.Role.FOLLOWER, leader.status().role());
        assertTrue(read.notLeader);
        assertFalse(read.ready);
    }

    @Test
    void readsArrivingTogetherShareOneRound() {
        lastAppendTo(2).answer(term(), true);
        Read first = new Read();
        Read second = new Read();
        leader.readIndex(first);
        leader.readIndex(second);
        int before = appends.size();
        sim.runFor(0);
        assertEquals(2, appends.size() - before, "one AppendEntries per peer for both reads");
        lastAppendTo(3).answer(term(), true);
        assertTrue(first.ready && second.ready);
    }
}
