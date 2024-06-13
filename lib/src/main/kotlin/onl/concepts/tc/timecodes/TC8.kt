package onl.concepts.tc.timecodes

import java.time.Instant
import java.time.ZonedDateTime

import io.github.oshai.kotlinlogging.KotlinLogging

import onl.concepts.tc.Time
import onl.concepts.tc.bases.Alpha20
import onl.concepts.tc.bases.Alpha12


/**
 *  TC8 Implementation.
 */
object TC8: TimeCode<TC8.Descriptor> {
    data class Descriptor(
        /** Year, starting from 0. So 2023 is 2023 and so on. */
        val year: Short,
        /** Month starting from 0. The first month is 0, the second
         *  is 1, and so on. */
        val month: Byte,
        /** The day and 2-hour window and tenth part of the hour within the
         *  month in a combined format. */
        val indexOf10thOfHourIn32DayMonth: Short,
    ) {
        init {
            assert(year in 0..9999) { "Year must be: [0,9999]" }
            assert(month in 0..11) { "Month must be: [0,11]" }
            assert(indexOf10thOfHourIn32DayMonth in 0..7999) {
                "Index of twentieth part must be: [0,7999]"
            }
        }

        companion object {
            fun of(t: Instant): Descriptor {
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

                return Descriptor(
                    year.toShort(),
                    month.toByte(),
                    indexOf10thOfHourIn32DayMonth.toShort(),
                )
            }
        }

        private fun utc(): ZonedDateTime {
            val indexIn25HourDay = indexOf10thOfHourIn32DayMonth % 250

            val day = indexOf10thOfHourIn32DayMonth / 250
            var hour = indexIn25HourDay / 10
            var minute = (indexIn25HourDay % 10) * 6

            // NB: Maps leap second / leap hour to last hour.
            if (hour > 23) {
                hour = 23
                minute = 57
            }

            return ZonedDateTime.of(
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

        fun start(): Instant = utc().toInstant()

        fun end(): Instant {
            // Adding 180 seconds is perfectly correct with
            // `java.time.Instant`, because in the smoothed UTC that
            // `java.time` implements, every day is exactly 86400 seconds.
            return start().plusSeconds(360)
        }
    }

    override fun encode(descriptor: Descriptor): String {
        val digits1234 = String.format("%04d", descriptor.year)

        val (remSM, digit5) = Alpha12.nibble(descriptor.month.toInt())

        assert(remSM == 0) {
            "There should be nothing left after translating the " +
                "month to base 12."
        }

        val i = descriptor.indexOf10thOfHourIn32DayMonth.toInt()
        val (remDH1, digit8) = Alpha20.nibble(i)
        val (remDH2, digit7) = Alpha20.nibble(remDH1)
        val (remDH3, digit6) = Alpha20.nibble(remDH2)

        assert(remDH3 == 0) {
            "There should be nothing left after translating the " +
                "index of the twentieth part to base 20."
        }

        return "$digits1234$digit5$digit6$digit7$digit8"
    }

    override fun decode(s: String): Result<Descriptor> = try {
        assert(s.length == 8) { "A TC8 descriptor is 8 characters." }

        val yearText = s.subSequence(0, 4)
        val semiMonthText = s.subSequence(4, 5)
        val rest = s.subSequence(5, 8)

        val year = Integer.parseInt(yearText.toString())
        val semiMonth = semiMonthText.fold(0, Alpha12::accumulate)
        val indexOf10thOfHourIn32DayMonth = rest.fold(0, Alpha20::accumulate)

        Result.success(
            Descriptor(
                year.toShort(),
                semiMonth.toByte(),
                indexOf10thOfHourIn32DayMonth.toShort(),
            )
        )
    } catch (e: Exception) {
        Result.failure(e)
    }

    private val logger = KotlinLogging.logger {}
}
