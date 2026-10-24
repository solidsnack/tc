# timecode

TimeCodes are sortable, short timestamps, as specified in
`../specification/index.md`. This crate has a library, with the `TimeCode`
type, and a command line tool, `timecode`, behind the `cli` feature. It uses
the `time` crate, and needs no time zone data.

The crate is versioned apart from the specification. `SPEC_VERSION` is the
revision of the specification it follows: `"1.1.0"`.

## Library

```rust
use time::UtcDateTime;
use timecode::TimeCode;

let code: TimeCode = "2026VDJBD5.25".parse()?;

code.tc5(); // "2026V"
code.tc8(); // "2026VDJB"
code.tcx(2); // "2026VDJBD5.25"
code.tc(12); // "2026VDJBD5.2"
code.to_string(); // "2026VDJBD5.25", with the precision of the value
code.descriptor(); // Descriptor { year: 2026, half_month: 19, ... }
code.bc4(); // "C6VD"
code.bc(11); // "C6VDJBD5.25"

let t: UtcDateTime = code.into(); // the start of its time, 14:23:45.25
TimeCode::try_from(t)?; // the same TimeCode
TimeCode::now();
TimeCode::parse_bc("C6VDJBD5.25")?; // the same TimeCode, read near now
TimeCode::parse_bc_near("C6VDJBD5.25", code)?; // read near a TimeCode
```

| From             | Into             | Trait                          |
| ---------------- | ---------------- | ------------------------------ |
| `&str`           | `TimeCode`       | `FromStr`                      |
| `UtcDateTime`    | `TimeCode`       | `TryFrom`, for years 0 to 9999 |
| `OffsetDateTime` | `TimeCode`       | `TryFrom`, for years 0 to 9999 |
| `Descriptor`     | `TimeCode`       | `TryFrom`, if in range         |
| `TimeCode`       | `UtcDateTime`    | `From`                         |
| `TimeCode`       | `OffsetDateTime` | `From`, in UTC                 |
| `TimeCode`       | `Descriptor`     | `From`                         |

A `TimeCode` becomes the start of the time it stands for.

A bicentennial code writes the year as its place in a cycle of 200 years, so
`C6` is 2026, and also 1826 and 2226. `parse_bc` reads it in the year
nearest the present, no more than 100 years before it and less than 100
after, and `parse_bc_near` reads it near another TimeCode.

Errors are `timecode::Error`. `tcx`, `tc` and `bc` never fail: a precision
of more than 9 digits gives 9, a length of fewer than 4 digits or more than
20 gives a TC4 or a TC20, and a length of fewer than 2 digits or more than 18
gives a BC2 or a BC18. TimeCodes are ordered in time, the same as their codes
sort.

The `time` crate has no leap seconds, so a leap second, like any code past
the end of its half month, is read as the last second of the half month:
`2016ZZNMB0` is `2016-12-31T23:59:59Z`.

## Command Line

The command line tool needs the `cli` feature, which brings in `clap`.
`--locked` builds it with the versions of its dependencies in `Cargo.lock`.

```sh
cargo install --locked --features=cli --path .
```

```sh
timecode                           # the current time, as 2026VDJB
timecode tc8 2026-10-17T14:23:45Z  # 2026VDJB
timecode tcx -p 2                  # the current time, as 2026VDJBD5.25
timecode tc 2026-10-17T14:23:45Z   # 2026VDJBD5
timecode bc4 2026-10-17T14:23:45Z  # C6VD
timecode bc5 2026-10-17T14:23:45Z  # C6VDJ
timecode bc8                       # the current time, as C6VDJBD5
timecode bc -d 6                   # the current time, as C6VDJB
timecode parse 2026VDJB            # 2026-10-17T14:23:20Z
timecode parse --json 2026VDJB
timecode --help
```

Each command takes one argument. A time is a TimeCode or a timestamp in UTC,
`yyyy-mm-ddTHH:MM:SS.nnnnnnnnnZ`, where the fraction may have from 1 to 9
digits, and the time may stop after the hours, the minutes or the seconds, or
be left out: `2026-10-17T14:23:45.25Z`, `2026-10-17T14:23:45Z`,
`2026-10-17T14:23Z`, `2026-10-17T14Z` and `2026-10-17Z` are all accepted.
Second 60, for a leap second, is read as second 59. Without a time, the
commands encode the current time. Without a command, `timecode` is the same as
`timecode tc8`.

## Development

```sh
cargo test --all-features  # specification/tests.yaml, properties, docs
cargo run --features=cli   # the command line tool
cargo fmt && cargo clippy --all-targets --all-features
```
