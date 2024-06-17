package onl.concepts.tc

import java.lang.System.Logger.Level
import java.util.concurrent.Callable
import java.time.Instant

import io.github.oshai.kotlinlogging.KotlinLogging
import picocli.CommandLine.*
import picocli.CommandLine.Model.CommandSpec

import onl.concepts.tc.timecodes.TC8

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
        arity = "0",
        description = ["With -v, print basic information.",
                       "Pass up to three times for more information.",],
        names = ["-v"],
        scope = ScopeType.INHERIT,
    )
    var verbose: BooleanArray = BooleanArray(0)

    @Option(
        arity = "0",
        hidden = true,
        names = ["-vv"],
        scope = ScopeType.INHERIT,
    )
    var vverbose: Boolean = false

    @Option(
        arity = "0",
        hidden = true,
        names = ["-vvv"],
        scope = ScopeType.INHERIT,
    )
    var vvverbose: Boolean = false

    @Command(
        description = ["Generate an 8 character time code."],
        mixinStandardHelpOptions = true,
        usageHelpAutoWidth = true,
    )
    fun tc8(
        @Option(
            converter = [UTCString::class],
            description = ["A UTC datetime to render as a time code.",
                           "Defaults to present date and time.",],
            names = ["-u", "--utc-timestamp"],
        )
        timestamp: Instant?,
    ): Int {
        val t = timestamp ?: Instant.now()
        val tc = TC8.encode(TC8.Descriptor.of(t))
        logger.info { "Translated $t to $tc" }
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
