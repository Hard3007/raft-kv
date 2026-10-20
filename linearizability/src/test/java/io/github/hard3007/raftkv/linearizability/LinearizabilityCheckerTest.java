package io.github.hard3007.raftkv.linearizability;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Hand-written histories with known answers. Each is checked by both the real checker and the
 * brute-force reference, which must agree with the expected verdict.
 */
class LinearizabilityCheckerTest {

    private static final LinearizabilityChecker CHECKER = new LinearizabilityChecker(Duration.ofSeconds(10));

    private static void assertLinearizable(Histories history) {
        assertVerdict(Verdict.LINEARIZABLE, history.build());
    }

    private static void assertNotLinearizable(Histories history) {
        assertVerdict(Verdict.NOT_LINEARIZABLE, history.build());
    }

    private static void assertVerdict(Verdict expected, List<Operation> operations) {
        assertEquals(expected, CHECKER.check(operations).verdict(), "checker on " + operations);
        assertEquals(expected == Verdict.LINEARIZABLE, BruteForceChecker.isLinearizable(operations),
                "brute force on " + operations);
    }

    // ------------------------------------------------------------------ basics

    @Test
    void emptyHistoryIsLinearizable() {
        assertLinearizable(new Histories());
    }

    @Test
    void sequentialOperationsFollowTheModel() {
        assertLinearizable(new Histories()
                .get("x", null, 0, 1)
                .put("x", "1", 2, 3)
                .get("x", "1", 4, 5)
                .delete("x", true, 6, 7)
                .delete("x", false, 8, 9)
                .get("x", null, 10, 11));
    }

    @Test
    void readingAValueNobodyWroteIsNotLinearizable() {
        assertNotLinearizable(new Histories().put("x", "1", 0, 1).get("x", "2", 2, 3));
    }

    @Test
    void staleReadAfterACompletedOverwriteIsNotLinearizable() {
        assertNotLinearizable(new Histories()
                .put("x", "1", 0, 1)
                .put("x", "2", 2, 3)
                .get("x", "1", 4, 5));
    }

    @Test
    void readConcurrentWithAWriteMaySeeEitherValue() {
        assertLinearizable(new Histories().put("x", "1", 0, 1).put("x", "2", 2, 10).get("x", "1", 3, 4));
        assertLinearizable(new Histories().put("x", "1", 0, 1).put("x", "2", 2, 10).get("x", "2", 3, 4));
    }

    @Test
    void onceANewValueIsSeenLaterReadsCannotGoBack() {
        // Both reads overlap the write, but the second starts after the first returned.
        assertNotLinearizable(new Histories()
                .put("x", "1", 0, 1)
                .put("x", "2", 2, 20)
                .get("x", "2", 3, 4)
                .get("x", "1", 5, 6));
    }

    @Test
    void deleteMustReportWhetherTheKeyExisted() {
        assertNotLinearizable(new Histories().delete("x", true, 0, 1));
        assertNotLinearizable(new Histories().put("x", "1", 0, 1).delete("x", false, 2, 3));
        assertLinearizable(new Histories().put("x", "1", 0, 1).delete("x", true, 2, 3));
    }

    @Test
    void equalTimestampsCountAsConcurrent() {
        // The read is invoked at the same instant the write returns: either order is allowed.
        assertLinearizable(new Histories().put("x", "1", 0, 5).get("x", null, 5, 6));
        assertNotLinearizable(new Histories().put("x", "1", 0, 5).get("x", null, 6, 7));
    }

    @Test
    void keysAreCheckedIndependentlyAndTheFailingKeyIsReported() {
        List<Operation> history = new Histories()
                .put("a", "1", 0, 1).get("a", "1", 2, 3)
                .put("b", "1", 0, 1).get("b", "9", 2, 3)
                .build();
        CheckResult result = CHECKER.check(history);
        assertEquals(Verdict.NOT_LINEARIZABLE, result.verdict());
        assertEquals("b", result.key());
        assertEquals(2, result.operations().size());
    }

    // ------------------------------------------------------------------ pending (unknown outcome) writes

    @Test
    void pendingWriteMayTakeEffectLongAfterItsInvocation() {
        assertLinearizable(new Histories()
                .pendingPut("x", "1", 0)
                .get("x", null, 10, 11)
                .get("x", "1", 50, 51));
    }

    @Test
    void pendingWriteMayNeverTakeEffect() {
        assertLinearizable(new Histories().pendingPut("x", "1", 0).get("x", null, 10, 11).get("x", null, 50, 51));
    }

    @Test
    void pendingWriteCannotTakeEffectBeforeItWasInvoked() {
        assertNotLinearizable(new Histories().get("x", "1", 0, 1).pendingPut("x", "1", 5));
    }

    @Test
    void pendingWriteTakesEffectAtMostOnce() {
        // Seen, then overwritten, then seen again: that would need the pending put to apply twice.
        assertNotLinearizable(new Histories()
                .pendingPut("x", "1", 0)
                .get("x", "1", 10, 11)
                .put("x", "2", 12, 13)
                .get("x", "1", 14, 15));
    }

    @Test
    void pendingDeleteMayOrMayNotHaveHappened() {
        assertLinearizable(new Histories().put("x", "1", 0, 1).pendingDelete("x", 2).get("x", "1", 10, 11));
        assertLinearizable(new Histories().put("x", "1", 0, 1).pendingDelete("x", 2).get("x", null, 10, 11));
        // ...but once it is seen to have happened, the value cannot come back.
        assertNotLinearizable(new Histories()
                .put("x", "1", 0, 1).pendingDelete("x", 2).get("x", null, 10, 11).get("x", "1", 12, 13));
    }

    // ------------------------------------------------------------------ timeout

    @Test
    void aSearchThatRunsOutOfTimeIsInconclusiveNotAPass() {
        // 24 mutually concurrent puts followed by a read of a value nobody wrote. Proving that is
        // impossible means exploring every subset of the puts (millions of configurations).
        Histories history = new Histories();
        for (int i = 0; i < 24; i++) {
            history.put("x", "v" + i, 0, 100);
        }
        history.get("x", "never-written", 101, 102);
        CheckResult result = new LinearizabilityChecker(Duration.ofMillis(1)).check(history.build());
        assertEquals(Verdict.INCONCLUSIVE, result.verdict());
        assertEquals("x", result.key());
    }
}
