package io.github.hard3007.raftkv.chaos;

import io.github.hard3007.raftkv.client.BlockingKvClient;
import io.github.hard3007.raftkv.client.KvResult;
import io.github.hard3007.raftkv.linearizability.History;
import io.github.hard3007.raftkv.linearizability.Operation;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.Random;

/**
 * One client thread's workload against a real cluster (in-process gRPC or Docker): random gets,
 * puts and deletes on a few shared keys, recorded in a {@link History} with the same rules as the
 * simulated chaos runs. The history is invoked just before the call and completed when the call
 * returns; Unknown writes stay pending, unanswered reads are left out.
 */
public final class BlockingWorkload implements Runnable {

    private final int client;
    private final BlockingKvClient kv;
    private final History history;
    private final Random random;
    private final int maxOperations;
    private final int keys;
    private final int maxThinkMs;
    private volatile boolean stopRequested;
    private int completed;
    private int pendingWrites;
    private int discardedReads;

    public BlockingWorkload(int client, BlockingKvClient kv, History history, Random random, int maxOperations,
            int keys, int maxThinkMs) {
        this.client = client;
        this.kv = kv;
        this.history = history;
        this.random = random;
        this.maxOperations = maxOperations;
        this.keys = keys;
        this.maxThinkMs = maxThinkMs;
    }

    /** Asks the workload to stop after its current operation. */
    public void stop() {
        stopRequested = true;
    }

    @Override
    public void run() {
        try {
            for (int n = 0; n < maxOperations && !stopRequested; n++) {
                doOne(n);
                if (maxThinkMs > 0) {
                    Thread.sleep(random.nextInt(maxThinkMs + 1)); // think time spreads load over the fault window
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void doOne(int n) throws InterruptedException {
        String key = "k" + random.nextInt(keys);
        int choice = random.nextInt(10);
        if (choice < 5) {
            int id = history.invoke(client, Operation.Kind.GET, key, null);
            KvResult<Optional<byte[]>> result = kv.get(bytes(key));
            if (result instanceof KvResult.Ok<Optional<byte[]>> ok) {
                history.complete(id, new Operation.Result.Read(ok.value().map(BlockingWorkload::string).orElse(null)));
                completed++;
            } else {
                history.discard(id);
                discardedReads++;
            }
        } else if (choice < 9) {
            String value = "c" + client + "-" + n;
            int id = history.invoke(client, Operation.Kind.PUT, key, value);
            endWrite(id, kv.put(bytes(key), bytes(value)), new Operation.Result.Written());
        } else {
            int id = history.invoke(client, Operation.Kind.DELETE, key, null);
            KvResult<Boolean> result = kv.delete(bytes(key));
            endWrite(id, result, result instanceof KvResult.Ok<Boolean> ok ? new Operation.Result.Deleted(ok.value()) : null);
        }
    }

    private void endWrite(int id, KvResult<?> result, Operation.Result observed) {
        if (result instanceof KvResult.Ok<?>) {
            history.complete(id, observed);
            completed++;
        } else {
            history.leavePending(id); // Unknown: may take effect at any later point, or never
            pendingWrites++;
        }
    }

    public int completed() {
        return completed;
    }

    public int pendingWrites() {
        return pendingWrites;
    }

    public int discardedReads() {
        return discardedReads;
    }

    private static byte[] bytes(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    private static String string(byte[] b) {
        return new String(b, StandardCharsets.UTF_8);
    }
}
