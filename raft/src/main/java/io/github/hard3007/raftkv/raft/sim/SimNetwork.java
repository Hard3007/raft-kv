package io.github.hard3007.raftkv.raft.sim;

import io.github.hard3007.raftkv.proto.raft.AppendEntriesReply;
import io.github.hard3007.raftkv.proto.raft.AppendEntriesRequest;
import io.github.hard3007.raftkv.proto.raft.InstallSnapshotReply;
import io.github.hard3007.raftkv.proto.raft.InstallSnapshotRequest;
import io.github.hard3007.raftkv.proto.raft.RequestVoteReply;
import io.github.hard3007.raftkv.proto.raft.RequestVoteRequest;
import io.github.hard3007.raftkv.raft.RaftRpcHandler;
import io.github.hard3007.raftkv.raft.Transport;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.BiFunction;
import java.util.function.IntFunction;

/**
 * A simulated network between nodes of one {@link Simulation}. It can:
 * <ul>
 *   <li><b>disconnect</b> a node from everyone ({@link #disconnect}) or split the cluster into
 *       groups that cannot talk to each other ({@link #partition});
 *   <li><b>drop</b> requests and replies at random ({@link #setUnreliable});
 *   <li><b>delay</b> every message by a random amount, which also <b>reorders</b> messages,
 *       and occasionally delay one by a lot ({@link #setLongDelays});
 *   <li>run a test-supplied {@link MessageFilter} that can observe or drop specific messages.
 * </ul>
 * Connectivity is checked when a message is sent and again when it arrives, so a partition also
 * cuts off messages already in flight. Messages to a crashed node are lost, and replies to a node
 * that crashed and restarted are discarded.
 *
 * <p>Besides Raft RPCs, any request/reply exchange can travel over the network with
 * {@link #call}, for example client requests to the KV service. Clients are endpoints too
 * ({@link #registerEndpoint}), so they can be partitioned along with servers.
 */
public final class SimNetwork {

    /** The receiving side of {@link #call}: answers by calling {@code reply} at most once, now or later. */
    @FunctionalInterface
    public interface AsyncHandler<Q, R> {
        void handle(Q request, Consumer<R> reply);
    }

    /** Sees every request and reply; returns true to drop it. */
    @FunctionalInterface
    public interface MessageFilter {
        boolean drop(int from, int to, Object message);
    }

    private final Simulation simulation;
    private final Random random;
    private final Map<Integer, RaftRpcHandler> raftHandlers = new HashMap<>();
    /** Endpoints that are up (not crashed). */
    private final Set<Integer> live = new HashSet<>();
    /** Incremented each time a node (re)registers, so replies to a dead incarnation are dropped. */
    private final Map<Integer, Long> incarnations = new HashMap<>();
    private final Map<Integer, Boolean> connected = new HashMap<>();
    private final Map<Integer, Integer> groupOf = new HashMap<>();

    private final Set<Integer> paused = new HashSet<>();
    /** Deliveries waiting for a paused node, in arrival order. */
    private final Map<Integer, List<Runnable>> held = new HashMap<>();
    /** addressedTo -> actually delivered to, like a stale DNS entry or a reused IP address. */
    private final Map<Integer, Integer> misdeliveries = new HashMap<>();
    private boolean unreliable;
    private boolean longDelays;
    private MessageFilter filter = (from, to, message) -> false;
    private long messagesSent;
    private long messagesLost;

    public SimNetwork(Simulation simulation) {
        this.simulation = simulation;
        this.random = simulation.random();
    }

    /** Attaches a (re)started node. Messages sent to it from now on are delivered to {@code handler}. */
    public void register(int nodeId, RaftRpcHandler handler) {
        registerEndpoint(nodeId);
        raftHandlers.put(nodeId, handler);
    }

    /** Attaches a (re)started endpoint that is not a Raft node, such as a client. */
    public void registerEndpoint(int nodeId) {
        live.add(nodeId);
        incarnations.merge(nodeId, 1L, Long::sum);
        connected.putIfAbsent(nodeId, true);
        groupOf.putIfAbsent(nodeId, 0);
    }

    /** Detaches a crashed node: in-flight messages to it are lost. */
    public void unregister(int nodeId) {
        live.remove(nodeId);
        raftHandlers.remove(nodeId);
        incarnations.merge(nodeId, 1L, Long::sum);
    }

    /** The transport a node uses to send. */
    public Transport transportFor(int nodeId) {
        return new SimTransport(nodeId);
    }

    public void disconnect(int nodeId) {
        connected.put(nodeId, false);
    }

    public void connect(int nodeId) {
        connected.put(nodeId, true);
    }

    public boolean isConnected(int nodeId) {
        return connected.getOrDefault(nodeId, false);
    }

    /** Splits nodes into groups; messages only flow within a group. */
    @SafeVarargs
    public final void partition(Set<Integer>... groups) {
        for (int g = 0; g < groups.length; g++) {
            for (int nodeId : groups[g]) {
                groupOf.put(nodeId, g);
            }
        }
    }

    /** Removes any partition (every node back in one group). Disconnected nodes stay disconnected. */
    public void heal() {
        groupOf.replaceAll((nodeId, group) -> 0);
    }

    /**
     * A paused node (see {@link Simulation.NodeScheduler#pause()}) receives nothing until it resumes:
     * messages for it wait, the way bytes wait in a frozen process's socket buffers.
     */
    public void pause(int nodeId) {
        paused.add(nodeId);
    }

