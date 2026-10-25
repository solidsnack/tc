# TimeCodes in Go

A Go module, `github.com/solidsnack/tc/rosetta/go`, with one package,
`timecode`, that follows revision 1.1.0 of the specification,
`../../specification/index.md`. It has no dependencies but the standard
library.

```go
import timecode "github.com/solidsnack/tc/rosetta/go"

t := time.Date(2026, 10, 17, 14, 23, 45, 250000000, time.UTC)

timecode.Encode(t, 0)                 // "2026VDJBD5.25"
timecode.Encode(t, 8)                 // "2026VDJB"
timecode.TC5(t)                       // "2026V"
timecode.TCX(t, 1)                    // "2026VDJBD5.2"
timecode.Parse("2026VDJB")            // 2026-10-17T14:23:20Z
timecode.BC(t, 5)                     // "C6VDJ"
timecode.BC4(t)                       // "C6VD"
timecode.ParseBC("C6VDJ", time.Now()) // 2026-10-17T14:23:20Z
timecode.SpecVersion                  // "1.1.0"
```

Apart from `BC`, `BC4` and `SpecVersion`, each gives an error as well, which
is `nil` here.

The encoders take a `time.Time` in any location and encode it in UTC.
`Encode` gives a code of 4 to 20 characters, or, for 0, a TCX with the
digits of the fraction of the second up to the last that is not 0. `BC`
does the same for bicentennial codes, of 2 to 18 characters. Asked for any
other length, they panic, as `TCX` does for a precision outside 0 to 9.
TimeCodes have four digit years, so `Encode`, `TC5`, `TC8` and `TCX` give
an error for a year outside 0000 to 9999; every year has a bicentennial
code.

`Parse` and `ParseBC` give a `time.Time` in UTC, or an error for a code that
is not well formed. `ParseBC` reads a code near a time: of the years with
its place in the cycle of 200 years, the one no more than 100 years before
the year of that time, and less than 100 years after. A `time.Time` has no
leap seconds, so a code past the end of its half month, like that of a leap
second, is parsed as the last second of the half month, and a leap second
cannot be encoded.

## Testing

```sh
go test ./...
```

The tests read the specification's cases through `testdata/tests.yaml`, a
link to `../../specification/tests.yaml`, with `go.yaml.in/yaml/v3`, the
module's only requirement, which only the tests import.
