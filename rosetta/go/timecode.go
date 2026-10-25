// Package timecode encodes and parses TimeCodes, sortable, short
// timestamps of digits and capital letters, and the bicentennial codes made
// from them, as revision 1.1.0 of the TimeCode specification describes
// them.
//
// [Encode] gives the TimeCode of a time, with the number of characters
// asked for or with the precision of the time, and [TC5], [TC8] and [TCX]
// give the codes the specification names. [Parse] gives the time a
// TimeCode of any length stands for. [BC], [BC4] and [ParseBC] do the same
// for bicentennial codes, which write only the place of the year in a
// cycle of 200 years, and so are read near a time.
//
// A [time.Time] keeps nanoseconds, and the years 0000 to 9999, so every
// TimeCode can be encoded and parsed. Like Temporal, it has no leap
// seconds: a code past the end of its half month, like that of a leap
// second, is parsed as the last second of the half month.
package timecode

import (
	"errors"
	"fmt"
	"strconv"
	"strings"
	"time"
)

// SpecVersion is the revision of the TimeCode specification that the
// package follows.
const SpecVersion = "1.1.0"

// The characters allowed in each place, which stand for the digits 0, 1, 2
// and so on, in that order.
const (
	decimal = "0123456789"
	b1      = "ABCDEFGHIJKLMNOPQRST"
	p5      = "ABCDEFGHIJKLOPQRSTUVWXYZ"
	p6      = "BDFGHJKLMPQRSTVWXYZ"
	p7      = "BCDFGHJKLMNPQRVWXYZ"
	p8      = "BCDFGHJLMNPQRSVWXYZ"
	p9      = "BCDEFHIJLMNOPQUVWXYZ"
)

// The start of the second half month in seconds. 372 hours is 15.5 days.
const hm2Begin = 372 * 3600

// Encode gives the TimeCode of a time in UTC, with a number of characters
// from 4 to 20. When digits is 0, the code has the precision of the time:
// it is a TCX, followed by the digits of the fraction of the second up to
// the last that is not 0, if any. As in the specification, a TC11 is
// written as a TC12.
//
// Encode gives an error when the year is not from 0000 to 9999, and panics
// when digits is not 0 or from 4 to 20.
func Encode(t time.Time, digits int) (string, error) {
	if digits != 0 && (digits < 4 || digits > 20) {
		panic(fmt.Sprintf(
			"timecode: TimeCodes have from 4 to 20 characters, not %d",
			digits,
		))
	}

	c := componentsOf(t)

	if c.year < 0 || c.year > 9999 {
		return "", fmt.Errorf(
			"timecode: TimeCodes support four digit years, not %d",
			c.year,
		)
	}

	return clip(fmt.Sprintf("%04d", c.year)+c.afterYear(), digits), nil
}

// TC5 gives the TC5 of a time: its year and half month. It gives an error
// when the year is not from 0000 to 9999.
func TC5(t time.Time) (string, error) {
	return Encode(t, 5)
}

// TC8 gives the TC8 of a time: its year, half month and block of 200
// seconds. It gives an error when the year is not from 0000 to 9999.
func TC8(t time.Time) (string, error) {
	return Encode(t, 8)
}

// TCX gives the TCX of a time, which goes to the second, followed by a
// number of digits of the fraction of the second, from 0 to 9. It gives an
// error when the year is not from 0000 to 9999, and panics when precision
// is not from 0 to 9.
func TCX(t time.Time, precision int) (string, error) {
	if precision < 0 || precision > 9 {
		panic(fmt.Sprintf(
			"timecode: a TCX has from 0 to 9 digits of the fraction of "+
				"the second, not %d",
			precision,
		))
	}

	if precision == 0 {
		return Encode(t, 10)
	}

	return Encode(t, 11+precision)
}

