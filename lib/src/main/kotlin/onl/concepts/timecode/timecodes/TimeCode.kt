package onl.concepts.timecode.timecodes

import java.time.Instant

interface TimeCode {
    /**
     * The textual timecode.
     */
    val code: String

    /**
     * The first time representable with the code.
     */
    val start: Instant

    /**
     * The time midway between the start and end -- the time which, on average,
     * times mapped to the code will be closest to.
     */
    val midpoint: Instant

    /**
     * The least upper bound of times representable within the code -- the
     * first time, after the start time, which is not covered by the code.
     */
    val end: Instant

    /**
     * Provides the start and end times of the code in ISO 8601 interval
     * format.
     */
    val summary: String

    /**
     * Detailed information about the code, presented as key value pairs.
     */
    fun describe(): Map<String, String>
}