    /**
     * Wakes a paused node. Its waiting messages are delivered within the next 10 ms in a random
     * order: a real process drains several connections (peers, clients) and nothing orders one
     * connection relative to another, so a client request may be handled before a peer's
     * newer-term heartbeat that arrived earlier.
     */
    public void resume(int nodeId) {
        paused.remove(nodeId);
        List<Runnable> waiting = held.remove(nodeId);
        if (waiting != null) {
            for (Runnable delivery : waiting) {
                simulation.schedule(random.nextInt(10), delivery);
            }
        }
    }

    /** Runs {@code delivery} now, or when {@code nodeId} resumes if it is paused. */
    private void whenAwake(int nodeId, Runnable delivery) {
        if (paused.contains(nodeId)) {
            held.computeIfAbsent(nodeId, n -> new ArrayList<>()).add(delivery);
        } else {
            delivery.run();
        }
    }

    /**
     * From now on, messages addressed to {@code addressedTo} reach {@code actuallyTo} instead, and
     * its replies come back as if {@code addressedTo} had sent them. This is what a cached
     * connection to a reused IP address does (seen with docker network disconnect/connect).
     */
    public void misdeliver(int addressedTo, int actuallyTo) {
        misdeliveries.put(addressedTo, actuallyTo);
    }

    public void stopMisdelivering() {
        misdeliveries.clear();
    }

    /** Unreliable: each request and each reply is dropped with probability 10%. */
    public void setUnreliable(boolean unreliable) {
        this.unreliable = unreliable;
    }

    /** Long delays: about one message in ten is delayed 200-2000 ms instead of 0-27 ms. */
    public void setLongDelays(boolean longDelays) {
        this.longDelays = longDelays;
    }

    public void setFilter(MessageFilter filter) {
        this.filter = filter;
    }

    /** Requests sent so far (replies not counted). */
    public long messagesSent() {
        return messagesSent;
    }

    /** Requests and replies dropped by random loss (not counting partitions or crashes). */
    public long messagesLost() {
        return messagesLost;
    }

    private boolean canTalk(int from, int to) {
        return isConnected(from) && isConnected(to) && groupOf.get(from).equals(groupOf.get(to));
    }

    private long delay() {
        if (longDelays && random.nextInt(10) == 0) {
            return 200 + random.nextInt(1800);
        }
        return unreliable ? random.nextInt(27) : random.nextInt(3);
    }

    private boolean lost() {
        if (unreliable && random.nextInt(10) == 0) {
            messagesLost++;
            return true;
        }
        return false;
    }

    /**
     * Delivers {@code request} from {@code from} to {@code to} after a delay, hands it to the
     * handler that {@code handlerAt} returns for {@code to} at arrival time (null if there is none),
     * and delivers the handler's reply back after another delay, unless something along the way
     * drops it. The handler is looked up on arrival because the target may have restarted since.
     */
    public <Q, R> void call(int from, int addressedTo, Q request, IntFunction<AsyncHandler<Q, R>> handlerAt,
            Consumer<R> onReply) {
        int to = misdeliveries.getOrDefault(addressedTo, addressedTo);
        messagesSent++;
        long senderIncarnation = incarnations.get(from);
        if (!canTalk(from, to) || lost() || filter.drop(from, to, request)) {
            return;
        }
        simulation.schedule(delay(), () -> whenAwake(to, () -> {
            AsyncHandler<Q, R> handler = live.contains(to) ? handlerAt.apply(to) : null;
            if (handler == null || !canTalk(from, to)) {
                return;
            }
            long receiverIncarnation = incarnations.get(to);
            handler.handle(request, reply -> {
                boolean receiverStillAlive = live.contains(to) && incarnations.get(to) == receiverIncarnation;
                if (!receiverStillAlive || lost() || filter.drop(to, from, reply)) {
                    return;
                }
                simulation.schedule(delay(), () -> whenAwake(from, () -> {
                    boolean senderStillAlive = live.contains(from) && incarnations.get(from) == senderIncarnation;
                    if (senderStillAlive && canTalk(from, to)) {
                        onReply.accept(reply);
                    }
                }));
            });
        }));
    }

    /** Adapts a synchronous Raft handler method on node {@code id} to an {@link AsyncHandler}. */
    private <Q, R> AsyncHandler<Q, R> raftHandler(int id, BiFunction<RaftRpcHandler, Q, R> method) {
        RaftRpcHandler handler = raftHandlers.get(id);
        return handler == null ? null : (request, reply) -> reply.accept(method.apply(handler, request));
    }

    private final class SimTransport implements Transport {
        private final int self;

        SimTransport(int self) {
            this.self = self;
        }

        @Override
        public void requestVote(int peer, RequestVoteRequest request, Consumer<RequestVoteReply> onReply) {
            call(self, peer, request, id -> raftHandler(id, RaftRpcHandler::handleRequestVote), onReply);
        }

        @Override
        public void appendEntries(int peer, AppendEntriesRequest request, Consumer<AppendEntriesReply> onReply) {
            call(self, peer, request, id -> raftHandler(id, RaftRpcHandler::handleAppendEntries), onReply);
        }

        @Override
        public void installSnapshot(int peer, InstallSnapshotRequest request, Consumer<InstallSnapshotReply> onReply) {
            call(self, peer, request, id -> raftHandler(id, RaftRpcHandler::handleInstallSnapshot), onReply);
        }
    }
}
