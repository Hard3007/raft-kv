#!/usr/bin/env bash
# Prints a Markdown description of this machine for benchmark reports: VM shape (on Oracle
# Cloud), CPU, memory, the disk under Docker's data directory, its fsync latency (measured now,
# on a Docker volume, with the raft-kv:local image), versions, and the fsync settings.
#   scripts/describe-machine.sh > setup.md      (run it while the cluster is down: an idle disk)
set -uo pipefail

shape=$(curl -fsS -m 1 -H "Authorization: Bearer Oracle" http://169.254.169.254/opc/v2/instance/shape 2>/dev/null \
  || echo "not an Oracle Cloud VM (no instance metadata)")
cpu=$(lscpu | awk -F: '/^Model name/ {gsub(/^ +/, "", $2); print $2; exit}')
cores=$(nproc)
mem=$(free -m | awk '/^Mem:/ {print $2 " MB total, " $7 " MB available"}')
docker_root=$(docker info --format '{{.DockerRootDir}}' 2>/dev/null || echo /var/lib/docker)
disk_dev=$(df --output=source "$docker_root" 2>/dev/null | tail -1)
disk_fs=$(df --output=fstype "$docker_root" 2>/dev/null | tail -1)
# The physical block device behind it (rotational = 1 means a spinning disk; network block
# volumes such as Oracle's usually report 1 too, so the shape line matters more).
base=$(lsblk -no PKNAME "$disk_dev" 2>/dev/null | head -1)
lsblk_line=$(lsblk -dno NAME,SIZE,ROTA,MODEL "/dev/${base:-$(basename "$disk_dev")}" 2>/dev/null || echo "unknown")

# fsync latency on a Docker volume (the same disk and file system the nodes use): one writer,
# then five at once (five nodes on one disk).
probe() {
  docker volume create raftkv-fsync-probe >/dev/null
  docker run --rm -v raftkv-fsync-probe:/data --entrypoint java raft-kv:local -cp "/opt/raft-kv/lib/*" \
    io.github.hard3007.raftkv.server.FsyncProbe /data 200 "$1" 2>/dev/null || echo "probe failed"
  docker volume rm raftkv-fsync-probe >/dev/null
}
fsync_one=$(probe 1)
fsync_five=$(probe 5)

cat <<MD
- **VM shape:** ${shape}
- **CPU:** ${cpu}, ${cores} cores ($(uname -m)); **memory:** ${mem}
- **OS:** $(. /etc/os-release && echo "$PRETTY_NAME"), kernel $(uname -r); **Docker** $(docker version --format '{{.Server.Version}}' 2>/dev/null)
- **Disk:** Docker data in ${docker_root} on ${disk_dev} (${disk_fs}); block device: ${lsblk_line} (NAME SIZE ROTA MODEL)
- **fsync latency now (FsyncProbe, idle disk):** ${fsync_one}; ${fsync_five}
- **fsync: ON.** Every node fsyncs its Raft log (term, vote, entries) before answering any RPC and before the
  leader counts its own copy (Figure 2). With group commit the leader writes all entries proposed in one burst
  with one fsync. The B-Tree's own WAL is lazy and is fsynced every 1,000 applied entries (DESIGN.md §1,
  decision 8). Snapshots every 10,000 entries.
- **Nodes:** 448 MB container limit, JVM heap 256 MB (Serial GC), election timeout 150–300 ms, heartbeat 50 ms,
  PreVote and CheckQuorum on.
- **Load generator:** on the same machine as the 5 nodes, so it competes with them for the ${cores} cores.
MD
