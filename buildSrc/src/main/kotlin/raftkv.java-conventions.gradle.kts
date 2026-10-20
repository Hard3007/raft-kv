// Convention plugin applied by every module: Java 21, JUnit 5, SLF4J, shared test settings.
plugins {
    java
}

group = "io.github.hard3007.raftkv"
version = "0.1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

val libs = the<VersionCatalogsExtension>().named("libs")

dependencies {
    implementation(libs.findLibrary("slf4j-api").get())
    testImplementation(platform(libs.findLibrary("junit-bom").get()))
    testImplementation(libs.findLibrary("junit-jupiter").get())
    testRuntimeOnly(libs.findLibrary("junit-platform-launcher").get())
    testRuntimeOnly(libs.findLibrary("logback-classic").get())
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Xlint:-processing", "-Xlint:-serial"))
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    maxHeapSize = "512m"
    testLogging {
        events("failed", "skipped")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}
