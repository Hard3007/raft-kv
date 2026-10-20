package io.github.hard3007.raftkv.proto;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.protobuf.ByteString;
import io.github.hard3007.raftkv.proto.kv.KvCommand;
import io.github.hard3007.raftkv.proto.kv.PutOp;
import io.github.hard3007.raftkv.proto.raft.AppendEntriesRequest;
import io.github.hard3007.raftkv.proto.raft.EntryType;
import io.github.hard3007.raftkv.proto.raft.LogEntry;
import org.junit.jupiter.api.Test;

/** Smoke test: generated classes exist and a KvCommand survives a trip through a log entry. */
class ProtoRoundTripTest {

    @Test
    void kvCommandRoundTripsThroughAppendEntries() throws Exception {
        KvCommand command = KvCommand.newBuilder()
                .setClientId(42)
                .setSequence(7)
                .setPut(PutOp.newBuilder()
                        .setKey(ByteString.copyFromUtf8("k"))
                        .setValue(ByteString.copyFromUtf8("v")))
                .build();
        AppendEntriesRequest request = AppendEntriesRequest.newBuilder()
                .setTerm(3)
                .setLeaderId(1)
                .addEntries(LogEntry.newBuilder()
                        .setTerm(3)
                        .setIndex(10)
                        .setType(EntryType.COMMAND)
                        .setCommand(command.toByteString()))
                .build();

        AppendEntriesRequest decoded = AppendEntriesRequest.parseFrom(request.toByteArray());
        KvCommand decodedCommand = KvCommand.parseFrom(decoded.getEntries(0).getCommand());

        assertEquals(request, decoded);
        assertEquals(command, decodedCommand);
    }
}
