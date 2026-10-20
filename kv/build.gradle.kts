plugins {
    id("raftkv.java-conventions")
    `java-library`
    `java-test-fixtures`
}

dependencies {
    api(project(":raft"))
    api(project(":storage"))

    // The simulated KV cluster (test fixtures) drives real clients; Phase 4's chaos harness reuses it.
    testFixturesApi(project(":client"))
    testFixturesApi(project(":raft"))
    testImplementation(project(":client"))
}

// Number of seeds each simulated-cluster test runs: ./gradlew :kv:test -PkvSeeds=50
tasks.test {
    systemProperty("kv.seeds", providers.gradleProperty("kvSeeds").getOrElse("3"))
}
