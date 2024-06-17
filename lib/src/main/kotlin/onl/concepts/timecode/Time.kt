package onl.concepts.timecode

import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime
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

    /**
     * Obtain the minimal representation that can be used with ISO 8601
     * interval syntax to represent following UTC hour in tandem with the given
     * value. For example:
     * - For `2022-02-02T15:15Z`, the minimal representation of the following
     *   hour is `16:00`. The interval can be represented as:
     *   `2022-02-02T15:15Z/16:00Z`.
     * - For `2022-02-28T23:23Z`, the minimal representation of the following
     *   hour is `03-01T00:00`. The interval can be represented as:
     *   `2022-02-28T23:23Z/03-01T00:00Z`.
     */
    fun followingHourMinimalRep(t: ZonedDateTime): String {
        val result = StringBuilder()
        val asUTC = t.withZoneSameInstant(utc)
        val following = asUTC.plusHours(1)

        if (following.year != asUTC.year) {
            result.append(String.format("%04d-", following.year))
        }

        if (following.monthValue != asUTC.monthValue) {
            result.append(String.format("%02d-", following.monthValue))
        }

        if (following.dayOfMonth != asUTC.dayOfMonth) {
            result.append(String.format("%02dT", following.dayOfMonth))
        }

        result.append(String.format("%02d:00", following.hour))

        return result.toString()
    }
}
