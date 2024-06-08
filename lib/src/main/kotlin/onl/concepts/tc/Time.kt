package onl.concepts.tc

import java.time.Duration
import java.time.ZoneId
import kotlin.math.ceil

object Time {
    val utc = ZoneId.of("UTC")

    /// The longest year on record is 1972, at 8784 hours and 2 seconds, due
    /// to leap seconds.
    val upperBoundOnYearDuration: Duration = Duration.ofHours(8785)
    /// The shortest possible year is 365 days.
    val lowerBoundOnYearDuration: Duration = Duration.ofHours(8760)
    /// There are under 32 million seconds in a year, so using 32 bit is
    /// perfectly safe when considering seconds within a year.
    val upperBoundOnSecondsInYear: Int =
        ceil(upperBoundOnYearDuration.toMillis() / 1000.0).toInt()
}
