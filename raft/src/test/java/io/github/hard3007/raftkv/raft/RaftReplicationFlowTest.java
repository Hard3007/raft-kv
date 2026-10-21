package io.github.hard3007.raftkv.raft;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.hard3007.raftkv.proto.raft.AppendEntriesReply;
import io.github.hard3007.raftkv.proto.raft.AppendEntriesRequest;
import io.github.hard3007.raftkv.proto.raft.LogEntry;
import io.github.hard3007.raftkv.proto.raft.RequestVoteReply;
import io.github.hard3007.raftkv.proto.raft.RequestVoteRequest;
import io.github.hard3007.raftkv.raft.sim.Simulation;
import io.github.hard3007.raftkv.storage.FileSystem;
import io.github.hard3007.raftkv.storage.SimulatedFileSystem;
import io.github.hard3007.raftkv.storage.StorageFile;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

/**
 * Batching, pipelining and group commit (Phase 7), message by message, on a leader (node 1 of 3)
 * whose network is driven by hand. The rules under test:
 * <ul>
 *   <li>at most maxAppendsInFlight requests carrying entries are unanswered per follower, and no
 *       entry is sent twice unless a request was refused or presumed lost;
 *   <li>matchIndex (and so the commit index) moves only on a successful reply, never on a send;
 *   <li>with group commit, entries proposed in one burst are written with one fsync, and no
 *       follower gets an entry before the leader's own copy is durable.
 * </ul>
 */
class RaftReplicationFlowTest {

    private record SentAppend(int peer, AppendEntriesRequest request, Consumer<AppendEntriesReply> onReply) {
        void succeed(long term) {
            onReply.accept(AppendEntriesReply.newBuilder().setTerm(term).setSuccess(true).setResponderId(peer).build());
        }

        /** The follower's log ends at {@code lastIndex} (Fig2 AppendEntries-2 with fast backup: conflictTerm 0). */
        void refuseTooShort(long term, long lastIndex) {
            onReply.accept(AppendEntriesReply.newBuilder().setTerm(term).setSuccess(false).setConflictTerm(0)
                    .setConflictIndex(lastIndex + 1).setResponderId(peer).build());
        }

        List<Long> indexes() {
            return request.getEntriesList().stream().map(LogEntry::getIndex).toList();
        }
    }

    /** Counts fsyncs of every file it opens. */
    private static final class CountingFileSystem implements FileSystem {
        final FileSystem inner = new SimulatedFileSystem(new Random(1));
        int fsyncs;

        @Override
        public StorageFile open(String name) {
            StorageFile file = inner.open(name);
            return new StorageFile() {
                @Override
                public void read(long position, byte[] dst, int offset, int length) {
                    file.read(position, dst, offset, length);
                }

                @Override
                public void write(long position, byte[] src, int offset, int length) {
                    file.write(position, src, offset, length);
                }

                @Override
                public long size() {
                    return file.size();
                }

                @Override
                public void truncate(long size) {
                    file.truncate(size);
                }

                @Override
                public void force() {
                    fsyncs++;
                    file.force();
                }

                @Override
                public void close() {
                    file.close();
                }
            };
        }

        @Override
        public boolean exists(String name) {
            return inner.exists(name);
        }

        @Override
        public void delete(String name) {
            inner.delete(name);
        }

        @Override
        public void replace(String source, String target) {
            inner.replace(source, target);
        }
    }

    private final Simulation sim = new Simulation(1);
    private final List<SentAppend> appends = new ArrayList<>();
    private final CountingFileSystem disk = new CountingFileSystem();
    private final List<LogEntry> applied = new ArrayList<>();
    private RaftNode leader;

