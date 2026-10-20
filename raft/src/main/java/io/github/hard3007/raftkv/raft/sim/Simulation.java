package io.github.hard3007.raftkv.raft.sim;

import io.github.hard3007.raftkv.common.Scheduler;
import java.util.PriorityQueue;
import java.util.Random;
import java.util.function.BooleanSupplier;

/**
 * A single-threaded discrete-event simulator on virtual time.
 *
 * <p>Events run in (time, insertion order). All nodes of a simulated cluster share one
 * Simulation, so they all run on one thread, and given the same seed a run makes exactly the same
 * decisions: which messages drop, how long each is delayed, every election timeout. A failing
 * seed therefore replays exactly. Virtual time also makes tests fast: a 10-second scenario takes
 * milliseconds.
 */
public final class Simulation {

    private final Random random;
    /** The one thread allowed to run this simulation (the one that created it). */
    private final Thread owner = Thread.currentThread();
    private final PriorityQueue<Event> queue = new PriorityQueue<>();
    private long now;
    private long nextSequence;
    private Runnable afterEachEvent = () -> { };

    public Simulation(long seed) {
        this.random = new Random(seed);
    }

    /** The simulation's single source of randomness. */
    public Random random() {
        return random;
    }

    public long now() {
        return now;
    }

    /** Runs {@code check} after every event, e.g. to assert a safety invariant continuously. */
    public void setAfterEachEvent(Runnable check) {
        this.afterEachEvent = check;
    }

    public Scheduler.Cancellable schedule(long delayMillis, Runnable task) {
        Event event = new Event(now + Math.max(0, delayMillis), nextSequence++, task);
        queue.add(event);
        return event;
    }

    /** Advances virtual time by {@code millis}, running every event due in that window. */
    public void runFor(long millis) {
        runUntil(() -> false, millis);
    }

    /**
     * Runs events until {@code condition} holds (checked after each event) or {@code maxMillis}
     * of virtual time pass. Returns whether the condition held.
     */
    public boolean runUntil(BooleanSupplier condition, long maxMillis) {
        long deadline = now + maxMillis;
        if (condition.getAsBoolean()) {
            return true;
        }
        while (!queue.isEmpty() && queue.peek().time <= deadline) {
            Event event = queue.poll();
            if (event.cancelled) {
                continue;
            }
            now = event.time;
            event.task.run();
            afterEachEvent.run();
            if (condition.getAsBoolean()) {
                return true;
            }
        }
        now = deadline;
        return condition.getAsBoolean();
    }

    /**
     * A {@link Scheduler} for one node incarnation. Once {@link NodeScheduler#kill()} is called
     * (the node crashed), its pending timers never fire.
     */
    public NodeScheduler newNodeScheduler() {
        return new NodeScheduler();
    }

    public final class NodeScheduler implements Scheduler {
        private boolean alive = true;

        @Override
        public long nowMillis() {
            return now;
        }

        @Override
        public Cancellable schedule(long delayMillis, Runnable task) {
            return Simulation.this.schedule(delayMillis, () -> {
                if (alive) {
                    task.run();
                }
            });
        }

        public void kill() {
            alive = false;
        }

        @Override
        public void checkThread() {
            if (Thread.currentThread() != owner) {
                throw new IllegalStateException("thread confinement violated: simulation owned by " + owner.getName()
                        + " was used from " + Thread.currentThread().getName());
            }
        }
    }

    private static final class Event implements Comparable<Event>, Scheduler.Cancellable {
        final long time;
        final long sequence;
        final Runnable task;
        boolean cancelled;

        Event(long time, long sequence, Runnable task) {
            this.time = time;
            this.sequence = sequence;
            this.task = task;
        }

        @Override
        public void cancel() {
            cancelled = true;
        }

        @Override
        public int compareTo(Event other) {
            int byTime = Long.compare(time, other.time);
            return byTime != 0 ? byTime : Long.compare(sequence, other.sequence);
        }
    }
}
