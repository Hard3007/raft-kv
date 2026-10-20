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
 */
public record RaftConfig(int electionTimeoutMinMs, int electionTimeoutMaxMs, int heartbeatIntervalMs,
        int maxEntriesPerAppend, boolean preVote, boolean checkQuorum, long snapshotThreshold, int snapshotChunkBytes) {

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
    }

    /**
     * The paper's 150-300 ms election timeout, a 50 ms heartbeat, PreVote and CheckQuorum on,
     * and snapshots off (state machines opt in with {@link #withSnapshotThreshold}).
     */
    public static RaftConfig defaults() {
        return new RaftConfig(150, 300, 50, 64, true, true, 0, 256 * 1024);
    }

    public RaftConfig withElectionTimeout(int minMs, int maxMs) {
        return new RaftConfig(minMs, maxMs, heartbeatIntervalMs, maxEntriesPerAppend, preVote, checkQuorum,
                snapshotThreshold, snapshotChunkBytes);
    }

    public RaftConfig withMaxEntriesPerAppend(int max) {
        return new RaftConfig(electionTimeoutMinMs, electionTimeoutMaxMs, heartbeatIntervalMs, max, preVote,
                checkQuorum, snapshotThreshold, snapshotChunkBytes);
    }

    public RaftConfig withPreVote(boolean enabled) {
        return new RaftConfig(electionTimeoutMinMs, electionTimeoutMaxMs, heartbeatIntervalMs, maxEntriesPerAppend,
                enabled, checkQuorum, snapshotThreshold, snapshotChunkBytes);
    }

    public RaftConfig withCheckQuorum(boolean enabled) {
        return new RaftConfig(electionTimeoutMinMs, electionTimeoutMaxMs, heartbeatIntervalMs, maxEntriesPerAppend,
                preVote, enabled, snapshotThreshold, snapshotChunkBytes);
    }

    public RaftConfig withSnapshotThreshold(long entries) {
        return new RaftConfig(electionTimeoutMinMs, electionTimeoutMaxMs, heartbeatIntervalMs, maxEntriesPerAppend,
                preVote, checkQuorum, entries, snapshotChunkBytes);
    }

    public RaftConfig withSnapshotChunkBytes(int bytes) {
        return new RaftConfig(electionTimeoutMinMs, electionTimeoutMaxMs, heartbeatIntervalMs, maxEntriesPerAppend,
                preVote, checkQuorum, snapshotThreshold, bytes);
    }
}
