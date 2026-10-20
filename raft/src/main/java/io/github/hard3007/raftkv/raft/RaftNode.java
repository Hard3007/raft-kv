package io.github.hard3007.raftkv.raft;

import com.google.protobuf.ByteString;
import io.github.hard3007.raftkv.common.Scheduler;
import io.github.hard3007.raftkv.proto.raft.AppendEntriesReply;
import io.github.hard3007.raftkv.proto.raft.AppendEntriesRequest;
import io.github.hard3007.raftkv.proto.raft.EntryType;
import io.github.hard3007.raftkv.proto.raft.InstallSnapshotReply;
import io.github.hard3007.raftkv.proto.raft.InstallSnapshotRequest;
import io.github.hard3007.raftkv.proto.raft.LogEntry;
import io.github.hard3007.raftkv.proto.raft.RequestVoteReply;
import io.github.hard3007.raftkv.proto.raft.RequestVoteRequest;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
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
 * same code runs on the deterministic simulator and in production. Every entry point calls
 * {@link Scheduler#checkThread()}, so a call from any other thread fails immediately.
 */
public final class RaftNode implements RaftRpcHandler {

    private static final Logger logger = LoggerFactory.getLogger(RaftNode.class);

    private static final int NOBODY = 0;

    public enum Role { FOLLOWER, CANDIDATE, LEADER }

    /** A snapshot of the node's state, for tests and the /status endpoint. */
    public record Status(int id, Role role, long term, int leaderId, long commitIndex, long lastApplied,
            long lastLogIndex, long snapshotIndex, long snapshotsTaken, long snapshotsInstalled) {
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

    // PreVote (thesis §9.6): a round of "would you vote for me?" before a real election.
    private boolean preVoting;
    private long preVoteRound;
    private final Set<Integer> preVotesGranted = new HashSet<>();
    /** When we last accepted AppendEntries from a current leader (scheduler time; MIN_VALUE = never). */
    private long lastLeaderContactMillis = Long.MIN_VALUE;

    // CheckQuorum (thesis §6.2), leader only.
    private long leaderSinceMillis;
    /** Per peer: when we last got any reply from it in our current term. */
    private final Map<Integer, Long> lastHeardFromMillis = new HashMap<>();
    private Scheduler.Cancellable electionTimer;
    private Scheduler.Cancellable heartbeatTimer;
    private Runnable onLeadershipLost = () -> { };

    // ReadIndex state (leader only); see readIndex().
    private final Deque<ReadIndexCallback> readsAwaitingNoop = new ArrayDeque<>();
    private final Deque<PendingRead> readsAwaitingConfirmation = new ArrayDeque<>();
    /** Number of heartbeat rounds this node has started for reads. Never decreases. */
    private long heartbeatRound;
    /** Per peer: the newest round whose AppendEntries that peer answered in our current term. */
    private final Map<Integer, Long> acknowledgedRound = new HashMap<>();
    private boolean heartbeatRoundScheduled;

    /**
     * @param peers        ids of the other servers in the cluster (not including {@code id})
     * @param storage      opened storage; the node resumes from its recovered term, vote and log
     * @param appliedIndex index of the last entry the state machine has durably applied (0 for a
     *                     fresh or purely in-memory state machine); applying resumes after it
     * @param random       source of election-timeout jitter (seeded in simulation)
     */
    public RaftNode(int id, List<Integer> peers, RaftConfig config, Scheduler scheduler, Transport transport,
            RaftStorage storage, StateMachine stateMachine, long appliedIndex, Random random) {
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
        this.log = new RaftLog(storage.snapshotIndex(), storage.snapshotTerm(), storage.recoveredEntries());
        long applied = appliedIndex;

        // Restart (thesis §5.1): start from the snapshot, then replay only entries after it.
        Optional<SnapshotStore.Snapshot> snapshot = storage.snapshots().latest();
        if (snapshot.isPresent()) {
            SnapshotStore.Snapshot s = snapshot.get();
            if (s.index() > log.snapshotIndex()) {
                // We crashed after saving a snapshot but before compacting the log; finish the job
                // with Figure 13's rules 6-7.
                if (log.hasEntry(s.index(), s.term())) {
                    log.compactThrough(s.index());
                } else {
                    log.resetTo(s.index(), s.term());
                }
                storage.rewrite(log.snapshotIndex(), log.snapshotTerm(), currentTerm, votedFor,
                        log.entriesAfterSnapshot());
            }
            if (applied < s.index()) {
                stateMachine.restoreSnapshot(s.data(), s.index()); // the state machine is behind the snapshot
                applied = s.index();
            }
        }
        if (applied < log.snapshotIndex()) {
            // The log dropped entries that neither the state machine nor any snapshot covers: the
            // ordering "snapshot durable before the log prefix is discarded" was broken.
            throw new IllegalStateException("log compacted through " + log.snapshotIndex()
                    + " but the state only covers " + applied + ": committed entries are lost");
        }
        if (applied > log.lastIndex()) {
            // Impossible if entries are persisted before they are applied (see handleAppendEntries).
            throw new IllegalStateException("state machine applied index " + applied
                    + " is ahead of the durable Raft log (last index " + log.lastIndex() + ")");
        }
        // Everything the state machine applied was committed, so both indices can start there.
        this.commitIndex = applied;
        this.lastApplied = applied;
    }

    /** Runs on the node thread whenever this node stops being leader (to fail pending requests). Call before {@link #start()}. */
    public void setOnLeadershipLost(Runnable callback) {
        this.onLeadershipLost = callback;
    }

    /** Starts the node as a follower. */
    public void start() {
        scheduler.checkThread(); // thread confinement: every entry point runs on the node's thread
        resetElectionTimer();
    }

    /** Stops all timers. Used when the node shuts down or (in simulation) crashes. */
    public void stop() {
        scheduler.checkThread(); // thread confinement: every entry point runs on the node's thread
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
        scheduler.checkThread(); // thread confinement: every entry point runs on the node's thread
        if (role != Role.LEADER) {
            return Optional.empty();
        }
        LogEntry entry = appendToOwnLog(EntryType.COMMAND, ByteString.copyFrom(command));
        sendAppendEntriesToAll();
        if (peers.isEmpty()) {
            // A single-node cluster is its own majority. Commit (and apply) on the next task, never
            // inside propose(): the caller must be able to record the proposal before its entry
            // is applied, or it would miss its own reply.
            scheduler.schedule(0, () -> {
                scheduler.checkThread();
                if (role == Role.LEADER) {
                    advanceCommitIndex();
                }
            });
        }
        return Optional.of(new Proposal(entry.getIndex(), entry.getTerm()));
    }

    // ================================================================== linearizable reads (ReadIndex)

    /** Outcome of {@link #readIndex}. Exactly one method is called, on the node's thread. */
    public interface ReadIndexCallback {
        /** It is now safe to read the state machine: the result will be linearizable. */
        void onReady();

        /** This node is not (or stopped being) the leader. Reads change nothing, so retrying is safe. */
        void onNotLeader();
    }

    /** A read whose read index is fixed and which waits for leadership confirmation, then for apply. */
    private record PendingRead(long readIndex, long requiredRound, ReadIndexCallback callback) {
    }

    /**
     * Prepares a linearizable read without writing to the log (ReadIndex, Raft thesis §6.4):
     * <ol>
     *   <li>Only once the leader has committed an entry from its current term (its no-op), record
     *       readIndex = commitIndex. Before that, the leader may not know the latest committed
     *       entries, so the read waits.
     *   <li>Confirm leadership with a heartbeat round that starts after the read arrived: a
     *       majority must answer an AppendEntries sent in that round (or later) in our term. A
     *       newer leader could otherwise have committed writes this (deposed) node never saw.
     *   <li>Wait until lastApplied >= readIndex.
     *   <li>Call {@link ReadIndexCallback#onReady()}; the caller reads the state machine there.
     * </ol>
     */
    public void readIndex(ReadIndexCallback callback) {
        scheduler.checkThread(); // thread confinement: every entry point runs on the node's thread
        if (role != Role.LEADER) {
            callback.onNotLeader();
            return;
        }
        if (log.termAt(commitIndex) != currentTerm) {
            readsAwaitingNoop.add(callback); // step 1 waits for our no-op to commit
            return;
        }
        startRead(callback);
    }

    private void startRead(ReadIndexCallback callback) {
        // Step 1: the read index. Step 2 needs a round numbered after every round started so far.
        readsAwaitingConfirmation.add(new PendingRead(commitIndex, heartbeatRound + 1, callback));
        scheduleHeartbeatRound();
    }

    /**
     * Starts a new heartbeat round very soon. Reads that arrive before it starts share it, so a
     * burst of reads costs one round of AppendEntries, not one per read.
     */
    private void scheduleHeartbeatRound() {
        if (heartbeatRoundScheduled) {
            return;
        }
        heartbeatRoundScheduled = true;
        scheduler.schedule(0, () -> {
            scheduler.checkThread();
            heartbeatRoundScheduled = false;
            if (role == Role.LEADER) {
                heartbeatRound++;
                sendAppendEntriesToAll();
                serveReadsIfPossible(); // a single-node cluster is its own majority
            }
        });
    }

    private void startReadsWaitingForNoop() {
        if (log.termAt(commitIndex) != currentTerm) {
            return;
        }
        while (!readsAwaitingNoop.isEmpty()) {
            startRead(readsAwaitingNoop.poll());
        }
    }

    /** Steps 2-4 for every read whose round a majority has confirmed and whose index is applied. */
    private void serveReadsIfPossible() {
        // Reads are queued in order of both requiredRound and readIndex, so stop at the first
        // one that is not ready.
        while (!readsAwaitingConfirmation.isEmpty()) {
            PendingRead read = readsAwaitingConfirmation.peek();
            if (!majorityAcknowledged(read.requiredRound()) || lastApplied < read.readIndex()) {
                return;
            }
            readsAwaitingConfirmation.poll();
            read.callback().onReady();
        }
    }

    private boolean majorityAcknowledged(long round) {
        int acknowledged = 1; // the leader itself
        for (int peer : peers) {
            if (acknowledgedRound.get(peer) >= round) {
                acknowledged++;
            }
        }
        return isMajority(acknowledged);
    }

    private void failPendingReads() {
        List<ReadIndexCallback> failed = new ArrayList<>(readsAwaitingNoop);
        readsAwaitingConfirmation.forEach(read -> failed.add(read.callback()));
        readsAwaitingNoop.clear();
        readsAwaitingConfirmation.clear();
        failed.forEach(ReadIndexCallback::onNotLeader);
    }

    // ================================================================== RequestVote

    /** [Fig2 Follower-1] Respond to RPCs from candidates and leaders (every role answers). */
    @Override
    public RequestVoteReply handleRequestVote(RequestVoteRequest request) {
        scheduler.checkThread(); // thread confinement: every entry point runs on the node's thread
        if (request.getPreVote()) {
            return handlePreVote(request);
        }
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
     * PreVote receiver (thesis §9.6; not part of Figure 2). Grants only if a real election in
     * {@code request.term} could make sense: that term is newer than ours, the candidate's log is
     * at least as up-to-date as ours, and we have not heard from a current leader within the
     * minimum election timeout. Changes no state at all: no term update, no vote, no timer reset,
     * so nothing needs persisting. (All-2 does not apply: a PreVote's term is hypothetical.)
     */
    private RequestVoteReply handlePreVote(RequestVoteRequest request) {
        boolean granted = request.getTerm() > currentTerm
                && !heardFromLeaderRecently()
                && candidateLogIsUpToDate(request.getLastLogTerm(), request.getLastLogIndex());
        return RequestVoteReply.newBuilder().setTerm(currentTerm).setVoteGranted(granted).build();
    }

    /** True if we are the leader, or accepted AppendEntries from one within the minimum election timeout. */
    private boolean heardFromLeaderRecently() {
        return role == Role.LEADER || (lastLeaderContactMillis != Long.MIN_VALUE
                && scheduler.nowMillis() - lastLeaderContactMillis < config.electionTimeoutMinMs());
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
        scheduler.checkThread(); // thread confinement: every entry point runs on the node's thread
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
        lastLeaderContactMillis = scheduler.nowMillis(); // PreVote: there is a live leader
        preVoting = false;
        // [Fig2 Follower-2] Hearing from the current leader resets the election timer.
        resetElectionTimer();

        // Our log may start after a snapshot. Everything up to the snapshot point is committed, so
        // it matches the leader's log: skip that part of the request and check from there.
        long prevIndex = request.getPrevLogIndex();
        long prevTerm = request.getPrevLogTerm();
        List<LogEntry> entries = request.getEntriesList();
        if (prevIndex < log.snapshotIndex()) {
            long covered = log.snapshotIndex();
            entries = entries.stream().filter(entry -> entry.getIndex() > covered).toList();
            prevIndex = covered;
            prevTerm = log.snapshotTerm();
        }

        // [Fig2 AppendEntries-2] Reply false if log doesn't contain an entry at prevLogIndex whose
        // term matches prevLogTerm. Beyond Figure 2 (fast backtracking, end of §5.3): tell the
        // leader where to retry so it can skip a whole conflicting term per round trip.
        if (prevIndex > log.lastIndex()) {
            return appendReply(false, 0, log.lastIndex() + 1); // our log is too short
        }
        if (log.termAt(prevIndex) != prevTerm) {
            long conflictTerm = log.termAt(prevIndex);
            return appendReply(false, conflictTerm, log.firstIndexOfTerm(conflictTerm, prevIndex));
        }

        for (LogEntry entry : entries) {
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
        scheduler.checkThread(); // thread confinement: every entry point runs on the node's thread
        if (role != Role.LEADER) {
            if (config.preVote()) {
                startPreVote();
            } else {
                startElection();
            }
        }
    }

    /**
     * PreVote (thesis §9.6): before disturbing anyone with a higher term, ask whether a majority
     * would vote for us in term currentTerm + 1. Our own term and vote stay unchanged, so a node
     * cut off from the cluster cannot inflate its term while it is away, and cannot depose a
     * healthy leader when it comes back.
     */
    private void startPreVote() {
        preVoting = true;
        preVoteRound++;
        preVotesGranted.clear();
        preVotesGranted.add(id);
        leaderId = NOBODY; // our election timer fired: we no longer believe in a leader
        resetElectionTimer(); // if this round fails, the next timeout tries again
        logger.debug("node={} event=start_pre_vote term={}", id, currentTerm + 1);
        if (isMajority(preVotesGranted.size())) {
            startElection(); // single-node cluster
            return;
        }
        long round = preVoteRound;
        RequestVoteRequest request = RequestVoteRequest.newBuilder()
                .setTerm(currentTerm + 1)
                .setCandidateId(id)
                .setLastLogIndex(log.lastIndex())
                .setLastLogTerm(log.lastTerm())
                .setPreVote(true)
                .build();
        for (int peer : peers) {
            transport.requestVote(peer, request, reply -> onPreVoteReply(peer, round, reply));
        }
    }

    private void onPreVoteReply(int peer, long round, RequestVoteReply reply) {
        scheduler.checkThread(); // thread confinement: every entry point runs on the node's thread
        // [Fig2 All-2] A reply carrying a newer real term still means we are behind.
        if (reply.getTerm() > currentTerm) {
            stepDown(reply.getTerm());
            persistTermAndVote();
            return;
        }
        if (!preVoting || round != preVoteRound) {
            return; // a reply to a round we abandoned
        }
        if (reply.getVoteGranted()) {
            preVotesGranted.add(peer);
            if (isMajority(preVotesGranted.size())) {
                startElection(); // a majority would vote for us: now a real election
            }
        }
    }

    /**
     * [Fig2 Candidate-1] On conversion to candidate, start election: increment currentTerm,
     * vote for self, reset election timer, send RequestVote RPCs to all other servers.
     */
    private void startElection() {
        preVoting = false;
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
        scheduler.checkThread(); // thread confinement: every entry point runs on the node's thread
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
            acknowledgedRound.put(peer, 0L);
            lastHeardFromMillis.put(peer, scheduler.nowMillis());
        }
        leaderSinceMillis = scheduler.nowMillis();
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
            scheduler.checkThread();
            if (role == Role.LEADER && config.checkQuorum() && !heardFromMajorityRecently()) {
                // CheckQuorum (thesis §6.2): we may be cut off from the cluster. Step down rather
                // than keep acting as leader (clients then look for the real one).
                logger.info("node={} event=check_quorum_failed term={}", id, currentTerm);
                stepDown(currentTerm);
                leaderId = NOBODY;
                return;
            }
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
        boolean wasLeader = role == Role.LEADER;
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
        preVoting = false;
        transfers.clear();
        if (wasLeader) {
            failPendingReads();
            onLeadershipLost.run();
        }
    }

    /** CheckQuorum: has a majority (counting us) replied within an election timeout? */
    private boolean heardFromMajorityRecently() {
        long now = scheduler.nowMillis();
        if (now - leaderSinceMillis < config.electionTimeoutMaxMs()) {
            return true; // too early to judge
        }
        int recent = 1;
        for (int peer : peers) {
            if (now - lastHeardFromMillis.get(peer) <= config.electionTimeoutMaxMs()) {
                recent++;
            }
        }
        return isMajority(recent);
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
        if (next <= log.snapshotIndex()) {
            sendSnapshot(peer); // the entries this follower needs were compacted away
            return;
        }
        transfers.remove(peer);
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
        long round = heartbeatRound; // this message counts toward rounds up to and including this one
        transport.appendEntries(peer, request, reply -> onAppendEntriesReply(peer, request, round, reply));
    }

    private void onAppendEntriesReply(int peer, AppendEntriesRequest request, long round, AppendEntriesReply reply) {
        scheduler.checkThread(); // thread confinement: every entry point runs on the node's thread
        // [Fig2 All-2] If RPC response contains term T > currentTerm: set currentTerm = T, convert to follower.
        if (reply.getTerm() > currentTerm) {
            stepDown(reply.getTerm());
            persistTermAndVote();
            return;
        }
        if (role != Role.LEADER || request.getTerm() != currentTerm) {
            return; // a reply from an earlier term
        }

        // Any reply in our term, success or not, proves this peer still accepted us as leader
        // when it answered. That is what ReadIndex counts.
        lastHeardFromMillis.put(peer, scheduler.nowMillis()); // CheckQuorum: this peer is reachable
        if (round > acknowledgedRound.get(peer)) {
            acknowledgedRound.put(peer, round);
            serveReadsIfPossible();
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

    // ================================================================== snapshots: sending (leader)

    /** An InstallSnapshot transfer to one follower: one chunk in flight at a time. */
    private static final class SnapshotTransfer {
        final byte[] file;
        final long index;
        final long term;
        int offset;
        long sentAtMillis;

        SnapshotTransfer(byte[] file, long index, long term) {
            this.file = file;
            this.index = index;
            this.term = term;
        }
    }

    private final Map<Integer, SnapshotTransfer> transfers = new HashMap<>();

    /**
     * [Fig13 Leader] Sends the latest snapshot in chunks of {@link RaftConfig#snapshotChunkBytes()}.
     * Only one chunk is in flight. Heartbeats call this too: if the chunk's reply has not come
     * within two heartbeats, the same chunk is sent again (writing it twice is harmless).
     */
    private void sendSnapshot(int peer) {
        SnapshotTransfer transfer = transfers.get(peer);
        if (transfer == null) {
            SnapshotStore.Snapshot latest = storage.snapshots().latest().orElseThrow();
            transfer = new SnapshotTransfer(storage.snapshots().latestFileBytes(), latest.index(), latest.term());
            transfers.put(peer, transfer);
        } else if (scheduler.nowMillis() - transfer.sentAtMillis < 2L * config.heartbeatIntervalMs()) {
            return; // a chunk is in flight; wait for its reply
        }
        sendSnapshotChunk(peer, transfer);
    }

    private void sendSnapshotChunk(int peer, SnapshotTransfer transfer) {
        int end = Math.min(transfer.file.length, transfer.offset + config.snapshotChunkBytes());
        InstallSnapshotRequest request = InstallSnapshotRequest.newBuilder()
                .setTerm(currentTerm)
                .setLeaderId(id)
                .setLastIncludedIndex(transfer.index)
                .setLastIncludedTerm(transfer.term)
                .setOffset(transfer.offset)
                .setData(ByteString.copyFrom(transfer.file, transfer.offset, end - transfer.offset))
                .setDone(end == transfer.file.length)
                .build();
        transfer.sentAtMillis = scheduler.nowMillis();
        transport.installSnapshot(peer, request, reply -> onInstallSnapshotReply(peer, request, reply));
    }

    private void onInstallSnapshotReply(int peer, InstallSnapshotRequest request, InstallSnapshotReply reply) {
        scheduler.checkThread(); // thread confinement: every entry point runs on the node's thread
        // [Fig2 All-2] If RPC response contains term T > currentTerm: set currentTerm = T, convert to follower.
        if (reply.getTerm() > currentTerm) {
            stepDown(reply.getTerm());
            persistTermAndVote();
            return;
        }
        if (role != Role.LEADER || request.getTerm() != currentTerm) {
            return;
        }
        lastHeardFromMillis.put(peer, scheduler.nowMillis()); // CheckQuorum: this peer is reachable
        SnapshotTransfer transfer = transfers.get(peer);
        if (transfer == null || transfer.index != request.getLastIncludedIndex()
                || transfer.offset != request.getOffset()) {
            return; // a reply to a chunk we already moved past
        }
        if (!request.getDone()) {
            transfer.offset += request.getData().size();
            sendSnapshotChunk(peer, transfer);
            return;
        }
        // The last chunk was acknowledged. Figure 13's reply carries only the term, so this does
        // NOT prove the follower installed the snapshot (it may have been damaged in transit).
        // Move nextIndex past it and let AppendEntries' consistency check establish matchIndex.
        transfers.remove(peer);
        nextIndex.put(peer, Math.max(nextIndex.get(peer), transfer.index + 1));
        sendAppendEntries(peer);
    }

    // ================================================================== snapshots: receiving (follower)

    /**
     * [Fig13 InstallSnapshot RPC receiver]
     * 1. Reply immediately if term &lt; currentTerm.
     * 2-4. Create the snapshot file at offset 0, write each chunk at its offset, wait for more
     *      until done.
     * 5. Save the snapshot file (fsync + rename), replacing any older one.
     * 6. If an existing log entry has the same index and term as the snapshot's last included
     *    entry, retain the log entries following it.
     * 7. Otherwise discard the entire log.
     * 8. Reset the state machine using the snapshot contents.
     */
    @Override
    public InstallSnapshotReply handleInstallSnapshot(InstallSnapshotRequest request) {
        scheduler.checkThread(); // thread confinement: every entry point runs on the node's thread
        long termBefore = currentTerm;
        int voteBefore = votedFor;
        // [Fig2 All-2] If RPC request contains term T > currentTerm: set currentTerm = T, convert to follower.
        if (request.getTerm() > currentTerm) {
            stepDown(request.getTerm());
        }
        boolean fromCurrentLeader = request.getTerm() == currentTerm; // [Fig13 1] otherwise just reply
        if (fromCurrentLeader) {
            if (role != Role.FOLLOWER) {
                stepDown(currentTerm);
            }
            leaderId = request.getLeaderId();
            lastLeaderContactMillis = scheduler.nowMillis();
            preVoting = false;
            resetElectionTimer(); // [Fig2 Follower-2] the leader is alive
        }
        // [Fig2 Persist] before replying (and before touching the snapshot).
        if (currentTerm != termBefore || votedFor != voteBefore) {
            persistTermAndVote();
        }
        if (fromCurrentLeader) {
            receiveSnapshotChunk(request);
        }
        return InstallSnapshotReply.newBuilder().setTerm(currentTerm).build();
    }

    private void receiveSnapshotChunk(InstallSnapshotRequest request) {
        long index = request.getLastIncludedIndex();
        if (index <= commitIndex) {
            return; // we already have every entry it covers (Figure 13 keeps only newer snapshots)
        }
        SnapshotStore store = storage.snapshots();
        if (request.getOffset() == 0) {
            store.beginReceiving(index); // [Fig13 2]
        }
        if (!store.isReceiving(index) || request.getOffset() != store.receivedBytes()) {
            return; // a duplicate or out-of-order chunk; the leader resends in order
        }
        store.writeChunk(request.getData().toByteArray()); // [Fig13 3]
        if (!request.getDone()) {
            return; // [Fig13 4]
        }
        store.finishReceiving().ifPresent(this::installSnapshot); // [Fig13 5] fsync, check, rename
    }

    private void installSnapshot(SnapshotStore.Snapshot snapshot) {
        if (log.hasEntry(snapshot.index(), snapshot.term())) {
            log.compactThrough(snapshot.index()); // [Fig13 6] keep what follows it
        } else {
            log.resetTo(snapshot.index(), snapshot.term()); // [Fig13 7]
        }
        stateMachine.restoreSnapshot(snapshot.data(), snapshot.index()); // [Fig13 8] durable on return
        storage.rewrite(log.snapshotIndex(), log.snapshotTerm(), currentTerm, votedFor, log.entriesAfterSnapshot());
        commitIndex = Math.max(commitIndex, snapshot.index());
        lastApplied = snapshot.index();
        snapshotsInstalled++;
        logger.info("node={} event=snapshot_installed index={} term={}", id, snapshot.index(), snapshot.term());
    }

    // ================================================================== snapshots: taking one

    private long snapshotsTaken;
    private long snapshotsInstalled;

    /**
     * Once {@link RaftConfig#snapshotThreshold()} entries have been applied since the last
     * snapshot, snapshot the state machine and compact the log. The order is the whole point:
     * 1. the snapshot is saved durably (fsync + rename) first;
     * 2. only then is the log prefix it covers dropped, in memory and in the WAL (rewrite).
     * A crash between the two leaves an uncompacted log next to the snapshot; restart finishes
     * the job (see the constructor). The other order could lose committed entries.
     */
    private void maybeTakeSnapshot() {
        if (config.snapshotThreshold() == 0 || lastApplied - log.snapshotIndex() < config.snapshotThreshold()) {
            return;
        }
        long index = lastApplied;
        long term = log.termAt(index);
        storage.snapshots().save(index, term, stateMachine.takeSnapshot());
        log.compactThrough(index);
        storage.rewrite(log.snapshotIndex(), log.snapshotTerm(), currentTerm, votedFor, log.entriesAfterSnapshot());
        snapshotsTaken++;
        logger.debug("node={} event=snapshot_taken index={} term={}", id, index, term);
    }

    // ================================================================== applying

    /**
     * [Fig2 All-1] If commitIndex > lastApplied: increment lastApplied, apply log[lastApplied]
     * to state machine. Strictly in index order, each entry exactly once.
     */
    private void applyCommittedEntries() {
        while (lastApplied < commitIndex) {
            lastApplied++;
            stateMachine.apply(log.get(lastApplied));
        }
        maybeTakeSnapshot();
        if (role == Role.LEADER) {
            startReadsWaitingForNoop();
            serveReadsIfPossible();
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
        scheduler.checkThread(); // thread confinement: every entry point runs on the node's thread
        return new Status(id, role, currentTerm, leaderId, commitIndex, lastApplied, log.lastIndex(),
                log.snapshotIndex(), snapshotsTaken, snapshotsInstalled);
    }

    /** Test support: the log entry at {@code index}. */
    LogEntry entryAt(long index) {
        return log.get(index);
    }
}
