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
            val app = cmd.getCommand<App>()

            println("app.level = ${app.level} app.debug = ${app.debug}")

            LogLevelControl.set(app.level)
            if (app.debug) {
                LogLevelControl.set(Release.application, Level.DEBUG)
                logger.debug {
                    "Enabled debug logging for ${Release.application}.*"
                }
            }

            logger.info { "Starting ${Release.name} (${Release.release})" }

            cmd.commandName = Release.name
            exitProcess(cmd.execute(*args))
        }
    }
}
