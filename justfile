# Recipes for testing TimeCodes, with or without Nix, and for testing the
# flake in a container, with Apple's container command.

# The container, and the image it is made from.
container := 'timecode-nix'
image := 'nixos/nix:2.35.2'
cpus := '4'
memory := '8G'

# Runs a command in the container's copy of the repository, as tester.
in_container := 'container exec --user tester --workdir /work ' + container

# Where pg-start keeps the server's data and socket. Git ignores tmp/.
pg_dir := justfile_directory() / 'rosetta/tmp/pg'

# nixfmt, from the flake's nixpkgs, with the guidance's indents and width.
nixfmt := 'nix run --inputs-from . nixpkgs#nixfmt -- --indent=4 --width=79'
nix_files := 'flake.nix rs/package.nix ts/package.nix'

# Lists the recipes.
default:
    @'{{ just_executable() }}' --list --unsorted --justfile '{{ justfile() }}'

# Runs the tests of the Rust library and command line tool.
[group('test')]
[working-directory('rs')]
test-rs:
    cargo test --all-features

# Runs the tests of the TypeScript library and command line tool.
[group('test')]
[working-directory('ts')]
test-ts:
    npm ci
    npm test

# Runs the tests of the Kotlin library.
[group('test')]
[working-directory('kt')]
test-kt:
    ./gradlew test

# Runs the tests of the rosetta ports, with a throwaway PostgreSQL server.
[group('test')]
[working-directory('rosetta')]
test-rosetta:
    #!/usr/bin/env bash
    set -o errexit -o nounset -o pipefail
    npm ci
    # Without initdb, the PostgreSQL tests are skipped, as they are whenever
    # psql cannot connect.
    if command -v initdb >/dev/null
    then
        just='{{ just_executable() }}'
        "$just" pg-start
        trap '"$just" pg-stop' EXIT
        eval "$("$just" pg-env)"
    fi
    npm test

# Runs all the tests.
[group('test')]
test: test-rs test-ts test-kt test-rosetta

# Starts a throwaway PostgreSQL server, on a socket in rosetta/tmp/pg.
[group('test')]
pg-start:
    #!/usr/bin/env bash
    set -o errexit -o nounset -o pipefail
    data='{{ pg_dir }}/data'
    if [[ ! -e "$data/PG_VERSION" ]]
    then
        mkdir -p "$data"
        initdb --pgdata="$data" --username=timecode --auth=trust \
            --no-sync --no-instructions >/dev/null
    fi
    # Only on a socket, so that it can run beside any other server.
    if ! pg_ctl status --pgdata="$data" >/dev/null
    then
        pg_ctl start --pgdata="$data" --log='{{ pg_dir }}/log' --wait \
            --options="-c listen_addresses='' -k '{{ pg_dir }}'" >/dev/null
    fi

# Stops the server pg-start started.
[group('test')]
pg-stop:
    pg_ctl stop --pgdata='{{ pg_dir }}/data' --mode=fast >/dev/null

# Prints the settings that connect psql to that server, for eval.
[group('test')]
pg-env:
    @printf 'export %s\n' "PGHOST='{{ pg_dir }}'" PGUSER=timecode \
        PGDATABASE=postgres

# Checks the flake, and the formatting of its Nix files.
[group('nix')]
nix-check:
    nix flake check --no-update-lock-file
    {{ nixfmt }} --check {{ nix_files }}

# Formats the Nix files.
[group('nix')]
nix-fmt:
    {{ nixfmt }} {{ nix_files }}

# Builds the command line tools, and checks what they print.
[group('nix')]
nix-build:
    #!/usr/bin/env bash
    set -o errexit -o nounset -o pipefail
    for package in timecode timecode-ts
    do
        out="$(nix build ".#$package" --no-link --print-out-paths)"
        code="$("$out/bin/timecode" bc5 2026-10-17T14:23:45Z)"
        if [[ "$code" != C6VDJ ]]
        then
            printf '%s\n' "$package printed $code, not C6VDJ." >&2
            exit 1
        fi
    done
    nix run . -- tc8

# Runs each set of tests in its own dev shell, with that shell's just.
[group('nix')]
nix-shells:
    nix develop .#rs --command just test-rs
    nix develop .#ts --command just test-ts
    nix develop .#kt --command just test-kt
    nix develop .#rosetta --command just test-rosetta

