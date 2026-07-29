use anyhow::{bail, Error};
use byteorder::{BigEndian, ByteOrder};
use chrono::{Utc};
use log::{debug};
use crate::wired::packets::{GenericConnectionStatus, SocksAuthenticationMethod, SocksAuthenticationResult, SocksConnectionHandshakeStatus, SocksTunnel, SocksType};
use crate::wired::packets::SocksType::{Socks4, Socks4A};
use crate::state::tables::tcp_table::TcpSession;
use crate::protocols::parsers::tcp::tcp_tools::determine_tcp_session_state;
use crate::helpers::network::{string_up_to_null_byte, to_ipv4_address, to_ipv6_address};
use crate::tracemark;

pub fn tag(cts: &[u8], stc: &[u8], session: &TcpSession) -> Option<SocksTunnel> {
    if cts.len() < 2 {
        return None;
    }

    // SOCKS 4(a)?
    if *cts.first().unwrap() == 0x04
        && (*cts.get(1).unwrap() == 0x01 || *cts.get(1).unwrap() == 0x02) {
        
        if cts.len() < 9 || stc.len() < 2 {
            tracemark!("SOCKS");
            return None;
        }

        // Potentially SOCKS 4(a), check if the response was SOCKS as well.
        if *stc.get(1).unwrap() < 0x5A
            || *stc.get(1).unwrap() > 0x5D {
            tracemark!("SOCKS");
            return None
        }

        let tunneled_destination_port = BigEndian::read_u16(&cts[2..4]);

        let tunneled_destination_address = if !is_socks_4a_address(&cts[4..8]) {
            Some(to_ipv4_address(&cts[4..8]))
        } else {
            None
        };

        let username = string_up_to_null_byte(&cts[8..]);

        let cursor = 8 + match &username {
            Some(username) => username.len() + 1,
            None => 1
        };

        if cursor > cts.len() {
            return None;
        }

        let tunneled_destination_host = if tunneled_destination_address.is_none() {
           string_up_to_null_byte(&cts[cursor..])
        } else {
            None
        };

        let handshake_status = match *stc.get(1).unwrap() {
            0x5A => SocksConnectionHandshakeStatus::Granted,
            0x5B => SocksConnectionHandshakeStatus::Rejected,
            0x5C => SocksConnectionHandshakeStatus::FailedIdentdUnreachable,
            0x5D => SocksConnectionHandshakeStatus::FailedIdentdAuth,
            _    => SocksConnectionHandshakeStatus::Invalid,
        };

        if handshake_status == SocksConnectionHandshakeStatus::Invalid {
            tracemark!("SOCKS");
            return None;
        }

        // Overwrite connection status in case of closed TCP connection.
        let (connection_status, terminated_at) = determine_tcp_session_state(session);

        let socks_type = if tunneled_destination_address.is_some() {
            Socks4
        } else {
            Socks4A
        };

        return Some(SocksTunnel {
            socks_type,
            authentication_status: SocksAuthenticationResult::Success,
            handshake_status,
            connection_status,
            username,
            tunneled_bytes: session.bytes_count(),
            tcp_session_key: session.session_key.clone(),
            tunneled_destination_address,
            tunneled_destination_host,
            tunneled_destination_port,
            source_mac: session.source_mac.clone(),
            destination_mac: session.destination_mac.clone(),
            source_address: session.source_address,
            destination_address: session.destination_address,
            source_port: session.source_port,
            destination_port: session.destination_port,
            established_at: session.start_time,
            terminated_at,
            most_recent_segment_time: session.most_recent_segment_time
        })
    }

    // SOCKS 5?
    if *cts.first().unwrap() == 0x05 && *cts.get(1).unwrap() <= 9 {
        if stc.len() < 2 {
            tracemark!("SOCKS");
            return None;
        }

        // Potentially SOCKS5, check if the response was SOCKS as well.
        if *stc.first().unwrap() != 0x05
            || (*stc.get(1).unwrap() != 0xFF && *stc.get(1).unwrap() > 9) {
            tracemark!("SOCKS");
            return None;
        }

        // Parse auth methods supported by client.
        let client_auth_methods_count: usize = *cts.get(1).unwrap() as usize;
        let mut client_auth_methods: Vec<SocksAuthenticationMethod> = vec![];

        let mut cts_cursor: usize = 2;
        let mut stc_cursor: usize = 0;
        if cts.len() < client_auth_methods_count+cts_cursor {
            tracemark!("SOCKS");
            return None;
        }

        for cam in &cts[cts_cursor..cts_cursor+client_auth_methods_count] {
            client_auth_methods.push(resolve_authentication_method(cam));
        }
        cts_cursor += client_auth_methods_count;

        // Find auth method server chose.
        let server_chosen_auth_method = resolve_authentication_method(stc.get(1).unwrap());
        stc_cursor += 2;

        if server_chosen_auth_method == SocksAuthenticationMethod::NoneAcceptable {
            // Server did not accept any of the offered auth methods.
            return Some(build_auth_failed_result(session,
                                                 SocksAuthenticationResult::Failure,
                                                 None))
        }

        // Authentication.
        let (auth_result, username, auth_cts_offset, auth_stc_offset) = match process_socks5_authentication(
            server_chosen_auth_method,
            cts,
            stc,
            &cts_cursor,
            &stc_cursor)
        {
            Ok(result) => result,
            Err(e) => {
                /*
                 * We couldn't parse the authentication steps, or we don't support the
                 * chosen authentication method.
                 */
                debug!("Could not parse SOCKS5 authentication steps: {}", e);
                return Some(build_auth_failed_result(session, 
                                                     SocksAuthenticationResult::Unknown,
                                                     None))
            }
        };

        if auth_result != SocksAuthenticationResult::Success {
            return Some(build_auth_failed_result(session,
                                                 SocksAuthenticationResult::Failure,
                                                 username))
        }

        // We have a completed, successful authentication at this point.
        cts_cursor += auth_cts_offset;
        stc_cursor += auth_stc_offset;

        if cts[cts_cursor] != 0x05 || cts.len() < cts_cursor+4 || stc.len() < stc_cursor+4 {
            tracemark!("SOCKS");
            return None;
        }

        // Skip command code and a reserved field.
        cts_cursor += 2;

        // Reserved field. Must be 0x00.
        if cts[cts_cursor] != 0x00 {
            tracemark!("SOCKS");
            return None;
        }

        cts_cursor += 1;

        let socks_address_type = &cts[cts_cursor];
        cts_cursor += 1;

        // Parse tunnel destination address according to type.
        let (tunneled_destination_address, tunneled_destination_host, address_cts_offset) =
            match socks_address_type {
                0x01 => {
                    // IPv4
                    if cts.len() < cts_cursor+4 {
                        tracemark!("SOCKS");
                        return None;
                    }

                    (Some(to_ipv4_address(&cts[cts_cursor..cts_cursor+4])), None, 4usize)
                },
                0x03 => {
                    // Domain name
                    if cts.len() < cts_cursor+2 {
                        tracemark!("SOCKS");
                        return None;
                    }

                    let len = cts[cts_cursor];

                    if len == 0 {
                        tracemark!("SOCKS");
                        return None;
                    }

                    let domain = String::from_utf8_lossy(
                        &cts[cts_cursor+1..cts_cursor+(len as usize)+1]
                    ).to_string();
                    (None, Some(domain), (len+1) as usize)
                },
                0x04 => {
                    // IPv6
                    if cts.len() < cts_cursor+16 {
                        tracemark!("SOCKS");
                        return None;
                    }

                    (Some(to_ipv6_address(&cts[cts_cursor..cts_cursor+16])), None, 16usize)
                },
                _ => {
                    tracemark!("SOCKS");
                    return None;
                }
            };
        cts_cursor += address_cts_offset;

        if cts.len() < cts_cursor+2 {
            tracemark!("SOCKS");
            return None;
        }

        // Parse tunnel port.
        let tunneled_destination_port = BigEndian::read_u16(&cts[cts_cursor..cts_cursor+2]);

        // Server response to connection request.
        if stc[stc_cursor] != 0x05 || stc.len() < stc_cursor+1 {
            tracemark!("SOCKS");
            return None;
        }
        stc_cursor += 1;

        let handshake_status = match stc[stc_cursor] {
            0x00 => SocksConnectionHandshakeStatus::Granted,
            0x01 => SocksConnectionHandshakeStatus::GeneralFailure,
            0x02 => SocksConnectionHandshakeStatus::ConnectionNotAllowedByRuleset,
            0x03 => SocksConnectionHandshakeStatus::NetworkUnreachable,
            0x04 => SocksConnectionHandshakeStatus::HostUnreachable,
            0x05 => SocksConnectionHandshakeStatus::ConnectionRefusedByDestination,
            0x06 => SocksConnectionHandshakeStatus::Ttl,
            0x07 => SocksConnectionHandshakeStatus::UnsupportedCommand,
            0x08 => SocksConnectionHandshakeStatus::UnsupportedAddressType,
            _    => SocksConnectionHandshakeStatus::Invalid
        };

        let (connection_status, terminated_at) = determine_tcp_session_state(session);

        return Some(SocksTunnel {
            socks_type: SocksType::Socks5,
            authentication_status: SocksAuthenticationResult::Success,
            handshake_status,
            connection_status,
            username,
            tunneled_bytes: session.bytes_count(),
            tcp_session_key: session.session_key.clone(),
            tunneled_destination_address,
            tunneled_destination_host,
            tunneled_destination_port,
            source_mac: session.source_mac.clone(),
            destination_mac: session.destination_mac.clone(),
            source_address: session.source_address,
            destination_address: session.destination_address,
            source_port: session.source_port,
            destination_port: session.destination_port,
            established_at: session.start_time,
            terminated_at,
            most_recent_segment_time: session.most_recent_segment_time
        })
    }

    // Not SOCKS.
    tracemark!("SOCKS");
    None
}

