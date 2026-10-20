package io.github.hard3007.raftkv.storage;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;

/** Random keys, values and batches for B-Tree tests, plus a TreeMap oracle. */
final class RandomData {

    private RandomData() {
    }

    /** One of {@code keySpace} keys, so the same keys are hit repeatedly. Lengths 1..33, any byte value. */
    static byte[] key(Random random, int keySpace) {
        Random keyRandom = new Random(random.nextInt(keySpace));
        byte[] key = new byte[1 + keyRandom.nextInt(33)];
        keyRandom.nextBytes(key);
        return key;
    }

    /** Mostly small values, occasionally up to the maximum size, so pages fill unevenly. */
    static byte[] value(Random random) {
        int length = random.nextInt(10) == 0 ? random.nextInt(BTree.MAX_VALUE_SIZE + 1) : random.nextInt(200);
        byte[] value = new byte[length];
        random.nextBytes(value);
        return value;
    }

    static WriteBatch batch(Random random, int keySpace, long appliedIndex) {
        WriteBatch batch = new WriteBatch().setAppliedIndex(appliedIndex);
        int size = 1 + random.nextInt(5);
        for (int i = 0; i < size; i++) {
            if (random.nextInt(4) == 0) {
                batch.delete(key(random, keySpace));
            } else {
                batch.put(key(random, keySpace), value(random));
            }
        }
        return batch;
    }

    static TreeMap<byte[], byte[]> newOracle() {
        return new TreeMap<>(Bytes.COMPARATOR);
    }

    static void apply(TreeMap<byte[], byte[]> oracle, WriteBatch batch) {
        for (WriteBatch.Operation operation : batch.operations()) {
            switch (operation) {
                case WriteBatch.Put put -> oracle.put(put.key(), put.value());
                case WriteBatch.Delete delete -> oracle.remove(delete.key());
            }
        }
    }

    /** The oracle state after applying the first {@code count} batches. */
    static TreeMap<byte[], byte[]> stateAfter(List<WriteBatch> batches, int count) {
        TreeMap<byte[], byte[]> oracle = newOracle();
        for (WriteBatch batch : batches.subList(0, count)) {
            apply(oracle, batch);
        }
        return oracle;
    }

    static void assertSameContents(Map<byte[], byte[]> expected, List<BTree.Entry> actual, String message) {
        assertEquals(expected.size(), actual.size(), message + ": entry count");
        Iterator<Map.Entry<byte[], byte[]>> it = expected.entrySet().iterator();
        for (BTree.Entry entry : actual) {
            Map.Entry<byte[], byte[]> want = it.next();
            assertArrayEquals(want.getKey(), entry.key(), message + ": key");
            assertArrayEquals(want.getValue(), entry.value(), message + ": value");
        }
    }
}
