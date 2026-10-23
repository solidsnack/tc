// Runs specification/tests.yaml.

use std::fs;

use time::format_description::well_known::Rfc3339;
use time::{OffsetDateTime, UtcDateTime};
use timecode::TimeCode;
use yaml_rust2::{Yaml, YamlLoader};

fn suite() -> Yaml {
    let path =
        concat!(env!("CARGO_MANIFEST_DIR"), "/../specification/tests.yaml");
    let text = fs::read_to_string(path).expect("specification/tests.yaml");

    YamlLoader::load_from_str(&text)
        .expect("valid YAML")
        .remove(0)
}

fn cases<'a>(
    suite: &'a Yaml,
    group: &str,
    kind: &str,
) -> Vec<(&'a str, &'a str)> {
    suite[group][kind]
        .as_vec()
        .expect("a list of cases")
        .iter()
        .map(|case| {
            let input = case["input"].as_str().expect("an input");
            let output = case["output"].as_str().expect("an output");
            (input, output)
        })
        .collect()
}

fn instant(s: &str) -> UtcDateTime {
    OffsetDateTime::parse(s, &Rfc3339)
        .expect("an RFC 3339 timestamp")
        .to_utc()
}

// Print a time with each method that gives a code of this group's length.
fn encodings(group: &str, code: TimeCode, length: usize) -> Vec<String> {
    let n = length as u8;

    match group {
        "tc5" => vec![code.tc5()],
        "tc8" => vec![code.tc8()],
        "tcx" => vec![code.tcx(0)],
        _ if n >= 12 => vec![code.tc(n), code.tcx(n - 11)],
        _ => vec![code.tc(n)],
    }
}

fn run(group: &str) {
    let suite = suite();
    let mut failures = vec![];

    for (input, output) in cases(&suite, group, "decode") {
        let t: UtcDateTime = TimeCode::parse(input).expect(input).into();

        if t != instant(output) {
            failures.push(format!("decode {input}: {t}, not {output}"));
        }
    }

    for (input, output) in cases(&suite, group, "encode") {
        let code = TimeCode::try_from(instant(input)).expect(input);

        for s in encodings(group, code, output.len()) {
            if s != output {
                failures.push(format!("encode {input}: {s}, not {output}"));
            }
        }
    }

    assert!(failures.is_empty(), "{}", failures.join("\n"));
}

#[test]
fn tc5() {
    run("tc5")
}

#[test]
fn tc8() {
    run("tc8")
}

#[test]
fn tcx() {
    run("tcx")
}

#[test]
fn tcn() {
    run("tcn")
}

// The time crate has no leap seconds: it reads 23:59:60 as 23:59:59 and
// 999,999,999 nanoseconds. TimeCodes treat a leap second as the last second
// of its half month, so both ways round, the leap seconds land in the second
// 23:59:59.
#[test]
fn leap_seconds() {
    let suite = suite();
    let mut failures = vec![];

    for (input, output) in cases(&suite, "leap-seconds", "decode") {
        let t: UtcDateTime = TimeCode::parse(input).expect(input).into();

        if t != instant(&output.replace(":60", ":59")) {
            failures.push(format!("decode {input}: {t}, not {output}"));
        }
    }

    for (input, output) in cases(&suite, "leap-seconds", "encode") {
        let t = instant(input);
        let code = TimeCode::try_from(t).expect(input);
        let expected = TimeCode::parse(output).expect(output);

        assert_eq!(t, instant(&input.replace(":60", ":59.999999999")));

        if code.tcx(0) != expected.tcx(0) {
            failures.push(format!("encode {input}: {}", code.tcx(0)));
        }
    }

    assert!(failures.is_empty(), "{}", failures.join("\n"));
}
