plugins {
    id("raftkv.java-conventions")
    application
}

dependencies {
    implementation(project(":kv"))
    implementation(project(":client"))
    implementation(libs.grpc.netty.shaded)
    runtimeOnly(libs.logback.classic)
    testImplementation(libs.grpc.inprocess)
}

application {
    mainClass = "io.github.hard3007.raftkv.server.Main"
    applicationDefaultJvmArgs = listOf("-Xmx256m")
}
