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

### TS

1. TypeScript should be formatted without terminal semicolons.

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

## Commit Messages

1. The first line should be no more than 50 characters.
   1. It should not end in a period.
   2. It should be a simple, textual sentence.
2. The first line should be followed by a blank line.
3. The commit message should not be more than 20 lines long. It is preferable
   if it is 10 or less.
4. The use of AI should be indicated by the conventional co-authorship line.
