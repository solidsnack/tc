import { temporal } from './temporal.js'

/** The revision of `specification/index.md` that this library follows. */
export const SPEC_VERSION: string = '1.1.0'

const p5: string = 'ABCDEFGHIJKLOPQRSTUVWXYZ'
const p6: string = 'BDFGHJKLMPQRSTVWXYZ'
const p7: string = 'BCDFGHJKLMNPQRVWXYZ'
const p8: string = 'BCDFGHJLMNPQRSVWXYZ'
const p9: string = 'BCDEFHIJLMNOPQUVWXYZ'
const b1: string = 'ABCDEFGHIJKLMNOPQRST'
const decimal: string = '0123456789'

// The start of the second half month in seconds. 372 hours is 15.5 days.
const hm2Begin = 372 * 3600

// The largest value of each component of a descriptor. All are at least 0.
const limits = {
    year: 9999,
    halfMonth: 23,
    block: (19 * 19 * 19) - 1,
    seconds: 199,
    nanoseconds: 999_999_999,
} as const

/**
 * A time in UTC, as described in `specification/index.md`: the year, the
 * half month, the block of 200 seconds within the half month, the second
 * within the block and the nanosecond within the second.
 */
export class TimeCode {
    // NB: Every value is an integer.
    #descriptor: TimeCode.Descriptor

