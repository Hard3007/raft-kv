#!/usr/bin/env bash
# Docker-level chaos: start the 5-node cluster with fresh volumes, run a workload for N seconds
# while killing, pausing and disconnecting containers, then check the history.
#   scripts/docker-chaos.sh [seconds]      (default 60; build the image first: scripts/docker-build.sh --local)
set -euo pipefail
cd "$(dirname "$0")/.."
seconds="${1:-60}"

# Snapshot every 200 entries (production default: 10,000) so the run exercises snapshots,
# compaction and InstallSnapshot.
export SNAPSHOT_THRESHOLD="${SNAPSHOT_THRESHOLD:-200}"
docker compose -p raftkv down -v --remove-orphans >/dev/null 2>&1 || true
docker compose -p raftkv up -d
echo "== waiting for all 5 containers to be healthy"
for _ in $(seq 1 60); do
  healthy=$(docker compose -p raftkv ps --format '{{.Health}}' | grep -c '^healthy$' || true)
  [[ "$healthy" == 5 ]] && break
  sleep 1
done
docker compose -p raftkv ps --format 'table {{.Name}}\t{{.Status}}'

# Every report is named with the commit and a UTC timestamp, and never overwritten.
stamp="$(git rev-parse --short HEAD)$(git diff --quiet HEAD -- || echo -dirty)-$(date -u +%Y%m%dT%H%M%SZ)"
report="reports/chaos/docker-chaos-${seconds}s-${stamp}.md"
status=0
./gradlew dockerChaos -Pduration="$seconds" -Preport="$report" --console=plain -q || status=$?

# Keep every node's log next to the report (elections, snapshot installs, CheckQuorum step-downs).
docker compose -p raftkv logs --no-color --timestamps 2>&1 | gzip > "${report%.md}-node-logs.txt.gz" || true
echo "== after the run"
docker compose -p raftkv ps --format 'table {{.Name}}\t{{.Status}}'
for port in 8081 8082 8083 8084 8085; do curl -fsS "localhost:$port/status" || echo "node on $port not answering"; echo; done
exit $status
