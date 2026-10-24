//! TimeCodes: sortable, short timestamps, as described in
//! `specification/index.md`.
//!
//! ```
//! use time::UtcDateTime;
//! use timecode::TimeCode;
//!
//! let code: TimeCode = "2026VDJBD5.25".parse()?;
//!
//! assert_eq!(code.tc5(), "2026V");
//! assert_eq!(code.tc8(), "2026VDJB");
//! assert_eq!(code.tcx(2), "2026VDJBD5.25");
//! assert_eq!(code.to_string(), "2026VDJBD5.25");
//! assert_eq!(code.bc4(), "C6VD");
//! assert_eq!(code.bc(11), "C6VDJBD5.25");
//!
//! let t: UtcDateTime = code.into();
//! assert_eq!(TimeCode::try_from(t)?, code);
//! assert_eq!(TimeCode::parse_bc_near("C6VDJBD5.25", code)?, code);
//! # Ok::<(), timecode::Error>(())
//! ```

use std::fmt;
use std::str::FromStr;

use time::{Date, Duration, Month, OffsetDateTime, Time, UtcDateTime};

const P5: &[u8] = b"ABCDEFGHIJKLOPQRSTUVWXYZ";
const P6: &[u8] = b"BDFGHJKLMPQRSTVWXYZ";
const P7: &[u8] = b"BCDFGHJKLMNPQRVWXYZ";
const P8: &[u8] = b"BCDFGHJLMNPQRSVWXYZ";
const P9: &[u8] = b"BCDEFHIJLMNOPQUVWXYZ";
const B1: &[u8] = b"ABCDEFGHIJKLMNOPQRST";
const DECIMAL: &[u8] = b"0123456789";

// The start of the second half month in seconds. 372 hours is 15.5 days.
const HM2_BEGIN: u32 = 372 * 3600;

/// The components of a TimeCode: the year, from 0 to 9999; the half month,
/// from 0 to 23; the block of 200 seconds within the half month, from 0 to
/// 6858; the second within the block, from 0 to 199; and the nanosecond
/// within the second, from 0 to 999,999,999.
#[derive(Clone, Copy, Debug, PartialEq, Eq, Hash, PartialOrd, Ord)]
pub struct Descriptor {
    pub year: u16,
    pub half_month: u8,
    pub block: u16,
    pub seconds: u8,
    pub nanoseconds: u32,
}

/// A time in UTC, as described in `specification/index.md`: the year, the
/// half month, the block of 200 seconds within the half month, the second
/// within the block and the nanosecond within the second.
///
/// TimeCodes are ordered in time, the same as their codes sort. They are
/// displayed with the precision of the encoded value.
#[derive(Clone, Copy, Debug, PartialEq, Eq, Hash, PartialOrd, Ord)]
pub struct TimeCode {
    descriptor: Descriptor,
}

impl TimeCode {
    /// The current time.
    pub fn now() -> TimeCode {
        TimeCode::try_from(UtcDateTime::now())
            .expect("the current year has four digits")
    }

    /// Read a TimeCode of any length, from TC4 to a TCX with a fraction of
    /// the second. It stands for the start of the time it covers, so places
    /// that are not present count as 0.
    pub fn parse(code: &str) -> Result<TimeCode, Error> {
        TimeCode::try_from(read(code)?)
    }

    /// Read a bicentennial code of any length, from BC2 to a BC8 with a
    /// fraction of the second, in the year nearest the present, as
    /// [`TimeCode::parse_bc_near`] does.
    pub fn parse_bc(code: &str) -> Result<TimeCode, Error> {
        TimeCode::parse_bc_near(code, TimeCode::now())
    }

    /// Read a bicentennial code of any length, from BC2 to a BC8 with a
    /// fraction of the second. Of the years with its place in the cycle of
    /// 200 years, it stands for the one nearest the year of `near`: no more
    /// than 100 years before it, and less than 100 after. As with a
    /// TimeCode, it stands for the start of the time it covers.
    ///
    /// ```
    /// use timecode::{Error, TimeCode};
    ///
    /// let near = TimeCode::parse("2026")?;
    /// let code = TimeCode::parse_bc_near("C6VD", near)?;
    ///
    /// assert_eq!(code.to_string(), "2026VDBBB0");
    /// assert_eq!(TimeCode::parse_bc_near("T9", near)?.tc(4), "1999");
    /// assert_eq!(
    ///     TimeCode::parse_bc_near("U9", near),
    ///     Err(Error::BicentennialCharacter { character: 'U', place: 1 }),
    /// );
    /// # Ok::<(), timecode::Error>(())
    /// ```
    pub fn parse_bc_near(
        code: &str,
        near: TimeCode,
    ) -> Result<TimeCode, Error> {
        TimeCode::try_from(read_bc(code, near.descriptor.year)?)
    }

