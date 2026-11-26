use std::sync::Arc;

use anyhow::Error;

use crate::protocols::parsers::dot11::management::management_frame_tools::{parse_disconnection_details, ManagementHeader};
use crate::wireless::dot11::frames::{Dot11DeauthenticationFrame, Dot11Frame};

pub fn parse(frame: &Arc<Dot11Frame>) -> Result<Dot11DeauthenticationFrame, Error> {
    let header = ManagementHeader::new(&frame.payload)?;

    Ok(Dot11DeauthenticationFrame {
        length: frame.length,
        header: frame.header.clone(),
        destination: header.receiver(),
        transmitter: header.transmitter(),
        bssid: header.bssid(),
        details: parse_disconnection_details(&header)?
    })
}
