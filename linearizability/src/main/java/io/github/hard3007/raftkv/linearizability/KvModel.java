package io.github.hard3007.raftkv.linearizability;

import java.util.Objects;

/**
 * The sequential specification of one key: a register that is either absent (null) or holds a
 * string. {@link #step} applies an operation to a state and says whether the result the client
 * observed is possible from that state.
 */
final class KvModel {

    /** Applying one operation: was its observed result possible, and what is the new state. */
    record Step(boolean possible, String state) {
        static final Step IMPOSSIBLE = new Step(false, null);
    }

    private KvModel() {
    }

    static Step step(String state, Operation operation) {
        return switch (operation.kind()) {
            case GET -> Objects.equals(state, ((Operation.Result.Read) operation.result()).value())
                    ? new Step(true, state) : Step.IMPOSSIBLE;
            case PUT -> new Step(true, operation.argument());
            case DELETE -> {
                // A pending delete's "existed" was never observed, so any state is fine.
                boolean possible = operation.isPending()
                        || ((Operation.Result.Deleted) operation.result()).existed() == (state != null);
                yield possible ? new Step(true, null) : Step.IMPOSSIBLE;
            }
        };
    }
}
