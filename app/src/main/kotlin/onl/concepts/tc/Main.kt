package onl.concepts.tc

import kotlin.system.exitProcess

import io.github.oshai.kotlinlogging.KotlinLogging
import org.slf4j.LoggerFactory
import picocli.CommandLine


class Main {
    companion object {
        init {
            // Disable spurious info messages on startup.
            System.setProperty("slf4j.internal.verbosity", "ERROR")
        }

        private val logger = KotlinLogging.logger {}

        @JvmStatic
        fun main(args: Array<String>) {

            val cmd = CommandLine(App())
            logger.info { "Starting ${Release.name} (${Release.release})" }
            cmd.commandName = Release.name
            exitProcess(cmd.execute(*args))
        }
    }
}
