package io.github.hard3007.raftkv.linearizability;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Finds a short explanation of a violation: the earliest moment T at which the history, as it
 * looked at time T, was already not linearizable.
 *
 * <p>"The history as it looked at T" keeps operations invoked before T. Of those, ones still
 * running at T are cut: writes become pending (their effect may or may not have happened), reads
 * are dropped. Such a cut is linearizable whenever the full history is, so the earliest
 * failing T is found by binary search, and the operation returning at T is the one that
 * completed the violation.
 */
public final class Witness {

    private Witness() {
    }

    /** The cut of one key's history at the earliest failing moment. Empty if it never fails. */
    public static List<Operation> earliestViolation(List<Operation> keyHistory, Duration timeoutPerCheck) {
        List<Long> returnTimes = keyHistory.stream().filter(op -> !op.isPending()).map(Operation::returnTime)
                .sorted().distinct().toList();
        LinearizabilityChecker checker = new LinearizabilityChecker(timeoutPerCheck);
        int low = 0;
        int high = returnTimes.size() - 1;
        int earliest = -1;
        while (low <= high) {
            int middle = (low + high) >>> 1;
            if (checker.check(cutAt(keyHistory, returnTimes.get(middle))).verdict() == Verdict.NOT_LINEARIZABLE) {
                earliest = middle;
                high = middle - 1;
            } else {
                low = middle + 1;
            }
        }
        return earliest == -1 ? List.of() : cutAt(keyHistory, returnTimes.get(earliest));
    }

    static List<Operation> cutAt(List<Operation> history, long time) {
        List<Operation> cut = new ArrayList<>();
        for (Operation op : history) {
            if (op.invokeTime() > time) {
                continue;
            }
            if (op.isPending() || op.returnTime() <= time) {
                cut.add(op);
            } else if (op.kind() != Operation.Kind.GET) {
                cut.add(new Operation(op.id(), op.client(), op.kind(), op.key(), op.argument(), op.invokeTime(),
                        Operation.PENDING, new Operation.Result.Pending()));
            }
        }
        return cut;
    }
}
