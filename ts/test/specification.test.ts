import { specification } from './helpers.js'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { join } from 'node:path'
import { describe, test } from 'node:test'
import { parse } from 'yaml'
import { TimeCode } from '../src/index.js'

type Case = { input: string; near?: string; output: string }
type Group = { decode: Case[]; encode: Case[] }

const suite: Record<string, Group> = parse(
    readFileSync(join(specification, 'tests.yaml'), 'utf8'),
)

const instant = (s: string) => Temporal.Instant.from(s)

// Print a time with each method that gives a code of this group's length.
const encoders: Record<string, (t: Temporal.Instant, n: number) => string[]> =
    {
        tc5: (t) => [TimeCode.of(t).tc5(), TimeCode.tc5(t)],
        tc8: (t) => [TimeCode.of(t).tc8(), TimeCode.tc8(t)],
        tcx: (t) => [TimeCode.of(t).tcX(), TimeCode.tcX(t)],
        tcn: (t, n) => [
            TimeCode.of(t).display(n),
            TimeCode.tc(t, n),
            ...(n >= 12 ? [TimeCode.of(t).tcX(n - 11)] : []),
        ],
    }

for (const [name, encoder] of Object.entries(encoders)) {
    describe(`tests.yaml: ${name}`, () => {
        for (const { input, output } of suite[name].decode) {
            test(`decode ${input}`, () => {
                const t = TimeCode.parse(input).toInstant()
                assert.equal(t.toString(), output)
            })
        }

        for (const { input, output } of suite[name].encode) {
            test(`encode ${input}`, () => {
                for (const code of encoder(instant(input), output.length)) {
                    assert.equal(code, output)
                }
            })
        }
    })
}

// Bicentennial codes are read near a time, which each decode test gives.
describe('tests.yaml: bc', () => {
    for (const { input, near, output } of suite.bc.decode) {
        test(`decode ${input} near ${near}`, () => {
            const t = TimeCode.parseBC(input, TimeCode.of(instant(near!)))
            assert.equal(t.toInstant().toString(), output)
        })
    }

    for (const { input, output } of suite.bc.encode) {
        test(`encode ${input}`, () => {
            const t = instant(input)
            const n = output.length
            const codes = [
                TimeCode.of(t).bc(n),
                TimeCode.bc(t, n),
                ...(n === 4 ? [TimeCode.of(t).bc4(), TimeCode.bc4(t)] : []),
            ]

            for (const code of codes) {
                assert.equal(code, output)
            }
        })
    }
})

// Temporal has no leap seconds: it reads 23:59:60 as 23:59:59. TimeCodes
// treat a leap second as the last second of its half month, so both ways
// round, the leap seconds land on 23:59:59.
describe('tests.yaml: leap-seconds', () => {
    for (const { input, output } of suite['leap-seconds'].decode) {
        test(`decode ${input} as the last second`, () => {
            const t = TimeCode.parse(input).toInstant()
            assert.equal(t.toString(), output.replace(':60', ':59'))
        })
    }

    for (const { input, output } of suite['leap-seconds'].encode) {
        test(`encode ${input} as the last second`, () => {
            const code = TimeCode.of(instant(input)).tcX()
            assert.equal(code, TimeCode.parse(output).tcX())
            assert.equal(
                instant(input).toString(),
                input.replace(':60', ':59'),
            )
        })
    }
})
