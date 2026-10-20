package io.github.hard3007.raftkv.raft;

import com.google.protobuf.InvalidProtocolBufferException;
import io.github.hard3007.raftkv.proto.raft.LogEntry;
import io.github.hard3007.raftkv.proto.raft.RaftStateRecord;
import io.github.hard3007.raftkv.storage.FileSystem;
import io.github.hard3007.raftkv.storage.FsyncPolicy;
import io.github.hard3007.raftkv.storage.StorageCorruptionException;
import io.github.hard3007.raftkv.storage.Wal;
import java.util.ArrayList;
import java.util.List;

/**
 * Durable storage for Raft's persistent state (Figure 2: currentTerm, votedFor, log[]) and its
 * latest snapshot (Figure 13).
 *
 * <p>Each {@link #save} appends one {@link RaftStateRecord} to a {@link Wal} with
 * {@link FsyncPolicy#ALWAYS}, so the state is on stable storage when {@code save} returns.
 * One record carries the whole change (new term and vote, truncation, new entries), so a crash
 * can never persist half of it. Recovery replays the records in order.
 *
 * <p><b>Compaction.</b> {@link #rewrite} atomically replaces the whole WAL (temporary file, fsync,
 * rename) with a single record: the compaction point, the term and vote, and the entries after
 * it. The WAL therefore stops growing. Callers rewrite only after the snapshot covering the
 * dropped prefix is durable ({@link SnapshotStore#save}).
 */
public final class RaftStorage implements AutoCloseable {

    private final Wal wal;
    private final SnapshotStore snapshots;
    private long currentTerm;
    private int votedFor;
    private long snapshotIndex;
    private long snapshotTerm;
    /** Recovered entries after {@code snapshotIndex}, in index order. */
    private final List<LogEntry> recoveredEntries = new ArrayList<>();

    private RaftStorage(FileSystem fileSystem, String name) {
        this.wal = Wal.open(fileSystem, name, FsyncPolicy.ALWAYS, (lsn, payload) -> replay(payload));
        this.snapshots = SnapshotStore.open(fileSystem, snapshotName(name));
    }

    /** Opens {@code name} (the log) and its snapshot ({@code name} with ".wal" replaced by ".snapshot"). */
    public static RaftStorage open(FileSystem fileSystem, String name) {
        return new RaftStorage(fileSystem, name);
    }

    private static String snapshotName(String walName) {
        return (walName.endsWith(".wal") ? walName.substring(0, walName.length() - 4) : walName) + ".snapshot";
    }

    private void replay(byte[] payload) {
        RaftStateRecord record;
        try {
            record = RaftStateRecord.parseFrom(payload);
        } catch (InvalidProtocolBufferException e) {
            // The WAL's CRC already passed, so this is a bug or a format mismatch, not a torn write.
            throw new StorageCorruptionException("unreadable Raft state record: " + e.getMessage());
        }
        currentTerm = record.getCurrentTerm();
        votedFor = record.getVotedFor();
        if (record.getCompactedThrough() > snapshotIndex) {
            long through = record.getCompactedThrough();
            recoveredEntries.removeIf(entry -> entry.getIndex() <= through);
            snapshotIndex = through;
            snapshotTerm = record.getCompactedTerm();
        }
        if (record.getTruncateFrom() > 0) {
            long from = record.getTruncateFrom();
            recoveredEntries.removeIf(entry -> entry.getIndex() >= from);
        }
        recoveredEntries.addAll(record.getEntriesList());
    }

    /** currentTerm as of the last save before the crash (0 for a new node). */
    long currentTerm() {
        return currentTerm;
    }

    /** votedFor as of the last save (0 = none). */
    int votedFor() {
        return votedFor;
    }

    /** The recovered log starts after this index (0 if never compacted). */
    long snapshotIndex() {
        return snapshotIndex;
    }

    long snapshotTerm() {
        return snapshotTerm;
    }

    /** The recovered entries after {@link #snapshotIndex()}. Only meaningful right after {@link #open}. */
    List<LogEntry> recoveredEntries() {
        return recoveredEntries;
    }

    public SnapshotStore snapshots() {
        return snapshots;
    }

    /**
     * Durably records the new term and vote, then truncation of every entry at or after
     * {@code truncateFrom} (0 = none), then {@code appended}. Durable when this returns.
     */
    void save(long term, int vote, long truncateFrom, List<LogEntry> appended) {
        RaftStateRecord record = RaftStateRecord.newBuilder()
                .setCurrentTerm(term)
                .setVotedFor(vote)
                .setTruncateFrom(truncateFrom)
                .addAllEntries(appended)
                .build();
        wal.append(record.toByteArray());
    }

    /**
     * Atomically replaces the whole stored log: it now starts after {@code compactedThrough}
     * (term {@code compactedTerm}) and holds exactly {@code entries}. Durable when this returns.
     */
    void rewrite(long compactedThrough, long compactedTerm, long term, int vote, List<LogEntry> entries) {
        RaftStateRecord record = RaftStateRecord.newBuilder()
                .setCurrentTerm(term)
                .setVotedFor(vote)
                .setCompactedThrough(compactedThrough)
                .setCompactedTerm(compactedTerm)
                .addAllEntries(entries)
                .build();
        wal.rewrite(List.of(record.toByteArray()));
    }

    /** Size of the log file in bytes (it shrinks on {@link #rewrite}). */
    public long walBytes() {
        return wal.sizeBytes();
    }

    @Override
    public void close() {
        wal.close();
    }
}
