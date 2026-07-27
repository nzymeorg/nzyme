use std::collections::HashMap;
use std::net::IpAddr;
use std::sync::MutexGuard;
use chrono::{DateTime, Utc};
use serde::Serialize;
use crate::protocols::parsers::l4_key::L4Key;
use crate::wired::packets::{RtspMediaDescription, RtspMediaLocator, RtspStream};

#[derive(Serialize)]
pub struct RtspStreamsReport {
    pub streams: Vec<RtspStreamReport>
}

#[derive(Serialize)]
pub struct RtspStreamReport {
    pub setup_source_address: String,
    pub setup_source_port: u16,
    pub setup_destination_address: String,
    pub setup_destination_port: u16,
    pub setup_connection_status: String,
    pub setup_established_at: DateTime<Utc>,
    pub setup_terminated_at: Option<DateTime<Utc>>,
    pub setup_most_recent_segment_time: DateTime<Utc>,
    pub state: String,
    pub media_locator: Option<RtspMediaLocatorReport>,
    pub request_uri: Option<String>,
    pub client_agent: Option<String>,
    pub server_info: Option<String>,
    pub authentication: String,
    pub media_description: Option<RtspMediaDescriptionReport>,
    pub flags: Vec<String>
}

#[derive(Serialize)]
pub struct RtspMediaDescriptionReport {
    pub has_video: bool,
    pub has_audio: bool,
    pub video_codec: Option<String>,
    pub audio_codec: Option<String>,
    pub resolution: Option<String>
}

impl From<&RtspMediaDescription> for RtspMediaDescriptionReport {
    fn from(m: &RtspMediaDescription) -> Self {
        RtspMediaDescriptionReport {
            has_video: m.has_video,
            has_audio: m.has_audio,
            video_codec: m.video_codec.clone(),
            audio_codec: m.audio_codec.clone(),
            resolution: m.resolution.clone()
        }
    }
}

#[derive(Serialize)]
#[serde(tag = "type")]
pub enum RtspMediaLocatorReport {
    Interleaved,

    Udp {
        client_rtp_port: u16,
        client_rtcp_port: Option<u16>,
        server_rtp_port: Option<u16>,
        server_rtcp_port: Option<u16>,
        redirect_destination: Option<IpAddr>
    },

    Multicast { group: IpAddr, port: u16 }
}

impl From<&RtspMediaLocator> for RtspMediaLocatorReport {
    fn from(m: &RtspMediaLocator) -> Self {
        match m {
            RtspMediaLocator::Interleaved => Self::Interleaved,
            RtspMediaLocator::Udp {
                client_rtp_port, client_rtcp_port,
                server_rtp_port, server_rtcp_port,
                redirect_destination,
            } => Self::Udp {
                client_rtp_port: *client_rtp_port,
                client_rtcp_port: *client_rtcp_port,
                server_rtp_port: *server_rtp_port,
                server_rtcp_port: *server_rtcp_port,
                redirect_destination: *redirect_destination,
            },
            RtspMediaLocator::Multicast { group, port } =>
                Self::Multicast { group: *group, port: *port },
        }
    }
}

pub fn generate(s: &MutexGuard<HashMap<L4Key, RtspStream>>) -> RtspStreamsReport {
    let mut streams: Vec<RtspStreamReport> = Vec::new();

    for stream in s.values() {
        streams.push(RtspStreamReport {
            setup_source_address: stream.setup_source_address.to_string(),
            setup_source_port: stream.setup_source_port,
            setup_destination_address: stream.setup_destination_address.to_string(),
            setup_destination_port: stream.setup_destination_port,
            setup_connection_status: stream.setup_connection_status.to_string(),
            setup_established_at: stream.setup_established_at,
            setup_terminated_at: stream.setup_terminated_at,
            setup_most_recent_segment_time: stream.setup_most_recent_segment_time,
            state: stream.state.to_string(),
            media_locator: stream.media.as_ref().map(RtspMediaLocatorReport::from),
            request_uri: stream.request_uri.clone(),
            client_agent: stream.client_agent.clone(),
            server_info: stream.server_info.clone(),
            authentication: stream.auth.to_string(),
            media_description: stream.media_desc.as_ref().map(RtspMediaDescriptionReport::from),
            flags: stream.flags.iter().map(ToString::to_string).collect(),
        })
    }

    RtspStreamsReport { streams }
}