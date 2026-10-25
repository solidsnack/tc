package timecode_test

import (
	"fmt"
	"os"
	"strings"
	"testing"
	"time"

	"go.yaml.in/yaml/v3"

	timecode "github.com/solidsnack/tc/rosetta/go"
)

// A case in testdata/tests.yaml, which links to the specification's cases:
// a code or a timestamp to decode or encode, what it should give back,
// and, for a bicentennial code, the time to read it near.
type testCase struct {
	Input  string `yaml:"input"`
	Output string `yaml:"output"`
	Near   string `yaml:"near"`
}

// The cases of a group in testdata/tests.yaml.
type group struct {
	Decode []testCase `yaml:"decode"`
	Encode []testCase `yaml:"encode"`
}

// The groups of TimeCodes, by length.
var timeCodeGroups = []string{"tc5", "tc8", "tcx", "tcn"}

// The cases in testdata/tests.yaml, by group.
func cases(t *testing.T) map[string]group {
	t.Helper()

	data, err := os.ReadFile("testdata/tests.yaml")

	if err != nil {
		t.Fatal(err)
	}

	var suite map[string]group

	if err := yaml.Unmarshal(data, &suite); err != nil {
		t.Fatal(err)
	}

	return suite
}

// The time of a timestamp in UTC. A time.Time cannot hold a leap second,
// 23:59:60, so it is read as 23:59:59.
func timestamp(t *testing.T, s string) time.Time {
	t.Helper()

	u, err := time.Parse(
		time.RFC3339Nano,
		strings.Replace(s, ":60Z", ":59Z", 1),
	)

	if err != nil {
		t.Fatal(err)
	}

	return u
}

// Checks a time that a function gave back.
func checkTime(
	t *testing.T,
	call string,
	u time.Time,
	err error,
	want time.Time,
) {
	t.Helper()

	switch {
	case err != nil:
		t.Errorf("%s: %v", call, err)
	case !u.Equal(want):
		t.Errorf("%s = %v, want %v", call, u, want)
	case u.Location() != time.UTC:
		t.Errorf("%s is in %v, not UTC", call, u.Location())
	}
}

// Checks a code that a function gave back.
func checkCode(t *testing.T, call, code string, err error, want string) {
	t.Helper()

	switch {
	case err != nil:
		t.Errorf("%s: %v", call, err)
	case code != want:
		t.Errorf("%s = %q, want %q", call, code, want)
	}
}

// Checks that a function panics.
func checkPanics(t *testing.T, call string, f func()) {
	t.Helper()

	defer func() {
		if recover() == nil {
			t.Errorf("%s did not panic", call)
		}
	}()

	f()
}

func TestParse(t *testing.T) {
	suite := cases(t)

	for _, name := range timeCodeGroups {
		t.Run(name, func(t *testing.T) {
			for _, c := range suite[name].Decode {
				t.Run(c.Input, func(t *testing.T) {
					u, err := timecode.Parse(c.Input)
					call := fmt.Sprintf("Parse(%q)", c.Input)

					checkTime(t, call, u, err, timestamp(t, c.Output))
				})
			}
		})
	}
}

// Each code is given by Encode, and by the function that gives a code of
// its length by name, if there is one.
func TestEncode(t *testing.T) {
	suite := cases(t)

	for _, name := range timeCodeGroups {
		t.Run(name, func(t *testing.T) {
			for _, c := range suite[name].Encode {
				t.Run(c.Input+"="+c.Output, func(t *testing.T) {
					u := timestamp(t, c.Input)
					n := len(c.Output)

					code, err := timecode.Encode(u, n)
					call := fmt.Sprintf("Encode(t, %d)", n)
					checkCode(t, call, code, err, c.Output)

					switch {
					case name == "tc5":
						code, err = timecode.TC5(u)
						checkCode(t, "TC5(t)", code, err, c.Output)
					case name == "tc8":
						code, err = timecode.TC8(u)
						checkCode(t, "TC8(t)", code, err, c.Output)
					case name == "tcx":
						code, err = timecode.TCX(u, 0)
						checkCode(t, "TCX(t, 0)", code, err, c.Output)
					case n >= 12:
						code, err = timecode.TCX(u, n-11)
						call = fmt.Sprintf("TCX(t, %d)", n-11)
						checkCode(t, call, code, err, c.Output)
					}
				})
			}
		})
	}
}

func TestParseBC(t *testing.T) {
	for _, c := range cases(t)["bc"].Decode {
		t.Run(c.Input+"~"+c.Near, func(t *testing.T) {
			u, err := timecode.ParseBC(c.Input, timestamp(t, c.Near))
			call := fmt.Sprintf("ParseBC(%q, %s)", c.Input, c.Near)

			checkTime(t, call, u, err, timestamp(t, c.Output))
		})
	}
}

func TestBC(t *testing.T) {
	for _, c := range cases(t)["bc"].Encode {
		t.Run(c.Input+"="+c.Output, func(t *testing.T) {
			u := timestamp(t, c.Input)
			n := len(c.Output)

			call := fmt.Sprintf("BC(t, %d)", n)
			checkCode(t, call, timecode.BC(u, n), nil, c.Output)

			if n == 4 {
				checkCode(t, "BC4(t)", timecode.BC4(u), nil, c.Output)
			}
		})
	}
}

