#!/usr/bin/env node
import { readFileSync } from 'node:fs'
import { object, or } from '@optique/core/constructs'
import { message, text } from '@optique/core/message'
import { optional, withDefault } from '@optique/core/modifiers'
import {
    argument,
    command,
    constant,
    flag,
    option,
} from '@optique/core/primitives'
import {
    integer,
    type ValueParser,
    type ValueParserResult,
} from '@optique/core/valueparser'
import { run } from '@optique/run'
import { temporal } from './temporal.js'
import { TimeCode } from './timecode.js'

const { version } = JSON.parse(
    readFileSync(new URL('../package.json', import.meta.url), 'utf8'),
)

// The only timestamps accepted: yyyy-mm-ddTHH:MM:SS.nnnnnnnnnZ, where the
// fraction may have from 1 to 9 digits, and the time may stop after the
// hours, the minutes or the seconds, or be left out.
const timestamp = new RegExp(
    '^[0-9]{4}-[0-9]{2}-[0-9]{2}' +
        '(T[0-9]{2}(:[0-9]{2}(:[0-9]{2}([.][0-9]{1,9})?)?)?)?Z$',
)

// A time to encode: a timestamp in UTC, or a TimeCode.
function time(): ValueParser<'sync', TimeCode> {
    return {
        mode: 'sync',
        metavar: 'TIME',
        placeholder: TimeCode.parse('2000A'),
        parse(input: string): ValueParserResult<TimeCode> {
            try {
                return { success: true, value: TimeCode.parse(input) }
            } catch {
                // Not a TimeCode, so it should be a timestamp.
            }

            if (timestamp.test(input)) {
                // Each shorter timestamp is the start of the full one, so
                // the rest of the time is filled in with zeros.
                const given = input.slice(0, -1)
                const rest = 'T00:00:00.000000000'.slice(given.length - 10)

                try {
                    const instant = temporal.Instant.from(`${given}${rest}Z`)
                    return { success: true, value: TimeCode.of(instant) }
                } catch {
                    // Not a real date or time, such as 2026-02-30 or 24:00.
                }
            }

            return {
                success: false,
                error: message`Not a TimeCode or a timestamp in UTC, such as ${
                    text('2026-10-17T14:23:45Z')
                }: ${input}.`,
            }
        },
        format(value: TimeCode): string {
            return value.display()
        },
    }
}

// A TimeCode to decode, kept with the input it was read from.
function code(): ValueParser<'sync', [string, TimeCode]> {
    return {
        mode: 'sync',
        metavar: 'CODE',
        placeholder: ['2000A', TimeCode.parse('2000A')],
        parse(input: string): ValueParserResult<[string, TimeCode]> {
            try {
                return { success: true, value: [input, TimeCode.parse(input)] }
            } catch (e) {
                const reason = e instanceof Error ? e.message : `${e}`
                return {
                    success: false,
                    error: message`Not a TimeCode: ${input}. ${text(reason)}`,
                }
            }
        },
        format(value: [string, TimeCode]): string {
            return value[0]
        },
    }
}

const now = () =>
    withDefault(
        argument(time(), {
            description: message`The time to encode; by default, now.`,
        }),
        () => TimeCode.now(),
    )

const parser = or(
    command(
        'tc5',
        object({ command: constant('tc5'), time: now() }),
        { brief: message`Print a time as a TC5 code: the half month.` },
    ),
    command(
        'tc8',
        object({ command: constant('tc8'), time: now() }),
        {
            brief:
                message`Print a time as a TC8 code: the block of 200 seconds.`,
        },
    ),
    command(
        'tcx',
        object({
            command: constant('tcx'),
            precision: withDefault(
                option(
                    '-p',
                    '--precision',
                    integer({ min: 0, max: 9, metavar: 'DIGITS' }),
                    {
                        description:
                            message`Digits of the fraction of the second.`,
                    },
                ),
                0,
            ),
            time: now(),
        }),
        { brief: message`Print a time as a TCX code: the second.` },
    ),
    command(
        'tc',
        object({
            command: constant('tc'),
            digits: optional(
                option(
                    '-d',
                    '--digits',
                    integer({ min: 4, max: 20, metavar: 'LENGTH' }),
                    {
                        description: message`Length of the code.`,
                    },
                ),
            ),
            time: now(),
        }),
        { brief: message`Print a time as a TimeCode of any length.` },
    ),
    command(
        'bc4',
        object({ command: constant('bc4'), time: now() }),
        {
            brief: message`Print a time as a BC4 code: about 20 hours, in a ${
                text('cycle of 200 years.')
            }`,
        },
    ),
    command(
        'bc',
        object({
            command: constant('bc'),
            digits: optional(
                option(
                    '-d',
                    '--digits',
                    integer({ min: 2, max: 18, metavar: 'LENGTH' }),
                    {
                        description: message`Length of the code.`,
                    },
                ),
            ),
            time: now(),
        }),
        { brief: message`Print a time as a bicentennial code of any length.` },
    ),
    command(
        'parse',
        object({
            command: constant('parse'),
            json: withDefault(
                flag('--json', {
                    description:
                        message`Print the code, time and descriptor as JSON.`,
                }),
                false,
            ),
            code: argument(code(), {
                description: message`The TimeCode to decode.`,
            }),
        }),
        { brief: message`Print the time a TimeCode stands for, in UTC.` },
    ),
    // Without a command, timecode is the same as timecode tc8.
    object({ command: constant('tc8'), time: constant(undefined) }),
)

const config = run(parser, {
    programName: 'timecode',
    brief: message`Encode and decode TimeCodes: sortable, short timestamps.`,
    description: message`Without a command, print the current time as a TC8.`,
    footer: message`${
        text(
            'A TIME is a TimeCode or a timestamp in UTC: ' +
                'yyyy-mm-ddTHH:MM:SS.nnnnnnnnnZ, where the fraction may ' +
                'have from 1 to 9 digits, and the time may stop after the ' +
                'hours, the minutes or the seconds, or be left out, as in ' +
                '2026-10-17Z. Second 60, for a leap second, is read as ' +
                'second 59. Without --digits, tc and bc show the full ' +
                'precision of the time.',
        )
    }`,
    help: 'both',
    version: version,
    completion: 'both',
})

const line = (() => {
    switch (config.command) {
        case 'tc5':
            return config.time.tc5()
        case 'tc8':
            return (config.time ?? TimeCode.now()).tc8()
        case 'tcx':
            return config.time.tcX(config.precision)
        case 'tc':
            return config.time.display(config.digits)
        case 'bc4':
            return config.time.bc4()
        case 'bc':
            return config.time.bc(config.digits)
        case 'parse': {
            const [input, t] = config.code
            const time = t.toInstant().toString()

            return config.json
                ? JSON.stringify({
                    code: input,
                    time: time,
                    descriptor: t.descriptor,
                })
                : time
        }
    }
})()

console.log(line)
