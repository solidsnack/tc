# TimeCodes: Sortable, Short Timestamps

TimeCodes are short strings of digits and capital letters that identify a time
in UTC. They are meant for release tags, lot numbers, purchase orders and other
identifiers that incorporate a date and time in a simple, flat character set.
They are much shorter than ISO 8601, RFC 3339, and RFC 9557 timestamps of
comparable precision and do not make use of special characters.

- The TCX code provides resolution of single seconds in 10 characters, which
  takes 20 characters in the usual format.
- The TC8 code provides resolution of 200 seconds -- almost 160000 codes per
  year -- in 8 characters.
- The TC5 code provides resolution of half-months in 5 characters.

The codes contain the year in full, followed by a mostly-alpha encoding of the
time within the year:

- RFC 3339: `2019-10-19T05:20:50Z`
- TC5: `2019V`
- TC8: `2019VGGY`
- TCX: `2019VGGYH0`

The codes can be extended to subsecond precision (see "Variations", below) by
adding a `.` and subseconds to TCX.

The letters allowed in each place were chosen in an iterative way to avoid
natural language words and variations of offensive words, including those with
vowels or consonants dropped.

## Common Definitions

### Places

| Place | Characters                             |
| ----- | -------------------------------------- |
| P1    | `0123456789                          ` |
| P2    | `0123456789                          ` |
| P3    | `0123456789                          ` |
| P4    | `0123456789                          ` |
| P5    | `          ABCDEFGHIJKL  OPQRSTUVWXYZ` |
| P6    | `           B D FGH JKLM  PQRST VWXYZ` |
| P7    | `           BCD FGH JKLMN PQR   VWXYZ` |
| P8    | `           BCD FGH J LMN PQRS  VWXYZ` |
| P9    | `           BCDEF HIJ LMNOPQ   UVWXYZ` |
| PX    | `0123456789                          ` |

The characters allowed in each place are listed in ASCII order, and they
stand for the digits 0, 1, 2 and so on, in that order.

### Components of the Time Code

A time code writes out four components of a time in UTC, from the most
significant to the least: the year, the half month, the block of 200 seconds
within the half month, and the second within the block.

#### The Year

The year is written in P1 to P4, in decimal, with leading zeros. TimeCodes
cover the years 0000 to 9999.

#### The Half Month

Each month of the UTC calendar is divided into two half months, each with
room for 15.5 days. The first half month begins with the month and ends at
12:00 on the 16th. The second begins at 12:00 on the 16th and ends with the
month. The half months of a year are numbered from 0, the first half of
January, to 23, the second half of December.

The half month is written in P5, from `A` for the first half of January to
`Z` for the second half of December.

#### The Block of 200 Seconds

The time since the start of the half month is counted in whole seconds and
divided into blocks of 200 seconds, numbered from 0. The block is written in
P6 to P8, as three digits in base 19, which allows 6859 blocks.

A half month has room for 15.5 days, or 6696 blocks, so blocks 6696 to
6858 are left over. The second half month is shorter in months of fewer
than 31 days, which leave its last blocks unused. So a day and time of the
month always falls in the same block, in every month of every year.

In leap years, February 29th takes blocks that February otherwise leaves
unused: the same blocks as the 29th of any other month. For example,
`2024-02-29T12:00:00Z` has the TCX `2024DWNQB0`, and `2026-03-29T12:00:00Z`
has the TCX `2026FWNQB0`.

A leap second is added at the end of a month, as 23:59:60 on its last day,
so it always falls at the end of a second half month. Each leap second takes
the next second in the count. In a month of 31 days, that is the first second
of block 6,696, which is otherwise unused: the leap second at the end of 2016
is `2016ZZNMB0`, just after `2016ZZNLZ9` for 23:59:59. In a shorter month, it
is the second that would be 00:00:00 on the day after the last, which the
month otherwise leaves unused. The next half month counts from 0 again, so a
leap second never changes the code of a later time.

#### The Second Within the Block

The second within the block, from 0 to 199, is written in P9 and PX: the tens
of seconds in P9, in base 20, and the seconds in PX, in decimal. A TCX may be
followed by `.` and decimal digits for a fraction of the second, as in
`2026VDJBD5.25`. Codes with fractions still sort in time order.

### Calculating a TimeCode

