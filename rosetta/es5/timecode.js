// TimeCodes in ECMAScript 5, as described in specification/index.md.
//
// timecode.encode(t, digits) gives the TimeCode of a time, with the number
// of digits asked for, from 4 to 20, or with the precision of the time.
// The time is a Date, or an RFC 3339 timestamp in UTC, such as
// 2026-10-17T14:23:45.25Z, with up to nine digits of the fraction of the
// second.
//
// timecode.tc5(t), timecode.tc8(t) and timecode.tcx(t, precision) give the
// TC5, TC8 and TCX of a time. The TCX may have up to nine digits of the
// fraction of the second.
//
// timecode.decode(code) gives the time a TimeCode of any length stands for,
// as an RFC 3339 timestamp in UTC, with as many digits of the fraction of
// the second as needed. A Date holds only milliseconds, so a timestamp keeps
// the precision of the code.
//
// Neither needs a Date to do its work: the calendar is reckoned here, the
// same as in Temporal, so the years 0000 to 9999 all work alike.
//
// timecode.SPEC_VERSION is the revision of the specification that this
// port follows. It has no bicentennial codes, which came in 1.1.0.
;(function (root) {
    'use strict'

    var decimal = '0123456789'
    var p5 = 'ABCDEFGHIJKLOPQRSTUVWXYZ'
    var p6 = 'BDFGHJKLMPQRSTVWXYZ'
    var p7 = 'BCDFGHJKLMNPQRVWXYZ'
    var p8 = 'BCDFGHJLMNPQRSVWXYZ'
    var p9 = 'BCDEFHIJLMNOPQUVWXYZ'

    // The start of the second half month in seconds. 372 hours is 15.5 days.
    var hm2Begin = 372 * 3600

    var timestamp = new RegExp(
        '^([0-9]{4})-([0-9]{2})-([0-9]{2})' +
            'T([0-9]{2}):([0-9]{2}):([0-9]{2})(?:\\.([0-9]{1,9}))?Z$'
    )

    function padStart(s, length, c) {
        while (s.length < length) {
            s = c + s
        }

        return s
    }

    function twoDigits(n) {
        return padStart(String(n), 2, '0')
    }

    function padEnd(s, length, c) {
        while (s.length < length) {
            s = s + c
        }

        return s
    }

    function daysInMonth(year, month) {
        var isLeapYear = (year % 4 === 0 && year % 100 !== 0) ||
            year % 400 === 0

        if (month === 2) {
            return isLeapYear ? 29 : 28
        }

        return [4, 6, 9, 11].indexOf(month) >= 0 ? 30 : 31
    }

    // The fields of a time in UTC, from a Date or a timestamp.
    function fieldsOf(t) {
        var fields
        var match

        if (t instanceof Date) {
            if (isNaN(t.getTime())) {
                throw new RangeError('Not a valid Date.')
            }

            return {
                year: t.getUTCFullYear(),
                month: t.getUTCMonth() + 1,
                day: t.getUTCDate(),
                hour: t.getUTCHours(),
                minute: t.getUTCMinutes(),
                second: t.getUTCSeconds(),
                nanoseconds: t.getUTCMilliseconds() * 1000000,
            }
        }

        match = timestamp.exec(t)

        if (!match) {
            throw new RangeError('Not a timestamp in UTC: ' + t + '.')
        }

        fields = {
            year: Number(match[1]),
            month: Number(match[2]),
            day: Number(match[3]),
            hour: Number(match[4]),
            minute: Number(match[5]),
            // Second 60, for a leap second, is read as second 59, the same as
            // Temporal reads it.
            second: Math.min(Number(match[6]), 59),
            nanoseconds: match[7] ? Number(padEnd(match[7], 9, '0')) : 0,
        }

        if (
            fields.month < 1 || fields.month > 12 ||
            fields.day < 1 ||
            fields.day > daysInMonth(fields.year, fields.month) ||
            fields.hour > 23 || fields.minute > 59 || Number(match[6]) > 60
        ) {
            throw new RangeError('Not a timestamp in UTC: ' + t + '.')
        }

        return fields
    }

    function encode(t, digits) {
        var fields = fieldsOf(t)
        var code

        if (fields.year < 0 || fields.year > 9999) {
            throw new RangeError('TimeCodes support four digit years.')
        }

        var locInMonth = ((fields.day - 1) * 24 * 3600) +
            (fields.hour * 3600) +
            (fields.minute * 60) +
            fields.second

        // Calculate offsets needed if we are in the second half-month.
        var corrections = locInMonth < hm2Begin ? [0, 0] : [1, -hm2Begin]

        // Calculate the half-month index.
        var halfMonth = (2 * (fields.month - 1)) + corrections[0]

        // Calculate the block of 200s and the seconds in the block.
        var locInHalfMonth = locInMonth + corrections[1]
        var block = Math.floor(locInHalfMonth / 200)
        var second = locInHalfMonth % 200

        code = padStart(String(fields.year), 4, '0') +
            p5.charAt(halfMonth) +
            p6.charAt(Math.floor(block / (19 * 19))) +
            p7.charAt(Math.floor(block / 19) % 19) +
            p8.charAt(block % 19) +
            p9.charAt(Math.floor(second / 10)) +
            String(second % 10) +
            '.' + padStart(String(fields.nanoseconds), 9, '0')

        if (digits === undefined) {
            return code.replace(/\.?0+$/, '')
        }

        if (
            typeof digits !== 'number' || digits % 1 !== 0 ||
            digits < 4 || digits > 20
        ) {
            throw new RangeError('TimeCodes have from 4 to 20 digits.')
        }

        return code.slice(0, digits === 11 ? 12 : digits)
    }

    function tc5(t) {
        return encode(t, 5)
    }

    function tc8(t) {
        return encode(t, 8)
    }

    function tcx(t, precision) {
        var p = precision === undefined ? 0 : precision

        if (typeof p !== 'number' || p % 1 !== 0 || p < 0 || p > 9) {
            throw new RangeError('The precision must be from 0 to 9 digits.')
        }

        return encode(t, p === 0 ? 10 : 11 + p)
    }

    function decode(code) {
        var parts = String(code).split('.')
        var places = parts[0]
        var fraction = parts[1]

        if (places.length < 4 || places.length > 10) {
            throw new RangeError('TimeCodes have from 4 to 10 places.')
        }

        if (parts.length > 2) {
            throw new RangeError('TimeCodes have at most one decimal point.')
        }

        if (fraction !== undefined && places.length < 10) {
            throw new RangeError(
                'Only a TCX may have a fraction of the second.'
            )
        }

        if (fraction !== undefined && !/^[0-9]{1,9}$/.test(fraction)) {
            throw new RangeError('Fractions of the second have 1 to 9 digits.')
        }

        // The digit a place stands for. Places that are not present count as
        // 0.
        function digit(chars, i) {
            var n

            if (i >= places.length) {
                return 0
            }

            n = chars.indexOf(places.charAt(i))

            if (n < 0) {
                throw new RangeError(
                    places.charAt(i) + ' is not allowed in ' +
                        (i < 9 ? 'P' + (i + 1) : 'PX') + '.'
                )
            }

            return n
        }

        var year = (1000 * digit(decimal, 0)) +
            (100 * digit(decimal, 1)) +
            (10 * digit(decimal, 2)) +
            digit(decimal, 3)
        var halfMonth = digit(p5, 4)
        var block = (19 * 19 * digit(p6, 5)) +
            (19 * digit(p7, 6)) +
            digit(p8, 7)
        var second = (10 * digit(p9, 8)) + digit(decimal, 9)

        var month = Math.floor(halfMonth / 2) + 1
        var isSecondHalf = halfMonth % 2 === 1

        // The second half month ends with the month, so it is shorter in
        // months of fewer than 31 days.
        var hmBegin = isSecondHalf ? hm2Begin : 0
        var hmLength = isSecondHalf
            ? (daysInMonth(year, month) * 24 * 3600) - hm2Begin
            : hm2Begin

        var locInHalfMonth = (block * 200) + second

        // Codes past the end of the half month, like those of leap seconds,
        // are clipped to its last second. Temporal does the same with
        // 23:59:60.
        var locInMonth = hmBegin + Math.min(locInHalfMonth, hmLength - 1)

        var subseconds = fraction === undefined
            ? ''
            : padEnd(fraction, 9, '0').replace(/0+$/, '')

        var day = Math.floor(locInMonth / 86400) + 1
        var hour = Math.floor(locInMonth / 3600) % 24
        var minute = Math.floor(locInMonth / 60) % 60

        return padStart(String(year), 4, '0') +
            '-' + twoDigits(month) +
            '-' + twoDigits(day) +
            'T' + twoDigits(hour) +
            ':' + twoDigits(minute) +
            ':' + twoDigits(locInMonth % 60) +
            (subseconds ? '.' + subseconds : '') +
            'Z'
    }

    var timecode = {
        SPEC_VERSION: '1.0.0',
        encode: encode,
        tc5: tc5,
        tc8: tc8,
        tcx: tcx,
        decode: decode,
    }

    if (typeof module === 'object' && module.exports) {
        module.exports = timecode
    } else {
        root.timecode = timecode
    }
})(this)
