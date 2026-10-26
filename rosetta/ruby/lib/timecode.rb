# frozen_string_literal: true

require 'date'

require_relative 'timecode/version'

# TimeCodes, sortable, short timestamps of digits and capital letters, and
# the bicentennial codes made from them, as revision 1.1.0 of the TimeCode
# specification describes them.
#
# TimeCode.encode gives the TimeCode of a time, with the number of
# characters asked for or with the precision of the time, and TimeCode.tc5,
# TimeCode.tc8 and TimeCode.tcx give the codes the specification names.
# TimeCode.parse gives the time a TimeCode of any length stands for.
# TimeCode.bc, TimeCode.bc4 and TimeCode.parse_bc do the same for
# bicentennial codes, which write only the place of the year in a cycle of
# 200 years, and so are read near a time.
#
# A Time keeps nanoseconds, and the years 0000 to 9999, so every TimeCode
# can be encoded and parsed. It has no leap seconds, so a code past the end
# of its half month, like that of a leap second, is parsed as the last
# second of the half month.
module TimeCode
    # The revision of the TimeCode specification that the module follows.
    SPEC_VERSION = '1.1.0'

    # The characters allowed in each place, which stand for the digits 0, 1,
    # 2 and so on, in that order.
    DECIMAL = '0123456789'
    B1 = 'ABCDEFGHIJKLMNOPQRST'
    P5 = 'ABCDEFGHIJKLOPQRSTUVWXYZ'
    P6 = 'BDFGHJKLMPQRSTVWXYZ'
    P7 = 'BCDFGHJKLMNPQRVWXYZ'
    P8 = 'BCDFGHJLMNPQRSVWXYZ'
    P9 = 'BCDEFHIJLMNOPQUVWXYZ'

    # A kind of code: what the codes are called, what the longest of them is
    # called, and their places, by name, with the characters allowed in each.
    Kind = Data.define(:name, :longest, :places)

    TIMECODE = Kind.new(
        name: 'TimeCodes',
        longest: 'TCX',
        places: {
            'P1' => DECIMAL,
            'P2' => DECIMAL,
            'P3' => DECIMAL,
            'P4' => DECIMAL,
            'P5' => P5,
            'P6' => P6,
            'P7' => P7,
            'P8' => P8,
            'P9' => P9,
            'PX' => DECIMAL
        }.freeze
    )

    # A bicentennial code writes the place of its year in the cycle in B1
    # and B2, where a TimeCode writes the year in P1 to P4.
    BICENTENNIAL = Kind.new(
        name: 'Bicentennial codes',
        longest: 'BC8',
        places: { 'B1' => B1, 'B2' => DECIMAL }
            .merge(TIMECODE.places.drop(4).to_h)
            .freeze
    )

    # The start of the second half month in seconds. 372 hours is 15.5 days.
    HM2_BEGIN = 372 * 3600

    # The components of a time in UTC, from the most significant to the
    # least: the year, the half month, the block of 200 seconds within the
    # half month and the second within the block; and the nanoseconds of the
    # second.
    Components = Data.define(
        :year, :half_month, :block, :second, :nanoseconds
    )

    private_constant :DECIMAL, :B1, :P5, :P6, :P7, :P8, :P9, :Kind,
                     :TIMECODE, :BICENTENNIAL, :HM2_BEGIN, :Components

    class << self
        # The TimeCode of a time in UTC, with a number of characters from 4
        # to 20, or, without one, with the precision of the time: a TCX,
        # followed by the digits of the fraction of the second up to the
        # last that is not 0, if any. As in the specification, a TC11 is
        # written as a TC12. Raises a RangeError if the year is not from 0000
        # to 9999.
        def encode(time, digits = nil)
            check_length(digits, 4..20, TIMECODE)
            c = components_of(time)

            unless (0..9999).cover?(c.year)
                raise RangeError,
                      "TimeCodes support four digit years, not #{c.year}"
            end

            clip(format('%04d', c.year) + after_year(c), digits)
        end

        # The TC5 of a time: its year and half month.
        def tc5(time) = encode(time, 5)

        # The TC8 of a time: its year, half month and block of 200 seconds.
        def tc8(time) = encode(time, 8)

        # The TCX of a time, which goes to the second, followed by a number
        # of digits of the fraction of the second, from 0 to 9.
        def tcx(time, precision = 0)
            unless precision.is_a?(Integer) && (0..9).cover?(precision)
                raise ArgumentError,
                      'A TCX has from 0 to 9 digits of the fraction of ' \
                      "the second, not #{precision.inspect}"
            end

            encode(time, precision.zero? ? 10 : 11 + precision)
        end

        # The time a TimeCode stands for, in UTC: the start of the time it
        # covers, with the places that are not present counted as 0. A
        # TimeCode has from 4 to 10 places, and a TCX may be followed by a
        # point and from 1 to 9 digits of the fraction of the second. A code
        # past the end of its half month, like that of a leap second, stands
        # for the last second of the half month. Raises an ArgumentError if
        # the code is not well formed.
        def parse(code)
            d, nanoseconds = read(code, TIMECODE)
            year = (1000 * d[0]) + (100 * d[1]) + (10 * d[2]) + d[3]

            time_of(year, d.drop(4), nanoseconds)
        end

        # The bicentennial code of a time in UTC, with a number of
        # characters from 2 to 18, or, without one, with the precision of
        # the time, as TimeCode.encode gives it. A BCn is the TC(n+2) of the
        # time with the place of its year in the cycle written in B1 and B2,
        # so a BC9 is written as a BC10. Every year has a bicentennial code.
        def bc(time, digits = nil)
            check_length(digits, 2..18, BICENTENNIAL)
            c = components_of(time)

            # The place of the year in the cycle, from 0 to 199, for any
            # year, as the result of % has the sign of the divisor.
            years = c.year % 200

            clip(B1[years / 10] + DECIMAL[years % 10] + after_year(c), digits)
        end

        # The BC4 of a time: the place of its year in the cycle, its half
        # month, and the first place of its block of 200 seconds.
        def bc4(time) = bc(time, 4)

        # The time a bicentennial code stands for, read near a time, by
        # default the present. Of the years with its place in the cycle, the
        # code stands for the one nearest the year of near in UTC: no more
        # than 100 years before it, and less than 100 years after.
        # Otherwise, it is parsed as TimeCode.parse parses a TimeCode, with
        # from 2 to 8 places, and a fraction of the second only after a BC8.
        def parse_bc(code, near = Time.now)
            d, nanoseconds = read(code, BICENTENNIAL)
            years = (10 * d[0]) + d[1]

            # Of the years with this place in the cycle, the one nearest the
            # year of near: no more than 100 years before it, and less than
            # 100 after.
            earliest = near.getutc.year - 100
            year = earliest + ((years - earliest) % 200)

            time_of(year, d.drop(2), nanoseconds)
        end

        private

        # Raises an ArgumentError unless the length asked for is nil, for
        # the precision of the time, or in a range.
        def check_length(digits, range, kind)
            return if digits.nil?
            return if digits.is_a?(Integer) && range.cover?(digits)

            raise ArgumentError,
                  "#{kind.name} have from #{range.min} to #{range.max} " \
                  "characters, not #{digits.inspect}"
        end

        def components_of(time)
            utc = time.getutc
            loc_in_month = ((utc.day - 1) * 24 * 3600) +
                           (utc.hour * 3600) + (utc.min * 60) + utc.sec

            # The half month, and the time since it began.
            half_month = 2 * (utc.month - 1)
            loc_in_half_month = loc_in_month

            if loc_in_month >= HM2_BEGIN
                half_month += 1
                loc_in_half_month -= HM2_BEGIN
            end

            Components.new(
                year: utc.year,
                half_month: half_month,
                block: loc_in_half_month / 200,
                second: loc_in_half_month % 200,
                nanoseconds: utc.nsec
            )
        end

        # The places of a code after the year, from P5 to PX, followed by a
        # point and all nine digits of the fraction of the second.
        def after_year(components)
            block = components.block
            second = components.second

            [
                P5[components.half_month],
                P6[block / (19 * 19)],
                P7[(block / 19) % 19],
                P8[block % 19],
                P9[second / 10],
                DECIMAL[second % 10],
                format('.%09d', components.nanoseconds)
            ].join
        end

        # A code cut off after a number of characters. A code is never cut
        # off just after its point, but after the first digit of its
        # fraction, as a TC11 is written as a TC12. For nil, it is cut off
        # after the last digit of its fraction that is not 0, or before its
        # point if every digit is 0.
        def clip(code, length)
            point = code.index('.')

            case length
            when nil then code.sub(/[.]?0+\z/, '')
            when point + 1 then code[0, point + 2]
            else code[0, length]
            end
        end

        # The digits that the places of a code stand for, with 0 for those
        # that are not present, and the nanoseconds of its fraction of the
        # second.
        def read(code, kind)
            text, point, fraction = code.partition('.')
            check_form(text, point, fraction, kind)

            digits = kind.places.each_with_index.map do |(name, chars), i|
                next 0 if i >= text.size

                digit = chars.index(text[i])

                if digit.nil?
                    raise ArgumentError,
                          "#{text[i].inspect} is not allowed in #{name}"
                end

                digit
            end

            [digits, fraction.ljust(9, '0').to_i]
        end

        # Raises an ArgumentError unless a code, split at its point, has at
        # least the places of its year, and a fraction of the second only
        # when it has every place.
        def check_form(text, point, fraction, kind)
            most = kind.places.size

            # After the places of the year come six more, P5 to PX.
            fewest = most - 6

            unless (fewest..most).cover?(text.size)
                raise ArgumentError,
                      "#{kind.name} have from #{fewest} to #{most} places, " \
                      "not #{text.size}"
            end

            if fraction.include?('.')
                raise ArgumentError,
                      "#{kind.name} have at most one decimal point"
            end

            return if point.empty?

            if text.size < most
                raise ArgumentError,
                      "Only a #{kind.longest} may have a fraction of the " \
                      'second'
            end

            return if fraction.match?(/\A[0-9]{1,9}\z/)

            raise ArgumentError, 'Fractions of the second have 1 to 9 digits'
        end

        # The time that a year, and the digits of the places of a code from
        # P5 to PX, stand for: the start of the time they cover, and the
        # nanoseconds of its fraction of the second.
        def time_of(year, digits, nanoseconds)
            half_month = digits[0]
            block = (19 * 19 * digits[1]) + (19 * digits[2]) + digits[3]
            second = (10 * digits[4]) + digits[5]

            whole_month = half_month / 2
            is_second_half = half_month.odd?

            m_begin = Time.utc(year, whole_month + 1)

            # The second half month ends with the month, so it is shorter in
            # months of fewer than 31 days. Day -1 is the last of the month.
            days_in_month = Date.new(
                year, whole_month + 1, -1, Date::GREGORIAN
            ).day
            hm_begin = is_second_half ? HM2_BEGIN : 0
            hm_length = if is_second_half
                            (days_in_month * 24 * 3600) - HM2_BEGIN
                        else
                            HM2_BEGIN
                        end

            loc_in_half_month = (block * 200) + second

            # Codes past the end of the half month, like those of leap
            # seconds, are clipped to its last second. Temporal does the same
            # with 23:59:60.
            loc_in_month = hm_begin + [loc_in_half_month, hm_length - 1].min

            m_begin + loc_in_month + Rational(nanoseconds, 1_000_000_000)
        end
    end
end
