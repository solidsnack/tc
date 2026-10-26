# TimeCodes in Python

A package, `timecode`, that follows revision 1.1.0 of the specification,
`../../specification/index.md`. It needs Python 3.11 or later, and nothing
else.

```python
from datetime import UTC, datetime

import timecode

t = datetime(2026, 10, 17, 14, 23, 45, 250000, tzinfo=UTC)

timecode.encode(t)  # '2026VDJBD5.25'
timecode.encode(t, 8)  # '2026VDJB'
timecode.tc5(t)  # '2026V'
timecode.tcx(t, 1)  # '2026VDJBD5.2'
timecode.parse('2026VDJB')  # datetime(2026, 10, 17, 14, 23, 20, tzinfo=UTC)
timecode.bc(t, 5)  # 'C6VDJ'
timecode.bc4(t)  # 'C6VD'
timecode.parse_bc('C6VDJ')  # datetime(2026, 10, 17, 14, 23, 20, tzinfo=UTC)
timecode.SPEC_VERSION  # '1.1.0'
```

The encoders take a `datetime` and encode it in UTC; one without a time
zone is taken to be in local time, as `datetime.astimezone` takes it.
`encode` gives a code of 4 to 20 characters, or, without a length, a TCX
with the digits of the fraction of the second up to the last that is not 0.
`bc` does the same for bicentennial codes, of 2 to 18 characters. Asked for
any other length, they raise a `ValueError`, as `tcx` does for a precision
outside 0 to 9.

`parse` and `parse_bc` give a `datetime` in UTC, or raise a `ValueError` for
a code that is not well formed. `parse_bc` reads a code near a `datetime`,
by default the present: of the years with its place in the cycle of 200
years, the one no more than 100 years before the year of that time, and less
than 100 years after.

A `datetime` keeps microseconds, and the years 1 to 9999. So a code made
here has at most six digits of the fraction of the second that are not 0,
and the fraction of a code parsed here is cut off after six digits, as in
the databases of `../sql-pg` and `../sql-duckdb`. A code of the year 0000
cannot be parsed, nor a bicentennial code read as a year past 9999; both
raise a `ValueError`. A `datetime` has no leap seconds either, so a code
past the end of its half month, like that of a leap second, is parsed as
the last second of the half month, and a leap second cannot be encoded.

## Testing

```sh
pytest
ruff format --check
ruff check
mypy
```

The tests use pytest and PyYAML, which `pyproject.toml` lists in its `test`
dependency group, and read the specification's cases through
`tests/tests.yaml`, a link to `../../specification/tests.yaml`. Ruff and
mypy, in the `lint` group, are set up there too, for the repository's
guidance. `python -m build` builds the package with Hatchling.
