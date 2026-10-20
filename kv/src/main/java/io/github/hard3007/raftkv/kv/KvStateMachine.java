package io.github.hard3007.raftkv.kv;

import com.google.protobuf.InvalidProtocolBufferException;
import io.github.hard3007.raftkv.proto.kv.KvCommand;
import io.github.hard3007.raftkv.proto.raft.EntryType;
import io.github.hard3007.raftkv.proto.raft.LogEntry;
import io.github.hard3007.raftkv.raft.StateMachine;
import io.github.hard3007.raftkv.storage.BTree;
import io.github.hard3007.raftkv.storage.FileSystem;
import io.github.hard3007.raftkv.storage.StorageCorruptionException;
import io.github.hard3007.raftkv.storage.StorageFile;
import io.github.hard3007.raftkv.storage.WriteBatch;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.LongConsumer;
import java.util.zip.CRC32;

/**
 * The replicated key-value state machine, stored in a {@link BTree}.
 *
 * <p><b>One entry = one atomic batch.</b> Applying a log entry writes a single
 * {@link WriteBatch} holding the data change, the client's session row, and the entry's index as
 * the B-Tree's applied index. After any crash the tree is exactly "the state after entry N" for
 * some N, sessions included, and Raft re-applies from N + 1.
 *
 * <p><b>Exactly-once writes (client sessions).</b> Each client has at most one outstanding request
 * and numbers its writes 1, 2, 3, ... A retry reuses the number. For each client the session
 * table keeps the latest applied sequence number and that write's reply:
 * <ul>
 *   <li>sequence > lastSequence: a new write; apply it and record its reply;
 *   <li>sequence == lastSequence: a retry of the latest write; return the cached reply and change nothing;
 *   <li>sequence < lastSequence: an old duplicate the client stopped waiting for; change nothing.
 * </ul>
 * Session rows are never evicted (a known limitation, see DESIGN.md).
 *
 * <p><b>Bounded recovery.</b> The B-Tree runs with lazy fsync (the Raft log is the source of
 * truth), so on its own it might fsync rarely, and after a power loss Raft would have to re-apply a
 * long stretch of log. Syncing the tree every {@code syncEveryEntries} applied entries bounds
 * that work. The choice of when to sync is count-based, so it is deterministic, and syncing never
 * changes the state.
 *
 * <p><b>Snapshots.</b> {@link #takeSnapshot} serializes every row of the tree, data and sessions,
 * in key order. {@link #restoreSnapshot} builds a <i>new generation</i> of the tree
 * ({@code <name>-<g+1>.db/.wal}), checkpoints it, and then atomically switches the pointer file
 * {@code <name>.current} to it (temporary file, fsync, rename). A crash at any step leaves the
 * pointer on either the complete old tree or the complete new one.
 *
 * <p><b>Determinism.</b> Every replica must reach the same state from the same log, so
 * {@link #apply} depends only on the entry and the current tree: no clocks, no randomness, no
 * iteration over hash-ordered collections.
 *
 * <p>Runs on the Raft node's thread; not thread-safe.
 */
public final class KvStateMachine implements StateMachine {

    /** What applying a write did. */
    public enum Outcome { APPLIED, DUPLICATE, STALE }

    /** The reply to a write: for deletes, whether the key existed when the delete applied. */
    public record WriteResult(Outcome outcome, boolean existed) {
    }

    /** Notified after each entry is applied; {@code result} is null for no-ops. */
    @FunctionalInterface
    public interface ApplyListener {
        void onApplied(LogEntry entry, WriteResult result);
    }

    private static final int POINTER_MAGIC = 0x524B5643; // "RKVC"
    private static final int RESTORE_BATCH = 256;

    private final FileSystem fileSystem;
    private final String name;
    private final BTree.Options treeOptions;
    private final long syncEveryEntries;
    private final List<ApplyListener> listeners = new ArrayList<>();
    private final List<LongConsumer> restoreListeners = new ArrayList<>();
    private BTree tree;
    private long generation;
    private long appliedSinceSync;