# Builds the command line tools as a flake that uses this one would.
[group('nix')]
nix-consumer:
    #!/usr/bin/env bash
    set -o errexit -o nounset -o pipefail
    consumer="$(mktemp -d)"
    trap 'rm -rf "$consumer"' EXIT
    cat > "$consumer/flake.nix" <<'EOF'
    {
        inputs.nixpkgs.url = "github:NixOS/nixpkgs/nixpkgs-unstable";
        inputs.timecode.url = "github:solidsnack/tc";
        inputs.timecode.inputs.nixpkgs.follows = "nixpkgs";

        outputs =
            { nixpkgs, timecode, ... }:
            let
                systems = [
                    "aarch64-darwin"
                    "aarch64-linux"
                    "x86_64-linux"
                ];
            in
            {
                packages = nixpkgs.lib.genAttrs systems (
                    system:
                    let
                        pkgs = import nixpkgs {
                            inherit system;
                            overlays = [ timecode.overlays.default ];
                        };
                    in
                    {
                        # As home.packages would have it.
                        default = pkgs.buildEnv {
                            name = "home";
                            paths = [ timecode.packages.${system}.default ];
                        };
                        # From the overlay, with the consumer's packages.
                        ts = pkgs.timecode-ts;
                    }
                );
            };
    }
    EOF
    # The consumer's nixpkgs is this flake's, which is already fetched, and
    # timecode is this repository.
    lock='builtins.fromJSON (builtins.readFile ./flake.lock)'
    rev="$(nix eval --raw --impure --expr "($lock).nodes.nixpkgs.locked.rev")"
    for package in default ts
    do
        out="$(nix build "$consumer#$package" --no-link --print-out-paths \
            --override-input nixpkgs "github:NixOS/nixpkgs/$rev" \
            --override-input timecode '{{ justfile_directory() }}')"
        code="$("$out/bin/timecode" bc5 2026-10-17T14:23:45Z)"
        if [[ "$code" != C6VDJ ]]
        then
            printf '%s\n' "$package printed $code, not C6VDJ." >&2
            exit 1
        fi
    done

# Runs all the Nix recipes.
[group('nix')]
nix-test: nix-check nix-build nix-shells nix-consumer

# Starts the container for testing the flake, making it if need be.
[group('container')]
container-start:
    #!/usr/bin/env bash
    set -o errexit -o nounset -o pipefail
    if ! container inspect '{{ container }}' >/dev/null 2>&1
    then
        container run --detach --init --name '{{ container }}' \
            --cpus '{{ cpus }}' --memory '{{ memory }}' \
            '{{ image }}' sleep infinity >/dev/null
        '{{ just_executable() }}' container-setup
    elif ! container list --quiet | grep -x '{{ container }}' >/dev/null
    then
        container start '{{ container }}' >/dev/null
    fi

# Prepares a new container, with a user for the tests that owns the store.
[private]
container-setup:
    #!/usr/bin/env bash
    set -o errexit -o nounset -o pipefail
    container exec --interactive '{{ container }}' bash -s <<'EOF'
    set -o errexit -o nounset -o pipefail
    # These are links into the store, which can't be changed.
    for file in /etc/group /etc/nix/nix.conf /etc/passwd
    do
        cp --remove-destination "$(readlink -f "$file")" "$file"
    done
    printf '%s\n' 'tester:x:1000:1000::/home/tester:/bin/bash' >> /etc/passwd
    printf '%s\n' 'tester:x:1000:' >> /etc/group
    printf '%s\n' 'experimental-features = nix-command flakes' \
        >> /etc/nix/nix.conf
    # The Bash port's #!/bin/bash, which the image lacks, as NixOS does.
    ln -s "$(readlink -f /bin/sh)" /bin/bash
    mkdir -p /home/tester /work
    chown -R tester:tester /home/tester /nix /work
    EOF

# Copies the working tree into the container, as one commit in a new repo.
[group('container')]
container-sync: container-start
    #!/usr/bin/env bash
    set -o errexit -o nounset -o pipefail
    {{ in_container }} find /work -mindepth 1 -delete
    # The files git would commit, as they are now, less those deleted.
    git ls-files -z --cached --others --exclude-standard |
        while IFS= read -r -d '' file
        do
            if [[ -e "$file" ]]
            then
                printf '%s\0' "$file"
            fi
        done |
        COPYFILE_DISABLE=1 tar -c -f - --format=ustar --null -T - |
        container exec --interactive --user tester --workdir /work \
            '{{ container }}' tar -x -f -
    {{ in_container }} git -c init.defaultBranch=main init --quiet
    {{ in_container }} git add --all
    {{ in_container }} git -c user.name=tester -c user.email=tester@localhost \
        commit --quiet --message='The working tree'

# Tests the flake in the container, with the working tree as it is now.
[group('container')]
container-test: container-sync
    {{ in_container }} nix run --inputs-from . nixpkgs#just -- nix-test

# Updates flake.lock in the container, and copies it back.
[group('container')]
container-lock: container-sync
    #!/usr/bin/env bash
    set -o errexit -o nounset -o pipefail
    {{ in_container }} nix flake update
    {{ in_container }} cat flake.lock > flake.lock.new
    mv flake.lock.new flake.lock

# Opens a shell in the container's copy of the repository.
[group('container')]
container-shell: container-start
    container exec --interactive --tty --user tester --workdir /work \
        '{{ container }}' bash

# Stops the container, keeping its store for next time.
[group('container')]
container-stop:
    container stop '{{ container }}'

# Deletes the container, and its store.
[group('container')]
container-delete:
    container delete --force '{{ container }}'
