#!/bin/bash
set -o errexit -o nounset -o pipefail

# TimeCodes in Bash, as described in specification/index.md.

# The revision of specification/index.md that this port follows.
SPEC_VERSION='1.1.0'

function main {
    # Without a command, print a TC8, as the other command line tools do.
    tc8 "$@"
}

# Each function below prints the current time. Each stands alone, using
# nothing but its own definitions, so that it can be copied into another
# script as it is, and reads the clock with one call to date.

# The current time as a TC5, such as 2026V.
function tc5 {
    local p5='ABCDEFGHIJKLOPQRSTUVWXYZ'
    local now year month day hour

    if [[ "$#" -gt 0 ]]
    then
        printf '%s\n' 'Usage: tc5' >&2
        return 2
    fi

    now="$(date -u '+%Y %m %d %H')" || return
    read -r year month day hour <<< "$now"

    # The hours since the start of the month. The fields have leading zeros,
    # so they are read in base 10.
    local hours_in_month=$(( ((10#$day - 1) * 24) + 10#$hour ))
    local half_month=$(( 2 * (10#$month - 1) ))

    # The second half month begins after 372 hours, which is 15.5 days.
    if [[ "$hours_in_month" -ge 372 ]]
    then
        half_month=$(( half_month + 1 ))
    fi

    printf '%s\n' "$year${p5:half_month:1}"
}

# The current time as a TC8, such as 2026VDJB.
function tc8 {
    local p5='ABCDEFGHIJKLOPQRSTUVWXYZ'
    local p6='BDFGHJKLMPQRSTVWXYZ'
    local p7='BCDFGHJKLMNPQRVWXYZ'
    local p8='BCDFGHJLMNPQRSVWXYZ'
    # The start of the second half month in seconds. 372 hours is 15.5 days.
    local hm2_begin=$(( 372 * 3600 ))
    local now year month day hour minute second

    if [[ "$#" -gt 0 ]]
    then
        printf '%s\n' 'Usage: tc8' >&2
        return 2
    fi

    now="$(date -u '+%Y %m %d %H %M %S')" || return
    read -r year month day hour minute second <<< "$now"

    # The fields have leading zeros, so they are read in base 10.
    local loc_in_month=$(( ((10#$day - 1) * 24 * 3600) +
        (10#$hour * 3600) + (10#$minute * 60) + 10#$second ))
    local half_month=$(( 2 * (10#$month - 1) ))
    local loc_in_half_month="$loc_in_month"

    if [[ "$loc_in_month" -ge "$hm2_begin" ]]
    then
        half_month=$(( half_month + 1 ))
        loc_in_half_month=$(( loc_in_month - hm2_begin ))
    fi

    local block=$(( loc_in_half_month / 200 ))

    printf '%s%s%s%s%s\n' "$year" "${p5:half_month:1}" \
        "${p6:block / 361:1}" "${p7:block / 19 % 19:1}" "${p8:block % 19:1}"
}

# The current time as a TCX, such as 2026VDJBD5, or with --extend, as a
# TC14, to the millisecond, such as 2026VDJBD5.250. The fraction of the
# second needs a date that knows %N.
function tcx {
    local p5='ABCDEFGHIJKLOPQRSTUVWXYZ'
    local p6='BDFGHJKLMPQRSTVWXYZ'
    local p7='BCDFGHJKLMNPQRVWXYZ'
    local p8='BCDFGHJLMNPQRSVWXYZ'
    local p9='BCDEFHIJLMNOPQUVWXYZ'
    # The start of the second half month in seconds. 372 hours is 15.5 days.
    local hm2_begin=$(( 372 * 3600 ))
    local extend=false
    local now year month day hour minute second nanoseconds

    if [[ "$#" -eq 1 && "$1" == --extend ]]
    then
        extend=true
    elif [[ "$#" -gt 0 ]]
    then
        printf '%s\n' 'Usage: tcx [--extend]' >&2
        return 2
    fi

    now="$(date -u '+%Y %m %d %H %M %S %N')" || return
    read -r year month day hour minute second nanoseconds <<< "$now"

    if [[ "$extend" == true && ! "$nanoseconds" =~ ^[0-9]{9}$ ]]
    then
        printf '%s\n' 'tcx: date gives no fraction of the second.' >&2
        return 1
    fi

    # The fields have leading zeros, so they are read in base 10.
    local loc_in_month=$(( ((10#$day - 1) * 24 * 3600) +
        (10#$hour * 3600) + (10#$minute * 60) + 10#$second ))
    local half_month=$(( 2 * (10#$month - 1) ))
    local loc_in_half_month="$loc_in_month"

    if [[ "$loc_in_month" -ge "$hm2_begin" ]]
    then
        half_month=$(( half_month + 1 ))
        loc_in_half_month=$(( loc_in_month - hm2_begin ))
    fi

    local block=$(( loc_in_half_month / 200 ))
    local second_in_block=$(( loc_in_half_month % 200 ))
    local code="$year${p5:half_month:1}"
    code+="${p6:block / 361:1}${p7:block / 19 % 19:1}${p8:block % 19:1}"
    code+="${p9:second_in_block / 10:1}$(( second_in_block % 10 ))"

    if [[ "$extend" == true ]]
    then
        printf '%s.%s\n' "$code" "${nanoseconds:0:3}"
    else
        printf '%s\n' "$code"
    fi
}

# The current time as a BC4, such as C6VD.
function bc4 {
    local b1='ABCDEFGHIJKLMNOPQRST'
    local p5='ABCDEFGHIJKLOPQRSTUVWXYZ'
    local p6='BDFGHJKLMPQRSTVWXYZ'
    # The start of the second half month in seconds. 372 hours is 15.5 days.
    local hm2_begin=$(( 372 * 3600 ))
    local now year month day hour minute second

    if [[ "$#" -gt 0 ]]
    then
        printf '%s\n' 'Usage: bc4' >&2
        return 2
    fi

    now="$(date -u '+%Y %m %d %H %M %S')" || return
    read -r year month day hour minute second <<< "$now"

    # The fields have leading zeros, so they are read in base 10.
    local loc_in_month=$(( ((10#$day - 1) * 24 * 3600) +
        (10#$hour * 3600) + (10#$minute * 60) + 10#$second ))
    local half_month=$(( 2 * (10#$month - 1) ))
    local loc_in_half_month="$loc_in_month"

    if [[ "$loc_in_month" -ge "$hm2_begin" ]]
    then
        half_month=$(( half_month + 1 ))
        loc_in_half_month=$(( loc_in_month - hm2_begin ))
    fi

    # The block of 200 seconds, of which P6 shows the first place, and the
    # place of the year in the cycle of 200 years, from 0 to 199.
    local block=$(( loc_in_half_month / 200 ))
    local years=$(( 10#$year % 200 ))

    printf '%s%s%s%s\n' "${b1:years / 10:1}" "$(( years % 10 ))" \
        "${p5:half_month:1}" "${p6:block / 361:1}"
}

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
