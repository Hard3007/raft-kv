package io.github.hard3007.raftkv.bench;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * What vmstat shows, measured over one benchmark window from /proc/stat (Linux, whole machine:
 * the nodes and the load generator share it). Every second it reads {@code procs_running} (vmstat's
 * "r": threads running or waiting for a CPU); at the start and end it reads the cumulative CPU time
 * counters, so user / system / idle / iowait / steal are percentages of all CPU time in the window.
 * Steal is time the hypervisor gave our virtual CPUs to someone else.
 */
final class CpuSampler {

    /** The window's CPU picture. {@code runnable*} include the sampling thread itself (1). */
    record CpuStats(double runnableMedian, int runnableMax, double user, double system, double idle,
            double iowait, double steal) {

        static final CpuStats UNAVAILABLE = new CpuStats(Double.NaN, -1, Double.NaN, Double.NaN, Double.NaN,
                Double.NaN, Double.NaN);

        String describe() {
            return Double.isNaN(user) ? "cpu n/a" : String.format(
                    "r %.0f (max %d), us %.0f%% sy %.0f%% id %.0f%% wa %.0f%% st %.0f%%", runnableMedian, runnableMax,
                    user, system, idle, iowait, steal);
        }
    }

    private static final Path PROC_STAT = Path.of("/proc/stat");

    private final List<Integer> runnable = new ArrayList<>();
    private long[] startTicks;
    private Thread thread;
    private volatile boolean running;

    /** Starts sampling; call {@link #stop()} at the end of the window. */
    void start() {
        startTicks = cpuTicks();
        if (startTicks == null) {
            return; // not Linux
        }
        running = true;
        thread = new Thread(() -> {
            while (running) {
                Integer r = procsRunning();
                if (r != null) {
                    synchronized (runnable) {
                        runnable.add(r);
                    }
                }
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    return;
                }
            }
        }, "cpu-sampler");
        thread.setDaemon(true);
        thread.start();
    }

    CpuStats stop() throws InterruptedException {
        if (startTicks == null) {
            return CpuStats.UNAVAILABLE;
        }
        running = false;
        thread.interrupt();
        thread.join();
        long[] end = cpuTicks();
        if (end == null) {
            return CpuStats.UNAVAILABLE;
        }
        // /proc/stat "cpu" fields: user nice system idle iowait irq softirq steal ...
        long[] d = new long[8];
        long total = 0;
        for (int i = 0; i < 8; i++) {
            d[i] = end[i] - startTicks[i];
            total += d[i];
        }
        double[] r;
        synchronized (runnable) {
            r = runnable.stream().mapToDouble(Integer::doubleValue).toArray();
        }
        double pct = total == 0 ? Double.NaN : 100.0 / total;
        return new CpuStats(r.length == 0 ? Double.NaN : Stats.median(r),
                r.length == 0 ? -1 : (int) Arrays.stream(r).max().getAsDouble(),
                (d[0] + d[1]) * pct, (d[2] + d[5] + d[6]) * pct, d[3] * pct, d[4] * pct, d[7] * pct);
    }

    /** The first 8 counters of the "cpu" line, or null if /proc/stat is not readable. */
    private static long[] cpuTicks() {
        try {
            String line = Files.readAllLines(PROC_STAT).get(0);
            String[] parts = line.trim().split("\\s+");
            long[] ticks = new long[8];
            for (int i = 0; i < 8; i++) {
                ticks[i] = Long.parseLong(parts[i + 1]);
            }
            return ticks;
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    private static Integer procsRunning() {
        try {
            for (String line : Files.readAllLines(PROC_STAT)) {
                if (line.startsWith("procs_running ")) {
                    return Integer.parseInt(line.substring("procs_running ".length()).trim());
                }
            }
        } catch (IOException | RuntimeException e) {
            // leave this sample out
        }
        return null;
    }
}
