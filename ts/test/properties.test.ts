import './helpers.js'
import assert from 'node:assert/strict'
import { describe, test } from 'node:test'
import fc from 'fast-check'
import { TimeCode } from '../src/index.js'

const p5 = 'ABCDEFGHIJKLOPQRSTUVWXYZ'
const p6 = 'BDFGHJKLMPQRSTVWXYZ'
const p7 = 'BCDFGHJKLMNPQRVWXYZ'
const p8 = 'BCDFGHJLMNPQRSVWXYZ'
const p9 = 'BCDEFHIJLMNOPQUVWXYZ'
const decimal = '0123456789'

const first = Temporal.Instant.from('0000-01-01T00:00:00Z')
const last = Temporal.Instant.from('9999-12-31T23:59:59.999999999Z')

// The instant at a count of nanoseconds, kept within the years TimeCodes
// cover.
const within = (ns: bigint): Temporal.Instant => {
    const clamped = ns < first.epochNanoseconds
        ? first.epochNanoseconds
        : ns > last.epochNanoseconds
        ? last.epochNanoseconds
        : ns
    return Temporal.Instant.fromEpochNanoseconds(clamped)
}

// Any instant TimeCodes cover, with extra weight on the edges of half
// months, where the calendar matters most.
const anywhere = fc
    .bigInt({ min: first.epochNanoseconds, max: last.epochNanoseconds })
    .map(within)
const nearEdge = fc
    .record({
        year: fc.integer({ min: 0, max: 9999 }),
        halfMonth: fc.integer({ min: 0, max: 23 }),
        offset: fc.bigInt({ min: -400_000_000_000n, max: 400_000_000_000n }),
    })
    .map(({ year, halfMonth, offset }) => {
        const start = TimeCode.parse(
            `${year}`.padStart(4, '0') + p5[halfMonth],
        ).toInstant()
        return within(start.epochNanoseconds + offset)
    })
const instant = fc.oneof(anywhere, nearEdge)

// Two instants, the first no later than the second and often close to it.
const ordered = fc
    .tuple(
        instant,
        fc.oneof(
            fc.constant(0n),
            fc.bigInt({ min: 1n, max: 1_000_000_000n }),
            fc.bigInt({ min: 1n, max: 1_000_000_000_000n }),
            fc.bigInt({
                min: 1n,
                max: last.epochNanoseconds - first.epochNanoseconds,
            }),
        ),
    )
    .map(([x, gap]) => [x, within(x.epochNanoseconds + gap)] as const)

// Lengths of TimeCodes, from TC4 to TC20.
const digits = fc.integer({ min: 4, max: 20 })

// Any TC5, TC8 or TCX the grammar allows, whether or not it is past the end
// of its half month, as a choice of character for every place. Within each
// place, the characters are in sorted order.
type Choice = { chars: string[]; length: number }

const pick = (chars: string) => fc.constantFrom(...chars)
const choice: fc.Arbitrary<Choice> = fc.record({
    chars: fc.tuple(
        pick(decimal),
        pick(decimal),
        pick(decimal),
        pick(decimal),
        pick(p5),
        // Often the last letter, where codes run past the end of the half
        // month.
        fc.oneof(pick(p6), fc.constant('Z')),
        pick(p7),
        pick(p8),
        pick(p9),
        pick(decimal),
    ),
    length: fc.constantFrom(5, 8, 10),
})

const render = ({ chars, length }: Choice): string =>
    chars.slice(0, length).join('')

// Two codes that share their first few places, so that they often differ
// only in the block or the second of one half month.
const codes = fc
    .tuple(
        choice,
        choice,
        fc.oneof(
            fc.integer({ min: 0, max: 10 }),
            fc.integer({ min: 5, max: 9 }),
        ),
    )
    .map(([a, b, shared]) => {
        const chars = [...a.chars.slice(0, shared), ...b.chars.slice(shared)]
        return [render(a), render({ ...b, chars })] as const
    })

// Any descriptor in range, often with a block near or past the end of the
// half month.
const descriptor = fc.record({
    year: fc.integer({ min: 0, max: 9999 }),
    halfMonth: fc.integer({ min: 0, max: 23 }),
    block: fc.oneof(
        fc.integer({ min: 0, max: 6858 }),
        fc.integer({ min: 5300, max: 6858 }),
    ),
    seconds: fc.integer({ min: 0, max: 199 }),
    nanoseconds: fc.integer({ min: 0, max: 999_999_999 }),
})

const runs = { numRuns: 2000 }

