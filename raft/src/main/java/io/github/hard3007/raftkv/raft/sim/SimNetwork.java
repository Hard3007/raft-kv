package io.github.hard3007.raftkv.raft.sim;

import io.github.hard3007.raftkv.proto.raft.AppendEntriesReply;
import io.github.hard3007.raftkv.proto.raft.AppendEntriesRequest;
import io.github.hard3007.raftkv.proto.raft.RequestVoteReply;
import io.github.hard3007.raftkv.proto.raft.RequestVoteRequest;
import io.github.hard3007.raftkv.raft.RaftRpcHandler;
import io.github.hard3007.raftkv.raft.Transport;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

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
 */
public final class SimNetwork {

    /** Sees every request and reply; returns true to drop it. */
    @FunctionalInterface
    public interface MessageFilter {
        boolean drop(int from, int to, Object message);
    }

    private final Simulation simulation;
    private final Random random;
    private final Map<Integer, RaftRpcHandler> handlers = new HashMap<>();
    /** Incremented each time a node (re)registers, so replies to a dead incarnation are dropped. */
    private final Map<Integer, Long> incarnations = new HashMap<>();
    private final Map<Integer, Boolean> connected = new HashMap<>();
    private final Map<Integer, Integer> groupOf = new HashMap<>();

    private boolean unreliable;
    private boolean longDelays;
    private MessageFilter filter = (from, to, message) -> false;
    private long messagesSent;

    public SimNetwork(Simulation simulation) {
        this.simulation = simulation;
        this.random = simulation.random();
    }

    /** Attaches a (re)started node. Messages sent to it from now on are delivered to {@code handler}. */
    public void register(int nodeId, RaftRpcHandler handler) {
        handlers.put(nodeId, handler);
        incarnations.merge(nodeId, 1L, Long::sum);
        connected.putIfAbsent(nodeId, true);
        groupOf.putIfAbsent(nodeId, 0);
    }

    /** Detaches a crashed node: in-flight messages to it are lost. */
    public void unregister(int nodeId) {
        handlers.remove(nodeId);
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
        return unreliable && random.nextInt(10) == 0;
    }

    /**
     * Delivers {@code request} from {@code from} to {@code to} after a delay, runs the handler
     * there, and delivers the reply back after another delay, unless something along the way
     * drops it.
     */
    private <Q, R> void send(int from, int to, Q request, Function<RaftRpcHandler, R> handle, Consumer<R> onReply) {
        messagesSent++;
        long senderIncarnation = incarnations.get(from);
        if (!canTalk(from, to) || lost() || filter.drop(from, to, request)) {
            return;
        }
        simulation.schedule(delay(), () -> {
            RaftRpcHandler target = handlers.get(to);
            if (target == null || !canTalk(from, to)) {
                return;
            }
            R reply = handle.apply(target);
            if (lost() || filter.drop(to, from, reply)) {
                return;
            }
            simulation.schedule(delay(), () -> {
                boolean senderStillAlive = handlers.containsKey(from) && incarnations.get(from) == senderIncarnation;
                if (senderStillAlive && canTalk(from, to)) {
                    onReply.accept(reply);
                }
            });
        });
    }

    private final class SimTransport implements Transport {
        private final int self;

        SimTransport(int self) {
            this.self = self;
        }

        @Override
        public void requestVote(int peer, RequestVoteRequest request, Consumer<RequestVoteReply> onReply) {
            send(self, peer, request, handler -> handler.handleRequestVote(request), onReply);
        }

        @Override
        public void appendEntries(int peer, AppendEntriesRequest request, Consumer<AppendEntriesReply> onReply) {
            send(self, peer, request, handler -> handler.handleAppendEntries(request), onReply);
        }
    }
}
