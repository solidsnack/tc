package onl.concepts.tc

import java.util.concurrent.Callable

import io.github.oshai.kotlinlogging.KotlinLogging
import picocli.CommandLine.*

@Command(
    mixinStandardHelpOptions = true,
    name = "tc8",
    usageHelpAutoWidth = true,
)
class TC8 : Callable<Int> {
    @Spec
    lateinit var spec: Model.CommandSpec

    companion object {
        private val logger = KotlinLogging.logger {}
    }

    @Command(
        description = ["Generate a time code."],
        mixinStandardHelpOptions = true,
        usageHelpAutoWidth = true,
    )
    fun encode(
        @Parameters(
            description = [],
        )
        timespec: String,
    ): Int {
        return 0
    }

    @Command(
        description = ["Decode a time code to get a time.",
                       "Returns a UTC timestamp in ISO 8601 format, to " +
                       "seconds precision, of the first instant that falls " +
                       "in the 6 minute window covered by the time code.",],
        mixinStandardHelpOptions = true,
        usageHelpAutoWidth = true,
    )
    fun decode(
        @Parameters(
            description = [],
        )
        timecode: String,
        @ArgGroup(exclusive = true)
        leap: ToLeapOrNotToLeap?,
    ): Int {
        val presentWith25thHour = leap?.let {
            logger.debug { "-l? ${it.leap} -s? ${it.smoothed}" }
            it.leap
        } ?: false
        return 0
    }

    class ToLeapOrNotToLeap {
        @Option(
            description = [
                "Decode in a time scale where leap seconds are smoothed ",
                "(as in UTC-SLS) or collapsed (classic POSIX approach).",
                "This is the default.",
            ],
            names = ["-s"],
        ) var smoothed = false
        @Option(
            description = [
                "Decode in a time scale where leap seconds may be ",
                "inserted at the end of the day.",
                "While a technically correct behavior for UTC, this is not",
                "how time is implemented in many common computing systems.",
                "The output may be unexpected."
            ],
            names = ["-l"]
        )
        var leap = false
    }

    @Command(
        description = ["Provide detailed information about a timecode.",
                       "This command presents a timecode in a diagnostic, ",
                       "interval format.",],
        mixinStandardHelpOptions = true,
        usageHelpAutoWidth = true,
    )
    fun describe(
        @Parameters(
            description = [],
        )
        timecode: String,
    ): Int {
        return 0
    }

    override fun call(): Int {
        spec.commandLine().usage(System.err)
        return 0
    }
}
