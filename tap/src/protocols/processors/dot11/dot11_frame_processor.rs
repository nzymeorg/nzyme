use std::collections::HashMap;
use std::panic;
use std::sync::{Arc, Mutex};

use log::{error, info, trace};

use crate::wireless::dot11::frames::{Dot11BeaconFrame, Dot11ChannelSwitchActionFrame, Dot11DataFrame, Dot11DeauthenticationFrame, Dot11Frame, Dot11ProbeRequestFrame, FrameSubType};
use crate::alerting::alert_types::{Dot11Alert, AlertAttribute, Dot11AlertType};
use crate::messagebus::bus::Bus;
use crate::metrics::Metrics;
use crate::wireless::dot11::frames::{Dot11DisassociationFrame, Dot11ProbeResponseFrame, PwnagotchiData};
use crate::protocols::parsers::dot11::management::{action_frame_parser, disassociation_frame_parser, probe_response_frame_parser};
use crate::protocols::parsers::dot11::data::data_frame_parser;
use crate::protocols::parsers::dot11::management::{beacon_frame_parser, deauthentication_frame_parser, probe_request_frame_parser};
use crate::state::tables::dot11_table::Dot11Table;

pub struct Dot11FrameProcessor {
    dot11_table: Arc<Mutex<Dot11Table>>,
    metrics: Arc<Mutex<Metrics>>,
    generic_bus: Arc<Bus>
}

impl Dot11FrameProcessor {

    pub fn new(dot11_table: Arc<Mutex<Dot11Table>>, metrics: Arc<Mutex<Metrics>>, generic_bus: Arc<Bus>) -> Self {
        Self { dot11_table, metrics, generic_bus }
    }

    pub fn process(&self, frame: Arc<Dot11Frame>) {
        let result = panic::catch_unwind(|| {
            match frame.frame_type {
                FrameSubType::Beacon => {
                    match beacon_frame_parser::parse(&frame, self.generic_bus.clone(), self.metrics.clone()) {
                        Ok(frame) => self.handle_beacon_frame(frame),
                        Err(e) => trace!("Could not parse beacon frame: {}", e)
                    }
                },
                FrameSubType::ProbeResponse => {
                    match probe_response_frame_parser::parse(&frame, self.generic_bus.clone(), self.metrics.clone()) {
                        Ok(frame) => self.handle_probe_response_frame(frame),
                        Err(e) => trace!("Could not parse probe response frame: {}", e)
                    }
                },
                FrameSubType::Deauthentication => {
                    match deauthentication_frame_parser::parse(&frame) {
                        Ok(frame) => self.handle_deauthentication_frame(frame),
                        Err(e) => trace!("Could not parse deauthentication frame: {}", e)
                    }
                },
                FrameSubType::Disassocation => {
                    match disassociation_frame_parser::parse(&frame) {
                        Ok(frame) => self.handle_disassociation_frame(frame),
                        Err(e) => trace!("Could not parse disassociation frame: {}", e)
                    }
                }
                FrameSubType::Action => {
                    match action_frame_parser::parse_channel_switch(&frame) {
                        Ok(channel_switch) => self.handle_action_frame(channel_switch),
                        Err(e) => {
                            trace!("Could not parse action frame: {}", e);
                            self.handle_action_frame(None);
                        }
                    }
                },
                FrameSubType::ProbeRequest => {
                    match probe_request_frame_parser::parse(&frame) {
                        Ok(frame) => self.handle_probe_request_frame(frame),
                        Err(e) => trace!("Could not parse probe response frame: {}", e)
                    }
                },
                FrameSubType::Data |
                FrameSubType::Null |
                FrameSubType::QosData |
                FrameSubType::QosNull |
                FrameSubType::QosDataCfAck |
                FrameSubType::QosDataCfPoll |
                FrameSubType::QosDataCfAckCfPoll |
                FrameSubType::QosCfPoll |
                FrameSubType::QosCfAckCfPoll => {
                    match data_frame_parser::parse(&frame) {
                        Ok(frame) => self.handle_data_frame(frame),
                        Err(e) => trace!("Could not parse data frame: {}", e)
                    }
                },
                FrameSubType::AssociationRequest |
                FrameSubType::AssociationResponse |
                FrameSubType::ReAssociationRequest |
                FrameSubType::ReAssociationResponse |
                FrameSubType::TimingAdvertisement |
                FrameSubType::Atim |
                FrameSubType::Authentication |
                FrameSubType::ActionNoAck |
                FrameSubType::Trigger |
                FrameSubType::Tack |
                FrameSubType::BeamformingReportPoll |
                FrameSubType::VhtHeNdpAnnouncement |
                FrameSubType::ControlFrameExtension |
                FrameSubType::ControlWrapper |
                FrameSubType::BlockAckRequest |
                FrameSubType::BlockAck |
                FrameSubType::PsPoll |
                FrameSubType::Rts |
                FrameSubType::Cts |
                FrameSubType::Ack |
                FrameSubType::CfEnd |
                FrameSubType::CfEndCfAck |
                FrameSubType::DmgBeacon |
                FrameSubType::S1gBeacon |
                FrameSubType::Reserved |
                FrameSubType::Invalid => { /* ignored */ }
            }
        });

        /*
         * Some frames will be malformed even though the integrity check passed. Log silently.
         * Passing of such frames appears to be chipset/driver specific.
         */
        if result.is_err() {
            info!("Frame parsing error: {:?}. Frame was: {:?}", result, frame);
        }
    }

