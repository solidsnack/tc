// Runs the cases in specification/tests.yaml through the Bash port. The
// port takes timestamps with whole seconds, and prints the current time to
// the millisecond, so for that it is run with a date command of the tests'
// own, which gives the time of each case. With --git, it is run in a
// repository of the tests' own.
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

// A directory with a date command that prints the time in NOW, which has
// the fields from the year to the nanosecond, in the format it is asked
// for. It must be asked for the time in UTC.
const bin = fs.mkdtempSync(path.join(os.tmpdir(), 'timecode-bash-'))

fs.writeFileSync(
    path.join(bin, 'date'),
    `#!/bin/sh
[ "$#" -eq 2 ] && [ "$1" = -u ] || exit 64
set -- "$2" $NOW
printf '%s\\n' "\${1#+}" | sed -e "s/%Y/$2/g" -e "s/%m/$3/g" -e "s/%d/$4/g" \\
    -e "s/%H/$5/g" -e "s/%M/$6/g" -e "s/%S/$7/g" -e "s/%N/$8/g"
`,
    { mode: 0o755 },
)
after(() => fs.rmSync(bin, { recursive: true }))

// A repository for --git, and git as the tests run it there: without the
// configuration of whoever runs them, so that it is the same everywhere,
// and in a time zone other than UTC, so that the port must ask for UTC.
const repository = fs.mkdtempSync(path.join(os.tmpdir(), 'timecode-git-'))
const gitEnv = {
    ...process.env,
    GIT_AUTHOR_EMAIL: 'tester@localhost',
    GIT_AUTHOR_NAME: 'tester',
    // Outside the repository, git finds none, wherever the tests are.
    GIT_CEILING_DIRECTORIES: os.tmpdir(),
    GIT_COMMITTER_DATE: '2026-10-26T12:00:00Z',
    GIT_COMMITTER_EMAIL: 'tester@localhost',
    GIT_COMMITTER_NAME: 'tester',
    GIT_CONFIG_GLOBAL: '/dev/null',
    GIT_CONFIG_NOSYSTEM: '1',
    // India's time zone, as POSIX writes it, which needs no time zone
    // database.
    TZ: 'IST-5:30',
}

after(() => fs.rmSync(repository, { recursive: true }))

// Runs a command in a directory, as git is run in the repository.
function runIn(cwd, command, ...args) {
    return spawnSync(command, args, { cwd, encoding: 'utf8', env: gitEnv })
}

function git(...args) {
    const { status, stderr } = runIn(repository, 'git', ...args)

    assert.equal(status, 0, stderr)
}

// The repository's commits, with the author dates they were written at, in
// time zones other than UTC, and those dates as timestamps in UTC. They are
// committed at another time, which the port does not read. The first is
// tagged, and on a branch of its own; the second is HEAD, on main.
const first = {
    date: '2026-10-17T23:23:45+09:00',
    timestamp: '2026-10-17T14:23:45Z',
}
const second = {
    date: '2026-10-31T23:59:59-07:00',
    timestamp: '2026-11-01T06:59:59Z',
}

git('init', '--quiet', '--initial-branch=main')
git('commit', '--quiet', '--allow-empty', `--date=${first.date}`, '-m', '1')
git('tag', '--annotate', '--message=The first commit', 'v1.0')
git('branch', 'older')
git('commit', '--quiet', '--allow-empty', `--date=${second.date}`, '-m', '2')

const timestamp = new RegExp(
    '^([0-9]{4})-([0-9]{2})-([0-9]{2})' +
        'T([0-9]{2}):([0-9]{2}):([0-9]{2})(?:[.]([0-9]{1,9}))?Z$',
)

// The fields of a timestamp, from the year to the nanosecond.
function fieldsOf(t) {
    const [, ...fields] = timestamp.exec(t)
    const fraction = fields.pop() ?? ''

    return [...fields, fraction.padEnd(9, '0')].join(' ')
}

// A timestamp without its fraction of the second, as the port takes it.
function wholeSecondOf(t) {
    return t.replace(/[.][0-9]+Z$/, 'Z')
}

// Runs a command with the clock at a time, given as its fields.
function run(now, command, ...args) {
    return spawnSync(command, args, {
        encoding: 'utf8',
        env: { ...process.env, NOW: now, PATH: `${bin}:${process.env.PATH}` },
    })
}

