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
 * Durable storage for Raft's persistent state (Figure 2: currentTerm, votedFor, log[]).
 *
 * <p>Each {@link #save} appends one {@link RaftStateRecord} to a {@link Wal} with
 * {@link FsyncPolicy#ALWAYS}, so the state is on stable storage when {@code save} returns.
 * One record carries the whole change (new term and vote, truncation, new entries), so a crash
 * can never persist half of it. Recovery replays the records in order.
 */
public final class RaftStorage implements AutoCloseable {

    private final Wal wal;
    private long currentTerm;
    private int votedFor;
    private final List<LogEntry> recoveredEntries = new ArrayList<>();

    private RaftStorage(FileSystem fileSystem, String name) {
        this.wal = Wal.open(fileSystem, name, FsyncPolicy.ALWAYS, (lsn, payload) -> replay(payload));
    }

    public static RaftStorage open(FileSystem fileSystem, String name) {
        return new RaftStorage(fileSystem, name);
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
        if (record.getTruncateFrom() > 0 && record.getTruncateFrom() <= recoveredEntries.size()) {
            recoveredEntries.subList((int) record.getTruncateFrom() - 1, recoveredEntries.size()).clear();
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

    /** The recovered log. Only meaningful right after {@link #open}. */
    List<LogEntry> recoveredEntries() {
        return recoveredEntries;
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

    @Override
    public void close() {
        wal.close();
    }
}
