/*                           This work by Jason Dusek is marked with CC0 1.0 */
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
