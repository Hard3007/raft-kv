package io.github.hard3007.raftkv.server;

import io.grpc.ForwardingServerCallListener;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Makes exceptions in gRPC handlers loud. gRPC would otherwise turn an exception thrown on its
 * own thread (for example a thread-confinement violation, if a handler forgot to post to the
 * node's executor) into an error reply that the client simply retries. This counts and logs it.
 */
final class FailureCountingInterceptor implements ServerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(FailureCountingInterceptor.class);

    private final int nodeId;
    private final AtomicLong failures = new AtomicLong();

    FailureCountingInterceptor(int nodeId) {
        this.nodeId = nodeId;
    }

    long failures() {
        return failures.get();
    }

    @Override
    public <Q, R> ServerCall.Listener<Q> interceptCall(ServerCall<Q, R> call, Metadata headers,
            ServerCallHandler<Q, R> next) {
        return new ForwardingServerCallListener.SimpleForwardingServerCallListener<>(next.startCall(call, headers)) {
            @Override
            public void onHalfClose() {
                try {
                    super.onHalfClose(); // unary handlers run here
                } catch (RuntimeException e) {
                    failures.incrementAndGet();
                    log.error("node={} event=grpc_handler_failed method={}", nodeId,
                            call.getMethodDescriptor().getFullMethodName(), e);
                    throw e;
                }
            }
        };
    }
}
