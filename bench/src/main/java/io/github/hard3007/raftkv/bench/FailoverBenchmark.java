package io.github.hard3007.raftkv.bench;

import io.github.hard3007.raftkv.client.BlockingKvClient;
import io.github.hard3007.raftkv.client.KvClient;
import io.github.hard3007.raftkv.client.KvResult;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Leader failover, measured the way a client sees it. One trial:
 * <ol>
 *   <li>wait until the cluster is stable (one leader, all 5 nodes caught up);
 *   <li>write once, so the probe client knows the leader;
 *   <li>start a watcher that asks the leader's /status over and over and remembers the end of
 *       its last successful answer: the last moment the leader was certainly alive;
 *   <li>{@code docker kill} the leader (SIGKILL);
 *   <li>a second watcher polls the 4 survivors' /status every 5 ms and notes when one reports
 *       itself LEADER in a newer term;
 *   <li>the probe client writes new keys in a loop; the trial ends at the first write that
 *       succeeds. It was sent after the kill, so the old leader cannot have answered it: it went
 *       through the new leader and committed on a majority of the survivors;
 *   <li>{@code docker start} the old leader and wait for the cluster to be stable again.
 * </ol>
 * Failover time = first successful write - last moment the old leader was seen alive. The true
 * moment of death is a little later, so this errs high.
 */
final class FailoverBenchmark {

    /** One trial's times, in milliseconds since the kill command started. */
    record Trial(int number, int killedNode, long oldTerm, int newLeader, long newTerm, double killCommandMs,
            double lastAliveMs, double electedMs, double firstWriteMs) {
    }

    private final ClusterProbe cluster;

    FailoverBenchmark(ClusterProbe cluster) {
        this.cluster = cluster;
    }

    Trial trial(int number) throws InterruptedException {
        cluster.awaitStable(Duration.ofSeconds(60));
        ClusterProbe.TermSnapshot before = cluster.termSnapshot();
        int oldLeader = before.leader();
        // Probe client: 300 ms per attempt (an attempt at the dead node's port may hang until
        // then), retry after 5 ms, give up after 10 s (then the loop below simply tries again).
        try (BlockingKvClient probe = new BlockingKvClient(ThreadLocalRandom.current().nextLong() & Long.MAX_VALUE | 1,
                cluster.servers(), new KvClient.Options(300, 5, 10_000))) {
            if (!(probe.put(bytes("failover-warm"), bytes("x")) instanceof KvResult.Ok<?>)) {
                throw new IllegalStateException("cluster did not accept a write before the kill");
            }

            AtomicLong lastAlive = new AtomicLong(System.nanoTime());
            Thread aliveWatcher = new Thread(() -> watchUntilDead(oldLeader, lastAlive), "failover-alive-watcher");
            aliveWatcher.start();
            AtomicLong electedAt = new AtomicLong();
            AtomicLong newLeader = new AtomicLong();
            AtomicLong newTerm = new AtomicLong();
            Thread leaderWatcher = new Thread(() -> watchForNewLeader(oldLeader, before.term(), electedAt, newLeader,
                    newTerm), "failover-leader-watcher");
            leaderWatcher.start();

            long killStart = System.nanoTime();
            ClusterProbe.docker("kill", cluster.container(oldLeader));
            long killDone = System.nanoTime();

            long firstWrite = 0;
            for (int attempt = 0; firstWrite == 0; attempt++) {
                KvResult<Void> result = probe.put(bytes("failover-" + number + "-" + attempt), bytes("v"));
                if (result instanceof KvResult.Ok<?>) {
                    firstWrite = System.nanoTime();
                }
                if (System.nanoTime() - killStart > Duration.ofSeconds(60).toNanos()) {
                    throw new IllegalStateException("no successful write within 60 s of the kill");
                }
            }
            aliveWatcher.join(Duration.ofSeconds(60).toMillis());
            leaderWatcher.join(Duration.ofSeconds(60).toMillis());
            ClusterProbe.docker("start", cluster.container(oldLeader)); // a no-op if Docker already restarted it
            return new Trial(number, oldLeader, before.term(), (int) newLeader.get(), newTerm.get(),
                    millis(killDone - killStart), millis(lastAlive.get() - killStart),
                    electedAt.get() == 0 ? Double.NaN : millis(electedAt.get() - killStart),
                    millis(firstWrite - killStart));
        }
    }

    /** Asks the node's /status until it fails; {@code lastAlive} = end of the last answer. */
    private void watchUntilDead(int node, AtomicLong lastAlive) {
        long deadline = System.nanoTime() + Duration.ofSeconds(60).toNanos();
        while (System.nanoTime() < deadline && cluster.status(node) != null) {
            lastAlive.set(System.nanoTime());
        }
    }

    private void watchForNewLeader(int oldLeader, long oldTerm, AtomicLong electedAt, AtomicLong newLeader,
            AtomicLong newTerm) {
        long deadline = System.nanoTime() + Duration.ofSeconds(60).toNanos();
        while (System.nanoTime() < deadline) {
            for (int node = 1; node <= ClusterProbe.NODES; node++) {
                if (node == oldLeader) {
                    continue;
                }
                ClusterProbe.NodeStatus s = cluster.status(node);
                if (s != null && s.role().equals("LEADER") && s.term() > oldTerm) {
                    electedAt.set(System.nanoTime());
                    newLeader.set(node);
                    newTerm.set(s.term());
                    return;
                }
            }
            try {
                Thread.sleep(5);
            } catch (InterruptedException e) {
                return;
            }
        }
    }

    private static double millis(long nanos) {
        return nanos / 1e6;
    }

    private static byte[] bytes(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }
}
