package onl.concepts.timecode

import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import net.jqwik.api.Arbitraries
import net.jqwik.api.Arbitrary
import net.jqwik.api.Combinators
import net.jqwik.api.ForAll
import net.jqwik.api.Property
import net.jqwik.api.Provide
import net.jqwik.kotlin.api.combine
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue

private const val p5 = "ABCDEFGHIJKLOPQRSTUVWXYZ"
private const val p6 = "BDFGHJKLMPQRSTVWXYZ"
private const val p7 = "BCDFGHJKLMNPQRVWXYZ"
private const val p8 = "BCDFGHJLMNPQRSVWXYZ"
private const val p9 = "BCDEFHIJLMNOPQUVWXYZ"
private const val b1 = "ABCDEFGHIJKLMNOPQRST"
private const val decimal = "0123456789"

private val first = Instant.parse("0000-01-01T00:00:00Z")
private val last = Instant.parse("9999-12-31T23:59:59.999999999Z")

// The instant, kept within the years TimeCodes cover.
private fun within(t: Instant): Instant = minOf(maxOf(t, first), last)

private fun ints(min: Int, max: Int) = Arbitraries.integers().between(min, max)

private fun longs(min: Long, max: Long) = Arbitraries.longs().between(min, max)

class PropertiesTest {
    // Any instant TimeCodes cover, with extra weight on the edges of half
    // months, where the calendar matters most.
    @Provide
    fun instants(): Arbitrary<Instant> {
        val anywhere = combine(
            longs(first.epochSecond, last.epochSecond),
            ints(0, 999_999_999),
        ) { seconds, nanos -> Instant.ofEpochSecond(seconds, nanos.toLong()) }
        val nearEdge = combine(
            ints(0, 9999),
            ints(0, 23),
            longs(-400_000_000_000, 400_000_000_000),
        ) { year, halfMonth, offset ->
            val code = "$year".padStart(4, '0') + p5[halfMonth]
            val start = TimeCode.parse(code).toInstant()
            within(start.plusNanos(offset))
        }

        return Arbitraries.oneOf(anywhere, nearEdge)
    }

    // Two instants, the first no later than the second and often close to
    // it.
    @Provide
    fun ordered(): Arbitrary<Pair<Instant, Instant>> {
        val span = Duration.between(first, last)
        val gap = Arbitraries.oneOf(
            Arbitraries.just(Duration.ZERO),
            longs(1, 1_000_000_000).map { Duration.ofNanos(it) },
            longs(1, 1_000_000_000_000).map { Duration.ofNanos(it) },
            combine(longs(0, span.seconds), ints(0, 999_999_999)) { s, n ->
                Duration.ofSeconds(s, n.toLong())
            },
        )

        return combine(instants(), gap) { x, d -> x to within(x.plus(d)) }
    }

    // Lengths of TimeCodes, from TC4 to TC20.
    @Provide
    fun digits(): Arbitrary<Int> = ints(4, 20)

    // Lengths of bicentennial codes, from BC2 to BC18.
    @Provide
    fun bcDigits(): Arbitrary<Int> = ints(2, 18)

    // Offsets of a year to read a bicentennial code near: no more than 100
    // years after it, and less than 100 before.
    @Provide
    fun offsets(): Arbitrary<Int> = ints(-99, 100)

    @Provide
    fun years(): Arbitrary<Int> = ints(0, 9999)

    @Provide
    fun encoders(): Arbitrary<String> = Arbitraries.of("tc5", "tc8", "tcX")

