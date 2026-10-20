package io.github.hard3007.raftkv.storage;

/** When the {@link Wal} calls fsync. */
public enum FsyncPolicy {

    /** fsync after every append: once {@code append} returns, the record survives power loss. */
    ALWAYS,

    /**
     * fsync only when {@link Wal#sync()} is called. Appends reach the OS page cache, so they
     * survive a process crash but may be lost on power loss. The B-Tree uses this when running
     * under Raft: the Raft log is the source of truth and lost state-machine writes are re-applied.
     */
    LAZY,
}
