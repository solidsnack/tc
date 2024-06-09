package onl.concepts.tc.timecodes

import java.time.Instant

import io.github.oshai.kotlinlogging.KotlinLogging

import onl.concepts.tc.PositionWithinYearAndHour
import onl.concepts.tc.Time
import onl.concepts.tc.bases.Base20
import onl.concepts.tc.bases.Base21
import java.time.Year
import java.time.ZonedDateTime

/**
 * Provides an eight character code every 180 seconds. The final four
 * characters are all alphabetical, and represent a value in the closed
 * interval [0, 185220] encoded in a mixed base:
 * base-20 base-21 base-21 base-21.
 *
 * Not all values are used, because the encoding is used to identify windows of
 * 3 minutes that are aligned with the beginning and end of hours. The first
 * three digits are used to represent the hour in the year. The last
 * digit, a base-21 digit, represents the 3 minute window.
 *
 * Why is a base-21 digit used to encode the 3 minute window in the hour?
 * There are only 20 3 minute windows in an hour. This is true unless (a)
 * the day has a leap second in it and (b) the time scale in use actually
 * allows this to be detected. That would require an additional window at
 * the end of an hour, if all windows are to be 180 seconds. Some system
 * clocks simply smooth the leap second over the last thousand seconds of
 * the day, in which case, no leap second is observed.
 */
object Year8 {
    fun of(t: Instant): String {
        val position = PositionWithinYearAndHour.of(t)

        val hour = position.hour()
        val window = position.seconds() / 180

        logger.info {
            "Notional index of $t: hour=$hour " +
                "minute=${3 * window}...${3 * (window + 1)}"
        }

        assert(hour < (20 * 21 * 21)) {
            "Hour outside of representable range: $hour"
        }

        val (rem1, digit3) = Base21.nibble(hour)
        val (rem2, digit2) = Base21.nibble(rem1)
        val (rem3, digit1) = Base20.nibble(rem2)

        assert(rem3 == 0) {
            "There should be nothing left at the end of this operation."
        }

        val (rem4, digit4) = Base21.nibble(window)

        assert(rem4 == 0) {
            "There should be nothing left at the end of this operation."
        }

        return "${position.year()}$digit1$digit2$digit3$digit4"
    }

    /**
     * Determine the 180 second window represented by the time code. Note that
     * the result is represented as text.
     */
    fun window(code: String): String {
        val yearText = code.subSequence(0, 4)
        val hourText = code.subSequence(4, 7)
        val windowText = code.subSequence(7, 8)

        var hourIndex = 0
        hourIndex = Base20.append(hourText[0], hourIndex)
        hourIndex = Base21.append(hourText[1], hourIndex)
        hourIndex = Base21.append(hourText[2], hourIndex)

        // Why we do days and then hours:
        // * In principle, you could just turn hours into seconds and step into
        //   the year, but this would shift everything forward into the
        //   preceding hour if there are leap seconds.
        // * While there may be more than 3600 seconds in an hour (3601 when a
        //   leap second is added, there are never more than 24 hours in a day.
        val year = Year.parse(yearText)
        val day = hourIndex / 24
        val hour = hourIndex % 24
        val window = Base21.append(windowText.first(), 0)

        val t = ZonedDateTime.of(year.value, 1, 1, 0, 0, 0, 0, Time.utc)
            .plusDays(day.toLong())
            .plusHours(hour.toLong())
        // Up to the first `:` of: yyyy-mm-ddTHH:MM:SS.NNNNNNZ
        val prefix = t.toInstant().toString().substring(0, 13)

        return when (window + 1) {
            // The 21st window is the leap second window.
            21 -> {
                val following = Time.followingHourMinimalRep(t)
                "$prefix:59:60Z/${following}Z"
            }
            // The 20th window: the last window in most cases.
            20 -> {
                val following = Time.followingHourMinimalRep(t)
                "$prefix:57Z/${following}Z"
            }
            else -> {
                val start = window * 3
                String.format(
                    "%s:%02dZ/%02d:%02dZ",
                    prefix,
                    start,
                    t.hour,
                    start + 3,
                )
            }
        }
    }

    private val logger = KotlinLogging.logger {}
}
