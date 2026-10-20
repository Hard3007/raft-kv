package io.github.hard3007.raftkv.client;

import com.google.protobuf.ByteString;
import io.github.hard3007.raftkv.common.Scheduler;
import io.github.hard3007.raftkv.proto.kv.DeleteRequest;
import io.github.hard3007.raftkv.proto.kv.GetRequest;
import io.github.hard3007.raftkv.proto.kv.LeaderHint;
import io.github.hard3007.raftkv.proto.kv.PutRequest;
import io.github.hard3007.raftkv.proto.kv.Status;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Client library: finds the leader, follows redirects, and retries until an operation's outcome
 * is known.
 *
 * <p><b>Exactly-once writes.</b> The client has a random 64-bit id and numbers its writes
 * 1, 2, 3, ... A write keeps its sequence number across every retry, so the servers' session
 * table applies it at most once no matter how many copies reach the log. The client allows only
 * one outstanding operation at a time, which is what lets the servers keep just one cached reply
 * per client.
 *
 * <p><b>Retries.</b>
 * <ul>
 *   <li>{@code NOT_LEADER}: go to the hinted leader, or the next server if there is no hint.
 *   <li>{@code UNKNOWN}, or no reply within the attempt timeout: retry, same sequence number.
 *   <li>{@code INVALID_ARGUMENT}: stop; the operation is {@link KvResult.Rejected}.
 * </ul>
 * After {@link Options#giveUpAfterMs()} the operation ends as {@link KvResult.Unknown}.
 *
 * <p>Asynchronous and single-threaded: call it and receive callbacks on the client's
 * {@link Scheduler} thread, so it also runs inside the deterministic simulator.
 */
public final class KvClient {

    /**
     * @param attemptTimeoutMs how long to wait for one server's reply before trying the next
     * @param retryDelayMs     pause before retrying when no leader is known
     * @param giveUpAfterMs    total time before an operation ends as {@link KvResult.Unknown}
     */
    public record Options(long attemptTimeoutMs, long retryDelayMs, long giveUpAfterMs) {
        public static Options defaults() {
            return new Options(1500, 50, 30_000);
        }
    }

    /** A server's answer, reduced to what the retry loop needs. */
    private record Response(Status status, LeaderHint hint, Object value) {
    }

    /** Sends one attempt of an operation to a server. */
    private interface Attempt {
        void send(int server, Consumer<Response> onResponse);
    }

    private final long clientId;
    private final List<Integer> servers;
    private final ClientTransport transport;
    private final Scheduler scheduler;
    private final Options options;

    private long lastSequence;
    private int leaderGuess;
    private boolean busy;
    /** Identifies the current attempt, so that late replies to earlier attempts are ignored. */
    private long attemptNumber;

    public KvClient(long clientId, List<Integer> servers, ClientTransport transport, Scheduler scheduler,
            Options options) {
        this.clientId = clientId;
        this.servers = List.copyOf(servers);
        this.transport = transport;
        this.scheduler = scheduler;
        this.options = options;
        this.leaderGuess = servers.get(0);
    }

    public long clientId() {
        return clientId;
    }

    // ------------------------------------------------------------------ operations

    public void get(byte[] key, Consumer<KvResult<Optional<byte[]>>> done) {
        GetRequest request = GetRequest.newBuilder().setKey(ByteString.copyFrom(key)).build();
        start((server, onResponse) -> transport.get(server, request, reply -> onResponse.accept(new Response(
                reply.getStatus(), reply.getLeaderHint(),
                reply.getFound() ? Optional.of(reply.getValue().toByteArray()) : Optional.empty()))), done);
    }

    public void put(byte[] key, byte[] value, Consumer<KvResult<Void>> done) {
        PutRequest request = PutRequest.newBuilder()
                .setClientId(clientId)
                .setSequence(nextSequence())
                .setKey(ByteString.copyFrom(key))
                .setValue(ByteString.copyFrom(value))
                .build();
        start((server, onResponse) -> transport.put(server, request,
                reply -> onResponse.accept(new Response(reply.getStatus(), reply.getLeaderHint(), null))), done);
    }

    /** Completes with whether the key existed when the delete took effect. */
    public void delete(byte[] key, Consumer<KvResult<Boolean>> done) {
        DeleteRequest request = DeleteRequest.newBuilder()
                .setClientId(clientId)
                .setSequence(nextSequence())
                .setKey(ByteString.copyFrom(key))
                .build();
        start((server, onResponse) -> transport.delete(server, request,
                reply -> onResponse.accept(new Response(reply.getStatus(), reply.getLeaderHint(), reply.getExisted()))),
                done);
    }

    // ------------------------------------------------------------------ retry loop

    /** Called before {@link #start}, so a rejected second operation never consumes a number. */
    private long nextSequence() {
        scheduler.checkThread();
        checkNotBusy();
        return ++lastSequence;
    }

    private void checkNotBusy() {
        if (busy) {
            throw new IllegalStateException("one operation at a time per client");
        }
    }

    private <T> void start(Attempt attempt, Consumer<KvResult<T>> done) {
        scheduler.checkThread();
        checkNotBusy();
        busy = true;
        long deadline = scheduler.nowMillis() + options.giveUpAfterMs();
        tryOnce(attempt, deadline, done);
    }

    private <T> void tryOnce(Attempt attempt, long deadline, Consumer<KvResult<T>> done) {
        if (scheduler.nowMillis() >= deadline) {
            finish(done, new KvResult.Unknown<>());
            return;
        }
        long thisAttempt = ++attemptNumber;
        int server = leaderGuess;
        Scheduler.Cancellable timeout = scheduler.schedule(options.attemptTimeoutMs(), () -> {
            if (attemptNumber == thisAttempt) {
                leaderGuess = nextServer(server); // no answer: maybe it is down or partitioned
                tryOnce(attempt, deadline, done);
            }
        });
        attempt.send(server, response -> {
            if (attemptNumber != thisAttempt) {
                return; // a late reply to an attempt we already gave up on
            }
            timeout.cancel();
            attemptNumber++; // this attempt is over
            handle(response, server, attempt, deadline, done);
        });
    }

    @SuppressWarnings("unchecked")
    private <T> void handle(Response response, int server, Attempt attempt, long deadline, Consumer<KvResult<T>> done) {
        switch (response.status()) {
            case OK -> finish(done, new KvResult.Ok<>((T) response.value()));
            case INVALID_ARGUMENT -> finish(done, new KvResult.Rejected<>("invalid argument"));
            case NOT_LEADER, UNKNOWN -> {
                int hinted = response.hint().getLeaderId();
                boolean useful = hinted != 0 && hinted != server && servers.contains(hinted);
                leaderGuess = useful ? hinted : (response.status() == Status.NOT_LEADER ? nextServer(server) : server);
                // Follow a fresh hint at once; otherwise pause so we do not spin while an election runs.
                retryAfter(useful ? 0 : options.retryDelayMs(), attempt, deadline, done);
            }
            default -> {
                leaderGuess = nextServer(server);
                retryAfter(options.retryDelayMs(), attempt, deadline, done);
            }
        }
    }

    private <T> void retryAfter(long delayMs, Attempt attempt, long deadline, Consumer<KvResult<T>> done) {
        scheduler.schedule(delayMs, () -> tryOnce(attempt, deadline, done));
    }

    private <T> void finish(Consumer<KvResult<T>> done, KvResult<T> result) {
        // Retire the current attempt: a late reply to it must not complete this operation a
        // second time (for example after giving up at the deadline).
        attemptNumber++;
        busy = false;
        done.accept(result);
    }

    private int nextServer(int server) {
        return servers.get((servers.indexOf(server) + 1) % servers.size());
    }

    /** For tests and the chaos harness: the sequence number of the latest write. */
    public long lastSequence() {
        return lastSequence;
    }
}
