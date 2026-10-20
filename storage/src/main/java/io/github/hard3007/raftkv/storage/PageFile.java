package io.github.hard3007.raftkv.storage;

import java.util.Arrays;

/** The B-Tree data file: an array of fixed-size pages addressed by page id. */
final class PageFile implements AutoCloseable {

    static final int PAGE_SIZE = 4096;

    private final StorageFile file;

    PageFile(StorageFile file) {
        this.file = file;
    }

    byte[] read(int pageId) {
        long position = (long) pageId * PAGE_SIZE;
        if (position + PAGE_SIZE > file.size()) {
            throw new StorageCorruptionException("page " + pageId + " is past the end of the data file");
        }
        byte[] page = new byte[PAGE_SIZE];
        file.read(position, page, 0, PAGE_SIZE);
        return page;
    }

    /** Writes {@code image} (at most one page; shorter images are zero-padded) to page {@code pageId}. */
    void write(int pageId, byte[] image) {
        if (image.length > PAGE_SIZE) {
            throw new IllegalArgumentException("page image too large: " + image.length);
        }
        byte[] page = image.length == PAGE_SIZE ? image : Arrays.copyOf(image, PAGE_SIZE);
        file.write((long) pageId * PAGE_SIZE, page, 0, PAGE_SIZE);
    }

    void truncate() {
        file.truncate(0);
    }

    void sync() {
        file.force();
    }

    @Override
    public void close() {
        file.close();
    }
}
