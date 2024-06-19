package onl.concepts.timecode.timecodes

import java.time.Instant
import java.time.Year
import kotlin.math.min

import io.github.oshai.kotlinlogging.KotlinLogging

import onl.concepts.timecode.Time
import onl.concepts.timecode.bases.Alpha20
import onl.concepts.timecode.util.flow

/**
 *  TC10 Implementation.
 */
data class TC10(
    /** Year, starting from 0. So 2023 is 2023 and so on. */
    val year: Short,
    /** Index of 10-second window within the year. */
    val window: Int,
    /** The second within the 10-second window. */
    val second: Byte,
) : TimeCode {
    init {
        assert(year in 0..9999) { "Year must be: [0,9999]" }
        assert(window in 0..3199999) { "Window must be: [0,3199999]" }
        assert(second in 0..9) { "Second must be: [0,9]" }
    }

    companion object {
        @JvmStatic
        fun of(t: Instant): TC10 {
            val jan1 = Time.firstDayOfYear(t)
            val year = jan1.year
            val millis = t.toEpochMilli() - jan1.toInstant().toEpochMilli()
            val seconds = (millis / 1000).toInt()
            val window = seconds / 10
            val second = seconds % 10

            return TC10(year.toShort(), window, second.toByte())
        }

        @JvmStatic
        fun of(s: String): Result<TC10> = try {
            assert(s.length == 10) { "A TC10 descriptor is 10 characters." }

            val yearText = s.subSequence(0, 4)
            val windowText = s.subSequence(4, 9)
            val secondText = s.subSequence(9, 10)

            val year = Integer.parseInt(yearText.toString())
            val window = windowText.fold(0, Alpha20::accumulate)
            val second = Integer.parseInt(secondText.toString())

            Result.success(TC10(year.toShort(), window, second.toByte()))
        } catch (e: Exception) {
            Result.failure(e)
        }

        private val logger = KotlinLogging.logger {}
    }

    override val code: String by lazy {
        val digits1234 = String.format("%04d", year)

        val (remW1, digit9) = Alpha20.nibble(window.toInt())
        val (remW2, digit8) = Alpha20.nibble(remW1)
        val (remW3, digit7) = Alpha20.nibble(remW2)
        val (remW4, digit6) = Alpha20.nibble(remW3)
        val (remW5, digit5) = Alpha20.nibble(remW4)

        assert(remW5 == 0) {
            """There should be nothing left after translating the
               window to base 20.""".flow()
        }

        val digitX = String.format("%01d", second)

        "$digits1234$digit5$digit6$digit7$digit8$digit9$digitX"
    }

    override val start: Instant by lazy {
        startBounded()
    }

    override val end: Instant by lazy {
        // Adding a fixed number of seconds is correct because leap seconds
        // are always smoothed or collapsed in the timescale underlying
        // `java.time.*` classes.
        start.plusSeconds(1)
    }

    override val summary: String
        get() = Time.representInterval(start, end)

    override val midpoint: Instant
        get() = start.plusMillis(500)

    override fun describe(): Map<String, String> {
        return sortedMapOf(
            "code" to code,
            "summary" to summary,
            "encoded-year" to String.format("%04d", year),
            "encoded-window-of-10s" to String.format("%07d", window),
            "encoded-second" to String.format("%01d", second),
        )
    }

    private fun startBounded(): Instant {
        val jan1 = Time.firstDayOfYear(Year.of(year.toInt())).toInstant()
        val dec31 = Time.lastDayOfYear(Year.of(year.toInt())).toInstant()
        val lastSecond = dec31.plusSeconds(Time.daySeconds - 1)
        val maxOffset = run {
            val millis = lastSecond.toEpochMilli() - jan1.toEpochMilli()
            millis / 1000
        }
        val secondInYear = (window * 10) + second
        val boundedSecond = min(secondInYear.toLong(), maxOffset)

        return jan1.plusSeconds(boundedSecond)
    }
}
