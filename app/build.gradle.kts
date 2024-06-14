plugins {
    kotlin("jvm")
    kotlin("kapt")
    id("grmiscellany.shadow-app")
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.jcabi:jcabi-manifests:1.+")
    kapt("info.picocli:picocli-codegen:4.+")
    implementation("info.picocli:picocli:4.+")
    implementation("io.github.oshai:kotlin-logging-jvm:5.+")
    implementation(project(":lib"))
    implementation("org.apache.logging.log4j:log4j-core:2.+")
    implementation("org.apache.logging.log4j:log4j-slf4j2-impl:2.+")
    implementation(platform("org.jetbrains.kotlin:kotlin-bom"))
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    implementation("org.slf4j:slf4j-api:2+")
}

kotlin {
    jvmToolchain(11)
}
