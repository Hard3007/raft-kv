package io.github.hard3007.raftkv.raft;

import io.github.hard3007.raftkv.proto.raft.LogEntry;
import java.util.ArrayList;
import java.util.List;

/**
 * The in-memory Raft log. Indices start at 1.
 *
 * <p>After compaction the log starts after a snapshot: entries up to {@link #snapshotIndex()} are
 * gone (the snapshot covers them), and only the snapshot's last index and term are remembered,
 * which is all that AppendEntries' consistency check needs at that point. A new log has
 * snapshotIndex = 0 and snapshotTerm = 0, so "prevLogIndex = 0" always matches. Durability is
 * handled by {@link RaftStorage}.
 */
final class RaftLog {

    private long snapshotIndex;
    private long snapshotTerm;
    /** entries.get(i) has index snapshotIndex + i + 1. */
    private final List<LogEntry> entries = new ArrayList<>();

    RaftLog(long snapshotIndex, long snapshotTerm, List<LogEntry> recovered) {
        this.snapshotIndex = snapshotIndex;
        this.snapshotTerm = snapshotTerm;
        entries.addAll(recovered);
    }

    long snapshotIndex() {
        return snapshotIndex;
    }

    long snapshotTerm() {
        return snapshotTerm;
    }

    long lastIndex() {
        return snapshotIndex + entries.size();
    }

    long lastTerm() {
        return termAt(lastIndex());
    }

    /** Term of the entry at {@code index}, which must not be below {@link #snapshotIndex()}. */
    long termAt(long index) {
        if (index == snapshotIndex) {
            return snapshotTerm;
        }
        return get(index).getTerm();
    }

    LogEntry get(long index) {
        if (index <= snapshotIndex || index > lastIndex()) {
            throw new IndexOutOfBoundsException("log index " + index + " is outside (" + snapshotIndex + ", "
                    + lastIndex() + "]");
        }
        return entries.get((int) (index - snapshotIndex - 1));
    }

    /** True if the log holds an entry (or the snapshot point) at {@code index} with {@code term}. */
    boolean hasEntry(long index, long term) {
        return index >= snapshotIndex && index <= lastIndex() && termAt(index) == term;
    }

    /** Entries from {@code from} through {@code toInclusive}; {@code from} must be after the snapshot. */
    List<LogEntry> slice(long from, long toInclusive) {
        if (from > toInclusive) {
            return List.of();
        }
        return List.copyOf(entries.subList((int) (from - snapshotIndex - 1), (int) (toInclusive - snapshotIndex)));
    }

    /** All entries after the snapshot point. */
    List<LogEntry> entriesAfterSnapshot() {
        return List.copyOf(entries);
    }

    void append(LogEntry entry) {
        if (entry.getIndex() != lastIndex() + 1) {
            throw new IllegalArgumentException("appending index " + entry.getIndex() + " after " + lastIndex());
        }
        entries.add(entry);
    }

    /** Deletes the entry at {@code index} and everything after it. */
    void truncateFrom(long index) {
        entries.subList((int) (index - snapshotIndex - 1), entries.size()).clear();
    }

    /** Drops every entry up to and including {@code index}; the log then starts after it. */
    void compactThrough(long index) {
        long term = termAt(index);
        entries.subList(0, (int) (index - snapshotIndex)).clear();
        snapshotIndex = index;
        snapshotTerm = term;
    }

    /** Discards the whole log; it now starts after a snapshot at ({@code index}, {@code term}). */
    void resetTo(long index, long term) {
        entries.clear();
        snapshotIndex = index;
        snapshotTerm = term;
    }

    /**
     * First index holding {@code term}, scanning back from {@code index} (whose term must be
     * {@code term}). Stops at the first entry after the snapshot.
     */
    long firstIndexOfTerm(long term, long index) {
        while (index - 1 > snapshotIndex && termAt(index - 1) == term) {
            index--;
        }
        return index;
    }

    /** Last index holding {@code term} (the snapshot point counts), or 0 if there is none. */
    long lastIndexOfTerm(long term) {
        for (long i = lastIndex(); i >= snapshotIndex; i--) {
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
