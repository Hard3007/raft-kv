plugins {
    id("raftkv.java-conventions")
    `java-library`
}

dependencies {
    api(project(":raft"))
    api(project(":storage"))
}
