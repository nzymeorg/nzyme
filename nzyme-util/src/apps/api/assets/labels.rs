use crate::apps::api::assets::worldmap_data::COUNTRIES;

pub fn dimension_title(dimension: &str) -> &str {
    match dimension {
        "TCP_FINGERPRINT" => "TCP Fingerprint",
        "JA4" => "JA4 (TLS Client)",
        "DHCP_FINGERPRINT" => "DHCP Fingerprint",
        "DHCP_VENDOR_CLASS" => "DHCP Vendor Class",
        "DHCP6_FINGERPRINT" => "DHCPv6 Fingerprint",
        "DHCP6_CLIENT_DUID" => "DHCPv6 Client DUID",
        "NTP_CLIENT_VERSION" => "NTP Client Version",
        "TLS_VERSION" => "TLS Version",
        "PROTOCOL_CLIENT" => "Protocol (as Client)",
        "PROTOCOL_SERVER" => "Protocol (as Server)",
        "INBOUND_PORT" => "Inbound Port",
        "INBOUND_PORT_PROTOCOL" => "Protocol on Inbound Port",
        "EXTERNAL_ASN" => "External Network",
        "EXTERNAL_COUNTRY" => "External Country",
        "GATEWAY_MAC" => "Gateway",
        "DHCP_SERVER" => "DHCP Server",
        "DNS_SERVER" => "DNS Server",
        "NTP_SERVER" => "NTP Server",
        "ROLE" => "Role",
        "NAME_RESOLUTION_PROTOCOL" => "Name Resolution Protocol",
        "TAP" => "Tap",
        other => other,
    }
}

pub fn role_title(role: &str) -> &str {
    match role {
        "GATEWAY" => "Gateway",
        "DHCP_SERVER" => "DHCP Server",
        "DNS_SERVER" => "DNS Server",
        "NTP_SERVER" => "NTP Server",
        "NAME_RESOLUTION_RESPONDER" => "Name Resolution Responder",
        "SSH_SERVER" => "SSH Server",
        "TLS_SERVER" => "TLS Server",
        "HTTP_SERVER" => "HTTP Server",
        "SOCKS_SERVER" => "SOCKS Server",
        "RTSP_SERVER" => "RTSP Server",
        "STUN_SERVER" => "STUN Server",
        "TURN_SERVER" => "TURN Server",
        "VPN_SERVER" => "VPN Server",
        "IPV6_TUNNEL_SERVER" => "IPv6 Tunnel Server",
        other => other,
    }
}

pub fn alert_role_title(role: &str) -> &str {
    match role {
        "ACTOR" => "Actor",
        "VICTIM" => "Victim",
        "ACTOR_AND_VICTIM" => "Actor and Victim",
        _ => "Subject",
    }
}