    /// Display the TimeCode in TC5 format.
    pub fn tc5(&self) -> String {
        self.format()[..5].to_string()
    }

    /// Display the TimeCode in TC8 format.
    pub fn tc8(&self) -> String {
        self.format()[..8].to_string()
    }

    /// Display the TimeCode in TCX format, with up to nine digits of
    /// subseconds. A precision of more than 9 digits gives 9.
    pub fn tcx(&self, precision: u8) -> String {
        let s = self.format();
        let length = match precision.min(9) {
            0 => 10,
            p => 11 + usize::from(p),
        };

        s[..length].to_string()
    }

    /// Display the TimeCode with the requested number of digits, from 4 to
    /// 20. Fewer than 4 digits gives a TC4, and more than 20 gives a TC20.
    /// As in the specification, TC11 is displayed like TC12.
    pub fn tc(&self, digits: u8) -> String {
        let s = self.format();
        let length = match digits.clamp(4, 20) {
            11 => 12,
            n => usize::from(n),
        };

        s[..length].to_string()
    }

    /// Display the TimeCode in BC4 format: the place of its year in a cycle
    /// of 200 years, the half month and the first place of the block.
    pub fn bc4(&self) -> String {
        self.bc(4)
    }

    /// Display the TimeCode as a bicentennial code with the requested number
    /// of digits, from 2 to 18: the TC with two more digits, with its year
    /// written as its place in a cycle of 200 years. Fewer than 2 digits
    /// gives a BC2, and more than 18 gives a BC18. As in the specification,
    /// BC9 is displayed like BC10.
    pub fn bc(&self, digits: u8) -> String {
        let years = usize::from(self.descriptor.year % 200);
        let code = self.tc(digits.clamp(2, 18) + 2);

        format!("{}{}{}", char::from(B1[years / 10]), years % 10, &code[4..])
    }

    /// The components of the TimeCode.
    pub fn descriptor(&self) -> Descriptor {
        self.descriptor
    }

    // Produce a full length (TC20) code.
    fn format(&self) -> String {
        let d = self.descriptor;
        let block = usize::from(d.block);
        let seconds = usize::from(d.seconds);

        format!(
            "{:04}{}{}{}{}{}{}.{:09}",
            d.year,
            char::from(P5[usize::from(d.half_month)]),
            char::from(P6[block / (19 * 19)]),
            char::from(P7[(block / 19) % 19]),
            char::from(P8[block % 19]),
            char::from(P9[seconds / 10]),
            seconds % 10,
            d.nanoseconds,
        )
    }
}

impl fmt::Display for TimeCode {
    /// Display the TimeCode with the precision of the encoded value.
    fn fmt(&self, f: &mut fmt::Formatter) -> fmt::Result {
        let s = self.format();

        f.pad(s.trim_end_matches('0').trim_end_matches('.'))
    }
}

impl FromStr for TimeCode {
    type Err = Error;

    fn from_str(code: &str) -> Result<TimeCode, Error> {
        TimeCode::parse(code)
    }
}

impl TryFrom<UtcDateTime> for TimeCode {
    type Error = Error;

    /// The TimeCode of a time in the years 0000 to 9999.
    fn try_from(t: UtcDateTime) -> Result<TimeCode, Error> {
        let year = u16::try_from(t.year())
            .ok()
            .filter(|year| *year <= 9999)
            .ok_or(Error::YearOutOfRange)?;

        // The seconds since the start of the month.
        let loc_in_month = ((u32::from(t.day()) - 1) * 24 * 3600)
            + (u32::from(t.hour()) * 3600)
            + (u32::from(t.minute()) * 60)
            + u32::from(t.second());

        // Calculate offsets needed if we are in the second half month.
        let corrections = if loc_in_month < HM2_BEGIN {
            (0, 0)
        } else {
            (1, HM2_BEGIN)
        };

        let whole_month = u8::from(t.month()) - 1;
        let loc_in_half_month = loc_in_month - corrections.1;

        Ok(TimeCode {
            descriptor: Descriptor {
                year,
                half_month: (2 * whole_month) + corrections.0,
                block: (loc_in_half_month / 200) as u16,
                seconds: (loc_in_half_month % 200) as u8,
                nanoseconds: t.nanosecond(),
            },
        })
    }
}

impl TryFrom<OffsetDateTime> for TimeCode {
    type Error = Error;

