package io.github.hard3007.raftkv.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Crash-recovery tests. Each scenario writes random batches, where batch i sets appliedIndex = i,
 * then crashes. After recovery, appliedIndex says which prefix of batches survived, and the tree
 * must equal the TreeMap oracle after exactly that prefix. A partially applied batch, a lost
 * acknowledged batch, or a structurally broken tree all fail the test.
 *
 * <p>The page cache is 4 pages and checkpoints are frequent, so dirty pages are constantly evicted
 * and every crash point is exercised: mid-append, mid-eviction, mid-checkpoint, mid-WAL-reset,
 * and mid-recovery.
 */
class BTreeRecoveryTest {

    private static final int KEY_SPACE = 300;

    private static BTree open(FileSystem fs, FsyncPolicy policy) {
        return BTree.open(fs, "tree", new BTree.Options(4, policy, 32 << 10));
    }

    /** What the workload did before the crash. */
    private record Workload(List<WriteBatch> batches, int acknowledged, int durable) {
    }

    /**
     * Writes up to a few hundred batches. Stops early if the file system's scheduled crash fires.
     * {@code durable} is the number of batches guaranteed to survive power loss: all acknowledged
     * ones under ALWAYS, and those acknowledged before the last sync or checkpoint under LAZY.
     */
    private static Workload runUntilCrash(Random random, SimulatedFileSystem fs, FsyncPolicy policy) {
        List<WriteBatch> batches = new ArrayList<>();
        int acknowledged = 0;
        int durable = 0;
        try {
            BTree tree = open(fs, policy);
            int total = 50 + random.nextInt(250);
            for (int i = 1; i <= total; i++) {
                WriteBatch batch = RandomData.batch(random, KEY_SPACE, i);
                batches.add(batch);
                tree.write(batch);
                acknowledged = i;
                if (policy == FsyncPolicy.ALWAYS) {
                    durable = i;
                }
                int action = random.nextInt(40);
                if (action == 0) {
                    tree.sync();
                    durable = i;
                } else if (action == 1) {
                    tree.checkpoint();
                    durable = i;
                }
            }
        } catch (SimulatedCrashException crash) {
            // The crash landed in the middle of an operation; the tree object is abandoned.
        }
        return new Workload(batches, acknowledged, durable);
    }

    /** Recovers and checks that the tree holds exactly a prefix of batches within the allowed range. */
    private static void assertRecoveredPrefix(SimulatedFileSystem fs, FsyncPolicy policy, Workload workload,
            int minimum, int maximum, String message) {
        BTree tree = open(fs, policy);
        int recovered = (int) tree.appliedIndex();
        assertTrue(recovered >= minimum, message + ": recovered " + recovered + " batches, expected at least " + minimum);
        assertTrue(recovered <= maximum, message + ": recovered " + recovered + " batches, expected at most " + maximum);
        TreeMap<byte[], byte[]> expected = RandomData.stateAfter(workload.batches(), recovered);
        RandomData.assertSameContents(expected, tree.scan(null, null, Integer.MAX_VALUE), message);
        tree.checkInvariants();

        // The recovered tree must keep working: write one more batch and reopen cleanly.
        WriteBatch next = new WriteBatch().put(new byte[] {42}, new byte[] {7}).setAppliedIndex(recovered + 1);
        tree.write(next);
        tree.close();
        RandomData.apply(expected, next);
        try (BTree reopened = open(fs, policy)) {
            RandomData.assertSameContents(expected, reopened.scan(null, null, Integer.MAX_VALUE), message + " (reopen)");
        }
    }

    @Test
    void processCrashLosesNothingEvenWithLazyFsync() {
        for (int seed = 0; seed < 100; seed++) {
            Random random = new Random(seed);
            SimulatedFileSystem fs = new SimulatedFileSystem(random);
            Workload workload = runUntilCrash(random, fs, FsyncPolicy.LAZY);
            fs.simulateProcessCrash();
            int all = workload.acknowledged();
            assertRecoveredPrefix(fs, FsyncPolicy.LAZY, workload, all, all, "seed " + seed);
        }
    }

    @Test
    void powerLossWithAlwaysFsyncKeepsEveryAcknowledgedBatch() {
        for (int seed = 0; seed < 200; seed++) {
            Random random = new Random(seed);
            SimulatedFileSystem fs = new SimulatedFileSystem(random);
            Workload workload = runUntilCrash(random, fs, FsyncPolicy.ALWAYS);
            fs.simulatePowerLoss();
            int all = workload.acknowledged();
            assertRecoveredPrefix(fs, FsyncPolicy.ALWAYS, workload, all, all, "seed " + seed);
        }
    }

    @Test
    void powerLossWithLazyFsyncRecoversAPrefixIncludingEverySyncedBatch() {
        for (int seed = 0; seed < 200; seed++) {
            Random random = new Random(seed);
            SimulatedFileSystem fs = new SimulatedFileSystem(random);
            Workload workload = runUntilCrash(random, fs, FsyncPolicy.LAZY);
            fs.simulatePowerLoss();
            assertRecoveredPrefix(fs, FsyncPolicy.LAZY, workload, workload.durable(), workload.acknowledged(),
                    "seed " + seed);
        }
    }

    @Test
    void crashAtAnyIoOperationRecoversAConsistentPrefix() {
        for (FsyncPolicy policy : FsyncPolicy.values()) {
            for (int seed = 0; seed < 300; seed++) {
                Random random = new Random(seed);
                SimulatedFileSystem fs = new SimulatedFileSystem(random);
                fs.crashAfter(random.nextInt(3000)); // may land during creation, a write, or a checkpoint
                Workload workload = runUntilCrash(random, fs, policy);
                fs.simulatePowerLoss();

                // Half the time, crash again during recovery itself.
                if (random.nextBoolean()) {
                    fs.crashAfter(random.nextInt(40));
                    try {
                        open(fs, policy);
                    } catch (SimulatedCrashException crashDuringRecovery) {
                        // expected most of the time
                    }
                    fs.simulatePowerLoss();
                }

                // The batch in flight when the crash hit may or may not have survived.
                int inFlight = Math.min(workload.acknowledged() + 1, workload.batches().size());
                assertRecoveredPrefix(fs, policy, workload, workload.durable(), inFlight,
                        policy + " seed " + seed);
            }
        }
    }

    @TempDir
    Path dir;

    @Test
    void realDiskRecoversAfterCrashWithoutCloseAndATornWalTail() throws IOException {
        Random random = new Random(99);
        List<WriteBatch> batches = new ArrayList<>();
        FileSystem fs = new DiskFileSystem(dir);
        BTree tree = BTree.open(fs, "tree", new BTree.Options(4, FsyncPolicy.ALWAYS, 1 << 30));
        for (int i = 1; i <= 500; i++) {
            WriteBatch batch = RandomData.batch(random, KEY_SPACE, i);
            batches.add(batch);
            tree.write(batch);
        }
        // "Crash": never call close(), so no checkpoint runs. Then simulate a torn append by
        // writing half of a record header plus garbage at the end of the WAL.
        Files.write(dir.resolve("tree.wal"), new byte[] {0, 0, 1, 0, 9, 9, 9}, StandardOpenOption.APPEND);

        try (BTree recovered = BTree.open(new DiskFileSystem(dir), "tree",
                new BTree.Options(4, FsyncPolicy.ALWAYS, 1 << 30))) {
            assertEquals(500, recovered.appliedIndex());
            RandomData.assertSameContents(RandomData.stateAfter(batches, 500),
                    recovered.scan(null, null, Integer.MAX_VALUE), "after crash");
            recovered.checkInvariants();
        }
    }
}
