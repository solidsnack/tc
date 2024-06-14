import java.time.*

plugins {
    application
    id("com.github.johnrengelman.shadow") version "7.+"
    kotlin("jvm")
    kotlin("kapt")
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

val distinguishedName = "${project(":").group}.${project(":").name}"

kotlin {
    jvmToolchain(11)
}

application {
    mainClass.set("$distinguishedName.Main")
}

tasks.shadowJar {
    minimize {
        exclude(dependency("org.jetbrains.kotlin:.*"))
        exclude(dependency("org.apache.logging.log4j:.*"))
    }

    val v = archiveVersion.get()

    manifest {
        attributes["Multi-Release"] = "true"
        attributes["Release"] = v
        attributes["Application"] = "$distinguishedName"
    }

    archiveFileName.set("$distinguishedName@$v.${project.name}.jar")

    doLast {
        val jar = tasks.shadowJar.get().archiveFile.get().asFile.toPath()
        val root = project(":").projectDir.toPath().toAbsolutePath()
        val rel = root.relativize(jar)

        val extTarget = "${project.name}.jar"
        val extDir = project(":").layout.buildDirectory.get().asFile.toPath()

        copy {
            from(jar)
            into(extDir)
            rename { extTarget }
        }

        println("Shadow JAR for ${project.name} is: $rel")
        println("Copied to: ${root.relativize(extDir)}/$extTarget")
    }
}
