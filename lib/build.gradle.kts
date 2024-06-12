import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent

plugins {
    kotlin("jvm")
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("io.github.oshai:kotlin-logging-jvm:5.+")
    implementation(platform("org.jetbrains.kotlin:kotlin-bom"))
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")

    testImplementation(kotlin("test"))
    testImplementation("net.jqwik:jqwik:1.+")
    testImplementation("net.jqwik:jqwik-kotlin:1.+")
    testImplementation("org.apache.logging.log4j:log4j-core:2.+")
    testImplementation("org.apache.logging.log4j:log4j-slf4j2-impl:2.+")
    testImplementation("org.slf4j:slf4j-api:2.+")
}

kotlin {
    jvmToolchain(11)
    compilerOptions {
        javaParameters.set(true)
        freeCompilerArgs.add("-Xjsr305=strict")
        freeCompilerArgs.add("-Xemit-jvm-type-annotations")
    }
}

tasks.test {
    useJUnitPlatform()

    doFirst {
        mkdir("$projectDir/tmp")
    }

    systemProperty("jqwik.database", "tmp/jqwik-database")

    testLogging {
        lifecycle {
            events = mutableSetOf(
                TestLogEvent.FAILED,
                TestLogEvent.PASSED,
                TestLogEvent.SKIPPED,
            )
            exceptionFormat = TestExceptionFormat.FULL

            showExceptions = true
            showCauses = true
            showStackTraces = false
            showStandardStreams = false
        }
        info.events = lifecycle.events
        info.exceptionFormat = lifecycle.exceptionFormat
    }
}
