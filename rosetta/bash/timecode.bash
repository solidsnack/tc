#!/bin/bash
set -o errexit -o nounset -o pipefail

# TimeCodes in Bash, as described in specification/index.md.

# The revision of specification/index.md that this port follows.
SPEC_VERSION='1.1.0'

function main {
    # Without a command, print a TC8, as the other command line tools do.
    tc8 "$@"
}

# Each function below prints the current time, and with --extend, prints it
# to the millisecond, as a TC14 or a BC12. Each stands alone, using nothing
# but its own definitions, so that it can be copied into another script as
# it is. The time comes from one call to date, which must know %N for the
# fraction of the second.

# The current time as a TC5, such as 2026V, or with --extend, as a TC14,
# such as 2026VDJBD5.250.
function tc5 {
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
        printf '%s\n' 'Usage: tc5 [--extend]' >&2
        return 2
    fi

    # One reading of the clock, in UTC, from the year to the nanosecond.
    now="$(date -u '+%Y %m %d %H %M %S %N')" || return
    read -r year month day hour minute second nanoseconds <<< "$now"

    if [[ "$extend" == true && ! "$nanoseconds" =~ ^[0-9]{9}$ ]]
    then
        printf '%s\n' 'tc5: date gives no fraction of the second.' >&2
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
        printf '%s\n' "${code:0:5}"
    fi
}

# The current time as a TC8, such as 2026VDJB, or with --extend, as a TC14,
# such as 2026VDJBD5.250.
function tc8 {
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
        printf '%s\n' 'Usage: tc8 [--extend]' >&2
        return 2
    fi

    # One reading of the clock, in UTC, from the year to the nanosecond.
    now="$(date -u '+%Y %m %d %H %M %S %N')" || return
    read -r year month day hour minute second nanoseconds <<< "$now"

    if [[ "$extend" == true && ! "$nanoseconds" =~ ^[0-9]{9}$ ]]
    then
        printf '%s\n' 'tc8: date gives no fraction of the second.' >&2
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
        printf '%s\n' "${code:0:8}"
    fi
}

# The current time as a TCX, such as 2026VDJBD5, or with --extend, as a
# TC14, such as 2026VDJBD5.250.
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

    # One reading of the clock, in UTC, from the year to the nanosecond.
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

# The current time as a BC4, such as C6VD, or with --extend, as a BC12, such
# as C6VDJBD5.250.
function bc4 {
    local b1='ABCDEFGHIJKLMNOPQRST'
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
        printf '%s\n' 'Usage: bc4 [--extend]' >&2
        return 2
    fi

    # One reading of the clock, in UTC, from the year to the nanosecond.
    now="$(date -u '+%Y %m %d %H %M %S %N')" || return
    read -r year month day hour minute second nanoseconds <<< "$now"

    if [[ "$extend" == true && ! "$nanoseconds" =~ ^[0-9]{9}$ ]]
    then
        printf '%s\n' 'bc4: date gives no fraction of the second.' >&2
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
    # The place of the year in the cycle of 200 years, from 0 to 199.
    local years=$(( 10#$year % 200 ))
    local code="${b1:years / 10:1}$(( years % 10 ))${p5:half_month:1}"
    code+="${p6:block / 361:1}${p7:block / 19 % 19:1}${p8:block % 19:1}"
    code+="${p9:second_in_block / 10:1}$(( second_in_block % 10 ))"

    if [[ "$extend" == true ]]
    then
        printf '%s.%s\n' "$code" "${nanoseconds:0:3}"
    else
        printf '%s\n' "${code:0:4}"
    fi
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
