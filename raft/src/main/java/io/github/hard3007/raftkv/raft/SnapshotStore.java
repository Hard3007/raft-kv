package io.github.hard3007.raftkv.raft;

import io.github.hard3007.raftkv.storage.FileSystem;
import io.github.hard3007.raftkv.storage.StorageCorruptionException;
import io.github.hard3007.raftkv.storage.StorageFile;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Optional;
import java.util.zip.CRC32;

/**
 * The latest snapshot, stored durably in one file.
 *
 * <pre>
 *   magic (4) | version (4) | lastIncludedIndex (8) | lastIncludedTerm (8) | data length (4) |
 *   crc32 of everything before it and of the data (4) | data
 * </pre>
 *
 * <p>The file is only ever replaced atomically. {@link #save} writes {@code <name>.tmp}, fsyncs it
 * and renames it over {@code <name>}. A snapshot received with InstallSnapshot (Figure 13) is
 * written chunk by chunk to {@code <name>.recv}, fsynced, read back and checked, and only then
 * renamed. So after any crash, {@code <name>} is either the previous snapshot or the new one, and
 * the Raft log may only drop a prefix once {@code save} has returned.
 *
 * <p>The latest snapshot is also kept in memory as the file's exact bytes, which is what a leader
 * sends in InstallSnapshot chunks. Snapshots here are small (the KV state, not the log). Not
 * thread-safe: used only on the node's thread.
 */
public final class SnapshotStore {

    private static final int MAGIC = 0x524B5653; // "RKVS"
    private static final int VERSION = 1;
    private static final int HEADER_SIZE = 32;

    /** A snapshot of the state machine after applying every entry up to {@code index}. */
    public record Snapshot(long index, long term, byte[] data) {
    }

    private final FileSystem fileSystem;
    private final String name;
    private Snapshot latest;
    private byte[] latestFile;

    // InstallSnapshot reception in progress (Figure 13 steps 2-4).
    private StorageFile receiving;
    private long receivingIndex;
    private long receivedBytes;

    private SnapshotStore(FileSystem fileSystem, String name) {
        this.fileSystem = fileSystem;
        this.name = name;
    }

    /** Opens the store and loads the existing snapshot, if any. Leftover temporary files are deleted. */
    public static SnapshotStore open(FileSystem fileSystem, String name) {
        SnapshotStore store = new SnapshotStore(fileSystem, name);
        fileSystem.delete(name + ".tmp");
        fileSystem.delete(name + ".recv");
        if (fileSystem.exists(name)) {
            byte[] file = readAll(fileSystem, name);
            store.latest = decode(file).orElseThrow(() ->
                    // It was fsynced before being renamed into place, so this is media corruption.
                    new StorageCorruptionException("snapshot " + name + " is damaged"));
            store.latestFile = file;
        }
        return store;
    }

    public Optional<Snapshot> latest() {
        return Optional.ofNullable(latest);
    }

    /** The latest snapshot's file bytes, as sent in InstallSnapshot chunks. */
    byte[] latestFileBytes() {
        return latestFile;
    }

    /** Durably replaces the snapshot: written to a temporary file, fsynced, then renamed into place. */
    public void save(long index, long term, byte[] data) {
        byte[] file = encode(index, term, data);
        String temporary = name + ".tmp";
        fileSystem.delete(temporary);
        try (StorageFile tmp = fileSystem.open(temporary)) {
            tmp.write(0, file, 0, file.length);
            tmp.force();
        }
        fileSystem.replace(temporary, name);
        latest = new Snapshot(index, term, data);
        latestFile = file;
    }

    // ------------------------------------------------------------------ InstallSnapshot reception

    /** Figure 13 step 2: create a new snapshot file (the first chunk, offset 0, has arrived). */
    void beginReceiving(long index) {
        abortReceiving();
        receiving = fileSystem.open(name + ".recv");
        receiving.truncate(0);
        receivingIndex = index;
        receivedBytes = 0;
    }

    boolean isReceiving(long index) {
        return receiving != null && receivingIndex == index;
    }

    long receivedBytes() {
        return receivedBytes;
    }

    /** Figure 13 step 3: write the chunk at the end of the snapshot file. */
    void writeChunk(byte[] chunk) {
        receiving.write(receivedBytes, chunk, 0, chunk.length);
        receivedBytes += chunk.length;
    }

    /**
     * Figure 13 step 5: the last chunk is in. Fsyncs the file, reads it back and checks it, then
     * renames it into place, replacing the previous snapshot. Returns empty (and discards the
     * file) if it is damaged, for example because chunks were lost; the leader will send it again.
     */
    Optional<Snapshot> finishReceiving() {
        receiving.force();
        receiving.close();
        receiving = null;
        String received = name + ".recv";
        byte[] file = readAll(fileSystem, received);
        Optional<Snapshot> snapshot = decode(file).filter(s -> s.index() == receivingIndex);
        if (snapshot.isEmpty()) {
            fileSystem.delete(received);
            return Optional.empty();
        }
        fileSystem.replace(received, name);
        latest = snapshot.get();
        latestFile = file;
        return snapshot;
    }

    private void abortReceiving() {
        if (receiving != null) {
            receiving.close();
            receiving = null;
        }
        fileSystem.delete(name + ".recv");
    }

    // ------------------------------------------------------------------ file format

    static byte[] encode(long index, long term, byte[] data) {
        ByteBuffer buffer = ByteBuffer.allocate(HEADER_SIZE + data.length);
        buffer.putInt(MAGIC).putInt(VERSION).putLong(index).putLong(term).putInt(data.length);
        buffer.putInt(crc(buffer.array(), data));
        buffer.put(data);
        return buffer.array();
    }

    static Optional<Snapshot> decode(byte[] file) {
        if (file.length < HEADER_SIZE) {
            return Optional.empty();
        }
        ByteBuffer buffer = ByteBuffer.wrap(file);
        int magic = buffer.getInt();
        int version = buffer.getInt();
        long index = buffer.getLong();
        long term = buffer.getLong();
        int length = buffer.getInt();
        int storedCrc = buffer.getInt();
        if (magic != MAGIC || version != VERSION || length != file.length - HEADER_SIZE) {
            return Optional.empty();
        }
        byte[] data = Arrays.copyOfRange(file, HEADER_SIZE, file.length);
        return storedCrc == crc(file, data) ? Optional.of(new Snapshot(index, term, data)) : Optional.empty();
    }

    /** CRC over the header fields before the CRC (28 bytes) and the data. */
    private static int crc(byte[] header, byte[] data) {
        CRC32 crc = new CRC32();
        crc.update(header, 0, HEADER_SIZE - 4);
        crc.update(data);
        return (int) crc.getValue();
    }

    private static byte[] readAll(FileSystem fileSystem, String name) {
        try (StorageFile file = fileSystem.open(name)) {
            byte[] bytes = new byte[Math.toIntExact(file.size())];
            file.read(0, bytes, 0, bytes.length);
            return bytes;
        }
    }
}
