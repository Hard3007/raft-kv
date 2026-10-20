package io.github.hard3007.raftkv.raft;

/**
 * Raft timing and batching parameters.
 *
 * @param electionTimeoutMinMs inclusive lower bound of the randomized election timeout
 * @param electionTimeoutMaxMs exclusive upper bound of the randomized election timeout
 * @param heartbeatIntervalMs  how often a leader sends AppendEntries when idle; must be well
 *                             below the election timeout so followers do not time out
 * @param maxEntriesPerAppend  most log entries sent in one AppendEntries
 */
public record RaftConfig(int electionTimeoutMinMs, int electionTimeoutMaxMs, int heartbeatIntervalMs,
        int maxEntriesPerAppend) {

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
    }

    /** The paper's suggested 150-300 ms election timeout, with a 50 ms heartbeat. */
    public static RaftConfig defaults() {
        return new RaftConfig(150, 300, 50, 64);
    }

    public RaftConfig withElectionTimeout(int minMs, int maxMs) {
        return new RaftConfig(minMs, maxMs, heartbeatIntervalMs, maxEntriesPerAppend);
    }

    public RaftConfig withMaxEntriesPerAppend(int max) {
        return new RaftConfig(electionTimeoutMinMs, electionTimeoutMaxMs, heartbeatIntervalMs, max);
    }
}
