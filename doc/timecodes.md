# TimeCodes: Sortable, Short Timestamps

TimeCodes are short strings of digits and capital letters that identify a
time in UTC. They are meant for release tags, lot numbers, purchase orders
and other identifiers that incorporate a date and time.

- **Sortable.** Codes of the same kind sort ASCIIbetically in time order.
- **Nested.** Shortening a code gives the code for the same time at a coarser
  granularity. The TC5 of an instant is the first five characters of its
  TC8, and its TC8 is the first eight characters of its TCX.
- **Inoffensive.** The letters allowed in each place are carefully
  chosen to avoid natural language words and any variations of offensive
  words, including those with vowels or consonants dropped.

The characters of a code are called places. There are ten places, P1 to P9
and then PX. Each kind of code uses the places from P1 up to the place that
gives it its length: a TC5 uses P1 to P5, a TC8 uses P1 to P8, and a TCX uses
all ten.

This document specifies TC5, TC8 and TCX. The appendix describes the other
lengths.

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

#### Half Months

Each month of the UTC calendar is divided into two half months, each with
room for 15.5 days. The first half month begins with the month and ends at
12:00 on the 16th. The second begins at 12:00 on the 16th and ends with the
month. It is shorter in months of fewer than 31 days, which leave its last
codes unused. So a day and time of the month always has the same code, in
every month of every year, and February 29th takes codes that are otherwise
unused. The half months of a year are numbered from 0, the first half of
January, to 23, the second half of December.

#### Blocks of 200 Seconds

The time since the start of a half month is counted in whole seconds and
divided into blocks of 200 seconds, numbered from 0. A half month is at most
15.5 days long, so it has at most 6,696 blocks. The block is written as three
digits in base 19, which allows 6,859 blocks. The calculations below never
produce a block numbered 6,696 or more; those blocks leave room for leap
seconds in systems that count them. `Temporal.Instant` does not count leap
seconds.

The 200 seconds of a block are written as tens of seconds, in base 20, and
then seconds, in decimal. A TCX may be followed by `.` and decimal digits for
a fraction of the second, as in `2026VDJBD5.25`. Codes with fractions still
sort in time order.

### Calculation

