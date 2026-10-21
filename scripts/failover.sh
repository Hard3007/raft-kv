#!/usr/bin/env bash
# Leader failover: N trials of "kill the leader, time the first successful write through the new
# one" on a fresh 5-container cluster with the default settings. Report in reports/bench/.
#   scripts/failover.sh [trials] [label]     (default 50; build the image first: scripts/docker-build.sh --local)
# ELECTION_TIMEOUT_MS (default 150-300) is passed to the nodes and recorded in the report.
set -euo pipefail
cd "$(dirname "$0")/.."
trials="${1:-50}"; label="${2:-failover}"
export ELECTION_TIMEOUT_MS="${ELECTION_TIMEOUT_MS:-150-300}"

./gradlew -q :bench:installDist
./gradlew -q --stop >/dev/null 2>&1 || true
docker compose -p raftkv down -v --remove-orphans >/dev/null 2>&1 || true
setup=$(mktemp); trap 'rm -f "$setup"' EXIT
scripts/describe-machine.sh > "$setup"

docker compose -p raftkv up -d >/dev/null 2>&1
for _ in $(seq 1 60); do
  [[ "$(docker compose -p raftkv ps --format '{{.Health}}' | grep -c '^healthy$' || true)" == 5 ]] && break
  sleep 1
done
JAVA_OPTS="-Xmx256m" bench/build/install/bench/bin/bench failover --label "$label" --trials "$trials" \
  --setup-file "$setup" --config "MAX_APPENDS_IN_FLIGHT=4 GROUP_COMMIT=true ELECTION_TIMEOUT_MS=$ELECTION_TIMEOUT_MS" \
  --command "scripts/failover.sh $trials $label"
report=$(ls -t reports/bench/failover-"$label"-*.md | head -1)
docker compose -p raftkv logs --no-color --timestamps 2>&1 | gzip > "${report%.md}-node-logs.txt.gz"
docker compose -p raftkv down -v >/dev/null 2>&1 || true
