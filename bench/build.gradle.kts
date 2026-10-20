plugins {
    id("raftkv.java-conventions")
    application
}

dependencies {
    implementation(project(":client"))
    implementation(libs.hdrhistogram)
    runtimeOnly(libs.logback.classic)
}

application {
    mainClass = "io.github.hard3007.raftkv.bench.Main"
}
