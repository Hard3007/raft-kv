package io.github.hard3007.raftkv.client;

import io.github.hard3007.raftkv.common.ExecutorScheduler;
import java.util.ArrayList;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

/**
 * A blocking facade over {@link KvClient} for ordinary threads (tests, benchmarks, tools). The
 * async client runs on its own thread; each call here posts the operation there and waits for
 * the result. Like {@link KvClient}, one operation at a time.
 */
public final class BlockingKvClient implements AutoCloseable {

    private final ExecutorScheduler clientThread;
    private final GrpcClientTransport transport;
    private final KvClient client;

    /** @param servers every node's address ("host:port") by node id */
    public BlockingKvClient(long clientId, Map<Integer, String> servers, KvClient.Options options) {
        this.clientThread = new ExecutorScheduler("kv-client-" + Long.toHexString(clientId));
        this.transport = new GrpcClientTransport(servers, clientThread, options.attemptTimeoutMs());
        this.client = new KvClient(clientId, new ArrayList<>(new TreeMap<>(servers).keySet()), transport, clientThread,
                options);
    }

    public KvResult<Optional<byte[]>> get(byte[] key) throws InterruptedException {
        return await(done -> client.get(key, done));
    }

    public KvResult<Void> put(byte[] key, byte[] value) throws InterruptedException {
        return await(done -> client.put(key, value, done));
    }

    public KvResult<Boolean> delete(byte[] key) throws InterruptedException {
        return await(done -> client.delete(key, done));
    }

    private <T> KvResult<T> await(Consumer<Consumer<KvResult<T>>> operation) throws InterruptedException {
        CompletableFuture<KvResult<T>> result = new CompletableFuture<>();
        clientThread.execute(() -> operation.accept(result::complete));
        try {
            return result.get(); // KvClient always completes (it gives up after giveUpAfterMs)
        } catch (ExecutionException e) {
            throw new IllegalStateException("client operation failed", e.getCause());
        }
    }

    @Override
    public void close() {
        transport.close();
        clientThread.close();
    }
}
