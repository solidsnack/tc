package onl.concepts.timecode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

// The library as Java sees it: static factories, overloads in place of
// default arguments, a record for the descriptor and unchecked exceptions.
class JavaUsageTest {
    private final Instant t = Instant.parse("2026-10-17T14:23:45.25Z");

    @Test
    void encodes() {
        TimeCode code = TimeCode.of(t);

        assertEquals("2026V", code.tc5());
        assertEquals("2026VDJB", code.tc8());
        assertEquals("2026VDJBD5", code.tcX());
        assertEquals("2026VDJBD5.25", code.tcX(2));
        assertEquals("2026VDJBD5.25", code.display());
        assertEquals("2026VDJBD5.2", code.display(12));
        assertEquals("2026VDJBD5.25", code.toString());
    }

    @Test
    void encodesWithStatics() {
        assertEquals("2026V", TimeCode.tc5(t));
        assertEquals("2026VDJB", TimeCode.tc8(t));
        assertEquals("2026VDJBD5", TimeCode.tcX(t));
        assertEquals("2026VDJBD5.250", TimeCode.tcX(t, 3));
        assertEquals("2026VDJBD5.25", TimeCode.tc(t));
        assertEquals("2026VDJBD", TimeCode.tc(t, 9));
    }

    @Test
    void encodesOtherTimes() {
        OffsetDateTime offset = t.atOffset(ZoneOffset.ofHours(-5));
        ZonedDateTime zoned = offset.toZonedDateTime();
        Clock clock = Clock.fixed(t, ZoneOffset.UTC);

        assertEquals(TimeCode.of(t), TimeCode.of(offset));
        assertEquals(TimeCode.of(t), TimeCode.of(zoned));
        assertEquals(TimeCode.of(t), TimeCode.now(clock));
    }

    @Test
    void decodes() {
        TimeCode code = TimeCode.parse("2026VDJB");
        TimeCode.Descriptor d = code.getDescriptor();

        assertEquals(Instant.parse("2026-10-17T14:23:20Z"), code.toInstant());
        assertEquals(2026, d.year());
        assertEquals(19, d.halfMonth());
        assertEquals(475, d.block());
        assertEquals(0, d.seconds());
        assertEquals(0, d.nanoseconds());
        assertEquals(
            code,
            TimeCode.of(new TimeCode.Descriptor(2026, 19, 475, 0, 0))
        );
    }

    @Test
    void sorts() {
        List<TimeCode> codes = new ArrayList<>();
        codes.add(TimeCode.parse("2026VDJBD5"));
        codes.add(TimeCode.parse("2026V"));
        codes.add(TimeCode.parse("2026VDJB"));

        Collections.sort(codes);

        assertEquals("[2026VBBBB0, 2026VDJBB0, 2026VDJBD5]", codes.toString());
    }

    @Test
    void rejects() {
        IllegalArgumentException e = assertThrows(
            IllegalArgumentException.class,
            () -> TimeCode.parse("2026VAAA")
        );

        assertEquals("A is not allowed in P6.", e.getMessage());
        assertThrows(
            IllegalArgumentException.class,
            () -> TimeCode.of(t).tcX(10)
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> TimeCode.of(new TimeCode.Descriptor(2026, 24, 0, 0, 0))
        );
    }
}