// The code the script prints, with the clock at a time.
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

const commands = ['tc5', 'tc8', 'tcx', 'bc4', 'bc5', 'bc8']

// The length of the code each command prints.
const lengths = { tc5: 5, tc8: 8, tcx: 10 }
const bcLengths = { bc4: 4, bc5: 5, bc8: 8 }

// The places a code has before the fraction of the second do not depend on
// it, so the timestamps given to the port drop it. The clock keeps it, for
// tcx --extend. A leap second is second 60, which the port counts as the
// next second, as the specification does.
for (const group of [...groups, 'leap-seconds']) {
    describe(`tests.yaml: ${group}`, () => {
        for (const { input, output } of suite[group].encode) {
            test(`encode ${input}`, () => {
                const now = fieldsOf(input)
                const extended = print(now, 'tcx', '--extend')

                for (const [command, n] of Object.entries(lengths)) {
                    const code = print(now, command, wholeSecondOf(input))

                    assert.equal(code.length, n)
                    assertAgrees(code, output)
                }

                assert.equal(extended.length, 14)
                assertAgrees(extended, output)
            })
        }
    })
}

describe('tests.yaml: bc', () => {
    for (const { input, output } of suite.bc.encode) {
        test(`encode ${input}`, () => {
            for (const [command, n] of Object.entries(bcLengths)) {
                const code = print('', command, wholeSecondOf(input))

                assert.equal(code.length, n)
                assertAgrees(code, output)
            }
        })
    }
})

const t = fieldsOf('2026-10-17T14:23:45.25Z')

// The codes of the time t, with and without a timestamp, which has whole
// seconds.
const codes = [
    ['tc5', '2026V'],
    ['tc8', '2026VDJB'],
    ['tcx', '2026VDJBD5'],
    ['tcx --extend', '2026VDJBD5.250'],
    ['tcx --extend 2026-10-17T14:23:45Z', '2026VDJBD5.000'],
    ['bc4', 'C6VD'],
    ['bc5', 'C6VDJ'],
    ['bc8', 'C6VDJBD5'],
]

test('each command prints the current time, or the time it is given', () => {
    for (const [call, code] of codes) {
        const args = call.split(' ')

        assert.equal(print(t, ...args), code, call)

        if (args.length === 1) {
            const given = print('', ...args, '2026-10-17T14:23:45Z')

            assert.equal(given, code.slice(0, 10), call)
        }
    }
})

test('without a command, the script prints a TC8', () => {
    assert.equal(print(t), '2026VDJB')
    assert.equal(print('', '1969-07-20T20:17:40Z'), '1969PJFW')
})

// What each command prints when it is given the wrong arguments.
function usageOf(command) {
    const flag = command === 'tcx' ? ' [--extend]' : ''
    const time = '[yyyy-mm-ddTHH:MM:SSZ | --git [commit]]'

    return `Usage: ${command}${flag} ${time}\n`
}

test('the commands take one timestamp, and tcx --extend before it', () => {
    const given = '2026-10-17T14:23:45Z'
    const timestamps = [
        '2026-13-17T14:23:45Z',
        '2026-00-17T14:23:45Z',
        '2026-10-32T14:23:45Z',
        '2026-10-00T14:23:45Z',
        '2026-10-17T24:23:45Z',
        '2026-10-17T14:60:45Z',
        '2026-10-17T14:23:61Z',
        '2026-10-17T14:23:45.25Z',
        '2026-10-17T14:23Z',
        '2026-10-17T14:23:45+00:00',
        '2026-10-17t14:23:45z',
        '26-10-17T14:23:45Z',
        ` ${given}`,
    ]
    const refusals = [
        ['tc5', '--extend'],
        ['bc4', '--extend'],
        ['bc5', '2026-10-17'],
        ['bc8', '--extend'],
        ['tcx', '--extended'],
        ['tcx', '--extend', '--extend'],
        ['tcx', given, '--extend'],
        ['tc8', given, given],
        ['tcx', '--git', 'HEAD', '--extend'],
    ]

    // Each function has its own pattern for a timestamp, and its own --git.
    for (const command of commands) {
        for (const timestamp of timestamps) {
            refusals.push([command, timestamp])
        }

        refusals.push(
            [command, '--git', 'HEAD', 'HEAD'],
            [command, given, '--git'],
        )
    }

    for (const args of refusals) {
        const { status, stdout, stderr } = run(t, script, ...args)

        assert.equal(status, 2, args.join(' '))
        assert.equal(stdout, '')
        assert.equal(stderr, usageOf(args[0]))
    }

    // Without a command, the script runs tc8.
    assert.equal(run(t, script, '--extend').stderr, usageOf('tc8'))
})

