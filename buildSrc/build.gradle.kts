/*   This is free and unencumbered software released into the public domain. */
plugins {
    `kotlin-dsl`
}

repositories {
    gradlePluginPortal()
    mavenCentral()
}

dependencies {
    implementation("com.github.johnrengelman:shadow:8.+")
    implementation("org.ajoberstar.grgit:grgit-gradle:5.+")
}
