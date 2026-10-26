# frozen_string_literal: true

require 'minitest/autorun'
require 'time'
require 'yaml'

require 'timecode'

# Checks the module against the specification's cases, read through
# test/tests.yaml, a link to them, and checks what the cases leave out.
class TimeCodeTest < Minitest::Test
    SUITE = YAML.load_file(File.join(__dir__, 'tests.yaml'))

    %w[tc5 tc8 tcx tcn].each do |group|
        SUITE[group]['decode'].each do |c|
            define_method("test_parse_#{group}_#{c['input']}") do
                assert_time time_at(c['output']), TimeCode.parse(c['input'])
            end
        end

        # Each code is given by TimeCode.encode, and by the function that
        # gives a code of its length by name, if there is one.
        SUITE[group]['encode'].each do |c|
            name = "test_encode_#{group}_#{c['input']}_#{c['output']}"

            define_method(name) do
                time = time_at(c['input'])
                n = c['output'].size
                code = by_name(group, time, n)

                assert_equal c['output'], TimeCode.encode(time, n)
                assert_equal c['output'], code unless code.nil?
            end
        end
    end

    SUITE['bc']['decode'].each do |c|
        define_method("test_parse_bc_#{c['input']}_near_#{c['near']}") do
            near = time_at(c['near'])

            assert_time time_at(c['output']),
                        TimeCode.parse_bc(c['input'], near)
        end
    end

    SUITE['bc']['encode'].each do |c|
        define_method("test_bc_#{c['input']}_#{c['output']}") do
            time = time_at(c['input'])
            n = c['output'].size

            assert_equal c['output'], TimeCode.bc(time, n)
            assert_equal c['output'], TimeCode.bc4(time) if n == 4
        end
    end

    # A leap second code, like any code past the end of its half month, is
    # parsed as the last second of the half month.
    SUITE['leap-seconds']['decode'].each do |c|
        define_method("test_parse_leap_second_#{c['input']}") do
            assert_time time_at(c['output']), TimeCode.parse(c['input'])
        end
    end

    SUITE['leap-seconds']['encode'].each do |c|
        define_method("test_encode_leap_second_#{c['input']}") do
            skip 'A Time has no leap seconds: it reads 23:59:60 as the ' \
                 'next second.'
        end
    end

    # Without a length, a code has the digits of the fraction of the second
    # up to the last that is not 0, if any.
    def test_precision_of_the_time
        {
            '2026-10-17T14:23:45Z' => %w[2026VDJBD5 C6VDJBD5],
            '2026-10-17T14:23:45.25Z' => %w[2026VDJBD5.25 C6VDJBD5.25],
            '2026-10-17T14:23:45.000000001Z' =>
                %w[2026VDJBD5.000000001 C6VDJBD5.000000001]
        }.each do |timestamp, (code, bc)|
            time = time_at(timestamp)

            assert_equal code, TimeCode.encode(time)
            assert_equal bc, TimeCode.bc(time)
        end
    end

    def test_tcx_without_a_precision
        time = time_at('2026-10-17T14:23:45.25Z')

        assert_equal '2026VDJBD5', TimeCode.tcx(time)
    end

    # A code is never cut off just after its point.
    def test_tc11_and_bc9
        time = time_at('2026-10-17T14:23:45.25Z')

        assert_equal '2026VDJBD5.2', TimeCode.encode(time, 11)
        assert_equal 'C6VDJBD5.2', TimeCode.bc(time, 9)
    end

    # A time is encoded in UTC, whatever its offset.
    def test_offset
        time = Time.new(2026, 10, 17, 19, 53, 45, '+05:30')

        assert_equal '2026VDJBD5', TimeCode.tcx(time)
        assert_equal 'C6VD', TimeCode.bc4(time)
    end

    # TimeCodes have four digit years, and every year has a bicentennial
    # code, which can be read near any year.
    def test_years
        first = Time.utc(10_000)
        last = Time.utc(-1, 12, 31, 23, 59, 59)

        assert_raises(RangeError) { TimeCode.encode(first, 10) }
        assert_raises(RangeError) { TimeCode.encode(last, 10) }
        assert_equal 'A0ABBBB0', TimeCode.bc(first, 8)
        assert_equal 'T9ZZNLZ9', TimeCode.bc(last, 8)
        assert_time Time.utc(10_026), TimeCode.parse_bc('C6', Time.utc(9950))
    end

    # Without a time to read it near, a bicentennial code is read near the
    # present.
    def test_near_the_present
        now = Time.now

        assert_equal now.getutc.year, TimeCode.parse_bc(TimeCode.bc4(now)).year
    end

    def test_parse_errors
        [
            '', '202', '2026VDJBD50', '2026VDJBD5 ', '2026M', '2026vdjb',
            'C6VD', '2026V.5', '2026VDJBD.5', '2026VDJBD5.',
            '2026VDJBD5.1234567890', '2026VDJBD5.1.2', '2026VDJBD5.-1',
            '2026VDJBD5.2a', "2026VDJBD5.2\n"
        ].each do |code|
            assert_raises(ArgumentError, code.inspect) { TimeCode.parse(code) }
        end
    end

    def test_parse_bc_errors
        [
            '', 'C', 'C6VDJBD50', 'U6', '26VD', 'C6M', 'c6', 'C6VDJB.5',
            'C6VDJBD5.', 'C6VDJBD5.1234567890'
        ].each do |code|
            assert_raises(ArgumentError, code.inspect) do
                TimeCode.parse_bc(code)
            end
        end
    end

    def test_length_errors
        time = Time.now

        [-1, 0, 1, 3, 21, 8.0].each do |n|
            assert_raises(ArgumentError) { TimeCode.encode(time, n) }
        end

        [-1, 10, 1.5].each do |n|
            assert_raises(ArgumentError) { TimeCode.tcx(time, n) }
        end

        [-1, 0, 1, 19].each do |n|
            assert_raises(ArgumentError) { TimeCode.bc(time, n) }
        end
    end

    private

    # The time of a timestamp in UTC. A Time cannot hold a leap second,
    # 23:59:60, so it is read as 23:59:59.
    def time_at(timestamp)
        Time.iso8601(timestamp.sub(':60Z', ':59Z'))
    end

    # The code of a time from the function that gives a code of a group's
    # length by name, if there is one.
    def by_name(group, time, length)
        case group
        when 'tc5' then TimeCode.tc5(time)
        when 'tc8' then TimeCode.tc8(time)
        when 'tcx' then TimeCode.tcx(time)
        else TimeCode.tcx(time, length - 11) if length >= 12
        end
    end

    # Checks a time that a function gave back, and that it is in UTC.
    def assert_time(expected, actual)
        assert_equal expected, actual
        assert_predicate actual, :utc?
    end
end