fn resolve_authentication_method(method: &u8) -> SocksAuthenticationMethod {
    match method {
        0x00 => SocksAuthenticationMethod::None,
        0x01 => SocksAuthenticationMethod::Gssapi,
        0x02 => SocksAuthenticationMethod::UsernamePassword,
        0x03 => SocksAuthenticationMethod::ChallengeHandshake,
        0x05 => SocksAuthenticationMethod::ChallengeResponse,
        0x06 => SocksAuthenticationMethod::Ssl,
        0x07 => SocksAuthenticationMethod::Nds,
        0x08 => SocksAuthenticationMethod::MultiAuthenticationFramework,
        0x09 => SocksAuthenticationMethod::JsonParameterBlock,
        0xFF => SocksAuthenticationMethod::NoneAcceptable,
        _ => SocksAuthenticationMethod::Unknown
    }
}

// Returns (client, server) bytes to skip.
fn process_socks5_authentication(method: SocksAuthenticationMethod,
                                 cts: &[u8],
                                 stc: &[u8],
                                 cts_cursor: &usize,
                                 stc_cursor: &usize)
    -> Result<(SocksAuthenticationResult, Option<String>, usize, usize), Error> {

    if cts.len() < *cts_cursor || stc.len() < *stc_cursor {
        bail!("Payload too short to hold authentication transaction.");
    }

    let cts_slice = &cts[*cts_cursor..];
    let stc_slice = &stc[*stc_cursor..];

    match method {
        SocksAuthenticationMethod::None =>
            Ok((SocksAuthenticationResult::Success, None, 0, 0)),
        SocksAuthenticationMethod::UsernamePassword =>
            process_socks5_username_password_auth(cts_slice, stc_slice),
        _ => bail!("Authentication method [{:?}] not supported.", method)
    }
}

