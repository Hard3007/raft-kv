package io.github.hard3007.raftkv.server;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.github.hard3007.raftkv.common.ExecutorScheduler;
import io.github.hard3007.raftkv.raft.RaftNode;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

/**
 * GET /status returns this node's Raft state as JSON, for the health check and the status page:
 * <pre>{"id":1,"role":"LEADER","term":3,"leaderId":1,"commitIndex":42,"lastApplied":42,"lastLogIndex":42}</pre>
 * The state is read on the node's thread (thread confinement); the HTTP thread just waits up to
 * a second for it, and answers 503 if the node does not respond.
 */
final class StatusServer implements AutoCloseable {

    private final HttpServer server;

    StatusServer(int port, RaftNode node, ExecutorScheduler nodeThread) throws IOException {
        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/status", exchange -> {
            try (exchange) {
                RaftNode.Status status;
                try {
                    status = nodeThread.call(node::status, 1000);
                } catch (Exception e) {
                    respond(exchange, 503, "{\"error\":\"node not responding\"}");
                    return;
                }
                respond(exchange, 200, toJson(status));
            }
        });
        server.start();
    }

    int port() {
        return server.getAddress().getPort();
    }

    static String toJson(RaftNode.Status s) {
        return "{\"id\":" + s.id() + ",\"role\":\"" + s.role() + "\",\"term\":" + s.term() + ",\"leaderId\":"
                + s.leaderId() + ",\"commitIndex\":" + s.commitIndex() + ",\"lastApplied\":" + s.lastApplied()
                + ",\"lastLogIndex\":" + s.lastLogIndex() + "}";
    }

    private static void respond(HttpExchange exchange, int code, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*"); // the status page polls this
        exchange.sendResponseHeaders(code, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
