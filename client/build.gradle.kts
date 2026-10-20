plugins {
    id("raftkv.java-conventions")
    `java-library`
}

dependencies {
    api(project(":proto"))
    implementation(libs.grpc.netty.shaded)
}
