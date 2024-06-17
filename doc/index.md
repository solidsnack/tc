# TCn Timecodes

The TCn Timecodes are a compact way to assign sortable identifiers to points in
time. This is of use for release tags, lot numbers, purchase orders and
other items were part of the identifier incorporates a date and time.

- TC8 - `YYYY<alpha12><alpha20><alpha20><alpha20>`
  - Represents a window of 6 minutes in UTC.
  - Calendared: The same date and time will have the same
    encoding from year to year, even in leap years.
  - Intervals of 6 minutes fit wholly within one hour or another in all
    timezones that are whole or half-hour offsets from UTC.
- TC10 - `YYYY<alpha20><alpha20><alpha20><alpha20><alpha20><base10>`
  - Represents a window of 1 second.
  - A monotonic count of seconds since the beginning of the year.

## Representation of Intervals

Timecodes are evenly spaced. They may be taken to represent a moment in time
or an interval in time.

## The Bases

- `base10` -- `[0-9]`
- `alpha12` -- `[DFHKLNPRTVXZ]`
  - Purely alphabetical base 12.
  - No vowels -- no words can be formed.
- `alpha20` -- `[BCDFGHJKLMNPQRSTVWXZ]`
  - Purely alphabetical base 20.
  - No vowels -- no words can be formed.

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

### Regarding the 25th Hour

The `DH` part represents an integer, from 0 to 399. This allows us to
assign 25 codes to every two days. The 1st and 2nd get 0 to 24, the 3rd and 4th
get 25 to 49, and so on. We use this to label every interval of two hours in
the two days. The `TI` part allows us to assign a code to each tenth of an
hour in the two hours.

There is plenty of space in February for the 29th day, when it is present,
without changing the encoding of days in the months that follow February.

Every day effectively has a 25th hour; this is simply a consequence of evenly
divvying up the 400 values in `DH` and the 20 values in `TI` among the
32 days.

For example, the first two days have values 0 to 24 in `DH`. The first twelve
values (0 to 11) are for the 24 hours of the first day. The 13th code (12)
is treated as split between the first day and the second day. The last twelve
values (13 to 24) belong to the second day.

- When the 13th code is set, the first ten values of the `TI` part (0 to 9)
  are for the "25th hour" of the first day.
- When the 25th code is set, that last ten values of the `TI` part (10 to 19)
  are for the "25th hour" of the second day.

When we have `DH` code 1 (second group of two hours) and `TI` code 10
(eleventh group of 6 minutes), what we have is:

- the first 6-minute group,
- in the second hour,
- in the second group of two hours,
- in the first day of the month.

In other words: `YYYY-MM-01T03:00/03:06Z`.

Now what does `DH` code 11 refer to? It is the twelfth group of 2 hours -- this
is interpreted as the last 2 hours of the first day of the month. Where we come
to a wrinkle is `DH` code 12 -- the thirteenth group of 2 hours. For this `DH`
code, we treat `TI` values 0 to 9 as unused, and `TI` values 10 to 19 are
for the first hour of the second day. This leads to an unused 25th hour at the
end of each day.

A timecode in this 25th hour, if ever actually presented to a conformant
parser, will sort later than other timecodes in the same day. If its time
window is requested, it should be assigned to the last window in that day,
starting at `23:54`. For example, if there is a timecode in the 25th hour
on the 27th of a given month, it should be assigned a window like:
`YYYY-MM-27T23:54/28T00:00Z` which, strictly speaking, it must be in, even if
there are leap seconds.

## Regarding TC10 & TAI

In principle, TC10 can be used to represent monotonic, continuous timestamps,
by referencing the TAI timescale when calculating the beginning of the year. We
could say that it represents "A monotonic count of SI seconds since the
beginning of the TAI year, modulo 100.". This is something that TC10 allows but
does not require. It may not be recommendable in many practical
situations.

- It makes no difference most of the time.
- It may even be better from an application standpoint to baseline against
  a different monotonic timescale, like GPS time.
- Many systems are not precise enough to keep track of the difference. The
  difference between UTC and TAI is 37 seconds as of 2024, after more
  than 50 years. Some embedded systems can drift a few minutes per day.

### UTC & TAI

The Olson database, which is used by both the JVM and POSIX to for
timezones, contains leap seconds lists and discussion of the relationship
between UTC and TAI:

- https://data.iana.org/time-zones/tzdb/leapseconds
- https://data.iana.org/time-zones/tzdb/leap-seconds.list
