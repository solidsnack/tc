// The direct function that gives a code of this group's length, if there
// is one, and the digits of the fraction to ask tcx for.
function directFor(group, length) {
    if (['tc5', 'tc8', 'tcx'].includes(group)) {
        return { direct: group, fractionDigits: 0 }
    }

    return length >= 12
        ? { direct: 'tcx', fractionDigits: length - 11 }
        : { direct: '', fractionDigits: 0 }
}

// Runs the cases in specification/tests.yaml through a SQL port. Each port
// is given its cases in two queries, one to decode and one to encode, which
// print an id and a result on each line.
const assert = require('node:assert/strict')
const { describe, test } = require('node:test')
const {
    suite,
    groups,
    epoch,
    microsecondsOf,
    atMicroseconds,
} = require('./cases.js')

// name: the port's name, for the tests.
// run(decodes, encodes): runs the queries and gives back their output, or
//   undefined when the database is not there to run them.
// decodes: [{ id, code }]
// encodes: [{ id, seconds, microseconds, digits, direct, fractionDigits }],
//   where direct is tc5, tc8, tcx or empty, for the direct function that
//   gives a code of the same length, and fractionDigits is for tcx.
// leapSeconds: why the port cannot encode 23:59:60.
function runSuite({ name, run, leapSeconds }) {
    const decodes = []
    const encodes = []
    const checks = []

    for (const group of [...groups, 'leap-seconds']) {
        const isLeap = group === 'leap-seconds'

        for (const { input, output } of suite[group].decode) {
            const id = checks.length
            // A leap second code reads as the last second of its half month.
            const expected = microsecondsOf(output.replace(':60', ':59'))

            decodes.push({ id, code: input })
            checks.push({ group, name: `decode ${input}`, id, expected })
        }

        for (const { input, output } of suite[group].encode) {
            if (isLeap) {
                checks.push({ group, name: `encode ${input}`, skip: true })
                continue
            }

            const id = checks.length
            const { seconds, microseconds } = epoch(input)
            const { direct, fractionDigits } = directFor(group, output.length)

            encodes.push({
                id,
                seconds,
                microseconds,
                digits: output.length,
                direct,
                fractionDigits,
            })
            checks.push({
                group,
                name: `encode ${input}`,
                id,
                expected: atMicroseconds(output),
                direct,
            })
        }
    }

    const output = run(decodes, encodes)
    const results = new Map()

    for (const line of (output ?? '').split('\n').filter(Boolean)) {
        const [id, result, direct] = line.split(',')
        results.set(Number(id), { result, direct })
    }

    describe(name, { skip: output === undefined && 'no database' }, () => {
        for (const group of [...groups, 'leap-seconds']) {
            describe(`tests.yaml: ${group}`, () => {
                for (const check of checks.filter((c) => c.group === group)) {
                    if (check.skip) {
                        test(check.name, { skip: leapSeconds })
                        continue
                    }

                    test(check.name, () => {
                        const { result, direct } = results.get(check.id) ?? {}

                        assert.equal(result, check.expected)

                        if (check.direct) {
                            assert.equal(direct, check.expected)
                        }
                    })
                }
            })
        }
    })
}

module.exports = { runSuite }
