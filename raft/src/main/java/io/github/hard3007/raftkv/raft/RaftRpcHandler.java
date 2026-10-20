package io.github.hard3007.raftkv.raft;

import io.github.hard3007.raftkv.proto.raft.AppendEntriesReply;
import io.github.hard3007.raftkv.proto.raft.AppendEntriesRequest;
import io.github.hard3007.raftkv.proto.raft.RequestVoteReply;
import io.github.hard3007.raftkv.proto.raft.RequestVoteRequest;

/** Incoming Raft RPCs. Called on the receiving node's thread; returns only after persisting. */
public interface RaftRpcHandler {

    RequestVoteReply handleRequestVote(RequestVoteRequest request);

    AppendEntriesReply handleAppendEntries(AppendEntriesRequest request);
}
