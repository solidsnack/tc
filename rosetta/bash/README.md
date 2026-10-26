# TimeCodes in Bash

The Bash port's interface differs from the other codes in the following ways:

- Only `tc5`, `tc8`, `tcx`, `bc4`, `bc5` and `bc8` are available.
- The functions take one optional argument, a timestamp in UTC in this RFC 3339
  format: `yyyy-mm-ddTHH:MM:SSZ`. Without it, they print the current time. They
  are standalone functions and make use of no common constants or definitions
  (they can be copy-pasted as is and will just work).
- In place of a timestamp, they may be passed `--git` and, optionally, a
  commit, in which case they print the author date of the commit, by default
  `HEAD`. A branch, tag or any other revision will do. This needs Git 2.24 or
  later.
- `tcx` may be passed the `--extend` flag, before any timestamp, in which case
  it prints its timecode extended to milliseconds precision.
- There are no parsing functions.
