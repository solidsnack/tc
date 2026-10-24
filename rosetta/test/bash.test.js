// Runs the cases in specification/tests.yaml through the Bash port. The
// port prints only the current time, so it is run with a date command of
// the tests' own, which gives the time of each case.
const assert = require('node:assert/strict')
const { spawnSync } = require('node:child_process')
const fs = require('node:fs')
const os = require('node:os')
const path = require('node:path')
const { after, describe, test } = require('node:test')
const timecode = require('../es5/timecode.js')
const { suite, groups } = require('./cases.js')

const script = path.join(__dirname, '..', 'bash', 'timecode.bash')
const source = fs.readFileSync(script, 'utf8')

// A directory with a date command that prints the fields in NOW, when it is
// asked for them as the port asks: in UTC, from the year to the nanosecond.
const bin = fs.mkdtempSync(path.join(os.tmpdir(), 'timecode-bash-'))

fs.writeFileSync(
    path.join(bin, 'date'),
    `#!/bin/sh
[ "$*" = '-u +%Y %m %d %H %M %S %N' ] || exit 64
printf '%s\\n' "$NOW"
`,
    { mode: 0o755 },
)
after(() => fs.rmSync(bin, { recursive: true }))

const timestamp = new RegExp(
    '^([0-9]{4})-([0-9]{2})-([0-9]{2})' +
        'T([0-9]{2}):([0-9]{2}):([0-9]{2})(?:[.]([0-9]{1,9}))?Z$',
)

// The fields of a timestamp, as date prints them for the port.
function fieldsOf(t) {
    const [, ...fields] = timestamp.exec(t)
    const fraction = fields.pop() ?? ''

    return [...fields, fraction.padEnd(9, '0')].join(' ')
}

// Runs a command with the clock at a time, given as the fields date prints.
function run(now, command, ...args) {
    return spawnSync(command, args, {
        encoding: 'utf8',
        env: { ...process.env, NOW: now, PATH: `${bin}:${process.env.PATH}` },
    })
}

// The code the script prints for a time, with a command and its flag.
function print(now, ...args) {
    const { status, stdout, stderr } = run(now, script, ...args)

    assert.equal(status, 0, stderr)

    return stdout.trimEnd()
}

// Every code of a time is the start of the longest one, so two codes of the
// same time agree as far as the shorter goes. There are no tests of TC11 or
// BC9, which are formatted like TC12 and BC10.
function assertAgrees(code, expected) {
    const n = Math.min(code.length, expected.length)

    assert.equal(code.slice(0, n), expected.slice(0, n))
}

// The length of the code each command prints, and with --extend, to the
// millisecond.
const lengths = {
    tc5: [5, 14],
    tc8: [8, 14],
    tcx: [10, 14],
}

// The date of a system that keeps leap seconds prints 23:59:60 for one,
// which the port counts as the next second, as the specification does.
for (const group of [...groups, 'leap-seconds']) {
    describe(`tests.yaml: ${group}`, () => {
        for (const { input, output } of suite[group].encode) {
            test(`encode ${input}`, () => {
                const now = fieldsOf(input)

                for (const [command, [n, m]] of Object.entries(lengths)) {
                    const code = print(now, command)
                    const extended = print(now, command, '--extend')

                    assert.equal(code.length, n)
                    assert.equal(extended.length, m)
                    assertAgrees(code, output)
                    assertAgrees(extended, output)
                }
            })
        }
    })
}

describe('tests.yaml: bc', () => {
    for (const { input, output } of suite.bc.encode) {
        test(`encode ${input}`, () => {
            const now = fieldsOf(input)
            const code = print(now, 'bc4')
            const extended = print(now, 'bc4', '--extend')

            assert.equal(code.length, 4)
            assert.equal(extended.length, 12)
            assertAgrees(code, output)
            assertAgrees(extended, output)
        })
    }
})

const t = fieldsOf('2026-10-17T14:23:45.25Z')

test('without a command, the script prints a TC8', () => {
    assert.equal(print(t), '2026VDJB')
    assert.equal(print(t, '--extend'), '2026VDJBD5.250')
})

test('the commands take no arguments but --extend', () => {
    for (const args of [['--extended'], ['--extend', '--extend'], ['8']]) {
        const { status, stdout, stderr } = run(t, script, 'tc8', ...args)

        assert.equal(status, 2)
        assert.equal(stdout, '')
        assert.equal(stderr, 'Usage: tc8 [--extend]\n')
    }
})

test('--extend needs a date that gives the fraction of the second', () => {
    // A date that does not know %N prints N, or %N.
    for (const now of ['2026 10 17 14 23 45 N', '2026 10 17 14 23 45 %N']) {
        const { status, stdout, stderr } = run(now, script, 'bc4', '--extend')

        assert.equal(print(now, 'bc4'), 'C6VD')
        assert.equal(status, 1)
        assert.equal(stdout, '')
        assert.equal(stderr, 'bc4: date gives no fraction of the second.\n')
    }
})

// A function as it is written in the script, and every word in it that
// could name a variable it sets.
function definitionOf(name) {
    const start = source.indexOf(`function ${name} {\n`)
    const end = source.indexOf('\n}\n', start) + 3
    const definition = source.slice(start, end)
    const words = [...new Set(definition.match(/[a-z_][a-z0-9_]*/g))]

    return { definition, words }
}

// Copied into a script by itself, a function works whether or not the
// script sets the options this one does, and leaves no variables behind:
// declare would print them.
test('each function stands alone', () => {
    const expected = {
        tc5: ['2026V', '2026VDJBD5.250'],
        tc8: ['2026VDJB', '2026VDJBD5.250'],
        tcx: ['2026VDJBD5', '2026VDJBD5.250'],
        bc4: ['C6VD', 'C6VDJBD5.250'],
    }

    for (const [name, [code, extended]] of Object.entries(expected)) {
        const { definition, words } = definitionOf(name)

        assert.ok(words.includes('code'), name)

        for (const options of ['', 'set -o errexit -o nounset -o pipefail']) {
            const shell = `${options}
${definition}
${name}
${name} --extend
declare -p ${words.join(' ')} 2>/dev/null || true
`
            const { status, stdout, stderr } = run(t, 'bash', '-c', shell)

            assert.equal(stderr, '', name)
            assert.equal(stdout, `${code}\n${extended}\n`, name)
            assert.equal(status, 0, name)
        }
    }
})

// With the real clock, in a time zone other than UTC, a code sorts between
// the codes of the times just before and after it.
test('the current time is encoded in UTC', () => {
    const earlier = timecode.encode(new Date(), 14)
    const { status, stdout, stderr } = spawnSync(script, ['tcx', '--extend'], {
        encoding: 'utf8',
        env: { ...process.env, TZ: 'Asia/Kolkata' },
    })
    const later = timecode.encode(new Date(), 14)
    const code = stdout.trimEnd()

    assert.equal(status, 0, stderr)
    assert.ok(earlier <= code && code <= later, `${earlier} ${code} ${later}`)
})
