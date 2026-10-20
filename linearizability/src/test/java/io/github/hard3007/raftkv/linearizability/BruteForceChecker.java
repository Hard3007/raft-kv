package io.github.hard3007.raftkv.linearizability;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * An obviously-correct (and exponentially slow) linearizability checker, used only to test
 * {@link LinearizabilityChecker}. It shares no logic with it: no per-key partitioning, no event
 * list, no memoization. It tries every ordering of the whole history that respects real time
 * (A must come before B if A returned before B was invoked) and replays each against a map of
 * all keys. Pending operations' results are not checked; ordering one last is the same as "it
 * never took effect".
 */
final class BruteForceChecker {

    static final int MAX_OPERATIONS = 9;

    private BruteForceChecker() {
    }

    static boolean isLinearizable(List<Operation> history) {
        if (history.size() > MAX_OPERATIONS) {
            throw new IllegalArgumentException("brute force is only for tiny histories");
        }
        return search(history, new boolean[history.size()], new HashMap<>(), 0);
    }

    /** Tries every operation that may come next, recursively. */
    private static boolean search(List<Operation> history, boolean[] placed, Map<String, String> state, int count) {
        if (count == history.size()) {
            return true;
        }
        for (int i = 0; i < history.size(); i++) {
            if (placed[i] || !allPredecessorsPlaced(history, placed, history.get(i))) {
                continue;
            }
            Operation operation = history.get(i);
            String before = state.get(operation.key());
            if (!resultMatches(operation, before)) {
                continue;
            }
            placed[i] = true;
            applyTo(state, operation);
            if (search(history, placed, state, count + 1)) {
                return true;
            }
            placed[i] = false;
            if (before == null) {
                state.remove(operation.key());
            } else {
                state.put(operation.key(), before);
            }
        }
        return false;
    }

    private static boolean allPredecessorsPlaced(List<Operation> history, boolean[] placed, Operation candidate) {
        for (int j = 0; j < history.size(); j++) {
            if (!placed[j] && history.get(j).returnTime() < candidate.invokeTime()) {
                return false;
            }
        }
        return true;
    }

    private static boolean resultMatches(Operation operation, String current) {
        if (operation.isPending()) {
            return true;
        }
        return switch (operation.result()) {
            case Operation.Result.Read read -> read.value() == null ? current == null : read.value().equals(current);
            case Operation.Result.Deleted deleted -> deleted.existed() == (current != null);
            case Operation.Result.Written written -> true;
            case Operation.Result.Pending pending -> true;
        };
    }

    private static void applyTo(Map<String, String> state, Operation operation) {
        switch (operation.kind()) {
            case PUT -> state.put(operation.key(), operation.argument());
            case DELETE -> state.remove(operation.key());
            case GET -> { }
        }
    }
}
