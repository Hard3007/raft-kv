package io.github.hard3007.raftkv.chaos;

import io.github.hard3007.raftkv.client.KvClient;
import io.github.hard3007.raftkv.client.KvResult;
import io.github.hard3007.raftkv.kv.KvService;
import io.github.hard3007.raftkv.kv.sim.SimKvCluster;
import io.github.hard3007.raftkv.linearizability.CheckResult;
import io.github.hard3007.raftkv.linearizability.History;
import io.github.hard3007.raftkv.linearizability.LinearizabilityChecker;
import io.github.hard3007.raftkv.linearizability.Operation;
import io.github.hard3007.raftkv.linearizability.Verdict;
import io.github.hard3007.raftkv.storage.SimulatedCrashException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.function.BooleanSupplier;

/**
 * One seeded chaos run: a simulated cluster, concurrent clients doing random operations on a few
 * shared keys, and a nemesis injecting faults. Afterwards the recorded history goes through the
 * linearizability checker. The same seed always produces the same run.
 *
 * <p><b>Faults</b> (one action every 100-400 ms of virtual time, chosen at random):
 * crash a node (power loss; at most two down at once), arm a crash at one of a node's next 0-30
 * I/O operations (so crashes also land mid-append, mid-snapshot, mid-install), restart a crashed
 * node, pause a node for 0.5-2 s (its timers and incoming messages are held, like a GC pause or
 * docker pause), route one node's traffic to another for 0.5-2 s (a reused IP address), isolate the
 * leader together with some clients, split the cluster into a random minority and majority
 * (clients assigned to random sides), heal all partitions, toggle 10% message loss, and toggle
 * long delays (some messages delayed 0.2-2 s, which reorders them and makes clients retry while
 * old copies are still in flight). When all clients are done, everything is healed.
 *
 * <p><b>History rules.</b> An operation is invoked just before the client sends and completed when
 * the client accepts the reply, both on the simulation clock. A write whose result is Unknown
 * stays pending forever. A read without a definite answer is left out (reads have no side effects).
 */
final class ChaosRun {

    /** What one run did, for the report. */
    record Stats(int operations, int completed, int pendingWrites, int discardedReads, int crashes, int midIoCrashes,
            int pauses, int staleRoutes, int partitions,
            int lossPeriods, int longDelayPeriods, long messagesLost, long virtualMillis, long snapshotsTaken,
            long snapshotsInstalled, long finalTerm) {
    }

    /**
     * {@code error} is set (and {@code verdict} is null) if the system itself failed an internal
     * check during the run: a safety assertion in the test harness, or a node refusing to start
     * because committed entries were lost. Such a run never passes.
     */
    record Result(long seed, Verdict verdict, Stats stats, CheckResult check, List<String> faultLog,
            List<Operation> history, long wallMillis, String error) {

        boolean passed() {
            return error == null && verdict == Verdict.LINEARIZABLE;
        }

        /** The same result without the history and fault log (kept only for runs that need explaining). */
        Result withoutDetails() {
            return new Result(seed, verdict, stats, check, List.of(), List.of(), wallMillis, error);
        }
    }

    private final long seed;
    private final ChaosConfig config;
    private final SimKvCluster cluster;
    private final Random random;
    private final History history;
    private final List<KvClient> clients = new ArrayList<>();
    private final List<Integer> clientEndpoints = new ArrayList<>();
    private final List<String> faultLog = new ArrayList<>();
    private int clientsFinished;
    private int completed;
    private int pendingWrites;
    private int discardedReads;
    private int crashes;
    private int midIoCrashes;
    private int pauses;
    private int staleRoutes;
    private boolean staleRouteActive;
    private final Set<Integer> pausedNodes = new HashSet<>();
    private final Set<Integer> armed = new HashSet<>();
    private int partitions;
    private int lossPeriods;
    private int longDelayPeriods;
    private boolean lossOn;
    private boolean longDelaysOn;

