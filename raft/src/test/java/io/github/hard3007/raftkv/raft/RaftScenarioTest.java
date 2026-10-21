package io.github.hard3007.raftkv.raft;

import static io.github.hard3007.raftkv.raft.RaftCluster.entry;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.hard3007.raftkv.proto.raft.AppendEntriesReply;
import io.github.hard3007.raftkv.proto.raft.AppendEntriesRequest;
import io.github.hard3007.raftkv.proto.raft.LogEntry;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Hand-built scenarios that put the cluster into one specific dangerous state, using seeded
 * persistent state, per-node election timeouts, and a message filter.
 */
class RaftScenarioTest {

    private static final String SEEDS = "io.github.hard3007.raftkv.raft.Seeds#seeds";

    // These scenarios force one exact sequence of elections. PreVote changes which elections can
    // happen at all (an isolated node can no longer inflate its term), so it is off here; the
    // randomized Figure 8 tests in RaftPersistenceTest run with it on.
    private static final RaftConfig BASE = RaftConfig.defaults().withPreVote(false);
    private static final RaftConfig FAST = BASE.withElectionTimeout(60, 80);
    private static final RaftConfig SLOW = BASE.withElectionTimeout(400, 600);

    /**
     * Figure 8 of the Raft paper, made deterministic.
     *
     * <pre>
     *   index:      1    2
     *   S1        [t1] [t2]          becomes leader of term 4
     *   S2,S3,S4  [t1]               will receive t2 at index 2 from S1
     *   S5        [t1] [t3]          isolated at first
     * </pre>
     *
     * S1 wins term 4 and replicates its old term-2 entry to S2 and S3, so index 2 sits on a
     * majority. The filter drops every message carrying S1's own term-4 entries, so nothing from
     * term 4 reaches a majority. Then S1 crashes and S5 returns: S5's last term (3) beats
     * S2/S3/S4's (2), so S5 can win and overwrite index 2 with its term-3 entry.
     *
     * <p>A correct leader never commits index 2 here: it is from an older term, and Leader-4 only
     * commits current-term entries by counting. A leader that did commit it would apply "t2" at
     * index 2 and S5's term would later apply "t3" there; the harness catches that as a State
     * Machine Safety violation.
     */
    @ParameterizedTest(name = "seed {0}")
    @MethodSource(SEEDS)
    void figure8_leaderDoesNotCommitPreviousTermEntryByCountingReplicas(long seed) {
        // One entry per AppendEntries (DESIGN.md §2.5), and no group commit: the message filter
        // below decides from the receiver's log when a request is SENT. With group commit the
        // no-op leaves in its own flush message, which can be overtaken by the backed-up index-2
        // message and then (legitimately) accepted, so the Figure 8 situation is never set up.
        // The commit rule under test does not depend on group commit; the randomized Figure 8
        // test (RaftPersistenceTest) runs with it.
        RaftCluster cluster = new RaftCluster(5, seed, SLOW.withMaxEntriesPerAppend(1).withGroupCommit(false));
        cluster.setConfig(1, FAST.withMaxEntriesPerAppend(1).withGroupCommit(false));
        cluster.setConfig(5, BASE.withElectionTimeout(100, 150).withMaxEntriesPerAppend(1).withGroupCommit(false));
        LogEntry first = entry(1, 1, "a");
        cluster.seedPersistentState(1, 3, 0, List.of(first, entry(2, 2, "t2")));
        for (int i = 2; i <= 4; i++) {
            cluster.seedPersistentState(i, 3, 0, List.of(first));
        }
        cluster.seedPersistentState(5, 3, 0, List.of(first, entry(2, 3, "t3")));

        // Drop S1's AppendEntries carrying index 3 (its term-4 no-op) once the receiver already has
        // index 2. The first probe still gets through, fails, and makes S1 back up and send index 2.
        cluster.network.setFilter((from, to, message) -> from == 1
                && message instanceof AppendEntriesRequest request
                && request.getEntriesList().stream().anyMatch(e -> e.getIndex() == 3)
                && cluster.node(to) != null && cluster.node(to).status().lastLogIndex() >= 2);
        cluster.disconnect(5);
        cluster.startAll();

        // Wait until S1 leads term 4 and S2, S3 hold its term-2 entry: index 2 is on a majority.
        boolean setUp = cluster.sim.runUntil(() -> cluster.node(1).status().role() == RaftNode.Role.LEADER
                && cluster.node(2).status().lastLogIndex() == 2
                && cluster.node(3).status().lastLogIndex() == 2, 2_000);
        assertTrue(setUp, "scenario setup failed");
        cluster.sim.runFor(50); // plenty of time for S1 to (wrongly) commit index 2 if it were going to
        assertEquals(0, cluster.node(1).status().commitIndex(),
                "S1 must not commit its term-2 entry by counting replicas");

        // S1 crashes; S5 comes back and wins with its term-3 entry.
        cluster.crash(1);
        cluster.network.setFilter((from, to, message) -> false);
        cluster.connect(5);
        boolean s5Leads = cluster.sim.runUntil(() -> cluster.node(5).status().role() == RaftNode.Role.LEADER
                && cluster.nCommitted(2) >= 3, 5_000);
        assertTrue(s5Leads, "S5 should win and commit");
        assertEquals("t3", cluster.committedCommand(2).orElseThrow(), "S5's entry overwrote index 2");

        // S1 restarts and converges on the same log.
        cluster.restart(1);
        cluster.one(cluster.newCommand(), 5, true);
    }

