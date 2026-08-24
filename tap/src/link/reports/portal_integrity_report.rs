use chrono::{DateTime, Utc};
use serde::Serialize;
use serde_with::{serde_as, base64::Base64};
use crate::wired::portalintegrity::probe::ProbeResult;

#[derive(Serialize)]
pub struct PortalIntegrityReport {
    pub probe_name: String,
    pub control_url: String,
    pub context: ContextReport,
    pub hops: Vec<HopReport>,
    pub error: Option<String>,
    pub probed_at: DateTime<Utc>
}

#[derive(Serialize)]
pub struct ContextReport {
    pub network_interface: String,
    pub mac: String,
    pub assigned_cidr: String,
    pub gateway: Option<String>,
    pub dhcp_server: Option<String>,
    pub dns_servers: Vec<String>
}

#[serde_as]
#[derive(Serialize)]
pub struct HopReport {
    pub url: String,
    pub resolved_ip: String,
    pub status: u16,
    pub followed_to: Option<String>,
    #[serde_as(as = "Base64")]
    pub raw: Vec<u8>,
    pub completeness: String,
    pub tls: Option<TlsReport>
}

#[serde_as]
#[derive(Serialize)]
pub struct TlsReport {
    #[serde_as(as = "Vec<Base64>")]
    pub chain_der: Vec<Vec<u8>>,
    pub leaf_sha256: String,
    pub protocol_version: Option<String>,
    pub cipher_suite: Option<u16>,
    pub sni: Option<String>
}

pub fn generate(p: &ProbeResult) -> PortalIntegrityReport {
    let hops = p.hops.iter().map(|h| {
        let tls = h.tls.clone().map(|tls| {
            TlsReport {
                chain_der: tls.chain_der.clone(),
                leaf_sha256: tls.leaf_sha256.to_string(),
                protocol_version: tls.protocol_version.clone(),
                cipher_suite: tls.cipher_suite,
                sni: tls.sni,
            }
        });

        HopReport {
            url: h.url.clone(),
            resolved_ip: h.resolved_ip.clone(),
            status: h.status,
            followed_to: h.followed_to.clone(),
            raw: h.raw.clone(),
            completeness: h.completeness.to_string(),
            tls,
        }
    }).collect();

    let context = ContextReport {
        network_interface: p.context.interface.clone(),
        mac: p.context.mac.clone(),
        assigned_cidr: p.context.assigned_cidr.clone(),
        gateway: p.context.gateway.clone(),
        dhcp_server: p.context.dhcp_server.clone(),
        dns_servers: p.context.dns_servers.clone(),
    };

    PortalIntegrityReport {
        probe_name: p.probe_name.clone(),
        control_url: p.control_url.clone(),
        context,
        hops,
        error: p.error.clone(),
        probed_at: p.probed_at.clone(),
    }
}