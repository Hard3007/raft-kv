package io.github.hard3007.raftkv.storage;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.zip.CRC32;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Append-only write-ahead log of opaque records.
 *
 * <p>File layout:
 * <pre>
 *   header:  magic (4) | version (4) | firstLsn (8) | crc32 of the previous 16 bytes (4)
 *   record:  length (4) | crc32 of length + payload (4) | payload (length bytes)
 * </pre>
 *
 * <p>Every record has a log sequence number (LSN). The first record in the file has LSN
 * {@code firstLsn} and each later record has the next number. LSNs keep increasing across
 * {@link #reset()}, so a caller can compare an LSN against {@link #durableLsn()} at any time.
 *
 * <p><b>Recovery.</b> {@link #open} replays records in order and stops at the first record that
 * is incomplete, has an impossible length, or fails its CRC. That record and everything after it
 * is truncated. A crash during an append can only damage the tail, so this recovers exactly the
 * records that were completely written. We cannot tell a torn tail apart from media corruption in
 * the middle of the log; both are treated the same way, so mid-log bit rot loses the records
 * after it. Detecting that would need extra redundancy, which is out of scope.
 *
 * <p>The header is never modified in place. A new file (or a {@link #reset()} or {@link #rewrite})
 * is written to a temporary file, fsynced, and atomically renamed over the old one, so the header is
 * never torn.
 *
 * <p>Not thread-safe: callers serialize access.
 */
public final class Wal implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(Wal.class);

    private static final int MAGIC = 0x524B5657; // "RKVW"
    private static final int VERSION = 1;
    static final int HEADER_SIZE = 20;
    static final int RECORD_HEADER_SIZE = 8;
    /** Upper bound on one record; a larger length field can only mean corruption. */
    public static final int MAX_RECORD_SIZE = 32 << 20;

    /** Receives each recovered record during {@link #open}. */
    @FunctionalInterface
    public interface RecordHandler {
        void accept(long lsn, byte[] payload);
    }

    private final FileSystem fileSystem;
    private final String name;
    private final FsyncPolicy fsyncPolicy;
    private StorageFile file;
    private long firstLsn;
    private long lastLsn;
    private long durableLsn;
    private long endOffset;

    private Wal(FileSystem fileSystem, String name, FsyncPolicy fsyncPolicy) {
        this.fileSystem = fileSystem;
        this.name = name;
        this.fsyncPolicy = fsyncPolicy;
    }

    /**
     * Opens the log, creating it if it does not exist. Every intact record is passed to
     * {@code handler} in LSN order. A damaged tail is truncated. When this returns, every
     * recovered record is durable.
     */
    public static Wal open(FileSystem fileSystem, String name, FsyncPolicy fsyncPolicy, RecordHandler handler) {
        if (!fileSystem.exists(name)) {
            writeFile(fileSystem, name, 1, List.of());
        }
        Wal wal = new Wal(fileSystem, name, fsyncPolicy);
        wal.recover(handler);
        return wal;
    }

    private void recover(RecordHandler handler) {
        file = fileSystem.open(name);
        firstLsn = readHeader(file, name);

        long fileSize = file.size();
        long offset = HEADER_SIZE;
        long lsn = firstLsn;
        while (true) {
            byte[] payload = readRecord(file, offset, fileSize);
            if (payload == null) {
                break;
            }
            handler.accept(lsn, payload);
            offset += RECORD_HEADER_SIZE + payload.length;
            lsn++;
        }

        if (offset < fileSize) {
            log.warn("WAL {}: truncating {} bytes of torn or corrupted tail at offset {}",
                    name, fileSize - offset, offset);
            file.truncate(offset);
        }
        // Records that survived a process crash may still sit only in the OS page cache.
        file.force();

        endOffset = offset;
        lastLsn = lsn - 1;
        durableLsn = lastLsn;
    }

    /** Appends a record and returns its LSN. Durable on return only under {@link FsyncPolicy#ALWAYS}. */
    public long append(byte[] payload) {
        if (payload.length > MAX_RECORD_SIZE) {
            throw new IllegalArgumentException("record too large: " + payload.length + " bytes");
        }
        byte[] record = encodeRecord(payload);
        file.write(endOffset, record, 0, record.length);

        endOffset += record.length;
        lastLsn++;
        if (fsyncPolicy == FsyncPolicy.ALWAYS) {
            sync();
        }
        return lastLsn;
    }

    /** Makes every appended record durable. */
    public void sync() {
        if (durableLsn < lastLsn) {
            file.force();
            durableLsn = lastLsn;
        }
    }

    /** Discards every record. The next append gets LSN {@code lastLsn() + 1}. */
    public void reset() {
        rewrite(List.of());
    }

    /**
     * Atomically replaces the whole log with {@code records}: they are written to a temporary file,
     * fsynced, and renamed over the log. After a crash the log holds either the old records or
     * exactly these. LSNs keep increasing: the first new record gets {@code lastLsn() + 1}.
     */
    public void rewrite(List<byte[]> records) {
        file.close();
        long first = lastLsn + 1;
        writeFile(fileSystem, name, first, records);
        file = fileSystem.open(name);
        firstLsn = first;
        endOffset = file.size();
        lastLsn = first + records.size() - 1;
        durableLsn = lastLsn;
    }

    /** LSN of the newest record, or {@code firstLsn - 1} if the log is empty. */
    public long lastLsn() {
        return lastLsn;
    }

    /** Every record with an LSN at or below this one survives power loss. */
    public long durableLsn() {
        return durableLsn;
    }

    public boolean isEmpty() {
        return lastLsn < firstLsn;
    }

    /** Size of the log file in bytes. */
    public long sizeBytes() {
        return endOffset;
    }

    @Override
    public void close() {
        file.close();
    }

    private static void writeFile(FileSystem fileSystem, String name, long firstLsn, List<byte[]> records) {
        String temporary = name + ".tmp";
        fileSystem.delete(temporary);
        try (StorageFile tmp = fileSystem.open(temporary)) {
            ByteBuffer header = ByteBuffer.allocate(HEADER_SIZE);
            header.putInt(MAGIC).putInt(VERSION).putLong(firstLsn);
            header.putInt(crc(header.array(), 0, 16));
            tmp.write(0, header.array(), 0, HEADER_SIZE);
            long offset = HEADER_SIZE;
            for (byte[] payload : records) {
                byte[] record = encodeRecord(payload);
                tmp.write(offset, record, 0, record.length);
                offset += record.length;
            }
            tmp.force();
        }
        fileSystem.replace(temporary, name);
    }

    private static byte[] encodeRecord(byte[] payload) {
        ByteBuffer record = ByteBuffer.allocate(RECORD_HEADER_SIZE + payload.length);
        record.putInt(payload.length);
        record.putInt(checksum(payload.length, payload));
        record.put(payload);
        return record.array();
    }

    private static long readHeader(StorageFile file, String name) {
        if (file.size() < HEADER_SIZE) {
            throw new StorageCorruptionException("WAL " + name + ": file shorter than its header");
        }
        byte[] bytes = new byte[HEADER_SIZE];
        file.read(0, bytes, 0, HEADER_SIZE);
        ByteBuffer header = ByteBuffer.wrap(bytes);
        int magic = header.getInt();
        int version = header.getInt();
        long firstLsn = header.getLong();
        int storedCrc = header.getInt();
        if (magic != MAGIC || version != VERSION || storedCrc != crc(bytes, 0, 16)) {
            throw new StorageCorruptionException("WAL " + name + ": bad header");
        }
        return firstLsn;
    }

    /** Returns the payload of the record at {@code offset}, or null if it is missing or damaged. */
    private static byte[] readRecord(StorageFile file, long offset, long fileSize) {
        if (fileSize - offset < RECORD_HEADER_SIZE) {
            return null;
        }
        byte[] headerBytes = new byte[RECORD_HEADER_SIZE];
        file.read(offset, headerBytes, 0, RECORD_HEADER_SIZE);
        ByteBuffer header = ByteBuffer.wrap(headerBytes);
        int length = header.getInt();
        int storedCrc = header.getInt();
        if (length < 0 || length > MAX_RECORD_SIZE || offset + RECORD_HEADER_SIZE + length > fileSize) {
            return null;
        }
        byte[] payload = new byte[length];
        file.read(offset + RECORD_HEADER_SIZE, payload, 0, length);
        return storedCrc == checksum(length, payload) ? payload : null;
    }

    /** The CRC covers the length field too, so a corrupted length cannot pass as valid. */
    private static int checksum(int length, byte[] payload) {
        CRC32 crc = new CRC32();
        crc.update(ByteBuffer.allocate(4).putInt(length).array());
        crc.update(payload);
        return (int) crc.getValue();
    }

    private static int crc(byte[] bytes, int offset, int length) {
        CRC32 crc = new CRC32();
        crc.update(bytes, offset, length);
        return (int) crc.getValue();
    }
}
