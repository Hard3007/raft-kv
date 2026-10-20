package io.github.hard3007.raftkv.common;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A {@link Scheduler} backed by one dedicated thread. All work for a node (RPC handlers, replies,
 * timers, client requests) is posted here with {@link #execute}, so it runs one task at a time
 * and needs no locks. Uses {@link System#nanoTime()}, which never jumps when the wall clock is
 * adjusted.
 */
public final class ExecutorScheduler implements Scheduler, Executor, AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(ExecutorScheduler.class);

    private final ScheduledExecutorService executor;
    private final Thread thread;
    private final String name;
    private final AtomicLong failedTasks = new AtomicLong();
    private volatile boolean closing;

    public ExecutorScheduler(String name) {
        this.name = name;
        CompletableFuture<Thread> created = new CompletableFuture<>();
        ScheduledThreadPoolExecutor pool = new ScheduledThreadPoolExecutor(1, runnable -> {
            Thread t = new Thread(runnable, name);
            t.setDaemon(true);
            created.complete(t);
            return t;
        });
        pool.setRemoveOnCancelPolicy(true); // cancelled election timers must not pile up
        pool.prestartCoreThread();
        this.executor = pool;
        this.thread = created.join();
    }

    @Override
    public long nowMillis() {
        return System.nanoTime() / 1_000_000;
    }

    @Override
    public Cancellable schedule(long delayMillis, Runnable task) {
        ScheduledFuture<?> future = executor.schedule(guarded(task), delayMillis, TimeUnit.MILLISECONDS);
        return () -> future.cancel(false);
    }

    /** Posts {@code task} to run on this scheduler's thread as soon as possible. */
    @Override
    public void execute(Runnable task) {
        executor.execute(guarded(task));
    }

    /** Runs {@code task} on this thread and waits for its result (for callers on other threads). */
    public <T> T call(Callable<T> task, long timeoutMillis) throws Exception {
        return executor.submit(task).get(timeoutMillis, TimeUnit.MILLISECONDS);
    }

    @Override
    public void checkThread() {
        if (Thread.currentThread() != thread) {
            throw new IllegalStateException("thread confinement violated: called on " + Thread.currentThread().getName()
                    + ", must run on " + name);
        }
    }

    /** An exception in a task would silently kill nothing but that task; log it loudly instead. */
    private Runnable guarded(Runnable task) {
        return () -> {
            try {
                task.run();
            } catch (RuntimeException | Error e) {
                if (!closing) { // tasks interrupted by a deliberate shutdown are expected to fail
                    failedTasks.incrementAndGet();
                    log.error("executor={} event=task_failed", name, e);
                }
                throw e;
            }
        };
    }

    /** Tasks that threw (excluding those cut short by {@link #close()}). Tests assert this stays 0. */
    public long failedTasks() {
        return failedTasks.get();
    }

    /**
     * Stops the thread: pending tasks are dropped and the running one is interrupted. Waits until
     * the thread has finished, so nothing of this node is still running (or writing files) when
     * this returns.
     */
    @Override
    public void close() {
        closing = true;
        executor.shutdownNow();
        try {
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("executor " + name + " did not stop");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
