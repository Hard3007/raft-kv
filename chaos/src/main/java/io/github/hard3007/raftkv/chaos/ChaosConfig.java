package io.github.hard3007.raftkv.chaos;

import io.github.hard3007.raftkv.client.KvClient;
import io.github.hard3007.raftkv.raft.RaftConfig;
import java.time.Duration;

/**
 * Parameters of one chaos run. All times are virtual (simulation) milliseconds except the
 * checker timeout, which is wall-clock time.
 *
 * @param nodes              cluster size
 * @param clients            concurrent clients, each with one outstanding operation at a time
 * @param keys               number of distinct keys (few, so clients collide on them)
 * @param operationsPerClient operations each client performs
 * @param maxThinkMs         pause between a client's operations: uniform in [0, maxThinkMs]
 * @param minFaultGapMs      shortest pause between two fault actions
 * @param maxFaultGapMs      longest pause between two fault actions
 * @param clientOptions      client timeouts; give-up must be short enough that ops end
 * @param checkerTimeout     wall-clock budget for the linearizability check of one run
 * @param powerLossOnCrash   every plain crash is a power loss; otherwise half are process crashes
 *                           (like docker kill: un-fsynced writes survive)
 * @param raft               Raft settings; snapshots are frequent and chunks tiny, so every run
 *                           exercises snapshotting, multi-chunk InstallSnapshot and compaction
 */
public record ChaosConfig(int nodes, int clients, int keys, int operationsPerClient, int maxThinkMs,
        int minFaultGapMs, int maxFaultGapMs, KvClient.Options clientOptions, Duration checkerTimeout,
        boolean powerLossOnCrash, RaftConfig raft) {

    public static ChaosConfig defaults() {
        return new ChaosConfig(5, 5, 3, 60, 300, 100, 400, new KvClient.Options(1000, 50, 4000),
                Duration.ofSeconds(10), true, RaftConfig.defaults().withSnapshotThreshold(20).withSnapshotChunkBytes(64));
    }

    /** Docker-like settings: process crashes too, 4 clients, a snapshot every 200 entries in one chunk. */
    public ChaosConfig dockerLike() {
        return new ChaosConfig(nodes, 4, keys, 1000, 50, minFaultGapMs, maxFaultGapMs, clientOptions, checkerTimeout, false,
                raft.withSnapshotThreshold(200).withSnapshotChunkBytes(256 * 1024));
    }

    public ChaosConfig withRaft(RaftConfig newRaft) {
        return new ChaosConfig(nodes, clients, keys, operationsPerClient, maxThinkMs, minFaultGapMs, maxFaultGapMs,
                clientOptions, checkerTimeout, powerLossOnCrash, newRaft);
    }

    public String describe() {
        return nodes + " nodes, " + clients + " clients, " + keys + " keys, " + operationsPerClient
                + " ops per client, think time 0-" + maxThinkMs + " ms, a fault action every " + minFaultGapMs + "-"
                + maxFaultGapMs + " ms (virtual), client attempt timeout " + clientOptions.attemptTimeoutMs()
                + " ms / give-up " + clientOptions.giveUpAfterMs() + " ms, checker timeout "
                + checkerTimeout.toSeconds() + " s (wall); PreVote " + (raft.preVote() ? "on" : "off")
                + ", CheckQuorum " + (raft.checkQuorum() ? "on" : "off") + ", a snapshot every "
                + raft.snapshotThreshold() + " entries in " + raft.snapshotChunkBytes() + "-byte InstallSnapshot chunks";
    }
}
