package onl.concepts.tc.timecodes

import java.time.Instant
import kotlin.test.*

import net.jqwik.api.Arbitraries
import net.jqwik.api.Arbitrary
import net.jqwik.api.ForAll
import net.jqwik.api.Property
import net.jqwik.api.Provide
import onl.concepts.tc.Time
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

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

    @Test
    fun outOfBoundsDescriptorsCanNotBeConstructed() {
        assertFails {
            val descriptor = TC8.Descriptor(20240, 10, 227, 5)
        }
        assertFails {
            val descriptor = TC8.Descriptor(2024, 100, 227, 5)
        }
        assertFails {
            val descriptor = TC8.Descriptor(2024, 10, 2270, 5)
        }
        assertFails {
            val descriptor = TC8.Descriptor(2024, 10, 227, 50)
        }
    }

    private fun millis(t: ZonedDateTime): Long = t.toInstant().toEpochMilli()

    @Provide
    fun theLate90sAndEarly2000s(): Arbitrary<Instant> {
        val start = ZonedDateTime.of(1995, 1, 1, 0, 0, 0, 0, Time.utc)
        val end = ZonedDateTime.of(2005, 1, 1, 0, 0, 0, 0, Time.utc)
        return Arbitraries.longs()
            .between(millis(start), millis(end))
            .map(Instant::ofEpochMilli)
    }

    @Property
    fun encodeAndDecodeLate90sAndEarly2000s(
        @ForAll("theLate90sAndEarly2000s") t: Instant
    ) {
        val descriptor = TC8.Descriptor.of(t)
        val encoded = TC8.encode(descriptor)
        val decoded = TC8.decode(encoded).getOrThrow()

        val start = descriptor.start()
        val end = descriptor.end()

        assertEquals(descriptor, decoded, "Encoded $t to: $encoded")
        assertTrue(
            t in start..end,
            "Encoded time is not within band for encoding ($encoded): " +
                "$t !in $start..$end",
        )
    }

    @Provide
    fun the1900sAnd2000s(): Arbitrary<Instant> {
        val start = ZonedDateTime.of(1900, 1, 1, 0, 0, 0, 0, Time.utc)
        val end = ZonedDateTime.of(2100, 1, 1, 0, 0, 0, 0, Time.utc)
        return Arbitraries.longs()
            .between(millis(start), millis(end))
            .map(Instant::ofEpochMilli)
    }

    @Property
    fun encodeAndDecode1900sAnd2000s(
        @ForAll("the1900sAnd2000s") t: Instant
    ) {
        val descriptor = TC8.Descriptor.of(t)
        val encoded = TC8.encode(descriptor)
        val decoded = TC8.decode(encoded).getOrThrow()

        val start = descriptor.start()
        val end = descriptor.end()

        assertEquals(descriptor, decoded, "Encoded $t to: $encoded")
        assertTrue(
            t in start..end,
            "Encoded time is not within band for encoding ($encoded): " +
                "$t !in $start..$end",
        )
    }
}