The example calculations are presented in terms of a `Temporal.Instant`, as
specified by the TC39 Temporal proposal
(<https://tc39.es/proposal-temporal/docs/>). They share these definitions:

```ts
const P5: string = "ABCDEFGHIJKLOPQRSTUVWXYZ"
const P6: string = "BDFGHJKLMPQRSTVWXYZ"
const P7: string = "BCDFGHJKLMNPQRVWXYZ"
const P8: string = "BCDFGHJLMNPQRSVWXYZ"
const P9: string = "BCDEFHIJLMNOPQUVWXYZ"

interface Parts {
    year: number
    halfMonth: number
    secondsInHalfMonth: number
}

function parts(instant: Temporal.Instant): Parts {
    const utc: Temporal.ZonedDateTime = instant.toZonedDateTimeISO("UTC")
    if (utc.year < 0 || utc.year > 9999) {
        throw new RangeError("TimeCodes cover the years 0000 to 9999.")
    }
    const start: Temporal.ZonedDateTime = utc.with({ day: 1 }).startOfDay()
    const elapsed: number = Math.floor(
        (instant.epochMilliseconds - start.epochMilliseconds) / 1000,
    )
    const length: number = 1339200 // 15.5 days, in seconds
    const isSecondHalf: number = elapsed < length ? 0 : 1
    return {
        year: utc.year,
        halfMonth: 2 * (utc.month - 1) + isSecondHalf,
        secondsInHalfMonth: elapsed - (isSecondHalf * length),
    }
}

function year(p: Parts): string {
    return String(p.year).padStart(4, "0")
}
```

## TC5

The TC5 timecode is a representation of a time in UTC to a granularity of one
half month. It has five places, P1 to P5.

| Place | Characters                             |
| ----- | -------------------------------------- |
| P1    | `0123456789                          ` |
| P2    | `0123456789                          ` |
| P3    | `0123456789                          ` |
| P4    | `0123456789                          ` |
| P5    | `          ABCDEFGHIJKL  OPQRSTUVWXYZ` |

- P1 to P4 are the year, in decimal.
- P5 is the half month.

To calculate the TC5 of an instant, split it into parts and write them out:

```ts
function tc5(instant: Temporal.Instant): string {
    const p: Parts = parts(instant)
    return year(p) + P5[p.halfMonth]
}
```

For example, `2026-10-17T14:23:45Z` has the TC5 `2026V`.

## TC8

The TC8 timecode is a representation of a time in UTC to a granularity of one
block of 200 seconds. It has eight places, P1 to P8.

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

- P1 to P4 are the year, in decimal.
- P5 is the half month.
- P6 to P8 are the block, in base 19.

To calculate the TC8 of an instant, split it into parts and write them out:

```ts
function tc8(instant: Temporal.Instant): string {
    const p: Parts = parts(instant)
    const block: number = Math.floor(p.secondsInHalfMonth / 200)
    return year(p) + P5[p.halfMonth] +
        P6[Math.floor(block / 361)] +
        P7[Math.floor(block / 19) % 19] +
        P8[block % 19]
}
```

For example, `2026-10-17T14:23:45Z` has the TC8 `2026VDJB`.

## TCX

The TCX timecode is a representation of a time in UTC to a granularity of one
second. It has ten places, P1 to PX.

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

- P1 to P4 are the year, in decimal.
- P5 is the half month.
- P6 to P8 are the block, in base 19.
- P9 is the tens of seconds within the block, in base 20.
- PX is the seconds within the block, in decimal.

To calculate the TCX of an instant, split it into parts and write them out:

```ts
function tcx(instant: Temporal.Instant): string {
    const p: Parts = parts(instant)
    const block: number = Math.floor(p.secondsInHalfMonth / 200)
    const second: number = p.secondsInHalfMonth % 200
    return year(p) + P5[p.halfMonth] +
        P6[Math.floor(block / 361)] +
        P7[Math.floor(block / 19) % 19] +
        P8[block % 19] +
        P9[Math.floor(second / 10)] +
        String(second % 10)
}
```

For example, `2026-10-17T14:23:45Z` has the TCX `2026VDJBD5`.

## Appendix: Variations

The places can be cut off after any of P4 to PX. Besides TC5, TC8 and TCX,
this gives TC4, TC6, TC7 and TC9. They follow the same rules and use the
same common definitions. The windows of TC6 and TC7 start at the beginning
of the half month and do not line up with days or hours.

### TC4

The TC4 timecode is a representation of a time in UTC to a granularity of one
year. It has four places, P1 to P4.

| Place | Characters                             |
| ----- | -------------------------------------- |
| P1    | `0123456789                          ` |
| P2    | `0123456789                          ` |
| P3    | `0123456789                          ` |
| P4    | `0123456789                          ` |

- P1 to P4 are the year, in decimal.

To calculate the TC4 of an instant, split it into parts and write them out:

```ts
function tc4(instant: Temporal.Instant): string {
    const p: Parts = parts(instant)
    return year(p)
}
```

For example, `2026-10-17T14:23:45Z` has the TC4 `2026`.

### TC6

The TC6 timecode is a representation of a time in UTC to a granularity of 361
blocks, or 72,200 seconds (20 hours, 3 minutes and 20 seconds). It has six
places, P1 to P6.

| Place | Characters                             |
| ----- | -------------------------------------- |
| P1    | `0123456789                          ` |
| P2    | `0123456789                          ` |
| P3    | `0123456789                          ` |
| P4    | `0123456789                          ` |
| P5    | `          ABCDEFGHIJKL  OPQRSTUVWXYZ` |
| P6    | `           B D FGH JKLM  PQRST VWXYZ` |

- P1 to P4 are the year, in decimal.
- P5 is the half month.
- P6 is the first digit of the block, in base 19.

To calculate the TC6 of an instant, split it into parts and write them out:

```ts
function tc6(instant: Temporal.Instant): string {
    const p: Parts = parts(instant)
    const block: number = Math.floor(p.secondsInHalfMonth / 200)
    return year(p) + P5[p.halfMonth] +
        P6[Math.floor(block / 361)]
}
```

For example, `2026-10-17T14:23:45Z` has the TC6 `2026VD`.

### TC7

The TC7 timecode is a representation of a time in UTC to a granularity of 19
blocks, or 3,800 seconds (1 hour, 3 minutes and 20 seconds). It has seven
places, P1 to P7.

| Place | Characters                             |
| ----- | -------------------------------------- |
| P1    | `0123456789                          ` |
| P2    | `0123456789                          ` |
| P3    | `0123456789                          ` |
| P4    | `0123456789                          ` |
| P5    | `          ABCDEFGHIJKL  OPQRSTUVWXYZ` |
| P6    | `           B D FGH JKLM  PQRST VWXYZ` |
| P7    | `           BCD FGH JKLMN PQR   VWXYZ` |

- P1 to P4 are the year, in decimal.
- P5 is the half month.
- P6 and P7 are the first two digits of the block, in base 19.

To calculate the TC7 of an instant, split it into parts and write them out:

```ts
function tc7(instant: Temporal.Instant): string {
    const p: Parts = parts(instant)
    const block: number = Math.floor(p.secondsInHalfMonth / 200)
    return year(p) + P5[p.halfMonth] +
        P6[Math.floor(block / 361)] +
        P7[Math.floor(block / 19) % 19]
}
```

For example, `2026-10-17T14:23:45Z` has the TC7 `2026VDJ`.

### TC9

The TC9 timecode is a representation of a time in UTC to a granularity of 10
seconds. It has nine places, P1 to P9.

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

- P1 to P4 are the year, in decimal.
- P5 is the half month.
- P6 to P8 are the block, in base 19.
- P9 is the tens of seconds within the block, in base 20.

To calculate the TC9 of an instant, split it into parts and write them out:

```ts
function tc9(instant: Temporal.Instant): string {
    const p: Parts = parts(instant)
    const block: number = Math.floor(p.secondsInHalfMonth / 200)
    const second: number = p.secondsInHalfMonth % 200
    return year(p) + P5[p.halfMonth] +
        P6[Math.floor(block / 361)] +
        P7[Math.floor(block / 19) % 19] +
        P8[block % 19] +
        P9[Math.floor(second / 10)]
}
```

For example, `2026-10-17T14:23:45Z` has the TC9 `2026VDJBD`.
