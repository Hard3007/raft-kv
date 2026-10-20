package io.github.hard3007.raftkv.kv;

import com.google.protobuf.InvalidProtocolBufferException;
import io.github.hard3007.raftkv.proto.kv.KvCommand;
import io.github.hard3007.raftkv.proto.raft.EntryType;
import io.github.hard3007.raftkv.proto.raft.LogEntry;
import io.github.hard3007.raftkv.raft.StateMachine;
import io.github.hard3007.raftkv.storage.BTree;
import io.github.hard3007.raftkv.storage.WriteBatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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

    private final BTree tree;
    private final long syncEveryEntries;
    private final List<ApplyListener> listeners = new ArrayList<>();
    private long appliedSinceSync;

    /** A state machine that never syncs the tree itself (the tree still syncs on eviction and checkpoints). */
    public KvStateMachine(BTree tree) {
        this(tree, 0);
    }

    /** @param syncEveryEntries make the applied state durable after this many entries (0 = never) */
    public KvStateMachine(BTree tree, long syncEveryEntries) {
        this.tree = tree;
        this.syncEveryEntries = syncEveryEntries;
    }

    public void addListener(ApplyListener listener) {
        listeners.add(listener);
    }

    /** Index of the last applied entry that is in the tree (durable once the tree syncs). */
    public long appliedIndex() {
        return tree.appliedIndex();
    }

    /** Reads a user key from the current state. Linearizable only when called after ReadIndex. */
    public Optional<byte[]> read(byte[] userKey) {
        return tree.get(KvKeys.dataKey(userKey));
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
}
