package io.github.hard3007.raftkv.server;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Measures fsync latency the way the Raft log pays it: append a small record, then
 * {@code FileChannel.force(false)} (fdatasync), repeated. With several writers (each on its own
 * file, all at once) it shows what 5 nodes sharing one disk see. Benchmark reports include its
 * output (scripts/describe-machine.sh runs it in a container, on a Docker volume).
 * <pre>  java -cp ... io.github.hard3007.raftkv.server.FsyncProbe DIR [fsyncs per writer] [writers]</pre>
 */
public final class FsyncProbe {

    private FsyncProbe() {
    }

    public static void main(String[] args) throws Exception {
        Path directory = Path.of(args[0]);
        int count = args.length > 1 ? Integer.parseInt(args[1]) : 200;
        int writers = args.length > 2 ? Integer.parseInt(args[2]) : 1;
        long[][] latencies = new long[writers][];
        List<Thread> threads = new ArrayList<>();
        for (int w = 0; w < writers; w++) {
            int writer = w;
            Thread thread = new Thread(() -> latencies[writer] = probe(directory.resolve("probe-" + writer), count));
            threads.add(thread);
            thread.start();
        }
        for (Thread thread : threads) {
            thread.join();
        }
        long[] all = Arrays.stream(latencies).flatMapToLong(Arrays::stream).sorted().toArray();
        System.out.printf("%d writer(s) x %d fsyncs of a 256-byte append: p50 %.1f ms, p99 %.1f ms, max %.1f ms%n",
                writers, count, ms(all, 50), ms(all, 99), all[all.length - 1] / 1e6);
    }

    private static long[] probe(Path file, int count) {
        long[] nanos = new long[count];
        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.CREATE, StandardOpenOption.WRITE,
                StandardOpenOption.APPEND)) {
            ByteBuffer record = ByteBuffer.allocate(256);
            for (int i = 0; i < count; i++) {
                record.clear();
                channel.write(record);
                long start = System.nanoTime();
                channel.force(false);
                nanos[i] = System.nanoTime() - start;
            }
        } catch (IOException e) {
            throw new IllegalStateException(e);
        } finally {
            try {
                Files.deleteIfExists(file);
            } catch (IOException e) {
                // the probe's container is about to go away anyway
            }
        }
        return nanos;
    }

    /** Nearest-rank percentile of sorted values, in ms. */
    private static double ms(long[] sorted, double percentile) {
        int rank = (int) Math.ceil(percentile / 100.0 * sorted.length);
        return sorted[Math.max(0, rank - 1)] / 1e6;
    }
}