// Parse gives the time a TimeCode stands for, in UTC: the start of the time
// it covers, with the places that are not present counted as 0. A TimeCode
// has from 4 to 10 places, and a TCX may be followed by a point and from 1
// to 9 digits of the fraction of the second. A code past the end of its
// half month, like that of a leap second, stands for the last second of the
// half month.
func Parse(code string) (time.Time, error) {
	d, nanoseconds, err := read(code, timeCodePlaces, "TimeCodes", "TCX")

	if err != nil {
		return time.Time{}, err
	}

	year := 1000*d[0] + 100*d[1] + 10*d[2] + d[3]

	return timeOf(year, d[4:], nanoseconds), nil
}

// BC gives the bicentennial code of a time in UTC, with a number of
// characters from 2 to 18, or, when digits is 0, with the precision of the
// time, as [Encode] gives it. A BCn is the TC(n+2) of the time with the
// place of its year in the cycle written in B1 and B2, so a BC9 is written
// as a BC10. Every year has a bicentennial code.
//
// BC panics when digits is not 0 or from 2 to 18.
func BC(t time.Time, digits int) string {
	if digits != 0 && (digits < 2 || digits > 18) {
		panic(fmt.Sprintf(
			"timecode: bicentennial codes have from 2 to 18 characters, "+
				"not %d",
			digits,
		))
	}

	c := componentsOf(t)

	// The place of the year in the cycle, from 0 to 199, for any year.
	years := (c.year%200 + 200) % 200
	year := string([]byte{b1[years/10], decimal[years%10]})

	return clip(year+c.afterYear(), digits)
}

// BC4 gives the BC4 of a time: the place of its year in the cycle, its half
// month, and the first place of its block of 200 seconds.
func BC4(t time.Time) string {
	return BC(t, 4)
}

// ParseBC gives the time a bicentennial code stands for, read near a time,
// such as the present. Of the years with its place in the cycle, the code
// stands for the one nearest the year of near in UTC: no more than 100
// years before it, and less than 100 years after. Otherwise, it is parsed
// as [Parse] parses a TimeCode, with from 2 to 8 places, and a fraction of
// the second only after a BC8.
func ParseBC(code string, near time.Time) (time.Time, error) {
	d, nanoseconds, err := read(
		code, bicentennialPlaces, "bicentennial codes", "BC8",
	)

	if err != nil {
		return time.Time{}, err
	}

	years := 10*d[0] + d[1]

	// Of the years with this place in the cycle, the one nearest the year
	// of near: no more than 100 years before it, and less than 100 after.
	earliest := near.UTC().Year() - 100
	year := earliest + ((years-earliest)%200+200)%200

	return timeOf(year, d[2:], nanoseconds), nil
}

// The components of a time in UTC, from the most significant to the least:
// the year, the half month, the block of 200 seconds within the half month
// and the second within the block; and the nanoseconds of the second.
type components struct {
	year, halfMonth, block, second, nanoseconds int
}

func componentsOf(t time.Time) components {
	utc := t.UTC()
	mBegin := time.Date(utc.Year(), utc.Month(), 1, 0, 0, 0, 0, time.UTC)
	locInMonth := int(utc.Sub(mBegin) / time.Second)

	// The half month, and the time since it began.
	halfMonth := 2 * (int(utc.Month()) - 1)
	locInHalfMonth := locInMonth

	if locInMonth >= hm2Begin {
		halfMonth++
		locInHalfMonth -= hm2Begin
	}

	return components{
		year:        utc.Year(),
		halfMonth:   halfMonth,
		block:       locInHalfMonth / 200,
		second:      locInHalfMonth % 200,
		nanoseconds: utc.Nanosecond(),
	}
}

// The places of a code after the year, from P5 to PX, followed by a point
// and all nine digits of the fraction of the second.
func (c components) afterYear() string {
	return fmt.Sprintf(
		"%c%c%c%c%c%d.%09d",
		p5[c.halfMonth],
		p6[c.block/(19*19)],
		p7[c.block/19%19],
		p8[c.block%19],
		p9[c.second/10],
		c.second%10,
		c.nanoseconds,
	)
}

// A code cut off after a number of characters. A code is never cut off just
// after its point, but after the first digit of its fraction, as a TC11 is
// written as a TC12. For 0, it is cut off after the last digit of its
// fraction that is not 0, or before its point if every digit is 0.
func clip(code string, length int) string {
	point := strings.IndexByte(code, '.')

	switch length {
	case 0:
		return strings.TrimSuffix(strings.TrimRight(code, "0"), ".")
	case point + 1:
		return code[:point+2]
	default:
		return code[:length]
	}
}

