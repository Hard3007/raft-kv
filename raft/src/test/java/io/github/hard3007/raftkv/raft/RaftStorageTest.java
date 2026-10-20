package io.github.hard3007.raftkv.raft;

import static io.github.hard3007.raftkv.raft.RaftCluster.entry;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.hard3007.raftkv.storage.SimulatedFileSystem;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class RaftStorageTest {

    @Test
    void replayRebuildsTermVoteAndLogIncludingTruncations() {
        SimulatedFileSystem disk = new SimulatedFileSystem(new Random(1));
        try (RaftStorage storage = RaftStorage.open(disk, "raft.wal")) {
            storage.save(1, 2, 0, List.of(entry(1, 1, "a"), entry(2, 1, "b"), entry(3, 1, "c")));
            storage.save(2, 3, 2, List.of(entry(2, 2, "B"))); // truncate from 2, then append
            storage.save(3, 0, 0, List.of());
        }
        disk.simulatePowerLoss(); // everything was fsynced, so nothing may be lost

        try (RaftStorage storage = RaftStorage.open(disk, "raft.wal")) {
            assertEquals(3, storage.currentTerm());
            assertEquals(0, storage.votedFor());
            assertEquals(List.of(entry(1, 1, "a"), entry(2, 2, "B")), storage.recoveredEntries());
        }
    }

    @Test
    void newStorageStartsEmpty() {
        try (RaftStorage storage = RaftStorage.open(new SimulatedFileSystem(new Random(1)), "raft.wal")) {
            assertEquals(0, storage.currentTerm());
            assertEquals(0, storage.votedFor());
            assertEquals(List.of(), storage.recoveredEntries());
        }
    }
}
