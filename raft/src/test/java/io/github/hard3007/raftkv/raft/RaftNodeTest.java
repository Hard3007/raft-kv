package io.github.hard3007.raftkv.raft;

import static io.github.hard3007.raftkv.raft.RaftCluster.entry;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.hard3007.raftkv.proto.raft.AppendEntriesReply;
import io.github.hard3007.raftkv.proto.raft.AppendEntriesRequest;
import io.github.hard3007.raftkv.proto.raft.LogEntry;
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
 * Figure 2's receiver rules, one at a time, on a single node (id 1 of a 3-node cluster) whose
 * RPC handlers are called directly. Each test names the rule it covers.
 */
class RaftNodeTest {

    /**
     * These tests drive Figure 2 rules one at a time; PreVote has its own tests (RaftPreVoteTest).
     * Figure 2 and nothing more: no PreVote, every proposal sent at once to every follower
     * (no flow control), and every entry fsynced on its own. Tests that drive single messages by
     * hand use it; RaftReplicationFlowTest covers batching, pipelining and group commit.
     */
    static final RaftConfig FIGURE_2_ONLY = RaftConfig.defaults().withPreVote(false).withMaxAppendsInFlight(0)
            .withGroupCommit(false);

    private final Simulation sim = new Simulation(1);
    private final SimulatedFileSystem disk = new SimulatedFileSystem(new Random(1));
    private final List<LogEntry> applied = new ArrayList<>();
    private RaftNode node;

    /** A transport that sends nothing: these tests only exercise the receiving side. */
    private static final Transport SILENT = new Transport() {
        @Override
        public void requestVote(int peer, RequestVoteRequest request, Consumer<RequestVoteReply> onReply) {
        }

        @Override
        public void appendEntries(int peer, AppendEntriesRequest request, Consumer<AppendEntriesReply> onReply) {
        }
    };

    @BeforeEach
    void startEmptyNode() {
        node = boot();
    }

    /** Starts (or, after a crash, restarts) node 1 from whatever is on its disk. */
    private RaftNode boot() {
        RaftNode n = new RaftNode(1, List.of(2, 3), FIGURE_2_ONLY, sim.newNodeScheduler(), SILENT,
                RaftStorage.open(disk, "raft.wal"), applied::add, 0, new Random(1));
        n.start();
        return n;
    }

    /** Power loss, then a fresh process reading the disk. */
    private RaftNode crashAndRestart() {
        node.stop();
        disk.simulatePowerLoss();
        applied.clear();
        return boot();
    }

    /** Gives node 1 a log (term of each entry, starting at index 1) via a leader's AppendEntries. */
    private void giveLog(long leaderTerm, long... terms) {
        AppendEntriesRequest.Builder request = AppendEntriesRequest.newBuilder()
                .setTerm(leaderTerm).setLeaderId(2).setPrevLogIndex(0).setPrevLogTerm(0);
        for (int i = 0; i < terms.length; i++) {
            request.addEntries(entry(i + 1, terms[i], "e" + (i + 1)));
        }
        assertTrue(node.handleAppendEntries(request.build()).getSuccess());
    }

    private RequestVoteReply vote(long term, int candidate, long lastIndex, long lastTerm) {
        return node.handleRequestVote(RequestVoteRequest.newBuilder()
                .setTerm(term).setCandidateId(candidate).setLastLogIndex(lastIndex).setLastLogTerm(lastTerm).build());
    }

    private AppendEntriesReply append(long term, long prevIndex, long prevTerm, long leaderCommit, LogEntry... entries) {
        return node.handleAppendEntries(AppendEntriesRequest.newBuilder()
                .setTerm(term).setLeaderId(2).setPrevLogIndex(prevIndex).setPrevLogTerm(prevTerm)
                .addAllEntries(List.of(entries)).setLeaderCommit(leaderCommit).build());
    }

    // ------------------------------------------------------------------ RequestVote