    /** Elects node 1 with peer 2's vote; with group commit, also runs the flush of its no-op. */
    private void elect(List<Integer> peers, int maxAppendsInFlight, boolean groupCommit) {
        List<SentVoteRequest> votes = new ArrayList<>();
        Transport manual = new Transport() {
            @Override
            public void requestVote(int peer, RequestVoteRequest request, Consumer<RequestVoteReply> onReply) {
                votes.add(new SentVoteRequest(peer, request, onReply));
            }

            @Override
            public void appendEntries(int peer, AppendEntriesRequest request, Consumer<AppendEntriesReply> onReply) {
                appends.add(new SentAppend(peer, request, onReply));
            }
        };
        RaftConfig config = RaftConfig.defaults().withPreVote(false).withMaxAppendsInFlight(maxAppendsInFlight)
                .withGroupCommit(groupCommit);
        leader = new RaftNode(1, peers, config, sim.newNodeScheduler(), manual, RaftStorage.open(disk, "raft.wal"),
                applied::add, 0, new Random(1));
        leader.start();
        sim.runFor(300); // the election timer (150-300 ms) fires once
        for (SentVoteRequest vote : votes) {
            if (vote.peer() == 2) {
                vote.onReply().accept(RequestVoteReply.newBuilder().setTerm(vote.request().getTerm())
                        .setVoteGranted(true).setResponderId(2).build());
            }
        }
        sim.runFor(0); // group commit: the no-op's flush
        assertEquals(RaftNode.Role.LEADER, leader.status().role());
    }

    private record SentVoteRequest(int peer, RequestVoteRequest request, Consumer<RequestVoteReply> onReply) {
    }

    private long term() {
        return leader.status().term();
    }

    private List<SentAppend> sentTo(int peer) {
        return appends.stream().filter(a -> a.peer() == peer).toList();
    }

    private List<SentAppend> withEntriesTo(int peer) {
        return sentTo(peer).stream().filter(a -> a.request().getEntriesCount() > 0).toList();
    }

    private SentAppend last(List<SentAppend> sent) {
        return sent.get(sent.size() - 1);
    }

    private void propose(int count) {
        for (int i = 0; i < count; i++) {
            assertTrue(leader.propose(new byte[] {(byte) i}).isPresent());
        }
    }

    @Test
    void batchingSendsOneRequestAtATimeAndPutsEverythingProposedMeanwhileInTheNext() {
        elect(List.of(2, 3), 1, false);
        assertEquals(List.of(List.of(1L)), withEntriesTo(2).stream().map(SentAppend::indexes).toList(), "the no-op");

        propose(3); // the window to each follower is full: these wait
        assertEquals(1, withEntriesTo(2).size());

        withEntriesTo(2).get(0).succeed(term());
        assertEquals(List.of(2L, 3L, 4L), last(withEntriesTo(2)).indexes(), "one request carries all three");
        assertEquals(1, withEntriesTo(3).size(), "peer 3 has not answered yet, so it still waits");
    }

    @Test
    void pipeliningKeepsUpToNRequestsInFlightAndSendsEachEntryOnce() {
        elect(List.of(2, 3), 2, false);
        propose(1); // second request in flight: entry 2 only (entry 1 is already on its way)
        propose(2); // window full
        assertEquals(List.of(List.of(1L), List.of(2L)), withEntriesTo(2).stream().map(SentAppend::indexes).toList());

        withEntriesTo(2).get(0).succeed(term()); // a slot frees up
        assertEquals(List.of(3L, 4L), last(withEntriesTo(2)).indexes());
    }

    @Test
    void sendingNeverCommitsOnlySuccessfulRepliesDoAndTheyMayArriveOutOfOrder() {
        elect(List.of(2, 3), 4, false);
        propose(2); // entries 1 (no-op), 2 and 3 are all in flight to both peers
        assertEquals(3, withEntriesTo(2).size());
        assertEquals(0, leader.status().commitIndex(), "nothing is committed by sending");

        withEntriesTo(2).get(2).succeed(term()); // the reply for entry 3 overtakes the others
        // matchIndex[2] = prevLogIndex + entries = 3: entries 1-3 are on the leader and peer 2, a majority.
        assertEquals(3, leader.status().commitIndex());
        assertEquals(3, applied.size());
    }

