package io.github.hard3007.raftkv.kv;

import java.util.stream.LongStream;

/** Seeds for simulated-cluster tests: {@code ./gradlew :kv:test -PkvSeeds=50} (default 3). */
final class Seeds {

    private Seeds() {
    }

    static LongStream seeds() {
        return LongStream.range(1, Long.getLong("kv.seeds", 3) + 1);
    }
}
