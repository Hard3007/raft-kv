rootProject.name = "raft-kv"

include(
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