pub fn risk_signal(signal: &str) -> (&str, &str) {
    match signal {
        "PLAINTEXT_HTTP" => ("Plaintext HTTP", "Initiated unencrypted HTTP sessions."),
        "PLAINTEXT_RTSP" => ("Plaintext RTSP", "Initiated unencrypted RTSP streaming sessions."),
        "LEGACY_TLS" => ("Legacy TLS", "Negotiated TLS versions that are no longer considered secure."),
        "SELF_SIGNED_TLS_SERVERS" => ("Self-Signed TLS Servers", "Connected to TLS servers presenting self-signed certificates. Only visible for TLS 1.2 and older, because TLS 1.3 encrypts certificates."),
        "UNTRUSTED_TLS_CHAINS" => ("Untrusted TLS Chains", "Connected to TLS servers with certificate chains that failed validation. Only visible for TLS 1.2 and older, because TLS 1.3 encrypts certificates."),
        "INBOUND_EXTERNAL_SESSIONS" => ("Inbound from the Internet", "Accepted sessions that were started by addresses outside the local network."),
        "DNS_TUNNEL" => ("DNS Tunnel", "DNS tunneling detected from this asset."),
        "ICMP_TUNNEL" => ("ICMP Tunnel", "ICMP tunneling detected from this asset."),
        "BEACON_MALICIOUS" => ("Malicious Beacon", "Source of a beacon classified as malicious."),
        "BEACON_SUSPICIOUS" => ("Suspicious Beacon", "Source of a beacon classified as suspicious."),
        "BEACON_UNCLASSIFIED" => ("Unclassified Beacon", "Source of a beacon that has not been classified yet."),
        "RECON_SCANNER" => ("Network Scanner", "Performed network scans."),
        "ICMP_REDIRECT_SENDER" => ("ICMP Redirect Sender", "Sent ICMP redirects to other hosts."),
        "TRACEROUTE_SOURCE" => ("Traceroute Source", "Performed traceroutes."),
        "SPOOFING_ARP_ACTOR" => ("ARP Spoofer", "Detected as the source of ARP poisoning."),
        "SPOOFING_DHCP_ACTOR" => ("Rogue DHCP Server", "Detected as a rogue or misbehaving DHCP server."),
        "SPOOFING_DNS_ACTOR" => ("DNS Spoofer", "Detected as the source of forged DNS responses."),
        "SPOOFING_NAME_RESOLUTION_ACTOR" => ("Name Resolution Poisoner", "Detected as the source of name resolution poisoning."),
        "ACTIVE_ALERTS" => ("Active Alerts", "Involved in alerts that are currently active."),
        "RANDOMIZED_MAC" => ("Randomized MAC Address", "The MAC address is locally administered, which usually means randomization. Identity may change."),
        "NO_CONTEXT" => ("No Context", "No MAC address context exists. This asset is not described or approved by an analyst."),
        other => (other, ""),
    }
}

pub fn finding_title(finding_type: &str) -> &str {
    match finding_type {
        "RECON_SCAN" => "Network Scans",
        "BEACON_UNKNOWN" => "Beacons (Unclassified)",
        "BEACON_BENIGN" => "Beacons (Benign)",
        "BEACON_SUSPICIOUS" => "Beacons (Suspicious)",
        "BEACON_MALICIOUS" => "Beacons (Malicious)",
        "DNS_TUNNEL" => "DNS Tunnels",
        "ICMP_TUNNEL" => "ICMP Tunnels",
        "ICMP_TRACEROUTE" => "Traceroutes",
        "ICMP_REDIRECT" => "ICMP Redirects",
        "SPOOFING_ARP" => "ARP and NDP Spoofing",
        "SPOOFING_DHCP" => "DHCP and Router Spoofing",
        "SPOOFING_DNS" => "DNS Spoofing",
        "SPOOFING_NAME_RESOLUTION" => "Name Resolution Spoofing",
        other => other,
    }
}

pub fn exclusion_title(exclusion_type: &str) -> &str {
    match exclusion_type {
        "DHCP_FINGERPRINT" => "DHCP Fingerprint",
        "SPOOFING" => "Spoofing",
        "BEACON" => "Beacons",
        "ICMP" => "ICMP",
        "RECON" => "Recon",
        "DNS_TUNNEL" => "DNS Tunnels",
        other => other,
    }
}

pub fn timeline_kind_title(event_type: &str, detail: Option<&str>) -> String {
    match event_type {
        "ASSET_FIRST_SEEN" => "First Seen".to_string(),
        "HOSTNAME" => "Hostname".to_string(),
        "IP_ADDRESS" => "IP Address".to_string(),
        "ALERT" => "Alert".to_string(),
        "ATTRIBUTE" => dimension_title(detail.unwrap_or("")).to_string(),
        other => other.to_string(),
    }
}

pub struct Change {
    pub action: String,
    pub value: Option<String>,
    pub explanation: Option<String>,
}

