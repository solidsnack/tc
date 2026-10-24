# timecode

TimeCodes are sortable, short timestamps, as specified in
`../specification/index.md`. This package has a library, which exports the
`TimeCode` class, and a command line tool, `timecode`.

The package is versioned apart from the specification. It exports
`SPEC_VERSION`, the revision of the specification it follows: `'1.1.0'`.

## Library

```ts
import { TimeCode } from 'timecode'

const t = Temporal.Instant.from('2026-10-17T14:23:45.25Z')

TimeCode.tc8(t) // '2026VDJB'
TimeCode.of(t).tcX(2) // '2026VDJBD5.25'
TimeCode.parse('2026VDJB').toInstant() // 2026-10-17T14:23:20Z
TimeCode.parse('2026VDJB').descriptor // { year: 2026, halfMonth: 19, ... }
TimeCode.bc4(t) // 'C6VD'
TimeCode.of(t).bc() // 'C6VDJBD5.25'
TimeCode.parseBC('C6VDJB').toInstant() // 2026-10-17T14:23:20Z, near now
```

A bicentennial code writes the year as its place in a cycle of 200 years, so
`C6` is 2026, and also 1826 and 2226. `TimeCode.parseBC` reads it in the year
nearest the present, no more than 100 years before it and less than 100
after, or near the TimeCode it is given.

The library uses the runtime's Temporal where there is one, and
`temporal-polyfill` elsewhere. It accepts a `Temporal.Instant` or a
`Temporal.ZonedDateTime` from either.

Temporal has no leap seconds, so a leap second, like any code past the end of
its half month, is read as the last second of the half month: `2016ZZNMB0` is
`2016-12-31T23:59:59Z`.

## Command Line

```sh
timecode                           # the current time, as 2026VDJB
timecode tc8 2026-10-17T14:23:45Z  # 2026VDJB
timecode tcx -p 2                  # the current time, as 2026VDJBD5.25
timecode tc 2026-10-17T14:23:45Z   # 2026VDJBD5
timecode bc4 2026-10-17T14:23:45Z  # C6VD
timecode bc -d 6                   # the current time, as C6VDJB
timecode parse 2026VDJB            # 2026-10-17T14:23:20Z
timecode parse --json 2026VDJB
timecode --help
```

Each command takes one argument. A time is a TimeCode or a timestamp in UTC,
`yyyy-mm-ddTHH:MM:SS.nnnnnnnnnZ`, where the fraction may have from 1 to 9
digits, and the time may stop after the hours, the minutes or the seconds, or
be left out: `2026-10-17T14:23:45.25Z`, `2026-10-17T14:23:45Z`,
`2026-10-17T14:23Z`, `2026-10-17T14Z` and `2026-10-17Z` are all accepted. As
in the library, second 60, for a leap second, is read as second 59. Without a
time, the commands encode the current time. Without a command, `timecode` is
the same as `timecode tc8`.

## Development

```sh
npm install
npm test     # builds, then runs specification/tests.yaml and the properties
npm run fmt  # dprint, with the guidance's settings in dprint.json
```
