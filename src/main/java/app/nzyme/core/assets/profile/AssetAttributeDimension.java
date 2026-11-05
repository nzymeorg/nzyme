package app.nzyme.core.assets.profile;

public enum AssetAttributeDimension {

    TCP_FINGERPRINT,
    JA4,
    DHCP_FINGERPRINT,
    DHCP_VENDOR_CLASS,
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
