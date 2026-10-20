package io.github.hard3007.raftkv.linearizability;

import java.util.List;

/**
 * The outcome of a check. For {@link Verdict#NOT_LINEARIZABLE} or {@link Verdict#INCONCLUSIVE},
 * {@code key} and {@code operations} identify the offending per-key sub-history (for debugging).
 */
public record CheckResult(Verdict verdict, String key, List<Operation> operations) {

    static CheckResult linearizable() {
        return new CheckResult(Verdict.LINEARIZABLE, null, List.of());
    }
}
