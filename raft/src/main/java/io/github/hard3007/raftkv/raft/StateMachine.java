package io.github.hard3007.raftkv.raft;

import io.github.hard3007.raftkv.proto.raft.LogEntry;

/**
 * Receives committed log entries, exactly once each and strictly in index order.
 *
 * <p>The snapshot methods are needed only when snapshots are enabled
 * ({@link RaftConfig#snapshotThreshold()} &gt; 0, or a leader sends InstallSnapshot). They run on
 * the node's thread, like {@link #apply}.
 */
public interface StateMachine {

    /** Applies {@code entry}. Called for every entry type, including leader no-ops. */
    void apply(LogEntry entry);

    /** Index of the last entry reflected in the state, as recovered at startup (0 if in memory only). */
    default long appliedIndex() {
        return 0;
    }

    /** The whole state as of the last applied entry. */
    default byte[] takeSnapshot() {
        throw new UnsupportedOperationException("this state machine does not support snapshots");
    }

    /**
     * Replaces the whole state with {@code snapshot}, taken after applying entry {@code index}.
     * Must be durable on return: afterwards {@link #appliedIndex()} is {@code index}, even after a crash.
     */
    default void restoreSnapshot(byte[] snapshot, long index) {
        throw new UnsupportedOperationException("this state machine does not support snapshots");
    }
}
