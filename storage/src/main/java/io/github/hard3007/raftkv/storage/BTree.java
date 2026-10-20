package io.github.hard3007.raftkv.storage;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.zip.CRC32;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A durable, ordered map from byte-string keys to byte-string values: a B+Tree on 4 KB pages
 * with an LRU page cache and a write-ahead log.
 *
 * <h2>Files</h2>
 * {@code <name>.db} holds the pages. Page 0 is the meta page (root page id, page count, applied
 * index); every other page is one {@link Node}. {@code <name>.wal} is the {@link Wal}.
 *
 * <h2>Durability</h2>
 * Each {@link #write(WriteBatch)} appends ONE WAL record holding the meta fields plus the full
 * new image of every page the batch changed. Because a WAL record is all-or-nothing, so is the
 * batch, even when it splits pages. Changed pages stay dirty in the cache and reach the data
 * file later, through eviction or a {@link #checkpoint()}.
 *
 * <p><b>Write-ahead rule</b> ({@link #writeBack}): a dirty page is written to the data file only
 * after the WAL record holding its latest image is durable. This holds even with
 * {@link FsyncPolicy#LAZY}: if needed, the WAL is fsynced right before the page write. Without
 * the rule, power loss could leave a page on disk that reflects a change the surviving WAL knows
 * nothing about, for example one half of a split, and recovery could not repair it.
 *
 * <p><b>Recovery</b> (on open): replay every intact WAL record by writing its page images to the
 * data file, then take a checkpoint. Replaying a full image is idempotent, so a crash during
 * recovery is harmless. Replaying images also repairs data pages that were torn by a crash
 * mid-write: any page written since the last checkpoint has its latest image in the WAL.
 *
 * <p><b>Checkpoint</b>: sync the WAL, write all dirty pages and the meta page, fsync the data
 * file, then empty the WAL. Runs when the WAL grows past {@link Options#checkpointWalBytes()}
 * and on {@link #close()}.
 *
 * <h2>Simplifications</h2>
 * Deletes never merge or rebalance nodes: a leaf may become empty and stays in the tree, and
 * pages are never freed. Keys are limited to {@value #MAX_KEY_SIZE} bytes and values to
 * {@value #MAX_VALUE_SIZE} bytes, so any entry fits in a third of a page and a split always
 * produces two nodes that fit (no overflow pages).
 *
 * <p>Thread-safe: every public method is synchronized. After any exception from the underlying
 * storage, discard the instance and reopen (fail-stop).
 */
public final class BTree implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(BTree.class);

    public static final int MAX_KEY_SIZE = 256;
    public static final int MAX_VALUE_SIZE = 1024;

    private static final int META_PAGE_ID = 0;
    private static final int META_MAGIC = 0x524B5642; // "RKVB"
    private static final int META_VERSION = 1;
    private static final int META_SIZE = 28;

    /**
     * @param cachePages         maximum number of pages kept in memory between operations
     * @param fsyncPolicy        WAL fsync policy
     * @param checkpointWalBytes take a checkpoint once the WAL reaches this size
     */
    public record Options(int cachePages, FsyncPolicy fsyncPolicy, long checkpointWalBytes) {
        public static Options defaults() {
            return new Options(2048, FsyncPolicy.ALWAYS, 16L << 20);
        }
    }

    public record Entry(byte[] key, byte[] value) {
    }

    private final Options options;
    private final PageFile pages;
    private final PageCache cache;
    private final Wal wal;

    // Meta fields. Stored in the meta page at checkpoint and in every WAL record.
    private int rootPageId;
    private int pageCount;
    private long appliedIndex;

    /** Pages changed by the batch in progress; their images go into the batch's WAL record. */
    private final Set<Integer> pagesChangedInBatch = new LinkedHashSet<>();

    /** Opens (or creates) the tree stored in {@code <name>.db} and {@code <name>.wal}. */
    public static BTree open(FileSystem fileSystem, String name, Options options) {
        return new BTree(fileSystem, name, options);
    }

    private BTree(FileSystem fileSystem, String name, Options options) {
        this.options = options;
        this.cache = new PageCache(options.cachePages());
        String walName = name + ".wal";

        // The WAL is created last during initialization, so a missing WAL means the tree was never
        // fully created and nothing in the data file can have been committed.
        boolean fresh = !fileSystem.exists(walName);
        this.pages = new PageFile(fileSystem.open(name + ".db"));
        if (fresh) {
            initializeDataFile();
        }
        boolean metaPageValid = readMetaPage();

        int[] replayed = {0};
        this.wal = Wal.open(fileSystem, walName, options.fsyncPolicy(), (lsn, payload) -> {
            redo(payload);
            replayed[0]++;
        });

        if (!metaPageValid && replayed[0] == 0) {
            throw new StorageCorruptionException("meta page is corrupted and the WAL is empty");
        }
        if (replayed[0] > 0) {
            log.info("B-Tree {}: replayed {} WAL records", name, replayed[0]);
            pages.sync();
            writeMetaPage();
            pages.sync();
            wal.reset();
        }
    }

    // ---------------------------------------------------------------- public API

    public synchronized Optional<byte[]> get(byte[] key) {
        try {
            Node node = load(rootPageId);
            while (node instanceof InternalNode internal) {
                node = load(internal.children.get(internal.childIndex(key)));
            }
            return Optional.ofNullable(((LeafNode) node).get(key));
        } finally {
            cache.evictIfOverCapacity(this::writeBack);
        }
    }

    public synchronized void put(byte[] key, byte[] value) {
        write(new WriteBatch().put(key, value));
    }

    /** Deletes {@code key}; returns true if it was present. */
    public synchronized boolean delete(byte[] key) {
        boolean existed = get(key).isPresent();
        if (existed) {
            write(new WriteBatch().delete(key));
        }
        return existed;
    }

    /** Applies every operation in the batch, in order, atomically with respect to crashes. */
    public synchronized void write(WriteBatch batch) {
        for (WriteBatch.Operation operation : batch.operations()) {
            validate(operation);
        }

        long appliedIndexBefore = appliedIndex;
        for (WriteBatch.Operation operation : batch.operations()) {
            switch (operation) {
                case WriteBatch.Put put -> applyPut(put.key(), put.value());
                case WriteBatch.Delete delete -> applyDelete(rootPageId, delete.key());
            }
        }
        batch.appliedIndex().ifPresent(index -> appliedIndex = index);
        if (pagesChangedInBatch.isEmpty() && appliedIndex == appliedIndexBefore) {
            return; // nothing changed, nothing to log
        }

        long lsn = wal.append(encodeBatchRecord());
        for (int pageId : pagesChangedInBatch) {
            cache.get(pageId).lsn = lsn;
        }
        pagesChangedInBatch.clear();

        cache.evictIfOverCapacity(this::writeBack);
        if (wal.sizeBytes() >= options.checkpointWalBytes()) {
            checkpoint();
        }
    }

    /**
     * Returns up to {@code limit} entries with {@code from <= key < to}, in key order.
     * A null bound means unbounded.
     */
    public synchronized List<Entry> scan(byte[] from, byte[] to, int limit) {
        List<Entry> result = new ArrayList<>();
        try {
            if (limit > 0) {
                scan(rootPageId, from, to, limit, result);
            }
            return result;
        } finally {
            cache.evictIfOverCapacity(this::writeBack);
        }
    }

    /** The applied index stored by the most recent batch that set one (0 initially). */
    public synchronized long appliedIndex() {
        return appliedIndex;
    }

    /** Makes every completed {@link #write} durable. */
    public synchronized void sync() {
        wal.sync();
    }

    /** Writes every dirty page to the data file and empties the WAL. */
    public synchronized void checkpoint() {
        if (wal.isEmpty()) {
            return; // nothing changed since the last checkpoint
        }
        wal.sync();
        for (PageCache.Frame frame : cache.frames()) {
            if (frame.dirty) {
                writeBack(frame);
            }
        }
        writeMetaPage();
        pages.sync();
        // Only now, with every change durable in the data file, may the WAL forget them.
        wal.reset();
    }

    @Override
    public synchronized void close() {
        checkpoint();
        wal.close();
        pages.close();
    }

    // ---------------------------------------------------------------- tree operations

    private void validate(WriteBatch.Operation operation) {
        if (operation.key().length > MAX_KEY_SIZE) {
            throw new IllegalArgumentException("key longer than " + MAX_KEY_SIZE + " bytes");
        }
        if (operation instanceof WriteBatch.Put put && put.value().length > MAX_VALUE_SIZE) {
            throw new IllegalArgumentException("value longer than " + MAX_VALUE_SIZE + " bytes");
        }
    }

    private void applyPut(byte[] key, byte[] value) {
        Promotion promotion = insert(rootPageId, key, value);
        if (promotion != null) {
            // The root split: the tree grows one level taller.
            rootPageId = allocate(InternalNode.newRoot(rootPageId, promotion.separator(), promotion.rightPageId()));
        }
    }

    /** A child split; the parent must add {@code separator} with {@code rightPageId} after the child. */
    private record Promotion(byte[] separator, int rightPageId) {
    }

    /** Inserts into the subtree rooted at {@code pageId}; returns a promotion if that node split. */
    private Promotion insert(int pageId, byte[] key, byte[] value) {
        Node node = load(pageId);
        if (node instanceof LeafNode leaf) {
            leaf.put(key, value);
        } else {
            InternalNode internal = (InternalNode) node;
            int childIndex = internal.childIndex(key);
            Promotion childPromotion = insert(internal.children.get(childIndex), key, value);
            if (childPromotion == null) {
                return null;
            }
            internal.insertSplitChild(childIndex, childPromotion.separator(), childPromotion.rightPageId());
        }
        markChanged(pageId);

        if (!node.isOverfull()) {
            return null;
        }
        Node.Split split = node.split();
        return new Promotion(split.separator(), allocate(split.right()));
    }

    private void applyDelete(int pageId, byte[] key) {
        Node node = load(pageId);
        while (node instanceof InternalNode internal) {
            pageId = internal.children.get(internal.childIndex(key));
            node = load(pageId);
        }
        if (((LeafNode) node).remove(key)) {
            markChanged(pageId);
        }
    }

    private void scan(int pageId, byte[] from, byte[] to, int limit, List<Entry> result) {
        Node node = load(pageId);
        if (node instanceof LeafNode leaf) {
            int start = from == null ? 0 : leaf.lowerBound(from);
            for (int i = start; i < leaf.size() && result.size() < limit; i++) {
                if (to != null && Bytes.compare(leaf.keys.get(i), to) >= 0) {
                    return;
                }
                result.add(new Entry(leaf.keys.get(i), leaf.values.get(i)));
            }
            return;
        }
        InternalNode internal = (InternalNode) node;
        int first = from == null ? 0 : internal.childIndex(from);
        for (int i = first; i < internal.children.size() && result.size() < limit; i++) {
            // Child i holds keys >= keys[i-1]; once that bound reaches 'to', we are done.
            if (i > 0 && to != null && Bytes.compare(internal.keys.get(i - 1), to) >= 0) {
                return;
            }
            scan(internal.children.get(i), from, to, limit, result);
        }
    }

    // ---------------------------------------------------------------- pages and the cache

    private Node load(int pageId) {
        PageCache.Frame frame = cache.get(pageId);
        if (frame == null) {
            frame = new PageCache.Frame(pageId, Node.decode(pages.read(pageId)));
            cache.add(frame);
        }
        return frame.node;
    }

    private void markChanged(int pageId) {
        cache.get(pageId).dirty = true;
        pagesChangedInBatch.add(pageId);
    }

    private int allocate(Node node) {
        int pageId = pageCount++;
        cache.add(new PageCache.Frame(pageId, node));
        markChanged(pageId);
        return pageId;
    }

    /** Writes a dirty page to the data file, obeying the write-ahead rule. */
    private void writeBack(PageCache.Frame frame) {
        if (frame.lsn > wal.durableLsn()) {
            wal.sync(); // WAL first: the page's latest image must be durable before the page is.
        }
        pages.write(frame.pageId, frame.node.encode());
        frame.dirty = false;
    }

    // ---------------------------------------------------------------- WAL records and the meta page

    /** WAL record: appliedIndex (8) | rootPageId (4) | pageCount (4) | n (4) | n × (pageId (4) | length (4) | image). */
    private byte[] encodeBatchRecord() {
        List<byte[]> images = new ArrayList<>();
        int size = 20;
        for (int pageId : pagesChangedInBatch) {
            byte[] image = cache.get(pageId).node.encode();
            images.add(image);
            size += 8 + image.length;
        }
        ByteBuffer record = ByteBuffer.allocate(size);
        record.putLong(appliedIndex).putInt(rootPageId).putInt(pageCount).putInt(images.size());
        int i = 0;
        for (int pageId : pagesChangedInBatch) {
            byte[] image = images.get(i++);
            record.putInt(pageId).putInt(image.length).put(image);
        }
        return record.array();
    }

    /** Recovery: re-apply one WAL record directly to the data file. */
    private void redo(byte[] payload) {
        ByteBuffer record = ByteBuffer.wrap(payload);
        appliedIndex = record.getLong();
        rootPageId = record.getInt();
        pageCount = record.getInt();
        int count = record.getInt();
        for (int i = 0; i < count; i++) {
            int pageId = record.getInt();
            byte[] image = new byte[record.getInt()];
            record.get(image);
            pages.write(pageId, image);
        }
    }

    private void initializeDataFile() {
        pages.truncate();
        rootPageId = 1;
        pageCount = 2;
        appliedIndex = 0;
        pages.write(rootPageId, new LeafNode().encode());
        writeMetaPage();
        pages.sync();
    }

    /** Meta page: magic (4) | version (4) | rootPageId (4) | pageCount (4) | appliedIndex (8) | crc32 (4). */
    private void writeMetaPage() {
        ByteBuffer meta = ByteBuffer.allocate(META_SIZE);
        meta.putInt(META_MAGIC).putInt(META_VERSION).putInt(rootPageId).putInt(pageCount).putLong(appliedIndex);
        meta.putInt(crc(meta.array(), META_SIZE - 4));
        pages.write(META_PAGE_ID, meta.array());
    }

    /**
     * Loads the meta fields; returns false if the meta page is damaged. That can happen if a
     * checkpoint was interrupted while writing it, in which case the WAL still holds every
     * record since the previous checkpoint and replaying it restores the meta fields.
     */
    private boolean readMetaPage() {
        ByteBuffer meta;
        try {
            meta = ByteBuffer.wrap(pages.read(META_PAGE_ID));
        } catch (StorageCorruptionException e) {
            return false;
        }
        int magic = meta.getInt();
        int version = meta.getInt();
        int root = meta.getInt();
        int count = meta.getInt();
        long applied = meta.getLong();
        int storedCrc = meta.getInt();
        if (magic != META_MAGIC || version != META_VERSION || storedCrc != crc(meta.array(), META_SIZE - 4)) {
            return false;
        }
        rootPageId = root;
        pageCount = count;
        appliedIndex = applied;
        return true;
    }

    private static int crc(byte[] bytes, int length) {
        CRC32 crc = new CRC32();
        crc.update(bytes, 0, length);
        return (int) crc.getValue();
    }

    // ---------------------------------------------------------------- test support

    /** Number of levels (1 = the root is a leaf). */
    synchronized int depth() {
        int depth = 1;
        Node node = load(rootPageId);
        while (node instanceof InternalNode internal) {
            node = load(internal.children.get(0));
            depth++;
        }
        cache.evictIfOverCapacity(this::writeBack);
        return depth;
    }

    /** Throws if any structural invariant is broken: ordering, separator bounds, page size, equal leaf depth. */
    synchronized void checkInvariants() {
        int[] leafDepth = {-1};
        checkSubtree(rootPageId, null, null, 1, leafDepth);
        cache.evictIfOverCapacity(this::writeBack);
    }

    private void checkSubtree(int pageId, byte[] low, byte[] high, int depth, int[] leafDepth) {
        Node node = load(pageId);
        if (node.encodedSize() > PageFile.PAGE_SIZE) {
            throw new IllegalStateException("page " + pageId + " is overfull");
        }
        List<byte[]> keys = node instanceof LeafNode leaf ? leaf.keys : ((InternalNode) node).keys;
        for (int i = 0; i < keys.size(); i++) {
            byte[] key = keys.get(i);
            if (i > 0 && Bytes.compare(keys.get(i - 1), key) >= 0) {
                throw new IllegalStateException("keys out of order in page " + pageId);
            }
            if ((low != null && Bytes.compare(key, low) < 0) || (high != null && Bytes.compare(key, high) >= 0)) {
                throw new IllegalStateException("key outside its separator bounds in page " + pageId);
            }
        }
        if (node instanceof InternalNode internal) {
            if (internal.children.size() != keys.size() + 1) {
                throw new IllegalStateException("child count mismatch in page " + pageId);
            }
            for (int i = 0; i < internal.children.size(); i++) {
                byte[] childLow = i == 0 ? low : keys.get(i - 1);
                byte[] childHigh = i == keys.size() ? high : keys.get(i);
                checkSubtree(internal.children.get(i), childLow, childHigh, depth + 1, leafDepth);
            }
        } else if (leafDepth[0] == -1) {
            leafDepth[0] = depth;
        } else if (leafDepth[0] != depth) {
            throw new IllegalStateException("leaves at different depths");
        }
    }
}
