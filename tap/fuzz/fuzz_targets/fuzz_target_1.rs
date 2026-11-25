#![no_main]

use std::sync::Arc;
use libfuzzer_sys::fuzz_target;
use nzyme_tap::protocols::parsers::dot11::dot11_header_parser::parse;
use nzyme_tap::wireless::dot11::frames::{Dot11CaptureSource, Dot11RawFrame};

fuzz_target!(|data: &[u8]| {
    // fuzzed code goes here
    let input = data.to_vec();
    let data = Dot11RawFrame { 
        interface_name: "wlan0".to_string(),
        capture_source: Dot11CaptureSource::Acquisition,
        data: input,
        captured_at: std::time::Instant::now()
    };
    let  _ = parse(&Arc::new(data));
});
