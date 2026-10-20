package io.github.hard3007.raftkv.kv;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.protobuf.InvalidProtocolBufferException;
import io.github.hard3007.raftkv.client.KvClient;
import io.github.hard3007.raftkv.client.KvResult;
import io.github.hard3007.raftkv.kv.sim.SimKvCluster;
import io.github.hard3007.raftkv.proto.kv.DeleteReply;
import io.github.hard3007.raftkv.proto.kv.KvCommand;
import io.github.hard3007.raftkv.proto.kv.PutReply;
import io.github.hard3007.raftkv.proto.kv.Status;
import io.github.hard3007.raftkv.proto.raft.AppendEntriesRequest;
import io.github.hard3007.raftkv.proto.raft.EntryType;
import io.github.hard3007.raftkv.proto.raft.LogEntry;
import io.github.hard3007.raftkv.raft.RaftConfig;
import io.github.hard3007.raftkv.raft.RaftNode;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/** The KV service end to end on a simulated cluster: real Raft, B-Trees, services and clients. */
class KvClusterTest {

    private static final String SEEDS = "io.github.hard3007.raftkv.kv.Seeds#seeds";
    private static final long WAIT = 20_000;

    /**
     * For tests that need a leader which has been deposed but does not know it. CheckQuorum would
     * end that state after about an election timeout, but ReadIndex and the UNKNOWN rule must be
     * correct without relying on CheckQuorum's timing (it is a liveness aid, not a safety
     * mechanism), so these tests keep the deposed leader around indefinitely.
     */
    private static final RaftConfig WITHOUT_CHECK_QUORUM = RaftConfig.defaults().withCheckQuorum(false);

