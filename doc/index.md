# TCn Timecodes

The TCn Timecodes are a compact way to assign sortable identifiers to points in
time. This is of use for release tags, lot numbers, purchase orders and
other items were part of the identifier incorporates a date and time.

- TC8 - `YYYY<alpha24><alpha20><alpha20><alpha20>`
  - Represents a window of 3 minutes in UTC.
  - Calendared: The same date in a different year will have the same text
    following the first four digits.
  - Intervals of 3 minutes are compatible with all time zones in common use.
- TC10 - `YYYY<alpha8><alpha8><alpha8><alpha8><alpha8><base10>`
  - Represents a window of 100 seconds.
  - A monotonic count of seconds since the beginning of the year, modulo 100.
  - Simplified processing due to the reduced, contiguous alphabet.
  - Especially convenient for embedded or disconnected operations.

## Representation of Intervals

Timecodes are evenly spaced. They may be taken to represent a moment in time
or an interval in time.

## The Bases

- `base10` -- `[0-9]`
- `alpha8` -- `[KLMNWXYZ]`
  - Purely alphabetical base 8.
  - No vowels -- no words can be formed.
  - Integer value is the ASCII value minus 3, with bits
    above the third bit masked out: `(c - 3) & 0b111`
- `alpha20` -- `[BCDFGHJKMNPQRSTVWXYZ]`
  - Purely alphabetical base 20, with no vowels -- no words can be formed.
- `alpha24` -- `[A-X]`
  - Purely alphabetical base 24.


## Regarding TC8 & UTC

The TC8 code is based on representing an instant in time as its year, month,
day, hour and minute, assuming UTC days of 24 hours and UTC hours of 60
minutes. The final four characters are a compact representation of a month and
day, an hour and a twentieth of an hour.

```
YYYY<alpha24><alpha20><alpha20><alpha20>
    |      / |               / |      /
    |     /  |              /  + TI -- The index of the 20th of the hour.
    |    /   |             /
    |   /    + DH -- The day in the semi-month and the hour in that day.
    |  /
    + SM -- The semi-month, the month and which half of the month.
```

The semi-month assigns each month two halves. The first half is always 16 days.
The second half may be anywhere from 12 to 15 days (though up to 16 can be
represented). The coding is simply the letters in order: January gets `A` and
`B`, February `C` and `D`, and so on.

The next two digits, in base 20, identify the day in the semi-month and the
hour in the day. Two base 20 digits can enumerate 400 items, which is 16 * 25.
Because each semi-month is 16 days, these digits give us a way to enumerate the
hours in each day.

The last digit, in base 20, identifies a window of three minutes in the hour.
Because other time zones in general use are based directly on UTC, and
differ by whole multiples of 3 minutes (generally whole hours, but
sometimes with remainders of 30 or 45 minutes), these windows will generally be
wholly within one hour or another, regardless of time zone.

### Regarding Leap Seconds & Leap Years

There are codes in the `DH` part of the TC8 code, the day and hour, that
can be used to handle leap years and even leap seconds while remaining
sortable.

The `DH` part represents an integer, from 0 to 399. There are 16 days in each
semi-month, and 16 evenly divides 400, so each day gets 25 codes: the first day
is 0 to 24, the second day is 25-49, and so on.

The second half of each month has at most 15 days, so a leap day can be
added to any month, if needed. There is plenty of space in February for the
29th day, when it is present, without changing the encoding of the days that
follow it.

Every day has a 25th hour; this is simply a consequence of evenly divvying up
the 400 values among the 16 days. Because the UTC leap second is always in the
last hour and minute of some UTC day, it can be represented as being in the
25th hour. This results in an encoding that sorts after every other timecode in
that day, but before all timecodes in the following day.

Whereas leap years and February 29th are unavoidable, many common timekeeping
systems finesse leap seconds for us. There are two common strategies for
doing so: the old POSIX strategy of simply counting the last second of the day
twice, and UTC-SLS, a smoothed UTC that divides leap seconds over nearby
seconds. When working with systems like these, we will never need to set the
25th hour. If we receive a time code with the 25th hour in it, it won't really
change anything for us until we try to map it back to a time. For most use
cases, it will be fine to map it to the last 3 minute interval in the 24 hour
day.

In timekeeping systems that provide something like UTC-SLS, every step of 3
minutes on every day, from the beginning of the year, is actually evenly
divisible by 180 seconds. Every day is exactly 86400 seconds. The seconds
themselves changed (fractionally) in length on some days; but this does not
affect timekeeping calculations unless another timescale, like TAI, is
introduced.

## Regarding TC10 & TAI

In principle, TC10 can be used to represent monotonic, continuous timestamps,
by referencing the TAI timescale when calculating the beginning of the year. We
could say that it represents "A monotonic count of SI seconds since the
beginning of the TAI year, modulo 100.". This is something that the author
recommends but does not require. The author recommends it because:

- Most time scales besides UTC that are relevant for embedded or disconnected
  operations maintain a fixed or practically fixed relationship
  to TAI. For example, both TT (Terrestrial Time) and GPS Time have a (nearly)
  fixed relationship to TAI; the deviation is due to the precision of
  instruments and synchronization among systems, not due to the standards
  themselves, which are all terms of monotonic SI seconds.
- For systems that are actually able to maintain UTC correctly -- they must
  connect to the network at least every few months to check for leap seconds
  -- it is also possible to obtain the offset for TAI.
  - UTC is itself defined in terms of TAI, and by definition differs by whole
    SI seconds from TAI. The table of leap seconds allows us to calculate this
    offset.
  - Smoothed UTC, UTC-SLS, may differ by fractional seconds from TAI while the
    smoothing is occurring; but otherwise, it is the same as UTC.

However, it is not required because:

- It makes no difference most of the time:
  - The difference is not very large.
  - Much of the time, we are comparing timestamps to get a sense of the
    relative ordering of events, rather than the exact elapsed time relative to
    the atomic scale.
  - It may even be better from an application standpoint to baseline against
    another timescale, like GPS time, if only to simplify the work of those
    debugging the system, when that timescale is already used throughout the
    application.
- The difference between UTC and TAI is well within the expected limits of
  time keeping of some systems. It is 37 seconds as of 2024, after more
  than 50 years. Some embedded systems can drift a few minutes per day.

If it should happen that an application really must make a choice in this
matter, in order to maintain ensure TC10 is monotonic, then TAI is preferable
because of its foundational role in timescales -- conversions among
other timescales, conceptually, always take place with reference to TAI.

### Leap Seconds Lists

The Olson database, which is used by both the JVM and POSIX to for
timezones, contains leap seconds lists and discussion of the relationship
between UTC and TAI:

- https://data.iana.org/time-zones/tzdb/leapseconds
- https://data.iana.org/time-zones/tzdb/leap-seconds.list
