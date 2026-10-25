# timecode

TimeCodes are sortable, short timestamps, as specified in
`../specification/index.md`. This is a library for the JVM, in the package
`onl.concepts.timecode`. It has one class, `TimeCode`, which is meant to be
as easy to use from Java as from Kotlin.

The library is versioned apart from the specification.
`TimeCode.SPEC_VERSION` is the revision of the specification it follows:
`"1.1.0"`.

## Kotlin

```kotlin
import java.time.Instant
import onl.concepts.timecode.TimeCode

val t = Instant.parse("2026-10-17T14:23:45.25Z")

TimeCode.tc8(t) // "2026VDJB"
TimeCode.of(t).tcX(2) // "2026VDJBD5.25"
TimeCode.parse("2026VDJB").toInstant() // 2026-10-17T14:23:20Z
TimeCode.parse("2026VDJB").descriptor // Descriptor(year=2026, ...)
TimeCode.bc4(t) // "C6VD"
TimeCode.of(t).bc() // "C6VDJBD5.25"
TimeCode.parseBC("C6VDJB").toInstant() // 2026-10-17T14:23:20Z, near now
```

## Java

```java
import java.time.Instant;
import onl.concepts.timecode.TimeCode;

Instant t = Instant.parse("2026-10-17T14:23:45.25Z");

TimeCode.tc8(t); // "2026VDJB"
TimeCode.of(t).tcX(2); // "2026VDJBD5.25"
TimeCode.parse("2026VDJB").toInstant(); // 2026-10-17T14:23:20Z
TimeCode.parse("2026VDJB").getDescriptor().halfMonth(); // 19
TimeCode.bc4(t); // "C6VD"
TimeCode.parseBC("C6VDJB", TimeCode.parse("2130")); // 2226VDJBB0
```

From Java, the factories are static methods, so there is no `Companion` to
reach through, and optional arguments are overloads. The
`TimeCode.Descriptor` is a record. TimeCodes are equal when they stand for
the same time, and they are `Comparable`, in time order. An invalid code,
descriptor, precision or length throws `IllegalArgumentException`.

A bicentennial code writes the year as its place in a cycle of 200 years, so
`C6` is 2026, and also 1826 and 2226. `TimeCode.parseBC` reads it in the year
nearest the present, no more than 100 years before it and less than 100
after, or near the TimeCode it is given.

`java.time` has no leap seconds, so a leap second, like any code past the
end of its half month, is read as the last second of the half month:
`2016ZZNMB0` is `2016-12-31T23:59:59Z`.

## Development

```sh
./gradlew test   # specification/tests.yaml, jqwik properties and a Java test
./gradlew build  # the jar and a sources jar, in build/libs
```

With Nix, `nix develop .#kt` gives a shell with JDK 17, which the build
needs, and `just`. The Gradle wrapper brings Gradle and Kotlin.
