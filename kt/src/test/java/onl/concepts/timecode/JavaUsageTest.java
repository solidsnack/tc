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
        assertEquals("C6VD", code.bc4());
        assertEquals("C6VDJBD5.25", code.bc());
        assertEquals("C6VDJBD5.2", code.bc(10));
    }

    @Test
    void encodesWithStatics() {
        assertEquals("2026V", TimeCode.tc5(t));
        assertEquals("2026VDJB", TimeCode.tc8(t));
        assertEquals("2026VDJBD5", TimeCode.tcX(t));
        assertEquals("2026VDJBD5.250", TimeCode.tcX(t, 3));
        assertEquals("2026VDJBD5.25", TimeCode.tc(t));
        assertEquals("2026VDJBD", TimeCode.tc(t, 9));
        assertEquals("C6VD", TimeCode.bc4(t));
        assertEquals("C6VDJBD5.25", TimeCode.bc(t));
        assertEquals("C6VDJBD", TimeCode.bc(t, 7));
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
    void decodesBicentennialCodes() {
        TimeCode near = TimeCode.parse("2130");

        assertEquals(TimeCode.parse("2026VDJB"), TimeCode.parseBC("C6VDJB"));
        assertEquals(
            TimeCode.parse("2226VDJB"),
            TimeCode.parseBC("C6VDJB", near)
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
        assertThrows(
            IllegalArgumentException.class,
            () -> TimeCode.of(t).bc(19)
        );
    }

    @Test
    void rejectsBicentennialCodes() {
        TimeCode near = TimeCode.parse("2026");
        String[][] errors = {
            {"C", "Bicentennial codes have from 2 to 8 places."},
            {"C6VDJBD5B", "Bicentennial codes have from 2 to 8 places."},
            {"C6VD.5", "Only a BC8 may have a fraction of the second."},
            {"U6VD", "U is not allowed in B1."},
            {"CXVD", "X is not allowed in B2."},
            {"C6MD", "M is not allowed in P5."},
            {"C6VDJBD5.", "Fractions of the second have 1 to 9 digits."},
        };

        for (String[] error : errors) {
            IllegalArgumentException e = assertThrows(
                IllegalArgumentException.class,
                () -> TimeCode.parseBC(error[0], near)
            );

            assertEquals(error[1], e.getMessage());
        }

        assertThrows(
            IllegalArgumentException.class,
            () -> TimeCode.parseBC("C6", TimeCode.parse("9950"))
        );
    }
}
