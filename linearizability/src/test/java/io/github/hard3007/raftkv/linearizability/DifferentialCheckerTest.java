package io.github.hard3007.raftkv.linearizability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * Differential testing: the Wing-Gong-Lowe checker and the brute-force reference must agree on
 * thousands of tiny random histories (at most 8 operations, 1-2 keys, with ties and pending
 * writes). Three generators produce a healthy mix of linearizable and non-linearizable histories.
 */
class DifferentialCheckerTest {

    private static final int HISTORIES_PER_GENERATOR = 5_000;
    private static final LinearizabilityChecker CHECKER = new LinearizabilityChecker(Duration.ofSeconds(10));

    private enum Generator { VALID_BY_CONSTRUCTION, ONE_RESULT_CORRUPTED, RANDOM_RESULTS }

    @Test
    void checkersAgreeOnRandomTinyHistories() {
        Random random = new Random(20261020);
        Map<Generator, int[]> verdicts = new HashMap<>(); // [linearizable, not]
        for (Generator generator : Generator.values()) {
            int[] counts = new int[2];
            for (int i = 0; i < HISTORIES_PER_GENERATOR; i++) {
                List<Operation> history = generate(random, generator);
                boolean expected = BruteForceChecker.isLinearizable(history);
                Verdict actual = CHECKER.check(history).verdict();
                assertEquals(expected ? Verdict.LINEARIZABLE : Verdict.NOT_LINEARIZABLE, actual,
                        generator + " history " + i + " disagrees:\n  " + String.join("\n  ",
                                history.stream().map(Operation::toString).toList()));
                counts[expected ? 0 : 1]++;
            }
            verdicts.put(generator, counts);
        }

        assertEquals(HISTORIES_PER_GENERATOR, verdicts.get(Generator.VALID_BY_CONSTRUCTION)[0],
                "histories built from a sequential execution are linearizable by construction");
        int linearizable = 0;
        int notLinearizable = 0;
        for (int[] counts : verdicts.values()) {
            linearizable += counts[0];
            notLinearizable += counts[1];
        }
        // Both answers must be well represented, or agreement would mean little.
        assertTrue(linearizable > 5_000 && notLinearizable > 4_000,
                "linearizable=" + linearizable + " not=" + notLinearizable);
        System.out.printf("differential: %d histories, %d linearizable, %d not; per generator %s%n",
                linearizable + notLinearizable, linearizable, notLinearizable, describe(verdicts));
    }

    private static String describe(Map<Generator, int[]> verdicts) {
        StringBuilder text = new StringBuilder();
        for (Generator g : Generator.values()) {
            text.append(g).append("=").append(verdicts.get(g)[0]).append("/").append(verdicts.get(g)[1]).append(" ");
        }
        return text.toString().trim();
    }

    /** One planned operation: its interval and, for valid histories, the instant it takes effect. */
    private record Planned(Operation.Kind kind, String key, String argument, long invoke, long ret, boolean pending,
            double effectAt, boolean takesEffect) {
    }

    static List<Operation> generate(Random random, Generator generator) {
        int size = 1 + random.nextInt(8);
        int keys = 1 + random.nextInt(2);
        List<Planned> plan = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            Operation.Kind kind = Operation.Kind.values()[random.nextInt(3)];
            String key = keys == 1 ? "a" : (random.nextBoolean() ? "a" : "b");
            long invoke = random.nextInt(16); // small range: plenty of overlap and some exact ties
            long ret = invoke + random.nextInt(6);
            boolean pending = kind != Operation.Kind.GET && random.nextInt(5) == 0;
            // A valid execution: each operation takes effect at one instant inside its interval;
            // a pending write takes effect at any instant after invocation, or never.
            double effectAt = pending ? invoke + random.nextDouble() * 30 : invoke + random.nextDouble() * (ret - invoke);
            boolean takesEffect = !pending || random.nextBoolean();
            plan.add(new Planned(kind, key, kind == Operation.Kind.PUT ? "v" + i : null, invoke, ret, pending,
                    effectAt, takesEffect));
        }

        // Execute the plan sequentially in effect order to get results a real system could return.
        Map<Planned, Operation.Result> results = new HashMap<>();
        Map<String, String> state = new HashMap<>();
        List<Planned> inEffectOrder = new ArrayList<>(plan);
        inEffectOrder.sort(Comparator.comparingDouble(Planned::effectAt));
        for (Planned p : inEffectOrder) {
            String current = state.get(p.key());
            results.put(p, switch (p.kind()) {
                case GET -> new Operation.Result.Read(current);
                case PUT -> new Operation.Result.Written();
                case DELETE -> new Operation.Result.Deleted(current != null);
            });
            if (p.takesEffect()) {
                if (p.kind() == Operation.Kind.PUT) {
                    state.put(p.key(), p.argument());
                } else if (p.kind() == Operation.Kind.DELETE) {
                    state.remove(p.key());
                }
            }
        }

        int corrupt = generator == Generator.ONE_RESULT_CORRUPTED ? random.nextInt(size) : -1;
        List<Operation> history = new ArrayList<>();
        for (int i = 0; i < plan.size(); i++) {
            Planned p = plan.get(i);
            Operation.Result result = results.get(p);
            if (generator == Generator.RANDOM_RESULTS || i == corrupt) {
                result = randomResult(random, p, size);
            }
            if (p.pending()) {
                result = new Operation.Result.Pending();
            }
            history.add(new Operation(i, i, p.kind(), p.key(), p.argument(), p.invoke(),
                    p.pending() ? Operation.PENDING : p.ret(), result));
        }
        return history;
    }

    private static Operation.Result randomResult(Random random, Planned p, int size) {
        return switch (p.kind()) {
            case GET -> new Operation.Result.Read(random.nextInt(3) == 0 ? null : "v" + random.nextInt(size + 1));
            case PUT -> new Operation.Result.Written();
            case DELETE -> new Operation.Result.Deleted(random.nextBoolean());
        };
    }
}