    private ChaosRun(long seed, ChaosConfig config) {
        this.seed = seed;
        this.config = config;
        this.cluster = new SimKvCluster(config.nodes(), seed, config.raft(), KvService.Options.defaults());
        this.random = cluster.sim.random();
        this.history = History.forSimulation(cluster.sim::now);
    }

    static Result run(long seed, ChaosConfig config) {
        ChaosRun run = new ChaosRun(seed, config);
        try {
            return run.execute();
        } catch (RuntimeException | AssertionError failure) {
            run.log("internal check failed: " + failure);
            return new Result(seed, null, null, null, run.faultLog, List.of(), 0, failure.toString());
        }
    }

    private Result execute() {
        long wallStart = System.currentTimeMillis();
        cluster.startAll();
        for (int c = 0; c < config.clients(); c++) {
            clients.add(cluster.newClient(config.clientOptions()));
            clientEndpoints.add(cluster.lastClientEndpoint());
        }
        for (int c = 0; c < config.clients(); c++) {
            scheduleNextOperation(c, 0);
        }
        scheduleNemesis();

        // Run until every client finished. Ops always end (clients give up), so this terminates;
        // the cap only guards against a bug.
        run(() -> clientsFinished == config.clients(), 3_600_000);
        healEverything();
        long settleUntil = cluster.sim.now() + 1_000;
        run(() -> cluster.sim.now() >= settleUntil, 1_000);

        List<Operation> operations = history.operations();
        CheckResult check = new LinearizabilityChecker(config.checkerTimeout()).check(operations);
        Stats stats = new Stats(operations.size() + discardedReads, completed, pendingWrites, discardedReads, crashes,
                midIoCrashes, pauses, staleRoutes, partitions, lossPeriods, longDelayPeriods, cluster.network.messagesLost(), cluster.sim.now(),
                cluster.snapshotsTaken(), cluster.snapshotsInstalled(), highestTerm());
        return new Result(seed, check.verdict(), stats, check, faultLog, operations,
                System.currentTimeMillis() - wallStart, null);
    }

    // ------------------------------------------------------------------ workload

    private void scheduleNextOperation(int c, int done) {
        if (done == config.operationsPerClient()) {
            clientsFinished++;
            return;
        }
        cluster.sim.schedule(random.nextInt(config.maxThinkMs() + 1), () -> startOperation(c, done));
    }

    private void startOperation(int c, int done) {
        KvClient client = clients.get(c);
        String key = "k" + random.nextInt(config.keys());
        int choice = random.nextInt(10);
        if (choice < 5) {
            int id = history.invoke(c, Operation.Kind.GET, key, null);
            client.get(bytes(key), result -> {
                if (result instanceof KvResult.Ok<Optional<byte[]>> ok) {
                    finish(id, new Operation.Result.Read(ok.value().map(ChaosRun::string).orElse(null)));
                } else {
                    history.discard(id); // no definite answer: a read has no side effects
                    discardedReads++;
                }
                scheduleNextOperation(c, done + 1);
            });
        } else if (choice < 9) {
            String value = "c" + c + "-" + done; // unique, so every read names the write it saw
            int id = history.invoke(c, Operation.Kind.PUT, key, value);
            client.put(bytes(key), bytes(value), result -> {
                endWrite(id, result, new Operation.Result.Written());
                scheduleNextOperation(c, done + 1);
            });
        } else {
            int id = history.invoke(c, Operation.Kind.DELETE, key, null);
            client.delete(bytes(key), result -> {
                endWrite(id, result, result instanceof KvResult.Ok<Boolean> ok
                        ? new Operation.Result.Deleted(ok.value()) : null);
                scheduleNextOperation(c, done + 1);
            });
        }
    }

    private void endWrite(int id, KvResult<?> result, Operation.Result observed) {
        switch (result) {
            case KvResult.Ok<?> ok -> finish(id, observed);
            case KvResult.Unknown<?> unknown -> {
                history.leavePending(id); // may take effect at any later point, or never
                pendingWrites++;
            }
            case KvResult.Rejected<?> rejected -> throw new IllegalStateException("workload sent an invalid request");
        }
    }

