use crate::protocols::detection::taggers::network_protocols::tls_tagger::TlsHandshake;
use crate::protocols::detection::taggers::network_protocols::vpn_tagger_tools::build_tcp_tunnel;
use crate::protocols::detection::taggers::network_protocols::wireguard_tagger::PROVIDER_TAILSCALE;
use crate::state::tables::tcp_table::TcpSession;
use crate::wired::packets::{VpnHandshakeStatus, VpnTunnel, VpnType};

/*
 * Tailscale clients that cannot reach a peer directly relay WireGuard packets through DERP
 * servers. DERP runs inside TLS to the relay, and the only thing visible on the wire is the
 * TLS handshake with the relay hostname as server name. Tailscale also keeps one connection
 * to its home DERP region open at all times, even when all peers are reached directly.
 *
 * Tailscale's relays are named derp<region><optional letter>.tailscale.com, for example
 * derp1.tailscale.com or derp1b.tailscale.com. Self-hosted relays are not recognized.
 */
const DERP_HOSTNAME_PREFIX: &str = "derp";
const DERP_HOSTNAME_SUFFIX: &str = ".tailscale.com";

pub fn tag_tcp(handshake: &TlsHandshake, session: &TcpSession) -> Option<VpnTunnel> {
    let server_name = handshake.client_hello.as_ref()?.server_name.as_ref()?;

    if !is_derp_server_name(server_name) {
        return None;
    }

    // The relay accepted the connection once it answered the ClientHello.
    let handshake_status = if handshake.server_hello.is_some() {
        VpnHandshakeStatus::Complete
    } else {
        VpnHandshakeStatus::Partial
    };

    let mut tunnel = build_tcp_tunnel(session, VpnType::DERP, handshake_status, vec![]);
    tunnel.provider = Some(PROVIDER_TAILSCALE.to_string());
    tunnel.server_name = Some(server_name.to_lowercase());

    Some(tunnel)
}

fn is_derp_server_name(name: &str) -> bool {
    let name = name.to_lowercase();

    let middle = match name.strip_prefix(DERP_HOSTNAME_PREFIX)
        .and_then(|n| n.strip_suffix(DERP_HOSTNAME_SUFFIX)) {
        Some(m) => m,
        None => return false
    };

    // Region number, optionally followed by a single letter for additional nodes in a region.
    let digits = middle.trim_end_matches(|c: char| c.is_ascii_lowercase());
    let letters = middle.len() - digits.len();

    !digits.is_empty() && digits.chars().all(|c| c.is_ascii_digit()) && letters <= 1
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn recognizes_tailscale_relays() {
        assert!(is_derp_server_name("derp1.tailscale.com"));
        assert!(is_derp_server_name("derp1b.tailscale.com"));
        assert!(is_derp_server_name("derp22.tailscale.com"));
        assert!(is_derp_server_name("DERP10C.tailscale.com"));
    }

    #[test]
    fn rejects_other_hostnames() {
        assert!(!is_derp_server_name("derp.tailscale.com"));
        assert!(!is_derp_server_name("derpab.tailscale.com"));
        assert!(!is_derp_server_name("controlplane.tailscale.com"));
        assert!(!is_derp_server_name("login.tailscale.com"));
        assert!(!is_derp_server_name("derp1.tailscale.com.example.org"));
        assert!(!is_derp_server_name("derp1.example.org"));
        assert!(!is_derp_server_name(""));
    }
}