test('--extend needs a date that gives the fraction of the second', () => {
    // A date that does not know %N prints N, or %N.
    for (const now of ['2026 10 17 14 23 45 N', '2026 10 17 14 23 45 %N']) {
        const { status, stdout, stderr } = run(now, script, 'tcx', '--extend')

        assert.equal(print(now, 'tcx'), '2026VDJBD5')
        assert.equal(status, 1)
        assert.equal(stdout, '')
        assert.equal(stderr, 'tcx: date gives no fraction of the second.\n')
    }
})

// A function as it is written in the script, and every word in it that
// could name a variable it sets.
function definitionOf(name) {
    const start = source.indexOf(`function ${name} {\n`)
    const end = source.indexOf('\n}\n', start) + 3
    const definition = source.slice(start, end)
    const words = [...new Set(definition.match(/[a-z][a-z0-9_]*/g))]

    return { definition, words }
}

// Copied into a script by itself, a function works whether or not the
// script sets the options this one does, and leaves no variables behind:
// declare would print them. The script is run by a function like run.
function assertStandsAlone(call, code, runner) {
    const { definition, words } = definitionOf(call.split(' ')[0])

    assert.ok(words.includes('half_month'), call)

    for (const options of ['', 'set -o errexit -o nounset -o pipefail']) {
        const shell = `${options}
${definition}
${call}
declare -p ${words.join(' ')} 2>/dev/null || true
`
        const { status, stdout, stderr } = runner('bash', '-c', shell)

        assert.equal(stderr, '', call)
        assert.equal(stdout, `${code}\n`, call)
        assert.equal(status, 0, call)
    }
}

test('each function stands alone', () => {
    for (const [call, code] of codes) {
        assertStandsAlone(call, code, (...args) => run(t, ...args))
    }

    for (const command of commands) {
        const code = print('', command, second.timestamp)

        assertStandsAlone(`${command} --git`, code, (...args) =>
            runIn(repository, ...args),
        )
    }
})

test('--git prints the author date of a commit, HEAD by default', () => {
    const hash = runIn(repository, 'git', 'rev-parse', 'older').stdout.trim()
    const revisions = [
        [[], second],
        [['HEAD'], second],
        [['main'], second],
        [['HEAD~1'], first],
        [['older'], first],
        [['v1.0'], first],
        [[hash], first],
    ]

    // The code of a commit is the code of its timestamp, with each command.
    for (const [revision, { timestamp }] of revisions) {
        for (const call of [...commands, 'tcx --extend']) {
            const args = [...call.split(' '), '--git', ...revision]
            const code = print('', ...call.split(' '), timestamp)
            const result = runIn(repository, script, ...args)

            assert.equal(result.stderr, '', args.join(' '))
            assert.equal(result.stdout, `${code}\n`, args.join(' '))
            assert.equal(result.status, 0, args.join(' '))
        }
    }

    // Without a command, the script runs tc8.
    const tc8 = print('', 'tc8', second.timestamp)

    assert.equal(runIn(repository, script, '--git').stdout, `${tc8}\n`)
})

test('--git fails as git does, with no commit or repository', () => {
    const failures = [
        [repository, 'nope', "fatal: bad revision 'nope'"],
        // An option of git log is read as a commit, and is not found.
        [repository, '--all', "fatal: bad revision '--all'"],
        [bin, 'HEAD', 'fatal: not a git repository'],
    ]

    for (const [cwd, revision, message] of failures) {
        for (const command of commands) {
            const call = `${command} --git ${revision}`
            const result = runIn(cwd, script, command, '--git', revision)

            assert.equal(result.status, 128, call)
            assert.equal(result.stdout, '', call)
            assert.ok(result.stderr.startsWith(message), call)
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
