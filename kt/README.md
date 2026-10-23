# timecode

TimeCodes are sortable, short timestamps, as specified in
`../specification/index.md`. This is a library for the JVM, in the package
`onl.concepts.timecode`. It has one class, `TimeCode`, which is meant to be
as easy to use from Java as from Kotlin.

## Kotlin

```kotlin
import java.time.Instant
import onl.concepts.timecode.TimeCode

val t = Instant.parse("2026-10-17T14:23:45.25Z")

TimeCode.tc8(t) // "2026VDJB"
TimeCode.of(t).tcX(2) // "2026VDJBD5.25"
TimeCode.parse("2026VDJB").toInstant() // 2026-10-17T14:23:20Z
TimeCode.parse("2026VDJB").descriptor // Descriptor(year=2026, ...)
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
```

From Java, the factories are static methods, so there is no `Companion` to
reach through, and optional arguments are overloads. The
`TimeCode.Descriptor` is a record. TimeCodes are equal when they stand for
the same time, and they are `Comparable`, in time order. An invalid code,
descriptor, precision or length throws `IllegalArgumentException`.

`java.time` has no leap seconds, so a leap second, like any code past the
end of its half month, is read as the last second of the half month:
`2016ZZNMB0` is `2016-12-31T23:59:59Z`.

## Development

```sh
./gradlew test   # specification/tests.yaml, jqwik properties and a Java test
./gradlew build  # the jar and a sources jar, in build/libs
```
