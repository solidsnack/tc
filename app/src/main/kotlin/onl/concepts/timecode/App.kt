package onl.concepts.timecode

import java.util.concurrent.Callable

import io.github.oshai.kotlinlogging.KotlinLogging
import picocli.CommandLine.*
import picocli.CommandLine.Model.CommandSpec

@Command(
    mixinStandardHelpOptions = true,
    usageHelpAutoWidth = true,
    versionProvider = Release.VersionProvider::class,
    subcommands = [TC8::class]
)
class App : Callable<Int> {
    @Spec
    lateinit var spec: CommandSpec

    companion object {
        private val logger = KotlinLogging.logger {}
    }

    @Option(
        arity = "0",
        description = ["Describe what the program is doing.",
                       "Pass up to three times for more detail.",],
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

    override fun call(): Int {
        spec.commandLine().usage(System.err)
        return 0
    }
}
