plugins {
    application
    id("com.github.johnrengelman.shadow")
}

val distinguishedName = "${project(":").group}.${project(":").name}"

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
