package io.github.hard3007.raftkv.linearizability;

import java.util.ArrayList;
import java.util.List;

/** A tiny builder for hand-written histories. Times are plain integers. */
final class Histories {

    private final List<Operation> operations = new ArrayList<>();

    Histories put(String key, String value, long invoke, long ret) {
        return add(Operation.Kind.PUT, key, value, invoke, ret, new Operation.Result.Written());
    }

    Histories get(String key, String observed, long invoke, long ret) {
        return add(Operation.Kind.GET, key, null, invoke, ret, new Operation.Result.Read(observed));
    }

    Histories delete(String key, boolean existed, long invoke, long ret) {
        return add(Operation.Kind.DELETE, key, null, invoke, ret, new Operation.Result.Deleted(existed));
    }

    Histories pendingPut(String key, String value, long invoke) {
        return add(Operation.Kind.PUT, key, value, invoke, Operation.PENDING, new Operation.Result.Pending());
    }

    Histories pendingDelete(String key, long invoke) {
        return add(Operation.Kind.DELETE, key, null, invoke, Operation.PENDING, new Operation.Result.Pending());
    }

    private Histories add(Operation.Kind kind, String key, String argument, long invoke, long ret,
            Operation.Result result) {
        int id = operations.size();
        operations.add(new Operation(id, id, kind, key, argument, invoke, ret, result));
        return this;
    }

    List<Operation> build() {
        return List.copyOf(operations);
    }
}