// The order is tested for TC5, TC8 and TCX, without fractions of the second.
// A code past the end of its half month reads as the last second, keeping
// its fraction, so 23:59:60.5 would read as a later time than 23:59:61.
describe('sort order of TC5, TC8 and TCX', () => {
    test('an earlier time has a code that sorts no later', () => {
        const encoders = [
            (code: TimeCode) => code.tc5(),
            (code: TimeCode) => code.tc8(),
            (code: TimeCode) => code.tcX(),
        ]

        fc.assert(
            fc.property(ordered, fc.constantFrom(...encoders), ([x, y], f) => {
                const [xc, yc] = [x, y].map((t) => f(TimeCode.of(t)))
                assert.ok(xc <= yc, `${x} ${y}: ${xc} ${yc}`)
            }),
            runs,
        )
    })

    test('a code that sorts earlier reads as a time no later', () => {
        fc.assert(
            fc.property(codes, ([a, b]) => {
                const [xc, yc] = a < b ? [a, b] : [b, a]
                const [x, y] = [xc, yc].map((c) => TimeCode.parse(c))
                const order = Temporal.Instant.compare(
                    x.toInstant(),
                    y.toInstant(),
                )
                assert.ok(order <= 0, `${xc} ${yc}: ${x} ${y}`)
            }),
            runs,
        )
    })
})

describe('reading and writing', () => {
    test('a full length code reads as the same time', () => {
        fc.assert(
            fc.property(instant, (t) => {
                const code = TimeCode.of(t)
                assert.ok(
                    TimeCode.parse(code.toString()).toInstant().equals(t),
                )
                assert.ok(
                    TimeCode.parse(code.descriptor).toInstant().equals(t),
                )
            }),
            runs,
        )
    })

    test('a shorter code reads as the start of its window', () => {
        fc.assert(
            fc.property(instant, digits, (t, n) => {
                const code = TimeCode.of(t).display(n)
                const start = TimeCode.parse(code).toInstant()
                assert.ok(Temporal.Instant.compare(start, t) <= 0)
                assert.equal(TimeCode.of(start).display(n), code)

                if (!start.equals(first)) {
                    const before = start.subtract({ nanoseconds: 1 })
                    assert.notEqual(TimeCode.of(before).display(n), code)
                }
            }),
            runs,
        )
    })

    test('every code is the start of the full length code', () => {
        fc.assert(
            fc.property(
                instant,
                digits,
                fc.integer({ min: 0, max: 9 }),
                (t, n, p) => {
                    const code = TimeCode.of(t)
                    const full = code.display(20)
                    const shorter = [
                        [code.tc5(), 5],
                        [code.tc8(), 8],
                        [code.tcX(p), p === 0 ? 10 : 11 + p],
                        [code.display(n), n === 11 ? 12 : n],
                    ] as const

                    for (const [s, length] of shorter) {
                        assert.equal(s, full.slice(0, length))
                    }

                    assert.ok(full.startsWith(code.toString()))
                },
            ),
            runs,
        )
    })

    test('a zoned time has the same code as its instant', () => {
        const zones = ['UTC', 'America/Chicago', 'Asia/Kolkata', '+05:45']
        fc.assert(
            fc.property(instant, fc.constantFrom(...zones), (t, zone) => {
                const zoned = t.toZonedDateTimeISO(zone)
                assert.equal(
                    TimeCode.of(zoned).toString(),
                    TimeCode.of(t).toString(),
                )
            }),
        )
    })

    test('a code past the end of its half month reads as its last second', () => {
        const offset = (d: TimeCode.Descriptor) => (d.block * 200) + d.seconds

        fc.assert(
            fc.property(descriptor, (d) => {
                const t = TimeCode.parse(d).toInstant()
                const read = TimeCode.of(t).descriptor
                const label = `${JSON.stringify(d)}: ${t}`

                assert.equal(read.year, d.year, label)
                assert.equal(read.halfMonth, d.halfMonth, label)
                assert.equal(read.nanoseconds, d.nanoseconds, label)

                if (offset(read) < offset(d)) {
                    // One second later is in the next half month, which may
                    // be past the year 9999.
                    const next = t.add({ seconds: 1 })
                    const isPast =
                        next.toZonedDateTimeISO('UTC').year > 9999 ||
                        TimeCode.of(next).descriptor.halfMonth !== d.halfMonth
                    assert.ok(isPast, label)
                } else {
                    assert.deepEqual(read, { ...d }, label)
                }
            }),
            runs,
        )
    })
})
