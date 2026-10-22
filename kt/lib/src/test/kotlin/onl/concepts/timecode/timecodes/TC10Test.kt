package onl.concepts.timecode.timecodes

import java.time.Instant
import java.time.ZonedDateTime
import kotlin.test.*

import net.jqwik.api.Arbitraries
import net.jqwik.api.Arbitrary
import net.jqwik.api.ForAll
import net.jqwik.api.Property
import net.jqwik.api.Provide

import onl.concepts.timecode.Time

object TC10Test: SetupTest() {
    private val jan2 = // Generated for: 2019-01-02T16:47:42Z
        Pair("2019BCVSJ2", TC10(2019, 14686, 2))

    @Test
    fun simpleEncoding() {
        val (expected, tc) = jan2
        val encoded = tc.code

        assertEquals(
            expected,
            encoded,
            "Failed to encode: $tc (${tc.summary})",
        )
    }

    @Test
    fun simpleDecoding() {
        val (encoded, expected) = jan2
        val decoded = TC10.of(encoded).getOrThrow()

        assertEquals(
            expected,
            decoded,
            "Failed to decode: $encoded",
        )
    }

    private val jan1 = // All values zero except year: 2023-01-01T00:00Z
        Pair("2023BBBBB0", TC10(2023, 0, 0))

    @Test
    fun encodeAndDecodeFirstDayOfYear() {
        val (encoded, tc) = jan1

        assertEquals(
            encoded,
            tc.code,
            "Failed to encode: $tc (${tc.summary})",
        )

        assertEquals(
            tc,
            TC10.of(encoded).getOrThrow(),
            "Failed to decode: $encoded",
        )
    }

    private fun millis(t: ZonedDateTime): Long = t.toInstant().toEpochMilli()

    @Provide
    fun theLate90sAndEarly2000sUTCSLS(): Arbitrary<Instant> {
        val start = ZonedDateTime.of(1995, 1, 1, 0, 0, 0, 0, Time.utc)
        val end = ZonedDateTime.of(2005, 1, 1, 0, 0, 0, 0, Time.utc)
        return Arbitraries.longs()
            .between(millis(start), millis(end))
            .map(Instant::ofEpochMilli)
    }

    @Property
    fun encodeAndDecodeLate90sAndEarly2000sUTCSLS(
        @ForAll("theLate90sAndEarly2000sUTCSLS") t: Instant
    ) {
        val descriptor = TC10.of(t)
        val encoded = descriptor.code
        val decoded = TC10.of(encoded).getOrThrow()

        val start = descriptor.start
        val end = descriptor.end

        assertEquals(descriptor, decoded, "Encoded $t to: $encoded")
        assertContains(
            start..end,
            t,
            "Encoded time ($t) is not within band for encoding " +
                "($encoded, $descriptor)"
        )
    }

    @Provide
    fun the1900sAnd2000sUTCSLS(): Arbitrary<Instant> {
        val start = ZonedDateTime.of(1900, 1, 1, 0, 0, 0, 0, Time.utc)
        val end = ZonedDateTime.of(2100, 1, 1, 0, 0, 0, 0, Time.utc)
        return Arbitraries.longs()
            .between(millis(start), millis(end))
            .map(Instant::ofEpochMilli)
    }

    @Property
    fun encodeAndDecode1900sAnd2000sUTCSLS(
        @ForAll("the1900sAnd2000sUTCSLS") t: Instant
    ) {
        val descriptor = TC10.of(t)
        val encoded = descriptor.code
        val decoded = TC10.of(encoded).getOrThrow()

        val start = descriptor.start
        val end = descriptor.end

        assertEquals(descriptor, decoded, "Encoded $t to: $encoded")
        assertContains(
            start..end,
            t,
            "Encoded time ($t) is not within band for encoding " +
                "($encoded, $descriptor)"
        )
    }

    @Provide
    fun innerYear(): Arbitrary<Short> {
        return Arbitraries.shorts().between(0, 9999)
    }

    @Provide
    fun innerWindow(): Arbitrary<Int> {
        return Arbitraries.integers().between(0, 3199999)
    }

    @Provide
    fun innerSecond(): Arbitrary<Byte> {
        return Arbitraries.bytes().between(0, 9)
    }

    @Property
    fun caseIndifference(
        @ForAll("innerYear") year: Short,
        @ForAll("innerWindow") window: Int,
        @ForAll("innerSecond") second: Byte,
    ) {
        val descriptor = TC10(year, window, second)
        val encoded = descriptor.code
        val lower = encoded.lowercase()

        assertEquals(
            descriptor,
            TC10.of(lower).getOrThrow(),
            "Decoding of upper ($encoded) and lower ($lower) case resulted " +
                "in different values",
        )
    }

    @Provide
    fun invalidInnerYear(): Arbitrary<Short> {
        val below = Arbitraries.shorts().lessOrEqual(-1)
        val above = Arbitraries.shorts().greaterOrEqual(10000)
        return Arbitraries.oneOf(below, above)
    }

    @Provide
    fun invalidInnerWindow(): Arbitrary<Int> {
        val below = Arbitraries.integers().lessOrEqual(-1)
        val above = Arbitraries.integers().greaterOrEqual(3200000)
        return Arbitraries.oneOf(below, above)
    }

    @Provide
    fun invalidInnerSecond(): Arbitrary<Byte> {
        val below = Arbitraries.bytes().lessOrEqual(-1)
        val above = Arbitraries.bytes().greaterOrEqual(10)
        return Arbitraries.oneOf(below, above)
    }

    @Property
    fun outOfBoundsDescriptorsCanNotBeConstructed(
        @ForAll("invalidInnerYear") year: Short,
        @ForAll("invalidInnerWindow") window: Int,
        @ForAll("invalidInnerSecond") second: Byte,
    ) {
        assertFails {
            val d = TC10(year, window, second)
        }
    }
}