    fn handle_beacon_frame(&self, frame: Dot11BeaconFrame) {
        match self.dot11_table.lock() {
            Ok(mut table) => {
                if let Some(pwnagotchi) = &frame.tagged_parameters.pwnagotchi_data {
                    // Pwnagotchi payload detected in tagged parameters. Raise alert.

                    let signal_strength = frame.header.antenna_signal.unwrap_or_else(|| -1);

                    table.register_alert(Dot11Alert {
                        alert_type: Dot11AlertType::PwnagotchiDetected,
                        attributes: build_pwnagotchi_alert_attributes(pwnagotchi),
                        signal_strength
                    })
                }

                table.register_beacon_frame(frame);
            },
            Err(e) => { error!("Could not acquire 802.11 table lock: {}", e); }
        }
    }

    fn handle_probe_response_frame(&self, frame: Dot11ProbeResponseFrame) {
        match self.dot11_table.lock() {
            Ok(mut table) => table.register_probe_response_frame(frame),
            Err(e) => error!("Could not acquire 802.11 table lock: {}", e)
        }
    }

    fn handle_probe_request_frame(&self, frame: Dot11ProbeRequestFrame) {
        match self.dot11_table.lock() {
            Ok(table) => table.register_probe_request_frame(frame),
            Err(e) => error!("Could not acquire 802.11 table lock: {}", e)
        }
    }

    fn handle_data_frame(&self, frame: Dot11DataFrame) {
        match self.dot11_table.lock() {
            Ok(table) => table.register_data_frame(frame),
            Err(e) => error!("Could not acquire 802.11 table lock: {}", e)
        }
    }

    fn handle_deauthentication_frame(&self, frame: Dot11DeauthenticationFrame) {
        match self.dot11_table.lock() {
            Ok(table) => table.register_deauthentication_frame(frame),
            Err(e) => error!("Could not acquire 802.11 table lock: {}", e)
        }
    }

    fn handle_action_frame(&self, channel_switch: Option<Dot11ChannelSwitchActionFrame>) {
        match self.dot11_table.lock() {
            Ok(table) => table.register_action_frame(channel_switch),
            Err(e) => error!("Could not acquire 802.11 table lock: {}", e)
        }
    }

    fn handle_disassociation_frame(&self, frame: Dot11DisassociationFrame) {
        match self.dot11_table.lock() {
            Ok(table) => table.register_disassociation_frame(frame),
            Err(e) => error!("Could not acquire 802.11 table lock: {}", e)
        }
    }

}

fn build_pwnagotchi_alert_attributes(data: &PwnagotchiData) -> HashMap<String, AlertAttribute> {
    let mut attributes = HashMap::new();

    attributes.insert("identity".to_string(), AlertAttribute::String(data.identity.clone()));
    attributes.insert("name".to_string(), AlertAttribute::String(data.name.clone()));
    attributes.insert("uptime".to_string(), AlertAttribute::Number(data.uptime));
    attributes.insert("pwnd_run".to_string(), AlertAttribute::Number(data.pwnd_run));
    attributes.insert("pwnd_tot".to_string(), AlertAttribute::Number(data.pwnd_tot));
    attributes.insert("version".to_string(), AlertAttribute::String(data.version.clone()));

    attributes
}