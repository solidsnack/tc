const { spawnSync } = require('node:child_process')
const path = require('node:path')
const { quote } = require('./cases.js')
const { runSuite } = require('./sql.js')

const port = path.join(__dirname, '..', 'sql-duckdb', 'timecode.sql')

// The duckdb command, in an in-memory database, without ~/.duckdbrc.
function duckdb(script) {
    return spawnSync(
        'duckdb',
        ['-init', '/dev/null', '-bail', '-csv', '-noheader', ':memory:'],
        { input: script, encoding: 'utf8' },
    )
}

function run(decodes, encodes) {
    if (duckdb('SELECT 1;').status !== 0) {
        return undefined
    }

    const decodeRows = decodes.map(({ id, code }) => `(${id}, ${quote(code)})`)
    const encodeRows = encodes.map((e) =>
        `(${e.id}, ${e.seconds}, ${e.microseconds}, ${e.digits}, ` +
        `${quote(e.direct)}, ${e.fractionDigits})`
    )
    const result = duckdb(`
.read ${port}
-- A time zone other than UTC, which should make no difference.
SET TimeZone = 'Asia/Kolkata';
SELECT id,
       epoch_us(timecode_parse(code))
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
               make_timestamptz((seconds::bigint * 1000000)
                                + microseconds) AS t,
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
`)

    if (result.status !== 0) {
        throw new Error(result.stderr)
    }

    return result.stdout
}

runSuite({
    name: 'sql-duckdb',
    run,
    leapSeconds: 'DuckDB does not read 23:59:60',
})
