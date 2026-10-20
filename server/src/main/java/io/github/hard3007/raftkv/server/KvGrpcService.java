package io.github.hard3007.raftkv.server;

import io.github.hard3007.raftkv.common.ExecutorScheduler;
import io.github.hard3007.raftkv.kv.KvService;
import io.github.hard3007.raftkv.proto.kv.DeleteReply;
import io.github.hard3007.raftkv.proto.kv.DeleteRequest;
import io.github.hard3007.raftkv.proto.kv.GetReply;
import io.github.hard3007.raftkv.proto.kv.GetRequest;
import io.github.hard3007.raftkv.proto.kv.KvServiceGrpc;
import io.github.hard3007.raftkv.proto.kv.PutReply;
import io.github.hard3007.raftkv.proto.kv.PutRequest;
import io.grpc.stub.StreamObserver;
import java.util.function.Consumer;

/** Client requests over gRPC. Each one is posted to the node's executor; the reply is sent from there. */
final class KvGrpcService extends KvServiceGrpc.KvServiceImplBase {

    private final KvService service;
    private final ExecutorScheduler nodeThread;

    KvGrpcService(KvService service, ExecutorScheduler nodeThread) {
        this.service = service;
        this.nodeThread = nodeThread;
    }

    @Override
    public void get(GetRequest request, StreamObserver<GetReply> responses) {
        nodeThread.execute(() -> service.get(request, reply(responses)));
    }

    @Override
    public void put(PutRequest request, StreamObserver<PutReply> responses) {
        nodeThread.execute(() -> service.put(request, reply(responses)));
    }

    @Override
    public void delete(DeleteRequest request, StreamObserver<DeleteReply> responses) {
        nodeThread.execute(() -> service.delete(request, reply(responses)));
    }

    private static <R> Consumer<R> reply(StreamObserver<R> responses) {
        return reply -> {
            responses.onNext(reply);
            responses.onCompleted();
        };
    }
}
