"""Checks the package against the specification's cases, read through
tests/tests.yaml, a link to them, and checks what the cases leave out.
"""

import time
from datetime import UTC, datetime, timedelta, timezone
from pathlib import Path

import pytest
import yaml

import timecode

SUITE = yaml.safe_load((Path(__file__).parent / 'tests.yaml').read_text())

NO_YEAR_0 = 'A datetime has no year 0.'
NO_LEAP_SECONDS = 'A datetime has no leap seconds.'


def cases(groups, direction):
    """The cases of groups in one direction, as parameters of a test, named
    after their groups, inputs and outputs.
    """
    return [
        pytest.param(case, id=f'{group}:{case["input"]}={case["output"]}')
        for group in groups
        for case in SUITE[group][direction]
    ]


def time_at(timestamp):
    """The time of a timestamp in UTC, with the fraction of the second cut
    off after six digits, as a datetime keeps microseconds. A datetime cannot
    hold a leap second, 23:59:60, so it is read as 23:59:59.
    """
    whole, _, fraction = timestamp.removesuffix('Z').partition('.')
    t = datetime.fromisoformat(whole.replace(':60', ':59'))

    return t.replace(tzinfo=UTC) + timedelta(
        microseconds=int(fraction.ljust(6, '0')[:6])
    )


def at_microseconds(code):
    """The code of a time that keeps only microseconds: past the sixth digit
    of its fraction of the second, its digits are 0.
    """
    point = code.find('.')

    return code if point < 0 else code[: point + 7].ljust(len(code), '0')


def by_length(t, length):
    """The code of a time from the function that gives a code of a length by
    name, if there is one.
    """
    if length == 5:
        return timecode.tc5(t)

    if length == 8:
        return timecode.tc8(t)

    if length == 10:
        return timecode.tcx(t)

    if length >= 12:
        return timecode.tcx(t, length - 11)

    return None


def assert_time(actual, expected):
    """Checks a time that a function gave back, and that it is in UTC."""
    assert actual == expected
    assert actual.tzinfo is UTC


@pytest.mark.parametrize('case', cases(['tc5', 'tc8', 'tcx', 'tcn'], 'decode'))
def test_parse(case):
    if case['input'].startswith('0000'):
        with pytest.raises(ValueError, match='cannot hold the year 0000'):
            timecode.parse(case['input'])
    else:
        assert_time(timecode.parse(case['input']), time_at(case['output']))


@pytest.mark.parametrize('case', cases(['tc5', 'tc8', 'tcx', 'tcn'], 'encode'))
def test_encode(case):
    """Each code is given by encode, and by the function that gives a code of
    its length by name, if there is one.
    """
    if case['input'].startswith('0000'):
        pytest.skip(NO_YEAR_0)

    t = time_at(case['input'])
    n = len(case['output'])
    expected = at_microseconds(case['output'])
    code = by_length(t, n)

    assert timecode.encode(t, n) == expected

    if code is not None:
        assert code == expected


@pytest.mark.parametrize(
    'case',
    [
        pytest.param(case, id=f'{case["input"]}~{case["near"]}')
        for case in SUITE['bc']['decode']
    ],
)
def test_parse_bc(case):
    near = time_at(case['near'])

    assert_time(
        timecode.parse_bc(case['input'], near), time_at(case['output'])
    )


@pytest.mark.parametrize('case', cases(['bc'], 'encode'))
def test_bc(case):
    if case['input'].startswith('0000'):
        pytest.skip(NO_YEAR_0)

    t = time_at(case['input'])
    n = len(case['output'])
    expected = at_microseconds(case['output'])

    assert timecode.bc(t, n) == expected

    if n == 4:
        assert timecode.bc4(t) == expected


@pytest.mark.parametrize('case', cases(['leap-seconds'], 'decode'))
def test_parse_leap_second(case):
    """A leap second code, like any code past the end of its half month, is
    parsed as the last second of the half month.
    """
    assert_time(timecode.parse(case['input']), time_at(case['output']))


@pytest.mark.parametrize('case', cases(['leap-seconds'], 'encode'))
@pytest.mark.skip(reason=NO_LEAP_SECONDS)
def test_encode_leap_second(case):
    """A datetime cannot hold 23:59:60, so leap seconds cannot be encoded."""


@pytest.mark.parametrize(
    ('timestamp', 'code', 'bc'),
    [
        ('2026-10-17T14:23:45Z', '2026VDJBD5', 'C6VDJBD5'),
        ('2026-10-17T14:23:45.25Z', '2026VDJBD5.25', 'C6VDJBD5.25'),
        (
            '2026-10-17T14:23:45.000001Z',
            '2026VDJBD5.000001',
            'C6VDJBD5.000001',
        ),
    ],
)
def test_precision_of_the_time(timestamp, code, bc):
    """Without a length, a code has the digits of the fraction of the second
    up to the last that is not 0, if any.
    """
    t = time_at(timestamp)

    assert timecode.encode(t) == code
    assert timecode.bc(t) == bc


def test_tcx_without_a_precision():
    t = time_at('2026-10-17T14:23:45.25Z')

    assert timecode.tcx(t) == '2026VDJBD5'


def test_tc11_and_bc9():
    """A code is never cut off just after its point."""
    t = time_at('2026-10-17T14:23:45.25Z')

    assert timecode.encode(t, 11) == '2026VDJBD5.2'
    assert timecode.bc(t, 9) == 'C6VDJBD5.2'


