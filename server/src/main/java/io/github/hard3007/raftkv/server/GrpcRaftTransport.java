package io.github.hard3007.raftkv.server;

import io.github.hard3007.raftkv.proto.raft.AppendEntriesReply;
import io.github.hard3007.raftkv.proto.raft.AppendEntriesRequest;
import io.github.hard3007.raftkv.proto.raft.RaftServiceGrpc;
import io.github.hard3007.raftkv.proto.raft.RequestVoteReply;
import io.github.hard3007.raftkv.proto.raft.RequestVoteRequest;
import io.github.hard3007.raftkv.raft.Transport;
import io.grpc.Grpc;
import io.grpc.InsecureChannelCredentials;
import io.grpc.ManagedChannel;
import io.grpc.stub.StreamObserver;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Raft RPCs over gRPC.
 * <ul>
 *   <li><b>Async stubs only.</b> A send returns at once; the node's thread never blocks on the network.
 *   <li><b>A deadline on every RPC</b> ({@code rpcTimeoutMs}). A peer that is down or partitioned
 *       just costs a failed call, never a stuck one. Failures are dropped: Raft retries by itself
 *       (heartbeats resend, candidates time out).
 *   <li><b>One channel per peer</b>, created once and reused for every call (gRPC multiplexes calls
 *       over its HTTP/2 connection and reconnects by itself).
 *   <li><b>Replies are posted to the node's executor</b> ({@code nodeThread}), never handled on
 *       gRPC's threads.
 * </ul>
 * For tests, {@link #setBlockedPeers} simulates a partition and {@link #dropConnections} kills
 * the live connections (in-flight calls fail, the next call reconnects).
 */
public final class GrpcRaftTransport implements Transport, AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(GrpcRaftTransport.class);

    private final int self;
    private final Map<Integer, String> addresses;
    private final Executor nodeThread;
    private final long rpcTimeoutMs;
    private final Map<Integer, ManagedChannel> channels = new ConcurrentHashMap<>();
    private volatile Set<Integer> blockedPeers = Set.of();

    public GrpcRaftTransport(int self, Map<Integer, String> addresses, Executor nodeThread, long rpcTimeoutMs) {
        this.self = self;
        this.addresses = Map.copyOf(addresses);
        this.nodeThread = nodeThread;
        this.rpcTimeoutMs = rpcTimeoutMs;
    }

    private ManagedChannel channel(int peer) {
        return channels.computeIfAbsent(peer,
                p -> Grpc.newChannelBuilder(addresses.get(p), InsecureChannelCredentials.create()).build());
    }

    private RaftServiceGrpc.RaftServiceStub stub(int peer) {
        // A deadline is per call, so it goes on a fresh (cheap) stub over the shared channel.
        return RaftServiceGrpc.newStub(channel(peer)).withDeadlineAfter(rpcTimeoutMs, TimeUnit.MILLISECONDS);
    }

    @Override
    public void requestVote(int peer, RequestVoteRequest request, Consumer<RequestVoteReply> onReply) {
        if (!blockedPeers.contains(peer)) {
            stub(peer).requestVote(request, observer(peer, "RequestVote", onReply));
        }
    }

    @Override
    public void appendEntries(int peer, AppendEntriesRequest request, Consumer<AppendEntriesReply> onReply) {
        if (!blockedPeers.contains(peer)) {
            stub(peer).appendEntries(request, observer(peer, "AppendEntries", onReply));
        }
    }

    private <R> StreamObserver<R> observer(int peer, String rpc, Consumer<R> onReply) {
        return new StreamObserver<>() {
            @Override
            public void onNext(R reply) {
                if (!blockedPeers.contains(peer)) {
                    nodeThread.execute(() -> onReply.accept(reply)); // hand over to the node's thread
                }
            }

            @Override
            public void onError(Throwable error) {
                log.debug("node={} event=rpc_failed rpc={} peer={} error={}", self, rpc, peer, error.toString());
            }

            @Override
            public void onCompleted() {
            }
        };
    }

    /** Test hook: messages to and replies from these peers are dropped (one side of a partition). */
    public void setBlockedPeers(Set<Integer> peers) {
        this.blockedPeers = Set.copyOf(peers);
    }

    Set<Integer> blockedPeers() {
        return blockedPeers;
    }

    /** Test hook: tears down every connection; in-flight calls fail and the next call reconnects. */
    public void dropConnections() {
        channels.values().forEach(ManagedChannel::shutdownNow);
        channels.clear();
    }

    @Override
    public void close() {
        dropConnections();
    }
}
