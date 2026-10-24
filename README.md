# TimeCodes

TimeCodes are sortable, short timestamps.

- `specification/` has the specification and its test cases.
- `kt/` has the Kotlin library, for Kotlin and Java, built with Gradle.
- `ts/` has the TypeScript library and command line tool, built with npm.
- `rs/` has the Rust library and command line tool, built with Cargo.
- `rosetta/` has small ports: ECMAScript 5, PostgreSQL and DuckDB.

Implementations of the TimeCode specification are versioned separately from it
but contain a constant indicating which version of the TimeCode spec they
follow.
