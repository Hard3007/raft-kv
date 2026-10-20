package io.github.hard3007.raftkv.raft;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.hard3007.raftkv.common.ExecutorScheduler;
import io.github.hard3007.raftkv.proto.raft.AppendEntriesReply;
import io.github.hard3007.raftkv.proto.raft.AppendEntriesRequest;
import io.github.hard3007.raftkv.proto.raft.RequestVoteReply;
import io.github.hard3007.raftkv.proto.raft.RequestVoteRequest;
import io.github.hard3007.raftkv.storage.SimulatedFileSystem;
import java.util.List;
import java.util.Random;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

/** Every RaftNode entry point refuses to run anywhere but on its node's executor thread. */
class RaftThreadConfinementTest {

    private static final Transport SILENT = new Transport() {
        @Override
        public void requestVote(int peer, RequestVoteRequest request, Consumer<RequestVoteReply> onReply) {
        }

        @Override
        public void appendEntries(int peer, AppendEntriesRequest request, Consumer<AppendEntriesReply> onReply) {
        }
    };

    @Test
    void entryPointsFailLoudlyOffTheNodeThreadAndWorkOnIt() throws Exception {
        try (ExecutorScheduler nodeThread = new ExecutorScheduler("raft-node-7")) {
            RaftNode node = new RaftNode(7, List.of(8, 9), RaftConfig.defaults(), nodeThread, SILENT,
                    RaftStorage.open(new SimulatedFileSystem(new Random(1)), "raft.wal"), entry -> { }, 0, new Random(1));

            // From the test thread (think: a gRPC thread that forgot to post): every entry point throws.
            assertThrows(IllegalStateException.class, node::start);
            assertThrows(IllegalStateException.class, node::status);
            assertThrows(IllegalStateException.class, () -> node.propose(new byte[] {1}));
            assertThrows(IllegalStateException.class, () -> node.handleRequestVote(RequestVoteRequest.getDefaultInstance()));
            assertThrows(IllegalStateException.class,
                    () -> node.handleAppendEntries(AppendEntriesRequest.getDefaultInstance()));
            assertThrows(IllegalStateException.class, () -> node.readIndex(new RaftNode.ReadIndexCallback() {
                @Override
                public void onReady() {
                }

                @Override
                public void onNotLeader() {
                }
            }));

            // On the node's thread, the same calls work.
            nodeThread.call(() -> {
                node.start();
                return null;
            }, 1000);
            assertEquals(RaftNode.Role.FOLLOWER, nodeThread.call(node::status, 1000).role());
            assertEquals(0, nodeThread.failedTasks());
        }
    }
}
