package onl.concepts.timecode

import java.time.Instant
import java.time.Year
import java.time.ZoneId
import java.time.ZonedDateTime

object Time {
    val utc: ZoneId = ZoneId.of("UTC")

    val daySeconds: Long = 86400

    /**
     * Obtain a more minimal representation in ISO 8601
     * interval syntax to of the interval between the instants, using at
     * least minutes precision.
     *
     * For example:
     * - For `2022-02-02T15:15Z` and `2022-02-02T16:00Z`, the minimal
     *   representation is `2022-02-02T15:15Z/16:00Z`.
     * - For `2022-04-30T23:23Z` and `2022-05-01T00:00Z`, the minimal
     *   representation is `2022-04-30T23:23Z/05-01T00:00Z`.
     * - For `2022-04-16T23:01:16Z` and `2022-04-16T23:30Z`, the minimal
     *   representation is `2022-04-16T23:01:16Z/23:30:00Z`.
     */
    fun representInterval(start: Instant, end: Instant): String {
        val a = minOf(start, end)
        val z = maxOf(start, end)
        val aStr = "$a".trimEnd('Z')
        val zStr = "$z".trimEnd('Z')
        val aZoned = a.atZone(utc)
        val zZoned = z.atZone(utc)
        var zStart = 0

        val (aEnd, zEnd) = run {
            val hasSeconds =
                listOf(aZoned.second, aZoned.nano, zZoned.second, zZoned.nano)
                    .any { it > 0 }

            if (hasSeconds) {
                Pair(aStr.length, zStr.length)
            } else {
                Pair(16, 16)
            }
        }

        if (zZoned.year == aZoned.year) zStart += 5

        if (
            zZoned.monthValue == aZoned.monthValue &&
            zZoned.dayOfMonth == aZoned.dayOfMonth
        ) zStart += 6

        return "${aStr.substring(0, aEnd)}Z/${zStr.substring(zStart, zEnd)}Z"
    }

    inline
    fun firstDayOfYear(year: Year): ZonedDateTime {
        return ZonedDateTime.of(year.value, 1, 1, 0, 0, 0, 0, utc)
    }

    inline
    fun firstDayOfYear(t: Instant): ZonedDateTime {
        val year = t.atZone(utc).year
        return firstDayOfYear(Year.of(year))
    }

    inline
    fun lastDayOfYear(year: Year): ZonedDateTime {
        return ZonedDateTime.of(year.value, 12, 31, 0, 0, 0, 0, utc)
    }

    inline
    fun lastDayOfYear(t: Instant): ZonedDateTime {
        val year = t.atZone(utc).year
        return lastDayOfYear(Year.of(year))
    }
}
