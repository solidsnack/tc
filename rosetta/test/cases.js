// The cases in specification/tests.yaml, and helpers for the ports that
// keep times in microseconds.
const fs = require('node:fs')
const path = require('node:path')
const { parse } = require('yaml')

const file = path.join(__dirname, '..', '..', 'specification', 'tests.yaml')
const suite = parse(fs.readFileSync(file, 'utf8'))
const groups = ['tc5', 'tc8', 'tcx', 'tcn']

// A timestamp as whole seconds since 1970 and microseconds, with the
// fraction of the second cut off after six digits. Neither database takes
// the year 0000 as text, so the ports are given times this way.
function epoch(timestamp) {
    const [, whole, fraction = ''] = /^(.*:[0-9]{2})(?:[.]([0-9]+))?Z$/.exec(
        timestamp,
    )
    const milliseconds = Date.parse(`${whole}Z`)

    return {
        seconds: Math.floor(milliseconds / 1000),
        microseconds: Number(fraction.padEnd(6, '0').slice(0, 6)),
    }
}

// The time as a count of microseconds since 1970, as a string.
function microsecondsOf(timestamp) {
    const { seconds, microseconds } = epoch(timestamp)

    return `${(BigInt(seconds) * 1000000n) + BigInt(microseconds)}`
}

// The code of a time that holds only microseconds: past TC17, its digits
// are 0.
function atMicroseconds(code) {
    return code.length <= 17
        ? code
        : code.slice(0, 17).padEnd(code.length, '0')
}

// SQL text for a string.
function quote(s) {
    return `'${s.replaceAll("'", "''")}'`
}

module.exports = {
    suite,
    groups,
    epoch,
    microsecondsOf,
    atMicroseconds,
    quote,
}
