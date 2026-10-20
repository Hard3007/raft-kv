package io.github.hard3007.raftkv.storage;

/**
 * A random-access file. Implementations throw unchecked exceptions on I/O failure: storage errors
 * are treated as fatal (fail-stop), because after a failed write or fsync the on-disk state is
 * unknown and the only safe recovery is to restart from what is durable.
 */
public interface StorageFile extends AutoCloseable {

    /** Reads exactly {@code length} bytes at {@code position}; fails if the file is too short. */
    void read(long position, byte[] dst, int offset, int length);

    /** Writes all {@code length} bytes at {@code position}, extending the file if needed. */
    void write(long position, byte[] src, int offset, int length);

    long size();

    void truncate(long size);

    /** Blocks until every previous write to this file is durable (fsync). */
    void force();

    @Override
    void close();
}