The calculation is presented in terms of a `Temporal.Instant`, as specified
by the TC39 Temporal proposal (<https://tc39.es/proposal-temporal/docs/>).

```ts
function timecode(t: Temporal.Instant, digits?: number): string {
    const p5: string = "ABCDEFGHIJKLOPQRSTUVWXYZ"
    const p6: string = "BDFGHJKLMPQRSTVWXYZ"
    const p7: string = "BCDFGHJKLMNPQRVWXYZ"
    const p8: string = "BCDFGHJLMNPQRSVWXYZ"
    const p9: string = "BCDEFHIJLMNOPQUVWXYZ"

    const utc: Temporal.ZonedDateTime = t.toZonedDateTimeISO("UTC")
    const nanoseconds = (utc.millisecond * 1e6) + (utc.microsecond * 1e3) +
        utc.nanosecond
    const year = utc.year

    if (year < 0 || year > 9999) {
        throw new RangeError("TimeCodes support four digit years.")
    }

    const mBegin = utc.with({ day: 1 }).startOfDay()
    const locInMonth = (t.epochMilliseconds - mBegin.epochMilliseconds) / 1000

    // The start of the second half month in seconds. 372 hours is 15.5 days.
    const hm2Begin = 372 * 3600

    // Calculate offsets needed if we are in the second half-month.
    const corrections = locInMonth < hm2Begin ? [0, 0] : [1, -hm2Begin]

    // Calculate the half-month index.
    const wholeMonth = utc.month - 1
    const halfMonth = (2 * wholeMonth) + corrections[0]

    // Calculate the block of 200s and the seconds in the block.
    const locInHalfMonth = locInMonth + corrections[1]
    const block = Math.floor(locInHalfMonth / 200)
    const second = locInHalfMonth % 200

    // Now we have the four components: year, half-month, block and seconds.

    let accum = ""

    while (digits === undefined || accum.length < digits) {
        if (accum.length < 4) {
            accum = `${year}`.padStart(4, "0")
            continue
        }

        if (accum.length < 5) {
            accum += p5[halfMonth]
            continue
        }

        if (accum.length < 8) {
            accum += p6[Math.floor(block / (19 * 19))]
            accum += p7[Math.floor((block / 19) % 19)]
            accum += p8[block % 19]
            continue
        }

        if (accum.length < 10) {
            accum += p9[Math.floor(second / 10)]
            accum += `${Math.floor(second % 10)}`
            continue
        }

        if (digits || nanoseconds > 0) {
            accum += "." + `${nanoseconds}`.padStart(9, "0")
        } else {
            accum += ".0"
        }

        break
    }

    return digits ? accum.slice(0, (digits === 11 ? 12 : digits)) : accum
}
```

## TC5

The TC5 TimeCode is a representation of a time in UTC to a granularity of one
half month. It comprises places P1 through P5: the year and the half month.

| UTC                    | TC5     |
| ---------------------- | ------- |
| `1969-07-20T20:17:40Z` | `1969P` |
| `2000-01-01T00:00:00Z` | `2000A` |
| `2026-10-16T11:59:59Z` | `2026U` |
| `2026-10-16T12:00:00Z` | `2026V` |
| `2026-10-17T14:23:45Z` | `2026V` |

Using the program shown earlier, TC5 codes can be produced by calling
`timecode(..., 5)`.

## TC8

The TC8 TimeCode is a representation of a time in UTC to a granularity of one
block of 200 seconds. It comprises places P1 through P8: the year, the half
month and the block of 200 seconds.

| UTC                    | TC8        |
| ---------------------- | ---------- |
| `1969-07-20T20:17:40Z` | `1969PJFW` |
| `2000-01-01T00:00:00Z` | `2000ABBB` |
| `2026-10-16T11:59:59Z` | `2026UZNL` |
| `2026-10-16T12:00:00Z` | `2026VBBB` |
| `2026-10-17T14:23:45Z` | `2026VDJB` |

Using the program shown earlier, TC8 codes can be produced by calling
`timecode(..., 8)`.

## TCX

The TCX TimeCode is a representation of a time in UTC to a granularity of one
second. It comprises places P1 through PX: the year, the half month, the
block of 200 seconds and the second within the block.

| UTC                    | TCX          |
| ---------------------- | ------------ |
| `1969-07-20T20:17:40Z` | `1969PJFWI0` |
| `2000-01-01T00:00:00Z` | `2000ABBBB0` |
| `2026-10-16T11:59:59Z` | `2026UZNLZ9` |
| `2026-10-16T12:00:00Z` | `2026VBBBB0` |
| `2026-10-17T14:23:45Z` | `2026VDJBD5` |

Using the program shown earlier, TCX codes can be produced by calling
`timecode(..., 10)`.

## Variations

By keeping fractional seconds or clipping the codes described above, we have,
informally, codes ranging from TC4 (just the year) to TC20 (nanoseconds):

- RFC 3339: `2019-10-19T05:20:50.992615967Z`
- TC4: `2019`
- TC5: `2019V`
- TC6: `2019VG`
- ...
- TC11: (Doesn't really make sense -- the example code formats it like TC12.)
- TC12: `2019VGGYH0.9`
- ...
- TC16: `2019VGGYH0.99261`
- ...
- TC20: `2019VGGYH0.992615967`

## Parsing a TimeCode

A TimeCode of any length, from TC4 to a TCX with a fraction of the second,
stands for the start of the time it covers, so places that are not present
count as 0. For example, `2026V` stands for `2026-10-16T12:00:00Z`, and
`2026VDJBD` for `2026-10-17T14:23:40Z`.

A `Temporal.Instant` has no leap seconds, so the program below reads a leap
second, like any code past the end of its half month, as the last second of
the half month. Temporal does the same with 23:59:60: `2016ZZNMB0` is read
as `2016-12-31T23:59:59Z`, the same as `2016ZZNLZ9`.

```ts
function parseTimecode(code: string): Temporal.Instant {
    const decimal: string = "0123456789"
    const p5: string = "ABCDEFGHIJKLOPQRSTUVWXYZ"
    const p6: string = "BDFGHJKLMPQRSTVWXYZ"
    const p7: string = "BCDFGHJKLMNPQRVWXYZ"
    const p8: string = "BCDFGHJLMNPQRSVWXYZ"
    const p9: string = "BCDEFHIJLMNOPQUVWXYZ"

    const [places, fraction, ...extra] = code.split(".")

    if (places.length < 4 || places.length > 10) {
        throw new RangeError("TimeCodes have from 4 to 10 places.")
    }

    if (fraction !== undefined && (places.length < 10 || extra.length > 0 ||
        !/^[0-9]{1,9}$/.test(fraction))) {
        throw new RangeError(
            "Only a TCX may have a fraction, of 1 to 9 decimal digits.")
    }

    // The digit a place stands for. Places that are not present count as 0.
    const digit = (chars: string, i: number): number => {
        if (i >= places.length) {
            return 0
        }

        const n = chars.indexOf(places[i])

        if (n < 0) {
            const place = i < 9 ? `P${i + 1}` : "PX"
            throw new RangeError(`${places[i]} is not allowed in ${place}.`)
        }

        return n
    }

    // First, the four components: year, half-month, block and seconds.

    const year = (1000 * digit(decimal, 0)) + (100 * digit(decimal, 1)) +
        (10 * digit(decimal, 2)) + digit(decimal, 3)
    const halfMonth = digit(p5, 4)
    const block = (19 * 19 * digit(p6, 5)) + (19 * digit(p7, 6)) +
        digit(p8, 7)
    const second = (10 * digit(p9, 8)) + digit(decimal, 9)
    const nanoseconds = fraction === undefined ? 0 :
        Number(fraction.padEnd(9, "0"))

    // The start of the second half month in seconds. 372 hours is 15.5 days.
    const hm2Begin = 372 * 3600

    const wholeMonth = Math.floor(halfMonth / 2)
    const isSecondHalf = halfMonth % 2 === 1

    const mBegin: Temporal.ZonedDateTime = Temporal.ZonedDateTime.from({
        year: year,
        month: wholeMonth + 1,
        day: 1,
        timeZone: "UTC",
    })

    // The second half month ends with the month, so it is shorter in months
    // of fewer than 31 days.
    const hmBegin = isSecondHalf ? hm2Begin : 0
    const hmLength = isSecondHalf ?
        (mBegin.daysInMonth * 24 * 3600) - hm2Begin : hm2Begin

    const locInHalfMonth = (block * 200) + second

    // Codes past the end of the half month, like those of leap seconds, are
    // clipped to its last second. Temporal does the same with 23:59:60.
    const locInMonth = hmBegin + Math.min(locInHalfMonth, hmLength - 1)

    return mBegin.toInstant().add({
        seconds: locInMonth,
        nanoseconds: nanoseconds,
    })
}
```
