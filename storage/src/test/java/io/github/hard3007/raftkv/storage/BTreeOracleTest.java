package io.github.hard3007.raftkv.storage;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 100,000 random operations against the B-Tree on a real disk, checked against a TreeMap after
 * every operation. A tiny page cache forces constant eviction (the write-ahead rule path), and a
 * small checkpoint threshold forces frequent checkpoints. The tree is closed and reopened midway.
 */
class BTreeOracleTest {

    private static final int OPERATIONS = 100_000;
    private static final int KEY_SPACE = 20_000;

    @TempDir
    Path dir;

    private BTree open(FileSystem fs) {
        return BTree.open(fs, "oracle", new BTree.Options(32, FsyncPolicy.LAZY, 2 << 20));
    }

    @Test
    void randomOperationsMatchTreeMap() {
        Random random = new Random(20261020);
        FileSystem fs = new DiskFileSystem(dir);
        TreeMap<byte[], byte[]> oracle = RandomData.newOracle();
        BTree tree = open(fs);
        int scans = 0;

        for (int op = 1; op <= OPERATIONS; op++) {
            int kind = random.nextInt(100);
            byte[] key = RandomData.key(random, KEY_SPACE);
            if (kind < 45) {
                byte[] value = RandomData.value(random);
                tree.put(key, value);
                oracle.put(key, value);
            } else if (kind < 65) {
                assertEquals(oracle.remove(key) != null, tree.delete(key), "delete result at op " + op);
            } else if (kind < 70) {
                WriteBatch batch = RandomData.batch(random, KEY_SPACE, op);
                tree.write(batch);
                RandomData.apply(oracle, batch);
            } else if (kind < 95) {
                Optional<byte[]> actual = tree.get(key);
                byte[] expected = oracle.get(key);
                assertEquals(expected != null, actual.isPresent(), "get presence at op " + op);
                if (expected != null) {
                    assertArrayEquals(expected, actual.get(), "get value at op " + op);
                }
            } else {
                byte[] to = RandomData.key(random, KEY_SPACE);
                int limit = 1 + random.nextInt(200);
                TreeMap<byte[], byte[]> expected = RandomData.newOracle();
                if (Bytes.compare(key, to) < 0) {
                    for (Map.Entry<byte[], byte[]> entry : oracle.subMap(key, true, to, false).entrySet()) {
                        if (expected.size() == limit) {
                            break;
                        }
                        expected.put(entry.getKey(), entry.getValue());
                    }
                }
                RandomData.assertSameContents(expected, tree.scan(key, to, limit), "scan at op " + op);
                scans++;
            }

            if (op % 10_000 == 0) {
                tree.checkInvariants();
            }
            if (op == OPERATIONS / 2) {
                tree.close();
                tree = open(fs);
            }
        }

        RandomData.assertSameContents(oracle, tree.scan(null, null, Integer.MAX_VALUE), "final full scan");
        tree.checkInvariants();
        assertTrue(tree.depth() >= 3, "the workload should build a tree at least 3 levels deep");
        assertTrue(scans > 1000);
        tree.close();

        try (BTree reopened = open(fs)) {
            RandomData.assertSameContents(oracle, reopened.scan(null, null, Integer.MAX_VALUE), "after reopen");
        }
    }
}
