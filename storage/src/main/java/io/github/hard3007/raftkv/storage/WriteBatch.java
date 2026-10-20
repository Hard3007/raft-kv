package io.github.hard3007.raftkv.storage;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalLong;

/**
 * A group of puts and deletes that {@link BTree#write} applies atomically: after a crash, either
 * all of them are visible or none are. A batch can also carry an "applied index", an opaque
 * counter stored with the same atomicity. The Raft state machine uses it for lastApplied.
 */
public final class WriteBatch {

    public sealed interface Operation permits Put, Delete {
        byte[] key();
    }

    public record Put(byte[] key, byte[] value) implements Operation {
    }

    public record Delete(byte[] key) implements Operation {
    }

    private final List<Operation> operations = new ArrayList<>();
    private OptionalLong appliedIndex = OptionalLong.empty();

    public WriteBatch put(byte[] key, byte[] value) {
        operations.add(new Put(key, value));
        return this;
    }

    public WriteBatch delete(byte[] key) {
        operations.add(new Delete(key));
        return this;
    }

    public WriteBatch setAppliedIndex(long index) {
        appliedIndex = OptionalLong.of(index);
        return this;
    }

    public List<Operation> operations() {
        return operations;
    }

    public OptionalLong appliedIndex() {
        return appliedIndex;
    }
}
