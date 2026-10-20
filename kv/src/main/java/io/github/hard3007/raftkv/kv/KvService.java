package io.github.hard3007.raftkv.kv;

import com.google.protobuf.ByteString;
import io.github.hard3007.raftkv.common.Scheduler;
import io.github.hard3007.raftkv.proto.kv.DeleteOp;
import io.github.hard3007.raftkv.proto.kv.DeleteReply;
import io.github.hard3007.raftkv.proto.kv.DeleteRequest;
import io.github.hard3007.raftkv.proto.kv.GetReply;
import io.github.hard3007.raftkv.proto.kv.GetRequest;
import io.github.hard3007.raftkv.proto.kv.KvCommand;
import io.github.hard3007.raftkv.proto.kv.LeaderHint;
import io.github.hard3007.raftkv.proto.kv.PutOp;
import io.github.hard3007.raftkv.proto.kv.PutReply;
import io.github.hard3007.raftkv.proto.kv.PutRequest;
import io.github.hard3007.raftkv.proto.kv.Status;
import io.github.hard3007.raftkv.proto.raft.LogEntry;
import io.github.hard3007.raftkv.raft.RaftNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Consumer;

/**
 * Serves client Get/Put/Delete requests on one node. Every method runs on the node's thread
 * (the same one as {@link RaftNode}); replies are delivered through callbacks, possibly later.
 *
 * <p><b>Writes</b> are proposed to Raft and answered when the entry at the proposed index is
 * applied ([Fig2 Leader-2]: "respond after entry applied to state machine"). The reply never
 * claims "failed" for a write that might still commit:
 * <ul>
 *   <li>{@code NOT_LEADER}: definite. This node is not the leader and proposed nothing.
 *   <li>{@code INVALID_ARGUMENT}: definite. Rejected before proposing.
 *   <li>{@code UNKNOWN}: the write was proposed but this node stepped down or timed out first,
 *       or a different entry ended up at its index. It may or may not take effect; the client
 *       retries with the same sequence number and the session table makes that exactly-once.
 * </ul>
 *
 * <p><b>Reads</b> use {@link RaftNode#readIndex} and never go through the log.
 */
public final class KvService {

    /** @param requestTimeoutMs how long a request may wait for commit (writes) or confirmation (reads) */
    public record Options(long requestTimeoutMs) {
        public static Options defaults() {
            return new Options(1000);
        }
    }

    /** A proposed write waiting for its entry to be applied. */
    private record PendingWrite(long term, Consumer<KvStateMachine.WriteResult> onApplied,
            Consumer<Status> onNotApplied, Scheduler.Cancellable timeout) {
    }

    private final RaftNode raft;
    private final KvStateMachine stateMachine;
    private final Scheduler scheduler;
    private final Map<Integer, String> addresses;
    private final Options options;
    /** Keyed by log index. A TreeMap so that failing them all happens in a deterministic order. */
    private final TreeMap<Long, PendingWrite> pendingWrites = new TreeMap<>();

    /** @param addresses each node's client-facing address, used in leader hints */
    public KvService(RaftNode raft, KvStateMachine stateMachine, Scheduler scheduler, Map<Integer, String> addresses,
            Options options) {
        this.raft = raft;
        this.stateMachine = stateMachine;
        this.scheduler = scheduler;
        this.addresses = Map.copyOf(addresses);
        this.options = options;
        stateMachine.addListener(this::onApplied);
        raft.setOnLeadershipLost(this::failAllPendingWrites);
    }

    // ------------------------------------------------------------------ requests

    public void get(GetRequest request, Consumer<GetReply> reply) {
        if (request.getKey().size() > KvKeys.MAX_USER_KEY_SIZE) {
            reply.accept(GetReply.newBuilder().setStatus(Status.INVALID_ARGUMENT).build());
            return;
        }
        boolean[] answered = {false};
        Scheduler.Cancellable timeout = scheduler.schedule(options.requestTimeoutMs(), () -> {
            if (!answered[0]) {
                answered[0] = true;
                reply.accept(GetReply.newBuilder().setStatus(Status.UNKNOWN).setLeaderHint(leaderHint()).build());
            }
        });
        raft.readIndex(new RaftNode.ReadIndexCallback() {
            @Override
            public void onReady() {
                if (answered[0]) {
                    return;
                }
                answered[0] = true;
                timeout.cancel();
                Optional<byte[]> value = stateMachine.read(request.getKey().toByteArray());
                GetReply.Builder builder = GetReply.newBuilder().setStatus(Status.OK).setFound(value.isPresent());
                value.ifPresent(v -> builder.setValue(ByteString.copyFrom(v)));
                reply.accept(builder.build());
            }

            @Override
            public void onNotLeader() {
                if (answered[0]) {
                    return;
                }
                answered[0] = true;
                timeout.cancel();
                reply.accept(GetReply.newBuilder().setStatus(Status.NOT_LEADER).setLeaderHint(leaderHint()).build());
            }
        });
    }

