#!/usr/bin/env bash
# Benchmarks against a fresh 5-container cluster on this machine. Reports go to reports/bench/.
#   scripts/bench.sh compare [label]  each optimization, before vs after, at the fixed configuration:
#                                     baseline -> +batching -> +pipelining -> +group commit (3 runs each)
#   scripts/bench.sh fixed [label]    the resume configuration only: 16 clients, 50% reads, 100 B values, 3 runs
#   scripts/bench.sh sweep [label]    1, 4, 16, 64 clients at 100 B, then 16 clients at 1 KB, 3 runs each
# Every run: 10 s warm-up, 30 s measured. Override with WARMUP_S, MEASURE_S, RUNS, READ_PERCENT
# (sanity runs only; the report records the values). ELECTION_TIMEOUT_MS (default 150-300) is
# passed to the nodes and recorded too. Build the image first: scripts/docker-build.sh --local
set -euo pipefail
cd "$(dirname "$0")/.."
mode="${1:?usage: scripts/bench.sh compare|fixed|sweep [label]}"
label="${2:-$mode}"
warmup="${WARMUP_S:-10}"; measure="${MEASURE_S:-30}"; runs="${RUNS:-3}"; reads="${READ_PERCENT:-50}"
export ELECTION_TIMEOUT_MS="${ELECTION_TIMEOUT_MS:-150-300}"

./gradlew -q :bench:installDist
./gradlew -q --stop >/dev/null 2>&1 || true   # free the Gradle daemon's memory before measuring
docker compose -p raftkv down -v --remove-orphans >/dev/null 2>&1 || true
setup=$(mktemp); trap 'rm -f "$setup"' EXIT
scripts/describe-machine.sh > "$setup"         # includes an fsync probe, so the disk must be idle

fresh_cluster() { # fresh_cluster <max appends in flight> <group commit>
  export MAX_APPENDS_IN_FLIGHT="$1" GROUP_COMMIT="$2"
  docker compose -p raftkv down -v --remove-orphans >/dev/null 2>&1 || true
  docker compose -p raftkv up -d >/dev/null 2>&1
  for _ in $(seq 1 60); do
    [[ "$(docker compose -p raftkv ps --format '{{.Health}}' | grep -c '^healthy$' || true)" == 5 ]] && return
    sleep 1
  done
  echo "cluster did not become healthy" >&2; exit 1
}

load() { # load <label> <clients> <value bytes> <max appends in flight> <group commit>
  fresh_cluster "$4" "$5"
  local config="MAX_APPENDS_IN_FLIGHT=$4 GROUP_COMMIT=$5 ELECTION_TIMEOUT_MS=$ELECTION_TIMEOUT_MS"
  echo "== $1: $2 clients, $3 B values, $config"
  JAVA_OPTS="-Xmx512m" bench/build/install/bench/bin/bench load --label "$1" --clients "$2" --value-bytes "$3" \
    --read-percent "$reads" --keys 1000 --warmup-s "$warmup" --measure-s "$measure" --runs "$runs" \
    --setup-file "$setup" --config "$config" \
    --command "scripts/bench.sh $mode $label (WARMUP_S=$warmup MEASURE_S=$measure RUNS=$runs READ_PERCENT=$reads)"
  # Keep the node logs (slow tasks, slow fsyncs, elections) next to the report.
  report=$(ls -t reports/bench/load-"$1"-*.md | head -1)
  docker compose -p raftkv logs --no-color --timestamps 2>&1 | gzip > "${report%.md}-node-logs.txt.gz"
}

case "$mode" in
  compare) load "$label-1-baseline" 16 100 0 false        # Phase 2-6 replication, one fsync per proposal
           load "$label-2-batching" 16 100 1 false        # + one AppendEntries in flight per follower
           load "$label-3-pipelining" 16 100 4 false      # + up to 4 in flight
           load "$label-4-group-commit" 16 100 4 true ;;  # + one leader fsync per burst (the default)
  fixed)   load "$label" 16 100 4 true ;;
  sweep)   load "$label-100B" 1,4,16,64 100 4 true
           load "$label-1KB" 16 1024 4 true ;;
  *) echo "unknown mode $mode" >&2; exit 2 ;;
esac
docker compose -p raftkv down -v >/dev/null 2>&1 || true
