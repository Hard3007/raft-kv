package io.github.hard3007.raftkv.linearizability;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Decides whether a key-value history is linearizable, using the Wing-Gong-Lowe search
 * (Wing and Gong 1993, with Lowe's 2017 memoization, as in the Porcupine checker).
 *
 * <p><b>Per-key partitioning.</b> Linearizability is compositional (Herlihy and Wing): a history
 * is linearizable iff each object's sub-history is. Every operation touches one key, so each key is
 * checked on its own. That shrinks the search space exponentially.
 *
 * <p><b>The search, for one key.</b> All call and return events go into one list in time order.
 * The search walks it, keeping a model state and a set of operations already linearized:
 * <ul>
 *   <li>at a <i>call</i>: try linearizing that operation now. If the model accepts its result,
 *       remove its call and return from the list, push it on a stack, and restart from the front.
 *       Otherwise move on to the next event;
 *   <li>at a <i>return</i>: an operation that already returned must have been linearized before
 *       this point, and it was not. So undo the most recent choice (pop, put its events back) and try
 *       the next candidate after it. If there is nothing to undo, the history is not linearizable.
 * </ul>
 * The list emptying means every operation found a valid place: linearizable.
 *
 * <p><b>Memoization.</b> Reaching the same (set of linearized operations, model state) twice can
 * only repeat earlier work, so such configurations are skipped.
 *
 * <p><b>Pending operations</b> (unknown outcome) have their return at infinity, so they never
 * force a backtrack, and their results are not checked. Linearizing one at the very end is the
 * same as "it never took effect", so "may take effect at any point after invocation, or never"
 * is covered.
 *
 * <p>Equal timestamps count as concurrent (calls sort before returns), which can only allow more
 * orderings, never report a false violation.
 */
public final class LinearizabilityChecker {

    private final Duration timeout;

    public LinearizabilityChecker(Duration timeout) {
        this.timeout = timeout;
    }

    public CheckResult check(List<Operation> history) {
        long deadline = System.nanoTime() + timeout.toNanos();
        Map<String, List<Operation>> byKey = new TreeMap<>();
        for (Operation operation : history) {
            byKey.computeIfAbsent(operation.key(), k -> new ArrayList<>()).add(operation);
        }

        CheckResult inconclusive = null;
        for (Map.Entry<String, List<Operation>> entry : byKey.entrySet()) {
            Verdict verdict = checkOneKey(entry.getValue(), deadline);
            if (verdict == Verdict.NOT_LINEARIZABLE) {
                return new CheckResult(verdict, entry.getKey(), entry.getValue()); // definite; stop here
            }
            if (verdict == Verdict.INCONCLUSIVE && inconclusive == null) {
                inconclusive = new CheckResult(verdict, entry.getKey(), entry.getValue());
            }
        }
        return inconclusive != null ? inconclusive : CheckResult.linearizable();
    }

    // ------------------------------------------------------------------ one key

    /** A call or return event, in a doubly linked list that the search edits in place. */
    private static final class Event {
        final Operation operation;
        final int index; // the operation's position in this key's sub-history, for the bit set
        final boolean isCall;
        final long time;
        Event match; // call <-> return
        Event prev;
        Event next;

        Event(Operation operation, int index, boolean isCall, long time) {
            this.operation = operation;
            this.index = index;
            this.isCall = isCall;
            this.time = time;
        }
    }

    /** A linearization choice that can be undone. */
    private record Choice(Event call, String stateBefore) {
    }

    /** A configuration already explored. */
    private record Configuration(BitSet linearized, String state) {
    }

    private static Verdict checkOneKey(List<Operation> operations, long deadline) {
        Event head = buildEventList(operations);
        Deque<Choice> stack = new ArrayDeque<>();
        Set<Configuration> explored = new HashSet<>();
        BitSet linearized = new BitSet(operations.size());
        String state = null; // every key starts absent
        Event event = head.next;
        long steps = 0;

        while (head.next != null) {
            if (++steps % 4096 == 0 && System.nanoTime() > deadline) {
                return Verdict.INCONCLUSIVE;
            }
            if (event.isCall) {
                KvModel.Step step = KvModel.step(state, event.operation);
                if (step.possible()) {
                    BitSet next = (BitSet) linearized.clone();
                    next.set(event.index);
                    if (explored.add(new Configuration(next, step.state()))) {
                        stack.push(new Choice(event, state));
                        state = step.state();
                        linearized = next;
                        lift(event);
                        event = head.next;
                        continue;
                    }
                }
                event = event.next;
            } else {
                // A return: its operation had to be linearized already. Undo the latest choice.
                if (stack.isEmpty()) {
                    return Verdict.NOT_LINEARIZABLE;
                }
                Choice choice = stack.pop();
                state = choice.stateBefore();
                linearized = (BitSet) linearized.clone();
                linearized.clear(choice.call().index);
                unlift(choice.call());
                event = choice.call().next;
            }
        }
        return Verdict.LINEARIZABLE;
    }

    /** Builds head -> events in time order (calls before returns on ties); returns the head sentinel. */
    private static Event buildEventList(List<Operation> operations) {
        List<Event> events = new ArrayList<>();
        for (int i = 0; i < operations.size(); i++) {
            Operation operation = operations.get(i);
            Event call = new Event(operation, i, true, operation.invokeTime());
            Event ret = new Event(operation, i, false, operation.returnTime());
            call.match = ret;
            ret.match = call;
            events.add(call);
            events.add(ret);
        }
        events.sort(Comparator.<Event>comparingLong(e -> e.time).thenComparing(e -> !e.isCall));
        Event head = new Event(null, -1, false, Long.MIN_VALUE);
        Event previous = head;
        for (Event e : events) {
            previous.next = e;
            e.prev = previous;
            previous = e;
        }
        return head;
    }

    /** Removes a call and its return from the list. They remember their neighbours for {@link #unlift}. */
    private static void lift(Event call) {
        unlink(call);
        unlink(call.match);
    }

    /** Puts a lifted call and its return back, in reverse order of removal. */
    private static void unlift(Event call) {
        relink(call.match);
        relink(call);
    }

    private static void unlink(Event e) {
        e.prev.next = e.next;
        if (e.next != null) {
            e.next.prev = e.prev;
        }
    }

    private static void relink(Event e) {
        e.prev.next = e;
        if (e.next != null) {
            e.next.prev = e;
        }
    }
}
