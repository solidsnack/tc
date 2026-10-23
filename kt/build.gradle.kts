plugins {
    kotlin("jvm") version "2.4.21"
    `java-library`
}

group = "onl.concepts"
version = "0.1.0"
description = "TimeCodes: sortable, short timestamps"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.14.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("net.jqwik:jqwik:1.10.1")
    testImplementation("net.jqwik:jqwik-kotlin:1.10.1")
    testImplementation(kotlin("reflect"))
    testImplementation("org.yaml:snakeyaml:2.7")
}

java {
    withSourcesJar()
}

kotlin {
    jvmToolchain(17)
    compilerOptions {
        javaParameters = true
        // Recommended by jqwik-kotlin, for nullability in generators.
        freeCompilerArgs.addAll(
            "-Xjsr305=strict",
            "-Xemit-jvm-type-annotations",
        )
    }
}

tasks.jar {
    manifest {
        attributes["Automatic-Module-Name"] = "onl.concepts.timecode"
    }
}

tasks.test {
    useJUnitPlatform {
        includeEngines("junit-jupiter", "jqwik")
    }

    systemProperty(
        "timecode.specification",
        file("../specification/tests.yaml").absolutePath,
    )
    systemProperty(
        "jqwik.database",
        layout.buildDirectory.file("jqwik-database").get().asFile.path,
    )

    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging
            .TestExceptionFormat.FULL
    }
}