    @Test
    void requestVote1_rejectsCandidateFromAnOlderTerm() {
        vote(5, 2, 0, 0);
        RequestVoteReply reply = vote(4, 3, 10, 4);
        assertFalse(reply.getVoteGranted());
        assertEquals(5, reply.getTerm(), "reply carries currentTerm so the candidate can update itself");
    }

    @Test
    void requestVote2_votesForAtMostOneCandidatePerTerm() {
        assertTrue(vote(5, 2, 0, 0).getVoteGranted());
        assertFalse(vote(5, 3, 0, 0).getVoteGranted(), "already voted for 2 in term 5");
        assertTrue(vote(5, 2, 0, 0).getVoteGranted(), "a repeated request from the same candidate is granted again");
        assertTrue(vote(6, 3, 0, 0).getVoteGranted(), "a new term allows a new vote");
    }

    @Test
    void requestVote2_deniesCandidateWhoseLastTermIsOlderEvenIfItsLogIsLonger() {
        giveLog(3, 1, 3); // our last entry is from term 3
        RequestVoteReply reply = vote(4, 2, 5, 2); // longer log, but last term 2 < 3
        assertFalse(reply.getVoteGranted(), "§5.4.1: the later last term wins regardless of length");
    }

    @Test
    void requestVote2_withEqualLastTermsTheLongerLogWins() {
        giveLog(3, 1, 3, 3);
        assertFalse(vote(4, 2, 2, 3).getVoteGranted(), "same last term, shorter log");
        assertTrue(vote(4, 3, 3, 3).getVoteGranted(), "same last term, same length");
    }

    @Test
    void requestVote2_grantsCandidateWithNewerLastTermEvenIfShorter() {
        giveLog(3, 1, 1, 1);
        assertTrue(vote(4, 2, 1, 2).getVoteGranted());
    }

    @Test
    void all2_newerTermInARequestMakesUsAdoptItAndForgetOurVote() {
        assertTrue(vote(5, 2, 0, 0).getVoteGranted());
        RequestVoteReply reply = vote(7, 3, 0, 0);
        assertTrue(reply.getVoteGranted(), "the vote for 2 was in term 5, not term 7");
        assertEquals(7, node.status().term());
        assertEquals(RaftNode.Role.FOLLOWER, node.status().role());
    }

    @Test
    void persist_voteAndTermSurviveACrash() {
        assertTrue(vote(7, 2, 0, 0).getVoteGranted());
        node = crashAndRestart();
        assertEquals(7, node.status().term(), "currentTerm must be durable before replying");
        assertFalse(vote(7, 3, 0, 0).getVoteGranted(),
                "votedFor must be durable before replying, or a crash allows two votes in one term");
    }

    @Test
    void persist_termLearnedFromARejectedRequestSurvivesACrash() {
        giveLog(1, 1);
        assertFalse(vote(9, 2, 0, 0).getVoteGranted(), "candidate's empty log is behind ours");
        node = crashAndRestart();
        assertEquals(9, node.status().term(), "term 9 was adopted (All-2) and must be durable");
        assertTrue(vote(9, 3, 1, 1).getVoteGranted(), "no vote was cast in term 9 yet");
    }

    // ------------------------------------------------------------------ AppendEntries

    @Test
    void appendEntries1_rejectsLeaderFromAnOlderTerm() {
        vote(5, 2, 0, 0);
        AppendEntriesReply reply = append(4, 0, 0, 0, entry(1, 4, "x"));
        assertFalse(reply.getSuccess());
        assertEquals(5, reply.getTerm());
        assertEquals(0, node.status().lastLogIndex());
    }

    @Test
    void appendEntries2_missingPrevEntryReportsLogLength() {
        giveLog(1, 1, 1);
        AppendEntriesReply reply = append(2, 5, 2, 0);
        assertFalse(reply.getSuccess());
        assertEquals(0, reply.getConflictTerm(), "log too short");
        assertEquals(3, reply.getConflictIndex(), "retry right after our last entry");
    }

