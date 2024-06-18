package onl.concepts.timecode.timecodes

import java.time.Instant
import java.time.ZonedDateTime

import io.github.oshai.kotlinlogging.KotlinLogging

import onl.concepts.timecode.Time
import onl.concepts.timecode.bases.Alpha20
import onl.concepts.timecode.bases.Alpha12

/**
 *  TC8 Implementation.
 */
data class TC8(
        /** Year, starting from 0. So 2023 is 2023 and so on. */
        val year: Short,
        /** Month starting from 0. The first month is 0, the second
         *  is 1, and so on. */
        val month: Byte,
        /** The day and 2-hour window and tenth part of the hour within the
         *  month in a combined format. */
        val indexOf10thOfHourIn32DayMonth: Short,
) : TimeCode {
    init {
        assert(year in 0..9999) { "Year must be: [0,9999]" }
        assert(month in 0..11) { "Month must be: [0,11]" }
        assert(indexOf10thOfHourIn32DayMonth in 0..7999) {
            "Index must be: [0,7999]"
        }
    }

    companion object {
        private const val SECONDS: Long = 360

        @JvmStatic
        fun of(t: Instant): TC8 {
            val utc = t.atZone(Time.utc)
            val year = utc.year
            val month = utc.monthValue - 1
            val dom = utc.dayOfMonth - 1
            val hour = utc.hour
            val minute = utc.minute

            var indexOf10thOfHourIn32DayMonth = 0
            // Beginning of day.
            indexOf10thOfHourIn32DayMonth += 250 * dom
            // Beginning of hour window.
            indexOf10thOfHourIn32DayMonth += 10 * hour
            // Beginning of six-minute window.
            indexOf10thOfHourIn32DayMonth += minute / 6

            return TC8(
                year.toShort(),
                month.toByte(),
                indexOf10thOfHourIn32DayMonth.toShort(),
            )
        }

        @JvmStatic
        fun of(s: String): Result<TC8> = try {
            assert(s.length == 8) { "A TC8 descriptor is 8 characters." }

            val yearText = s.subSequence(0, 4)
            val monthText = s.subSequence(4, 5)
            val rest = s.subSequence(5, 8)

            val year = Integer.parseInt(yearText.toString())
            val month = monthText.fold(0, Alpha12::accumulate)
            val indexOf10thOfHourIn32DayMonth = rest.fold(0, Alpha20::accumulate)

            Result.success(
                TC8(
                    year.toShort(),
                    month.toByte(),
                    indexOf10thOfHourIn32DayMonth.toShort(),
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }

        private val logger = KotlinLogging.logger {}
    }

    override val code: String by lazy {
        val digits1234 = String.format("%04d", year)

        val (remSM, digit5) = Alpha12.nibble(month.toInt())

        assert(remSM == 0) {
            "There should be nothing left after translating the " +
                "month to base 12."
        }

        val i = indexOf10thOfHourIn32DayMonth.toInt()
        val (remDH1, digit8) = Alpha20.nibble(i)
        val (remDH2, digit7) = Alpha20.nibble(remDH1)
        val (remDH3, digit6) = Alpha20.nibble(remDH2)

        assert(remDH3 == 0) {
            "There should be nothing left after translating the " +
                "index of the twentieth part to base 20."
        }

        "$digits1234$digit5$digit6$digit7$digit8"
   }

    override val start: Instant
        get() = startZ.toInstant()

    override val end: Instant
        get() = endZ.toInstant()
    override val summary: String
        get() = Time.representInterval(start, end)

    override val midpoint: Instant
        get() = startZ.plusSeconds(SECONDS / 2).toInstant()

    override fun describe(): Map<String, String> {
        val (day, hour, minute) = dayAndTimeAsEncoded()

        return sortedMapOf(
            "code" to code,
            "summary" to summary,
            "encoded-year" to String.format("%04d", year),
            "encoded-month" to String.format("%02d", month + 1),
            "encoded-day" to String.format("%02d", day + 1),
            "encoded-hour" to String.format("%02d", hour),
            "encoded-minute" to String.format("%02d", minute),
        )
    }

    private fun dayAndTimeAsEncoded(): Triple<Int, Int, Int> {
        val indexIn25HourDay = indexOf10thOfHourIn32DayMonth % 250

        val day = indexOf10thOfHourIn32DayMonth / 250
        val hour = indexIn25HourDay / 10
        val minute = (indexIn25HourDay % 10) * 6

        return Triple(day, hour, minute)
    }

    private val startZ: ZonedDateTime by lazy {
        var (day, hour, minute) = dayAndTimeAsEncoded()

        // NB: Maps leap second / leap hour to last hour.
        if (hour > 23) {
            hour = 23
            minute = 54
        }

        ZonedDateTime.of(
            year.toInt(),
            month + 1,
            day + 1,
            hour,
            minute,
            0,
            0,
            Time.utc,
        )
    }

    // Adding a fixed number of seconds is correct because leap seconds
    // are always smoothed or collapsed in the timescale underlying
    // `java.time.*` classes.
    private val endZ: ZonedDateTime by lazy { startZ.plusSeconds (SECONDS) }
}
