#!/usr/bin/env bash
# Builds the raft-kv image.
#   scripts/docker-build.sh            # verify the multi-arch build (amd64 + arm64), then load amd64 as raft-kv:local
#   scripts/docker-build.sh --local    # only build and load the image for this machine
set -euo pipefail
cd "$(dirname "$0")/.."

# Multi-platform builds need a BuildKit builder with the docker-container driver.
if ! docker buildx inspect raftkv >/dev/null 2>&1; then
  docker buildx create --name raftkv --driver docker-container >/dev/null
fi

if [[ "${1:-}" != "--local" ]]; then
  echo "== multi-arch build (linux/amd64, linux/arm64)"
  time docker buildx build --builder raftkv --platform linux/amd64,linux/arm64 -t raft-kv:multiarch .
fi
echo "== building and loading raft-kv:local for this machine"
time docker buildx build --builder raftkv --load -t raft-kv:local .
docker image ls raft-kv:local
