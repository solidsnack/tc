plugins {
    application
    kotlin("jvm") version "1.+"
    kotlin("kapt") version "1.+"
}

repositories {
    mavenCentral()
}

tasks.withType<AbstractArchiveTask>().configureEach {
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}
