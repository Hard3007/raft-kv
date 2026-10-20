package io.github.hard3007.raftkv.storage;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * An internal node with keys k[0..n-1] and children c[0..n]. Child c[i] holds the keys in
 * [k[i-1], k[i]): c[0] holds everything below k[0], and c[n] everything at or above k[n-1].
 *
 * <p>Body encoding: child[0] (4) | count × (keyLength (2) | key | child (4)).
 */
final class InternalNode extends Node {

    final List<byte[]> keys = new ArrayList<>();
    final List<Integer> children = new ArrayList<>();

    /** A new root above a split: {@code left} keeps keys below {@code separator}. */
    static InternalNode newRoot(int left, byte[] separator, int right) {
        InternalNode root = new InternalNode();
        root.children.add(left);
        root.keys.add(separator);
        root.children.add(right);
        return root;
    }

    /** Index of the child whose range contains {@code key}: the number of keys that are at or below it. */
    int childIndex(byte[] key) {
        int index = Collections.binarySearch(keys, key, Bytes.COMPARATOR);
        return index >= 0 ? index + 1 : -index - 1;
    }

    /** After child {@code childIndex} split, records its new right sibling. */
    void insertSplitChild(int childIndex, byte[] separator, int rightPageId) {
        keys.add(childIndex, separator);
        children.add(childIndex + 1, rightPageId);
    }

    private int entrySize(int keyIndex) {
        return 2 + keys.get(keyIndex).length + 4;
    }

    @Override
    int encodedSize() {
        int size = HEADER_SIZE + 4;
        for (int i = 0; i < keys.size(); i++) {
            size += entrySize(i);
        }
        return size;
    }

    /**
     * Splits around a middle key (by bytes). The middle key moves up to the parent as the
     * separator and is kept in neither half: left keeps k[0..m-1] and c[0..m], right gets
     * k[m+1..] and c[m+1..].
     */
    @Override
    Split split() {
        int half = (encodedSize() - HEADER_SIZE - 4) / 2;
        int leftBytes = 0;
        int middle = 0;
        while (middle < keys.size() - 2 && leftBytes + entrySize(middle) <= half) {
            leftBytes += entrySize(middle);
            middle++;
        }
        middle = Math.max(middle, 1);

        byte[] separator = keys.get(middle);
        InternalNode right = new InternalNode();
        List<byte[]> movedKeys = keys.subList(middle + 1, keys.size());
        List<Integer> movedChildren = children.subList(middle + 1, children.size());
        right.keys.addAll(movedKeys);
        right.children.addAll(movedChildren);
        movedKeys.clear();
        movedChildren.clear();
        keys.remove(middle);
        return new Split(separator, right);
    }

    @Override
    void encodeInto(ByteBuffer buffer) {
        buffer.put(INTERNAL);
        buffer.putShort((short) keys.size());
        buffer.putInt(children.get(0));
        for (int i = 0; i < keys.size(); i++) {
            writeBytes(buffer, keys.get(i));
            buffer.putInt(children.get(i + 1));
        }
    }

    static InternalNode decodeBody(ByteBuffer buffer, int count) {
        InternalNode node = new InternalNode();
        node.children.add(buffer.getInt());
        for (int i = 0; i < count; i++) {
            node.keys.add(readBytes(buffer));
            node.children.add(buffer.getInt());
        }
        return node;
    }
}
