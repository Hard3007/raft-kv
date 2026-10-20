package io.github.hard3007.raftkv.kv;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.hard3007.raftkv.client.KvClient;
import io.github.hard3007.raftkv.client.KvResult;
import io.github.hard3007.raftkv.kv.sim.SimKvCluster;
import io.github.hard3007.raftkv.proto.raft.InstallSnapshotRequest;
import io.github.hard3007.raftkv.raft.RaftConfig;
import io.github.hard3007.raftkv.raft.RaftNode;
import io.github.hard3007.raftkv.storage.BTree;
import io.github.hard3007.raftkv.storage.FsyncPolicy;
import io.github.hard3007.raftkv.storage.SimulatedCrashException;
import io.github.hard3007.raftkv.storage.SimulatedFileSystem;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/** Snapshots and log compaction (Figure 13), end to end on simulated clusters. */
class KvSnapshotTest {

    private static final String SEEDS = "io.github.hard3007.raftkv.kv.Seeds#seeds";
    private static final long WAIT = 20_000;
    /** A snapshot every 10 entries, sent in 64-byte chunks, so even small states take many chunks. */
    private static final RaftConfig SNAPSHOTS = RaftConfig.defaults().withSnapshotThreshold(10).withSnapshotChunkBytes(64);

    private static byte[] b(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    private static <T> T ok(KvResult<T> result) {
        assertInstanceOf(KvResult.Ok.class, result, "expected Ok, got " + result);
        return ((KvResult.Ok<T>) result).value();
    }

    private static void write(SimKvCluster cluster, KvClient client, int from, int to) {
        for (int i = from; i < to; i++) {
            int n = i;
            ok(cluster.<Void>await(done -> client.put(b("k" + n), b("v" + n), done), WAIT));
        }
    }

    /** Runs until two nodes have applied the same index, then compares every row (data and sessions). */
    private static void assertSameState(SimKvCluster cluster, int a, int b, String message) {
        boolean caughtUp = cluster.sim.runUntil(() -> cluster.stateMachine(a).appliedIndex()
                == cluster.stateMachine(b).appliedIndex(), WAIT);
        assertTrue(caughtUp, message + ": node " + b + " never caught up with node " + a);
        List<BTree.Entry> rowsA = cluster.stateMachine(a).rows();
        List<BTree.Entry> rowsB = cluster.stateMachine(b).rows();
        assertEquals(rowsA.size(), rowsB.size(), message);
        for (int i = 0; i < rowsA.size(); i++) {
            assertArrayEquals(rowsA.get(i).key(), rowsB.get(i).key(), message);
            assertArrayEquals(rowsA.get(i).value(), rowsB.get(i).value(), message);
        }
    }

    // ------------------------------------------------------------------ state machine

    @Test
    void snapshotRoundTripsEveryRowIncludingSessions() {
        SimulatedFileSystem disk = new SimulatedFileSystem(new Random(1));
        KvStateMachine source = KvStateMachine.open(disk, "a", BTree.Options.defaults(), 0);
        for (int i = 1; i <= 50; i++) {
            source.apply(KvStateMachineTest.put(i, 1 + i % 3, i, "k" + i % 7, "v" + i));
        }
        KvStateMachine target = KvStateMachine.open(disk, "b", BTree.Options.defaults(), 0);
        target.restoreSnapshot(source.takeSnapshot(), 50);
        assertEquals(50, target.appliedIndex());
        assertEquals(source.rows().size(), target.rows().size());
        // Session rows came along: a retry of client 2's latest write is still recognized.
        List<KvStateMachine.WriteResult> results = new ArrayList<>();
        target.addListener((entry, result) -> results.add(result));
        target.apply(KvStateMachineTest.put(51, 2, 49, "k1", "v49")); // client 2's latest write was #49
        assertEquals(KvStateMachine.Outcome.DUPLICATE, results.get(0).outcome());
    }

    /** A crash at every I/O step of a restore: the tree is afterwards entirely old or entirely new. */
    @Test
    void crashAtEveryStepOfRestoreLeavesTheOldOrTheNewState() {
        KvStateMachine source = KvStateMachine.open(new SimulatedFileSystem(new Random(9)), "src",
                BTree.Options.defaults(), 0);
        for (int i = 1; i <= 40; i++) {
            source.apply(KvStateMachineTest.put(i, 7, i, "k" + i, "new" + i));
        }
        byte[] snapshot = source.takeSnapshot();
        int crashPoints = 0;
        for (int n = 0; ; n++) {
            SimulatedFileSystem disk = new SimulatedFileSystem(new Random(n));
            BTree.Options options = new BTree.Options(4, FsyncPolicy.LAZY, 4 << 10);
            KvStateMachine target = KvStateMachine.open(disk, "kv", options, 0);
            target.apply(KvStateMachineTest.put(1, 3, 1, "old", "old"));
            target.close(); // durable old state at applied index 1
            target = KvStateMachine.open(disk, "kv", options, 0);
            disk.crashAfter(n);
            boolean crashed = false;
            try {
                target.restoreSnapshot(snapshot, 40);
            } catch (SimulatedCrashException expected) {
                crashed = true;
            }
            disk.simulatePowerLoss();
            KvStateMachine after = KvStateMachine.open(disk, "kv", options, 0);
            if (after.appliedIndex() == 1) {
                assertArrayEquals(b("old"), after.read(b("old")).orElseThrow(), "crash point " + n);
                assertEquals(2, after.rows().size(), "crash point " + n + ": old data plus its session row");
            } else {
                assertEquals(40, after.appliedIndex(), "crash point " + n);
                assertEquals(source.rows().size(), after.rows().size(), "crash point " + n);
                assertTrue(after.read(b("old")).isEmpty(), "crash point " + n + ": a mix of old and new");
            }
            if (!crashed) {
                assertEquals(40, after.appliedIndex(), "a restore that returned must be durable");
                break;
            }
            crashPoints++;
        }
        assertTrue(crashPoints > 10, "restore should have many I/O steps, saw " + crashPoints);
    }

    // ------------------------------------------------------------------ cluster

    /** A follower that misses entries the leader has compacted away catches up through InstallSnapshot. */
    @ParameterizedTest(name = "seed {0}")
    @MethodSource(SEEDS)
    void laggingFollowerCatchesUpThroughInstallSnapshot(long seed) {
        SimKvCluster cluster = new SimKvCluster(3, seed, SNAPSHOTS, KvService.Options.defaults()).startAll();
        KvClient client = cluster.newClient(KvClient.Options.defaults());
        write(cluster, client, 0, 5);

        int leader = cluster.awaitLeader(WAIT);
        int follower = cluster.after(leader, 1);
        cluster.network.disconnect(follower);
        write(cluster, client, 5, 60); // several snapshots; the follower's next entry is long gone
        RaftNode.Status leaderStatus = cluster.raft(leader).status();
        assertTrue(leaderStatus.snapshotIndex() > cluster.raft(follower).status().lastLogIndex(),
                "the leader must have compacted past the follower's log");

        int[] chunks = {0};
        cluster.network.setFilter((from, to, message) -> {
            if (message instanceof InstallSnapshotRequest && to == follower) {
                chunks[0]++;
            }
            return false;
        });
        cluster.network.connect(follower);
        assertSameState(cluster, leader, follower, "after catching up");
        assertTrue(cluster.raft(follower).status().snapshotsInstalled() >= 1, "should have caught up by snapshot");
        assertTrue(chunks[0] > 1, "the snapshot should have taken several chunks, took " + chunks[0]);
        for (int i = 0; i < 60; i++) {
            assertArrayEquals(b("v" + i), cluster.stateMachine(follower).read(b("k" + i)).orElseThrow());
        }
    }

    /**
     * After a crash, a node starts from its snapshot and replays only the entries after it. With
     * lazy fsync its B-Tree may come back behind the snapshot; then it is rebuilt from the snapshot.
     */
    @ParameterizedTest(name = "seed {0}")
    @MethodSource(SEEDS)
    void restartStartsFromTheSnapshotAndReplaysOnlyLaterEntries(long seed) {
        SimKvCluster cluster = new SimKvCluster(3, seed, SNAPSHOTS, KvService.Options.defaults()).startAll();
        KvClient client = cluster.newClient(KvClient.Options.defaults());
        write(cluster, client, 0, 45);
        int leader = cluster.awaitLeader(WAIT);
        int victim = cluster.after(leader, 1);
        cluster.sim.runUntil(() -> cluster.raft(victim).status().snapshotIndex() >= 30, WAIT);

        cluster.crash(victim); // power loss
        long[] firstAppliedAfterRestart = {-1};
        cluster.addObserver((node, entry, result) -> {
            if (node == victim && firstAppliedAfterRestart[0] == -1) {
                firstAppliedAfterRestart[0] = entry.getIndex();
            }
        });
        cluster.restart(victim);
        RaftNode.Status restarted = cluster.raft(victim).status();
        assertTrue(restarted.snapshotIndex() >= 30, "the log must still start after the snapshot");
        assertTrue(restarted.lastApplied() >= restarted.snapshotIndex(),
                "the state must start at the snapshot or later, never from an empty tree");

        write(cluster, client, 45, 50);
        assertSameState(cluster, leader, victim, "after restart");
        assertTrue(firstAppliedAfterRestart[0] > restarted.snapshotIndex(),
                "replayed entry " + firstAppliedAfterRestart[0] + " although the snapshot covers up to "
                        + restarted.snapshotIndex());
    }

    /**
     * A crash at every I/O step around taking a snapshot (single node: writing the entry, applying
     * it, saving the snapshot, rewriting the WAL). After power loss and restart, every
     * acknowledged write is there and the in-flight one completes exactly once.
     */
    @Test
    void crashAtEveryIoStepOfSnapshotCreation() {
        int crashPoints = 0;
        for (int n = 0; ; n++) {
            SimKvCluster cluster = new SimKvCluster(1, 77, SNAPSHOTS, KvService.Options.defaults()).startAll();
            KvClient client = cluster.newClient(new KvClient.Options(1000, 50, 30_000));
            write(cluster, client, 0, 8); // entries 1 (no-op) .. 9; the next one reaches the threshold
            assertEquals(0, cluster.snapshotsTaken());

            cluster.disk(1).crashAfter(n);
            List<KvResult<Void>> result = new ArrayList<>();
            client.put(b("k8"), b("v8"), result::add);
            boolean crashed = false;
            try {
                cluster.sim.runUntil(() -> !result.isEmpty(), WAIT);
            } catch (SimulatedCrashException expected) {
                crashed = true;
            }
            if (!crashed) {
                assertEquals(1, cluster.snapshotsTaken(), "the write should have triggered a snapshot");
                break;
            }
            crashPoints++;
            cluster.restart(1); // power loss, then recovery from whatever is on disk
            cluster.sim.runUntil(() -> !result.isEmpty(), WAIT);
            ok(result.get(0)); // the client's retry completes
            for (int i = 0; i <= 8; i++) {
                int k = i;
                Optional<byte[]> value = ok(cluster.<Optional<byte[]>>await(done -> client.get(b("k" + k), done), WAIT));
                assertArrayEquals(b("v" + i), value.orElseThrow(), "crash point " + n + ", key k" + i);
            }
            write(cluster, client, 9, 25); // and the node keeps working, snapshots included
            assertTrue(cluster.raft(1).status().snapshotIndex() >= 10, "crash point " + n);
        }
        assertTrue(crashPoints > 10, "only " + crashPoints + " crash points");
        System.out.println("snapshot creation: crashed at each of " + crashPoints + " I/O steps");
    }

    /**
     * A crash at every I/O step of receiving and installing a snapshot on a follower (chunks,
     * fsync, rename, B-Tree rebuild, pointer switch, WAL rewrite). After restart the follower
     * still catches up and ends with exactly the leader's state.
     */
    @Test
    void crashAtEveryIoStepOfSnapshotInstallation() {
        int crashPoints = 0;
        for (int n = 0; ; n++) {
            SimKvCluster cluster = new SimKvCluster(3, 5, SNAPSHOTS, KvService.Options.defaults()).startAll();
            KvClient client = cluster.newClient(KvClient.Options.defaults());
            write(cluster, client, 0, 3);
            int leader = cluster.awaitLeader(WAIT);
            int follower = cluster.after(leader, 1);
            cluster.network.disconnect(follower);
            write(cluster, client, 3, 40);

            cluster.disk(follower).crashAfter(n);
            cluster.network.connect(follower);
            boolean crashed = false;
            try {
                cluster.sim.runUntil(() -> cluster.raft(follower).status().snapshotsInstalled() >= 1
                        && cluster.stateMachine(follower).appliedIndex() == cluster.stateMachine(leader).appliedIndex(),
                        WAIT);
            } catch (SimulatedCrashException expected) {
                crashed = true;
                crashPoints++;
                cluster.restart(follower);
            }
            assertSameState(cluster, leader, follower, "crash point " + n);
            if (!crashed) {
                break;
            }
        }
        assertTrue(crashPoints > 20, "only " + crashPoints + " crash points");
        System.out.println("snapshot installation: crashed at each of " + crashPoints + " I/O steps");
    }
}
