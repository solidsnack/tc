package onl.concepts.tc.timecodes

import java.time.Instant
import java.time.ZonedDateTime
import kotlin.test.*

import net.jqwik.api.Arbitraries
import net.jqwik.api.Arbitrary
import net.jqwik.api.ForAll
import net.jqwik.api.Property
import net.jqwik.api.Provide

import onl.concepts.tc.Time

object TC8Test: SetupTest() {
    private val jun9 = // Generated for: 2024-06-09T02:16:32Z
        Pair("2024KQKH", TC8.Descriptor(2024, 10, 227, 5))

    @Test
    fun simpleEncoding() {
        val (expected, descriptor) = jun9
        val encoded = TC8.encode(descriptor)

        assertEquals(
            expected,
            encoded,
            "Failed to encode: $descriptor (${descriptor.start()})",
        )
    }

    @Test
    fun simpleDecoding() {
        val (encoded, expected) = jun9
        val decoded = TC8.decode(encoded).getOrThrow()

        assertEquals(
            expected,
            decoded,
            "Failed to decode: $encoded",
        )
    }

    private val jan1 = // All values zero except year: 2019-01-01T00:00Z
        Pair("2019ABBB", TC8.Descriptor(2019, 0, 0, 0))

    @Test
    fun encodeAndDecodeFirstDayOfYear() {
        val (encoded, descriptor) = jan1

        assertEquals(
            encoded,
            TC8.encode(descriptor),
            "Failed to encode: $descriptor (${descriptor.start()})",
        )

        assertEquals(
            descriptor,
            TC8.decode(encoded).getOrThrow(),
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
        val descriptor = TC8.Descriptor.of(t)
        val encoded = TC8.encode(descriptor)
        val decoded = TC8.decode(encoded).getOrThrow()

        val start = descriptor.start()
        val end = descriptor.end()

        assertEquals(descriptor, decoded, "Encoded $t to: $encoded")
        assertContains(
            start..end,
            t,
            "Encoded time is not within band for encoding ($encoded)."
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
        val descriptor = TC8.Descriptor.of(t)
        val encoded = TC8.encode(descriptor)
        val decoded = TC8.decode(encoded).getOrThrow()

        val start = descriptor.start()
        val end = descriptor.end()

        assertEquals(descriptor, decoded, "Encoded $t to: $encoded")
        assertContains(
            start..end,
            t,
            "Encoded time is not within band for encoding ($encoded)."
        )
    }

    @Provide
    fun innerYear(): Arbitrary<Short> {
        return Arbitraries.shorts().between(0, 9999)
    }

    @Provide
    fun innerSemiMonth(): Arbitrary<Byte> {
        return Arbitraries.bytes().between(0, 23)
    }

    @Provide
    fun innerDH(): Arbitrary<Short> {
        return Arbitraries.shorts().between(0, 399)
    }

    @Provide
    fun innerTwentiethIndex(): Arbitrary<Byte> {
        return Arbitraries.bytes().between(0, 19)
    }

    @Property
    fun caseIndifference(
        @ForAll("innerYear") year: Short,
        @ForAll("innerSemiMonth") semiMonth: Byte,
        @ForAll("innerDH") dh: Short,
        @ForAll("innerTwentiethIndex") twentiethIndex: Byte,
    ) {
        val descriptor = TC8.Descriptor(year, semiMonth, dh, twentiethIndex)
        val encoded = TC8.encode(descriptor)
        val lower = encoded.lowercase()

        assertEquals(
            descriptor,
            TC8.decode(lower).getOrThrow(),
            "Decoding of upper ($encoded) and lower ($lower) case resulted " +
                "in different values.",
        )
    }

    @Provide
    fun invalidInnerYear(): Arbitrary<Short> {
        val below = Arbitraries.shorts().lessOrEqual(-1)
        val above = Arbitraries.shorts().greaterOrEqual(10000)
        return Arbitraries.oneOf(below, above)
    }

    @Provide
    fun invalidInnerSemiMonth(): Arbitrary<Byte> {
        val below = Arbitraries.bytes().lessOrEqual(-1)
        val above = Arbitraries.bytes().greaterOrEqual(24)
        return Arbitraries.oneOf(below, above)
    }

    @Provide
    fun invalidInnerDH(): Arbitrary<Short> {
        val below = Arbitraries.shorts().lessOrEqual(-1)
        val above = Arbitraries.shorts().greaterOrEqual(4000)
        return Arbitraries.oneOf(below, above)
    }

    @Provide
    fun invalidInnerTwentiethIndex(): Arbitrary<Byte> {
        val below = Arbitraries.bytes().lessOrEqual(-1)
        val above = Arbitraries.bytes().greaterOrEqual(20)
        return Arbitraries.oneOf(below, above)
    }

    @Property
    fun outOfBoundsDescriptorsCanNotBeConstructed(
        @ForAll("invalidInnerYear") year: Short,
        @ForAll("invalidInnerSemiMonth") semiMonth: Byte,
        @ForAll("invalidInnerDH") dh: Short,
        @ForAll("invalidInnerTwentiethIndex") twentiethIndex: Byte,
    ) {
        assertFails {
            val d = TC8.Descriptor(year, semiMonth, dh, twentiethIndex)
        }
    }
}