pub fn describe_change(event_type: &str, value: Option<&str>, detail: Option<&str>) -> Change {
    let value_string = value.map(|v| v.to_string());
    let via = detail.unwrap_or("an unknown source");

    match event_type {
        "ASSET_FIRST_SEEN" => Change {
            action: "First seen".to_string(), value: None,
            explanation: Some("The asset appeared on the network for the first time.".to_string()),
        },
        "HOSTNAME" => Change {
            action: "New hostname".to_string(), value: value_string,
            explanation: Some(format!("The asset was seen under a hostname it had not used before, learned via {}.", via)),
        },
        "IP_ADDRESS" => Change {
            action: "New IP address".to_string(), value: value_string,
            explanation: Some(format!("The asset used an IP address it had not used before, learned via {}.", via)),
        },
        "ALERT" => Change {
            action: "Alert".to_string(), value: value.map(|v| v.replace('_', " ")),
            explanation: Some("A detection alert involved this asset.".to_string()),
        },
        "ATTRIBUTE" => {
            let dimension = detail.unwrap_or("");
            let shown = if dimension == "ROLE" { value.map(|v| role_title(v).to_string()) } else { value_string };
            let v = shown.clone().unwrap_or_default();
            let (action, explanation) = match dimension {
                "TCP_FINGERPRINT" => ("New TCP fingerprint", "The asset's TCP stack presented a fingerprint not seen from it before, which can mean a different operating system or network stack.".to_string()),
                "JA4" => ("New TLS client fingerprint", "The asset's TLS client presented a JA4 fingerprint not seen from it before, which usually means different client software.".to_string()),
                "DHCP_FINGERPRINT" => ("New DHCP fingerprint", "The asset sent DHCP requests with an option fingerprint not seen from it before.".to_string()),
                "DHCP_VENDOR_CLASS" => ("New DHCP vendor class", "The asset identified itself to DHCP servers with a vendor class not seen from it before.".to_string()),
                "DHCP6_FINGERPRINT" => ("New DHCPv6 fingerprint", "The asset sent DHCPv6 requests with an option fingerprint not seen from it before.".to_string()),
                "DHCP6_CLIENT_DUID" => ("New DHCPv6 client DUID", "The asset identified itself to DHCPv6 servers with a DUID not seen from it before. A DUID normally stays the same for the lifetime of an installation.".to_string()),
                "NTP_CLIENT_VERSION" => ("New NTP client version", "The asset sent NTP requests with a client version not seen from it before.".to_string()),
                "TLS_VERSION" => ("New TLS version", "The asset negotiated a TLS version it had not used before.".to_string()),
                "PROTOCOL_CLIENT" => ("New client protocol", format!("The asset initiated {} sessions for the first time.", v)),
                "PROTOCOL_SERVER" => ("New server protocol", format!("The asset served {} sessions for the first time.", v)),
                "INBOUND_PORT" => ("New inbound port", "The asset accepted sessions on this port for the first time. This is a port it listens on, not a port it connected to.".to_string()),
                "INBOUND_PORT_PROTOCOL" => ("New protocol on inbound port", "The asset served this protocol on this port for the first time.".to_string()),
                "EXTERNAL_ASN" => ("New external network", "The asset exchanged traffic with this autonomous system for the first time.".to_string()),
                "EXTERNAL_COUNTRY" => ("New external country", "The asset exchanged traffic with addresses in this country for the first time.".to_string()),
                "GATEWAY_MAC" => ("New gateway", "The asset started sending traffic through this gateway.".to_string()),
                "DHCP_SERVER" => ("New DHCP server", "The asset obtained a lease from this DHCP server for the first time.".to_string()),
                "DNS_SERVER" => ("New DNS server", "The asset started sending DNS queries to this server.".to_string()),
                "NTP_SERVER" => ("New NTP server", "The asset started synchronizing its clock with this server.".to_string()),
                "ROLE" => ("New role", "The asset was observed on the server side of the protocol behind this role for the first time.".to_string()),
                "NAME_RESOLUTION_PROTOCOL" => ("New name resolution protocol", format!("The asset answered {} queries for the first time.", v)),
                "TAP" => ("New tap", "This tap observed the asset for the first time.".to_string()),
                _ => {
                    return Change {
                        action: format!("New {}", dimension_title(dimension).to_lowercase()),
                        value: shown, explanation: None,
                    };
                }
            };
            Change { action: action.to_string(), value: shown, explanation: Some(explanation) }
        }
        other => Change { action: other.to_string(), value: value_string, explanation: None },
    }
}

