plugins {
    id("raftkv.java-conventions")
    `java-library`
}

dependencies {
    api(project(":common"))
    api(project(":proto"))
    api(project(":storage"))
}

// Number of seeds each simulated-cluster test runs: ./gradlew :raft:test -PraftSeeds=50
tasks.test {
    systemProperty("raft.seeds", providers.gradleProperty("raftSeeds").getOrElse("3"))
}
