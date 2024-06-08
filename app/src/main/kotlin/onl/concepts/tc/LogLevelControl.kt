package onl.concepts.tc

import java.lang.System.Logger.Level

import org.apache.logging.log4j.core.config.Configurator

object LogLevelControl {
    /**
     *  Disable misformatted info messages on startup from the SLF4J
     *  "reporter". The reporter is not a logger and ignores logging
     *  configuration.
     */
    fun slf4jSuppression() {
        System.setProperty("slf4j.internal.verbosity", "ERROR")
    }

    /**
     *  Set the root log level.
     */
    fun set(level: Level) {
        val log4j: org.apache.logging.log4j.Level = when (level) {
            Level.ALL -> org.apache.logging.log4j.Level.ALL
            Level.TRACE -> org.apache.logging.log4j.Level.TRACE
            Level.DEBUG -> org.apache.logging.log4j.Level.DEBUG
            Level.INFO -> org.apache.logging.log4j.Level.INFO
            Level.WARNING -> org.apache.logging.log4j.Level.WARN
            Level.ERROR -> org.apache.logging.log4j.Level.ERROR
            Level.OFF -> org.apache.logging.log4j.Level.OFF
        }

        Configurator.setRootLevel(log4j)
    }
}
