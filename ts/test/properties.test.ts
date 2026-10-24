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
const b1 = 'ABCDEFGHIJKLMNOPQRST'
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

// Lengths of bicentennial codes, from BC2 to BC18.
const bcDigits = fc.integer({ min: 2, max: 18 })

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

    test(
        'a code past the end of its half month reads as its last second',
        () => {
            const offset = (d: TimeCode.Descriptor) =>
                (d.block * 200) + d.seconds

            fc.assert(
                fc.property(descriptor, (d) => {
                    const t = TimeCode.parse(d).toInstant()
                    const read = TimeCode.of(t).descriptor
                    const label = `${JSON.stringify(d)}: ${t}`

                    assert.equal(read.year, d.year, label)
                    assert.equal(read.halfMonth, d.halfMonth, label)
                    assert.equal(read.nanoseconds, d.nanoseconds, label)

                    if (offset(read) < offset(d)) {
                        // One second later is in the next half month, which
                        // may be past the year 9999.
                        const next = t.add({ seconds: 1 })
                        const isPast =
                            next.toZonedDateTimeISO('UTC').year > 9999 ||
                            TimeCode.of(next).descriptor.halfMonth !==
                                d.halfMonth
                        assert.ok(isPast, label)
                    } else {
                        assert.deepEqual(read, { ...d }, label)
                    }
                }),
                runs,
            )
        },
    )
})

describe('bicentennial codes', () => {
    // The place of a TimeCode's year in its cycle of 200 years.
    const place = (code: TimeCode): string => {
        const years = code.descriptor.year % 200
        return b1[Math.floor(years / 10)] + `${years % 10}`
    }

    // The TimeCode of the start of a year.
    const startOf = (year: number): TimeCode =>
        TimeCode.parse(`${year}`.padStart(4, '0'))

    test('a code is the TimeCode with the place of its year', () => {
        fc.assert(
            fc.property(instant, bcDigits, (t, n) => {
                const code = TimeCode.of(t)

                assert.equal(
                    code.bc(n),
                    place(code) + code.display(n + 2).slice(4),
                )
                assert.equal(code.bc(), place(code) + code.display().slice(4))
                assert.equal(code.bc4(), code.bc(4))
            }),
            runs,
        )
    })

    // Near a time no more than 100 years after it, or less than 100 before,
    // a code reads as the TimeCode it comes from does.
    test('a code reads near its time as its TimeCode does', () => {
        fc.assert(
            fc.property(
                instant,
                bcDigits,
                fc.integer({ min: -99, max: 100 }),
                (t, n, offset) => {
                    const code = TimeCode.of(t)
                    const year = code.descriptor.year + offset
                    const near = startOf(Math.min(Math.max(year, 0), 9999))

                    assert.equal(
                        TimeCode.parseBC(code.bc(n), near).toString(),
                        TimeCode.parse(code.display(n + 2)).toString(),
                    )
                },
            ),
            runs,
        )
    })

    test('a code reads as the nearest year in its cycle', () => {
        fc.assert(
            fc.property(
                instant,
                fc.integer({ min: 0, max: 9999 }),
                (t, nearYear) => {
                    const code = TimeCode.of(t)
                    const near = startOf(nearYear)
                    let read: TimeCode

                    try {
                        read = TimeCode.parseBC(code.bc4(), near)
                    } catch (e) {
                        // The nearest year may be before 0000 or after 9999.
                        assert.ok(nearYear < 100 || nearYear >= 9900)
                        assert.equal(
                            (e as Error).message,
                            'TimeCodes support four digit years.',
                        )
                        return
                    }

                    const year = read.descriptor.year
                    assert.ok(year - nearYear >= -100 && year - nearYear < 100)
                    assert.equal(year % 200, code.descriptor.year % 200)
                },
            ),
            runs,
        )
    })

    test('a code that is not one is rejected', () => {
        const near = startOf(2026)
        const errors = [
            ['C', 'Bicentennial codes have from 2 to 8 places.'],
            ['C6VDJBD5B', 'Bicentennial codes have from 2 to 8 places.'],
            ['C6VD.5', 'Only a BC8 may have a fraction of the second.'],
            ['U6VD', 'U is not allowed in B1.'],
            ['CXVD', 'X is not allowed in B2.'],
            ['C6MD', 'M is not allowed in P5.'],
            ['C6VDJBD5.', 'Fractions of the second have 1 to 9 digits.'],
        ]

        for (const [input, reason] of errors) {
            assert.throws(() => TimeCode.parseBC(input, near), {
                name: 'RangeError',
                message: reason,
            })
        }

        assert.throws(() => TimeCode.of(first).bc(1), RangeError)
        assert.throws(() => TimeCode.of(first).bc(19), RangeError)
    })
})
