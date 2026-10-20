package io.github.hard3007.raftkv.raft;

import com.google.protobuf.ByteString;
import io.github.hard3007.raftkv.proto.raft.AppendEntriesReply;
import io.github.hard3007.raftkv.proto.raft.AppendEntriesRequest;
import io.github.hard3007.raftkv.proto.raft.EntryType;
import io.github.hard3007.raftkv.proto.raft.LogEntry;
import io.github.hard3007.raftkv.proto.raft.RequestVoteReply;
import io.github.hard3007.raftkv.proto.raft.RequestVoteRequest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * One Raft server, implementing Figure 2 of the Raft paper (Ongaro and Ousterhout).
 *
 * <p>Comments tagged {@code [Fig2 ...]} name the Figure 2 rule the code implements:
 * <ul>
 *   <li>{@code State}: the persistent and volatile state variables;
 *   <li>{@code Persist}: "updated on stable storage before responding to RPCs";
 *   <li>{@code RequestVote-1..2}, {@code AppendEntries-1..5}: the receiver implementation steps;
 *   <li>{@code All-1..2}, {@code Follower-1..2}, {@code Candidate-1..4}, {@code Leader-1..4}:
 *       the "Rules for Servers", numbered in the order the paper lists them.
 * </ul>
 * Two additions beyond Figure 2 are marked as such: fast log backtracking (conflictTerm /
 * conflictIndex, end of §5.3) and the leader's no-op entry (§5.4.2 and §8).
 *
 * <p><b>Threading.</b> Not thread-safe by design. Every method (RPC handlers, reply callbacks,
 * timers, {@link #propose}) must run on the node's single thread, which the {@link Scheduler}
 * and {@link Transport} guarantee. This class never creates threads or reads the clock, so the
 * same code runs on the deterministic simulator and in production.
 */
public final class RaftNode implements RaftRpcHandler {

    private static final Logger logger = LoggerFactory.getLogger(RaftNode.class);

    private static final int NOBODY = 0;

    public enum Role { FOLLOWER, CANDIDATE, LEADER }

    /** A snapshot of the node's state, for tests and the /status endpoint. */
    public record Status(int id, Role role, long term, int leaderId, long commitIndex, long lastApplied,
            long lastLogIndex) {
    }

    /** Where a proposed command was placed in the leader's log. */
    public record Proposal(long index, long term) {
    }

    private final int id;
    private final List<Integer> peers;
    private final RaftConfig config;
    private final Scheduler scheduler;
    private final Transport transport;
    private final RaftStorage storage;
    private final StateMachine stateMachine;
    private final Random random;

    // [Fig2 State] Persistent state on all servers (saved by RaftStorage before replying to RPCs).
    private long currentTerm;
    private int votedFor;
    private final RaftLog log;

    // [Fig2 State] Volatile state on all servers.
    private long commitIndex;
    private long lastApplied;

    // [Fig2 State] Volatile state on leaders, reinitialized after each election.
    private final Map<Integer, Long> nextIndex = new HashMap<>();
    private final Map<Integer, Long> matchIndex = new HashMap<>();

    private Role role = Role.FOLLOWER;
    private int leaderId = NOBODY;
    private final Set<Integer> votesGranted = new HashSet<>();
    private Scheduler.Cancellable electionTimer;
    private Scheduler.Cancellable heartbeatTimer;

    /**
     * @param peers   ids of the other servers in the cluster (not including {@code id})
     * @param storage opened storage; the node resumes from its recovered term, vote and log
     * @param random  source of election-timeout jitter (seeded in simulation)
     */
    public RaftNode(int id, List<Integer> peers, RaftConfig config, Scheduler scheduler, Transport transport,
            RaftStorage storage, StateMachine stateMachine, Random random) {
        this.id = id;
        this.peers = List.copyOf(peers);
        this.config = config;
        this.scheduler = scheduler;
        this.transport = transport;
        this.storage = storage;
        this.stateMachine = stateMachine;
        this.random = random;
        this.currentTerm = storage.currentTerm();
        this.votedFor = storage.votedFor();
        this.log = new RaftLog(storage.recoveredEntries());
    }

    /** Starts the node as a follower. */
    public void start() {
        resetElectionTimer();
    }

    /** Stops all timers. Used when the node shuts down or (in simulation) crashes. */
    public void stop() {
        cancelTimers();
    }

    // ================================================================== client entry point

    /**
     * [Fig2 Leader-2] "If command received from client: append entry to local log, respond after
     * entry applied to state machine." Appends the command and starts replicating it. The caller
     * learns the outcome when the entry at the returned index is applied. Returns empty if this
     * node is not the leader.
     */
    public Optional<Proposal> propose(byte[] command) {
        if (role != Role.LEADER) {
            return Optional.empty();
        }
        LogEntry entry = appendToOwnLog(EntryType.COMMAND, ByteString.copyFrom(command));
        sendAppendEntriesToAll();
        advanceCommitIndex(); // a single-node cluster commits immediately
        return Optional.of(new Proposal(entry.getIndex(), entry.getTerm()));
    }

    // ================================================================== RequestVote

    /** [Fig2 Follower-1] Respond to RPCs from candidates and leaders (every role answers). */
    @Override
    public RequestVoteReply handleRequestVote(RequestVoteRequest request) {
        long termBefore = currentTerm;
        int voteBefore = votedFor;

        // [Fig2 All-2] If RPC request contains term T > currentTerm: set currentTerm = T, convert to follower.
        if (request.getTerm() > currentTerm) {
            stepDown(request.getTerm());
        }

        boolean granted = false;
        // [Fig2 RequestVote-1] Reply false if term < currentTerm (after All-2, term <= currentTerm).
        if (request.getTerm() == currentTerm
                // [Fig2 RequestVote-2] If votedFor is null or candidateId, and candidate's log is at
                // least as up-to-date as receiver's log, grant vote.
                && (votedFor == NOBODY || votedFor == request.getCandidateId())
                && candidateLogIsUpToDate(request.getLastLogTerm(), request.getLastLogIndex())) {
            votedFor = request.getCandidateId();
            granted = true;
            // [Fig2 Follower-2] Granting a vote resets the election timer.
            resetElectionTimer();
        }

        // [Fig2 Persist] currentTerm and votedFor reach stable storage before we reply.
        if (currentTerm != termBefore || votedFor != voteBefore) {
            persistTermAndVote();
        }
        return RequestVoteReply.newBuilder().setTerm(currentTerm).setVoteGranted(granted).build();
    }

    /**
     * §5.4.1: the log with the later last term is more up-to-date; with equal last terms, the
     * longer log is. This is what guarantees Leader Completeness.
     */
    private boolean candidateLogIsUpToDate(long candidateLastTerm, long candidateLastIndex) {
        if (candidateLastTerm != log.lastTerm()) {
            return candidateLastTerm > log.lastTerm();
        }
        return candidateLastIndex >= log.lastIndex();
    }

    // ================================================================== AppendEntries (receiver)

    /** [Fig2 Follower-1] Respond to RPCs from candidates and leaders (every role answers). */
    @Override
    public AppendEntriesReply handleAppendEntries(AppendEntriesRequest request) {
        long termBefore = currentTerm;
        int voteBefore = votedFor;
        List<LogEntry> appended = new ArrayList<>();
        long[] truncatedFrom = {0};

        AppendEntriesReply reply = processAppendEntries(request, appended, truncatedFrom);

        // [Fig2 Persist] Term, vote and log changes reach stable storage before we reply, and
        // before anything is applied, so the state machine never gets ahead of the durable log.
        if (currentTerm != termBefore || votedFor != voteBefore || !appended.isEmpty() || truncatedFrom[0] != 0) {
            storage.save(currentTerm, votedFor, truncatedFrom[0], appended);
        }

        if (reply.getSuccess()) {
            // [Fig2 AppendEntries-5] If leaderCommit > commitIndex, set
            // commitIndex = min(leaderCommit, index of last new entry). Never move it backwards:
            // a delayed, older AppendEntries may name a smaller index.
            long lastNewEntry = request.getPrevLogIndex() + request.getEntriesCount();
            if (request.getLeaderCommit() > commitIndex) {
                commitIndex = Math.max(commitIndex, Math.min(request.getLeaderCommit(), lastNewEntry));
                applyCommittedEntries();
            }
        }
        return reply;
    }

    /** Steps 1-4 of the AppendEntries receiver. Records log changes in the two out-parameters. */
    private AppendEntriesReply processAppendEntries(AppendEntriesRequest request, List<LogEntry> appended,
            long[] truncatedFrom) {
        // [Fig2 All-2] If RPC request contains term T > currentTerm: set currentTerm = T, convert to follower.
        if (request.getTerm() > currentTerm) {
            stepDown(request.getTerm());
        }
        // [Fig2 AppendEntries-1] Reply false if term < currentTerm.
        if (request.getTerm() < currentTerm) {
            return appendReply(false, 0, 0);
        }

        // The sender is the leader of our current term.
        // [Fig2 Candidate-3] If AppendEntries RPC received from new leader: convert to follower.
        if (role != Role.FOLLOWER) {
            stepDown(currentTerm);
        }
        leaderId = request.getLeaderId();
        // [Fig2 Follower-2] Hearing from the current leader resets the election timer.
        resetElectionTimer();

        // [Fig2 AppendEntries-2] Reply false if log doesn't contain an entry at prevLogIndex whose
        // term matches prevLogTerm. Beyond Figure 2 (fast backtracking, end of §5.3): tell the
        // leader where to retry so it can skip a whole conflicting term per round trip.
        long prevIndex = request.getPrevLogIndex();
        if (prevIndex > log.lastIndex()) {
            return appendReply(false, 0, log.lastIndex() + 1); // our log is too short
        }
        if (log.termAt(prevIndex) != request.getPrevLogTerm()) {
            long conflictTerm = log.termAt(prevIndex);
            return appendReply(false, conflictTerm, log.firstIndexOfTerm(conflictTerm, prevIndex));
        }

        for (LogEntry entry : request.getEntriesList()) {
            if (entry.getIndex() <= log.lastIndex()) {
                if (log.termAt(entry.getIndex()) == entry.getTerm()) {
                    continue; // already have it; a delayed duplicate must not truncate anything
                }
                // [Fig2 AppendEntries-3] If an existing entry conflicts with a new one (same index
                // but different terms), delete the existing entry and all that follow it.
                log.truncateFrom(entry.getIndex());
                truncatedFrom[0] = entry.getIndex();
            }
            // [Fig2 AppendEntries-4] Append any new entries not already in the log.
            log.append(entry);
            appended.add(entry);
        }
        return appendReply(true, 0, 0);
    }

    private AppendEntriesReply appendReply(boolean success, long conflictTerm, long conflictIndex) {
        return AppendEntriesReply.newBuilder()
                .setTerm(currentTerm)
                .setSuccess(success)
                .setConflictTerm(conflictTerm)
                .setConflictIndex(conflictIndex)
                .build();
    }

    // ================================================================== elections

    private void resetElectionTimer() {
        if (electionTimer != null) {
            electionTimer.cancel();
        }
        // Randomized timeouts (§5.2) make split votes rare: usually one server times out first,
        // wins the election, and sends heartbeats before anyone else times out.
        int range = config.electionTimeoutMaxMs() - config.electionTimeoutMinMs();
        long timeout = config.electionTimeoutMinMs() + random.nextInt(range);
        electionTimer = scheduler.schedule(timeout, this::onElectionTimeout);
    }

    /**
     * [Fig2 Follower-2] If election timeout elapses without receiving AppendEntries from the
     * current leader or granting vote to candidate: convert to candidate.
     * [Fig2 Candidate-4] If election timeout elapses: start new election.
     */
    private void onElectionTimeout() {
        if (role != Role.LEADER) {
            startElection();
        }
    }

    /**
     * [Fig2 Candidate-1] On conversion to candidate, start election: increment currentTerm,
     * vote for self, reset election timer, send RequestVote RPCs to all other servers.
     */
    private void startElection() {
        role = Role.CANDIDATE;
        currentTerm++;
        votedFor = id;
        leaderId = NOBODY;
        votesGranted.clear();
        votesGranted.add(id);
        // [Fig2 Persist] The new term and self-vote must be durable before any RPC goes out;
        // otherwise a crash could let this node vote for someone else in the same term.
        persistTermAndVote();
        resetElectionTimer();
        logger.debug("node={} event=start_election term={}", id, currentTerm);

        if (isMajority(votesGranted.size())) {
            becomeLeader(); // single-node cluster
            return;
        }
        long electionTerm = currentTerm;
        RequestVoteRequest request = RequestVoteRequest.newBuilder()
                .setTerm(currentTerm)
                .setCandidateId(id)
                .setLastLogIndex(log.lastIndex())
                .setLastLogTerm(log.lastTerm())
                .build();
        for (int peer : peers) {
            transport.requestVote(peer, request, reply -> onRequestVoteReply(peer, electionTerm, reply));
        }
    }

    private void onRequestVoteReply(int peer, long electionTerm, RequestVoteReply reply) {
        // [Fig2 All-2] If RPC response contains term T > currentTerm: set currentTerm = T, convert to follower.
        if (reply.getTerm() > currentTerm) {
            stepDown(reply.getTerm());
            persistTermAndVote();
            return;
        }
        if (role != Role.CANDIDATE || currentTerm != electionTerm) {
            return; // a reply to an election we are no longer running
        }
        if (reply.getVoteGranted()) {
            votesGranted.add(peer);
            // [Fig2 Candidate-2] If votes received from majority of servers: become leader.
            if (isMajority(votesGranted.size())) {
                becomeLeader();
            }
        }
    }

    private void becomeLeader() {
        role = Role.LEADER;
        leaderId = id;
        if (electionTimer != null) {
            electionTimer.cancel();
            electionTimer = null;
        }
        // [Fig2 State] nextIndex starts at leader last log index + 1, matchIndex at 0.
        for (int peer : peers) {
            nextIndex.put(peer, log.lastIndex() + 1);
            matchIndex.put(peer, 0L);
        }
        logger.info("node={} event=became_leader term={} last_log_index={}", id, currentTerm, log.lastIndex());

        // Beyond Figure 2 (§5.4.2, §8): commit a no-op from our own term right away. A leader may
        // only commit entries from its current term by counting replicas (Leader-4), so without
        // this, entries from earlier terms (and reads) could wait until the next client write.
        appendToOwnLog(EntryType.NOOP, ByteString.EMPTY);

        // [Fig2 Leader-1] Upon election: send initial empty AppendEntries RPCs (heartbeat) to each
        // server; repeat during idle periods to prevent election timeouts. Here the first round
        // already carries the no-op.
        sendAppendEntriesToAll();
        scheduleHeartbeat();
        advanceCommitIndex(); // a single-node cluster commits immediately
    }

    private void scheduleHeartbeat() {
        heartbeatTimer = scheduler.schedule(config.heartbeatIntervalMs(), () -> {
            if (role == Role.LEADER) {
                sendAppendEntriesToAll(); // [Fig2 Leader-1] heartbeats during idle periods
                scheduleHeartbeat();
            }
        });
    }

    /**
     * Converts to follower. With a newer term, also adopts it and forgets the vote (it was for an
     * older term). The caller persists.
     */
    private void stepDown(long term) {
        if (term > currentTerm) {
            currentTerm = term;
            votedFor = NOBODY;
            leaderId = NOBODY;
        }
        if (role == Role.LEADER) {
            if (heartbeatTimer != null) {
                heartbeatTimer.cancel();
                heartbeatTimer = null;
            }
            resetElectionTimer(); // a leader had no election timer running
        }
        if (role != Role.FOLLOWER) {
            logger.debug("node={} event=step_down term={}", id, currentTerm);
        }
        role = Role.FOLLOWER;
    }

    private boolean isMajority(int count) {
        return count * 2 > peers.size() + 1;
    }

    // ================================================================== replication (leader)

    private LogEntry appendToOwnLog(EntryType type, ByteString command) {
        LogEntry entry = LogEntry.newBuilder()
                .setIndex(log.lastIndex() + 1)
                .setTerm(currentTerm)
                .setType(type)
                .setCommand(command)
                .build();
        log.append(entry);
        // The leader counts itself toward a majority (Leader-4), so its own copy must be durable first.
        storage.save(currentTerm, votedFor, 0, List.of(entry));
        return entry;
    }

    private void sendAppendEntriesToAll() {
        for (int peer : peers) {
            sendAppendEntries(peer);
        }
    }

    /**
     * [Fig2 Leader-3] If last log index >= nextIndex for a follower: send AppendEntries RPC with
     * log entries starting at nextIndex. With nothing new to send, this is a heartbeat.
     */
    private void sendAppendEntries(int peer) {
        long next = nextIndex.get(peer);
        long prevIndex = next - 1;
        long lastToSend = Math.min(log.lastIndex(), prevIndex + config.maxEntriesPerAppend());
        AppendEntriesRequest request = AppendEntriesRequest.newBuilder()
                .setTerm(currentTerm)
                .setLeaderId(id)
                .setPrevLogIndex(prevIndex)
                .setPrevLogTerm(log.termAt(prevIndex))
                .addAllEntries(log.slice(next, lastToSend))
                .setLeaderCommit(commitIndex)
                .build();
        transport.appendEntries(peer, request, reply -> onAppendEntriesReply(peer, request, reply));
    }

    private void onAppendEntriesReply(int peer, AppendEntriesRequest request, AppendEntriesReply reply) {
        // [Fig2 All-2] If RPC response contains term T > currentTerm: set currentTerm = T, convert to follower.
        if (reply.getTerm() > currentTerm) {
            stepDown(reply.getTerm());
            persistTermAndVote();
            return;
        }
        if (role != Role.LEADER || request.getTerm() != currentTerm) {
            return; // a reply from an earlier term
        }

        if (reply.getSuccess()) {
            // [Fig2 Leader-3] If successful: update nextIndex and matchIndex for follower.
            // Replies can arrive out of order, so never move either backwards.
            long match = request.getPrevLogIndex() + request.getEntriesCount();
            if (match > matchIndex.get(peer)) {
                matchIndex.put(peer, match);
            }
            nextIndex.put(peer, Math.max(nextIndex.get(peer), match + 1));
            advanceCommitIndex();
            if (nextIndex.get(peer) <= log.lastIndex()) {
                sendAppendEntries(peer); // the follower is still behind; keep going
            }
            return;
        }

        // [Fig2 Leader-3] If AppendEntries fails because of log inconsistency: decrement nextIndex
        // and retry. Fast backtracking (beyond Figure 2) jumps back a whole term at a time.
        long backedUp = Math.max(fastBackupNextIndex(reply), matchIndex.get(peer) + 1);
        if (backedUp < nextIndex.get(peer)) {
            nextIndex.put(peer, backedUp);
            sendAppendEntries(peer);
        }
        // Otherwise this is a stale failure for a position we already moved past; ignore it.
    }

    /**
     * Fast log backtracking, as described at the end of §5.3:
     * <ul>
     *   <li>follower's log too short (conflictTerm = 0): retry from the end of its log;
     *   <li>leader has entries from conflictTerm: retry just after its last entry of that term;
     *   <li>otherwise: skip the follower's whole conflicting term (retry from conflictIndex).
     * </ul>
     */
    private long fastBackupNextIndex(AppendEntriesReply reply) {
        if (reply.getConflictTerm() == 0) {
            return reply.getConflictIndex();
        }
        long lastOfTerm = log.lastIndexOfTerm(reply.getConflictTerm());
        return lastOfTerm > 0 ? lastOfTerm + 1 : reply.getConflictIndex();
    }

    /**
     * [Fig2 Leader-4] If there exists an N such that N > commitIndex, a majority of
     * matchIndex[i] >= N, and log[N].term == currentTerm: set commitIndex = N.
     */
    private void advanceCommitIndex() {
        for (long n = log.lastIndex(); n > commitIndex; n--) {
            // Only entries from the current term are committed by counting replicas (§5.4.2,
            // Figure 8). Earlier entries are then committed indirectly by the Log Matching
            // Property. Terms never decrease along the log, so we can stop at the first older one.
            if (log.termAt(n) != currentTerm) {
                break;
            }
            int replicas = 1; // the leader itself
            for (int peer : peers) {
                if (matchIndex.get(peer) >= n) {
                    replicas++;
                }
            }
            if (isMajority(replicas)) {
                commitIndex = n;
                applyCommittedEntries();
                return;
            }
        }
    }

    // ================================================================== applying

    /**
     * [Fig2 All-1] If commitIndex > lastApplied: increment lastApplied, apply log[lastApplied]
     * to state machine. Strictly in index order, each entry exactly once per incarnation.
     */
    private void applyCommittedEntries() {
        while (lastApplied < commitIndex) {
            lastApplied++;
            stateMachine.apply(log.get(lastApplied));
        }
    }

    // ================================================================== helpers

    private void persistTermAndVote() {
        storage.save(currentTerm, votedFor, 0, List.of());
    }

    private void cancelTimers() {
        if (electionTimer != null) {
            electionTimer.cancel();
            electionTimer = null;
        }
        if (heartbeatTimer != null) {
            heartbeatTimer.cancel();
            heartbeatTimer = null;
        }
    }

    public int id() {
        return id;
    }

    public Status status() {
        return new Status(id, role, currentTerm, leaderId, commitIndex, lastApplied, log.lastIndex());
    }

    /** Test support: the log entry at {@code index}. */
    LogEntry entryAt(long index) {
        return log.get(index);
    }
}
