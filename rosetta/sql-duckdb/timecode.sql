-- TimeCodes in DuckDB, as described in specification/index.md.
--
-- timecode(t, digits) gives the TimeCode of a timestamptz, with the number
-- of digits asked for, from 4 to 20, or with the precision of the time.
--
-- tc5(t), tc8(t) and tcx(t, fraction_digits) give the TC5, TC8 and TCX of a
-- timestamptz. The TCX may have up to nine digits of the fraction of the
-- second.
--
-- timecode_parse(code) gives the timestamptz a TimeCode of any length stands
-- for.
--
-- The macros cast what they are given to a timestamptz, as a function that
-- takes one would, so a timestamp is read in the session's time zone. The
-- session's time zone makes no other difference.
--
-- A timestamptz holds microseconds, so a TimeCode made here has at most six
-- digits of the fraction of the second that are not 0, and the fraction of
-- a code that is read here is cut off after six digits. Like the
-- specification, DuckDB counts the year 0000, which it prints as 0001 (BC).

CREATE OR REPLACE MACRO timecode(t, digits := NULL) AS (
  -- The time in UTC. The fields of a timestamptz, like year(t), are in the
  -- session's time zone, but its epoch is not.
  WITH times AS (
         SELECT make_timestamp(epoch_us(t::timestamptz)) AS utc
       ),
       fields AS (
         SELECT year(utc) AS year_number,
                month(utc) AS month_number,
                ((day(utc) - 1) * 24 * 3600)
                  + (hour(utc) * 3600)
                  + (minute(utc) * 60)
                  + second(utc) AS loc_in_month,
                (microsecond(utc) % 1000000) * 1000 AS nanoseconds
           FROM times
       ),
       -- The second half month begins 372 hours, or 15.5 days, after the
       -- month.
       half_months AS (
         SELECT year_number,
                ((month_number - 1) * 2)
                  + CASE WHEN loc_in_month < 372 * 3600 THEN 0 ELSE 1 END
                  AS half_month,
                loc_in_month
                  - CASE WHEN loc_in_month < 372 * 3600
                         THEN 0
                         ELSE 372 * 3600
                     END AS loc_in_half_month,
                nanoseconds
           FROM fields
       ),
       blocks AS (
         SELECT year_number,
                half_month,
                loc_in_half_month // 200 AS block,
                loc_in_half_month % 200 AS second_in_block,
                nanoseconds
           FROM half_months
       ),
       -- A full length (TC20) code.
       codes AS (
         SELECT year_number,
                lpad(year_number::varchar, 4, '0')
                  || substr('ABCDEFGHIJKLOPQRSTUVWXYZ', half_month + 1, 1)
                  || substr('BDFGHJKLMPQRSTVWXYZ', (block // 361) + 1, 1)
                  || substr('BCDFGHJKLMNPQRVWXYZ',
                            ((block // 19) % 19) + 1,
                            1)
                  || substr('BCDFGHJLMNPQRSVWXYZ', (block % 19) + 1, 1)
                  || substr('BCDEFHIJLMNOPQUVWXYZ',
                            (second_in_block // 10) + 1,
                            1)
                  || (second_in_block % 10)::varchar
                  || '.'
                  || lpad(nanoseconds::varchar, 9, '0') AS code
           FROM blocks
       )
  SELECT CASE
           WHEN year_number NOT BETWEEN 0 AND 9999
           THEN error('TimeCodes support four digit years.')
           WHEN digits IS NULL
           THEN rtrim(rtrim(code, '0'), '.')
           WHEN digits NOT BETWEEN 4 AND 20
           THEN error('TimeCodes have from 4 to 20 digits.')
           ELSE left(code, CASE WHEN digits = 11 THEN 12 ELSE digits END)
         END
    FROM codes
);

CREATE OR REPLACE MACRO tc5(t) AS timecode(t, 5);

CREATE OR REPLACE MACRO tc8(t) AS timecode(t, 8);

CREATE OR REPLACE MACRO tcx(t, fraction_digits := 0) AS
  CASE
    WHEN fraction_digits NOT BETWEEN 0 AND 9
    THEN error('The precision must be from 0 to 9 digits.')
    ELSE timecode(t, CASE WHEN fraction_digits = 0
                          THEN 10
                          ELSE 11 + fraction_digits
                      END)
  END;

-- The digit a place of a code stands for, counting the places from 1.
-- Places that are not present count as 0.
CREATE OR REPLACE MACRO timecode_digit(places, chars, place) AS
  CASE
    WHEN place > length(places)
    THEN 0
    WHEN strpos(chars, substr(places, place, 1)) = 0
    THEN error(substr(places, place, 1)
               || ' is not allowed in '
               || CASE WHEN place < 10 THEN 'P' || place ELSE 'PX' END
               || '.')
    ELSE strpos(chars, substr(places, place, 1)) - 1
  END;

CREATE OR REPLACE MACRO timecode_parse(code) AS (
  WITH parts AS (
         SELECT split_part(code, '.', 1) AS places,
                CASE WHEN contains(code, '.')
                     THEN split_part(code, '.', 2)
                 END AS fraction
       ),
       checked AS (
         SELECT CASE
                  WHEN length(places) NOT BETWEEN 4 AND 10
                  THEN error('TimeCodes have from 4 to 10 places.')
                  WHEN length(code) - length(replace(code, '.', '')) > 1
                  THEN error('TimeCodes have at most one decimal point.')
                  WHEN fraction IS NOT NULL AND length(places) < 10
                  THEN error('Only a TCX may have a fraction of the second.')
                  WHEN NOT regexp_full_match(fraction, '[0-9]{1,9}')
                  THEN error('Fractions of the second have 1 to 9 digits.')
                  ELSE places
                END AS places,
                fraction
           FROM parts
       ),
       -- First, the four components: year, half month, block and seconds.
       -- A timestamp holds microseconds, so the rest of the fraction is
       -- cut off.
       components AS (
         SELECT (1000 * timecode_digit(places, '0123456789', 1))
                  + (100 * timecode_digit(places, '0123456789', 2))
                  + (10 * timecode_digit(places, '0123456789', 3))
                  + timecode_digit(places, '0123456789', 4) AS year_number,
                timecode_digit(places, 'ABCDEFGHIJKLOPQRSTUVWXYZ', 5)
                  AS half_month,
                (19 * 19 * timecode_digit(places, 'BDFGHJKLMPQRSTVWXYZ', 6))
                  + (19 * timecode_digit(places, 'BCDFGHJKLMNPQRVWXYZ', 7))
                  + timecode_digit(places, 'BCDFGHJLMNPQRSVWXYZ', 8)
                  AS block,
                (10 * timecode_digit(places, 'BCDEFHIJLMNOPQUVWXYZ', 9))
                  + timecode_digit(places, '0123456789', 10)
                  AS second_in_block,
                COALESCE(left(rpad(fraction, 9, '0'), 6)::integer, 0)
                  AS microseconds
           FROM checked
       ),
       months AS (
         SELECT make_timestamp(year_number,
                               (half_month // 2) + 1,
                               1,
                               0,
                               0,
                               0) AS m_begin,
                half_month,
                block,
                second_in_block,
                microseconds
           FROM components
       ),
       -- The second half month begins 372 hours, or 15.5 days, after the
       -- month, and ends with the month, so it is shorter in months of
       -- fewer than 31 days.
       half_months AS (
         SELECT m_begin,
                CASE WHEN half_month % 2 = 1 THEN 372 * 3600 ELSE 0 END
                  AS hm_begin,
                CASE WHEN half_month % 2 = 1
                     THEN (day(last_day(m_begin)) * 24 * 3600) - (372 * 3600)
                     ELSE 372 * 3600
                 END AS hm_length,
                block,
                second_in_block,
                microseconds
           FROM months
       )
  -- Codes past the end of the half month, like those of leap seconds, are
  -- clipped to its last second. The time is in UTC, so it is made into a
  -- timestamptz by way of its epoch.
  SELECT make_timestamptz(
           epoch_us(m_begin
                    + to_seconds(hm_begin
                                 + LEAST((block * 200) + second_in_block,
                                         hm_length - 1))
                    + to_microseconds(microseconds)))
    FROM half_months
);