    private static byte[] b(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    private static <T> T ok(KvResult<T> result) {
        assertNotNull(result, "operation did not complete");
        assertInstanceOf(KvResult.Ok.class, result, "expected Ok, got " + result);
        return ((KvResult.Ok<T>) result).value();
    }

    /** Counts, per node, how often each (client, sequence) was APPLIED vs answered as a DUPLICATE. */
    private static final class ApplyCounter {
        final Map<String, Integer> applied = new HashMap<>();
        int duplicates;

        void attach(SimKvCluster cluster) {
            cluster.addObserver((node, entry, result) -> {
                if (entry.getType() != EntryType.COMMAND || result == null) {
                    return;
                }
                KvCommand command = parse(entry);
                String key = node + "/" + command.getClientId() + "/" + command.getSequence();
                if (result.outcome() == KvStateMachine.Outcome.APPLIED) {
                    applied.merge(key, 1, Integer::sum);
                } else if (result.outcome() == KvStateMachine.Outcome.DUPLICATE) {
                    duplicates++;
                }
            });
        }

        void assertAppliedAtMostOnceEverywhere() {
            applied.forEach((key, count) -> assertEquals(1, count, "applied more than once: node/client/seq " + key));
        }

        private static KvCommand parse(LogEntry entry) {
            try {
                return KvCommand.parseFrom(entry.getCommand());
            } catch (InvalidProtocolBufferException e) {
                throw new AssertionError(e);
            }
        }
    }

    @ParameterizedTest(name = "seed {0}")
    @MethodSource(SEEDS)
    void putGetDelete(long seed) {
        SimKvCluster cluster = new SimKvCluster(5, seed).startAll();
        KvClient client = cluster.newClient(KvClient.Options.defaults());

        assertTrue(ok(cluster.<Optional<byte[]>>await(done -> client.get(b("k"), done), WAIT)).isEmpty());
        ok(cluster.<Void>await(done -> client.put(b("k"), b("v1"), done), WAIT));
        ok(cluster.<Void>await(done -> client.put(b("k"), b("v2"), done), WAIT));
        assertArrayEquals(b("v2"), ok(cluster.<Optional<byte[]>>await(done -> client.get(b("k"), done), WAIT)).orElseThrow());
        assertTrue(ok(cluster.<Boolean>await(done -> client.delete(b("k"), done), WAIT)), "existed");
        assertFalse(ok(cluster.<Boolean>await(done -> client.delete(b("k"), done), WAIT)), "already gone");
        assertTrue(ok(cluster.<Optional<byte[]>>await(done -> client.get(b("k"), done), WAIT)).isEmpty());
    }

    @Test
    void oversizedKeysAndValuesAreDefinitelyRejected() {
        SimKvCluster cluster = new SimKvCluster(3, 1).startAll();
        KvClient client = cluster.newClient(KvClient.Options.defaults());
        byte[] longKey = new byte[KvKeys.MAX_USER_KEY_SIZE + 1];
        byte[] longValue = new byte[KvKeys.MAX_USER_VALUE_SIZE + 1];
        assertInstanceOf(KvResult.Rejected.class, cluster.<Void>await(done -> client.put(longKey, b("v"), done), WAIT));
        assertInstanceOf(KvResult.Rejected.class, cluster.<Void>await(done -> client.put(b("k"), longValue, done), WAIT));
        // Limits are inclusive: the largest legal key and value work.
        ok(cluster.<Void>await(done -> client.put(new byte[KvKeys.MAX_USER_KEY_SIZE],
                new byte[KvKeys.MAX_USER_VALUE_SIZE], done), WAIT));
    }

    /**
     * ReadIndex in action. The old leader is cut off with one client; the majority elects a new
     * leader and commits a newer value. The old leader still believes it leads and still holds
     * the old value, but it cannot confirm leadership with a majority, so it must never serve
     * that stale value.
     */
    @ParameterizedTest(name = "seed {0}")
    @MethodSource(SEEDS)
    void deposedLeaderNeverServesAStaleRead(long seed) {
        SimKvCluster cluster = new SimKvCluster(5, seed, WITHOUT_CHECK_QUORUM, KvService.Options.defaults()).startAll();
        KvClient writer = cluster.newClient(KvClient.Options.defaults());
        int writerEndpoint = cluster.lastClientEndpoint();
        KvClient staleReader = cluster.newClient(new KvClient.Options(1500, 50, 6_000));
        int readerEndpoint = cluster.lastClientEndpoint();
        ok(cluster.<Void>await(done -> writer.put(b("k"), b("old"), done), WAIT));

        int oldLeader = cluster.awaitLeader(WAIT);
        Set<Integer> minority = Set.of(oldLeader, readerEndpoint);
        Set<Integer> majority = new HashSet<>(Set.of(writerEndpoint));
        for (int i = 1; i <= 5; i++) {
            if (i != oldLeader) {
                majority.add(i);
            }
        }
        cluster.network.partition(minority, majority);
        ok(cluster.<Void>await(done -> writer.put(b("k"), b("new"), done), WAIT));
        assertEquals(RaftNode.Role.LEADER, cluster.raft(oldLeader).status().role(),
                "the scenario needs a deposed leader that does not know it yet");

        KvResult<Optional<byte[]>> read = cluster.await(done -> staleReader.get(b("k"), done), WAIT);
        assertInstanceOf(KvResult.Unknown.class, read, "a minority leader must not serve reads, got " + read);

        cluster.network.heal();
        assertArrayEquals(b("new"), ok(cluster.<Optional<byte[]>>await(done -> staleReader.get(b("k"), done), WAIT))
                .orElseThrow());
    }

    /** The reply to a delete is lost; the client retries with the same sequence number. */
    @ParameterizedTest(name = "seed {0}")
    @MethodSource(SEEDS)
    void retriedWriteIsAppliedOnceAndReturnsTheOriginalReply(long seed) {
        SimKvCluster cluster = new SimKvCluster(5, seed).startAll();
        ApplyCounter counter = new ApplyCounter();
        counter.attach(cluster);
        KvClient client = cluster.newClient(KvClient.Options.defaults());
        ok(cluster.<Void>await(done -> client.put(b("k"), b("v"), done), WAIT));

        boolean[] droppedOne = {false};
        cluster.network.setFilter((from, to, message) -> {
            if (message instanceof DeleteReply reply && reply.getStatus() == Status.OK && !droppedOne[0]) {
                droppedOne[0] = true;
                return true;
            }
            return false;
        });
        boolean existed = ok(cluster.<Boolean>await(done -> client.delete(b("k"), done), WAIT));
        assertTrue(droppedOne[0], "the scenario needs a lost reply");
        assertTrue(existed, "re-executing the delete would say false; the cached reply says true");

        cluster.sim.runFor(2_000); // let every replica apply both copies
        assertTrue(counter.duplicates >= 1, "the retry should have reached the log as a duplicate");
        counter.assertAppliedAtMostOnceEverywhere();
    }

    /**
     * The leader accepts a write but cannot replicate it (its AppendEntries are dropped). The
     * others elect a new leader, which deposes the old one. The client must hear UNKNOWN, not a
     * definite failure, then retry and succeed, with the write applied once.
     */
    @ParameterizedTest(name = "seed {0}")
    @MethodSource(SEEDS)
    void leaderThatStepsDownAfterProposingAnswersUnknown(long seed) {
        SimKvCluster cluster = new SimKvCluster(5, seed).startAll();
        ApplyCounter counter = new ApplyCounter();
        counter.attach(cluster);
        KvClient client = cluster.newClient(KvClient.Options.defaults());
        ok(cluster.<Void>await(done -> client.put(b("warmup"), b("1"), done), WAIT)); // client now knows the leader

        int oldLeader = cluster.awaitLeader(WAIT);
        long oldTerm = cluster.raft(oldLeader).status().term();
        Map<Status, Integer> repliesFromOldLeader = new TreeMap<>();
        cluster.network.setFilter((from, to, message) -> {
            if (message instanceof PutReply reply && from == oldLeader) {
                repliesFromOldLeader.merge(reply.getStatus(), 1, Integer::sum);
            }
            // The old leader's term-oldTerm AppendEntries never arrive: it can accept but never commit.
            return from == oldLeader && message instanceof AppendEntriesRequest request && request.getTerm() == oldTerm;
        });

        ok(cluster.<Void>await(done -> client.put(b("k"), b("v"), done), WAIT));
        assertTrue(repliesFromOldLeader.getOrDefault(Status.UNKNOWN, 0) >= 1,
                "the old leader must answer UNKNOWN for its proposed write; replies were " + repliesFromOldLeader);
        assertTrue(cluster.raft(oldLeader).status().term() > oldTerm, "the old leader should have been deposed");

        cluster.network.setFilter((from, to, message) -> false);
        assertArrayEquals(b("v"), ok(cluster.<Optional<byte[]>>await(done -> client.get(b("k"), done), WAIT))
                .orElseThrow());
        cluster.sim.runFor(2_000);
        counter.assertAppliedAtMostOnceEverywhere();
    }

    /**
     * The leader is partitioned with the client and keeps its leadership (no step-down), so the
     * write can only time out on the server. That timeout must also be UNKNOWN.
     */
    @ParameterizedTest(name = "seed {0}")
    @MethodSource(SEEDS)
    void serverTimeoutAfterProposingAnswersUnknown(long seed) {
        SimKvCluster cluster = new SimKvCluster(5, seed, WITHOUT_CHECK_QUORUM, KvService.Options.defaults()).startAll();
        KvClient client = cluster.newClient(new KvClient.Options(1500, 50, 3_000));
        int clientEndpoint = cluster.lastClientEndpoint();
        ok(cluster.<Void>await(done -> client.put(b("warmup"), b("1"), done), WAIT));

        int leader = cluster.awaitLeader(WAIT);
        Set<Integer> others = new HashSet<>();
        for (int i = 1; i <= 5; i++) {
            if (i != leader) {
                others.add(i);
            }
        }
        cluster.network.partition(Set.of(leader, clientEndpoint), others);
        Map<Status, Integer> replies = new TreeMap<>();
        cluster.network.setFilter((from, to, message) -> {
            if (message instanceof PutReply reply) {
                replies.merge(reply.getStatus(), 1, Integer::sum);
            }
            return false;
        });

        KvResult<Void> result = cluster.await(done -> client.put(b("k"), b("v"), done), WAIT);
        assertInstanceOf(KvResult.Unknown.class, result, "the client cannot know the outcome");
        assertEquals(Set.of(Status.UNKNOWN), replies.keySet(), "only UNKNOWN replies; never a definite failure");
    }

    /**
     * One client performs random operations while nodes crash (power loss) and restart, always
     * keeping a majority up. With a single sequential client, every result must match a simple
     * model, and every replica must end with the same data. Restarted nodes resume from their
     * B-Tree's applied index rather than replaying the whole log into an empty state machine.
     */
    @ParameterizedTest(name = "seed {0}")
    @MethodSource(SEEDS)
    void crashesAndRestartsLoseNoAcknowledgedWrite(long seed) {
        SimKvCluster cluster = new SimKvCluster(5, seed).startAll();
        Random random = new Random(seed);
        KvClient client = cluster.newClient(KvClient.Options.defaults());
        Map<String, byte[]> model = new HashMap<>();
        long resumedFrom = 0;
        int forcedVictim = -1;

        for (int op = 0; op < 200; op++) {
            String key = "k" + random.nextInt(8);
            int kind = random.nextInt(3);
            if (kind == 0) {
                byte[] value = b("v" + op);
                ok(cluster.<Void>await(done -> client.put(b(key), value, done), WAIT));
                model.put(key, value);
            } else if (kind == 1) {
                boolean existed = ok(cluster.<Boolean>await(done -> client.delete(b(key), done), WAIT));
                assertEquals(model.remove(key) != null, existed, "delete of " + key + " at op " + op);
            } else {
                Optional<byte[]> value = ok(cluster.<Optional<byte[]>>await(done -> client.get(b(key), done), WAIT));
                assertTrue(Arrays.equals(model.get(key), value.orElse(null)), "get of " + key + " at op " + op);
            }

            // Besides the random faults below, always crash one follower mid-run and restart it
            // later, so every seed exercises resuming from a B-Tree that already holds data.
            if (op == 60) {
                for (int i = 1; i <= 5; i++) {
                    if (!cluster.isAlive(i)) {
                        cluster.restart(i);
                    }
                }
                forcedVictim = cluster.after(cluster.awaitLeader(WAIT), 1);
                cluster.crash(forcedVictim);
            } else if (op == 120 && !cluster.isAlive(forcedVictim)) {
                cluster.restart(forcedVictim);
                resumedFrom = Math.max(resumedFrom, cluster.stateMachine(forcedVictim).appliedIndex());
            }

            int alive = 0;
            for (int i = 1; i <= 5; i++) {
                alive += cluster.isAlive(i) ? 1 : 0;
            }
            int victim = 1 + random.nextInt(5);
            if (random.nextInt(10) == 0 && cluster.isAlive(victim) && alive > 3) {
                cluster.crash(victim);
            } else if (random.nextInt(5) == 0 && !cluster.isAlive(victim)) {
                cluster.restart(victim);
                resumedFrom = Math.max(resumedFrom, cluster.stateMachine(victim).appliedIndex());
            }
        }

        for (int i = 1; i <= 5; i++) {
            if (!cluster.isAlive(i)) {
                cluster.restart(i);
                resumedFrom = Math.max(resumedFrom, cluster.stateMachine(i).appliedIndex());
            }
        }
        cluster.sim.runFor(3_000);
        for (int i = 1; i <= 5; i++) {
            for (int k = 0; k < 8; k++) {
                byte[] expected = model.get("k" + k);
                byte[] actual = cluster.stateMachine(i).read(b("k" + k)).orElse(null);
                assertTrue(Arrays.equals(expected, actual), "node " + i + " disagrees on k" + k);
            }
        }
        assertTrue(resumedFrom > 0, "some restart should resume from a non-empty B-Tree");
    }
}
