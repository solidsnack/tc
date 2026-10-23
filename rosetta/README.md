# Rosetta

Small ports of the TimeCode specification, `../specification/index.md`. Each
has a way to encode a time, at any length or as a TC5, TC8 or TCX, and a way
to parse a code. Each is checked against `../specification/tests.yaml`.

| Port   | Encode                        | Parse    |
| ------ | ----------------------------- | -------- |
| `es5/` | `encode`, `tc5`, `tc8`, `tcx` | `decode` |

The encoders take a time, `t`. `encode(t, n)` gives a code of `n` digits,
from 4 to 20, or with the precision of the time if `n` is left out. `tc5(t)`
and `tc8(t)` give a TC5 and a TC8. `tcx(t, p)` gives a TCX with `p` digits of
the fraction of the second, from 0 to 9, or none if `p` is left out. The
parser takes a code of any length. Codes past the end of their half month,
like those of leap seconds, are read as the last second of the half month.

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

## Testing

```sh
npm install
npm test
```
