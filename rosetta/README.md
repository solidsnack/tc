# Rosetta

Small ports of the TimeCode specification, `../specification/index.md`. Most
have a way to encode a time, at any length or as a TC5, TC8 or TCX, and a way
to parse a code; the Bash port prints a time, by default the current time, as
a TC5, TC8, TCX, BC4, BC5 or BC8. Each is checked against
`../specification/tests.yaml`.

Most of the ports are files, which are tested together, with Node:

| Port          | Encode                                   | Parse            |
| ------------- | ---------------------------------------- | ---------------- |
| `es5/`        | `encode`, `tc5`, `tc8`, `tcx`            | `decode`         |
| `sql-pg/`     | `timecode`, `tc5`, `tc8`, `tcx`          | `timecode_parse` |
| `sql-duckdb/` | `timecode`, `tc5`, `tc8`, `tcx`          | `timecode_parse` |
| `bash/`       | `tc5`, `tc8`, `tcx`, `bc4`, `bc5`, `bc8` |                  |

Apart from Bash's, the encoders take a time, `t`. `encode(t, n)` and
`timecode(t, n)` give a code of `n` digits, from 4 to 20, or with the
precision of the time if `n` is left out. `tc5(t)` and `tc8(t)` give a TC5
and a TC8. `tcx(t, p)` gives a TCX with `p` digits of the fraction of the
second, from 0 to 9, or none if `p` is left out. The parsers take a code of
any length. Codes past the end of their half month, like those of leap
seconds, are read as the last second of the half month.

The others are packages, in the usual form for their languages, with tests
of their own. They encode and parse bicentennial codes as well as
TimeCodes:

- `go/`, a Go module: `Encode`, `TC5`, `TC8`, `TCX` and `Parse`, and `BC`,
  `BC4` and `ParseBC`.
- `ruby/`, a gem: `encode`, `tc5`, `tc8`, `tcx` and `parse`, and `bc`,
  `bc4` and `parse_bc`.

Each port also gives the revision of the specification it follows:
`timecode.SPEC_VERSION` in ECMAScript 5, `timecode_spec_version()` in SQL,
`SPEC_VERSION` in Bash, `timecode.SpecVersion` in Go and
`TimeCode::SPEC_VERSION` in Ruby. The Bash port and the packages follow
1.1.0, for their bicentennial codes; the others follow 1.0.0, without them.

## ECMAScript 5

```js
timecode.encode('2026-10-17T14:23:45.25Z') // '2026VDJBD5.25'
timecode.encode(new Date(), 8) // the current TC8
timecode.tcx('2026-10-17T14:23:45.25Z', 1) // '2026VDJBD5.2'
timecode.decode('2026VDJB') // '2026-10-17T14:23:20Z'
```

The file has no dependencies. It defines `timecode` as a global, or as the
module's exports under CommonJS. Times are RFC 3339 timestamps in UTC, with up
to nine digits of the fraction of the second, so that a code keeps its
precision; a `Date`, which holds milliseconds, may also be encoded.

## PostgreSQL

```sql
SELECT timecode(now(), 12),
       tc8(now()),
       tcx(now(), 3),
       timecode_parse('2026VDJB');
```

`timecode`, `tc5`, `tc8` and `tcx` take a `timestamptz`, and `timecode_parse`
gives one, in any session time zone. The digits of the fraction of a TCX are
`fraction_digits`, since `precision` is a keyword. A helper, `timecode_digit`,
is made as well.

## DuckDB

```sql
.read sql-duckdb/timecode.sql
SELECT timecode(now(), 12),
       tc8(now()),
       tcx(now(), 3),
       timecode_parse('2026VDJB');
```

As in PostgreSQL, `timecode`, `tc5`, `tc8` and `tcx` take a `timestamptz`,
and `timecode_parse` gives one, in any session time zone. The macros cast
what they are given to a `timestamptz`, as a function would, so a `timestamp`
is read in the session's time zone. The digits of the fraction of a TCX are
`fraction_digits` here too. A helper macro, `timecode_digit`, is made as
well.

## Bash

