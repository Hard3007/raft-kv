package io.github.hard3007.raftkv.server;

import io.github.hard3007.raftkv.common.ExecutorScheduler;
import io.github.hard3007.raftkv.proto.raft.AppendEntriesReply;
import io.github.hard3007.raftkv.proto.raft.AppendEntriesRequest;
import io.github.hard3007.raftkv.proto.raft.InstallSnapshotReply;
import io.github.hard3007.raftkv.proto.raft.InstallSnapshotRequest;
import io.github.hard3007.raftkv.proto.raft.RaftServiceGrpc;
import io.github.hard3007.raftkv.proto.raft.RequestVoteReply;
import io.github.hard3007.raftkv.proto.raft.RequestVoteRequest;
import io.github.hard3007.raftkv.raft.RaftNode;
import io.grpc.stub.StreamObserver;
import java.util.Set;
import java.util.function.Supplier;

/**
 * The server side of Raft RPCs. gRPC calls these on its own threads; each request is posted to
 * the node's executor, handled there (including persisting before replying), and the reply is
 * sent from the node's thread.
 */
final class RaftGrpcService extends RaftServiceGrpc.RaftServiceImplBase {

    private final RaftNode node;
    private final ExecutorScheduler nodeThread;
    private final Supplier<Set<Integer>> blockedPeers;

    RaftGrpcService(RaftNode node, ExecutorScheduler nodeThread, Supplier<Set<Integer>> blockedPeers) {
        this.node = node;
        this.nodeThread = nodeThread;
        this.blockedPeers = blockedPeers;
    }

    @Override
    public void requestVote(RequestVoteRequest request, StreamObserver<RequestVoteReply> responses) {
        if (blockedPeers.get().contains(request.getCandidateId())) {
            return; // test partition: never answer (the caller's deadline expires)
        }
        nodeThread.execute(() -> {
            responses.onNext(node.handleRequestVote(request));
            responses.onCompleted();
        });
    }

    @Override
    public void appendEntries(AppendEntriesRequest request, StreamObserver<AppendEntriesReply> responses) {
        if (blockedPeers.get().contains(request.getLeaderId())) {
            return;
        }
        nodeThread.execute(() -> {
            responses.onNext(node.handleAppendEntries(request));
            responses.onCompleted();
        });
    }

    @Override
    public void installSnapshot(InstallSnapshotRequest request, StreamObserver<InstallSnapshotReply> responses) {
        if (blockedPeers.get().contains(request.getLeaderId())) {
            return;
        }
        nodeThread.execute(() -> {
            responses.onNext(node.handleInstallSnapshot(request));
            responses.onCompleted();
        });
    }
}
