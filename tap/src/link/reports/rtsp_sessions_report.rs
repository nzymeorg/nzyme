use std::net::IpAddr;
use chrono::{DateTime, Utc};
use serde::Serialize;

#[derive(Serialize)]
pub struct RtspSessionsReport {
    pub sessions: Vec<RtspSessionReport>
}

#[derive(Serialize)]
pub struct RtspSessionReport {
    pub source_address: String,
    pub source_port: u16,
    pub destination_address: String,
    pub destination_port: u16,
    pub established_at: DateTime<Utc>,

    pub state: String,
    pub media: Option<RtspMediaLocatorReport>,
    pub request_uri: Option<String>,
    pub client_agent: Option<String>,
    pub server_info: Option<String>,
    pub auth: String,
    pub media_desc: Option<RtspMediaDescriptionReport>,
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

#[derive(Serialize)]
pub enum RtspMediaLocatorReport {
    Interleaved,

    Udp {
        client_rtp_port: u16,
        client_rtcp_port: Option<u16>,
        server_rtp_port: Option<u16>,
        server_rtcp_port: Option<u16>,
        redirect_destination: Option<IpAddr>,
    },

    Multicast { group: IpAddr, port: u16 }
}