pub fn country_name(code: &str) -> &str {
    COUNTRIES.iter().find(|(c, _, _, _)| *c == code).map(|(_, name, _, _)| *name).unwrap_or(code)
}

pub fn country_centroid(code: &str) -> Option<(f64, f64)> {
    COUNTRIES.iter().find(|(c, _, _, _)| *c == code).map(|(_, _, lat, lng)| (*lat, *lng))
}

pub const UNRESOLVED_EXTERNAL_LABEL: &str = "Internet (unresolved)";

pub fn external_peer_label(asn_name: Option<&str>, asn_number: Option<i64>, country_code: Option<&str>) -> String {
    if let Some(name) = asn_name.filter(|n| !n.is_empty()) {
        return name.to_string();
    }
    if let Some(number) = asn_number {
        return format!("AS{}", number);
    }
    if let Some(code) = country_code.filter(|c| !c.is_empty()) {
        return code.to_string();
    }
    UNRESOLVED_EXTERNAL_LABEL.to_string()
}

pub fn normalize_mac(input: &str) -> Option<String> {
    let hex: String = input.chars().filter(|c| c.is_ascii_hexdigit()).collect();
    let separators = input.chars().filter(|c| !c.is_ascii_hexdigit() && !c.is_whitespace()).count();
    if hex.len() != 12 || input.chars().any(|c| !c.is_ascii_hexdigit() && !matches!(c, ':' | '-' | '.' | ' ')) {
        return None;
    }
    if separators > 5 {
        return None;
    }

    let upper = hex.to_ascii_uppercase();
    Some((0..6).map(|i| &upper[i * 2..i * 2 + 2]).collect::<Vec<_>>().join(":"))
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn normalizes_macs() {
        assert_eq!(normalize_mac("94:83:c4:ce:d3:b0"), Some("94:83:C4:CE:D3:B0".to_string()));
        assert_eq!(normalize_mac("94-83-C4-CE-D3-B0"), Some("94:83:C4:CE:D3:B0".to_string()));
        assert_eq!(normalize_mac("9483.c4ce.d3b0"), Some("94:83:C4:CE:D3:B0".to_string()));
        assert_eq!(normalize_mac("9483c4ced3b0"), Some("94:83:C4:CE:D3:B0".to_string()));
        assert_eq!(normalize_mac("94:83:c4:ce:d3"), None);
        assert_eq!(normalize_mac("not a mac"), None);
    }

    #[test]
    fn describes_changes() {
        let c = describe_change("ATTRIBUTE", Some("DNS"), Some("PROTOCOL_CLIENT"));
        assert_eq!(c.action, "New client protocol");
        assert_eq!(c.explanation.as_deref(), Some("The asset initiated DNS sessions for the first time."));

        let c = describe_change("ATTRIBUTE", Some("DNS_SERVER"), Some("ROLE"));
        assert_eq!(c.value.as_deref(), Some("DNS Server"));

        let c = describe_change("ALERT", Some("DOT11_SOMETHING"), None);
        assert_eq!(c.value.as_deref(), Some("DOT11 SOMETHING"));
    }

    #[test]
    fn knows_countries() {
        assert_eq!(country_name("US"), "United States");
        assert_eq!(country_name("GB"), "United Kingdom");
        assert_eq!(country_name("XX"), "XX");
        assert!(country_centroid("DE").is_some());
    }
}