    private void finish(int id, Operation.Result result) {
        history.complete(id, result);
        completed++;
    }

    // ------------------------------------------------------------------ nemesis

    private void scheduleNemesis() {
        int gap = config.minFaultGapMs() + random.nextInt(config.maxFaultGapMs() - config.minFaultGapMs() + 1);
        cluster.sim.schedule(gap, () -> {
            if (clientsFinished == config.clients()) {
                return;
            }
            injectFault();
            scheduleNemesis();
        });
    }

    private void injectFault() {
        switch (random.nextInt(10)) {
            case 0 -> crashSomeNode();
            case 1 -> restartSomeNode();
            case 7 -> armCrashAtNextIo();
            case 8 -> pauseSomeNode();
            case 9 -> staleRoute();
            case 2 -> isolateLeader();
            case 3 -> randomSplit();
            case 4 -> {
                cluster.network.heal();
                log("heal partitions");
            }
            case 5 -> {
                lossOn = !lossOn;
                cluster.network.setUnreliable(lossOn);
                lossPeriods += lossOn ? 1 : 0;
                log(lossOn ? "message loss on" : "message loss off");
            }
            default -> {
                longDelaysOn = !longDelaysOn;
                cluster.network.setLongDelays(longDelaysOn);
                longDelayPeriods += longDelaysOn ? 1 : 0;
                log(longDelaysOn ? "long delays on" : "long delays off");
            }
        }
    }

    private void crashSomeNode() {
        List<Integer> alive = new ArrayList<>();
        for (int i = 1; i <= config.nodes(); i++) {
            if (cluster.isAlive(i)) {
                alive.add(i);
            }
        }
        if (alive.size() - armed.size() <= config.nodes() - 2) {
            return; // at most two down at a time, so a majority can still make progress
        }
        int victim = alive.get(random.nextInt(alive.size()));
        cluster.crash(victim, config.powerLossOnCrash() || random.nextBoolean()); // power loss or a process crash
        armed.remove(victim);
        crashes++;
        log("crash node " + victim);
    }

    private void restartSomeNode() {
        for (int i = 1; i <= config.nodes(); i++) {
            int candidate = 1 + (i + random.nextInt(config.nodes())) % config.nodes();
            if (!cluster.isAlive(candidate)) {
                armed.remove(candidate);
                cluster.restart(candidate);
                log("restart node " + candidate);
                return;
            }
        }
    }

    private void isolateLeader() {
        int leader = cluster.leader();
        if (leader == -1) {
            return;
        }
        Set<Integer> minority = new HashSet<>(Set.of(leader));
        partitionWith(minority, "isolate leader " + leader);
    }

    private void randomSplit() {
        Set<Integer> minority = new HashSet<>();
        int size = 1 + random.nextInt((config.nodes() - 1) / 2); // 1 or 2 of 5
        while (minority.size() < size) {
            minority.add(1 + random.nextInt(config.nodes()));
        }
        partitionWith(minority, "split off " + minority);
    }

    /** Partitions servers into {@code minorityServers} vs the rest, with each client on a random side. */
    private void partitionWith(Set<Integer> minorityServers, String description) {
        Set<Integer> minority = new HashSet<>(minorityServers);
        Set<Integer> majority = new HashSet<>();
        for (int i = 1; i <= config.nodes(); i++) {
            if (!minority.contains(i)) {
                majority.add(i);
            }
        }
        for (int endpoint : clientEndpoints) {
            (random.nextInt(3) == 0 ? minority : majority).add(endpoint);
        }
        cluster.network.heal();
        cluster.network.partition(minority, majority);
        partitions++;
        log(description + " (minority side: " + minority + ")");
    }

    /** Runs the simulation; a crash armed by the nemesis can land in any event, and power-cycles that node. */
    private void run(BooleanSupplier until, long maxMillis) {
        long deadline = cluster.sim.now() + maxMillis;
        while (true) {
            try {
                cluster.sim.runUntil(until, deadline - cluster.sim.now());
                return;
            } catch (SimulatedCrashException crash) {
                powerCycleTheNodeWhoseDiskFailed();
            }
        }
    }

