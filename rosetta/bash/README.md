# TimeCodes in Bash

The Bash port's interface differs from the other codes in the following ways:

- Only `tc5`, `tc8`, `tcx` and `bc4` are available.
- The functions take no arguments. They are standalone functions and make use
  of no common constants or definitions (they can be copy-pasted as is and will
  just work).
- `tcx` may be passed the `--extend` flag, in which case it prints its
  timecode extended to milliseconds precision.
- There are no parsing functions.
