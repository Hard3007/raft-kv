package io.github.hard3007.raftkv.storage;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A leaf holds sorted key/value pairs.
 *
 * <p>Body encoding: count × (keyLength (2) | key | valueLength (2) | value).
 */
final class LeafNode extends Node {

    final List<byte[]> keys = new ArrayList<>();
    final List<byte[]> values = new ArrayList<>();

    int size() {
        return keys.size();
    }

    /** Index of {@code key}, or {@code -(insertionPoint) - 1} if absent (as in Collections.binarySearch). */
    int find(byte[] key) {
        return Collections.binarySearch(keys, key, Bytes.COMPARATOR);
    }

    /** Index of the first key that is greater than or equal to {@code key}. */
    int lowerBound(byte[] key) {
        int index = find(key);
        return index >= 0 ? index : -index - 1;
    }

    byte[] get(byte[] key) {
        int index = find(key);
        return index >= 0 ? values.get(index) : null;
    }

    void put(byte[] key, byte[] value) {
        int index = find(key);
        if (index >= 0) {
            values.set(index, value);
        } else {
            keys.add(-index - 1, key);
            values.add(-index - 1, value);
        }
    }

    /** Returns true if the key was present. */
    boolean remove(byte[] key) {
        int index = find(key);
        if (index < 0) {
            return false;
        }
        keys.remove(index);
        values.remove(index);
        return true;
    }

    private int entrySize(int index) {
        return 2 + keys.get(index).length + 2 + values.get(index).length;
    }

    @Override
    int encodedSize() {
        int size = HEADER_SIZE;
        for (int i = 0; i < keys.size(); i++) {
            size += entrySize(i);
        }
        return size;
    }

    /**
     * Moves the upper half (by bytes, not by count) into a new right sibling. The separator is
     * the right sibling's first key. Because a single entry is at most a third of a page
     * ({@link BTree#MAX_KEY_SIZE} and {@link BTree#MAX_VALUE_SIZE} guarantee this), both halves
     * of an overfull leaf fit in a page.
     */
    @Override
    Split split() {
        int half = (encodedSize() - HEADER_SIZE) / 2;
        int leftBytes = 0;
        int splitIndex = 0;
        while (splitIndex < keys.size() - 1 && leftBytes + entrySize(splitIndex) <= half) {
            leftBytes += entrySize(splitIndex);
            splitIndex++;
        }
        splitIndex = Math.max(splitIndex, 1);

        LeafNode right = new LeafNode();
        List<byte[]> movedKeys = keys.subList(splitIndex, keys.size());
        List<byte[]> movedValues = values.subList(splitIndex, values.size());
        right.keys.addAll(movedKeys);
        right.values.addAll(movedValues);
        movedKeys.clear();
        movedValues.clear();
        return new Split(right.keys.get(0), right);
    }

    @Override
    void encodeInto(ByteBuffer buffer) {
        buffer.put(LEAF);
        buffer.putShort((short) keys.size());
        for (int i = 0; i < keys.size(); i++) {
            writeBytes(buffer, keys.get(i));
            writeBytes(buffer, values.get(i));
        }
    }

    static LeafNode decodeBody(ByteBuffer buffer, int count) {
        LeafNode leaf = new LeafNode();
        for (int i = 0; i < count; i++) {
            leaf.keys.add(readBytes(buffer));
            leaf.values.add(readBytes(buffer));
        }
        return leaf;
    }
}
