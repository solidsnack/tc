package onl.concepts.tc

import java.time.Instant
import java.time.Year
import java.time.temporal.ChronoField
import java.time.temporal.ChronoUnit
import kotlin.math.ceil

import io.github.oshai.kotlinlogging.KotlinLogging

data class PositionWithinYearAndHour(
    private val year: Year,
    private val millis: Long,
    private val hour: Int,
    private val millisInHour: Int,
) {
    companion object {
        private val logger = KotlinLogging.logger {}

        fun of(t: Instant): PositionWithinYearAndHour {
            val utc = t.atZone(Time.utc)
            val beginningOfYear = utc.truncatedTo(ChronoUnit.DAYS)
                .withDayOfYear(1)
                .toInstant()
            logger.info { "Year for $t is $beginningOfYear" }
            val year = Year.of(utc.get(ChronoField.YEAR))

            val position = t.toEpochMilli() - beginningOfYear.toEpochMilli()
            // This where we would start to look at Julian days and try to take
            // into account leap seconds. However, all classes in `java.time`
            // assume and require a day of 86400 seconds; leap seconds are
            // smoothed over the last thousand or so seconds in a day.
            val hour = (position / 3600000).toInt()
            val withinHour = (position % 3600000).toInt()

            return PositionWithinYearAndHour(year, position, hour, withinHour)
        }
    }

    fun hour(): Int = hour

    fun seconds(): Int = ceil(millisInHour / 1000.0).toInt()

    fun year(): String = "%04d".format(year.value)
}
