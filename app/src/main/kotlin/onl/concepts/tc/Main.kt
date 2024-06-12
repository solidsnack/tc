package onl.concepts.tc

import java.lang.System.Logger.Level
import kotlin.system.exitProcess

import io.github.oshai.kotlinlogging.KotlinLogging
import picocli.CommandLine
import picocli.CommandLine.RunLast


object Main {
    init {
        LogLevelControl.slf4jSuppression()
    }

    private val logger = KotlinLogging.logger {}

    @JvmStatic
    fun main(args: Array<String>) {
        val cmd = CommandLine(App())
            .setPosixClusteredShortOptionsAllowed(false)

        cmd.setExecutionStrategy {
            val app = cmd.getCommand<App>()

            LogLevelControl.set(app.level)
            if (app.debug) {
                LogLevelControl.set(Release.application, Level.DEBUG)
            }

            logger.info {
                "Starting ${Release.name} (${Release.release})"
            }

            logger.debug {
                "debug = ${logger.isDebugEnabled()} " +
                    "trace = ${logger.isTraceEnabled()}"
            }

            RunLast().execute(it)
        }

        cmd.commandName = Release.name
        exitProcess(cmd.execute(*args))
    }
}
