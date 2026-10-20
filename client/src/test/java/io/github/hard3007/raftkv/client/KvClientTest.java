package io.github.hard3007.raftkv.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.hard3007.raftkv.common.Scheduler;
import io.github.hard3007.raftkv.proto.kv.DeleteReply;
import io.github.hard3007.raftkv.proto.kv.DeleteRequest;
import io.github.hard3007.raftkv.proto.kv.GetReply;
import io.github.hard3007.raftkv.proto.kv.GetRequest;
import io.github.hard3007.raftkv.proto.kv.LeaderHint;
import io.github.hard3007.raftkv.proto.kv.PutReply;
import io.github.hard3007.raftkv.proto.kv.PutRequest;
import io.github.hard3007.raftkv.proto.kv.Status;
import java.util.ArrayList;
import java.util.List;
import java.util.PriorityQueue;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

/** KvClient's retry loop against a hand-driven transport and a manual clock. */
class KvClientTest {

    /** A minimal manual scheduler: tasks run when the test advances time. */
    private static final class ManualScheduler implements Scheduler {
        private record Task(long at, long order, Runnable run, boolean[] cancelled) {
        }

        private final PriorityQueue<Task> tasks = new PriorityQueue<>(
                (a, b) -> a.at != b.at ? Long.compare(a.at, b.at) : Long.compare(a.order, b.order));
        private long now;
        private long order;

        @Override
        public long nowMillis() {
            return now;
        }

        @Override
        public Cancellable schedule(long delayMillis, Runnable task) {
            boolean[] cancelled = {false};
            tasks.add(new Task(now + delayMillis, order++, task, cancelled));
            return () -> cancelled[0] = true;
        }

        @Override
        public void checkThread() {
            // single-threaded test
        }

        void advance(long millis) {
            long until = now + millis;
            while (!tasks.isEmpty() && tasks.peek().at <= until) {
                Task task = tasks.poll();
                now = task.at;
                if (!task.cancelled[0]) {
                    task.run.run();
                }
            }
            now = until;
        }
    }

    /** Remembers every put sent, so the test decides when (and whether) each one is answered. */
    private static final class RecordingTransport implements ClientTransport {
        final List<Consumer<PutReply>> putReplies = new ArrayList<>();
        final List<PutRequest> puts = new ArrayList<>();
        final List<Integer> servers = new ArrayList<>();

        @Override
        public void get(int server, GetRequest request, Consumer<GetReply> onReply) {
        }

        @Override
        public void put(int server, PutRequest request, Consumer<PutReply> onReply) {
            puts.add(request);
            servers.add(server);
            putReplies.add(onReply);
        }

        @Override
        public void delete(int server, DeleteRequest request, Consumer<DeleteReply> onReply) {
        }
    }

    private final ManualScheduler scheduler = new ManualScheduler();
    private final RecordingTransport transport = new RecordingTransport();
    private final KvClient client = new KvClient(42, List.of(1, 2, 3), transport, scheduler,
            new KvClient.Options(100, 10, 250));

    private static PutReply reply(Status status) {
        return PutReply.newBuilder().setStatus(status).setLeaderHint(LeaderHint.getDefaultInstance()).build();
    }

    @Test
    void aLateReplyAfterGivingUpDoesNotCompleteTheOperationTwice() {
        List<KvResult<Void>> results = new ArrayList<>();
        client.put(new byte[] {1}, new byte[] {2}, results::add);
        scheduler.advance(300); // attempts at 0, 100, 200 all time out; the deadline (250) passes
        assertEquals(1, results.size());
        assertInstanceOf(KvResult.Unknown.class, results.get(0));

        // The last attempt's reply finally shows up.
        transport.putReplies.get(transport.putReplies.size() - 1).accept(reply(Status.OK));
        assertEquals(1, results.size(), "an operation must complete exactly once");
    }

    @Test
    void retriesReuseTheSequenceNumberAndTheNextWriteGetsANewOne() {
        List<KvResult<Void>> results = new ArrayList<>();
        client.put(new byte[] {1}, new byte[] {2}, results::add);
        transport.putReplies.get(0).accept(reply(Status.UNKNOWN));
        scheduler.advance(20); // retry after the retry delay
        transport.putReplies.get(1).accept(reply(Status.OK));
        assertInstanceOf(KvResult.Ok.class, results.get(0));
        assertEquals(transport.puts.get(0).getSequence(), transport.puts.get(1).getSequence());

        client.put(new byte[] {1}, new byte[] {3}, results::add);
        assertEquals(transport.puts.get(0).getSequence() + 1, transport.puts.get(2).getSequence());
    }

    @Test
    void onlyOneOperationAtATime() {
        client.put(new byte[] {1}, new byte[] {2}, result -> { });
        assertThrows(IllegalStateException.class, () -> client.put(new byte[] {1}, new byte[] {3}, result -> { }));
        assertEquals(1, transport.puts.size(), "the rejected call must not send or consume a sequence number");
    }

    @Test
    void notLeaderWithAHintGoesStraightToTheHintedServer() {
        List<KvResult<Void>> results = new ArrayList<>();
        client.put(new byte[] {1}, new byte[] {2}, results::add);
        transport.putReplies.get(0).accept(PutReply.newBuilder().setStatus(Status.NOT_LEADER)
                .setLeaderHint(LeaderHint.newBuilder().setLeaderId(3)).build());
        scheduler.advance(0);
        assertEquals(2, transport.puts.size(), "redirect immediately, without the retry delay");
        assertEquals(List.of(1, 3), transport.servers);
    }
}