    @Test
    void aRefusedPipelinedRequestRestartsTheWindowAtNextIndex() {
        elect(List.of(2, 3), 2, false);
        propose(1);
        SentAppend second = withEntriesTo(2).get(1); // entry 2, prevLogIndex 1
        // It overtook the first request: peer 2's log is still empty, so it refuses.
        second.refuseTooShort(term(), 0);
        SentAppend resent = last(sentTo(2));
        assertEquals(0, resent.request().getPrevLogIndex());
        assertEquals(List.of(1L, 2L), resent.indexes(), "starts again at nextIndex = 1");
    }

    @Test
    void requestsWithNoAnswerForAMinimumElectionTimeoutAreResent() {
        elect(List.of(2, 3), 1, false);
        withEntriesTo(3).get(0).succeed(term()); // peer 3 keeps the leader in office (CheckQuorum)
        propose(1);
        sim.runFor(100); // heartbeats: the window to peer 2 is full, so they carry no entries
        assertEquals(1, withEntriesTo(2).size());
        assertTrue(sentTo(2).size() > 1, "heartbeats still go out");

        sim.runFor(100); // more than 150 ms since the no-op was sent with no answer: presumed lost
        assertEquals(List.of(1L, 2L), last(withEntriesTo(2)).indexes());
    }

    @Test
    void groupCommitWritesABurstWithOneFsyncAndReplicatesOnlyDurableEntries() {
        elect(List.of(2, 3), 4, true);
        withEntriesTo(2).get(0).succeed(term()); // the no-op commits
        int fsyncsBefore = disk.fsyncs;
        int sentBefore = withEntriesTo(2).size();

        propose(3);
        assertEquals(fsyncsBefore, disk.fsyncs, "not written yet");
        assertEquals(sentBefore, withEntriesTo(2).size(), "followers never get entries the leader has not persisted");

        sim.runFor(0); // the flush task
        assertEquals(fsyncsBefore + 1, disk.fsyncs, "one fsync for the whole burst");
        assertEquals(List.of(2L, 3L, 4L), last(withEntriesTo(2)).indexes());
        assertEquals(List.of(2L, 3L, 4L), last(withEntriesTo(3)).indexes());
    }

    @Test
    void withGroupCommitASingleNodeCommitsOnlyAfterItsFlush() {
        // Look at the node right after the event in which it became leader, before the flush
        // task that writes its no-op has run: the no-op must not be committed yet.
        int[] appliedWhenElected = {-1};
        sim.setAfterEachEvent(() -> {
            if (appliedWhenElected[0] < 0 && leader != null && leader.status().role() == RaftNode.Role.LEADER) {
                appliedWhenElected[0] = applied.size();
            }
        });
        elect(List.of(), 4, true); // its own vote is a majority
        assertEquals(0, appliedWhenElected[0], "the leader counted its own copy before it was durable");
        int appliedBefore = applied.size(); // the no-op, after the flush
        assertEquals(1, appliedBefore);
        leader.propose(new byte[] {1});
        assertEquals(appliedBefore, applied.size(), "not durable on the only node yet");
        sim.runFor(0);
        assertEquals(appliedBefore + 1, applied.size());
    }

    @Test
    void steppingDownWritesThePendingEntriesFirst() {
        elect(List.of(2, 3), 4, true);
        propose(2);
        int fsyncsBefore = disk.fsyncs;
        // Before the flush runs, a reply from a newer term deposes us.
        last(sentTo(2)).onReply().accept(AppendEntriesReply.newBuilder().setTerm(term() + 1).setSuccess(false)
                .setResponderId(2).build());
        assertEquals(RaftNode.Role.FOLLOWER, leader.status().role());
        assertTrue(disk.fsyncs > fsyncsBefore, "pending entries (and the new term) were written");
        assertEquals(3, leader.status().lastLogIndex(), "the entries stay in the log, uncommitted");
    }
}
