package io.github.hard3007.raftkv.raft;

import java.util.stream.LongStream;

/**
 * Seeds for simulated-cluster tests. Each test runs once per seed; set the count with
 * {@code ./gradlew :raft:test -PraftSeeds=50} (default 3). A failing seed replays exactly.
 */
final class Seeds {

    private Seeds() {
    }

    static LongStream seeds() {
        return LongStream.range(1, Long.getLong("raft.seeds", 3) + 1);
    }
}
