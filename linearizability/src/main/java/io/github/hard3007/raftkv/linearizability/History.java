package io.github.hard3007.raftkv.linearizability;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.LongSupplier;

/**
 * Records a history of client operations as they happen.
 *
 * <p>Call {@link #invoke} just before the client sends a request and {@link #complete} when it
 * accepts the reply. {@link #leavePending} marks a write whose outcome is unknown, so it may take
 * effect at any later point. {@link #discard} removes a read with no answer (reads have no side
 * effects).
 *
 * <p>Timestamps come from one of two clocks:
 * <ul>
 *   <li>{@link #forSimulation}: virtual milliseconds combined with a counter, as
 *       {@code millis * 1_000_000 + counter}. The simulation is single-threaded, so this gives every
 *       event a unique time that matches the true order of events, even within one millisecond.
 *   <li>{@link #withNanoTime}: {@link System#nanoTime()}, for real threads and real networks. Equal
 *       readings are possible and are treated as concurrent by the checker.
 * </ul>
 * Thread-safe: client threads may record concurrently.
 */
public final class History {

    private record Invocation(int client, Operation.Kind kind, String key, String argument, long time) {
    }

    private final LongSupplier clock;
    private final Map<Integer, Invocation> open = new HashMap<>();
    private final List<Operation> operations = new ArrayList<>();
    private int nextId;

    private History(LongSupplier clock) {
        this.clock = clock;
    }

    /** A history on a simulation's virtual clock (milliseconds). */
    public static History forSimulation(LongSupplier virtualMillis) {
        long[] counter = {0};
        return new History(() -> virtualMillis.getAsLong() * 1_000_000 + (counter[0]++ % 1_000_000));
    }

    /** A history on {@link System#nanoTime()}, for real threads. */
    public static History withNanoTime() {
        return new History(System::nanoTime);
    }

    private long now() {
        return clock.getAsLong();
    }

    /** Records an invocation; returns the operation id to complete it with. */
    public synchronized int invoke(int client, Operation.Kind kind, String key, String argument) {
        int id = nextId++;
        open.put(id, new Invocation(client, kind, key, argument, now()));
        return id;
    }

    public synchronized void complete(int id, Operation.Result result) {
        Invocation call = take(id);
        operations.add(new Operation(id, call.client(), call.kind(), call.key(), call.argument(), call.time(), now(),
                result));
    }

    /** A write whose outcome is unknown: it may take effect at any point after its invocation, or never. */
    public synchronized void leavePending(int id) {
        Invocation call = take(id);
        if (call.kind() == Operation.Kind.GET) {
            throw new IllegalArgumentException("reads are discarded, not left pending");
        }
        operations.add(new Operation(id, call.client(), call.kind(), call.key(), call.argument(), call.time(),
                Operation.PENDING, new Operation.Result.Pending()));
    }

    /** Drops an operation entirely, e.g. a read that never got an answer. */
    public synchronized void discard(int id) {
        take(id);
    }

    private Invocation take(int id) {
        Invocation call = open.remove(id);
        if (call == null) {
            throw new IllegalArgumentException("unknown or already finished operation " + id);
        }
        return call;
    }

    /**
     * The recorded operations. Operations still open (invoked, never finished) are treated like
     * unknown outcomes: writes become pending, reads are dropped.
     */
    public synchronized List<Operation> operations() {
        List<Operation> all = new ArrayList<>(operations);
        open.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            Invocation call = entry.getValue();
            if (call.kind() != Operation.Kind.GET) {
                all.add(new Operation(entry.getKey(), call.client(), call.kind(), call.key(), call.argument(),
                        call.time(), Operation.PENDING, new Operation.Result.Pending()));
            }
        });
        return all;
    }
}