// A leap second code, like any code past the end of its half month, is
// parsed as the last second of the half month.
func TestLeapSeconds(t *testing.T) {
	suite := cases(t)

	t.Run("decode", func(t *testing.T) {
		for _, c := range suite["leap-seconds"].Decode {
			t.Run(c.Input, func(t *testing.T) {
				u, err := timecode.Parse(c.Input)
				call := fmt.Sprintf("Parse(%q)", c.Input)

				checkTime(t, call, u, err, timestamp(t, c.Output))
			})
		}
	})

	t.Run("encode", func(t *testing.T) {
		for _, c := range suite["leap-seconds"].Encode {
			t.Run(c.Input, func(t *testing.T) {
				t.Skip("a time.Time has no leap seconds")
			})
		}
	})
}

// Without a length, a code has the digits of the fraction of the second up
// to the last that is not 0, if any.
func TestPrecisionOfTheTime(t *testing.T) {
	for _, c := range []struct{ time, code, bc string }{
		{"2026-10-17T14:23:45Z", "2026VDJBD5", "C6VDJBD5"},
		{"2026-10-17T14:23:45.25Z", "2026VDJBD5.25", "C6VDJBD5.25"},
		{
			"2026-10-17T14:23:45.000000001Z",
			"2026VDJBD5.000000001",
			"C6VDJBD5.000000001",
		},
	} {
		u := timestamp(t, c.time)
		code, err := timecode.Encode(u, 0)

		checkCode(t, "Encode(t, 0)", code, err, c.code)
		checkCode(t, "BC(t, 0)", timecode.BC(u, 0), nil, c.bc)
	}
}

// A code is never cut off just after its point.
func TestTC11AndBC9(t *testing.T) {
	u := timestamp(t, "2026-10-17T14:23:45.25Z")
	code, err := timecode.Encode(u, 11)

	checkCode(t, "Encode(t, 11)", code, err, "2026VDJBD5.2")
	checkCode(t, "BC(t, 9)", timecode.BC(u, 9), nil, "C6VDJBD5.2")
}

// A time is encoded in UTC, whatever its location.
func TestLocation(t *testing.T) {
	kolkata := time.FixedZone("IST", 5*3600+30*60)
	u := time.Date(2026, 10, 17, 19, 53, 45, 0, kolkata)
	code, err := timecode.TCX(u, 0)

	checkCode(t, "TCX(t, 0)", code, err, "2026VDJBD5")
	checkCode(t, "BC4(t)", timecode.BC4(u), nil, "C6VD")
}

// TimeCodes have four digit years, and every year has a bicentennial code,
// which can be read near any year.
func TestYears(t *testing.T) {
	first := time.Date(10000, 1, 1, 0, 0, 0, 0, time.UTC)
	last := time.Date(-1, 12, 31, 23, 59, 59, 0, time.UTC)

	for _, u := range []time.Time{first, last} {
		if code, err := timecode.Encode(u, 10); err == nil {
			t.Errorf("Encode(%v, 10) = %q, want an error", u, code)
		}
	}

	checkCode(t, "BC(10000, 8)", timecode.BC(first, 8), nil, "A0ABBBB0")
	checkCode(t, "BC(-1, 8)", timecode.BC(last, 8), nil, "T9ZZNLZ9")

	near := time.Date(9950, 1, 1, 0, 0, 0, 0, time.UTC)
	u, err := timecode.ParseBC("C6", near)
	want := time.Date(10026, 1, 1, 0, 0, 0, 0, time.UTC)

	checkTime(t, `ParseBC("C6", 9950)`, u, err, want)
}

func TestParseErrors(t *testing.T) {
	for _, code := range []string{
		"",
		"202",
		"2026VDJBD50",
		"2026VDJBD5 ",
		"2026M",
		"2026vdjb",
		"C6VD",
		"2026V.5",
		"2026VDJBD.5",
		"2026VDJBD5.",
		"2026VDJBD5.1234567890",
		"2026VDJBD5.1.2",
		"2026VDJBD5.-1",
		"2026VDJBD5.2a",
	} {
		if u, err := timecode.Parse(code); err == nil {
			t.Errorf("Parse(%q) = %v, want an error", code, u)
		}
	}

	near := time.Now()

	for _, code := range []string{
		"",
		"C",
		"C6VDJBD50",
		"U6",
		"26VD",
		"C6M",
		"c6",
		"C6VDJB.5",
		"C6VDJBD5.",
		"C6VDJBD5.1234567890",
	} {
		if u, err := timecode.ParseBC(code, near); err == nil {
			t.Errorf("ParseBC(%q) = %v, want an error", code, u)
		}
	}
}

func TestLengthPanics(t *testing.T) {
	u := time.Now()

	for _, n := range []int{-1, 1, 3, 21} {
		checkPanics(t, fmt.Sprintf("Encode(t, %d)", n), func() {
			_, _ = timecode.Encode(u, n)
		})
	}

	for _, n := range []int{-1, 10} {
		checkPanics(t, fmt.Sprintf("TCX(t, %d)", n), func() {
			_, _ = timecode.TCX(u, n)
		})
	}

	for _, n := range []int{-1, 1, 19} {
		checkPanics(t, fmt.Sprintf("BC(t, %d)", n), func() {
			timecode.BC(u, n)
		})
	}
}