    /** A state machine on an already open tree, without snapshot support (for unit tests). */
    public KvStateMachine(BTree tree) {
        this.fileSystem = null;
        this.name = null;
        this.treeOptions = null;
        this.syncEveryEntries = 0;
        this.tree = tree;
    }

    private KvStateMachine(FileSystem fileSystem, String name, BTree.Options treeOptions, long syncEveryEntries) {
        this.fileSystem = fileSystem;
        this.name = name;
        this.treeOptions = treeOptions;
        this.syncEveryEntries = syncEveryEntries;
        this.generation = readPointer();
        // Remove whatever an interrupted restore or cleanup left behind.
        deleteTree(generation + 1);
        if (generation > 0) {
            deleteTree(generation - 1);
        }
        this.tree = BTree.open(fileSystem, treeName(generation), treeOptions);
    }

    /**
     * Opens the state machine stored under {@code name} on {@code fileSystem}.
     *
     * @param syncEveryEntries make the applied state durable after this many entries (0 = never)
     */
    public static KvStateMachine open(FileSystem fileSystem, String name, BTree.Options treeOptions,
            long syncEveryEntries) {
        return new KvStateMachine(fileSystem, name, treeOptions, syncEveryEntries);
    }

    public void addListener(ApplyListener listener) {
        listeners.add(listener);
    }

    /** Notified with the snapshot index after {@link #restoreSnapshot}. */
    public void addRestoreListener(LongConsumer listener) {
        restoreListeners.add(listener);
    }

    /** Index of the last applied entry that is in the tree (durable once the tree syncs). */
    @Override
    public long appliedIndex() {
        return tree.appliedIndex();
    }

    /** Reads a user key from the current state. Linearizable only when called after ReadIndex. */
    public Optional<byte[]> read(byte[] userKey) {
        return tree.get(KvKeys.dataKey(userKey));
    }

    /** Every row in the tree (data and sessions), in key order. For tests and snapshots. */
    public List<BTree.Entry> rows() {
        return tree.scan(null, null, Integer.MAX_VALUE);
    }

    @Override
    public void apply(LogEntry entry) {
        WriteBatch batch = new WriteBatch().setAppliedIndex(entry.getIndex());
        WriteResult result = null;
        if (entry.getType() == EntryType.COMMAND) {
            result = applyCommand(parse(entry), batch);
        }
        tree.write(batch);
        if (syncEveryEntries > 0 && ++appliedSinceSync >= syncEveryEntries) {
            tree.sync();
            appliedSinceSync = 0;
        }
        for (ApplyListener listener : listeners) {
            listener.onApplied(entry, result);
        }
    }

    private WriteResult applyCommand(KvCommand command, WriteBatch batch) {
        byte[] sessionKey = KvKeys.sessionKey(command.getClientId());
        Optional<KvKeys.Session> session = tree.get(sessionKey).map(KvKeys.Session::decode);
        if (session.isPresent()) {
            long last = session.get().lastSequence();
            if (command.getSequence() == last) {
                return new WriteResult(Outcome.DUPLICATE, session.get().existed());
            }
            if (command.getSequence() < last) {
                return new WriteResult(Outcome.STALE, false);
            }
        }

        boolean existed = false;
        switch (command.getOpCase()) {
            case PUT -> batch.put(KvKeys.dataKey(command.getPut().getKey().toByteArray()),
                    command.getPut().getValue().toByteArray());
            case DELETE -> {
                byte[] dataKey = KvKeys.dataKey(command.getDelete().getKey().toByteArray());
                existed = tree.get(dataKey).isPresent();
                batch.delete(dataKey);
            }
            default -> throw new IllegalStateException("command without an operation: " + command);
        }
        batch.put(sessionKey, new KvKeys.Session(command.getSequence(), existed).encode());
        return new WriteResult(Outcome.APPLIED, existed);
    }

    private static KvCommand parse(LogEntry entry) {
        try {
            return KvCommand.parseFrom(entry.getCommand());
        } catch (InvalidProtocolBufferException e) {
            // Commands are validated before proposing, so this is a bug, not bad input.
            throw new IllegalStateException("unparseable command at index " + entry.getIndex(), e);
        }
    }