    // Two TC5, TC8 or TCX codes the grammar allows, whether or not they are
    // past the end of their half month. They share their first few places,
    // so that they often differ only in the block or the second of one half
    // month. Within each place, the characters are in sorted order.
    @Provide
    fun codes(): Arbitrary<Pair<String, String>> {
        fun pick(chars: String) = Arbitraries.of(chars.toList())

        val places = listOf(
            pick(decimal),
            pick(decimal),
            pick(decimal),
            pick(decimal),
            pick(p5),
            // Often the last letter, where codes run past the end of the
            // half month.
            Arbitraries.oneOf(pick(p6), Arbitraries.just('Z')),
            pick(p7),
            pick(p8),
            pick(p9),
            pick(decimal),
        )
        val chars = Combinators.combine(places).`as` { it }
        val lengths = Arbitraries.of(5, 8, 10)
        val shared = Arbitraries.oneOf(ints(0, 10), ints(5, 9))

        return combine(chars, lengths, chars, lengths, shared) {
                a, n, b, m, k ->
            val c = a.take(k) + b.drop(k)
            a.take(n).joinToString("") to c.take(m).joinToString("")
        }
    }

    // Any descriptor in range, often with a block near or past the end of
    // the half month.
    @Provide
    fun descriptors(): Arbitrary<TimeCode.Descriptor> = combine(
        ints(0, 9999),
        ints(0, 23),
        Arbitraries.oneOf(ints(0, 6858), ints(5300, 6858)),
        ints(0, 199),
        ints(0, 999_999_999),
    ) { year, halfMonth, block, seconds, nanoseconds ->
        TimeCode.Descriptor(year, halfMonth, block, seconds, nanoseconds)
    }

    // The order is tested for TC5, TC8 and TCX, without fractions of the
    // second. A code past the end of its half month reads as the last
    // second, keeping its fraction, so 23:59:60.5 would read as a later time
    // than 23:59:61.
    @Property(tries = 2000)
    fun `an earlier time has a code that sorts no later`(
        @ForAll("ordered") times: Pair<Instant, Instant>,
        @ForAll("encoders") encoder: String,
    ) {
        val (x, y) = times
        val (xc, yc) = listOf(x, y).map {
            val code = TimeCode.of(it)

            when (encoder) {
                "tc5" -> code.tc5()
                "tc8" -> code.tc8()
                else -> code.tcX()
            }
        }

        assertTrue(xc <= yc, "$x $y: $xc $yc")
    }

    @Property(tries = 2000)
    fun `a code that sorts earlier reads as a time no later`(
        @ForAll("codes") codes: Pair<String, String>,
    ) {
        val (a, b) = codes
        val (xc, yc) = if (a < b) a to b else b to a
        val (x, y) = listOf(xc, yc).map { TimeCode.parse(it).toInstant() }

        assertTrue(x <= y, "$xc $yc: $x $y")
    }

    @Property(tries = 2000)
    fun `a full length code reads as the same time`(
        @ForAll("instants") t: Instant,
    ) {
        val code = TimeCode.of(t)

        assertEquals(t, TimeCode.parse(code.toString()).toInstant())
        assertEquals(t, TimeCode.of(code.descriptor).toInstant())
    }

    @Property(tries = 2000)
    fun `a shorter code reads as the start of its window`(
        @ForAll("instants") t: Instant,
        @ForAll("digits") n: Int,
    ) {
        val code = TimeCode.of(t).display(n)
        val start = TimeCode.parse(code).toInstant()

        assertTrue(start <= t)
        assertEquals(code, TimeCode.of(start).display(n))

        if (start != first) {
            val before = start.minusNanos(1)
            assertNotEquals(code, TimeCode.of(before).display(n))
        }
    }

    @Property(tries = 2000)
    fun `every code is the start of the full length code`(
        @ForAll("instants") t: Instant,
        @ForAll("digits") n: Int,
        @ForAll("precisions") p: Int,
    ) {
        val code = TimeCode.of(t)
        val full = code.display(20)
        val shorter = listOf(
            code.tc5() to 5,
            code.tc8() to 8,
            code.tcX(p) to if (p == 0) 10 else 11 + p,
            code.display(n) to if (n == 11) 12 else n,
        )

        for ((s, length) in shorter) {
            assertEquals(full.substring(0, length), s)
        }

        assertTrue(full.startsWith(code.toString()))
    }