    /**
     * Regression test for the bug found by Docker chaos. After {@code docker network disconnect}
     * and {@code connect}, a container's old IP address was reused by another container, so a
     * cached connection to node A actually reached node B. B answered, and the sender counted the
     * answer as A's: one node's acknowledgement (or vote) counted twice.
     *
     * <p>Here the leader can reach only node B, plus a stale route on which traffic addressed to
     * node A lands on B. The leader and B are 2 of 5 nodes, so nothing proposed now may commit.
     * If replies are attributed to the addressee, the leader counts B twice, sees 3 of 5, and
     * commits. The true majority (A, C, D) then elects a leader without that entry and puts a
     * different one at the same index: a State Machine Safety violation, which the harness catches.
     */
    @ParameterizedTest(name = "seed {0}")
    @MethodSource(SEEDS)
    void aReplyFromTheWrongNodeIsNeverCountedAsTheAddressedPeer(long seed) {
        RaftCluster cluster = new RaftCluster(5, seed).startAll();
        int leader = cluster.checkOneLeader();
        cluster.one(cluster.newCommand(), 5, true);
        int b = cluster.after(leader, 1);
        int a = cluster.after(leader, 2);
        int c = cluster.after(leader, 3);
        int d = cluster.after(leader, 4);

        cluster.disconnect(a);
        cluster.disconnect(c);
        cluster.disconnect(d);
        cluster.network.misdeliver(a, b); // the stale route: "node A" is really node B
        long commitBefore = cluster.node(leader).status().commitIndex();
        assertTrue(cluster.propose(leader, "on-two-nodes-only").isPresent());
        cluster.sim.runFor(1_000);
        assertEquals(commitBefore, cluster.node(leader).status().commitIndex(),
                "committed with real acknowledgements from only 2 of 5 nodes");

        // The real majority takes over; the uncommitted entry must simply disappear.
        cluster.crash(leader);
        cluster.crash(b);
        cluster.network.stopMisdelivering();
        cluster.connect(a);
        cluster.connect(c);
        cluster.connect(d);
        cluster.one(cluster.newCommand(), 3, true);
        cluster.restart(leader);
        cluster.restart(b);
        cluster.one(cluster.newCommand(), 5, true);
    }

    /**
     * Fast backtracking: a follower with 50 conflicting entries from one term is repaired in one
     * or two round trips, not fifty. Without conflictTerm/conflictIndex the leader would back up
     * one entry per rejected AppendEntries.
     */
    @ParameterizedTest(name = "seed {0}")
    @MethodSource(SEEDS)
    void fastBackup_skipsAWholeConflictingTermPerRoundTrip(long seed) {
        RaftCluster cluster = new RaftCluster(3, seed, SLOW);
        cluster.setConfig(1, FAST);
        List<LogEntry> leaderLog = new ArrayList<>(List.of(entry(1, 1, "a")));
        List<LogEntry> divergentLog = new ArrayList<>(List.of(entry(1, 1, "a")));
        for (int i = 2; i <= 51; i++) {
            leaderLog.add(entry(i, 3, "leader-" + i));
            divergentLog.add(entry(i, 2, "stale-" + i));
        }
        cluster.seedPersistentState(1, 3, 0, leaderLog);
        cluster.seedPersistentState(2, 3, 0, divergentLog);
        cluster.seedPersistentState(3, 3, 0, leaderLog);

        int[] rejectionsFromNode2 = {0};
        cluster.network.setFilter((from, to, message) -> {
            if (from == 2 && message instanceof AppendEntriesReply reply && !reply.getSuccess()) {
                rejectionsFromNode2[0]++;
            }
            return false;
        });
        cluster.startAll();

        boolean repaired = cluster.sim.runUntil(() -> cluster.nCommitted(52) == 3, 5_000);
        assertTrue(repaired, "node 2 never caught up");
        assertEquals("leader-51", cluster.committedCommand(51).orElseThrow());
        assertTrue(rejectionsFromNode2[0] <= 2,
                "expected at most 2 rejected AppendEntries, got " + rejectionsFromNode2[0]);
    }
}
