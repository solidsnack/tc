# Rosetta

Small ports of the TimeCode specification, `../specification/index.md`. Each
has a way to encode a time, at any length or as a TC5, TC8 or TCX, and a way
to parse a code. Each is checked against `../specification/tests.yaml`.

| Port          | Encode                          | Parse            |
| ------------- | ------------------------------- | ---------------- |
| `es5/`        | `encode`, `tc5`, `tc8`, `tcx`   | `decode`         |
| `sql-pg/`     | `timecode`, `tc5`, `tc8`, `tcx` | `timecode_parse` |
| `sql-duckdb/` | `timecode`, `tc5`, `tc8`, `tcx` | `timecode_parse` |

The encoders take a time, `t`. `encode(t, n)` and `timecode(t, n)` give a
code of `n` digits, from 4 to 20, or with the precision of the time if `n` is
left out. `tc5(t)` and `tc8(t)` give a TC5 and a TC8. `tcx(t, p)` gives a TCX
with `p` digits of the fraction of the second, from 0 to 9, or none if `p` is
left out. The parsers take a code of any length. Codes past the end of their
half month, like those of leap seconds, are read as the last second of the
half month.

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
database cannot be reached.
