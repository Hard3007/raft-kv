package io.github.hard3007.raftkv.linearizability;

import java.util.function.LongFunction;

/**
 * One client operation in a history: what was asked, when, and what came back.
 *
 * <p>Times are totally ordered and unique per event in simulation (virtual clock plus the order in
 * which events happened); operation A precedes B in real time iff {@code A.returnTime < B.invokeTime}.
 * A {@link Result.Pending} operation has {@code returnTime == PENDING}: its outcome is unknown, so
 * it may take effect at any point after its invocation, or never.
 */
public record Operation(int id, int client, Kind kind, String key, String argument, long invokeTime, long returnTime,
        Result result) {

    /** Return time of an operation whose outcome is unknown: "after everything". */
    public static final long PENDING = Long.MAX_VALUE;

    public enum Kind { GET, PUT, DELETE }

    /** What the client observed. */
    public sealed interface Result permits Result.Read, Result.Written, Result.Deleted, Result.Pending {

        /** A GET returned this value; null means the key was absent. */
        record Read(String value) implements Result {
        }

        /** A PUT took effect. */
        record Written() implements Result {
        }

        /** A DELETE took effect; {@code existed} says whether the key was there. */
        record Deleted(boolean existed) implements Result {
        }

        /** Unknown outcome (only writes are ever pending). */
        record Pending() implements Result {
        }
    }

    public Operation {
        if ((result instanceof Result.Pending) != (returnTime == PENDING)) {
            throw new IllegalArgumentException("pending operations, and only those, have no return time");
        }
        if (result instanceof Result.Pending && kind == Kind.GET) {
            throw new IllegalArgumentException("reads with unknown outcome are left out of histories");
        }
        if (returnTime != PENDING && returnTime < invokeTime) {
            throw new IllegalArgumentException("returned before it was invoked");
        }
    }

    public boolean isPending() {
        return result instanceof Result.Pending;
    }

    @Override
    public String toString() {
        return describe(Long::toString);
    }

    /** Like {@link #toString()}, with times rendered by {@code formatTime}. */
    public String describe(LongFunction<String> formatTime) {
        String call = switch (kind) {
            case GET -> "get(" + key + ")";
            case PUT -> "put(" + key + "=" + argument + ")";
            case DELETE -> "delete(" + key + ")";
        };
        String outcome = switch (result) {
            case Result.Read read -> read.value() == null ? "absent" : read.value();
            case Result.Written written -> "ok";
            case Result.Deleted deleted -> "existed=" + deleted.existed();
            case Result.Pending pending -> "pending";
        };
        String end = isPending() ? "inf" : formatTime.apply(returnTime);
        return "#" + id + " c" + client + " [" + formatTime.apply(invokeTime) + ", " + end + "] " + call + " -> "
                + outcome;
    }
}
