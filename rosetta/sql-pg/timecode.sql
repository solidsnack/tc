-- TimeCodes in PostgreSQL, as described in specification/index.md.
--
-- timecode(t, digits) gives the TimeCode of a timestamptz, with the number
-- of digits asked for, from 4 to 20, or with the precision of the time.
--
-- tc5(t), tc8(t) and tcx(t, fraction_digits) give the TC5, TC8 and TCX of a
-- timestamptz. The TCX may have up to nine digits of the fraction of the
-- second.
--
-- timecode_parse(code) gives the time a TimeCode of any length stands for.
--
-- timecode_spec_version() gives the revision of the specification that
-- these functions follow. They have no bicentennial codes, which came in
-- 1.1.0.
--
-- A timestamptz holds microseconds, so a TimeCode made here has at most six
-- digits of the fraction of the second that are not 0, and the fraction of
-- a code that is read here is cut off after six digits. PostgreSQL has no
-- year 0: the year the specification calls 0000 is 1 BC.

CREATE OR REPLACE FUNCTION timecode_spec_version()
  RETURNS text
  LANGUAGE sql
  IMMUTABLE
  PARALLEL SAFE
AS $$
  SELECT '1.0.0'
$$;

CREATE OR REPLACE FUNCTION timecode(t timestamptz, digits integer DEFAULT NULL)
  RETURNS text
  LANGUAGE plpgsql
  IMMUTABLE
  PARALLEL SAFE
AS $$
DECLARE
  p5 CONSTANT text := 'ABCDEFGHIJKLOPQRSTUVWXYZ';
  p6 CONSTANT text := 'BDFGHJKLMPQRSTVWXYZ';
  p7 CONSTANT text := 'BCDFGHJKLMNPQRVWXYZ';
  p8 CONSTANT text := 'BCDFGHJLMNPQRSVWXYZ';
  p9 CONSTANT text := 'BCDEFHIJLMNOPQUVWXYZ';

  -- The start of the second half month in seconds. 372 hours is 15.5 days.
  hm2_begin CONSTANT integer := 372 * 3600;

  utc timestamp := t AT TIME ZONE 'UTC';
  year_number integer := EXTRACT(YEAR FROM utc);
  since_m_begin numeric := EXTRACT(EPOCH FROM utc - date_trunc('month', utc));
  loc_in_month integer := floor(since_m_begin);
  nanoseconds integer := (since_m_begin - floor(since_m_begin)) * 1000000000;
  half_month integer := 2 * (EXTRACT(MONTH FROM utc) - 1);
  loc_in_half_month integer := loc_in_month;
  block integer;
  second_in_block integer;
  code text;
BEGIN
  -- The year before 1 AD is 1 BC, or -1, which the specification calls 0.
  IF year_number < 0 THEN
    year_number := year_number + 1;
  END IF;

  IF year_number NOT BETWEEN 0 AND 9999 THEN
    RAISE EXCEPTION 'TimeCodes support four digit years.'
      USING ERRCODE = 'invalid_parameter_value';
  END IF;

  -- Calculate offsets needed if we are in the second half month.
  IF loc_in_month >= hm2_begin THEN
    half_month := half_month + 1;
    loc_in_half_month := loc_in_month - hm2_begin;
  END IF;

  block := loc_in_half_month / 200;
  second_in_block := loc_in_half_month % 200;

  code := lpad(year_number::text, 4, '0')
       || substr(p5, half_month + 1, 1)
       || substr(p6, (block / (19 * 19)) + 1, 1)
       || substr(p7, ((block / 19) % 19) + 1, 1)
       || substr(p8, (block % 19) + 1, 1)
       || substr(p9, (second_in_block / 10) + 1, 1)
       || (second_in_block % 10)::text
       || '.'
       || lpad(nanoseconds::text, 9, '0');

  IF digits IS NULL THEN
    RETURN rtrim(rtrim(code, '0'), '.');
  END IF;

  IF digits NOT BETWEEN 4 AND 20 THEN
    RAISE EXCEPTION 'TimeCodes have from 4 to 20 digits.'
      USING ERRCODE = 'invalid_parameter_value';
  END IF;

  RETURN left(code, CASE WHEN digits = 11 THEN 12 ELSE digits END);
END
$$;

CREATE OR REPLACE FUNCTION tc5(t timestamptz)
  RETURNS text
  LANGUAGE sql
  IMMUTABLE
  STRICT
  PARALLEL SAFE
AS $$
  SELECT timecode(t, 5)
$$;

CREATE OR REPLACE FUNCTION tc8(t timestamptz)
  RETURNS text
  LANGUAGE sql
  IMMUTABLE
  STRICT
  PARALLEL SAFE
AS $$
  SELECT timecode(t, 8)
$$;

CREATE OR REPLACE FUNCTION tcx(
  t timestamptz,
  fraction_digits integer DEFAULT 0
)
  RETURNS text
  LANGUAGE plpgsql
  IMMUTABLE
  STRICT
  PARALLEL SAFE
AS $$
BEGIN
  IF fraction_digits NOT BETWEEN 0 AND 9 THEN
    RAISE EXCEPTION 'The precision must be from 0 to 9 digits.'
      USING ERRCODE = 'invalid_parameter_value';
  END IF;

  RETURN timecode(t, CASE WHEN fraction_digits = 0
                          THEN 10
                          ELSE 11 + fraction_digits
                      END);
