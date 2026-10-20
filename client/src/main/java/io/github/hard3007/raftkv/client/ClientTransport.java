package io.github.hard3007.raftkv.client;

import io.github.hard3007.raftkv.proto.kv.DeleteReply;
import io.github.hard3007.raftkv.proto.kv.DeleteRequest;
import io.github.hard3007.raftkv.proto.kv.GetReply;
import io.github.hard3007.raftkv.proto.kv.GetRequest;
import io.github.hard3007.raftkv.proto.kv.PutReply;
import io.github.hard3007.raftkv.proto.kv.PutRequest;
import java.util.function.Consumer;

/**
 * Sends client requests to a server. {@code onReply} runs on the client's thread if a reply
 * arrives, and never if the request or reply is lost; {@link KvClient} handles timeouts.
 * Implemented by gRPC in production and by the simulated network in tests.
 */
public interface ClientTransport {

    void get(int server, GetRequest request, Consumer<GetReply> onReply);

    void put(int server, PutRequest request, Consumer<PutReply> onReply);

    void delete(int server, DeleteRequest request, Consumer<DeleteReply> onReply);
}
