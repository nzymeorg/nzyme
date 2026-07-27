use std::collections::HashMap;
use std::net::IpAddr;
use std::sync::MutexGuard;
use chrono::{DateTime, Utc};
use serde::Serialize;
use crate::protocols::parsers::l4_key::L4Key;
use crate::wired::packets::{RtspMediaDescription, RtspMediaLocator, RtspSession};

#[derive(Serialize)]
pub struct RtspSessionsReport {
    pub sessions: Vec<RtspSessionReport>
}

#[derive(Serialize)]
pub struct RtspSessionReport {
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

pub fn generate(s: &MutexGuard<HashMap<L4Key, RtspSession>>) -> RtspSessionsReport {
    let mut sessions: Vec<RtspSessionReport> = Vec::new();

    for session in s.values() {
        sessions.push(RtspSessionReport {
            setup_source_address: session.setup_source_address.to_string(),
            setup_source_port: session.setup_source_port,
            setup_destination_address: session.setup_destination_address.to_string(),
            setup_destination_port: session.setup_destination_port,
            setup_connection_status: session.setup_connection_status.to_string(),
            setup_established_at: session.setup_established_at,
            setup_terminated_at: session.setup_terminated_at,
            setup_most_recent_segment_time: session.setup_most_recent_segment_time,
            state: session.state.to_string(),
            media_locator: session.media.as_ref().map(RtspMediaLocatorReport::from),
            request_uri: session.request_uri.clone(),
            client_agent: session.client_agent.clone(),
            server_info: session.server_info.clone(),
            authentication: session.auth.to_string(),
            media_description: session.media_desc.as_ref().map(RtspMediaDescriptionReport::from),
            flags: session.flags.iter().map(ToString::to_string).collect(),
        })
    }

    RtspSessionsReport { sessions }
}