def test_time_zone():
    """A time is encoded in UTC, whatever its time zone."""
    kolkata = timezone(timedelta(hours=5, minutes=30))
    t = datetime(2026, 10, 17, 19, 53, 45, tzinfo=kolkata)

    assert timecode.tcx(t) == '2026VDJBD5'
    assert timecode.bc4(t) == 'C6VD'


def test_local_time(monkeypatch):
    """A datetime without a time zone is in local time."""
    monkeypatch.setenv('TZ', 'IST-5:30')
    time.tzset()

    try:
        assert timecode.tcx(datetime(2026, 10, 17, 19, 53, 45)) == '2026VDJBD5'
    finally:
        monkeypatch.undo()
        time.tzset()


def test_years():
    """A datetime has the years 1 to 9999. Every year has a bicentennial
    code, but a datetime cannot hold every year it may be read as.
    """
    first = datetime(1, 1, 1, tzinfo=UTC)
    last = datetime(9999, 12, 31, 23, 59, 59, 999999, tzinfo=UTC)

    assert timecode.tcx(first) == '0001ABBBB0'
    assert timecode.encode(last) == '9999ZZNLZ9.999999'
    assert timecode.bc(first, 8) == 'A1ABBBB0'
    assert timecode.bc(last, 8) == 'T9ZZNLZ9'

    with pytest.raises(ValueError, match='cannot hold the year 10026'):
        timecode.parse_bc('C6', datetime(9950, 1, 1, tzinfo=UTC))


def test_near_the_present():
    """Without a time to read it near, a bicentennial code is read near the
    present.
    """
    now = datetime.now(UTC)

    assert timecode.parse_bc(timecode.bc4(now)).year == now.year


@pytest.mark.parametrize(
    ('code', 'error'),
    [
        ('', 'TimeCodes have from 4 to 10 places'),
        ('202', 'TimeCodes have from 4 to 10 places'),
        ('2026VDJBD50', 'TimeCodes have from 4 to 10 places'),
        ('2026VDJBD5 ', 'TimeCodes have from 4 to 10 places'),
        ('2026M', "'M' is not allowed in P5"),
        ('2026vdjb', "'v' is not allowed in P5"),
        ('C6VD', "'C' is not allowed in P1"),
        ('2026V.5', 'Only a TCX may have a fraction'),
        ('2026VDJBD.5', 'Only a TCX may have a fraction'),
        ('2026VDJBD5.1.2', 'TimeCodes have at most one decimal point'),
        ('2026VDJBD5.', 'Fractions of the second have 1 to 9 digits'),
        ('2026VDJBD5.1234567890', 'Fractions of the second have 1 to 9'),
        ('2026VDJBD5.-1', 'Fractions of the second have 1 to 9 digits'),
        ('2026VDJBD5.2a', 'Fractions of the second have 1 to 9 digits'),
        ('2026VDJBD5.2\n', 'Fractions of the second have 1 to 9 digits'),
        ('2026VDJBD5.٣', 'Fractions of the second have 1 to 9 digits'),
    ],
)
def test_parse_errors(code, error):
    with pytest.raises(ValueError, match=error):
        timecode.parse(code)


@pytest.mark.parametrize(
    ('code', 'error'),
    [
        ('', 'Bicentennial codes have from 2 to 8 places'),
        ('C', 'Bicentennial codes have from 2 to 8 places'),
        ('C6VDJBD50', 'Bicentennial codes have from 2 to 8 places'),
        ('U6', "'U' is not allowed in B1"),
        ('26VD', "'2' is not allowed in B1"),
        ('c6', "'c' is not allowed in B1"),
        ('C6M', "'M' is not allowed in P5"),
        ('C6VDJB.5', 'Only a BC8 may have a fraction'),
        ('C6VDJBD5.', 'Fractions of the second have 1 to 9 digits'),
        ('C6VDJBD5.1234567890', 'Fractions of the second have 1 to 9'),
    ],
)
def test_parse_bc_errors(code, error):
    with pytest.raises(ValueError, match=error):
        timecode.parse_bc(code)


@pytest.mark.parametrize('digits', [-1, 0, 1, 3, 21])
def test_encode_length_errors(digits):
    with pytest.raises(ValueError, match='TimeCodes have from 4 to 20'):
        timecode.encode(datetime.now(UTC), digits)


@pytest.mark.parametrize('precision', [-1, 10])
def test_tcx_precision_errors(precision):
    with pytest.raises(ValueError, match='A TCX has from 0 to 9 digits'):
        timecode.tcx(datetime.now(UTC), precision)


@pytest.mark.parametrize('digits', [-1, 0, 1, 19])
def test_bc_length_errors(digits):
    with pytest.raises(ValueError, match='Bicentennial codes have from 2'):
        timecode.bc(datetime.now(UTC), digits)


def test_lengths_are_integers():
    t = datetime.now(UTC)
    error = 'cannot be interpreted as an integer'

    with pytest.raises(TypeError, match=error):
        timecode.encode(t, 8.0)

    with pytest.raises(TypeError, match=error):
        timecode.tcx(t, 1.5)

    with pytest.raises(TypeError, match=error):
        timecode.bc(t, 4.0)