END
$$;

-- The digit a place of a code stands for, counting the places from 1.
-- Places that are not present count as 0.
CREATE OR REPLACE FUNCTION timecode_digit(
  places text,
  chars text,
  place integer
)
  RETURNS integer
  LANGUAGE plpgsql
  IMMUTABLE
  STRICT
  PARALLEL SAFE
AS $$
DECLARE
  n integer;
BEGIN
  IF place > length(places) THEN
    RETURN 0;
  END IF;

  n := strpos(chars, substr(places, place, 1));

  IF n = 0 THEN
    RAISE EXCEPTION '% is not allowed in %.',
          substr(places, place, 1),
          CASE WHEN place < 10 THEN 'P' || place ELSE 'PX' END
      USING ERRCODE = 'invalid_parameter_value';
  END IF;

  RETURN n - 1;
END
$$;

CREATE OR REPLACE FUNCTION timecode_parse(code text)
  RETURNS timestamptz
  LANGUAGE plpgsql
  IMMUTABLE
  STRICT
  PARALLEL SAFE
AS $$
DECLARE
  decimal_digits CONSTANT text := '0123456789';
  p5 CONSTANT text := 'ABCDEFGHIJKLOPQRSTUVWXYZ';
  p6 CONSTANT text := 'BDFGHJKLMPQRSTVWXYZ';
  p7 CONSTANT text := 'BCDFGHJKLMNPQRVWXYZ';
  p8 CONSTANT text := 'BCDFGHJLMNPQRSVWXYZ';
  p9 CONSTANT text := 'BCDEFHIJLMNOPQUVWXYZ';

  -- The start of the second half month in seconds. 372 hours is 15.5 days.
  hm2_begin CONSTANT integer := 372 * 3600;

  places text := split_part(code, '.', 1);
  fraction text := CASE WHEN strpos(code, '.') > 0
                        THEN split_part(code, '.', 2)
                    END;
  year_number integer;
  half_month integer;
  block integer;
  second_in_block integer;
  microseconds integer;
  m_begin timestamptz;
  days_in_month integer;
  hm_begin integer;
  hm_length integer;
  loc_in_month integer;
BEGIN
  IF length(places) NOT BETWEEN 4 AND 10 THEN
    RAISE EXCEPTION 'TimeCodes have from 4 to 10 places.'
      USING ERRCODE = 'invalid_parameter_value';
  END IF;

  IF length(code) - length(replace(code, '.', '')) > 1 THEN
    RAISE EXCEPTION 'TimeCodes have at most one decimal point.'
      USING ERRCODE = 'invalid_parameter_value';
  END IF;

  IF fraction IS NOT NULL AND length(places) < 10 THEN
    RAISE EXCEPTION 'Only a TCX may have a fraction of the second.'
      USING ERRCODE = 'invalid_parameter_value';
  END IF;

  IF fraction !~ '^[0-9]{1,9}$' THEN
    RAISE EXCEPTION 'Fractions of the second have 1 to 9 digits.'
      USING ERRCODE = 'invalid_parameter_value';
  END IF;

  -- First, the four components: year, half month, block and seconds.

  year_number := (1000 * timecode_digit(places, decimal_digits, 1))
               + (100 * timecode_digit(places, decimal_digits, 2))
               + (10 * timecode_digit(places, decimal_digits, 3))
               + timecode_digit(places, decimal_digits, 4);
  half_month := timecode_digit(places, p5, 5);
  block := (19 * 19 * timecode_digit(places, p6, 6))
         + (19 * timecode_digit(places, p7, 7))
         + timecode_digit(places, p8, 8);
  second_in_block := (10 * timecode_digit(places, p9, 9))
                   + timecode_digit(places, decimal_digits, 10);

  -- A timestamptz holds microseconds, so the rest of the fraction is cut
  -- off.
  microseconds := COALESCE(left(rpad(fraction, 9, '0'), 6)::integer, 0);

  -- The year the specification calls 0 is 1 BC, or -1.
  m_begin := make_timestamptz(
               CASE WHEN year_number = 0 THEN -1 ELSE year_number END,
               (half_month / 2) + 1,
               1,
               0,
               0,
               0,
               'UTC'
             );
  days_in_month := EXTRACT(
                     DAY FROM (m_begin AT TIME ZONE 'UTC')
                            + '1 month'::interval
                            - '1 day'::interval
                   );

  -- The second half month ends with the month, so it is shorter in months
  -- of fewer than 31 days.
  hm_begin := CASE WHEN half_month % 2 = 1 THEN hm2_begin ELSE 0 END;
  hm_length := CASE WHEN half_month % 2 = 1
                    THEN (days_in_month * 24 * 3600) - hm2_begin
                    ELSE hm2_begin
                END;

  -- Codes past the end of the half month, like those of leap seconds, are
  -- clipped to its last second.
  loc_in_month := hm_begin
                + LEAST((block * 200) + second_in_block, hm_length - 1);

  RETURN m_begin
       + (loc_in_month * '1 second'::interval)
       + (microseconds * '1 microsecond'::interval);
END
$$;
