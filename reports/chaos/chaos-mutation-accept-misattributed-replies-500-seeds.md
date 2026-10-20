
## Mutation applied

This run used a deliberately broken build (`scripts/chaos-mutation.sh accept-misattributed-replies 500`).
Change to `raft/src/main/java/io/github/hard3007/raftkv/raft/RaftNode.java`:

```diff
@@ -570,9 +570,6 @@
 
     private void onPreVoteReply(int peer, long round, RequestVoteReply reply) {
         scheduler.checkThread(); // thread confinement: every entry point runs on the node's thread
-        if (reply.getResponderId() != peer) {
-            return; // answered by a different node than we asked (a stale route): never count it as that peer
-        }
         // [Fig2 All-2] A reply carrying a newer real term still means we are behind.
         if (reply.getTerm() > currentTerm) {
             stepDown(reply.getTerm());
@@ -626,9 +623,6 @@
 
     private void onRequestVoteReply(int peer, long electionTerm, RequestVoteReply reply) {
         scheduler.checkThread(); // thread confinement: every entry point runs on the node's thread
-        if (reply.getResponderId() != peer) {
-            return; // answered by a different node than we asked (a stale route): never count it as that peer
-        }
         // [Fig2 All-2] If RPC response contains term T > currentTerm: set currentTerm = T, convert to follower.
         if (reply.getTerm() > currentTerm) {
             stepDown(reply.getTerm());
@@ -798,9 +792,6 @@
 
     private void onAppendEntriesReply(int peer, AppendEntriesRequest request, long round, AppendEntriesReply reply) {
         scheduler.checkThread(); // thread confinement: every entry point runs on the node's thread
-        if (reply.getResponderId() != peer) {
-            return; // answered by a different node than we asked (a stale route): never count it as that peer
-        }
         // [Fig2 All-2] If RPC response contains term T > currentTerm: set currentTerm = T, convert to follower.
         if (reply.getTerm() > currentTerm) {
             stepDown(reply.getTerm());
@@ -939,9 +930,6 @@
 
     private void onInstallSnapshotReply(int peer, InstallSnapshotRequest request, InstallSnapshotReply reply) {
         scheduler.checkThread(); // thread confinement: every entry point runs on the node's thread
-        if (reply.getResponderId() != peer) {
-            return; // answered by a different node than we asked (a stale route): never count it as that peer
-        }
         // [Fig2 All-2] If RPC response contains term T > currentTerm: set currentTerm = T, convert to follower.
         if (reply.getTerm() > currentTerm) {
             stepDown(reply.getTerm());
```
