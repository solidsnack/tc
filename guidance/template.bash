#!/bin/bash
set -o errexit -o nounset -o pipefail

function main {
    # Write functions for each script task. Change `main` to call one of them.
    : # Do nothing.
}

# Functions go here.

##################################################################### Utilities

function msg {
    out "$*" >&2
}

function err {
    local x=$?
    msg "$*"
    return $(( $x == 0 ? 1 : $x ))
}

function out {
    printf '%s\n' "$*"
}

# Handles "no-match" exit code specified by POSIX for filtering tools.
function maybe {
    "$@" || return $(( $? == 1 ? 0 : $? ))
}


######################### Delegates to subcommands or runs main, as appropriate

if declare -f -- "${1:-}" >/dev/null
then
    "$@"
else
    main "$@"
fi
