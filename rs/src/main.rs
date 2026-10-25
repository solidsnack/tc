use clap::{Parser, Subcommand};
use time::{Date, Month, Time, UtcDateTime};
use timecode::TimeCode;

const AFTER_HELP: &str = "A TIME is a TimeCode or a timestamp in UTC: \
    yyyy-mm-ddTHH:MM:SS.nnnnnnnnnZ, where the fraction may have from 1 to 9 \
    digits, and the time may stop after the hours, the minutes or the \
    seconds, or be left out, as in 2026-10-17Z. Second 60, for a leap \
    second, is read as second 59. Without --digits, tc and bc show the full \
    precision of the time.";

/// Encode and decode TimeCodes: sortable, short timestamps. Without a
/// command, print the current time as a TC8.
#[derive(Parser)]
#[command(name = "timecode", version, after_help = AFTER_HELP)]
struct CLI {
    #[command(subcommand)]
    command: Option<Command>,
}

#[derive(Subcommand)]
enum Command {
    /// Print a time as a TC5 code: the half month.
    #[command(after_help = AFTER_HELP)]
    TC5 {
        /// The time to encode; by default, now.
        #[arg(value_parser = time)]
        time: Option<TimeCode>,
    },
    /// Print a time as a TC8 code: the block of 200 seconds.
    #[command(after_help = AFTER_HELP)]
    TC8 {
        /// The time to encode; by default, now.
        #[arg(value_parser = time)]
        time: Option<TimeCode>,
    },
    /// Print a time as a TCX code: the second.
    #[command(after_help = AFTER_HELP)]
    TCX {
        /// Digits of the fraction of the second.
        #[arg(
            short,
            long,
            value_name = "DIGITS",
            default_value_t = 0,
            value_parser = clap::value_parser!(u8).range(0..=9),
        )]
        precision: u8,
        /// The time to encode; by default, now.
        #[arg(value_parser = time)]
        time: Option<TimeCode>,
    },
    /// Print a time as a TimeCode of any length.
    #[command(after_help = AFTER_HELP)]
    TC {
        /// Length of the code.
        #[arg(
            short,
            long,
            value_name = "LENGTH",
            value_parser = clap::value_parser!(u8).range(4..=20),
        )]
        digits: Option<u8>,
        /// The time to encode; by default, now.
        #[arg(value_parser = time)]
        time: Option<TimeCode>,
    },
    /// Print a time as a BC4 code: about 20 hours, in a cycle of 200 years.
    #[command(after_help = AFTER_HELP)]
    BC4 {
        /// The time to encode; by default, now.
        #[arg(value_parser = time)]
        time: Option<TimeCode>,
    },
    /// Print a time as a BC5 code: about an hour, in a cycle of 200 years.
    #[command(after_help = AFTER_HELP)]
    BC5 {
        /// The time to encode; by default, now.
        #[arg(value_parser = time)]
        time: Option<TimeCode>,
    },
    /// Print a time as a BC8 code: the second, in a cycle of 200 years.
    #[command(after_help = AFTER_HELP)]
    BC8 {
        /// The time to encode; by default, now.
        #[arg(value_parser = time)]
        time: Option<TimeCode>,
    },
    /// Print a time as a bicentennial code of any length.
    #[command(after_help = AFTER_HELP)]
    BC {
        /// Length of the code.
        #[arg(
            short,
            long,
            value_name = "LENGTH",
            value_parser = clap::value_parser!(u8).range(2..=18),
        )]
        digits: Option<u8>,
        /// The time to encode; by default, now.
        #[arg(value_parser = time)]
        time: Option<TimeCode>,
    },
    /// Print the time a TimeCode stands for, in UTC.
    Parse {
        /// Print the code, time and descriptor as JSON.
        #[arg(long)]
        json: bool,
        /// The TimeCode to decode.
        #[arg(value_name = "CODE", value_parser = code)]
        code: (String, TimeCode),
    },
}

