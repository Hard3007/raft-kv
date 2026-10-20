package io.github.hard3007.raftkv.storage;

import java.util.Arrays;
import java.util.Comparator;

/** Keys are compared as unsigned bytes, lexicographically (so 0xFF sorts after 0x01). */
public final class Bytes {

    public static final Comparator<byte[]> COMPARATOR = Arrays::compareUnsigned;

    private Bytes() {
    }

    public static int compare(byte[] a, byte[] b) {
        return Arrays.compareUnsigned(a, b);
    }
}
