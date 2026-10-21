package io.github.hard3007.raftkv.raft;

/**
 * Raft timing, batching and snapshot parameters.
 *
 * @param electionTimeoutMinMs inclusive lower bound of the randomized election timeout
 * @param electionTimeoutMaxMs exclusive upper bound of the randomized election timeout
 * @param heartbeatIntervalMs  how often a leader sends AppendEntries when idle; must be well
 *                             below the election timeout so followers do not time out
 * @param maxEntriesPerAppend  most log entries sent in one AppendEntries
 * @param preVote              run a PreVote round before every real election (thesis §9.6)
 * @param checkQuorum          a leader that has not heard from a majority within an election
 *                             timeout steps down (thesis §6.2)
 * @param snapshotThreshold    take a snapshot once this many applied entries follow the last one
 *                             (0 = never; the state machine must then not need snapshots)
 * @param snapshotChunkBytes   InstallSnapshot chunk size (well under gRPC's 4 MB message limit)
 * @param maxAppendsInFlight   most unanswered AppendEntries carrying entries per follower:
 *                             1 = batching (one request at a time; entries proposed meanwhile
 *                             go in the next one), more = pipelining, 0 = no limit (every
 *                             proposal re-sends everything from nextIndex: the Phase 2-6
 *                             behavior, kept as the benchmark baseline)
 * @param groupCommit          the leader writes all entries proposed in one burst with ONE fsync
 *                             (a flush task) instead of one fsync per proposal
 */
public record RaftConfig(int electionTimeoutMinMs, int electionTimeoutMaxMs, int heartbeatIntervalMs,
        int maxEntriesPerAppend, boolean preVote, boolean checkQuorum, long snapshotThreshold, int snapshotChunkBytes,
        int maxAppendsInFlight, boolean groupCommit) {

    public RaftConfig {
        if (electionTimeoutMinMs <= 0 || electionTimeoutMaxMs <= electionTimeoutMinMs) {
            throw new IllegalArgumentException("need 0 < electionTimeoutMin < electionTimeoutMax");
        }
        if (heartbeatIntervalMs <= 0 || heartbeatIntervalMs >= electionTimeoutMinMs) {
            throw new IllegalArgumentException("heartbeat interval must be below the election timeout");
        }
        if (maxEntriesPerAppend < 1) {
            throw new IllegalArgumentException("maxEntriesPerAppend must be at least 1");
        }
        if (snapshotThreshold < 0 || snapshotChunkBytes < 1) {
            throw new IllegalArgumentException("bad snapshot settings");
        }
        if (maxAppendsInFlight < 0) {
            throw new IllegalArgumentException("maxAppendsInFlight must be 0 (no limit) or more");
        }
    }

    /**
     * The paper's 150-300 ms election timeout, a 50 ms heartbeat, PreVote and CheckQuorum on,
     * snapshots off (state machines opt in with {@link #withSnapshotThreshold}), pipelining with
     * up to 4 AppendEntries in flight per follower, and group commit.
     */
    public static RaftConfig defaults() {
        return new RaftConfig(150, 300, 50, 64, true, true, 0, 256 * 1024, 4, true);
    }

    public RaftConfig withElectionTimeout(int minMs, int maxMs) {
        return new RaftConfig(minMs, maxMs, heartbeatIntervalMs, maxEntriesPerAppend, preVote, checkQuorum,
                snapshotThreshold, snapshotChunkBytes, maxAppendsInFlight, groupCommit);
    }

    public RaftConfig withMaxEntriesPerAppend(int max) {
        return new RaftConfig(electionTimeoutMinMs, electionTimeoutMaxMs, heartbeatIntervalMs, max, preVote,
                checkQuorum, snapshotThreshold, snapshotChunkBytes, maxAppendsInFlight, groupCommit);
    }

    public RaftConfig withPreVote(boolean enabled) {
        return new RaftConfig(electionTimeoutMinMs, electionTimeoutMaxMs, heartbeatIntervalMs, maxEntriesPerAppend,
                enabled, checkQuorum, snapshotThreshold, snapshotChunkBytes, maxAppendsInFlight, groupCommit);
    }

    public RaftConfig withCheckQuorum(boolean enabled) {
        return new RaftConfig(electionTimeoutMinMs, electionTimeoutMaxMs, heartbeatIntervalMs, maxEntriesPerAppend,
                preVote, enabled, snapshotThreshold, snapshotChunkBytes, maxAppendsInFlight, groupCommit);
    }

    public RaftConfig withSnapshotThreshold(long entries) {
        return new RaftConfig(electionTimeoutMinMs, electionTimeoutMaxMs, heartbeatIntervalMs, maxEntriesPerAppend,
                preVote, checkQuorum, entries, snapshotChunkBytes, maxAppendsInFlight, groupCommit);
    }

    public RaftConfig withSnapshotChunkBytes(int bytes) {
        return new RaftConfig(electionTimeoutMinMs, electionTimeoutMaxMs, heartbeatIntervalMs, maxEntriesPerAppend,
                preVote, checkQuorum, snapshotThreshold, bytes, maxAppendsInFlight, groupCommit);
    }

    public RaftConfig withMaxAppendsInFlight(int max) {
        return new RaftConfig(electionTimeoutMinMs, electionTimeoutMaxMs, heartbeatIntervalMs, maxEntriesPerAppend,
                preVote, checkQuorum, snapshotThreshold, snapshotChunkBytes, max, groupCommit);
    }

    public RaftConfig withGroupCommit(boolean enabled) {
        return new RaftConfig(electionTimeoutMinMs, electionTimeoutMaxMs, heartbeatIntervalMs, maxEntriesPerAppend,
                preVote, checkQuorum, snapshotThreshold, snapshotChunkBytes, maxAppendsInFlight, enabled);
    }
}
