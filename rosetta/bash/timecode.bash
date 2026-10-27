#!/bin/bash
set -o errexit -o nounset -o pipefail

# TimeCodes in Bash, as described in specification/index.md.

# The revision of specification/index.md that this port follows.
SPEC_VERSION='1.1.0'

function main {
    # Without a command, print a TC8, as the other command line
    # tools do.
    tc8 "$@"
}

# Each function below prints the time it is given, as a
# timestamp in UTC such as 2026-10-17T14:23:45Z, or else the
# current time, which it reads with one call to date. Second 60
# is for a leap second. With --git, each prints instead the
# author date of a commit, HEAD by default, which it reads with
# one call to git. Each stands alone, using nothing but its own
# definitions, so that it can be copied into another script or
# program as it is; for that, no line is longer than 64 columns.

# A time as a TC5, such as 2026V.
function tc5 {
    local p5='ABCDEFGHIJKLOPQRSTUVWXYZ'
    # The start of the second half month: 15.5 days, in seconds.
    local hm2_begin=$(( 372 * 3600 ))
    local timestamp='^([0-9]{4})-(0[1-9]|1[0-2])'
    timestamp+='-(0[1-9]|[12][0-9]|3[01])'
    timestamp+='T([01][0-9]|2[0-3]):([0-5][0-9])'
    timestamp+=':([0-5][0-9]|60)Z$'
    local now='' year month day hour minute second

    case "$#:${1:-}" in
        0:)
            now="$(date -u '+%Y-%m-%dT%H:%M:%SZ')" || return
            ;;
        [12]:--git)
            # The author date of a commit, HEAD by default.
            now="$(TZ=UTC git log -1 --format=%ad \
                --date='format-local:%Y-%m-%dT%H:%M:%SZ' \
                --end-of-options "${2:-HEAD}" --)" || return
            ;;
        1:*)
            now="$1"
            ;;
    esac

    # Any other arguments leave now empty, and are refused.
    if [[ ! "$now" =~ $timestamp ]]
    then
        printf '%s %s\n' >&2 'Usage: tc5' \
            '[yyyy-mm-ddTHH:MM:SSZ | --git [commit]]'
        return 2
    fi

    read -r year month day hour minute second \
        <<< "${BASH_REMATCH[*]:1}"

    # The fields, with leading zeros, are read in base 10.
    local loc_in_month=$(( ((10#$day - 1) * 24 * 3600) +
        (10#$hour * 3600) + (10#$minute * 60) + 10#$second ))
    local half_month=$(( 2 * (10#$month - 1) ))

    if [[ "$loc_in_month" -ge "$hm2_begin" ]]
    then
        half_month=$(( half_month + 1 ))
    fi

    printf '%s\n' "$year${p5:half_month:1}"
}

# A time as a TC8, such as 2026VDJB.
function tc8 {
    local p5='ABCDEFGHIJKLOPQRSTUVWXYZ'
    local p6='BDFGHJKLMPQRSTVWXYZ'
    local p7='BCDFGHJKLMNPQRVWXYZ'
    local p8='BCDFGHJLMNPQRSVWXYZ'
    # The start of the second half month: 15.5 days, in seconds.
    local hm2_begin=$(( 372 * 3600 ))
    local timestamp='^([0-9]{4})-(0[1-9]|1[0-2])'
    timestamp+='-(0[1-9]|[12][0-9]|3[01])'
    timestamp+='T([01][0-9]|2[0-3]):([0-5][0-9])'
    timestamp+=':([0-5][0-9]|60)Z$'
    local now='' year month day hour minute second

    case "$#:${1:-}" in
        0:)
            now="$(date -u '+%Y-%m-%dT%H:%M:%SZ')" || return
            ;;
        [12]:--git)
            # The author date of a commit, HEAD by default.
            now="$(TZ=UTC git log -1 --format=%ad \
                --date='format-local:%Y-%m-%dT%H:%M:%SZ' \
                --end-of-options "${2:-HEAD}" --)" || return
            ;;
        1:*)
            now="$1"
            ;;
    esac

    # Any other arguments leave now empty, and are refused.
    if [[ ! "$now" =~ $timestamp ]]
    then
        printf '%s %s\n' >&2 'Usage: tc8' \
            '[yyyy-mm-ddTHH:MM:SSZ | --git [commit]]'
        return 2
    fi

    read -r year month day hour minute second \
        <<< "${BASH_REMATCH[*]:1}"

    # The fields, with leading zeros, are read in base 10.
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
    local code="$year${p5:half_month:1}${p6:block / 361:1}"
    code+="${p7:block / 19 % 19:1}${p8:block % 19:1}"

    printf '%s\n' "$code"
}

# A time as a TCX, such as 2026VDJBD5, or with --extend, as a
# TC14, to the millisecond, such as 2026VDJBD5.250. A timestamp,
# like the author date of a commit, has whole seconds, so only
# the current time has a fraction of the second, for which date
# must know %N.
function tcx {
    local p5='ABCDEFGHIJKLOPQRSTUVWXYZ'
    local p6='BDFGHJKLMPQRSTVWXYZ'
    local p7='BCDFGHJKLMNPQRVWXYZ'
    local p8='BCDFGHJLMNPQRSVWXYZ'
    local p9='BCDEFHIJLMNOPQUVWXYZ'
    # The start of the second half month: 15.5 days, in seconds.
    local hm2_begin=$(( 372 * 3600 ))
    local timestamp='^([0-9]{4})-(0[1-9]|1[0-2])'
    timestamp+='-(0[1-9]|[12][0-9]|3[01])'
    timestamp+='T([01][0-9]|2[0-3]):([0-5][0-9])'
    timestamp+=':([0-5][0-9]|60)Z$'
    local extend=false
    local nanoseconds='000000000'
    local now='' year month day hour minute second

    if [[ "${1:-}" == --extend ]]
    then
        extend=true
        shift
    fi

    case "$#:${1:-}" in
        0:)
            now="$(date -u '+%Y-%m-%dT%H:%M:%SZ %N')" || return
            nanoseconds="${now#* }"
            now="${now%% *}"
            ;;
        [12]:--git)
            # The author date of a commit, HEAD by default.
            now="$(TZ=UTC git log -1 --format=%ad \
                --date='format-local:%Y-%m-%dT%H:%M:%SZ' \
                --end-of-options "${2:-HEAD}" --)" || return
            ;;
        1:*)
            now="$1"
            ;;
    esac

    # Any other arguments leave now empty, and are refused.
    if [[ ! "$now" =~ $timestamp ]]
    then
        printf '%s %s\n' >&2 'Usage: tcx [--extend]' \
            '[yyyy-mm-ddTHH:MM:SSZ | --git [commit]]'
        return 2
    fi

    read -r year month day hour minute second \
        <<< "${BASH_REMATCH[*]:1}"

    if [[ "$extend" == true && ! "$nanoseconds" =~ ^[0-9]{9}$ ]]
    then
        printf '%s\n' >&2 \
            'tcx: date gives no fraction of the second.'
        return 1
    fi

    # The fields, with leading zeros, are read in base 10.
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
    local code="$year${p5:half_month:1}${p6:block / 361:1}"
    code+="${p7:block / 19 % 19:1}${p8:block % 19:1}"
    code+="${p9:second_in_block / 10:1}"
    code+="$(( second_in_block % 10 ))"

    if [[ "$extend" == true ]]
    then
        printf '%s.%s\n' "$code" "${nanoseconds:0:3}"
    else
        printf '%s\n' "$code"
    fi
}

# A time as a BC4, such as C6VD.
function bc4 {
    local b1='ABCDEFGHIJKLMNOPQRST'
    local p5='ABCDEFGHIJKLOPQRSTUVWXYZ'
    local p6='BDFGHJKLMPQRSTVWXYZ'
    # The start of the second half month: 15.5 days, in seconds.
    local hm2_begin=$(( 372 * 3600 ))
    local timestamp='^([0-9]{4})-(0[1-9]|1[0-2])'
    timestamp+='-(0[1-9]|[12][0-9]|3[01])'
    timestamp+='T([01][0-9]|2[0-3]):([0-5][0-9])'
    timestamp+=':([0-5][0-9]|60)Z$'
    local now='' year month day hour minute second

    case "$#:${1:-}" in
        0:)
            now="$(date -u '+%Y-%m-%dT%H:%M:%SZ')" || return
            ;;
        [12]:--git)
            # The author date of a commit, HEAD by default.
            now="$(TZ=UTC git log -1 --format=%ad \
                --date='format-local:%Y-%m-%dT%H:%M:%SZ' \
                --end-of-options "${2:-HEAD}" --)" || return
            ;;
        1:*)
            now="$1"
            ;;
    esac

    # Any other arguments leave now empty, and are refused.
    if [[ ! "$now" =~ $timestamp ]]
    then
        printf '%s %s\n' >&2 'Usage: bc4' \
            '[yyyy-mm-ddTHH:MM:SSZ | --git [commit]]'
        return 2
    fi

    read -r year month day hour minute second \
        <<< "${BASH_REMATCH[*]:1}"

    # The fields, with leading zeros, are read in base 10.
    local loc_in_month=$(( ((10#$day - 1) * 24 * 3600) +
        (10#$hour * 3600) + (10#$minute * 60) + 10#$second ))
    local half_month=$(( 2 * (10#$month - 1) ))
    local loc_in_half_month="$loc_in_month"

    if [[ "$loc_in_month" -ge "$hm2_begin" ]]
    then
        half_month=$(( half_month + 1 ))
        loc_in_half_month=$(( loc_in_month - hm2_begin ))
    fi

    # The block of 200 seconds, of which P6 shows the first
    # place, and the place of the year in the cycle of 200
    # years, from 0 to 199.
    local block=$(( loc_in_half_month / 200 ))
    local years=$(( 10#$year % 200 ))
    local code="${b1:years / 10:1}$(( years % 10 ))"
    code+="${p5:half_month:1}${p6:block / 361:1}"

    printf '%s\n' "$code"
}

# A time as a BC5, such as C6VDJ.
function bc5 {
    local b1='ABCDEFGHIJKLMNOPQRST'
    local p5='ABCDEFGHIJKLOPQRSTUVWXYZ'
    local p6='BDFGHJKLMPQRSTVWXYZ'
    local p7='BCDFGHJKLMNPQRVWXYZ'
    # The start of the second half month: 15.5 days, in seconds.
    local hm2_begin=$(( 372 * 3600 ))
    local timestamp='^([0-9]{4})-(0[1-9]|1[0-2])'
    timestamp+='-(0[1-9]|[12][0-9]|3[01])'
    timestamp+='T([01][0-9]|2[0-3]):([0-5][0-9])'
    timestamp+=':([0-5][0-9]|60)Z$'
    local now='' year month day hour minute second

    case "$#:${1:-}" in
        0:)
            now="$(date -u '+%Y-%m-%dT%H:%M:%SZ')" || return
            ;;
        [12]:--git)
            # The author date of a commit, HEAD by default.
            now="$(TZ=UTC git log -1 --format=%ad \
                --date='format-local:%Y-%m-%dT%H:%M:%SZ' \
                --end-of-options "${2:-HEAD}" --)" || return
            ;;
        1:*)
            now="$1"
            ;;
    esac

    # Any other arguments leave now empty, and are refused.
    if [[ ! "$now" =~ $timestamp ]]
    then
        printf '%s %s\n' >&2 'Usage: bc5' \
            '[yyyy-mm-ddTHH:MM:SSZ | --git [commit]]'
        return 2
    fi

    read -r year month day hour minute second \
        <<< "${BASH_REMATCH[*]:1}"

    # The fields, with leading zeros, are read in base 10.
    local loc_in_month=$(( ((10#$day - 1) * 24 * 3600) +
        (10#$hour * 3600) + (10#$minute * 60) + 10#$second ))
    local half_month=$(( 2 * (10#$month - 1) ))
    local loc_in_half_month="$loc_in_month"

    if [[ "$loc_in_month" -ge "$hm2_begin" ]]
    then
        half_month=$(( half_month + 1 ))
        loc_in_half_month=$(( loc_in_month - hm2_begin ))
    fi

    # The block of 200 seconds, of which P6 and P7 show the
    # first places, and the place of the year in the cycle of
    # 200 years, from 0 to 199.
    local block=$(( loc_in_half_month / 200 ))
    local years=$(( 10#$year % 200 ))
    local code="${b1:years / 10:1}$(( years % 10 ))"
    code+="${p5:half_month:1}${p6:block / 361:1}"
    code+="${p7:block / 19 % 19:1}"

    printf '%s\n' "$code"
}

# A time as a BC8, such as C6VDJBD5.
function bc8 {
    local b1='ABCDEFGHIJKLMNOPQRST'
    local p5='ABCDEFGHIJKLOPQRSTUVWXYZ'
    local p6='BDFGHJKLMPQRSTVWXYZ'
    local p7='BCDFGHJKLMNPQRVWXYZ'
    local p8='BCDFGHJLMNPQRSVWXYZ'
    local p9='BCDEFHIJLMNOPQUVWXYZ'
    # The start of the second half month: 15.5 days, in seconds.
    local hm2_begin=$(( 372 * 3600 ))
    local timestamp='^([0-9]{4})-(0[1-9]|1[0-2])'
    timestamp+='-(0[1-9]|[12][0-9]|3[01])'
    timestamp+='T([01][0-9]|2[0-3]):([0-5][0-9])'
    timestamp+=':([0-5][0-9]|60)Z$'
    local now='' year month day hour minute second

    case "$#:${1:-}" in
        0:)
            now="$(date -u '+%Y-%m-%dT%H:%M:%SZ')" || return
            ;;
        [12]:--git)
            # The author date of a commit, HEAD by default.
            now="$(TZ=UTC git log -1 --format=%ad \
                --date='format-local:%Y-%m-%dT%H:%M:%SZ' \
                --end-of-options "${2:-HEAD}" --)" || return
            ;;
        1:*)
            now="$1"
            ;;
    esac

    # Any other arguments leave now empty, and are refused.
    if [[ ! "$now" =~ $timestamp ]]
    then
        printf '%s %s\n' >&2 'Usage: bc8' \
            '[yyyy-mm-ddTHH:MM:SSZ | --git [commit]]'
        return 2
    fi

    read -r year month day hour minute second \
        <<< "${BASH_REMATCH[*]:1}"

    # The fields, with leading zeros, are read in base 10.
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
    # The year's place in the cycle of 200 years, from 0 to 199.
    local years=$(( 10#$year % 200 ))
    local code="${b1:years / 10:1}$(( years % 10 ))"
    code+="${p5:half_month:1}${p6:block / 361:1}"
    code+="${p7:block / 19 % 19:1}${p8:block % 19:1}"
    code+="${p9:second_in_block / 10:1}"
    code+="$(( second_in_block % 10 ))"

    printf '%s\n' "$code"
}


########## Delegates to subcommands or runs main, as appropriate

if declare -f -- "${1:-}" >/dev/null
then
    "$@"
else
    main "$@"
fi
