package app.nzyme.core.assets.profile;

import javax.annotation.Nullable;

public enum AssetRole {

    GATEWAY,
    DHCP_SERVER,
    DNS_SERVER,
    NTP_SERVER,
    NAME_RESOLUTION_RESPONDER,
    SSH_SERVER,
    TLS_SERVER,
    HTTP_SERVER,
    SOCKS_SERVER,
    RTSP_SERVER,
    STUN_SERVER,
    TURN_SERVER,
    VPN_SERVER;

    @Nullable
    public static AssetRole fromServerSideTag(String tag) {
        if (tag == null) {
            return null;
        }

        switch (tag) {
            case "SSH": return SSH_SERVER;
            case "TLS": return TLS_SERVER;
            case "HTTP": return HTTP_SERVER;
            case "SOCKS": return SOCKS_SERVER;
            case "RTSP": return RTSP_SERVER;
            case "STUN": return STUN_SERVER;
            case "TURN": return TURN_SERVER;
            case "DNS": return DNS_SERVER;
            case "DHCP4": return DHCP_SERVER;
            case "NTP": return NTP_SERVER;
            case "WireGuard":
            case "OpenVPN":
                return VPN_SERVER;
            default: return null;
        }
    }

}
