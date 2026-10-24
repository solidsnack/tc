# Guidance

## Source Code Formatting

Source code should generally be formatted:

- with spaces for indentation;
- with 4 space indents;
- with lines of 79 columns;
- with UNIX line separators.

In languages where either single quotes or double quotes can be used for
strings, prefer single quotes.

In languages where terminal semicolons are allowed but optional, the code
should be structured to avoid terminal semicolons.

When a formatter or linter is configured, they should be run and the issues
addressed before committing anything.

Imports should be put in three groups: standard library imports, third
party library imports and internal imports. There should be a blank line
between groups. Each group should be alphabetized. (Some languages, like Python
and Rust, have formatters that can do this for you.)

### Bash

1. Prefer the forms given below for `if` and other structures.
2. Use double quoted variable references aggressively. Only use unquoted
   references where the effects of such references -- word splitting, &c -- are
   being made use of.
3. Refer to `./template.bash` and apply it as a general rule.
   1. When the intrinsic logic of the script is more than 25 lines, implement
      the script by adding functions to the template and calling them from
      `main`.
   2. When the template can't be used directly, try to follow the patterns it
      suggests. For example, when writing scripts in a `justfile`, insert
      `set -o errexit -o nounset -o pipefail` before any other logic.
4. Use dashes (`-`) in function names instead of underscores, because functions
   can be directly called as subcommands.
5. Prefer lowercase variable names with underscores.

#### Preferred Forms

##### Control Structures

Use the form with `if` and `then` on separate lines.

```bash
if something
then
    step1
    step2
fi
```

Similarly, separate `while` and `do` and `for` and `do` and all other similar
cases.

### Regular Expressions

1. Prefer bracketed character classes (`[.]`) to escapes (`\.`).

### SQL

1. SQL should be formatted with "river formatting" (also known as "Joe Celko's
   Style" or "right-aligned keywords style").
2. SQL shall use 2 space indents, because sometimes a great deal of indenting
   is needed when it is needed at all.
3. SQL keywords (`JOIN`, `NULL`, `SELECT`, &c) shall be capitalized.
   1. Special forms like `COALESCE` are keywords.
   2. The PostgreSQL index types (`GIN`, `GIST`, &c) are not keywords!
4. Type names, column names, table names, function names and variable names
   shall be `snake_style`.
5. Table aliases should be avoided unless absolutely necessary; and they should
   be descriptive names, not single letters.
6. Aliases for columns or tables should always be written with `AS`.

### TS

1. TypeScript should be formatted without terminal semicolons.

## Commit Messages

1. The first line should be no more than 50 characters.
   1. It should not end in a period.
   2. It should be a simple, textual sentence.
2. The first line should be followed by a blank line.
3. The commit message should not be more than 20 lines long. It is preferable
   if it is 10 or less.
4. The use of AI should be indicated by the conventional co-authorship line.
