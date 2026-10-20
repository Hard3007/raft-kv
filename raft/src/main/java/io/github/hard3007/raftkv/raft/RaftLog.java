package io.github.hard3007.raftkv.raft;

import io.github.hard3007.raftkv.proto.raft.LogEntry;
import java.util.ArrayList;
import java.util.List;

/**
 * The in-memory Raft log. Indices start at 1. Index 0 is a sentinel with term 0, so
 * "prevLogIndex = 0" always matches. Durability is handled by {@link RaftStorage}.
 */
final class RaftLog {

    /** entries.get(i) has index i + 1. */
    private final List<LogEntry> entries = new ArrayList<>();

    RaftLog(List<LogEntry> recovered) {
        entries.addAll(recovered);
    }

    long lastIndex() {
        return entries.size();
    }

    long lastTerm() {
        return termAt(lastIndex());
    }

    /** Term of the entry at {@code index}; 0 for the sentinel index 0. */
    long termAt(long index) {
        return index == 0 ? 0 : get(index).getTerm();
    }

    LogEntry get(long index) {
        if (index < 1 || index > lastIndex()) {
            throw new IndexOutOfBoundsException("log index " + index + ", last is " + lastIndex());
        }
        return entries.get((int) (index - 1));
    }

    /** Entries from {@code from} through {@code toInclusive}. */
    List<LogEntry> slice(long from, long toInclusive) {
        if (from > toInclusive) {
            return List.of();
        }
        return List.copyOf(entries.subList((int) (from - 1), (int) toInclusive));
    }

    void append(LogEntry entry) {
        if (entry.getIndex() != lastIndex() + 1) {
            throw new IllegalArgumentException("appending index " + entry.getIndex() + " after " + lastIndex());
        }
        entries.add(entry);
    }

    /** Deletes the entry at {@code index} and everything after it. */
    void truncateFrom(long index) {
        entries.subList((int) (index - 1), entries.size()).clear();
    }

    /** First index holding {@code term}, scanning back from {@code index} (whose term must be {@code term}). */
    long firstIndexOfTerm(long term, long index) {
        while (index > 1 && termAt(index - 1) == term) {
            index--;
        }
        return index;
    }

    /** Last index holding {@code term}, or 0 if the log has no entry from that term. */
    long lastIndexOfTerm(long term) {
        for (long i = lastIndex(); i >= 1; i--) {
            long t = termAt(i);
            if (t == term) {
                return i;
            }
            if (t < term) {
                return 0; // terms only increase along the log
            }
        }
        return 0;
    }
}