// A place of a code: its name, and the characters allowed in it.
type place struct {
	name  string
	chars string
}

// The places of a TimeCode, and those of a bicentennial code, which writes
// the place of its year in the cycle in B1 and B2, where a TimeCode writes
// the year in P1 to P4.
var (
	timeCodePlaces = []place{
		{"P1", decimal},
		{"P2", decimal},
		{"P3", decimal},
		{"P4", decimal},
		{"P5", p5},
		{"P6", p6},
		{"P7", p7},
		{"P8", p8},
		{"P9", p9},
		{"PX", decimal},
	}
	bicentennialPlaces = append(
		[]place{{"B1", b1}, {"B2", decimal}},
		timeCodePlaces[4:]...,
	)
)

// The digits that the places of a code stand for, with 0 for those that are
// not present, and the nanoseconds of its fraction of the second. A code
// has at least the places of its year, and a fraction of the second only
// when it has every place. The codes, and the longest of them, are called
// kind and longest in errors.
func read(
	code string,
	places []place,
	kind string,
	longest string,
) ([]int, int, error) {
	text, fraction, hasFraction := strings.Cut(code, ".")

	// After the places of the year come six more, P5 to PX.
	shortest := len(places) - 6

	switch {
	case len(text) < shortest || len(text) > len(places):
		return nil, 0, fmt.Errorf(
			"timecode: %s have from %d to %d places",
			kind, shortest, len(places),
		)
	case strings.Contains(fraction, "."):
		return nil, 0, fmt.Errorf(
			"timecode: %s have at most one decimal point", kind,
		)
	case hasFraction && len(text) < len(places):
		return nil, 0, fmt.Errorf(
			"timecode: only a %s may have a fraction of the second",
			longest,
		)
	case hasFraction && (len(fraction) == 0 || len(fraction) > 9 ||
		strings.Trim(fraction, decimal) != ""):
		return nil, 0, errors.New(
			"timecode: fractions of the second have 1 to 9 digits",
		)
	}

	digits := make([]int, len(places))

	for i := range len(text) {
		digits[i] = strings.IndexByte(places[i].chars, text[i])

		if digits[i] < 0 {
			return nil, 0, fmt.Errorf(
				"timecode: %q is not allowed in %s",
				text[i:i+1], places[i].name,
			)
		}
	}

	nanoseconds := 0

	if hasFraction {
		nanoseconds, _ = strconv.Atoi(
			fraction + strings.Repeat("0", 9-len(fraction)),
		)
	}

	return digits, nanoseconds, nil
}

// The time that a year, and the digits of the places of a code from P5 to
// PX, stand for: the start of the time they cover, and the nanoseconds of
// its fraction of the second.
func timeOf(year int, d []int, nanoseconds int) time.Time {
	halfMonth := d[0]
	block := 19*19*d[1] + 19*d[2] + d[3]
	second := 10*d[4] + d[5]

	wholeMonth := halfMonth / 2
	isSecondHalf := halfMonth%2 == 1

	mBegin := time.Date(
		year, time.Month(wholeMonth+1), 1, 0, 0, 0, 0, time.UTC,
	)

	// The second half month ends with the month, so it is shorter in months
	// of fewer than 31 days.
	hmBegin, hmLength := 0, hm2Begin

	if isSecondHalf {
		daysInMonth := mBegin.AddDate(0, 1, -1).Day()
		hmBegin, hmLength = hm2Begin, daysInMonth*24*3600-hm2Begin
	}

	locInHalfMonth := block*200 + second

	// Codes past the end of the half month, like those of leap seconds, are
	// clipped to its last second. Temporal does the same with 23:59:60.
	locInMonth := hmBegin + min(locInHalfMonth, hmLength-1)

	return mBegin.Add(
		time.Duration(locInMonth)*time.Second + time.Duration(nanoseconds),
	)
}
