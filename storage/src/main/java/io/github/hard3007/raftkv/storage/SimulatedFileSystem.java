package io.github.hard3007.raftkv.storage;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * An in-memory {@link FileSystem} that models what survives a crash, for deterministic tests.
 *
 * <p>Each file keeps two images: what reads see ("current", like the OS page cache) and what
 * would survive power loss ("durable"). Writes and truncates go to the current image and onto a
 * pending list; {@link StorageFile#force()} moves the pending operations into the durable image.
 *
 * <ul>
 *   <li>{@link #simulateProcessCrash()}: the process dies but the OS keeps running, so every write
 *       survives, fsynced or not.
 *   <li>{@link #simulatePowerLoss()}: each pending (un-fsynced) operation is independently dropped,
 *       applied, or (for writes) torn, applying only a prefix. Keeping a later write while dropping
 *       an earlier one models the disk reordering writes.
 *   <li>{@link #crashAfter(long)}: the Nth mutating operation from now and every operation after it
 *       throw {@link SimulatedCrashException}, so a crash can land at any point, even mid-recovery.
 * </ul>
 *
 * <p>File creation, deletion and {@link #replace} are durable immediately: the real implementation
 * fsyncs the directory after each of them. Handles opened before a crash stop working.
 * All randomness comes from the seeded {@link Random}, so a seed reproduces a run exactly.
 */
public final class SimulatedFileSystem implements FileSystem {

    private final Random random;
    private final Map<String, FileState> files = new HashMap<>();
    /** Incremented on every crash; handles from an older generation are dead. */
    private long generation;
    /** Mutating operations left before the scheduled crash; negative means none scheduled. */
    private long operationsUntilCrash = -1;
    private boolean crashed;
    private long mutations;

    public SimulatedFileSystem(Random random) {
        this.random = random;
    }

    /** The {@code operations}-th mutating operation from now (0 = the next one) throws. */
    public void crashAfter(long operations) {
        this.operationsUntilCrash = operations;
    }

    public void simulateProcessCrash() {
        restart();
    }

    public void simulatePowerLoss() {
        for (FileState file : files.values()) {
            file.losePendingOperations(random);
        }
        restart();
    }

    private void restart() {
        generation++;
        operationsUntilCrash = -1;
        crashed = false;
    }

    @Override
    public StorageFile open(String name) {
        if (!files.containsKey(name)) {
            beforeMutation();
            files.put(name, new FileState());
        }
        checkNotCrashed();
        return new SimulatedFile(name, generation);
    }

    @Override
    public boolean exists(String name) {
        checkNotCrashed();
        return files.containsKey(name);
    }

    @Override
    public void delete(String name) {
        beforeMutation();
        files.remove(name);
    }

    @Override
    public void replace(String source, String target) {
        beforeMutation();
        FileState state = files.remove(source);
        if (state == null) {
            throw new IllegalArgumentException("no such file: " + source);
        }
        files.put(target, state);
    }

    private void checkNotCrashed() {
        if (crashed) {
            throw new SimulatedCrashException();
        }
    }

    /** True once a scheduled crash ({@link #crashAfter}) has fired, until the next simulated crash. */
    public boolean hasCrashed() {
        return crashed;
    }

    /** Mutating operations (writes, truncates, fsyncs, creates, deletes, renames) so far. */
    public long mutations() {
        return mutations;
    }

    private void beforeMutation() {
        checkNotCrashed();
        mutations++;
        if (operationsUntilCrash == 0) {
            crashed = true;
            throw new SimulatedCrashException();
        }
        if (operationsUntilCrash > 0) {
            operationsUntilCrash--;
        }
    }

    private final class SimulatedFile implements StorageFile {

        private final String name;
        private final long openedInGeneration;

        SimulatedFile(String name, long openedInGeneration) {
            this.name = name;
            this.openedInGeneration = openedInGeneration;
        }

        private FileState state() {
            checkNotCrashed();
            if (openedInGeneration != generation) {
                throw new IllegalStateException("file handle used after a crash: " + name);
            }
            FileState state = files.get(name);
            if (state == null) {
                throw new IllegalStateException("file was deleted or renamed: " + name);
            }
            return state;
        }

        @Override
        public void read(long position, byte[] dst, int offset, int length) {
            state().current.read(position, dst, offset, length);
        }

        @Override
        public void write(long position, byte[] src, int offset, int length) {
            FileState state = state();
            beforeMutation();
            byte[] data = Arrays.copyOfRange(src, offset, offset + length);
            state.current.write(position, data);
            state.pending.add(new PendingWrite(position, data));
        }

        @Override
        public long size() {
            return state().current.length();
        }

        @Override
        public void truncate(long size) {
            FileState state = state();
            beforeMutation();
            state.current.truncate(size);
            state.pending.add(new PendingTruncate(size));
        }

        @Override
        public void force() {
            FileState state = state();
            beforeMutation();
            for (PendingOperation op : state.pending) {
                op.applyTo(state.durable);
            }
            state.pending.clear();
        }

        @Override
        public void close() {
            // Nothing to release; durability is decided by force(), not by close().
        }
    }

    private static final class FileState {
        final GrowableBytes current = new GrowableBytes();
        final GrowableBytes durable = new GrowableBytes();
        final List<PendingOperation> pending = new ArrayList<>();

        void losePendingOperations(Random random) {
            for (PendingOperation op : pending) {
                switch (random.nextInt(3)) {
                    case 0 -> { } // lost
                    case 1 -> op.applyTo(durable);
                    default -> op.applyTornTo(durable, random);
                }
            }
            pending.clear();
            current.copyFrom(durable);
        }
    }

    private sealed interface PendingOperation permits PendingWrite, PendingTruncate {
        void applyTo(GrowableBytes file);

        void applyTornTo(GrowableBytes file, Random random);
    }

    private record PendingWrite(long position, byte[] data) implements PendingOperation {
        @Override
        public void applyTo(GrowableBytes file) {
            file.write(position, data);
        }

        @Override
        public void applyTornTo(GrowableBytes file, Random random) {
            int prefix = random.nextInt(data.length + 1);
            file.write(position, Arrays.copyOf(data, prefix));
        }
    }

    private record PendingTruncate(long size) implements PendingOperation {
        @Override
        public void applyTo(GrowableBytes file) {
            file.truncate(size);
        }

        @Override
        public void applyTornTo(GrowableBytes file, Random random) {
            applyTo(file); // a truncate either happens or it does not
        }
    }

    /** A byte array that grows on writes past its end; the gap is zero-filled like a file hole. */
    private static final class GrowableBytes {
        private byte[] bytes = new byte[0];
        private int length;

        int length() {
            return length;
        }

        void read(long position, byte[] dst, int offset, int count) {
            if (position + count > length) {
                throw new IllegalArgumentException("read past end of file at " + position);
            }
            System.arraycopy(bytes, (int) position, dst, offset, count);
        }

        void write(long position, byte[] data) {
            int end = Math.toIntExact(position + data.length);
            if (end > bytes.length) {
                bytes = Arrays.copyOf(bytes, Math.max(end, bytes.length * 2));
            }
            System.arraycopy(data, 0, bytes, (int) position, data.length);
            length = Math.max(length, end);
        }

        void truncate(long size) {
            int newLength = Math.toIntExact(size);
            if (newLength < length) {
                Arrays.fill(bytes, newLength, length, (byte) 0);
            } else if (newLength > bytes.length) {
                bytes = Arrays.copyOf(bytes, newLength);
            }
            length = newLength;
        }

        void copyFrom(GrowableBytes other) {
            bytes = Arrays.copyOf(other.bytes, other.bytes.length);
            length = other.length;
        }
    }
}
