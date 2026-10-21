package io.github.hard3007.raftkv.bench;

import io.github.hard3007.raftkv.client.BlockingKvClient;
import io.github.hard3007.raftkv.client.KvClient;
import io.github.hard3007.raftkv.client.KvResult;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import org.HdrHistogram.Histogram;

/**
 * Closed-loop load against the running cluster. Each client thread sends one operation, waits
 * for the answer, and immediately sends the next (no think time). One run:
 * <ol>
 *   <li>wait until the cluster is stable (one leader, everyone caught up);
 *   <li>start the clients and let them run for the warm-up period (JIT, connections, caches),
 *       recording nothing;
 *   <li>record the term, then measure for the measurement window: only operations that start
 *       and finish inside the window count;
 *   <li>record the term again. A different term means an election happened under load.
 * </ol>
 * Latency is measured per operation from just before the call to its answer, in microseconds,
 * into HdrHistograms (one per thread, merged at the end).
 */
final class LoadBenchmark {

    /** What one configuration runs. {@code readPercent} of operations are Gets, the rest Puts. */
    record Workload(int clients, int readPercent, int valueBytes, int keys) {
        String describe() {
            return clients + " clients, " + readPercent + "% reads / " + (100 - readPercent) + "% writes, "
                    + valueBytes + " B values, " + keys + " keys";
        }
    }

    /** The outcome of one measured run. */
    record RunResult(Workload workload, int run, long operations, long reads, long writes, long failures,
            double seconds, Histogram all, Histogram readLatency, Histogram writeLatency,
            ClusterProbe.TermSnapshot termAtStart, ClusterProbe.TermSnapshot termAtEnd) {

        double opsPerSecond() {
            return operations / seconds;
        }

        boolean electionDuringRun() {
            return termAtStart.term() != termAtEnd.term() || termAtStart.leader() != termAtEnd.leader();
        }
    }

    private static final long HIGHEST_TRACKABLE_MICROS = TimeUnit.SECONDS.toMicros(60);

    private final ClusterProbe cluster;
    private final Duration warmup;
    private final Duration measure;

    LoadBenchmark(ClusterProbe cluster, Duration warmup, Duration measure) {
        this.cluster = cluster;
        this.warmup = warmup;
        this.measure = measure;
    }

    /** Writes every key once, so reads find values of the configured size. */
    void preload(Workload workload) throws InterruptedException {
        int loaders = 8;
        List<Thread> threads = new ArrayList<>();
        List<RuntimeException> errors = new ArrayList<>();
        for (int t = 0; t < loaders; t++) {
            int first = t;
            Thread thread = new Thread(() -> {
                try (BlockingKvClient client = newClient()) {
                    for (int k = first; k < workload.keys(); k += loaders) {
                        if (!(client.put(key(k), value(workload.valueBytes())) instanceof KvResult.Ok<?>)) {
                            throw new IllegalStateException("preload write failed for key " + k);
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (RuntimeException e) {
                    synchronized (errors) {
                        errors.add(e);
                    }
                }
            }, "preload-" + t);
            threads.add(thread);
            thread.start();
        }
        for (Thread thread : threads) {
            thread.join();
        }
        if (!errors.isEmpty()) {
            throw errors.get(0);
        }
    }

    RunResult run(Workload workload, int runNumber) throws InterruptedException {
        cluster.awaitStable(Duration.ofSeconds(60));
        List<ClientLoop> loops = new ArrayList<>();
        List<Thread> threads = new ArrayList<>();
        for (int c = 0; c < workload.clients(); c++) {
            ClientLoop loop = new ClientLoop(newClient(), workload);
            loops.add(loop);
            Thread thread = new Thread(loop, "bench-client-" + c);
            threads.add(thread);
            thread.start();
        }

        Thread.sleep(warmup.toMillis()); // warm-up: operations run but are not recorded
        ClusterProbe.TermSnapshot termAtStart = cluster.termSnapshot();
        long windowStart = System.nanoTime();
        long windowEnd = windowStart + measure.toNanos();
        loops.forEach(loop -> loop.measureBetween(windowStart, windowEnd));
        Thread.sleep(measure.toMillis());
        while (System.nanoTime() < windowEnd) {
            Thread.onSpinWait();
        }
        ClusterProbe.TermSnapshot termAtEnd = cluster.termSnapshot();

        loops.forEach(ClientLoop::stop);
        for (Thread thread : threads) {
            thread.join();
        }
        Histogram all = newHistogram();
        Histogram reads = newHistogram();
        Histogram writes = newHistogram();
        long failures = 0;
        for (ClientLoop loop : loops) {
            reads.add(loop.readLatency);
            writes.add(loop.writeLatency);
            failures += loop.failures;
            loop.client.close();
        }
        all.add(reads);
        all.add(writes);
        return new RunResult(workload, runNumber, all.getTotalCount(), reads.getTotalCount(), writes.getTotalCount(),
                failures, measure.toNanos() / 1e9, all, reads, writes, termAtStart, termAtEnd);
    }

    private BlockingKvClient newClient() {
        // attempt timeout 1 s, retry after 10 ms, give up after 10 s (a give-up counts as a failure)
        return new BlockingKvClient(ThreadLocalRandom.current().nextLong() & Long.MAX_VALUE | 1, cluster.servers(),
                new KvClient.Options(1000, 10, 10_000));
    }

    static Histogram newHistogram() {
        return new Histogram(HIGHEST_TRACKABLE_MICROS, 3);
    }

    static byte[] key(int k) {
        return ("key" + k).getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] value(int bytes) {
        byte[] value = new byte[bytes];
        ThreadLocalRandom.current().nextBytes(value);
        return value;
    }

    /** One closed-loop client: send, wait for the answer, record it, repeat. */
    private static final class ClientLoop implements Runnable {
        final BlockingKvClient client;
        final Workload workload;
        final Random random = new Random();
        final Histogram readLatency = newHistogram();
        final Histogram writeLatency = newHistogram();
        long failures; // operations in the window that did not end OK (only read after join)
        private volatile boolean running = true;
        private volatile long windowStart = Long.MAX_VALUE;
        private volatile long windowEnd = Long.MAX_VALUE;

        ClientLoop(BlockingKvClient client, Workload workload) {
            this.client = client;
            this.workload = workload;
        }

        void measureBetween(long start, long end) {
            windowEnd = end;
            windowStart = start;
        }

        void stop() {
            running = false;
        }

        @Override
        public void run() {
            try {
                while (running) {
                    byte[] key = key(random.nextInt(workload.keys()));
                    boolean read = random.nextInt(100) < workload.readPercent();
                    long start = System.nanoTime();
                    KvResult<?> result = read ? client.get(key) : client.put(key, value(workload.valueBytes()));
                    long end = System.nanoTime();
                    if (start < windowStart || end > windowEnd) {
                        continue; // warm-up, or finished after the window closed
                    }
                    if (result instanceof KvResult.Ok<?>) {
                        (read ? readLatency : writeLatency).recordValue(
                                Math.min(TimeUnit.NANOSECONDS.toMicros(end - start), HIGHEST_TRACKABLE_MICROS));
                    } else {
                        failures++;
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