    get #year(): number {
        return this.#descriptor.year
    }

    get #halfMonth(): number {
        return this.#descriptor.halfMonth
    }

    get #block(): number {
        return this.#descriptor.block
    }

    get #seconds(): number {
        return this.#descriptor.seconds
    }

    get #nanoseconds(): number {
        return this.#descriptor.nanoseconds
    }

    private constructor(descriptor: TimeCode.Descriptor) {
        this.#descriptor = Object.freeze({ ...descriptor })
    }

    static of(t: Temporal.Instant | Temporal.ZonedDateTime): TimeCode {
        const instant = 'toInstant' in t ? t.toInstant() : t
        const utc = instant.toZonedDateTimeISO('UTC')
        const year = utc.year

        if (year < 0 || year > 9999) {
            throw new RangeError('TimeCodes support four digit years.')
        }

        const mBegin = utc.with({ day: 1 }).startOfDay()
        const fromMBegin = instant.epochNanoseconds - mBegin.epochNanoseconds
        const locInMonth = Number(fromMBegin / 1_000_000_000n)
        const nanoseconds = Number(fromMBegin % 1_000_000_000n)

        // Calculate offsets needed if we are in the second half-month.
        const corrections = locInMonth < hm2Begin ? [0, 0] : [1, -hm2Begin]

        const wholeMonth = utc.month - 1
        const locInHalfMonth = locInMonth + corrections[1]

        return new TimeCode({
            year: year,
            halfMonth: (2 * wholeMonth) + corrections[0],
            block: Math.floor(locInHalfMonth / 200),
            seconds: locInHalfMonth % 200,
            nanoseconds: nanoseconds,
        })
    }

    static now(): TimeCode {
        return TimeCode.of(temporal.Now.instant())
    }

    static parse(d: TimeCode.Descriptor): TimeCode
    static parse(s: string): TimeCode
    static parse(input: string | TimeCode.Descriptor): TimeCode {
        const d = typeof input === 'string' ? TimeCode.#read(input) : input

        for (const [name, max] of Object.entries(limits)) {
            const value = d[name as keyof typeof limits]

            if (!Number.isInteger(value) || value < 0 || value > max) {
                throw new RangeError(
                    `The ${name} must be an integer from 0 to ${max}.`,
                )
            }
        }

        // A leap second takes the second just after the end of the month.
        // Like anything else past the end of the half month, it is read as
        // the last second of the half month, as Temporal does with 23:59:60.
        const last = TimeCode.#lengthOf(d.year, d.halfMonth) - 1
        const locInHalfMonth = Math.min((d.block * 200) + d.seconds, last)

        return new TimeCode({
            year: d.year,
            halfMonth: d.halfMonth,
            block: Math.floor(locInHalfMonth / 200),
            seconds: locInHalfMonth % 200,
            nanoseconds: d.nanoseconds,
        })
    }

    /**
     * Read a bicentennial code of any length, from BC2 to a BC8 with a
     * fraction of the second. Of the years with its place in the cycle of
     * 200 years, it stands for the one nearest the year of near, by default
     * now: no more than 100 years before it, and less than 100 after. As
     * with a TimeCode, it stands for the start of the time it covers.
     */
    static parseBC(code: string, near: TimeCode = TimeCode.now()): TimeCode {
        const [places, fraction] = code.split('.')

        if (places.length < 2 || places.length > 8) {
            throw new RangeError('Bicentennial codes have from 2 to 8 places.')
        }

        if (fraction !== undefined && places.length < 8) {
            throw new RangeError(
                'Only a BC8 may have a fraction of the second.',
            )
        }

        // The digit a place of the year stands for.
        const digit = (chars: string, i: number): number => {
            const n = chars.indexOf(places[i])

            if (n < 0) {
                throw new RangeError(
                    `${places[i]} is not allowed in B${i + 1}.`,
                )
            }

            return n
        }

        const years = (10 * digit(b1, 0)) + digit(decimal, 1)
        const earliest = near.#year - 100
        const year = earliest + ((((years - earliest) % 200) + 200) % 200)

        if (year < 0 || year > 9999) {
            throw new RangeError('TimeCodes support four digit years.')
        }

        // The rest is read as the TimeCode with the year written out.
        return TimeCode.parse(`${year}`.padStart(4, '0') + code.slice(2))
    }

    // Reads the components of a TimeCode of any length, from TC4 to TCX
    // with a fraction of the second. Places that are not present count as 0.
    static #read(code: string): TimeCode.Descriptor {
        const [places, fraction, ...extra] = code.split('.')

        if (places.length < 4 || places.length > 10) {
            throw new RangeError('TimeCodes have from 4 to 10 places.')
        }

        if (extra.length > 0) {
            throw new RangeError('TimeCodes have at most one decimal point.')
        }

        if (fraction !== undefined && places.length < 10) {
            throw new RangeError(
                'Only a TCX may have a fraction of the second.',
            )
        }

        if (fraction !== undefined && !/^[0-9]{1,9}$/.test(fraction)) {
            throw new RangeError('Fractions of the second have 1 to 9 digits.')
        }

        const digit = (chars: string, i: number): number => {
            if (i >= places.length) {
                return 0
            }

            const n = chars.indexOf(places[i])

            if (n < 0) {
                const place = i < 9 ? `P${i + 1}` : 'PX'
                throw new RangeError(
                    `${places[i]} is not allowed in ${place}.`,
                )
            }

            return n
        }

        return {
            year: (1000 * digit(decimal, 0)) + (100 * digit(decimal, 1)) +
                (10 * digit(decimal, 2)) + digit(decimal, 3),
            halfMonth: digit(p5, 4),
            block: (19 * 19 * digit(p6, 5)) + (19 * digit(p7, 6)) +
                digit(p8, 7),
            seconds: (10 * digit(p9, 8)) + digit(decimal, 9),
            nanoseconds: fraction === undefined
                ? 0
                : Number(fraction.padEnd(9, '0')),
        }
    }

    // The length of a half month in seconds. The second half month ends with
    // the month, so it is shorter in months of fewer than 31 days.
    static #lengthOf(year: number, halfMonth: number): number {
        if (halfMonth % 2 === 0) {
            return hm2Begin
        }

        const month = temporal.PlainYearMonth.from({
            year: year,
            month: Math.floor(halfMonth / 2) + 1,
        })

        return (month.daysInMonth * 24 * 3600) - hm2Begin
    }

    /**
     * Present the input value or current time as a TC5 code.
     */
    static tc5(): string
    static tc5(t: Temporal.Instant): string
    static tc5(t: Temporal.ZonedDateTime): string
    static tc5(t?: Temporal.Instant | Temporal.ZonedDateTime): string {
        const code = TimeCode.of(t ?? temporal.Now.instant())
        return code.tc5()
    }

    /**
     * Present the input value or current time as a TC8 code.
     */
    static tc8(): string
    static tc8(t: Temporal.Instant): string
    static tc8(t: Temporal.ZonedDateTime): string
    static tc8(t?: Temporal.Instant | Temporal.ZonedDateTime): string {
        const code = TimeCode.of(t ?? temporal.Now.instant())
        return code.tc8()
    }

    /**
     * Present the input value or current time as a TCX code.
     *
     * If precision is passed, up to nine digits of subseconds can be
     * displayed.
     */
    static tcX(precision?: number): string
    static tcX(t: Temporal.Instant, precision?: number): string
    static tcX(t: Temporal.ZonedDateTime, precision?: number): string
    static tcX(
        t?: number | Temporal.Instant | Temporal.ZonedDateTime,
        precision?: number,
    ): string {
        if (typeof t === 'number') {
            return TimeCode.now().tcX(t)
        }

        const code = TimeCode.of(t ?? temporal.Now.instant())
        return code.tcX(precision)
    }

    /**
     * Present the input value or current time with the requested number of
     * digits, if passed, or with the precision of the value.
     */
    static tc(digits?: number): string
    static tc(t: Temporal.Instant, digits?: number): string
    static tc(t: Temporal.ZonedDateTime, digits?: number): string
    static tc(
        t?: number | Temporal.Instant | Temporal.ZonedDateTime,
        digits?: number,
    ): string {
        if (typeof t === 'number') {
            return TimeCode.now().display(t)
        }

        const code = TimeCode.of(t ?? temporal.Now.instant())
        return code.display(digits)
    }

    /**
     * Present the input value or current time as a BC4 code.
     */
    static bc4(): string
    static bc4(t: Temporal.Instant): string
    static bc4(t: Temporal.ZonedDateTime): string
    static bc4(t?: Temporal.Instant | Temporal.ZonedDateTime): string {
        const code = TimeCode.of(t ?? temporal.Now.instant())
        return code.bc4()
    }

    /**
     * Present the input value or current time as a bicentennial code with
     * the requested number of digits, if passed, or with the precision of
     * the value.
     */
    static bc(digits?: number): string
    static bc(t: Temporal.Instant, digits?: number): string
    static bc(t: Temporal.ZonedDateTime, digits?: number): string
    static bc(
        t?: number | Temporal.Instant | Temporal.ZonedDateTime,
        digits?: number,
    ): string {
        if (typeof t === 'number') {
            return TimeCode.now().bc(t)
        }

        const code = TimeCode.of(t ?? temporal.Now.instant())
        return code.bc(digits)
    }

    // Produce a full length (TC20) code.
    #format(): string {
        const block = this.#block
        const seconds = this.#seconds

        return `${this.#year}`.padStart(4, '0') +
            p5[this.#halfMonth] +
            p6[Math.floor(block / (19 * 19))] +
            p7[Math.floor(block / 19) % 19] +
            p8[block % 19] +
            p9[Math.floor(seconds / 10)] +
            `${seconds % 10}` +
            '.' + `${this.#nanoseconds}`.padStart(9, '0')
    }

    /**
     * Display the TimeCode in TC5 format.
     */
    tc5(): string {
        return this.#format().slice(0, 5)
    }

    /**
     * Display the TimeCode in TC8 format.
     */
    tc8(): string {
        return this.#format().slice(0, 8)
    }

    /**
     * Display the TimeCode in TCX format. If precision is passed, up to nine
     * digits of subseconds can be displayed.
     */
    tcX(precision?: number): string {
        if (
            precision !== undefined &&
            (!Number.isInteger(precision) || precision < 0 || precision > 9)
        ) {
            throw new RangeError('The precision must be from 0 to 9 digits.')
        }

        const s = this.#format()

        return precision ? s.slice(0, 11 + precision) : s.slice(0, 10)
    }

    /**
     * Display the TimeCode with the requested number of digits, if passed,
     * or with the precision of the encoded value. As in the specification,
     * TC11 is displayed like TC12.
     */
    display(digits?: number): string {
        if (
            digits !== undefined &&
            (!Number.isInteger(digits) || digits < 4 || digits > 20)
        ) {
            throw new RangeError('TimeCodes have from 4 to 20 digits.')
        }

        const s = this.#format()

        if (digits === undefined) {
            return s.replace(/[.]?0+$/, '')
        }

        return s.slice(0, digits === 11 ? 12 : digits)
    }

    /**
     * Display the TimeCode in BC4 format: the place of its year in a cycle
     * of 200 years, the half month and the first place of the block.
     */
    bc4(): string {
        return this.bc(4)
    }

    /**
     * Display the TimeCode as a bicentennial code, with the requested number
     * of digits, if passed, or with the precision of the encoded value: the
     * TimeCode with its year written as its place in a cycle of 200 years.
     * As in the specification, BC9 is displayed like BC10.
     */
    bc(digits?: number): string {
        if (
            digits !== undefined &&
            (!Number.isInteger(digits) || digits < 2 || digits > 18)
        ) {
            throw new RangeError(
                'Bicentennial codes have from 2 to 18 digits.',
            )
        }

        const years = this.#year % 200
        const code = this.display(digits === undefined ? digits : digits + 2)

        return b1[Math.floor(years / 10)] + `${years % 10}` + code.slice(4)
    }

    /**
     * The components of the TimeCode.
     */
    get descriptor(): TimeCode.Descriptor {
        return { ...this.#descriptor }
    }

    /**
     * The start of the time the TimeCode stands for.
     */
    toInstant(): Temporal.Instant {
        const mBegin = temporal.ZonedDateTime.from({
            year: this.#year,
            month: Math.floor(this.#halfMonth / 2) + 1,
            day: 1,
            timeZone: 'UTC',
        })
        const hmBegin = this.#halfMonth % 2 === 1 ? hm2Begin : 0

        return mBegin.toInstant().add({
            seconds: hmBegin + (this.#block * 200) + this.#seconds,
            nanoseconds: this.#nanoseconds,
        })
    }

    toString(): string {
        return this.display()
    }

    toJSON(): string {
        return this.display()
    }
}

export declare namespace TimeCode {
    /**
     * The components of a TimeCode. Every value is an integer.
     */
    type Descriptor = {
        year: number
        halfMonth: number
        block: number
        seconds: number
        nanoseconds: number
    }
}
