plugins {
    application
    kotlin("jvm") version "2.+"
    kotlin("kapt") version "2.+"
    id("org.jetbrains.dokka") version "1.+"
}

repositories {
    mavenCentral()
}

tasks.withType<AbstractArchiveTask>().configureEach {
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

subprojects {
    apply(plugin = "org.jetbrains.dokka")
}
