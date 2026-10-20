package io.github.hard3007.raftkv.raft;

import static io.github.hard3007.raftkv.raft.RaftCluster.entry;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.hard3007.raftkv.proto.raft.LogEntry;
import io.github.hard3007.raftkv.storage.SimulatedCrashException;
import io.github.hard3007.raftkv.storage.SimulatedFileSystem;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import org.junit.jupiter.api.Test;

class SnapshotStoreTest {

    private static byte[] data(int size, int seed) {
        byte[] data = new byte[size];
        new Random(seed).nextBytes(data);
        return data;
    }

    @Test
    void savedSnapshotSurvivesPowerLossAndReopen() {
        SimulatedFileSystem disk = new SimulatedFileSystem(new Random(1));
        SnapshotStore.open(disk, "s").save(42, 7, data(1000, 1));
        disk.simulatePowerLoss();
        SnapshotStore.Snapshot snapshot = SnapshotStore.open(disk, "s").latest().orElseThrow();
        assertEquals(42, snapshot.index());
        assertEquals(7, snapshot.term());
        assertArrayEquals(data(1000, 1), snapshot.data());
    }

    /** A crash at every single I/O step of save(): afterwards the store holds the old or the new snapshot, whole. */
    @Test
    void crashAtEveryStepOfSaveLeavesTheOldOrTheNewSnapshot() {
        int crashPoints = 0;
        for (int n = 0; ; n++) {
            SimulatedFileSystem disk = new SimulatedFileSystem(new Random(n));
            SnapshotStore.open(disk, "s").save(10, 1, data(300, 1));
            disk.crashAfter(n);
            boolean crashed = false;
            try {
                SnapshotStore.open(disk, "s").save(20, 2, data(500, 2));
            } catch (SimulatedCrashException expected) {
                crashed = true;
            }
            disk.simulatePowerLoss();
            SnapshotStore.Snapshot after = SnapshotStore.open(disk, "s").latest().orElseThrow();
            if (after.index() == 10) {
                assertArrayEquals(data(300, 1), after.data(), "crash point " + n);
            } else {
                assertEquals(20, after.index(), "crash point " + n);
                assertArrayEquals(data(500, 2), after.data(), "crash point " + n);
            }
            if (!crashed) {
                assertEquals(20, after.index(), "a save that returned must be durable");
                break;
            }
            crashPoints++;
        }
        assertTrue(crashPoints >= 5, "save() should have several I/O steps, saw " + crashPoints);
    }

    @Test
    void receivedChunksAreCheckedBeforeTheyReplaceTheSnapshot() {
        SimulatedFileSystem disk = new SimulatedFileSystem(new Random(1));
        SnapshotStore store = SnapshotStore.open(disk, "s");
        store.save(5, 1, data(100, 1));
        byte[] file = SnapshotStore.encode(30, 3, data(700, 3));

        // A transfer that lost a chunk (bytes 64-127): the CRC check rejects it.
        store.beginReceiving(30);
        store.writeChunk(Arrays.copyOfRange(file, 0, 64));
        store.writeChunk(Arrays.copyOfRange(file, 128, file.length));
        assertEquals(Optional.empty(), store.finishReceiving());
        assertEquals(5, store.latest().orElseThrow().index(), "a damaged transfer must not replace the snapshot");

        // A complete transfer in 64-byte chunks replaces it.
        store.beginReceiving(30);
        for (int offset = 0; offset < file.length; offset += 64) {
            store.writeChunk(Arrays.copyOfRange(file, offset, Math.min(file.length, offset + 64)));
        }
        SnapshotStore.Snapshot installed = store.finishReceiving().orElseThrow();
        assertEquals(30, installed.index());
        disk.simulatePowerLoss();
        assertArrayEquals(data(700, 3), SnapshotStore.open(disk, "s").latest().orElseThrow().data());
    }

    @Test
    void compactionRewritesTheWalSoItStopsGrowing() {
        SimulatedFileSystem disk = new SimulatedFileSystem(new Random(1));
        RaftStorage storage = RaftStorage.open(disk, "raft.wal");
        List<LogEntry> all = new ArrayList<>();
        for (int i = 1; i <= 200; i++) {
            all.add(entry(i, 1, "command-" + i));
            storage.save(1, 0, 0, List.of(all.get(i - 1)));
        }
        long before = storage.walBytes();
        storage.rewrite(190, 1, 2, 3, all.subList(190, 200));
        assertTrue(storage.walBytes() < before / 10, "WAL went from " + before + " to " + storage.walBytes());
        storage.close();

        disk.simulatePowerLoss();
        RaftStorage recovered = RaftStorage.open(disk, "raft.wal");
        assertEquals(190, recovered.snapshotIndex());
        assertEquals(1, recovered.snapshotTerm());
        assertEquals(2, recovered.currentTerm());
        assertEquals(3, recovered.votedFor());
        assertEquals(all.subList(190, 200), recovered.recoveredEntries());
        assertFalse(recovered.snapshots().latest().isPresent());
    }
}
