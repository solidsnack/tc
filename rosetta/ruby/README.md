# TimeCodes in Ruby

A gem, `timecode`, with one module, `TimeCode`, that follows revision 1.1.0
of the specification, `../../specification/index.md`. It needs Ruby 3.2 or
later, and nothing else.

```ruby
require 'timecode'

t = Time.utc(2026, 10, 17, 14, 23, 45, 250_000)

TimeCode.encode(t)         # => "2026VDJBD5.25"
TimeCode.encode(t, 8)      # => "2026VDJB"
TimeCode.tc5(t)            # => "2026V"
TimeCode.tcx(t, 1)         # => "2026VDJBD5.2"
TimeCode.parse('2026VDJB') # => 2026-10-17 14:23:20 UTC
TimeCode.bc(t, 5)          # => "C6VDJ"
TimeCode.bc4(t)            # => "C6VD"
TimeCode.parse_bc('C6VDJ') # => 2026-10-17 14:23:20 UTC
TimeCode::SPEC_VERSION     # => "1.1.0"
```

The encoders take a `Time` with any offset and encode it in UTC.
`TimeCode.encode` gives a code of 4 to 20 characters, or, without a length,
a TCX with the digits of the fraction of the second up to the last that is
not 0. `TimeCode.bc` does the same for bicentennial codes, of 2 to 18
characters. Asked for any other length, they raise an `ArgumentError`, as
`TimeCode.tcx` does for a precision outside 0 to 9. TimeCodes have four
digit years, so `encode`, `tc5`, `tc8` and `tcx` raise a `RangeError` for a
year outside 0000 to 9999; every year has a bicentennial code.

`TimeCode.parse` and `TimeCode.parse_bc` give a `Time` in UTC, or raise an
`ArgumentError` for a code that is not well formed. `parse_bc` reads a code
near a `Time`, by default the present: of the years with its place in the
cycle of 200 years, the one no more than 100 years before the year of that
time, and less than 100 years after. A `Time` has no leap seconds, and reads
23:59:60 as the next second, so a code past the end of its half month, like
that of a leap second, is parsed as the last second of the half month, and a
leap second cannot be encoded.

## Testing

```sh
rake test
rubocop
```

The tests use Minitest and Rake, which come with Ruby, and read the
specification's cases through `test/tests.yaml`, a link to
`../../specification/tests.yaml`. With Bundler, `bundle exec rake test`
works as well. RuboCop follows the repository's guidance, as
`.rubocop.yml` sets it up.
