package io.github.hard3007.raftkv.storage;

import java.nio.ByteBuffer;

/**
 * A B-Tree node as it lives in memory. Each node is stored in exactly one 4 KB page.
 *
 * <p>Page encoding: type (1 byte) | entry count (2 bytes) | type-specific body
 * (see {@link LeafNode} and {@link InternalNode}). A node is split as soon as its encoding
 * exceeds {@link PageFile#PAGE_SIZE}.
 */
abstract sealed class Node permits LeafNode, InternalNode {

    static final byte LEAF = 1;
    static final byte INTERNAL = 2;
    static final int HEADER_SIZE = 3;

    /** The result of splitting a node: everything at or above {@code separator} moved to {@code right}. */
    record Split(byte[] separator, Node right) {
    }

    abstract int encodedSize();

    abstract void encodeInto(ByteBuffer buffer);

    abstract Split split();

    boolean isOverfull() {
        return encodedSize() > PageFile.PAGE_SIZE;
    }

    /** Encodes this node; the result is at most one page long (callers split first). */
    final byte[] encode() {
        ByteBuffer buffer = ByteBuffer.allocate(encodedSize());
        encodeInto(buffer);
        return buffer.array();
    }

    static Node decode(byte[] page) {
        ByteBuffer buffer = ByteBuffer.wrap(page);
        byte type = buffer.get();
        int count = Short.toUnsignedInt(buffer.getShort());
        return switch (type) {
            case LEAF -> LeafNode.decodeBody(buffer, count);
            case INTERNAL -> InternalNode.decodeBody(buffer, count);
            default -> throw new StorageCorruptionException("unknown page type " + type);
        };
    }

    static byte[] readBytes(ByteBuffer buffer) {
        byte[] bytes = new byte[Short.toUnsignedInt(buffer.getShort())];
        buffer.get(bytes);
        return bytes;
    }

    static void writeBytes(ByteBuffer buffer, byte[] bytes) {
        buffer.putShort((short) bytes.length);
        buffer.put(bytes);
    }
}