    @Provide
    fun precisions(): Arbitrary<Int> = ints(0, 9)

    @Provide
    fun zones(): Arbitrary<ZoneId> = Arbitraries.of(
        ZoneOffset.UTC,
        ZoneId.of("America/Chicago"),
        ZoneId.of("Asia/Kolkata"),
        ZoneOffset.ofHoursMinutes(5, 45),
    )

    @Property
    fun `a zoned time has the same code as its instant`(
        @ForAll("instants") t: Instant,
        @ForAll("zones") zone: ZoneId,
    ) {
        val zoned = t.atZone(zone)
        val expected = TimeCode.of(t)

        assertEquals(expected, TimeCode.of(zoned))
        assertEquals(expected, TimeCode.of(zoned.toOffsetDateTime()))
    }

    @Property(tries = 2000)
    fun `a code past the end of its half month reads as its last second`(
        @ForAll("descriptors") d: TimeCode.Descriptor,
    ) {
        fun offset(d: TimeCode.Descriptor) = (d.block * 200) + d.seconds

        val t = TimeCode.of(d).toInstant()
        val read = TimeCode.of(t).descriptor
        val label = "$d: $t"

        assertEquals(d.year, read.year, label)
        assertEquals(d.halfMonth, read.halfMonth, label)
        assertEquals(d.nanoseconds, read.nanoseconds, label)

        if (offset(read) < offset(d)) {
            // One second later is in the next half month, which may be past
            // the year 9999.
            val next = t.plusSeconds(1)
            val isPast = next.atOffset(ZoneOffset.UTC).year > 9999 ||
                TimeCode.of(next).descriptor.halfMonth != d.halfMonth
            assertTrue(isPast, label)
        } else {
            assertEquals(d, read, label)
        }
    }

    // The place of a TimeCode's year in its cycle of 200 years.
    private fun place(code: TimeCode): String {
        val years = code.descriptor.year % 200

        return "${b1[years / 10]}${years % 10}"
    }

    // The TimeCode of the start of a year.
    private fun startOf(year: Int): TimeCode =
        TimeCode.parse("$year".padStart(4, '0'))

    @Property(tries = 2000)
    fun `a bicentennial code is the TimeCode with the place of its year`(
        @ForAll("instants") t: Instant,
        @ForAll("bcDigits") n: Int,
    ) {
        val code = TimeCode.of(t)

        assertEquals(
            place(code) + code.display(n + 2).substring(4),
            code.bc(n),
        )
        assertEquals(place(code) + code.display().substring(4), code.bc())
        assertEquals(code.bc(4), code.bc4())
    }

    // Near a time no more than 100 years after it, or less than 100 before,
    // a bicentennial code reads as the TimeCode it comes from does.
    @Property(tries = 2000)
    fun `a bicentennial code reads near its time as its TimeCode does`(
        @ForAll("instants") t: Instant,
        @ForAll("bcDigits") n: Int,
        @ForAll("offsets") offset: Int,
    ) {
        val code = TimeCode.of(t)
        val near = startOf((code.descriptor.year + offset).coerceIn(0, 9999))

        assertEquals(
            TimeCode.parse(code.display(n + 2)),
            TimeCode.parseBC(code.bc(n), near),
        )
    }

    @Property(tries = 2000)
    fun `a bicentennial code reads as the nearest year in its cycle`(
        @ForAll("instants") t: Instant,
        @ForAll("years") nearYear: Int,
    ) {
        val code = TimeCode.of(t)
        val read = try {
            TimeCode.parseBC(code.bc4(), startOf(nearYear))
        } catch (e: IllegalArgumentException) {
            // The nearest year may be before 0000 or after 9999.
            assertTrue(nearYear < 100 || nearYear >= 9900)
            assertEquals("TimeCodes support four digit years.", e.message)
            return
        }
        val year = read.descriptor.year

        assertTrue(year - nearYear >= -100 && year - nearYear < 100)
        assertEquals(code.descriptor.year % 200, year % 200)
    }
}
