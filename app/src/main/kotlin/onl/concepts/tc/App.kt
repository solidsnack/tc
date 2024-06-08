package onl.concepts.tc

import java.lang.System.Logger.Level
import java.util.concurrent.Callable
import java.time.Instant

import io.github.oshai.kotlinlogging.KotlinLogging
import picocli.CommandLine.*
import picocli.CommandLine.Model.CommandSpec

import onl.concepts.tc.timecodes.TimeCode8

@Command(
    mixinStandardHelpOptions = true,
    usageHelpAutoWidth = true,
    versionProvider = Release.VersionProvider::class,
)
class App : Callable<Int> {
    @Spec
    lateinit var spec: CommandSpec

    companion object {
        private val logger = KotlinLogging.logger {}
    }

    @Option(
        converter = [LogLevelString::class],
        defaultValue = "WARNING",
        description = ["Set log level to one of: ..."],
        names = ["-l", "--log-level"],
        scope = ScopeType.INHERIT,
    )
    var level: Level = Level.WARNING

    @Option(
        arity = "0",
        description = ["Enable debugging for this application."],
        names = ["-d", "--debug"],
        scope = ScopeType.INHERIT,
    )
    var debug: Boolean = false

    @Command(
        description = ["Generate an 8 character time code."],
        usageHelpAutoWidth = true,
    )
    fun tc8(
        @Option(
            converter = [UTCString::class],
            description = ["A UTC datetime to render as a time code.",
                           "Defaults to present date and time."],
            names = ["-u", "--utc-timestamp"],
        )
        timestamp: Instant?,
    ): Int {
        val tc = TimeCode8.of(timestamp ?: Instant.now())
        logger.info { "Translated $timestamp to $tc" }
        println("$tc")
        return 0
    }

    override fun call(): Int {
        spec.commandLine().usage(System.err)
        return 0
    }

    private class LogLevelString: ITypeConverter<Level> {
        override fun convert(value: String?): Level
            = Level.valueOf(value!!.uppercase())
    }

    private class UTCString: ITypeConverter<Instant> {
        override fun convert(value: String?): Instant = Instant.parse(value)
    }
}
