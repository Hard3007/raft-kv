package io.github.hard3007.raftkv.kv;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.protobuf.ByteString;
import io.github.hard3007.raftkv.proto.kv.DeleteOp;
import io.github.hard3007.raftkv.proto.kv.KvCommand;
import io.github.hard3007.raftkv.proto.kv.PutOp;
import io.github.hard3007.raftkv.proto.raft.EntryType;
import io.github.hard3007.raftkv.proto.raft.LogEntry;
import io.github.hard3007.raftkv.storage.BTree;
import io.github.hard3007.raftkv.storage.FsyncPolicy;
import io.github.hard3007.raftkv.storage.SimulatedFileSystem;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class KvStateMachineTest {

    private final List<KvStateMachine.WriteResult> results = new ArrayList<>();
    private long nextIndex = 1;

    private static BTree memoryTree(long seed) {
        return BTree.open(new SimulatedFileSystem(new Random(seed)), "kv", BTree.Options.defaults());
    }

    private KvStateMachine newStateMachine(BTree tree) {
        KvStateMachine sm = new KvStateMachine(tree);
        sm.addListener((entry, result) -> results.add(result));
        return sm;
    }

    private static byte[] b(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    static LogEntry put(long index, long client, long seq, String key, String value) {
        return command(index, KvCommand.newBuilder().setClientId(client).setSequence(seq)
                .setPut(PutOp.newBuilder().setKey(ByteString.copyFromUtf8(key)).setValue(ByteString.copyFromUtf8(value)))
                .build());
    }

    static LogEntry delete(long index, long client, long seq, String key) {
        return command(index, KvCommand.newBuilder().setClientId(client).setSequence(seq)
                .setDelete(DeleteOp.newBuilder().setKey(ByteString.copyFromUtf8(key))).build());
    }

    static LogEntry noop(long index) {
        return LogEntry.newBuilder().setIndex(index).setTerm(1).setType(EntryType.NOOP).build();
    }

    private static LogEntry command(long index, KvCommand command) {
        return LogEntry.newBuilder().setIndex(index).setTerm(1).setType(EntryType.COMMAND)
                .setCommand(command.toByteString()).build();
    }

    private KvStateMachine.WriteResult last() {
        return results.get(results.size() - 1);
    }

    @Test
    void putsAndDeletesApplyAndAdvanceTheAppliedIndex() {
        KvStateMachine sm = newStateMachine(memoryTree(1));
        sm.apply(put(nextIndex++, 7, 1, "k", "v"));
        assertArrayEquals(b("v"), sm.read(b("k")).orElseThrow());
        sm.apply(delete(nextIndex++, 7, 2, "k"));
        assertTrue(last().existed());
        assertTrue(sm.read(b("k")).isEmpty());
        assertEquals(2, sm.appliedIndex());
    }

    @Test
    void noopsOnlyAdvanceTheAppliedIndex() {
        KvStateMachine sm = newStateMachine(memoryTree(1));
        sm.apply(noop(1));
        assertNull(last(), "no-ops have no client result");
        assertEquals(1, sm.appliedIndex());
    }

    @Test
    void retryOfTheLatestSequenceReturnsTheCachedReplyWithoutReapplying() {
        KvStateMachine sm = newStateMachine(memoryTree(1));
        sm.apply(put(1, 7, 1, "k", "v"));
        sm.apply(delete(2, 7, 2, "k"));
        assertEquals(KvStateMachine.Outcome.APPLIED, last().outcome());
        assertTrue(last().existed());

        // The same delete reaches the log again (the client retried after an UNKNOWN reply).
        sm.apply(delete(3, 7, 2, "k"));
        assertEquals(KvStateMachine.Outcome.DUPLICATE, last().outcome());
        assertTrue(last().existed(), "re-executing would say false; the cached reply says true");
    }

    @Test
    void anOldDuplicateDoesNotOverwriteANewerWrite() {
        KvStateMachine sm = newStateMachine(memoryTree(1));
        sm.apply(put(1, 7, 1, "k", "old"));
        sm.apply(put(2, 7, 2, "k", "new"));
        sm.apply(put(3, 7, 1, "k", "old")); // a long-delayed copy of write #1
        assertEquals(KvStateMachine.Outcome.STALE, last().outcome());
        assertArrayEquals(b("new"), sm.read(b("k")).orElseThrow());
        assertEquals(3, sm.appliedIndex());
    }

    @Test
    void sessionsAreIndependentPerClient() {
        KvStateMachine sm = newStateMachine(memoryTree(1));
        sm.apply(put(1, 7, 5, "a", "1"));
        sm.apply(put(2, 8, 1, "b", "2")); // client 8's sequence 1 is new even though 7 is at 5
        assertEquals(KvStateMachine.Outcome.APPLIED, last().outcome());
    }

    /** Random entries, including retries and stale copies, from a few clients over a few keys. */
    private static List<LogEntry> randomLog(Random random, int count) {
        List<LogEntry> log = new ArrayList<>();
        long[] sequence = new long[4];
        for (int index = 1; index <= count; index++) {
            int client = random.nextInt(4);
            int choice = random.nextInt(10);
            long seq;
            if (choice == 0 && sequence[client] > 0) {
                seq = sequence[client]; // retry of the latest
            } else if (choice == 1 && sequence[client] > 1) {
                seq = 1 + random.nextInt((int) sequence[client] - 1); // stale copy
            } else if (choice == 2) {
                log.add(noop(index));
                continue;
            } else {
                seq = ++sequence[client];
            }
            String key = "k" + random.nextInt(5);
            log.add(random.nextBoolean()
                    ? put(index, client + 1, seq, key, "v" + random.nextInt(1000))
                    : delete(index, client + 1, seq, key));
        }
        return log;
    }

    @Test
    void applyIsDeterministic() {
        List<LogEntry> log = randomLog(new Random(42), 2000);
        List<KvStateMachine.WriteResult> resultsA = new ArrayList<>();
        List<KvStateMachine.WriteResult> resultsB = new ArrayList<>();
        BTree treeA = memoryTree(1);
        BTree treeB = memoryTree(2);
        KvStateMachine a = new KvStateMachine(treeA);
        KvStateMachine b = new KvStateMachine(treeB);
        a.addListener((e, r) -> resultsA.add(r));
        b.addListener((e, r) -> resultsB.add(r));
        log.forEach(a::apply);
        log.forEach(b::apply);

        assertEquals(resultsA, resultsB);
        List<BTree.Entry> contentsA = treeA.scan(null, null, Integer.MAX_VALUE);
        List<BTree.Entry> contentsB = treeB.scan(null, null, Integer.MAX_VALUE);
        assertEquals(contentsA.size(), contentsB.size());
        for (int i = 0; i < contentsA.size(); i++) {
            assertArrayEquals(contentsA.get(i).key(), contentsB.get(i).key());
            assertArrayEquals(contentsA.get(i).value(), contentsB.get(i).value());
        }
    }

    /**
     * Data, session rows and the applied index are written in one atomic batch. After a power
     * loss with lazy fsync, the tree must equal a reference that applied exactly the first N
     * entries (N = the recovered applied index), session rows included. Re-applying the rest
     * must then end in exactly the reference's final state, with the same replies.
     */
    @Test
    void sessionsDataAndAppliedIndexSurvivePowerLossTogether() {
        int recoveredBehind = 0;
        int recoveredSomething = 0;
        for (int seed = 0; seed < 100; seed++) {
            Random random = new Random(seed);
            List<LogEntry> log = randomLog(random, 300);
            SimulatedFileSystem disk = new SimulatedFileSystem(new Random(seed));
            KvStateMachine sm = new KvStateMachine(BTree.open(disk, "kv", new BTree.Options(4, FsyncPolicy.LAZY, 8 << 10)));
            int crashAt = random.nextInt(log.size());
            log.subList(0, crashAt).forEach(sm::apply);
            disk.simulatePowerLoss();

            BTree recovered = BTree.open(disk, "kv", new BTree.Options(4, FsyncPolicy.LAZY, 8 << 10));
            int n = (int) recovered.appliedIndex();
            assertTrue(n <= crashAt, "seed " + seed);
            recoveredBehind += n < crashAt ? 1 : 0;
            recoveredSomething += n > 0 ? 1 : 0;
            BTree reference = memoryTree(seed);
            KvStateMachine referenceSm = new KvStateMachine(reference);
            log.subList(0, n).forEach(referenceSm::apply);
            assertSameContents(reference, recovered, "seed " + seed + " after recovery at " + n);

            // Raft re-applies the rest; replies must match a crash-free run exactly.
            List<KvStateMachine.WriteResult> recoveredReplies = new ArrayList<>();
            List<KvStateMachine.WriteResult> referenceReplies = new ArrayList<>();
            KvStateMachine resumed = new KvStateMachine(recovered);
            resumed.addListener((e, r) -> recoveredReplies.add(r));
            referenceSm.addListener((e, r) -> referenceReplies.add(r));
            log.subList(n, log.size()).forEach(resumed::apply);
            log.subList(n, log.size()).forEach(referenceSm::apply);
            assertEquals(referenceReplies, recoveredReplies, "seed " + seed);
            assertSameContents(reference, recovered, "seed " + seed + " at the end");
        }
        // The scenario must really exercise both paths: losing the unsynced tail, and keeping a prefix.
        assertTrue(recoveredBehind > 10, "only " + recoveredBehind + " runs lost un-fsynced entries");
        assertTrue(recoveredSomething > 10, "only " + recoveredSomething + " runs kept any entries");
    }

    private static void assertSameContents(BTree expected, BTree actual, String message) {
        List<BTree.Entry> want = expected.scan(null, null, Integer.MAX_VALUE);
        List<BTree.Entry> got = actual.scan(null, null, Integer.MAX_VALUE);
        assertEquals(want.size(), got.size(), message);
        for (int i = 0; i < want.size(); i++) {
            assertArrayEquals(want.get(i).key(), got.get(i).key(), message);
            assertArrayEquals(want.get(i).value(), got.get(i).value(), message);
        }
    }
}