    @Test
    void appendEntries2_mismatchedPrevTermReportsFirstIndexOfThatTerm() {
        giveLog(3, 1, 2, 2, 2);
        AppendEntriesReply reply = append(4, 4, 3, 0);
        assertFalse(reply.getSuccess());
        assertEquals(2, reply.getConflictTerm());
        assertEquals(2, reply.getConflictIndex(), "first index holding term 2");
        assertEquals(4, node.status().lastLogIndex(), "a failed check must not modify the log");
    }

    @Test
    void appendEntries3_conflictingEntriesAndEverythingAfterThemAreDeleted() {
        giveLog(1, 1, 1, 1);
        AppendEntriesReply reply = append(2, 1, 1, 0, entry(2, 2, "new"));
        assertTrue(reply.getSuccess());
        assertEquals(2, node.status().lastLogIndex(), "entry 3 followed the conflict and must be gone");
        assertEquals(2, node.entryAt(2).getTerm());
        assertEquals("new", node.entryAt(2).getCommand().toStringUtf8());
    }

    @Test
    void appendEntries3_aDelayedDuplicateDoesNotTruncateLaterEntries() {
        giveLog(1, 1, 1, 1);
        assertTrue(append(1, 0, 0, 0, entry(1, 1, "e1")).getSuccess()); // old, reordered message
        assertEquals(3, node.status().lastLogIndex());
    }

    @Test
    void appendEntries4_newEntriesAreAppended() {
        giveLog(1, 1);
        assertTrue(append(1, 1, 1, 0, entry(2, 1, "b"), entry(3, 1, "c")).getSuccess());
        assertEquals(3, node.status().lastLogIndex());
    }

    @Test
    void appendEntries5_commitIndexIsTheSmallerOfLeaderCommitAndLastNewEntry() {
        giveLog(1, 1, 1, 1, 1, 1);
        // The leader has committed 5, but this message only vouches for entries up to index 2.
        assertTrue(append(1, 1, 1, 5, entry(2, 1, "e2")).getSuccess());
        assertEquals(2, node.status().commitIndex());
        assertEquals(2, applied.size(), "[All-1] committed entries are applied in order");
        assertTrue(append(1, 5, 1, 5).getSuccess());
        assertEquals(5, node.status().commitIndex());
        assertEquals(List.of(1L, 2L, 3L, 4L, 5L), applied.stream().map(LogEntry::getIndex).toList());
    }

    @Test
    void appendEntries5_commitIndexNeverMovesBackwards() {
        giveLog(1, 1, 1, 1);
        assertTrue(append(1, 3, 1, 3).getSuccess());
        assertTrue(append(1, 0, 0, 1).getSuccess()); // delayed older heartbeat
        assertEquals(3, node.status().commitIndex());
    }

    @Test
    void persist_logSurvivesACrashIncludingTruncation() {
        giveLog(1, 1, 1, 1);
        append(2, 1, 1, 0, entry(2, 2, "new"));
        node = crashAndRestart();
        assertEquals(2, node.status().lastLogIndex());
        assertEquals(2, node.status().term());
        assertEquals("new", node.entryAt(2).getCommand().toStringUtf8());
    }

    @Test
    void candidate3_candidateStepsDownOnAppendEntriesFromLeaderOfSameTerm() {
        sim.runFor(400); // the election timer fires: node 1 becomes a candidate in term 1
        assertEquals(RaftNode.Role.CANDIDATE, node.status().role());
        long term = node.status().term();
        assertTrue(append(term, 0, 0, 0).getSuccess());
        assertEquals(RaftNode.Role.FOLLOWER, node.status().role());
        assertEquals(2, node.status().leaderId());
    }

    @Test
    void persist_candidatesSelfVoteSurvivesACrash() {
        sim.runFor(400); // becomes candidate in term 1, voting for itself
        long term = node.status().term();
        node = crashAndRestart();
        assertFalse(vote(term, 2, 0, 0).getVoteGranted(), "it already voted for itself in this term");
    }
}