    /// The TimeCode of a time in the years 0000 to 9999, in UTC.
    fn try_from(t: OffsetDateTime) -> Result<TimeCode, Error> {
        let utc = t.checked_to_utc().ok_or(Error::YearOutOfRange)?;

        TimeCode::try_from(utc)
    }
}

impl TryFrom<Descriptor> for TimeCode {
    type Error = Error;

    /// The TimeCode of a descriptor, whose components must be in range. A
    /// leap second takes the second just after the end of the month. Like
    /// anything else past the end of the half month, it is read as the last
    /// second of the half month.
    fn try_from(d: Descriptor) -> Result<TimeCode, Error> {
        in_range("year", d.year.into(), 9999)?;
        in_range("half month", d.half_month.into(), 23)?;
        in_range("block", d.block.into(), (19 * 19 * 19) - 1)?;
        in_range("second", d.seconds.into(), 199)?;
        in_range("nanosecond", d.nanoseconds, 999_999_999)?;

        let last = length_of(d.year, d.half_month) - 1;
        let loc_in_half_month =
            ((u32::from(d.block) * 200) + u32::from(d.seconds)).min(last);

        Ok(TimeCode {
            descriptor: Descriptor {
                block: (loc_in_half_month / 200) as u16,
                seconds: (loc_in_half_month % 200) as u8,
                ..d
            },
        })
    }
}

impl From<TimeCode> for UtcDateTime {
    /// The start of the time the TimeCode stands for.
    fn from(code: TimeCode) -> UtcDateTime {
        let d = code.descriptor;
        let m_begin = UtcDateTime::new(
            Date::from_calendar_date(d.year.into(), month_of(d.half_month), 1)
                .expect("TimeCodes have four digit years"),
            Time::MIDNIGHT,
        );
        let hm_begin = match d.half_month % 2 {
            1 => HM2_BEGIN,
            _ => 0,
        };
        let seconds =
            hm_begin + (u32::from(d.block) * 200) + u32::from(d.seconds);

        m_begin
            + Duration::seconds(seconds.into())
            + Duration::nanoseconds(d.nanoseconds.into())
    }
}

impl From<TimeCode> for OffsetDateTime {
    /// The start of the time the TimeCode stands for, in UTC.
    fn from(code: TimeCode) -> OffsetDateTime {
        UtcDateTime::from(code).into()
    }
}

impl From<TimeCode> for Descriptor {
    fn from(code: TimeCode) -> Descriptor {
        code.descriptor
    }
}

