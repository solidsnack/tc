package onl.concepts.timecode

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.YearMonth
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

/**
 * A time in UTC, as described in `specification/index.md`: the year, the
 * half month, the block of 200 seconds within the half month, the second
 * within the block and the nanosecond within the second.
 *
 * TimeCodes are immutable. They are equal when they stand for the same time,
 * and they are ordered in time, the same as their codes sort.
 *
 * Methods that are given an invalid code, descriptor, precision or length
 * throw [IllegalArgumentException].
 */
class TimeCode private constructor(
    /**
     * The components of the TimeCode.
     */
    val descriptor: Descriptor,
) : Comparable<TimeCode> {
    /**
     * Display the TimeCode in TC5 format.
     */
    fun tc5(): String = format().substring(0, 5)

    /**
     * Display the TimeCode in TC8 format.
     */
    fun tc8(): String = format().substring(0, 8)

    /**
     * Display the TimeCode in TCX format. If precision is passed, up to nine
     * digits of subseconds can be displayed.
     */
    @JvmOverloads
    fun tcX(precision: Int = 0): String {
        require(precision in 0..9) {
            "The precision must be from 0 to 9 digits."
        }

        val s = format()

        return if (precision == 0) {
            s.substring(0, 10)
        } else {
            s.substring(0, 11 + precision)
        }
    }

    /**
     * Display the TimeCode with the precision of the encoded value.
     */
    fun display(): String = format().replace(trailingZeros, "")

    /**
     * Display the TimeCode with the requested number of digits, from 4 to
     * 20. As in the specification, TC11 is displayed like TC12.
     */
    fun display(digits: Int): String {
        require(digits in 4..20) { "TimeCodes have from 4 to 20 digits." }

        return format().substring(0, if (digits == 11) 12 else digits)
    }

    /**
     * The start of the time the TimeCode stands for.
     */
    fun toInstant(): Instant {
        val d = descriptor
        val mBegin = LocalDate.of(d.year, (d.halfMonth / 2) + 1, 1)
            .atStartOfDay(ZoneOffset.UTC)
            .toInstant()
        val hmBegin = if (d.halfMonth % 2 == 1) hm2Begin else 0
        val seconds = hmBegin + (d.block * 200) + d.seconds

        return mBegin.plusSeconds(seconds.toLong())
            .plusNanos(d.nanoseconds.toLong())
    }

    override fun compareTo(other: TimeCode): Int =
        order.compare(descriptor, other.descriptor)

    override fun equals(other: Any?): Boolean =
        other is TimeCode && descriptor == other.descriptor

    override fun hashCode(): Int = descriptor.hashCode()

    override fun toString(): String = display()

    // Produce a full length (TC20) code.
    private fun format(): String {
        val (year, halfMonth, block, seconds, nanoseconds) = descriptor

        return "$year".padStart(4, '0') +
            p5[halfMonth] +
            p6[block / (19 * 19)] +
            p7[(block / 19) % 19] +
            p8[block % 19] +
            p9[seconds / 10] +
            "${seconds % 10}" +
            "." + "$nanoseconds".padStart(9, '0')
    }

    /**
     * The components of a TimeCode: the year, from 0 to 9999; the half
     * month, from 0 to 23; the block of 200 seconds within the half month,
     * from 0 to 6858; the second within the block, from 0 to 199; and the
     * nanosecond within the second, from 0 to 999,999,999.
     */
    @JvmRecord
    data class Descriptor(
        val year: Int,
        val halfMonth: Int,
        val block: Int,
        val seconds: Int,
        val nanoseconds: Int,
    )

    companion object {
        private const val p5: String = "ABCDEFGHIJKLOPQRSTUVWXYZ"
        private const val p6: String = "BDFGHJKLMPQRSTVWXYZ"
        private const val p7: String = "BCDFGHJKLMNPQRVWXYZ"
        private const val p8: String = "BCDFGHJLMNPQRSVWXYZ"
        private const val p9: String = "BCDEFHIJLMNOPQUVWXYZ"
        private const val decimal: String = "0123456789"

        // The start of the second half month in seconds. 372 hours is 15.5
        // days.
        private const val hm2Begin: Int = 372 * 3600

        private val fractionDigits = Regex("[0-9]{1,9}")
        private val trailingZeros = Regex("\\.?0+$")

        private val order = compareBy<Descriptor>(
            { it.year },
            { it.halfMonth },
            { it.block },
            { it.seconds },
            { it.nanoseconds },
        )

        /**
         * The TimeCode of an instant, in the years 0000 to 9999.
         */
        @JvmStatic
        fun of(instant: Instant): TimeCode {
            val utc = instant.atOffset(ZoneOffset.UTC)
            val year = utc.year

            require(year in 0..9999) { "TimeCodes support four digit years." }

            val mBegin = utc.withDayOfMonth(1).truncatedTo(ChronoUnit.DAYS)
            val locInMonth =
                (instant.epochSecond - mBegin.toEpochSecond()).toInt()

            // Calculate offsets needed if we are in the second half month.
            val corrections = if (locInMonth < hm2Begin) {
                listOf(0, 0)
            } else {
                listOf(1, -hm2Begin)
            }

            val wholeMonth = utc.monthValue - 1
            val locInHalfMonth = locInMonth + corrections[1]

            return TimeCode(
                Descriptor(
                    year = year,
                    halfMonth = (2 * wholeMonth) + corrections[0],
                    block = locInHalfMonth / 200,
                    seconds = locInHalfMonth % 200,
                    nanoseconds = instant.nano,
                ),
            )
        }

        /**
         * The TimeCode of a time with an offset from UTC.
         */
        @JvmStatic
        fun of(time: OffsetDateTime): TimeCode = of(time.toInstant())

        /**
         * The TimeCode of a time in a time zone.
         */
        @JvmStatic
        fun of(time: ZonedDateTime): TimeCode = of(time.toInstant())

        /**
         * The TimeCode of a descriptor, whose components must be in range. A
         * leap second takes the second just after the end of the month. Like
         * anything else past the end of the half month, it is read as the
         * last second of the half month, as `Instant.parse` does with
         * 23:59:60.
         */
        @JvmStatic
        fun of(descriptor: Descriptor): TimeCode {
            val d = descriptor

            inRange("year", d.year, 9999)
            inRange("half month", d.halfMonth, 23)
            inRange("block", d.block, (19 * 19 * 19) - 1)
            inRange("second", d.seconds, 199)
            inRange("nanosecond", d.nanoseconds, 999_999_999)

            val last = lengthOf(d.year, d.halfMonth) - 1
            val locInHalfMonth = minOf((d.block * 200) + d.seconds, last)

            return TimeCode(
                d.copy(
                    block = locInHalfMonth / 200,
                    seconds = locInHalfMonth % 200,
                ),
            )
        }

        /**
         * The current time.
         */
        @JvmStatic
        fun now(): TimeCode = of(Instant.now())

        /**
         * The current time, as told by a clock.
         */
        @JvmStatic
        fun now(clock: Clock): TimeCode = of(clock.instant())

        /**
         * Read a TimeCode of any length, from TC4 to a TCX with a fraction
         * of the second. It stands for the start of the time it covers, so
         * places that are not present count as 0.
         */
        @JvmStatic
        fun parse(code: String): TimeCode = of(read(code))

        /**
         * Present the time as a TC5 code.
         */
        @JvmStatic
        fun tc5(instant: Instant): String = of(instant).tc5()

        /**
         * Present the time as a TC8 code.
         */
        @JvmStatic
        fun tc8(instant: Instant): String = of(instant).tc8()

        /**
         * Present the time as a TCX code. If precision is passed, up to nine
         * digits of subseconds can be displayed.
         */
        @JvmStatic
        @JvmOverloads
        fun tcX(instant: Instant, precision: Int = 0): String =
            of(instant).tcX(precision)

        /**
         * Present the time with the precision of the value.
         */
        @JvmStatic
        fun tc(instant: Instant): String = of(instant).display()

        /**
         * Present the time with the requested number of digits.
         */
        @JvmStatic
        fun tc(instant: Instant, digits: Int): String =
            of(instant).display(digits)

        private fun inRange(name: String, value: Int, max: Int) {
            require(value in 0..max) { "The $name must be from 0 to $max." }
        }

        // Reads the components of a TimeCode of any length, from TC4 to TCX
        // with a fraction of the second. Places that are not present count
        // as 0.
        private fun read(code: String): Descriptor {
            val parts = code.split('.')
            val places = parts[0]
            val fraction = parts.getOrNull(1)

            require(places.length in 4..10) {
                "TimeCodes have from 4 to 10 places."
            }
            require(parts.size <= 2) {
                "TimeCodes have at most one decimal point."
            }
            require(fraction == null || places.length == 10) {
                "Only a TCX may have a fraction of the second."
            }
            require(fraction == null || fraction.matches(fractionDigits)) {
                "Fractions of the second have 1 to 9 digits."
            }

            fun digit(chars: String, i: Int): Int {
                if (i >= places.length) {
                    return 0
                }

                val n = chars.indexOf(places[i])

                require(n >= 0) {
                    val place = if (i < 9) "P${i + 1}" else "PX"
                    "${places[i]} is not allowed in $place."
                }

                return n
            }

            return Descriptor(
                year = (1000 * digit(decimal, 0)) +
                    (100 * digit(decimal, 1)) +
                    (10 * digit(decimal, 2)) +
                    digit(decimal, 3),
                halfMonth = digit(p5, 4),
                block = (19 * 19 * digit(p6, 5)) +
                    (19 * digit(p7, 6)) +
                    digit(p8, 7),
                seconds = (10 * digit(p9, 8)) + digit(decimal, 9),
                nanoseconds = fraction?.padEnd(9, '0')?.toInt() ?: 0,
            )
        }

        // The length of a half month in seconds. The second half month ends
        // with the month, so it is shorter in months of fewer than 31 days.
        private fun lengthOf(year: Int, halfMonth: Int): Int {
            if (halfMonth % 2 == 0) {
                return hm2Begin
            }

            val month = YearMonth.of(year, (halfMonth / 2) + 1)

            return (month.lengthOfMonth() * 24 * 3600) - hm2Begin
        }
    }
}
