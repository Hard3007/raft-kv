#!/usr/bin/env bash
# Benchmarks against a fresh 5-container cluster on this machine. Reports go to reports/bench/.
#   scripts/bench.sh compare [label]  each optimization, before vs after, at the fixed configuration:
#                                     baseline -> +batching -> +pipelining -> +group commit (3 runs each)
#   scripts/bench.sh fixed [label]    the resume configuration only: 16 clients, 50% reads, 100 B values, 3 runs
#   scripts/bench.sh sweep [label]    1, 4, 16, 64 clients at 100 B, then 16 clients at 1 KB, 3 runs each
# Every fresh cluster: 60 s warm-up before its first run (the nodes' JIT), 10 s before each later
# run, 30 s measured. Override with CLUSTER_WARMUP_S, WARMUP_S, MEASURE_S, RUNS, READ_PERCENT (the
# report records the values). Passed to the nodes and recorded too: ELECTION_TIMEOUT_MS (default
# 150-300) and NODE_JVM_FLAGS (extra JVM flags, default none); BENCH_JVM_FLAGS go to the load
# generator. A `vmstat 1` log (and `iostat -x 1`, if installed) is kept next to every report.
# Build the image first: scripts/docker-build.sh --local
set -euo pipefail
cd "$(dirname "$0")/.."
mode="${1:?usage: scripts/bench.sh compare|fixed|sweep [label]}"
label="${2:-$mode}"
cluster_warmup="${CLUSTER_WARMUP_S:-60}"; warmup="${WARMUP_S:-10}"; measure="${MEASURE_S:-30}"
runs="${RUNS:-3}"; reads="${READ_PERCENT:-50}"
export ELECTION_TIMEOUT_MS="${ELECTION_TIMEOUT_MS:-150-300}" NODE_JVM_FLAGS="${NODE_JVM_FLAGS:-}"
bench_jvm_flags="${BENCH_JVM_FLAGS:-}"

./gradlew -q :bench:installDist
./gradlew -q --stop >/dev/null 2>&1 || true   # free the Gradle daemon's memory before measuring
docker compose -p raftkv down -v --remove-orphans >/dev/null 2>&1 || true
setup=$(mktemp); monitors=$(mktemp -d)
trap 'rm -rf "$setup" "$monitors"; kill $(jobs -p) 2>/dev/null || true' EXIT
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
  local config="MAX_APPENDS_IN_FLIGHT=$4 GROUP_COMMIT=$5 ELECTION_TIMEOUT_MS=$ELECTION_TIMEOUT_MS NODE_JVM_FLAGS='$NODE_JVM_FLAGS' BENCH_JVM_FLAGS='$bench_jvm_flags'"
  echo "== $1: $2 clients, $3 B values, $config"
  vmstat -t 1 > "$monitors/vmstat.txt" & local vmstat_pid=$!
  local iostat_pid=""
  if command -v iostat >/dev/null; then iostat -x -t 1 > "$monitors/iostat.txt" & iostat_pid=$!; fi
  JAVA_OPTS="-Xmx512m $bench_jvm_flags" bench/build/install/bench/bin/bench load --label "$1" --clients "$2" \
    --value-bytes "$3" --read-percent "$reads" --keys 1000 --cluster-warmup-s "$cluster_warmup" \
    --warmup-s "$warmup" --measure-s "$measure" --runs "$runs" --setup-file "$setup" --config "$config" \
    --command "scripts/bench.sh $mode $label (CLUSTER_WARMUP_S=$cluster_warmup WARMUP_S=$warmup MEASURE_S=$measure RUNS=$runs READ_PERCENT=$reads)"
  kill "$vmstat_pid" $iostat_pid 2>/dev/null || true
  # Keep the node logs (slow tasks, slow fsyncs, elections) and the vmstat/iostat logs next to the report.
  report=$(ls -t reports/bench/load-"$1"-*.md | head -1)
  docker compose -p raftkv logs --no-color --timestamps 2>&1 | gzip > "${report%.md}-node-logs.txt.gz"
  gzip -c "$monitors/vmstat.txt" > "${report%.md}-vmstat.txt.gz"
  if [[ -n "$iostat_pid" ]]; then gzip -c "$monitors/iostat.txt" > "${report%.md}-iostat.txt.gz"; fi
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
