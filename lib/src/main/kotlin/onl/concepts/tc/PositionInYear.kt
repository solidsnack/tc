package onl.concepts.tc

import java.time.Instant
import java.time.Year
import java.time.temporal.ChronoField
import java.time.temporal.ChronoUnit
import kotlin.math.ceil

import io.github.oshai.kotlinlogging.KotlinLogging

data class PositionInYear(
    private val year: Year,
    private val millis: Long,
) {
    companion object {
        private val logger = KotlinLogging.logger {}

        fun of(t: Instant): PositionInYear {
            val utc = t.atZone(Time.utc)
            val beginningOfYear = utc.truncatedTo(ChronoUnit.DAYS)
                .withDayOfYear(1)
                .toInstant()
            logger.info { "Year for $t is $beginningOfYear" }
            val year = Year.of(utc.get(ChronoField.YEAR))

            val position = t.toEpochMilli() - beginningOfYear.toEpochMilli()

            return PositionInYear(year, position)
        }

    }

    fun seconds(): Int = ceil(millis / 1000.0).toInt()

    fun milliseconds(): Long = millis

    fun year(): String = "%04d".format(year.value)
}
