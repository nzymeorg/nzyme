package app.nzyme.core.assets.profile;

public enum AssetAttributeDimension {

    // Formats are defined in TcpSignature. Directly attached stacks only, except TCP_FINGERPRINT_FORWARDED.
    TCP_STACK,
    TCP_FINGERPRINT,
    TCP_SIGNATURE,
    // SYNs the MAC forwarded for another stack (hop distance above zero).
    TCP_FINGERPRINT_FORWARDED,
    TCP_SERVER_FINGERPRINT,
    TCP_SERVER_SIGNATURE,
    // Like "32768-49151".
    SOURCE_PORT_RANGE,
    // Hz.
    TCP_TIMESTAMP_RATE,
    IP_ID_BEHAVIOR,
    JA4,
    DHCP_FINGERPRINT,
    DHCP_VENDOR_CLASS,
    // Option 55 as comma-separated option numbers.
    DHCP_PARAMETER_REQUEST_LIST,
    // Option 61 as colon-separated hex bytes.
    DHCP_CLIENT_IDENTIFIER,
    NTP_CLIENT_VERSION,
    TLS_VERSION,

    PROTOCOL_CLIENT,
    PROTOCOL_SERVER,

    // Value format: "TCP/22".
    INBOUND_PORT,

    // Value format: "TCP/22 SSH".
    INBOUND_PORT_PROTOCOL,

    EXTERNAL_ASN,
    EXTERNAL_COUNTRY,

    GATEWAY_MAC,
    DHCP_SERVER,
    DNS_SERVER,
    NTP_SERVER,

    ROLE,

    NAME_RESOLUTION_PROTOCOL,

    TAP

}
