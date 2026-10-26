package timecode_test

import (
	"fmt"
	"time"

	timecode "github.com/solidsnack/tc/rosetta/go"
)

func ExampleEncode() {
	t := time.Date(2026, 10, 17, 14, 23, 45, 250000000, time.UTC)

	for _, digits := range []int{0, 8, 14} {
		code, err := timecode.Encode(t, digits)

		if err != nil {
			panic(err)
		}

		fmt.Println(code)
	}
	// Output:
	// 2026VDJBD5.25
	// 2026VDJB
	// 2026VDJBD5.250
}

func ExampleTCX() {
	t := time.Date(2026, 10, 17, 14, 23, 45, 250000000, time.UTC)
	code, err := timecode.TCX(t, 1)

	if err != nil {
		panic(err)
	}

	fmt.Println(code)
	// Output: 2026VDJBD5.2
}

func ExampleParse() {
	t, err := timecode.Parse("2026VDJB")

	if err != nil {
		panic(err)
	}

	fmt.Println(t.Format(time.RFC3339Nano))
	// Output: 2026-10-17T14:23:20Z
}

func ExampleBC() {
	t := time.Date(2026, 10, 17, 14, 23, 45, 0, time.UTC)

	fmt.Println(timecode.BC(t, 5))
	fmt.Println(timecode.BC4(t))
	// Output:
	// C6VDJ
	// C6VD
}

func ExampleParseBC() {
	near := time.Date(2130, 1, 1, 0, 0, 0, 0, time.UTC)
	t, err := timecode.ParseBC("C6VDJ", near)

	if err != nil {
		panic(err)
	}

	fmt.Println(t.Format(time.RFC3339Nano))
	// Output: 2226-10-17T14:23:20Z
}
