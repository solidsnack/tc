package onl.concepts.tc.timecodes

import java.time.Instant
import java.time.ZonedDateTime

import io.github.oshai.kotlinlogging.KotlinLogging

import onl.concepts.tc.Time
import onl.concepts.tc.bases.Alpha20
import onl.concepts.tc.bases.Alpha24


/**
 *  TC8 Implementation.
 */
object TC8: TimeCode<TC8.Descriptor> {
    data class Descriptor(
        /** Year, starting from 0. So 2023 is 2023 and so on. */
        val year: Short,
        /** Semi-month starting from 0. The first month is 0 or 1, the second
         *  is 2 or 3, and so on. */
        val semiMonth: Byte,
        /** The day and hour in within the half month in a combined format. */
        val dayAndHour: Short,
        /** The twentieth part of the hour. */
        val twentiethIndex: Byte,
    ) {
        init {
            assert(year in 0..9999) { "Year must be: [0,9999]" }
            assert(semiMonth in 0..23) { "Semi-month must be: [0,23]" }
            assert(dayAndHour in 0..399) { "Day-and-hour must be: [0,399]" }
            assert(twentiethIndex in 0..19) {
                "Index of twentieth part must be: [0,19]"
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

                var semiMonth = month * 2
                var dh = (dom * 25) + hour
                val twentiethIndex = minute / 3

                if (dh >= 400) {
                    semiMonth += 1
                    dh -= 400
                }

                return Descriptor(
                    year.toShort(),
                    semiMonth.toByte(),
                    dh.toShort(),
                    twentiethIndex.toByte(),
                )
            }
        }

        private fun utc(): ZonedDateTime {
            val month = semiMonth / 2
            val halfOfMonth = semiMonth % 2
            val day = (dayAndHour / 25) + (halfOfMonth * 16)
            var hour = dayAndHour % 25
            var minute = twentiethIndex * 3

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
            return start().plusSeconds(180)
        }
    }

    override fun encode(descriptor: Descriptor): String {
        val digits1234 = String.format("%04d", descriptor.year)

        val (remSM, digit5) = Alpha24.nibble(descriptor.semiMonth.toInt())

        assert(remSM == 0) {
            "There should be nothing left after translating the " +
                "semi-month to base 24."
        }

        val (remDH1, digit7) = Alpha20.nibble(descriptor.dayAndHour.toInt())
        val (remDH2, digit6) = Alpha20.nibble(remDH1)

        assert(remDH2 == 0) {
            "There should be nothing left after translating the " +
                "day-and-hour to base 20."
        }

        val (remTI, digit8) = Alpha20.nibble(descriptor.twentiethIndex.toInt())

        assert(remTI == 0) {
            "There should be nothing left after translating the " +
                "index of the twentieth part to base 20."
        }

        return "$digits1234$digit5$digit6$digit7$digit8"
    }

    override fun decode(s: String): Result<Descriptor> = try {
        val yearText = s.subSequence(0, 4)
        val semiMonthText = s.subSequence(4, 5)
        val dhText = s.subSequence(5, 7)
        val twentiethIndexText = s.subSequence(7, 8)

        val year = Integer.parseInt(yearText.toString())
        val semiMonth = semiMonthText.fold(0, Alpha24::accumulate)
        val dh = dhText.fold(0, Alpha20::accumulate)
        val twentiethIndex = twentiethIndexText.fold(0, Alpha20::accumulate)

        Result.success(
            Descriptor(
                year.toShort(),
                semiMonth.toByte(),
                dh.toShort(),
                twentiethIndex.toByte(),
            )
        )
    } catch (e: Exception) {
        Result.failure(e)
    }

    private val logger = KotlinLogging.logger {}
}