    public void put(PutRequest request, Consumer<PutReply> reply) {
        if (request.getKey().size() > KvKeys.MAX_USER_KEY_SIZE || request.getValue().size() > KvKeys.MAX_USER_VALUE_SIZE) {
            reply.accept(PutReply.newBuilder().setStatus(Status.INVALID_ARGUMENT).build());
            return;
        }
        KvCommand command = KvCommand.newBuilder()
                .setClientId(request.getClientId())
                .setSequence(request.getSequence())
                .setPut(PutOp.newBuilder().setKey(request.getKey()).setValue(request.getValue()))
                .build();
        proposeWrite(command,
                result -> reply.accept(PutReply.newBuilder().setStatus(Status.OK).build()),
                status -> reply.accept(PutReply.newBuilder().setStatus(status).setLeaderHint(leaderHint()).build()));
    }

    public void delete(DeleteRequest request, Consumer<DeleteReply> reply) {
        if (request.getKey().size() > KvKeys.MAX_USER_KEY_SIZE) {
            reply.accept(DeleteReply.newBuilder().setStatus(Status.INVALID_ARGUMENT).build());
            return;
        }
        KvCommand command = KvCommand.newBuilder()
                .setClientId(request.getClientId())
                .setSequence(request.getSequence())
                .setDelete(DeleteOp.newBuilder().setKey(request.getKey()))
                .build();
        proposeWrite(command,
                result -> reply.accept(DeleteReply.newBuilder().setStatus(Status.OK).setExisted(result.existed()).build()),
                status -> reply.accept(DeleteReply.newBuilder().setStatus(status).setLeaderHint(leaderHint()).build()));
    }

    // ------------------------------------------------------------------ writes

    private void proposeWrite(KvCommand command, Consumer<KvStateMachine.WriteResult> onApplied,
            Consumer<Status> onNotApplied) {
        Optional<RaftNode.Proposal> proposal = raft.propose(command.toByteArray());
        if (proposal.isEmpty()) {
            onNotApplied.accept(Status.NOT_LEADER); // definite: nothing entered the log
            return;
        }
        long index = proposal.get().index();
        Scheduler.Cancellable timeout = scheduler.schedule(options.requestTimeoutMs(), () -> {
            PendingWrite expired = pendingWrites.remove(index);
            if (expired != null) {
                expired.onNotApplied().accept(Status.UNKNOWN); // it may still commit later
            }
        });
        pendingWrites.put(index, new PendingWrite(proposal.get().term(), onApplied, onNotApplied, timeout));
    }

    private void onApplied(LogEntry entry, KvStateMachine.WriteResult result) {
        PendingWrite pending = pendingWrites.remove(entry.getIndex());
        if (pending == null) {
            return; // not proposed by this node (or already answered)
        }
        pending.timeout().cancel();
        boolean ourEntry = entry.getTerm() == pending.term();
        if (ourEntry && result != null && result.outcome() != KvStateMachine.Outcome.STALE) {
            pending.onApplied().accept(result);
        } else {
            // Another leader's entry won this index, so ours is not here. It could still be
            // elsewhere in the log only via a client retry, so the honest answer is UNKNOWN.
            pending.onNotApplied().accept(Status.UNKNOWN);
        }
    }

    private void failAllPendingWrites() {
        List<PendingWrite> failed = new ArrayList<>(pendingWrites.values());
        pendingWrites.clear();
        for (PendingWrite pending : failed) {
            pending.timeout().cancel();
            pending.onNotApplied().accept(Status.UNKNOWN); // a new leader may still commit it
        }
    }

    private LeaderHint leaderHint() {
        int leaderId = raft.status().leaderId();
        return LeaderHint.newBuilder()
                .setLeaderId(leaderId)
                .setLeaderAddress(addresses.getOrDefault(leaderId, ""))
                .build();
    }
}
