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
    private val nov3 = // Generated for: 2022-11-05T13:31:32Z
        Pair("2022XJKH", TC8.Descriptor(2022, 10, 2545))

    @Test
    fun simpleEncoding() {
        val (expected, descriptor) = nov3
        val encoded = TC8.encode(descriptor)

        assertEquals(
            expected,
            encoded,
            "Failed to encode: $descriptor (${descriptor.start()})",
        )
    }

    @Test
    fun simpleDecoding() {
        val (encoded, expected) = nov3
        val decoded = TC8.decode(encoded).getOrThrow()

        assertEquals(
            expected,
            decoded,
            "Failed to decode: $encoded",
        )
    }

    private val jan1 = // All values zero except year: 2019-01-01T00:00Z
        Pair("2019DBBB", TC8.Descriptor(2019, 0, 0))

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
        val descriptor = TC8.Descriptor.of(t)
        val encoded = TC8.encode(descriptor)
        val decoded = TC8.decode(encoded).getOrThrow()

        val start = descriptor.start()
        val end = descriptor.end()

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
    fun innerMonth(): Arbitrary<Byte> {
        return Arbitraries.bytes().between(0, 11)
    }

    @Provide
    fun innerTwentiethIndex(): Arbitrary<Short> {
        return Arbitraries.shorts().between(0, 7999)
    }

    @Property
    fun caseIndifference(
        @ForAll("innerYear") year: Short,
        @ForAll("innerMonth") month: Byte,
        @ForAll("innerTwentiethIndex") twentiethIndex: Short,
    ) {
        val descriptor = TC8.Descriptor(year, month, twentiethIndex)
        val encoded = TC8.encode(descriptor)
        val lower = encoded.lowercase()

        assertEquals(
            descriptor,
            TC8.decode(lower).getOrThrow(),
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
    fun invalidInnerMonth(): Arbitrary<Byte> {
        val below = Arbitraries.bytes().lessOrEqual(-1)
        val above = Arbitraries.bytes().greaterOrEqual(12)
        return Arbitraries.oneOf(below, above)
    }

    @Provide
    fun invalidInnerTwentiethIndex(): Arbitrary<Short> {
        val below = Arbitraries.shorts().lessOrEqual(-1)
        val above = Arbitraries.shorts().greaterOrEqual(8000)
        return Arbitraries.oneOf(below, above)
    }

    @Property
    fun outOfBoundsDescriptorsCanNotBeConstructed(
        @ForAll("invalidInnerYear") year: Short,
        @ForAll("invalidInnerMonth") month: Byte,
        @ForAll("invalidInnerTwentiethIndex") twentiethIndex: Short,
    ) {
        assertFails {
            val d = TC8.Descriptor(year, month, twentiethIndex)
        }
    }
}
