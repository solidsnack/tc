use proptest::prelude::*;
use proptest::sample::select;
use time::{Date, Duration, Month, Time, UtcDateTime, UtcOffset};
use timecode::{Descriptor, Error, TimeCode};

const P5: &str = "ABCDEFGHIJKLOPQRSTUVWXYZ";
const P6: &str = "BDFGHJKLMPQRSTVWXYZ";
const P7: &str = "BCDFGHJKLMNPQRVWXYZ";
const P8: &str = "BCDFGHJLMNPQRSVWXYZ";
const P9: &str = "BCDEFHIJLMNOPQUVWXYZ";
const B1: &str = "ABCDEFGHIJKLMNOPQRST";
const DECIMAL: &str = "0123456789";

fn first() -> UtcDateTime {
    let date = Date::from_calendar_date(0, Month::January, 1).unwrap();
    UtcDateTime::new(date, Time::MIDNIGHT)
}

fn last() -> UtcDateTime {
    let date = Date::from_calendar_date(9999, Month::December, 31).unwrap();
    let time = Time::from_hms_nano(23, 59, 59, 999_999_999).unwrap();
    UtcDateTime::new(date, time)
}

// The instant, kept within the years TimeCodes cover.
fn within(t: Option<UtcDateTime>) -> UtcDateTime {
    t.unwrap_or(last()).clamp(first(), last())
}

// Any instant TimeCodes cover, with extra weight on the edges of half
// months, where the calendar matters most.
fn instant() -> impl Strategy<Value = UtcDateTime> {
    let anywhere = (
        first().unix_timestamp()..=last().unix_timestamp(),
        0..1_000_000_000u32,
    )
        .prop_map(|(seconds, nanos)| {
            UtcDateTime::from_unix_timestamp(seconds).unwrap()
                + Duration::nanoseconds(nanos.into())
        });
    let near_edge = (
        0..=9999u16,
        0..24usize,
        -400_000_000_000..=400_000_000_000i64,
    )
        .prop_map(|(year, half_month, offset)| {
            let code = format!("{year:04}{}", &P5[half_month..=half_month]);
            let start: UtcDateTime = TimeCode::parse(&code).unwrap().into();
            within(start.checked_add(Duration::nanoseconds(offset)))
        });

    prop_oneof![anywhere, near_edge]
}

// Two instants, the first no later than the second and often close to it.
fn ordered() -> impl Strategy<Value = (UtcDateTime, UtcDateTime)> {
    let span = (last() - first()).whole_seconds();
    let gap = prop_oneof![
        Just(Duration::ZERO),
        (1..=1_000_000_000i64).prop_map(Duration::nanoseconds),
        (1..=1_000_000_000_000i64).prop_map(Duration::nanoseconds),
        (0..=span, 0..1_000_000_000i32).prop_map(|(s, n)| Duration::new(s, n)),
    ];

    (instant(), gap).prop_map(|(x, gap)| (x, within(x.checked_add(gap))))
}

// Two TC5, TC8 or TCX codes the grammar allows, whether or not they are
// past the end of their half month. They share their first few places, so
// that they often differ only in the block or the second of one half month.
// Within each place, the characters are in sorted order.
fn codes() -> impl Strategy<Value = (String, String)> {
    fn pick(chars: &str) -> BoxedStrategy<char> {
        select(chars.chars().collect::<Vec<_>>()).boxed()
    }

    let chars = (
        (pick(DECIMAL), pick(DECIMAL), pick(DECIMAL), pick(DECIMAL)),
        // Often the last letter of P6, where codes run past the end of the
        // half month.
        (pick(P5), prop_oneof![pick(P6), Just('Z')]),
        (pick(P7), pick(P8), pick(P9), pick(DECIMAL)),
    )
        .prop_map(|((y1, y2, y3, y4), (p5, p6), (p7, p8, p9, px))| {
            vec![y1, y2, y3, y4, p5, p6, p7, p8, p9, px]
        });
    let length = select(vec![5, 8, 10]);
    let shared = prop_oneof![0..=10usize, 5..=9usize];

    (chars.clone(), length.clone(), chars, length, shared).prop_map(
        |(a, n, b, m, k)| {
            let c: Vec<char> = a[..k].iter().chain(&b[k..]).copied().collect();
            let render = |chars: &[char], n: usize| -> String {
                chars[..n].iter().collect()
            };
            (render(&a, n), render(&c, m))
        },
    )
}

