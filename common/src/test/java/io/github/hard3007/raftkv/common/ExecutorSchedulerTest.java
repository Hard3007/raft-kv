package io.github.hard3007.raftkv.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class ExecutorSchedulerTest {

    @Test
    void checkThreadPassesOnTheSchedulerThreadAndFailsElsewhere() throws Exception {
        try (ExecutorScheduler scheduler = new ExecutorScheduler("node-1")) {
            assertEquals("ok", scheduler.call(() -> {
                scheduler.checkThread();
                return "ok";
            }, 1000));
            IllegalStateException e = assertThrows(IllegalStateException.class, scheduler::checkThread);
            assertTrue(e.getMessage().contains("node-1"), e.getMessage());
        }
    }

    @Test
    void timersRunOnTheSchedulerThreadAndCanBeCancelled() throws Exception {
        try (ExecutorScheduler scheduler = new ExecutorScheduler("node-2")) {
            boolean[] cancelledRan = {false};
            scheduler.schedule(10, () -> cancelledRan[0] = true).cancel();
            // One thread runs timers in deadline order, so once this later timer has run, the cancelled
            // one would already have run too, if cancel had not worked.
            CountDownLatch later = new CountDownLatch(1);
            scheduler.schedule(30, () -> {
                scheduler.checkThread();
                later.countDown();
            });
            assertTrue(later.await(2, TimeUnit.SECONDS));
            assertEquals(false, scheduler.call(() -> cancelledRan[0], 1000));
        }
    }
}
