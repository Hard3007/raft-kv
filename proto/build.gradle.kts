plugins {
    id("raftkv.java-conventions")
    `java-library`
    alias(libs.plugins.protobuf)
}

dependencies {
    api(libs.protobuf.java)
    api(libs.grpc.protobuf)
    api(libs.grpc.stub)
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:${libs.versions.protobuf.get()}"
    }
    plugins {
        create("grpc") {
            artifact = "io.grpc:protoc-gen-grpc-java:${libs.versions.grpc.get()}"
        }
    }
    generateProtoTasks {
        all().configureEach {
            plugins {
                // Omit @javax.annotation.Generated so we don't need the javax.annotation jar.
                create("grpc") { option("@generated=omit") }
            }
        }
    }
}

// Generated code triggers lint warnings we don't control.
tasks.named<JavaCompile>("compileJava") {
    options.compilerArgs.removeAll { it.startsWith("-Xlint") }
}
