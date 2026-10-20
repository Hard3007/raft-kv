package io.github.hard3007.raftkv.storage;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WalTest {

    @TempDir
    Path dir;

    /** Opens the WAL and collects every recovered record. */
    private static Wal open(FileSystem fs, List<byte[]> recovered, List<Long> lsns, FsyncPolicy policy) {
        return Wal.open(fs, "test.wal", policy, (lsn, payload) -> {
            lsns.add(lsn);
            recovered.add(payload);
        });
    }

    private static byte[] bytes(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    /** Writes records "r1".."rN" and closes the WAL; returns the file offset where each record starts. */
    private long[] writeRecords(int n) {
        FileSystem fs = new DiskFileSystem(dir);
        long[] offsets = new long[n + 1];
        try (Wal wal = open(fs, new ArrayList<>(), new ArrayList<>(), FsyncPolicy.ALWAYS)) {
            for (int i = 1; i <= n; i++) {
                offsets[i - 1] = wal.sizeBytes();
                wal.append(bytes("r" + i));
            }
            offsets[n] = wal.sizeBytes();
        }
        return offsets;
    }

    private List<String> recoverStrings() {
        List<byte[]> recovered = new ArrayList<>();
        open(new DiskFileSystem(dir), recovered, new ArrayList<>(), FsyncPolicy.ALWAYS).close();
        return recovered.stream().map(b -> new String(b, StandardCharsets.UTF_8)).toList();
    }

    private Path walFile() {
        return dir.resolve("test.wal");
    }

    private void flipByte(long offset) throws IOException {
        try (RandomAccessFile file = new RandomAccessFile(walFile().toFile(), "rw")) {
            file.seek(offset);
            int b = file.read();
            file.seek(offset);
            file.write(b ^ 0xFF);
        }
    }

    @Test
    void recordsAreReplayedInOrderWithIncreasingLsns() {
        writeRecords(3);
        List<byte[]> recovered = new ArrayList<>();
        List<Long> lsns = new ArrayList<>();
        try (Wal wal = open(new DiskFileSystem(dir), recovered, lsns, FsyncPolicy.ALWAYS)) {
            assertEquals(List.of(1L, 2L, 3L), lsns);
            assertArrayEquals(bytes("r1"), recovered.get(0));
            assertArrayEquals(bytes("r3"), recovered.get(2));
            assertEquals(4, wal.append(bytes("r4")));
        }
        assertEquals(List.of("r1", "r2", "r3", "r4"), recoverStrings());
    }

    @Test
    void emptyPayloadsAreAllowed() {
        FileSystem fs = new DiskFileSystem(dir);
        try (Wal wal = open(fs, new ArrayList<>(), new ArrayList<>(), FsyncPolicy.ALWAYS)) {
            wal.append(new byte[0]);
            wal.append(bytes("x"));
        }
        assertEquals(List.of("", "x"), recoverStrings());
    }

    @Test
    void tornTailIsTruncatedAtEveryPossibleCutPoint() throws IOException {
        long[] offsets = writeRecords(3);
        byte[] original = Files.readAllBytes(walFile());
        // Cut the file at every byte inside the last record: header, length, CRC, and payload.
        for (long cut = offsets[2]; cut < offsets[3]; cut++) {
            Files.write(walFile(), Arrays.copyOf(original, (int) cut));
            assertEquals(List.of("r1", "r2"), recoverStrings(), "cut at " + cut);
            assertEquals(offsets[2], Files.size(walFile()), "tail must be truncated");
        }
    }

    @Test
    void appendsAfterTruncationAreRecovered() throws IOException {
        long[] offsets = writeRecords(3);
        Files.write(walFile(), Arrays.copyOf(Files.readAllBytes(walFile()), (int) offsets[2] + 3));
        List<Long> lsns = new ArrayList<>();
        try (Wal wal = open(new DiskFileSystem(dir), new ArrayList<>(), lsns, FsyncPolicy.ALWAYS)) {
            assertEquals(3, wal.append(bytes("new3")), "the lost record's LSN is reused");
        }
        assertEquals(List.of("r1", "r2", "new3"), recoverStrings());
    }

    @Test
    void corruptedCrcInTailRecordIsDropped() throws IOException {
        long[] offsets = writeRecords(3);
        flipByte(offsets[3] - 1); // last payload byte of r3
        assertEquals(List.of("r1", "r2"), recoverStrings());
    }

    @Test
    void corruptedLengthFieldIsDetected() throws IOException {
        long[] offsets = writeRecords(3);
        flipByte(offsets[2] + 3); // low byte of r3's length field
        assertEquals(List.of("r1", "r2"), recoverStrings());
    }

    @Test
    void corruptionInTheMiddleDropsEverythingAfterIt() throws IOException {
        long[] offsets = writeRecords(4);
        flipByte(offsets[1] + Wal.RECORD_HEADER_SIZE); // first payload byte of r2
        // Documented behavior: we cannot distinguish this from a torn tail.
        assertEquals(List.of("r1"), recoverStrings());
    }

    @Test
    void zeroFilledTailIsTruncated() throws IOException {
        long[] offsets = writeRecords(2);
        Files.write(walFile(), new byte[100], StandardOpenOption.APPEND);
        assertEquals(List.of("r1", "r2"), recoverStrings());
        assertEquals(offsets[2], Files.size(walFile()));
    }

    @Test
    void corruptedHeaderFailsLoudly() throws IOException {
        writeRecords(1);
        flipByte(5);
        assertThrows(StorageCorruptionException.class, this::recoverStrings);
    }

    @Test
    void resetDiscardsRecordsButLsnsKeepIncreasing() {
        FileSystem fs = new DiskFileSystem(dir);
        try (Wal wal = open(fs, new ArrayList<>(), new ArrayList<>(), FsyncPolicy.ALWAYS)) {
            wal.append(bytes("a"));
            wal.append(bytes("b"));
            wal.reset();
            assertTrue(wal.isEmpty());
            assertEquals(2, wal.lastLsn());
            assertEquals(3, wal.append(bytes("c")));
        }
        List<byte[]> recovered = new ArrayList<>();
        List<Long> lsns = new ArrayList<>();
        open(fs, recovered, lsns, FsyncPolicy.ALWAYS).close();
        assertEquals(List.of(3L), lsns);
        assertArrayEquals(bytes("c"), recovered.get(0));
    }

    @Test
    void durableLsnFollowsTheFsyncPolicy() {
        FileSystem fs = new SimulatedFileSystem(new Random(1));
        try (Wal always = Wal.open(fs, "always.wal", FsyncPolicy.ALWAYS, (lsn, p) -> { })) {
            always.append(bytes("a"));
            assertEquals(1, always.durableLsn());
        }
        try (Wal lazy = Wal.open(fs, "lazy.wal", FsyncPolicy.LAZY, (lsn, p) -> { })) {
            lazy.append(bytes("a"));
            lazy.append(bytes("b"));
            assertEquals(0, lazy.durableLsn());
            lazy.sync();
            assertEquals(2, lazy.durableLsn());
        }
    }

    @Test
    void powerLossKeepsEveryRecordWithAlwaysFsync() {
        for (int seed = 0; seed < 200; seed++) {
            Random random = new Random(seed);
            SimulatedFileSystem fs = new SimulatedFileSystem(random);
            int appended = 1 + random.nextInt(50);
            try (Wal wal = Wal.open(fs, "test.wal", FsyncPolicy.ALWAYS, (lsn, p) -> { })) {
                for (int i = 0; i < appended; i++) {
                    wal.append(payload(seed, i));
                }
            }
            fs.simulatePowerLoss();

            List<byte[]> recovered = new ArrayList<>();
            Wal.open(fs, "test.wal", FsyncPolicy.ALWAYS, (lsn, p) -> recovered.add(p)).close();
            assertEquals(appended, recovered.size(), "seed " + seed);
            for (int i = 0; i < appended; i++) {
                assertArrayEquals(payload(seed, i), recovered.get(i), "seed " + seed);
            }
        }
    }

    @Test
    void powerLossWithLazyFsyncKeepsAPrefixThatIncludesEverySyncedRecord() {
        for (int seed = 0; seed < 200; seed++) {
            Random random = new Random(seed);
            SimulatedFileSystem fs = new SimulatedFileSystem(random);
            int appended = 1 + random.nextInt(50);
            long synced = 0;
            try (Wal wal = Wal.open(fs, "test.wal", FsyncPolicy.LAZY, (lsn, p) -> { })) {
                for (int i = 0; i < appended; i++) {
                    wal.append(payload(seed, i));
                    if (random.nextInt(10) == 0) {
                        wal.sync();
                        synced = wal.durableLsn();
                    }
                }
            }
            fs.simulatePowerLoss();

            List<byte[]> recovered = new ArrayList<>();
            Wal.open(fs, "test.wal", FsyncPolicy.LAZY, (lsn, p) -> recovered.add(p)).close();
            assertTrue(recovered.size() >= synced, "seed " + seed + ": lost a synced record");
            assertTrue(recovered.size() <= appended, "seed " + seed);
            for (int i = 0; i < recovered.size(); i++) {
                assertArrayEquals(payload(seed, i), recovered.get(i), "seed " + seed + " record " + i);
            }
        }
    }

    /** Payload i is derived from (seed, i) alone, so the test can recompute what was written. */
    private static byte[] payload(int seed, int i) {
        Random r = new Random(seed * 1_000_003L + i);
        byte[] payload = new byte[r.nextInt(300)];
        r.nextBytes(payload);
        return payload;
    }
}
