package io.github.hard3007.raftkv.storage;

import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.function.Consumer;

/**
 * LRU cache of decoded B-Tree nodes, one {@link Frame} per page.
 *
 * <p>Eviction only happens when the B-Tree calls {@link #evictIfOverCapacity} between
 * operations, never in the middle of one. So while an operation runs, every node it touched stays
 * in memory, and a half-finished change can never be written to disk. The cache may go over
 * capacity during a large operation.
 */
final class PageCache {

    static final class Frame {
        final int pageId;
        final Node node;
        /** The in-memory node differs from the page on disk. */
        boolean dirty;
        /** LSN of the WAL record holding this page's latest image (meaningful while dirty). */
        long lsn;

        Frame(int pageId, Node node) {
            this.pageId = pageId;
            this.node = node;
        }
    }

    private final int capacity;
    /** Access-ordered: iteration starts at the least recently used frame. */
    private final LinkedHashMap<Integer, Frame> frames = new LinkedHashMap<>(16, 0.75f, true);

    PageCache(int capacity) {
        if (capacity < 1) {
            throw new IllegalArgumentException("cache capacity must be at least 1 page");
        }
        this.capacity = capacity;
    }

    /** Returns the cached frame (marking it most recently used), or null. */
    Frame get(int pageId) {
        return frames.get(pageId);
    }

    void add(Frame frame) {
        frames.put(frame.pageId, frame);
    }

    Collection<Frame> frames() {
        return frames.values();
    }

    int size() {
        return frames.size();
    }

    /** Evicts least recently used frames, passing dirty ones to {@code writeBack} first. */
    void evictIfOverCapacity(Consumer<Frame> writeBack) {
        Iterator<Frame> eldestFirst = frames.values().iterator();
        while (frames.size() > capacity && eldestFirst.hasNext()) {
            Frame frame = eldestFirst.next();
            if (frame.dirty) {
                writeBack.accept(frame);
            }
            eldestFirst.remove();
        }
    }

    void clear() {
        frames.clear();
    }
}
