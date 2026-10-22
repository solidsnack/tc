package onl.concepts.timecode

import picocli.CommandLine

class Timespec {
    @CommandLine.Parameters(
        arity = "0..1",
        description = [
            "The word `now` or an ISO 8601 format UTC timestamp with " +
            "seconds: yyyy-mm-ddTHH:MM:SSZ"
        ],
    )
    var timespec: String? = null
}
