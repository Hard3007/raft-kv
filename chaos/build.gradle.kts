plugins {
    id("raftkv.java-conventions")
}

dependencies {
    implementation(project(":kv"))
    implementation(testFixtures(project(":kv")))
    implementation(project(":client"))
    implementation(project(":linearizability"))
    runtimeOnly(libs.logback.classic)
}

// ./gradlew chaos -Pruns=500 [-PstartSeed=1] [-Plabel=baseline] [-Pverbose] [-PexpectViolations]
tasks.register<JavaExec>("chaos") {
    group = "verification"
    description = "Runs seeded chaos runs on the simulated cluster and checks each history for linearizability."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = "io.github.hard3007.raftkv.chaos.ChaosMain"
    workingDir = rootDir
    maxHeapSize = "1g"
    val chaosArgs = mutableListOf(
        "--runs", providers.gradleProperty("runs").getOrElse("50"),
        "--start-seed", providers.gradleProperty("startSeed").getOrElse("1"),
        "--label", providers.gradleProperty("label").getOrElse("baseline"),
        "--check-quorum", providers.gradleProperty("checkQuorum").getOrElse("true"),
    )
    if (providers.gradleProperty("verbose").isPresent) {
        chaosArgs.add("--verbose")
    }
    args(chaosArgs)
    // Mutation runs are expected to find violations; they still write their report.
    isIgnoreExitValue = providers.gradleProperty("expectViolations").isPresent
}

// Chaos against the Docker cluster (start it first; see scripts/docker-chaos.sh).
tasks.register<JavaExec>("dockerChaos") {
    group = "verification"
    description = "Runs a workload against the 5-container cluster while killing, pausing and disconnecting containers."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = "io.github.hard3007.raftkv.chaos.DockerChaosMain"
    workingDir = rootDir
    maxHeapSize = "512m"
    val dockerArgs = mutableListOf(
        "--duration", providers.gradleProperty("duration").getOrElse("60"),
        "--clients", providers.gradleProperty("clients").getOrElse("4"),
    )
    providers.gradleProperty("report").orNull?.let { dockerArgs.addAll(listOf("--report", it)) }
    args(dockerArgs)
}
