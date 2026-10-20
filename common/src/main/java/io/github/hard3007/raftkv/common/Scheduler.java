package io.github.hard3007.raftkv.common;

/**
 * Time and timers for one node. In production this is a single-threaded executor and the real
 * clock; in simulation it is a virtual-time event loop. Every task runs on the node's one thread,
 * so Raft state is never touched concurrently.
 */
public interface Scheduler {

    /** Current time in milliseconds (virtual time in simulation). */
    long nowMillis();

    /** Runs {@code task} on the node's thread after {@code delayMillis}. */
    Cancellable schedule(long delayMillis, Runnable task);

    interface Cancellable {
        void cancel();
    }
}
