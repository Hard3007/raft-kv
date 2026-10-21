package io.github.hard3007.raftkv.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

class NodeConfigTest {

    private static final Map<String, String> MINIMAL = Map.of("NODE_ID", "2", "CLUSTER", "1=a:7000,2=b:7000,3=c:7000");

    @Test
    void defaultsAreThePapersTimeoutWithPipeliningAndGroupCommit() {
        NodeConfig config = NodeConfig.fromEnvironment(MINIMAL);
        assertEquals(2, config.id());
        assertEquals(3, config.cluster().size());
        assertEquals(150, config.raft().electionTimeoutMinMs());
        assertEquals(300, config.raft().electionTimeoutMaxMs());
        assertEquals(4, config.raft().maxAppendsInFlight());
        assertTrue(config.raft().groupCommit());
        assertEquals(10_000, config.raft().snapshotThreshold());
    }

    @Test
    void replicationAndTimeoutSettingsComeFromTheEnvironment() {
        Map<String, String> env = new java.util.HashMap<>(MINIMAL);
        env.put("MAX_APPENDS_IN_FLIGHT", "0");
        env.put("GROUP_COMMIT", "false");
        env.put("ELECTION_TIMEOUT_MS", "500-1000");
        NodeConfig config = NodeConfig.fromEnvironment(env);
        assertEquals(0, config.raft().maxAppendsInFlight());
        assertEquals(false, config.raft().groupCommit());
        assertEquals(500, config.raft().electionTimeoutMinMs());
        assertEquals(1000, config.raft().electionTimeoutMaxMs());
    }

    @Test
    void anElectionTimeoutBelowTheHeartbeatIsRejected() {
        Map<String, String> env = new java.util.HashMap<>(MINIMAL);
        env.put("ELECTION_TIMEOUT_MS", "40-80"); // heartbeat is 50 ms
        assertThrows(IllegalArgumentException.class, () -> NodeConfig.fromEnvironment(env));
    }
}
