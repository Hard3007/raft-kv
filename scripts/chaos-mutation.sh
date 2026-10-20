#!/usr/bin/env bash
# Deliberately breaks the system in one specific way, runs the chaos suite against the broken
# build, and restores the original source no matter what. Shows that the linearizability checker
# catches each bug. Usage:
#
#   scripts/chaos-mutation.sh <mutation> [runs]
#
# Mutations:
#   reads-without-readindex   the leader serves reads without the confirming heartbeat round
#   no-session-dedup          every copy of a write is applied (no exactly-once)
#   follower-reads            followers answer reads from their own state machine
#
# The report goes to reports/chaos/chaos-mutation-<mutation>-<runs>-seeds.md.
set -euo pipefail
cd "$(dirname "$0")/.."

mutation="${1:?usage: scripts/chaos-mutation.sh <mutation> [runs]}"
runs="${2:-100}"

case "$mutation" in
  reads-without-readindex)
    file=raft/src/main/java/io/github/hard3007/raftkv/raft/RaftNode.java
    # Step 2 of ReadIndex needs round heartbeatRound + 1; round 0 is "confirmed" immediately.
    patch='s/new PendingRead\(commitIndex, heartbeatRound \+ 1, callback\)/new PendingRead(commitIndex, 0, callback)/'
    ;;
  no-session-dedup)
    file=kv/src/main/java/io/github/hard3007/raftkv/kv/KvStateMachine.java
    patch='s/        if \(session.isPresent\(\)\) \{\n.*?\n        \}\n\n        boolean existed/        boolean existed/s'
    ;;
  follower-reads)
    file=kv/src/main/java/io/github/hard3007/raftkv/kv/KvService.java
    patch='s/(        raft.readIndex\(new RaftNode.ReadIndexCallback\(\) \{)/        if (raft.status().role() != RaftNode.Role.LEADER) {\n            answered[0] = true;\n            timeout.cancel();\n            Optional<byte[]> local = stateMachine.read(request.getKey().toByteArray());\n            GetReply.Builder builder = GetReply.newBuilder().setStatus(Status.OK).setFound(local.isPresent());\n            local.ifPresent(v -> builder.setValue(ByteString.copyFrom(v)));\n            reply.accept(builder.build());\n            return;\n        }\n$1/'
    ;;
  *)
    echo "unknown mutation: $mutation" >&2
    exit 2
    ;;
esac

backup="$(mktemp)"
cp "$file" "$backup"
trap 'cp "$backup" "$file"; rm -f "$backup"; echo "restored $file"' EXIT

perl -0pi -e "$patch" "$file"
if cmp -s "$file" "$backup"; then
  echo "mutation $mutation did not apply to $file" >&2
  exit 1
fi
echo "== mutation $mutation applied to $file:"
diff "$backup" "$file" || true

./gradlew chaos -Pruns="$runs" -Plabel="mutation-$mutation" -PexpectViolations --console=plain \
  | grep -E "^chaos:|LINEARIZABLE=|runs done$" || true
