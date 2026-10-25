# TimeCodes

TimeCodes are sortable, short timestamps.

- `specification/` has the specification and its test cases.
- `kt/` has the Kotlin library, for Kotlin and Java, built with Gradle.
- `ts/` has the TypeScript library and command line tool, built with npm.
- `rs/` has the Rust library and command line tool, built with Cargo.
- `rosetta/` has small ports: ECMAScript 5, PostgreSQL, DuckDB, Bash, Go and
  Ruby.

Implementations of the TimeCode specification are versioned separately from it
but contain a constant indicating which version of the TimeCode spec they
follow.

## Nix

The flake at the root of the repository builds the command line tools:
`timecode`, the default, is the Rust one, and `timecode-ts` the TypeScript
one. Both install a `timecode` command, so a profile can have only one.

```sh
nix run github:solidsnack/tc -- bc5        # run it once
nix profile add github:solidsnack/tc       # install it
nix profile add github:solidsnack/tc#timecode-ts
```

In another flake, such as a Home Manager configuration, it is an input:

```nix
{
    inputs.timecode.url = "github:solidsnack/tc";
    inputs.timecode.inputs.nixpkgs.follows = "nixpkgs";
}
```

The tool is then `timecode.packages.${system}.default`, for `home.packages`.
With `timecode.overlays.default` among the overlays of nixpkgs, it is
`pkgs.timecode`, or `pkgs.timecode-ts`, built with that nixpkgs.

The flake has a dev shell for each directory, with its tools and `just`:
`rs`, `ts`, `kt` and `rosetta`. The `default` shell has all of them.

```sh
nix develop .#rs  # from anywhere in the repository
```

## Testing

The `justfile` runs the tests, in the dev shells or wherever the tools are
installed, and tests the flake in a container, with Apple's `container`.

```sh
just test            # the tests of every directory
just test-rosetta    # with a throwaway PostgreSQL server in rosetta/tmp/pg
just container-test  # the flake's checks, packages and dev shells, on Linux
just container-lock  # update flake.lock
just                 # the other recipes
```

`just container-test` copies the working tree into the container and runs
`just nix-test` there, which runs as well wherever Nix is installed. As with
any flake in a Git repository, Nix sees only the files Git knows of, so new
files must be added to the index first.
