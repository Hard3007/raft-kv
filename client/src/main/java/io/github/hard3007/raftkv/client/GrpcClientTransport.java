package io.github.hard3007.raftkv.client;

import io.github.hard3007.raftkv.proto.kv.DeleteReply;
import io.github.hard3007.raftkv.proto.kv.DeleteRequest;
import io.github.hard3007.raftkv.proto.kv.GetReply;
import io.github.hard3007.raftkv.proto.kv.GetRequest;
import io.github.hard3007.raftkv.proto.kv.KvServiceGrpc;
import io.github.hard3007.raftkv.proto.kv.PutReply;
import io.github.hard3007.raftkv.proto.kv.PutRequest;
import io.grpc.Grpc;
import io.grpc.InsecureChannelCredentials;
import io.grpc.ManagedChannel;
import io.grpc.stub.StreamObserver;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * {@link ClientTransport} over gRPC: async stubs, a deadline on every call, one reused channel per
 * server. Replies are posted to {@code clientThread}, because {@link KvClient} is single-threaded.
 * A failed call (deadline, server down) produces no callback; {@link KvClient}'s attempt timeout
 * moves on to another server.
 */
public final class GrpcClientTransport implements ClientTransport, AutoCloseable {

    private final Map<Integer, String> servers;
    private final Executor clientThread;
    private final long deadlineMs;
    private final Map<Integer, ManagedChannel> channels = new ConcurrentHashMap<>();

    public GrpcClientTransport(Map<Integer, String> servers, Executor clientThread, long deadlineMs) {
        this.servers = Map.copyOf(servers);
        this.clientThread = clientThread;
        this.deadlineMs = deadlineMs;
    }

    private KvServiceGrpc.KvServiceStub stub(int server) {
        ManagedChannel channel = channels.computeIfAbsent(server,
                s -> Grpc.newChannelBuilder(servers.get(s), InsecureChannelCredentials.create()).build());
        return KvServiceGrpc.newStub(channel).withDeadlineAfter(deadlineMs, TimeUnit.MILLISECONDS);
    }

    @Override
    public void get(int server, GetRequest request, Consumer<GetReply> onReply) {
        stub(server).get(request, observer(onReply));
    }

    @Override
    public void put(int server, PutRequest request, Consumer<PutReply> onReply) {
        stub(server).put(request, observer(onReply));
    }

    @Override
    public void delete(int server, DeleteRequest request, Consumer<DeleteReply> onReply) {
        stub(server).delete(request, observer(onReply));
    }

    private <R> StreamObserver<R> observer(Consumer<R> onReply) {
        return new StreamObserver<>() {
            @Override
            public void onNext(R reply) {
                clientThread.execute(() -> onReply.accept(reply));
            }

            @Override
            public void onError(Throwable error) {
                // No callback: the client's attempt timeout handles it.
            }

            @Override
            public void onCompleted() {
            }
        };
    }

    @Override
    public void close() {
        channels.values().forEach(ManagedChannel::shutdownNow);
    }
}
