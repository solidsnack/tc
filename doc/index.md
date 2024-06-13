# TCn Timecodes

The TCn Timecodes are a compact way to assign sortable identifiers to points in
time. This is of use for release tags, lot numbers, purchase orders and
other items were part of the identifier incorporates a date and time.

- TC8 - `YYYY<alpha12><alpha20><alpha20><alpha20>`
  - Represents a window of 6 minutes in UTC.
  - Calendared: The same date and time in a different year will have the same
    encoding (except for the year), even in leap years.
  - Intervals of 6 minutes fit wholly within one hour or another in all
    timezones that are whole or half-hour offsets from UTC.
- TC10 - `YYYY<alpha8><alpha8><alpha8><alpha8><alpha8><base10>`
  - Represents a window of 100 seconds.
  - A monotonic count of seconds since the beginning of the year, modulo 100.
  - Simplified processing due to the reduced alphabet.

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
- `alpha12` -- `[DFHKLNPRTVXZ]`
    - Purely alphabetical base 12 with no vowels.
- `alpha20` -- `[BCDFGHJKLMNPQRSTVWXZ]`
  - Purely alphabetical base 20, with no vowels -- no words can be formed.

## Regarding TC8 & UTC

The TC8 code is based on representing an instant in time as its year, month,
day, hour and minute, assuming UTC days of 24 hours and UTC hours of 60
minutes. The final four characters are a compact representation of a month and
day, an hour and a twentieth of an hour.

```
YYYY<alpha12><alpha20><alpha20><alpha20>
    |      / |               / |      /
    |     /  |              /  + TI -- The 20th part of two hours.
    |    /   |             /
    |   /    + DH -- The two-day group and the two-hour window in the group.
    |  /
    + M -- The month.
```

Every month is assigned a unique letter -- they are simply every other letter
from `D` to `Z` (all consonants).

The next two digits, in base 20, identify the day and 2-hour window. Two
base 20 digits can enumerate 400 items, which is 16 * 25. This allows us to
assign a code to every grouping of 2 hours in a month of up to 32 days with
each day having up to 25 hours.

The last digit, in base 20, identifies the hour in the two-hour window and
the six-minute window in the hour.

### Regarding Leap Seconds & Leap Years

There are codes in the `DH` part of the TC8 code, the day and hour, that
can be used to handle leap years and even leap seconds, without changing.

The `DH` part represents an integer, from 0 to 399. This allows us to
assign 25 codes to every two days. The 1st and 2nd get 0 to 24, the 3rd and 4th
get 25 to 49, and so on. We use this to label every interval of two hours in
the two days. The `TI` part allows us to assign a code each tenth of hour
in the two hours.

There is plenty of space in February for the 29th day, when it is present,
without changing the encoding of days in the months that follow February.

Every day effectively has a 25th hour; this is simply a consequence of evenly
divvying up the 400 values in `DH` and the 20 values in the `TI` among the
32 days. Because the UTC leap second is always in the last hour and minute of
some UTC day, it can be represented as being in the 25th hour. This results in
an encoding that sorts after every other timecode in that day, but before all
timecodes in the following day.

This because every two days have a range of 25 values in the `DH` part. For
example, the first two days values 0 to 24. The first twelve values (0 to 11)
are for the 24 hours of the first day. The 13th code (12) is treated as split
between the first day and the second day. The last twelve values (13 to 24)
belong to the second day.

- When the 13th code is set, the first ten values of the `TI` part (0 to 9)
  are for the "25th hour" of the first day. Any leap seconds go in this hour.
- When the 25th code is set, that last ten values of the `TI` part (10 to 19)
  are for the "25th hour" of the second day. Any leap seconds go in this hour.

To see how this works exactly, we can start by looking at the first two
days of any month.

- The first two days have `DH` codes 0 to 24.
- The first twelve codes -- 0 to 11 -- are two hour windows that are in the
  first day.

So when we have `DH` code 1 (second group of two hours) and `TI` code 10
(eleventh group of 6 minutes), we are referring to the 6-minute group at the
beginning of the second hour in the second group of two hours --
`03:00/03:06` -- in the first day of the month.

Now what does `DH` code 11 refer to? It is the twelfth group of 2 hours -- this
is interpreted as the last 2 hours of the first day of the month. Where we come
to a wrinkle is `DH` code 12 -- the thirteenth group of 2 hours. It could be
simply the first two hours of the next day, but if a leap second is on the 31st
day of the month (they often are), then we have nowhere to put it.

Whereas leap years and February 29th are unavoidable, many common timekeeping
systems finesse leap seconds for us. There are two common strategies for
doing so: the old POSIX strategy of simply counting the last second of the day
twice, and UTC-SLS, a smoothed UTC that divides leap seconds over nearby
seconds. When working with systems like these, we will never need to set the
25th hour. If we receive a time code with the 25th hour in it, it won't really
change anything for us until we try to map it back to a time. For most use
cases, it will be fine to map it to the last 3 minute interval in the 24 hour
day.

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