fn process_socks5_username_password_auth(cts: &[u8], stc: &[u8])
    -> Result<(SocksAuthenticationResult, Option<String>, usize, usize), Error> {

    if cts.len() < 5 || stc.len() < 2 {
        bail!("Payload too short to hold username/password authentication transaction.")
    }

    if *cts.first().unwrap() != 0x01 {
        bail!("Unexpected authentication request version");
    }

    let mut cts_cursor: usize = 1;
    let username_length = cts[cts_cursor] as usize;
    cts_cursor += 1;

    if cts.len() < cts_cursor+username_length {
        bail!("Username does not fit into payload.");
    }

    let username = String::from_utf8_lossy(&cts[cts_cursor..cts_cursor+username_length]).to_string();
    cts_cursor += username_length;

    if cts.len() < cts_cursor {
        bail!("Password length does not fit into payload.")
    }

    let password_length = cts[cts_cursor] as usize;
    cts_cursor += 1;

    if cts.len() < cts_cursor+password_length {
        bail!("Password does not fit into payload.")
    }
    cts_cursor += password_length; // We are not recording the password. Just skip over it.

    // Server response.
    if stc.len() < 2 {
        bail!("Authentication response doesn't fit into payload.")
    }

    if *stc.first().unwrap() != 0x01 {
        bail!("Unexpected authentication response version");
    }

    let auth_result = match *stc.get(1).unwrap() {
        0x00  => SocksAuthenticationResult::Success,
        0x01  => SocksAuthenticationResult::Failure,
        other => bail!("Unexpected authentication response status [{}].", other)
    };
    let stc_cursor = 2;

    Ok((auth_result, Some(username), cts_cursor, stc_cursor))
}

fn is_socks_4a_address(address: &[u8]) -> bool {
    address.len() == 4
        && address[0] == 0x00
        && address[1] == 0x00
        && address[2] == 0x00
        && address[3] != 0x00
}

fn build_auth_failed_result(session: &TcpSession,
                            auth_result: SocksAuthenticationResult,
                            username: Option<String>)
    -> SocksTunnel {

    SocksTunnel {
        socks_type: SocksType::Socks5,
        authentication_status: auth_result,
        handshake_status: SocksConnectionHandshakeStatus::NotReached,
        connection_status: GenericConnectionStatus::Inactive,
        username,
        tunneled_bytes: 0,
        tunneled_destination_address: None,
        tunneled_destination_host: None,
        tunneled_destination_port: 0,
        tcp_session_key: session.session_key.clone(),
        source_mac: session.source_mac.clone(),
        destination_mac: session.destination_mac.clone(),
        source_address: session.source_address,
        destination_address: session.destination_address,
        source_port: session.source_port,
        destination_port: session.destination_port,
        established_at: session.start_time,
        terminated_at: session.end_time,
        most_recent_segment_time: session.most_recent_segment_time
    }
}