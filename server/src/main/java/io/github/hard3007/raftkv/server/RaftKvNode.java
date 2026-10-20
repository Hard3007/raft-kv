package io.github.hard3007.raftkv.server;

import io.github.hard3007.raftkv.common.ExecutorScheduler;
import io.github.hard3007.raftkv.kv.KvService;
import io.github.hard3007.raftkv.kv.KvStateMachine;
import io.github.hard3007.raftkv.raft.RaftNode;
import io.github.hard3007.raftkv.raft.RaftStorage;
import io.github.hard3007.raftkv.storage.BTree;
import io.github.hard3007.raftkv.storage.DiskFileSystem;
import io.github.hard3007.raftkv.storage.FileSystem;
import io.github.hard3007.raftkv.storage.FsyncPolicy;
import io.grpc.Grpc;
import io.grpc.InsecureServerCredentials;
import io.grpc.Server;
import io.grpc.ServerInterceptors;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * One running node: Raft + the KV state machine + gRPC + /status, all on one executor thread.
 *
 * <p>Threading: the {@link ExecutorScheduler} is the node's only thread for Raft and KV state.
 * gRPC handlers, gRPC reply callbacks, timers and /status all post onto it (see
 * {@link GrpcRaftTransport}, {@link RaftGrpcService}, {@link KvGrpcService}, {@link StatusServer}).
 */
public final class RaftKvNode implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(RaftKvNode.class);

    /** Applied entries between B-Tree syncs: bounds the log replay after a crash (DESIGN.md §3.1). */
    private static final long SYNC_EVERY_ENTRIES = 1000;

    private final NodeConfig config;
    private final ExecutorScheduler executor;
    private final GrpcRaftTransport transport;
    private final KvStateMachine stateMachine;
    private final RaftNode raft;
    private final Server grpcServer;
    private final StatusServer statusServer;
    private final FailureCountingInterceptor handlerFailures;

    private RaftKvNode(NodeConfig config) throws IOException {
        this.config = config;
        this.executor = new ExecutorScheduler("raft-node-" + config.id());
        this.transport = new GrpcRaftTransport(config.id(), config.cluster(), executor, config.rpcTimeoutMs());

        FileSystem disk = new DiskFileSystem(config.dataDirectory());
        RaftStorage storage = RaftStorage.open(disk, "raft.wal");
        this.stateMachine = KvStateMachine.open(disk, "kv", new BTree.Options(2048, FsyncPolicy.LAZY, 16L << 20),
                SYNC_EVERY_ENTRIES);

        List<Integer> peers = new ArrayList<>(config.cluster().keySet());
        peers.remove(Integer.valueOf(config.id()));
        this.raft = new RaftNode(config.id(), peers, config.raft(), executor, transport, storage, stateMachine,
                stateMachine.appliedIndex(), new Random());
        KvService kvService = new KvService(raft, stateMachine, executor, config.cluster(), KvService.Options.defaults());

        this.handlerFailures = new FailureCountingInterceptor(config.id());
        this.grpcServer = Grpc.newServerBuilderForPort(config.grpcPort(), InsecureServerCredentials.create())
                .addService(ServerInterceptors.intercept(new RaftGrpcService(raft, executor, transport::blockedPeers),
                        handlerFailures))
                .addService(ServerInterceptors.intercept(new KvGrpcService(kvService, executor), handlerFailures))
                .build()
                .start();
        this.statusServer = new StatusServer(config.statusPort(), raft, executor);
        executor.execute(raft::start);
        log.info("node={} event=started grpc_port={} status_port={} data_dir={} applied_index={}", config.id(),
                config.grpcPort(), statusServer.port(), config.dataDirectory(), stateMachine.appliedIndex());
    }

    /** Starts a node; it recovers from whatever is in its data directory. */
    public static RaftKvNode start(NodeConfig config) throws IOException {
        return new RaftKvNode(config);
    }

    public int id() {
        return config.id();
    }

    public int statusPort() {
        return statusServer.port();
    }

    /** The node's Raft state, read on its own thread. */
    public RaftNode.Status status() throws Exception {
        return executor.call(raft::status, 2000);
    }

    /**
     * Tasks on the node's thread that threw, plus gRPC handlers that threw. Thread-confinement
     * violations show up here wherever they happen. Tests assert this stays 0.
     */
    public long failedTasks() {
        return executor.failedTasks() + handlerFailures.failures();
    }

    public GrpcRaftTransport transport() {
        return transport;
    }

    /**
     * Stops the node abruptly, like a killed process: no checkpoint, no clean close of files.
     * Waits until its thread has stopped, so a restart never runs alongside it.
     */
    public void crash() {
        grpcServer.shutdownNow();
        statusServer.close();
        transport.close();
        executor.close();
        awaitServer();
    }

    /** Graceful stop: the B-Tree is checkpointed so the next start replays nothing. */
    @Override
    public void close() {
        grpcServer.shutdown();
        statusServer.close();
        try {
            executor.call(() -> {
                raft.stop();
                stateMachine.close();
                return null;
            }, 10_000);
        } catch (Exception e) {
            // Not fatal: the data is still recoverable from the Raft log, like after a crash.
            log.error("node={} event=clean_close_failed", config.id(), e);
        }
        transport.close();
        executor.close();
        awaitServer();
    }

    private void awaitServer() {
        try {
            grpcServer.awaitTermination(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
