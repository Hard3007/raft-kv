/**
 * Raft consensus (Figure 2 of the Raft paper): leader election, log replication, persistence.
 * Runs on an injected Scheduler/Clock and Transport so the same code runs on the deterministic
 * simulated network and on real gRPC.
 */
package io.github.hard3007.raftkv.raft;