```sh
bash/timecode.bash                           # the current time, as a TC8
bash/timecode.bash tcx 2026-10-17T14:23:45Z  # 2026VDJBD5
bash/timecode.bash bc5 2026-10-17T14:23:45Z  # C6VDJ
bash/timecode.bash bc8                       # the current time, as C6VDJBD5
bash/timecode.bash tcx --extend              # the current time, to the ms
```

As `bash/README.md` says, each function takes a timestamp in UTC,
`yyyy-mm-ddTHH:MM:SSZ`, or prints the current time without one. `tcx` also
takes `--extend`, before any timestamp, to print the time to the
millisecond, as a TC14, such as `2026VDJBD5.250`; a timestamp has whole
seconds, so its milliseconds are `000`. Each field of a timestamp must be in
range, though the day is not checked against the length of its month, and
second 60, for a leap second, counts as the next second, as in the
specification.

Each function stands alone, so it can be copied into another script as it
is. It reads the current time with one call to `date`, which must know `%N`
for `--extend`, as GNU date and the date of macOS 26 do. The script needs
Bash 3.2 or later, at `/bin/bash`; where there is none, as on NixOS, it can
be run as `bash bash/timecode.bash`.

## Go

```go
tc8, err := timecode.Encode(time.Now(), 8) // the current TC8
t, err := timecode.Parse("2026VDJB")       // 2026-10-17T14:23:20Z
bc5 := timecode.BC(time.Now(), 5)          // the current BC5
u, err := timecode.ParseBC("C6VDJ", near)  // read near a time.Time
```

`go/` is a module, `github.com/solidsnack/tc/rosetta/go`, with one package,
`timecode`, which needs only the standard library; `go/README.md` describes
it. It takes and gives a `time.Time`, which keeps nanoseconds and the year
0000, so every TimeCode can be encoded and parsed, but it has no leap
seconds. A length of 0 asks for a code with the precision of the time.

## Ruby

```ruby
TimeCode.encode(Time.now, 8)  # the current TC8
TimeCode.parse('2026VDJB')    # 2026-10-17 14:23:20 UTC
TimeCode.bc(Time.now, 5)      # the current BC5
TimeCode.parse_bc('C6VDJ')    # read near the present, or a Time given
```

`ruby/` is a gem, `timecode`, with one module, `TimeCode`, which needs only
Ruby 3.2 or later; `ruby/README.md` describes it. Like a `time.Time` in Go,
a `Time` keeps nanoseconds and the year 0000, but has no leap seconds.
Without a length, a code has the precision of the time.

## Precision

The databases keep microseconds, so a code made there has at most six digits
of the fraction that are not 0, and the fraction of a code read there is cut
off after six digits. PostgreSQL has no year 0, so the year the specification
calls 0000 is 1 BC there; DuckDB prints it as 0001 (BC). PostgreSQL reads
23:59:60 as 00:00:00 of the next day, and DuckDB does not read it at all, so
their tests of encoding leap seconds are skipped.

## Testing

```sh
npm install
npm test
```

The PostgreSQL tests connect with `psql` and the usual `PG*` environment
variables, and make the functions in a transaction that is rolled back. The
DuckDB tests use the `duckdb` command, in memory. Either is skipped when its
database cannot be reached. The Bash tests give the script the timestamp
of each case, and run it with a `date` command of their own, which gives
the fraction of the second too.

From anywhere in the repository, `just test-rosetta` runs the tests with a
throwaway PostgreSQL server, on a socket in `tmp/pg`, and stops it after.

The packages are tested with their languages' own tools, and read the cases
through a link to `../specification/tests.yaml`. From anywhere in the
repository, `just test-go` checks the formatting of the Go package with
`gofmt`, vets it and runs `go test`, and `just test-ruby` checks the gem
with RuboCop, runs its tests with Rake and Minitest, and builds it.

With Nix, `nix develop .#rosetta` gives a shell with Node, PostgreSQL,
DuckDB, Go, Ruby, RuboCop and `just`. Its `bash` and `date` are Bash 5 and
GNU date, from Nix, so the tests use them where they call `bash` or the real
clock, though the script itself still runs with `/bin/bash`.
