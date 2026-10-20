plugins {
    id("raftkv.java-conventions")
    application
}

dependencies {
    implementation(project(":kv"))
    implementation(project(":client"))
    implementation(libs.grpc.netty.shaded)
    runtimeOnly(libs.logback.classic)
    testImplementation(project(":linearizability"))
    testImplementation(project(":chaos")) // the shared real-client workload
}

application {
    mainClass = "io.github.hard3007.raftkv.server.Main"
    applicationName = "raft-kv"
    applicationDefaultJvmArgs = listOf("-Xmx256m")
}

tasks.test {
    maxHeapSize = "1g" // five real nodes plus clients in one JVM
}
