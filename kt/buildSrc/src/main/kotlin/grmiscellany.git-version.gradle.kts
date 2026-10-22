/*   This is free and unencumbered software released into the public domain. */
import java.time.*

plugins {
    id("org.ajoberstar.grgit")
}

repositories {
    mavenCentral()
}

fun versionManager(): String {
    val branch = grgit.branch?.current()
    val commit = grgit.head()
    val tags = grgit.tag?.list()

    fun formatDate(t: ZonedDateTime, suffix: String = "SNAPSHOT"): String {
        val utc = t.toInstant()
        return "${utc.toString().substring(0, 10).replace("-", "")}-$suffix"
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
        println("version = ${versionManager()}")
    }
}