    /**
     * For 0.5-2 s, traffic addressed to one node reaches another (a cached connection to a reused
     * IP address, as docker network disconnect/connect produced). The impostor answers for the
     * addressee.
     */
    private void staleRoute() {
        if (staleRouteActive) {
            return;
        }
        int addressedTo = 1 + random.nextInt(config.nodes());
        int actuallyTo = 1 + random.nextInt(config.nodes());
        if (addressedTo == actuallyTo) {
            return;
        }
        cluster.network.misdeliver(addressedTo, actuallyTo);
        staleRouteActive = true;
        staleRoutes++;
        log("stale route: traffic for node " + addressedTo + " reaches node " + actuallyTo);
        cluster.sim.schedule(500 + random.nextInt(1501), () -> {
            cluster.network.stopMisdelivering();
            staleRouteActive = false;
            log("stale route cleared");
        });
    }

    /** Freezes a live node for 0.5-2 s (like a GC pause): it wakes up believing what it believed before. */
    private void pauseSomeNode() {
        int node = 1 + random.nextInt(config.nodes());
        if (!cluster.isAlive(node) || pausedNodes.contains(node)) {
            return;
        }
        cluster.pause(node);
        pausedNodes.add(node);
        pauses++;
        log("pause node " + node);
        cluster.sim.schedule(500 + random.nextInt(1501), () -> {
            cluster.resume(node);
            pausedNodes.remove(node);
            log("resume node " + node);
        });
    }

    /** Arms a crash at one of a live node's next 0-30 mutating I/O operations (write, fsync, rename, ...). */
    private void armCrashAtNextIo() {
        int node = 1 + random.nextInt(config.nodes());
        if (cluster.isAlive(node) && !armed.contains(node) && liveNodes() > config.nodes() - 2) {
            cluster.disk(node).crashAfter(random.nextInt(31));
            armed.add(node);
            log("arm crash at a coming I/O on node " + node);
        }
    }

    private void powerCycleTheNodeWhoseDiskFailed() {
        for (int i = 1; i <= config.nodes(); i++) {
            if (cluster.isAlive(i) && cluster.disk(i).hasCrashed()) {
                cluster.crash(i); // power loss: un-fsynced writes are lost or torn
                armed.remove(i);
                midIoCrashes++;
                crashes++;
                log("node " + i + " crashed in the middle of an I/O operation");
                return;
            }
        }
        throw new IllegalStateException("a simulated crash fired, but no live node's disk failed");
    }

    private int liveNodes() {
        int live = 0;
        for (int i = 1; i <= config.nodes(); i++) {
            live += cluster.isAlive(i) ? 1 : 0;
        }
        return live;
    }

    private void healEverything() {
        cluster.network.stopMisdelivering();
        staleRouteActive = false;
        for (int node : List.copyOf(pausedNodes)) {
            cluster.resume(node);
        }
        pausedNodes.clear();
        for (int node : armed) {
            cluster.disk(node).crashAfter(-1); // disarm crashes that have not fired yet
        }
        armed.clear();
        cluster.network.heal();
        cluster.network.setUnreliable(false);
        cluster.network.setLongDelays(false);
        for (int i = 1; i <= config.nodes(); i++) {
            if (!cluster.isAlive(i)) {
                cluster.restart(i);
            }
        }
        log("final heal");
    }

    private long highestTerm() {
        long highest = 0;
        for (int i = 1; i <= config.nodes(); i++) {
            highest = Math.max(highest, cluster.raft(i).status().term());
        }
        return highest;
    }

    private void log(String event) {
        faultLog.add("t=" + cluster.sim.now() + "ms " + event);
    }

    private static byte[] bytes(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    private static String string(byte[] b) {
        return new String(b, StandardCharsets.UTF_8);
    }
}
