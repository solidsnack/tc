package onl.concepts.timecode

import java.time.Instant
import java.util.concurrent.Callable

import io.github.oshai.kotlinlogging.KotlinLogging
import picocli.CommandLine.*

import onl.concepts.timecode.timecodes.TC8

@Command(
    description = ["Subcommands for TC8 timecodes. With no subcommand, " +
                   "generates a new TC8 timecode, with the same options " +
                   "and behavior as the `encode` subcommand."],
    mixinStandardHelpOptions = true,
    name = "tc8",
    usageHelpAutoWidth = true,
)
class TC8App : Callable<Int> {
    @Spec
    lateinit var spec: Model.CommandSpec

    @ArgGroup
    var timespec: Timespec? = null

    companion object {
        private val logger = KotlinLogging.logger {}
    }

    @Command(
        description = ["Generate a timecode."],
        mixinStandardHelpOptions = true,
        usageHelpAutoWidth = true,
    )
    fun encode(
        @ArgGroup
        timespec: Timespec?,
    ): Int {
        val t = when (timespec?.timespec) {
            null -> {
                val t = Instant.now()
                logger.info { "No timestamp provided; encoding `now`: $t" }
                t
            }
            "now" -> {
                val t = Instant.now()
                logger.info { "User requested encoding of present time: $t" }
                t
            }
            else -> {
                // TODO: Try to parse with just minutes.
                val s = timespec.timespec!!
                Instant.parse(s)
            }
        }

        println(TC8.of(t).code)

        return 0
    }

    @Command(
        description = ["Decode a timecode to get the notional time or time " +
                       "interval.",
                       "Returns a UTC timestamp in ISO 8601 format, to " +
                       "seconds precision, of the midpoint of the interval " +
                       "covered by the timecode.",],
        mixinStandardHelpOptions = true,
        usageHelpAutoWidth = true,
    )
    fun decode(
        @Parameters(
            description = [],
        )
        timecode: String,
        @Option(
            defaultValue = "false",
            description = ["Print the midpoint of the interval covered by " +
                           "the code.",],
            names = ["-m", "--midpoint"],
        )
        midpoint: Boolean,
    ): Int {
        val parsed = TC8.of(timecode).getOrThrow()

        if (midpoint) {
            println("${parsed.midpoint}")
        } else {
            println("${parsed.summary}")
        }

        return 0
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
        val parsed = TC8.of(timecode).getOrThrow()

        for ((k, v) in parsed.describe()) println("$k: $v")

        return 0
    }

    override fun call(): Int {
        return encode(timespec)
    }
}