    // ------------------------------------------------------------------ snapshots

    /** Snapshot format: rowCount (4) | rowCount × (keyLength (4) | key | valueLength (4) | value). */
    @Override
    public byte[] takeSnapshot() {
        List<BTree.Entry> rows = rows();
        int size = 4;
        for (BTree.Entry row : rows) {
            size += 8 + row.key().length + row.value().length;
        }
        ByteBuffer buffer = ByteBuffer.allocate(size).putInt(rows.size());
        for (BTree.Entry row : rows) {
            buffer.putInt(row.key().length).put(row.key()).putInt(row.value().length).put(row.value());
        }
        return buffer.array();
    }

    @Override
    public void restoreSnapshot(byte[] snapshot, long index) {
        if (fileSystem == null) {
            throw new UnsupportedOperationException("opened without a file system; cannot restore");
        }
        long next = generation + 1;
        deleteTree(next);
        BTree fresh = BTree.open(fileSystem, treeName(next), treeOptions);
        ByteBuffer buffer = ByteBuffer.wrap(snapshot);
        int rows = buffer.getInt();
        WriteBatch batch = new WriteBatch();
        for (int i = 0; i < rows; i++) {
            byte[] key = new byte[buffer.getInt()];
            buffer.get(key);
            byte[] value = new byte[buffer.getInt()];
            buffer.get(value);
            batch.put(key, value);
            if (batch.operations().size() == RESTORE_BATCH) {
                fresh.write(batch);
                batch = new WriteBatch();
            }
        }
        fresh.write(batch.setAppliedIndex(index));
        fresh.close(); // checkpoint: the new tree is complete and durable on its own

        writePointer(next); // the atomic switch (temporary file, fsync, rename)
        BTree old = tree;
        tree = BTree.open(fileSystem, treeName(next), treeOptions);
        generation = next;
        appliedSinceSync = 0;
        old.close();
        deleteTree(next - 1);
        for (LongConsumer listener : restoreListeners) {
            listener.accept(index);
        }
    }

    /** Checkpoints the tree and closes it (clean shutdown). */
    public void close() {
        tree.close();
    }

    private String treeName(long g) {
        return name + "-" + g;
    }

    private void deleteTree(long g) {
        fileSystem.delete(treeName(g) + ".db");
        fileSystem.delete(treeName(g) + ".wal");
        fileSystem.delete(treeName(g) + ".wal.tmp");
    }

    /** Pointer file: magic (4) | generation (8) | crc32 of the previous 12 bytes (4). */
    private long readPointer() {
        String pointer = name + ".current";
        fileSystem.delete(pointer + ".tmp");
        if (!fileSystem.exists(pointer)) {
            return 0;
        }
        byte[] bytes = new byte[16];
        try (StorageFile file = fileSystem.open(pointer)) {
            if (file.size() != 16) {
                throw new StorageCorruptionException("bad pointer file " + pointer);
            }
            file.read(0, bytes, 0, 16);
        }
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        int magic = buffer.getInt();
        long g = buffer.getLong();
        if (magic != POINTER_MAGIC || buffer.getInt() != crc(bytes)) {
            throw new StorageCorruptionException("bad pointer file " + pointer); // written via rename: never torn
        }
        return g;
    }

    private void writePointer(long g) {
        String pointer = name + ".current";
        ByteBuffer buffer = ByteBuffer.allocate(16).putInt(POINTER_MAGIC).putLong(g);
        buffer.putInt(crc(buffer.array()));
        fileSystem.delete(pointer + ".tmp");
        try (StorageFile tmp = fileSystem.open(pointer + ".tmp")) {
            tmp.write(0, buffer.array(), 0, 16);
            tmp.force();
        }
        fileSystem.replace(pointer + ".tmp", pointer);
    }

    private static int crc(byte[] bytes) {
        CRC32 crc = new CRC32();
        crc.update(bytes, 0, 12);
        return (int) crc.getValue();
    }
}
