const { spawnSync } = require('node:child_process')
const path = require('node:path')
const { quote } = require('./cases.js')
const { runSuite } = require('./sql.js')

const port = path.join(__dirname, '..', 'sql-pg', 'timecode.sql')

// psql connects with the usual PG* environment variables. The functions are
// made in a transaction that is rolled back, so nothing is left behind.
function psql(script) {
    return spawnSync(
        'psql',
        ['-X', '-q', '-At', '-F', ',', '-v', 'ON_ERROR_STOP=1'],
        { input: script, encoding: 'utf8' },
    )
}

function run(decodes, encodes) {
    if (psql('SELECT 1').status !== 0) {
        return undefined
    }

    const decodeRows = decodes.map(({ id, code }) => `(${id}, ${quote(code)})`)
    const encodeRows = encodes.map((e) =>
        `(${e.id}, ${e.seconds}, ${e.microseconds}, ${e.digits}, ` +
        `${quote(e.direct)}, ${e.fractionDigits})`
    )
    const result = psql(`
BEGIN;
-- A time zone other than UTC, which should make no difference.
SET LOCAL TimeZone = 'Asia/Kolkata';
\\i ${quote(port)}
SELECT id,
       (EXTRACT(EPOCH FROM timecode_parse(code)) * 1000000)::bigint
  FROM (VALUES ${decodeRows.join(',\n')}) AS cases (id, code);
SELECT id,
       timecode(t, digits),
       COALESCE(CASE direct
                  WHEN 'tc5' THEN tc5(t)
                  WHEN 'tc8' THEN tc8(t)
                  WHEN 'tcx' THEN tcx(t, fraction_digits)
                END,
                '')
  FROM (SELECT id,
               'epoch'::timestamptz
                 + (seconds * '1 second'::interval)
                 + (microseconds * '1 microsecond'::interval) AS t,
               digits,
               direct,
               fraction_digits
          FROM (VALUES ${encodeRows.join(',\n')})
                 AS cases (id,
                           seconds,
                           microseconds,
                           digits,
                           direct,
                           fraction_digits)) AS times;
ROLLBACK;
`)

    if (result.status !== 0) {
        throw new Error(result.stderr)
    }

    return result.stdout
}

runSuite({
    name: 'sql-pg',
    run,
    leapSeconds: 'PostgreSQL reads 23:59:60 as 00:00:00 of the next day',
})
