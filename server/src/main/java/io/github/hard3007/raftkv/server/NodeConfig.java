package io.github.hard3007.raftkv.server;

import io.github.hard3007.raftkv.raft.RaftConfig;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;

/**
 * Everything one node needs to start.
 *
 * @param id            this node's id (1..n)
 * @param cluster       every node's gRPC address ("host:port"), including this one; the same port
 *                      serves Raft peers and clients
 * @param grpcPort      port this node's gRPC server listens on
 * @param statusPort    port of the HTTP /status endpoint (0 = any free port)
 * @param dataDirectory where the Raft log and the B-Tree live
 * @param rpcTimeoutMs  deadline for every Raft RPC
 */
public record NodeConfig(int id, Map<Integer, String> cluster, int grpcPort, int statusPort, Path dataDirectory,
        RaftConfig raft, long rpcTimeoutMs) {

    public NodeConfig {
        cluster = Map.copyOf(cluster);
        if (!cluster.containsKey(id)) {
            throw new IllegalArgumentException("cluster does not include this node (" + id + ")");
        }
    }

    /**
     * Reads the configuration from environment variables:
     * <pre>
     *   NODE_ID=1
     *   CLUSTER=1=node1:7000,2=node2:7000,3=node3:7000,4=node4:7000,5=node5:7000
     *   GRPC_PORT=7000  STATUS_PORT=8080  DATA_DIR=/data  SNAPSHOT_THRESHOLD=10000
     *   MAX_APPENDS_IN_FLIGHT=4  GROUP_COMMIT=true   (replication settings; see RaftConfig)
     *   ELECTION_TIMEOUT_MS=150-300                  (min-max; raise it only for slow disks, see BENCHMARKS.md)
     * </pre>
     */
    public static NodeConfig fromEnvironment(Map<String, String> env) {
        int id = Integer.parseInt(required(env, "NODE_ID"));
        Map<Integer, String> cluster = new TreeMap<>();
        for (String member : required(env, "CLUSTER").split(",")) {
            String[] parts = member.trim().split("=", 2);
            cluster.put(Integer.parseInt(parts[0]), parts[1]);
        }
        String[] timeout = env.getOrDefault("ELECTION_TIMEOUT_MS", "150-300").split("-", 2);
        return new NodeConfig(id, cluster,
                Integer.parseInt(env.getOrDefault("GRPC_PORT", "7000")),
                Integer.parseInt(env.getOrDefault("STATUS_PORT", "8080")),
                Path.of(env.getOrDefault("DATA_DIR", "/data")),
                RaftConfig.defaults()
                        .withElectionTimeout(Integer.parseInt(timeout[0].trim()), Integer.parseInt(timeout[1].trim()))
                        .withSnapshotThreshold(Long.parseLong(env.getOrDefault("SNAPSHOT_THRESHOLD", "10000")))
                        .withMaxAppendsInFlight(Integer.parseInt(env.getOrDefault("MAX_APPENDS_IN_FLIGHT",
                                String.valueOf(RaftConfig.defaults().maxAppendsInFlight()))))
                        .withGroupCommit(Boolean.parseBoolean(env.getOrDefault("GROUP_COMMIT",
                                String.valueOf(RaftConfig.defaults().groupCommit())))),
                Long.parseLong(env.getOrDefault("RPC_TIMEOUT_MS", "500")));
    }

    private static String required(Map<String, String> env, String name) {
        String value = env.get(name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("missing environment variable " + name);
        }
        return value;
    }
}
