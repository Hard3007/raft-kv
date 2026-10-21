#!/usr/bin/env bash
# Leader failover: N trials of "kill the leader, time the first successful write through the new
# one" on a fresh 5-container cluster with the default settings. Report in reports/bench/.
#   scripts/failover.sh [trials] [label]     (default 50; build the image first: scripts/docker-build.sh --local)
# Before the first trial: WARMUP_S (default 60) seconds of load, not recorded, to warm the JIT.
# ELECTION_TIMEOUT_MS (default 150-300) and NODE_JVM_FLAGS (default none) are passed to the nodes
# and recorded in the report. A `vmstat 1` log is kept next to it.
set -euo pipefail
cd "$(dirname "$0")/.."
trials="${1:-50}"; label="${2:-failover}"
warmup="${WARMUP_S:-60}"
export ELECTION_TIMEOUT_MS="${ELECTION_TIMEOUT_MS:-150-300}" NODE_JVM_FLAGS="${NODE_JVM_FLAGS:-}"

./gradlew -q :bench:installDist
./gradlew -q --stop >/dev/null 2>&1 || true
docker compose -p raftkv down -v --remove-orphans >/dev/null 2>&1 || true
setup=$(mktemp); vmstat_log=$(mktemp)
trap 'rm -f "$setup" "$vmstat_log"; kill $(jobs -p) 2>/dev/null || true' EXIT
scripts/describe-machine.sh > "$setup"

docker compose -p raftkv up -d >/dev/null 2>&1
for _ in $(seq 1 60); do
  [[ "$(docker compose -p raftkv ps --format '{{.Health}}' | grep -c '^healthy$' || true)" == 5 ]] && break
  sleep 1
done
vmstat -t 1 > "$vmstat_log" &
JAVA_OPTS="-Xmx256m" bench/build/install/bench/bin/bench failover --label "$label" --trials "$trials" \
  --warmup-s "$warmup" --setup-file "$setup" \
  --config "MAX_APPENDS_IN_FLIGHT=4 GROUP_COMMIT=true ELECTION_TIMEOUT_MS=$ELECTION_TIMEOUT_MS NODE_JVM_FLAGS='$NODE_JVM_FLAGS'" \
  --command "scripts/failover.sh $trials $label (WARMUP_S=$warmup)"
report=$(ls -t reports/bench/failover-"$label"-*.md | head -1)
docker compose -p raftkv logs --no-color --timestamps 2>&1 | gzip > "${report%.md}-node-logs.txt.gz"
gzip -c "$vmstat_log" > "${report%.md}-vmstat.txt.gz"
docker compose -p raftkv down -v >/dev/null 2>&1 || true
