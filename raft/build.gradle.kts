plugins {
    id("raftkv.java-conventions")
    `java-library`
}

dependencies {
    api(project(":proto"))
    api(project(":storage"))
}
