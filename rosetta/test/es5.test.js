const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const { describe, test } = require('node:test')
const acorn = require('acorn')
const { suite, groups } = require('./cases.js')
const timecode = require('../es5/timecode.js')

const file = path.join(__dirname, '..', 'es5', 'timecode.js')

test('es5/timecode.js is ECMAScript 5', () => {
    acorn.parse(fs.readFileSync(file, 'utf8'), { ecmaVersion: 5 })
})

test('a Date is encoded in milliseconds', () => {
    const t = new Date('2026-10-17T14:23:45.250Z')

    assert.equal(timecode.encode(t), '2026VDJBD5.25')
    assert.equal(timecode.encode(t, 8), '2026VDJB')
    assert.equal(timecode.tcx(t, 3), '2026VDJBD5.250')
})

test('tcx takes a precision from 0 to 9 digits', () => {
    const t = '2026-10-17T14:23:45.25Z'

    assert.equal(timecode.tcx(t, 0), '2026VDJBD5')
    assert.equal(timecode.tcx(t, 9), '2026VDJBD5.250000000')
    assert.throws(() => timecode.tcx(t, 10), RangeError)
    assert.throws(() => timecode.tcx(t, 1.5), RangeError)
})

// Print a time with each function that gives a code of this group's length.
const encoders = {
    tc5: (t) => [timecode.encode(t, 5), timecode.tc5(t)],
    tc8: (t) => [timecode.encode(t, 8), timecode.tc8(t)],
    tcx: (t) => [timecode.encode(t, 10), timecode.tcx(t)],
    tcn: (t, n) => [
        timecode.encode(t, n),
        ...(n >= 12 ? [timecode.tcx(t, n - 11)] : []),
    ],
}

for (const group of groups) {
    describe(`tests.yaml: ${group}`, () => {
        for (const { input, output } of suite[group].decode) {
            test(`decode ${input}`, () => {
                assert.equal(timecode.decode(input), output)
            })
        }

        for (const { input, output } of suite[group].encode) {
            test(`encode ${input}`, () => {
                for (const code of encoders[group](input, output.length)) {
                    assert.equal(code, output)
                }
            })
        }
    })
}

// Like Temporal, the port reads 23:59:60 as 23:59:59, and a leap second
// code, like any code past the end of its half month, as the last second of
// the half month.
describe('tests.yaml: leap-seconds', () => {
    for (const { input, output } of suite['leap-seconds'].decode) {
        test(`decode ${input} as the last second`, () => {
            assert.equal(timecode.decode(input), output.replace(':60', ':59'))
        })
    }

    for (const { input, output } of suite['leap-seconds'].encode) {
        test(`encode ${input} as the last second`, () => {
            const last = timecode.tcx(timecode.decode(output))
            assert.equal(timecode.tcx(input), last)
        })
    }
})
