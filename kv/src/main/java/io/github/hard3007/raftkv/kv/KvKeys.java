package io.github.hard3007.raftkv.kv;

import io.github.hard3007.raftkv.storage.BTree;
import java.nio.ByteBuffer;

/**
 * How the KV state machine lays out its data in the B-Tree. Two key spaces share one tree,
 * separated by a one-byte prefix:
 * <pre>
 *   'D' + user key             -> user value                 (client data)
 *   'S' + clientId (8 bytes)   -> lastSequence (8) | existed (1)   (session table)
 * </pre>
 * Both stay inside the B-Tree's limits: user keys are capped one byte below
 * {@link BTree#MAX_KEY_SIZE} to leave room for the prefix, and a session row is 9 + 9 bytes.
 */
final class KvKeys {

    static final byte DATA_PREFIX = 'D';
    static final byte SESSION_PREFIX = 'S';

    /** Longest user key the API accepts. */
    static final int MAX_USER_KEY_SIZE = BTree.MAX_KEY_SIZE - 1;
    /** Longest user value the API accepts. */
    static final int MAX_USER_VALUE_SIZE = BTree.MAX_VALUE_SIZE;

    private KvKeys() {
    }

    static byte[] dataKey(byte[] userKey) {
        byte[] key = new byte[userKey.length + 1];
        key[0] = DATA_PREFIX;
        System.arraycopy(userKey, 0, key, 1, userKey.length);
        return key;
    }

    static byte[] sessionKey(long clientId) {
        return ByteBuffer.allocate(9).put(SESSION_PREFIX).putLong(clientId).array();
    }

    /** One client's session: its latest applied sequence number and that write's reply. */
    record Session(long lastSequence, boolean existed) {

        byte[] encode() {
            return ByteBuffer.allocate(9).putLong(lastSequence).put((byte) (existed ? 1 : 0)).array();
        }

        static Session decode(byte[] bytes) {
            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            return new Session(buffer.getLong(), buffer.get() == 1);
        }
    }
}