// Any descriptor in range, often with a block near or past the end of the
// half month.
fn descriptor() -> impl Strategy<Value = Descriptor> {
    (
        0..=9999u16,
        0..24u8,
        prop_oneof![0..=6858u16, 5300..=6858u16],
        0..200u8,
        0..1_000_000_000u32,
    )
        .prop_map(|(year, half_month, block, seconds, nanoseconds)| {
            Descriptor {
                year,
                half_month,
                block,
                seconds,
                nanoseconds,
            }
        })
}

proptest! {
    #![proptest_config(ProptestConfig::with_cases(2000))]

    // The order is tested for TC5, TC8 and TCX, without fractions of the
    // second. A code past the end of its half month reads as the last
    // second, keeping its fraction, so 23:59:60.5 would read as a later time
    // than 23:59:61.
    #[test]
    fn an_earlier_time_has_a_code_that_sorts_no_later(
        (x, y) in ordered(),
        encoder in 0..3usize,
    ) {
        let encode = |t: UtcDateTime| {
            let code = TimeCode::try_from(t).unwrap();

            match encoder {
                0 => code.tc5(),
                1 => code.tc8(),
                _ => code.tcx(0),
            }
        };
        let (xc, yc) = (encode(x), encode(y));

        prop_assert!(xc <= yc, "{x} {y}: {xc} {yc}");
    }

    #[test]
    fn a_code_that_sorts_earlier_reads_as_a_time_no_later(
        (a, b) in codes(),
    ) {
        let (xc, yc) = if a < b { (a, b) } else { (b, a) };
        let read = |c: &str| -> UtcDateTime {
            TimeCode::parse(c).unwrap().into()
        };
        let (x, y) = (read(&xc), read(&yc));

        prop_assert!(x <= y, "{xc} {yc}: {x} {y}");
    }

    #[test]
    fn a_full_length_code_reads_as_the_same_time(t in instant()) {
        let code = TimeCode::try_from(t).unwrap();
        let read: UtcDateTime =
            TimeCode::parse(&code.to_string()).unwrap().into();
        let rebuilt: UtcDateTime =
            TimeCode::try_from(code.descriptor()).unwrap().into();

        prop_assert_eq!(read, t);
        prop_assert_eq!(rebuilt, t);
    }

    #[test]
    fn a_shorter_code_reads_as_the_start_of_its_window(
        t in instant(),
        n in 4..=20u8,
    ) {
        let code = TimeCode::try_from(t).unwrap().tc(n);
        let start: UtcDateTime = TimeCode::parse(&code).unwrap().into();

        prop_assert!(start <= t);
        prop_assert_eq!(&TimeCode::try_from(start).unwrap().tc(n), &code);

        if start != first() {
            let before = start - Duration::nanoseconds(1);
            prop_assert_ne!(&TimeCode::try_from(before).unwrap().tc(n), &code);
        }
    }

    #[test]
    fn every_code_is_the_start_of_the_full_length_code(
        t in instant(),
        n in 4..=20u8,
        p in 0..=9u8,
    ) {
        let code = TimeCode::try_from(t).unwrap();
        let full = code.tc(20);
        let shorter = [
            (code.tc5(), 5),
            (code.tc8(), 8),
            (code.tcx(p), if p == 0 { 10 } else { 11 + usize::from(p) }),
            (code.tc(n), if n == 11 { 12 } else { usize::from(n) }),
        ];

        for (s, length) in shorter {
            prop_assert_eq!(s, &full[..length]);
        }

        prop_assert!(full.starts_with(&code.to_string()));
    }

    #[test]
    fn a_length_out_of_range_gives_the_nearest_code(
        t in instant(),
        p in 10..=u8::MAX,
        short in 0..4u8,
        long in 21..=u8::MAX,
        short_bc in 0..2u8,
        long_bc in 19..=u8::MAX,
    ) {
        let code = TimeCode::try_from(t).unwrap();

        prop_assert_eq!(code.tcx(p), code.tcx(9));
        prop_assert_eq!(code.tc(short), code.tc(4));
        prop_assert_eq!(code.tc(long), code.tc(20));
        prop_assert_eq!(code.bc(short_bc), code.bc(2));
        prop_assert_eq!(code.bc(long_bc), code.bc(18));
    }

    #[test]
    fn a_bicentennial_code_is_the_timecode_with_the_place_of_its_year(
        t in instant(),
        n in 2..=18u8,
    ) {
        let code = TimeCode::try_from(t).unwrap();
        let years = usize::from(code.descriptor().year % 200);
        let place = format!("{}{}", &B1[years / 10..=years / 10], years % 10);

        prop_assert_eq!(code.bc(n), place + &code.tc(n + 2)[4..]);
        prop_assert_eq!(code.bc4(), code.bc(4));
    }

    // Near a time no more than 100 years after it, or less than 100 before,
    // a bicentennial code reads as the TimeCode it comes from does.
    #[test]
    fn a_bicentennial_code_reads_near_its_time_as_its_timecode_does(
        t in instant(),
        n in 2..=18u8,
        offset in -99..=100i32,
    ) {
        let code = TimeCode::try_from(t).unwrap();
        let year = i32::from(code.descriptor().year);
        let near_year = (year + offset).clamp(0, 9999);
        let near = TimeCode::parse(&format!("{near_year:04}")).unwrap();

        prop_assert_eq!(
            TimeCode::parse_bc_near(&code.bc(n), near).unwrap(),
            TimeCode::parse(&code.tc(n + 2)).unwrap(),
        );
    }

    #[test]
    fn a_bicentennial_code_reads_as_the_nearest_year_in_its_cycle(
        t in instant(),
        near_year in 0..=9999i32,
    ) {
        let code = TimeCode::try_from(t).unwrap();
        let near = TimeCode::parse(&format!("{near_year:04}")).unwrap();

        match TimeCode::parse_bc_near(&code.bc4(), near) {
            Ok(read) => {
                let year = i32::from(read.descriptor().year);

                prop_assert!((-100..100).contains(&(year - near_year)));
                prop_assert_eq!(
                    year % 200,
                    i32::from(code.descriptor().year % 200),
                );
            }
            // The nearest year may be before 0000 or after 9999.
            Err(e) => {
                prop_assert_eq!(e, Error::YearOutOfRange);
                prop_assert!(!(100..9900).contains(&near_year));
            }
        }
    }

    #[test]
    fn a_time_with_an_offset_has_the_same_code_as_its_utc_time(
        t in instant(),
        offset in select(vec![(0, 0), (-5, 0), (5, 30), (5, 45)]),
    ) {
        let offset = UtcOffset::from_hms(offset.0, offset.1, 0).unwrap();
        let shifted = t.checked_to_offset(offset);

        // Near the ends of the years TimeCodes cover, the time with an
        // offset may be out of the time crate's range.
        prop_assume!(shifted.is_some());

        prop_assert_eq!(
            TimeCode::try_from(shifted.unwrap()).unwrap(),
            TimeCode::try_from(t).unwrap(),
        );
    }

    #[test]
    fn a_code_past_the_end_of_its_half_month_reads_as_its_last_second(
        d in descriptor(),
    ) {
        let offset = |d: Descriptor| {
            (u32::from(d.block) * 200) + u32::from(d.seconds)
        };
        let t: UtcDateTime = TimeCode::try_from(d).unwrap().into();
        let read = TimeCode::try_from(t).unwrap().descriptor();

        prop_assert_eq!(read.year, d.year);
        prop_assert_eq!(read.half_month, d.half_month);
        prop_assert_eq!(read.nanoseconds, d.nanoseconds);

        if offset(read) < offset(d) {
            // One second later is in the next half month, which may be past
            // the year 9999, where the time crate's range ends.
            let is_past = match t.checked_add(Duration::seconds(1)) {
                Some(next) if next.year() <= 9999 => {
                    TimeCode::try_from(next).unwrap().descriptor().half_month
                        != d.half_month
                }
                _ => true,
            };
            prop_assert!(is_past, "{d:?}: {t}");
        } else {
            prop_assert_eq!(read, d);
        }
    }
}
