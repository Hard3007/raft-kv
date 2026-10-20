package io.github.hard3007.raftkv.common;

/**
 * Time and timers for one node. In production this is a single-threaded executor and the real
 * clock ({@link ExecutorScheduler}); in simulation it is a virtual-time event loop. Every task runs
 * on the node's one thread, so Raft state is never touched concurrently.
 */
public interface Scheduler {

    /** Current time in milliseconds (monotonic; virtual time in simulation). */
    long nowMillis();

    /** Runs {@code task} on the node's thread after {@code delayMillis}. */
    Cancellable schedule(long delayMillis, Runnable task);

    /**
     * Thread confinement: throws {@link IllegalStateException} unless called on this scheduler's
     * thread. Code that owns single-threaded state calls this at every entry point, so a call from
     * the wrong thread (for example a gRPC thread) fails loudly instead of racing silently.
     */
    void checkThread();

    interface Cancellable {
        void cancel();
    }
}
