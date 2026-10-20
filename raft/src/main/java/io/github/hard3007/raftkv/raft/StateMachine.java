package io.github.hard3007.raftkv.raft;

import io.github.hard3007.raftkv.proto.raft.LogEntry;

/** Receives committed log entries, exactly once each and strictly in index order. */
public interface StateMachine {

    /** Applies {@code entry}. Called for every entry type, including leader no-ops. */
    void apply(LogEntry entry);
}
