# Docker chaos, 180 s, after Phase 6 (commit 43a3adb): an open violation

Machine: GitHub Codespace, Linux 6.8.0-1064-azure amd64, 2 cores (AMD EPYC 7763), Docker 29.9.1-1.
Command: `scripts/docker-chaos.sh 180` (SNAPSHOT_THRESHOLD=200, image rebuilt from 43a3adb).

## Run 1: NOT_LINEARIZABLE (unexplained, open)

Console output, as captured:

```
final term 50 (highest current term across nodes after the run)
verdict NOT_LINEARIZABLE: 8141 operations (8102 completed, 17 pending writes, 22 reads left out);
faults {KILL=21, PAUSE=17, DISCONNECT=18}; peak memory {raftkv-node1-1=133.6, raftkv-node2-1=115.9,
raftkv-node3-1=123.7, raftkv-node4-1=129.0, raftkv-node5-1=115.5} (limit 384MiB)
```

From the report (since overwritten by run 2): the failing key was `k1`, checked in 210 ms. At the
end, every node had compacted its log through index 4002-4004 with lastApplied 4022, and the
per-process InstallSnapshot counters were 2, 1, 0, 1, 1.

At that time the Docker driver did not save histories or node logs, so **there is no witness for
this run**. It has since been changed to write the earliest-violation witness
(`docker-chaos-<N>s-violation.txt`) and all node logs (`docker-chaos-<N>s-node-logs.txt`).

## Run 2: LINEARIZABLE

```
final term 22 (highest current term across nodes after the run)
verdict LINEARIZABLE: 5234 operations (5203 completed, 20 pending writes, 11 reads left out);
faults {KILL=17, PAUSE=15, DISCONNECT=16}; peak memory {raftkv-node1-1=114.1, raftkv-node2-1=139.3,
raftkv-node3-1=132.0, raftkv-node4-1=109.9, raftkv-node5-1=99.54} (limit 384MiB)
```

Its node logs are in `docker-chaos-180s-phase6-run2-node-logs.txt.gz`. Its Markdown report was
accidentally overwritten while renaming files; the numbers above are the captured console output.

## Status

A passing rerun does not explain run 1. Docker runs are not deterministic, so run 1 cannot be
replayed. What has been ruled out so far:

- 10,000 simulated chaos seeds, all LINEARIZABLE (with snapshots, mid-I/O crashes and pauses).
- 15 extra in-process real-gRPC chaos runs of about 1,600 operations each, with node-thread freezes,
  crashes, isolations and dropped connections, all LINEARIZABLE.
- Reviewing the ReadIndex, CheckQuorum, PreVote and InstallSnapshot paths for the pause and kill
  cases that only Docker produces found no flaw.

Next step: reproduce with the witness and logs enabled (repeated Docker runs), then explain and fix.
