package io.github.hard3007.raftkv.raft;

import io.github.hard3007.raftkv.proto.raft.AppendEntriesReply;
import io.github.hard3007.raftkv.proto.raft.AppendEntriesRequest;
import io.github.hard3007.raftkv.proto.raft.InstallSnapshotReply;
import io.github.hard3007.raftkv.proto.raft.InstallSnapshotRequest;
import io.github.hard3007.raftkv.proto.raft.RequestVoteReply;
import io.github.hard3007.raftkv.proto.raft.RequestVoteRequest;
import java.util.function.Consumer;

/**
 * Outgoing Raft RPCs. Sends are fire-and-forget: {@code onReply} runs on the sending node's
 * thread if and when a reply arrives, and never runs if the request or reply is lost. Raft does
 * not need RPC timeouts, because the leader resends on every heartbeat and candidates time out
 * and start a new election.
 */
public interface Transport {

    void requestVote(int peer, RequestVoteRequest request, Consumer<RequestVoteReply> onReply);

    void appendEntries(int peer, AppendEntriesRequest request, Consumer<AppendEntriesReply> onReply);

    /** Figure 13. Test transports that never send snapshots may leave this unimplemented. */
    default void installSnapshot(int peer, InstallSnapshotRequest request, Consumer<InstallSnapshotReply> onReply) {
        throw new UnsupportedOperationException("this transport does not carry InstallSnapshot");
    }
}
