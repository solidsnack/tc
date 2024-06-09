import java.time.*
import java.time.format.*

plugins {
    application
    id("com.github.johnrengelman.shadow") version "7.+"
    id("org.ajoberstar.grgit") version "4.+"
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
    implementation(platform("org.jetbrains.kotlin:kotlin-bom"))
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    implementation("org.slf4j:slf4j-api:2+")
    implementation("org.apache.logging.log4j:log4j-core:2.+")
    implementation("org.apache.logging.log4j:log4j-slf4j2-impl:2.+")
}

val distinguishedName = "${project(":").group}.${project(":").name}"

kotlin {
    jvmToolchain(11)
}

application {
    mainClass.set("$distinguishedName.Main")
}

fun versionManager(): String {
    val branch = grgit?.branch?.current()
    val commit = grgit?.head()
    val tags = grgit?.tag?.list()

    fun formatDate(t: ZonedDateTime, suffix: String = "SNAPSHOT"): String {
        val utc = ZoneId.of("UTC")
        val fmt = DateTimeFormatter.BASIC_ISO_DATE
        val local = LocalDateTime.from(t.withZoneSameInstant(utc))
        return local.format(fmt) + "-" + suffix
    }

    if (commit == null) return formatDate(ZonedDateTime.now())

    if (tags != null) for (tag in tags) {
        if (tag.commit.id == commit.id) return tag.getName()
    }

    val t = commit.dateTime
    val sha = commit.abbreviatedId

    if (branch == null) return formatDate(t, sha)
    if (branch.getName() == "main") return formatDate(t, sha)

    return formatDate(t, branch.getName() + "-" + sha)
}

version = versionManager()

tasks.register("rev") {
    description = "Calculate a version based on current VCS information."

    doLast {
        println(versionManager())
    }
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

        val exportTarget = "${project.name}.jar"
        val exportDir = project(":").buildDir.toPath()

        copy {
            from(jar)
            into(exportDir)
            rename { exportTarget }
        }

        println("Shadow JAR for ${project.name} is: $rel")
        println("Copied to: ${root.relativize(exportDir)}/$exportTarget")
    }
}
