package onl.concepts.tc

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

    @Command(
        defaultValueProvider = UTCNow::class,
        description = ["Generate an 8 character time code."],
        usageHelpAutoWidth = true,
    )
    fun tc8(
        @Option(
            converter = [UTCString::class],
            description = ["A UTC datetime to render as a time code.",
                           "Defaults to present date and time."],
            names = ["-d", "--datetime"],

        )
        datetime: Instant,
    ): Int {
        val tc = TimeCode8.of(datetime)
        logger.info { "Translated $datetime to $tc" }
        println("$tc")
        return 0
    }

    override fun call(): Int {
        spec.commandLine().usage(System.err)
        return 0
    }

    private class UTCNow: IDefaultValueProvider {
        override fun defaultValue(argSpec: Model.ArgSpec?): String {
            return Instant.now().toString()
        }
    }

    private class UTCString: ITypeConverter<Instant> {
        override fun convert(value: String?): Instant = Instant.parse(value)
    }
}