fn main() {
    let command = CLI::parse().command.unwrap_or(Command::TC8 { time: None });
    let line = match command {
        Command::TC5 { time } => time.unwrap_or_else(TimeCode::now).tc5(),
        Command::TC8 { time } => time.unwrap_or_else(TimeCode::now).tc8(),
        Command::TCX { precision, time } => {
            time.unwrap_or_else(TimeCode::now).tcx(precision)
        }
        Command::TC { digits, time } => {
            let code = time.unwrap_or_else(TimeCode::now);

            match digits {
                Some(digits) => code.tc(digits),
                None => code.to_string(),
            }
        }
        Command::BC4 { time } => time.unwrap_or_else(TimeCode::now).bc4(),
        Command::BC5 { time } => time.unwrap_or_else(TimeCode::now).bc(5),
        Command::BC8 { time } => time.unwrap_or_else(TimeCode::now).bc(8),
        Command::BC { digits, time } => {
            let code = time.unwrap_or_else(TimeCode::now);

            match digits {
                Some(digits) => code.bc(digits),
                // The place of the year, then the rest of the TimeCode, with
                // the precision of the time.
                None => code.bc(2) + &code.to_string()[4..],
            }
        }
        Command::Parse {
            json,
            code: (input, code),
        } => {
            let time = rfc3339(code.into());
            let d = code.descriptor();

            if json {
                format!(
                    "{{\"code\":\"{input}\",\"time\":\"{time}\",\
                     \"descriptor\":{{\"year\":{},\"halfMonth\":{},\
                     \"block\":{},\"seconds\":{},\"nanoseconds\":{}}}}}",
                    d.year, d.half_month, d.block, d.seconds, d.nanoseconds,
                )
            } else {
                time
            }
        }
    };

    println!("{line}");
}

// A time to encode: a timestamp in UTC, or a TimeCode.
fn time(input: &str) -> Result<TimeCode, String> {
    if let Ok(code) = input.parse() {
        return Ok(code);
    }

    timestamp(input)
        .and_then(|t| TimeCode::try_from(t).ok())
        .ok_or_else(|| {
            "Not a TimeCode or a timestamp in UTC, such as \
             2026-10-17T14:23:45Z."
                .to_string()
        })
}

// A TimeCode to decode, kept with the input it was read from.
fn code(input: &str) -> Result<(String, TimeCode), String> {
    match TimeCode::parse(input) {
        Ok(code) => Ok((input.to_string(), code)),
        Err(e) => Err(format!("Not a TimeCode. {e}")),
    }
}

// The only timestamps accepted: yyyy-mm-ddTHH:MM:SS.nnnnnnnnnZ, where the
// fraction may have from 1 to 9 digits, and the time may stop after the
// hours, the minutes or the seconds, or be left out.
fn timestamp(input: &str) -> Option<UtcDateTime> {
    let body = input.strip_suffix('Z')?;
    let (stamp, fraction) = match body.split_once('.') {
        Some((stamp, fraction)) => (stamp, Some(fraction)),
        None => (body, None),
    };

    // Each shorter timestamp is the start of the full one. In the pattern,
    // 0 stands for any digit.
    let pattern = b"0000-00-00T00:00:00";
    let is_like_pattern = [10, 13, 16, 19].contains(&stamp.len())
        && stamp.bytes().zip(pattern).all(|(c, p)| match p {
            b'0' => c.is_ascii_digit(),
            _ => c == *p,
        });

    if !is_like_pattern {
        return None;
    }

    if let Some(digits) = fraction
        && (stamp.len() < 19
            || digits.is_empty()
            || digits.len() > 9
            || !digits.bytes().all(|b| b.is_ascii_digit()))
    {
        return None;
    }

    // The number in a part of the timestamp, or 0 if it was left out.
    let number = |from: usize, to: usize| -> u32 {
        stamp.get(from..to).map_or(0, |s| s.parse().unwrap_or(0))
    };

    // Second 60, for a leap second, is read as second 59, the same as the
    // library reads codes past the end of a half month.
    let second = match number(17, 19) {
        60 => 59,
        second => second,
    };
    let nanosecond = fraction
        .map_or(0, |digits| format!("{digits:0<9}").parse().unwrap_or(0));

    let date = Date::from_calendar_date(
        number(0, 4) as i32,
        Month::try_from(number(5, 7) as u8).ok()?,
        number(8, 10) as u8,
    )
    .ok()?;
    let time = Time::from_hms_nano(
        number(11, 13) as u8,
        number(14, 16) as u8,
        second as u8,
        nanosecond,
    )
    .ok()?;

    Some(UtcDateTime::new(date, time))
}

// An RFC 3339 timestamp in UTC, with as many digits of the fraction of the
// second as needed, the same as Temporal prints them.
fn rfc3339(t: UtcDateTime) -> String {
    let fraction = format!(".{:09}", t.nanosecond());
    let fraction = fraction.trim_end_matches('0').trim_end_matches('.');

    format!(
        "{:04}-{:02}-{:02}T{:02}:{:02}:{:02}{fraction}Z",
        t.year(),
        u8::from(t.month()),
        t.day(),
        t.hour(),
        t.minute(),
        t.second(),
    )
}
