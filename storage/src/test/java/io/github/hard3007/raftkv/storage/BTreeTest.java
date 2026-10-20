package io.github.hard3007.raftkv.storage;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class BTreeTest {

    private final SimulatedFileSystem fs = new SimulatedFileSystem(new Random(1));

    private BTree open() {
        return BTree.open(fs, "t", new BTree.Options(16, FsyncPolicy.ALWAYS, 1 << 20));
    }

    private static byte[] b(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    private static String s(byte[] bytes) {
        return new String(bytes, StandardCharsets.UTF_8);
    }

    @Test
    void putGetOverwriteDelete() {
        try (BTree tree = open()) {
            assertTrue(tree.get(b("a")).isEmpty());
            tree.put(b("a"), b("1"));
            tree.put(b("a"), b("2"));
            assertEquals("2", s(tree.get(b("a")).orElseThrow()));
            assertTrue(tree.delete(b("a")));
            assertFalse(tree.delete(b("a")));
            assertTrue(tree.get(b("a")).isEmpty());
        }
    }

    @Test
    void keysCompareAsUnsignedBytes() {
        try (BTree tree = open()) {
            tree.put(new byte[] {(byte) 0xFF}, b("high"));
            tree.put(new byte[] {0x01}, b("low"));
            List<BTree.Entry> all = tree.scan(null, null, 10);
            assertEquals("low", s(all.get(0).value()));
            assertEquals("high", s(all.get(1).value()));
        }
    }

    @Test
    void scanRespectsBoundsAndLimit() {
        try (BTree tree = open()) {
            for (char c = 'a'; c <= 'z'; c++) {
                tree.put(b(String.valueOf(c)), b("v"));
            }
            List<BTree.Entry> range = tree.scan(b("c"), b("f"), 100);
            assertEquals(List.of("c", "d", "e"), range.stream().map(e -> s(e.key())).toList());
            assertEquals(2, tree.scan(b("x"), null, 2).size());
            assertEquals(0, tree.scan(b("q"), b("q"), 10).size());
        }
    }

    @Test
    void oversizedKeysAndValuesAreRejectedWithoutChangingTheTree() {
        try (BTree tree = open()) {
            WriteBatch batch = new WriteBatch()
                    .put(b("ok"), b("v"))
                    .put(b("big"), new byte[BTree.MAX_VALUE_SIZE + 1]);
            assertThrows(IllegalArgumentException.class, () -> tree.write(batch));
            assertThrows(IllegalArgumentException.class,
                    () -> tree.put(new byte[BTree.MAX_KEY_SIZE + 1], b("v")));
            assertTrue(tree.get(b("ok")).isEmpty(), "a rejected batch must not be partially applied");
        }
    }

    @Test
    void maximumSizeEntriesSplitCorrectly() {
        Random random = new Random(7);
        try (BTree tree = open()) {
            for (int i = 0; i < 500; i++) {
                byte[] key = new byte[BTree.MAX_KEY_SIZE];
                random.nextBytes(key);
                tree.put(key, new byte[BTree.MAX_VALUE_SIZE]);
            }
            tree.checkInvariants();
            assertEquals(500, tree.scan(null, null, 1000).size());
            assertTrue(tree.depth() >= 3);
        }
    }

    @Test
    void appliedIndexIsStoredWithTheBatchAndSurvivesReopen() {
        try (BTree tree = open()) {
            tree.write(new WriteBatch().put(b("k"), b("v")).setAppliedIndex(41));
            tree.write(new WriteBatch().setAppliedIndex(42)); // an index-only batch is logged too
            assertEquals(42, tree.appliedIndex());
        }
        try (BTree tree = open()) {
            assertEquals(42, tree.appliedIndex());
            assertArrayEquals(b("v"), tree.get(b("k")).orElseThrow());
        }
    }
}
