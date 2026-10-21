package io.github.hard3007.raftkv.bench;

import java.util.Arrays;

/** Median, spread and percentiles of a handful of numbers (runs, or failover trials). */
final class Stats {

    private Stats() {
    }

    static double median(double[] values) {
        return percentile(values, 50);
    }

    /**
     * Nearest-rank percentile: the smallest value with at least p% of the values at or below it.
     * With 50 trials, p99 is the largest value; reports say so.
     */
    static double percentile(double[] values, double p) {
        double[] sorted = values.clone();
        Arrays.sort(sorted);
        int rank = (int) Math.ceil(p / 100.0 * sorted.length);
        return sorted[Math.max(0, rank - 1)];
    }

    static double min(double[] values) {
        return Arrays.stream(values).min().orElse(Double.NaN);
    }

    static double max(double[] values) {
        return Arrays.stream(values).max().orElse(Double.NaN);
    }

    /** "median (min-max)", the way every table in the reports shows a set of runs. */
    static String medianAndSpread(double[] values, String format) {
        return String.format(format, median(values)) + " (" + String.format(format, min(values)) + "–"
                + String.format(format, max(values)) + ")";
    }
}
