rootProject.name = "raft-kv"

include(
    "common",
    "proto",
    "storage",
    "raft",
    "kv",
    "client",
    "server",
    "linearizability",
    "chaos",
    "bench",
)