/// Why a code, descriptor or time is not a TimeCode.
#[derive(Clone, Debug, PartialEq, Eq)]
#[non_exhaustive]
pub enum Error {
    /// The time is not in the years 0000 to 9999.
    YearOutOfRange,
    /// The code has fewer than 4 or more than 10 places.
    Places,
    /// The code has more than one decimal point.
    DecimalPoints,
    /// The code has a fraction of the second, but is shorter than a TCX.
    Fraction,
    /// The fraction of the second does not have from 1 to 9 digits.
    FractionDigits,
    /// A place of the code, from 1 to 10, has a character it does not allow.
    Character { character: char, place: u8 },
    /// A component of a descriptor is out of range.
    OutOfRange { component: &'static str, max: u32 },
    /// The bicentennial code has fewer than 2 or more than 8 places.
    BicentennialPlaces,
    /// The bicentennial code has a fraction of the second, but is shorter
    /// than a BC8.
    BicentennialFraction,
    /// A place of the year of a bicentennial code, B1 or B2 (1 or 2), has a
    /// character it does not allow.
    BicentennialCharacter { character: char, place: u8 },
}

impl fmt::Display for Error {
    fn fmt(&self, f: &mut fmt::Formatter) -> fmt::Result {
        match self {
            Error::YearOutOfRange => {
                f.write_str("TimeCodes support four digit years.")
            }
            Error::Places => {
                f.write_str("TimeCodes have from 4 to 10 places.")
            }
            Error::DecimalPoints => {
                f.write_str("TimeCodes have at most one decimal point.")
            }
            Error::Fraction => {
                f.write_str("Only a TCX may have a fraction of the second.")
            }
            Error::FractionDigits => {
                f.write_str("Fractions of the second have 1 to 9 digits.")
            }
            Error::Character {
                character,
                place: 10,
            } => {
                write!(f, "{character} is not allowed in PX.")
            }
            Error::Character { character, place } => {
                write!(f, "{character} is not allowed in P{place}.")
            }
            Error::OutOfRange { component, max } => {
                write!(f, "The {component} must be from 0 to {max}.")
            }
            Error::BicentennialPlaces => {
                f.write_str("Bicentennial codes have from 2 to 8 places.")
            }
            Error::BicentennialFraction => {
                f.write_str("Only a BC8 may have a fraction of the second.")
            }
            Error::BicentennialCharacter { character, place } => {
                write!(f, "{character} is not allowed in B{place}.")
            }
        }
    }
}

impl std::error::Error for Error {}

fn in_range(
    component: &'static str,
    value: u32,
    max: u32,
) -> Result<(), Error> {
    if value <= max {
        Ok(())
    } else {
        Err(Error::OutOfRange { component, max })
    }
}

fn month_of(half_month: u8) -> Month {
    Month::try_from((half_month / 2) + 1).expect("half months are 0 to 23")
}

// The length of a half month in seconds. The second half month ends with
// the month, so it is shorter in months of fewer than 31 days.
fn length_of(year: u16, half_month: u8) -> u32 {
    if half_month.is_multiple_of(2) {
        return HM2_BEGIN;
    }

    let days = month_of(half_month).length(year.into());

    (u32::from(days) * 24 * 3600) - HM2_BEGIN
}

// Reads the components of a TimeCode of any length, from TC4 to TCX with a
// fraction of the second. Places that are not present count as 0.
fn read(code: &str) -> Result<Descriptor, Error> {
    let mut parts = code.split('.');
    let places: Vec<char> = parts.next().unwrap_or("").chars().collect();
    let fraction = parts.next();

    if places.len() < 4 || places.len() > 10 {
        return Err(Error::Places);
    }

    if parts.next().is_some() {
        return Err(Error::DecimalPoints);
    }

    if fraction.is_some() && places.len() < 10 {
        return Err(Error::Fraction);
    }

    if let Some(digits) = fraction
        && (digits.is_empty()
            || digits.len() > 9
            || !digits.bytes().all(|b| b.is_ascii_digit()))
    {
        return Err(Error::FractionDigits);
    }

    // The digit a place stands for. Places that are not present count as 0.
    let digit = |chars: &[u8], i: usize| -> Result<u32, Error> {
        let Some(&c) = places.get(i) else {
            return Ok(0);
        };

        chars
            .iter()
            .position(|&allowed| char::from(allowed) == c)
            .map(|n| n as u32)
            .ok_or(Error::Character {
                character: c,
                place: (i + 1) as u8,
            })
    };

    let year = (1000 * digit(DECIMAL, 0)?)
        + (100 * digit(DECIMAL, 1)?)
        + (10 * digit(DECIMAL, 2)?)
        + digit(DECIMAL, 3)?;
    let half_month = digit(P5, 4)?;
    let block =
        (19 * 19 * digit(P6, 5)?) + (19 * digit(P7, 6)?) + digit(P8, 7)?;
    let seconds = (10 * digit(P9, 8)?) + digit(DECIMAL, 9)?;
    let nanoseconds = match fraction {
        None => 0,
        Some(digits) => format!("{digits:0<9}")
            .parse()
            .expect("fractions have 1 to 9 digits"),
    };

    Ok(Descriptor {
        year: year as u16,
        half_month: half_month as u8,
        block: block as u16,
        seconds: seconds as u8,
        nanoseconds,
    })
}

// Reads the components of a bicentennial code of any length, from BC2 to
// BC8 with a fraction of the second, in the year with its place in the cycle
// nearest the year `near`: no more than 100 years before it, and less than
// 100 after.
fn read_bc(code: &str, near: u16) -> Result<Descriptor, Error> {
    let mut parts = code.split('.');
    let places: Vec<char> = parts.next().unwrap_or("").chars().collect();
    let fraction = parts.next();

    if places.len() < 2 || places.len() > 8 {
        return Err(Error::BicentennialPlaces);
    }

    if fraction.is_some() && places.len() < 8 {
        return Err(Error::BicentennialFraction);
    }

    // The digit a place of the year stands for.
    let digit = |chars: &[u8], i: usize| -> Result<i32, Error> {
        let c = places[i];

        chars
            .iter()
            .position(|&allowed| char::from(allowed) == c)
            .map(|n| n as i32)
            .ok_or(Error::BicentennialCharacter {
                character: c,
                place: (i + 1) as u8,
            })
    };

    let years = (10 * digit(B1, 0)?) + digit(DECIMAL, 1)?;
    let earliest = i32::from(near) - 100;
    let year = earliest + (years - earliest).rem_euclid(200);

    if !(0..=9999).contains(&year) {
        return Err(Error::YearOutOfRange);
    }

    // The rest is read as the TimeCode with the year written out. The places
    // of the year are ASCII, so the rest of the code starts at byte 2.
    read(&format!("{year:04}{}", &code[2..]))
}
