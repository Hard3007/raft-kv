package io.github.hard3007.raftkv.storage;

/** A flat directory of named files. Implemented by {@link DiskFileSystem} and {@link SimulatedFileSystem}. */
public interface FileSystem {

    /** Opens the named file, creating it (durably) if it does not exist. */
    StorageFile open(String name);

    boolean exists(String name);

    /** Deletes the named file if it exists. */
    void delete(String name);

    /**
     * Atomically and durably replaces {@code target} with {@code source} (a rename). After a crash,
     * {@code target} holds either its old content or the complete new content, never a mix.
     */
    void replace(String source, String target);
}
