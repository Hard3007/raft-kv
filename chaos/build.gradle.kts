plugins {
    id("raftkv.java-conventions")
}

dependencies {
    implementation(project(":kv"))
    implementation(project(":client"))
    implementation(project(":linearizability"))
    runtimeOnly(libs.logback.classic)
}
