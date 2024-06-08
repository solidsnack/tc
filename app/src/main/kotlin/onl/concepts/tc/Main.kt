package onl.concepts.tc

import java.lang.System.Logger.Level
import kotlin.system.exitProcess

import io.github.oshai.kotlinlogging.KotlinLogging
import picocli.CommandLine


class Main {
    companion object {
        init {
            LogLevelControl.slf4jSuppression()
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
