import {BSSID_FILTER_FIELDS} from "../../dot11/bssids/BssidFilterFields";
import {CONNECTED_CLIENT_FILTER_FIELDS} from "../../dot11/clients/ConnectedClientFilterFields";
import {DISCONNECTED_CLIENT_FILTER_FIELDS} from "../../dot11/clients/DisconnectedClientFilterFields";
import {BLUETOOTH_DEVICES_FILTER_FIELDS} from "../../bluetooth/BluetoothDevicesFilterFields";
import {ASSET_FILTER_FIELDS} from "../../ethernet/assets/AssetFilterFields";
import {L4_SESSIONS_FILTER_FIELDS} from "../../ethernet/l4/L4SessionFilterFields";
import {DNS_FILTER_FIELDS} from "../../ethernet/dns/DNSFilterFields";
import {DHCP_FILTER_FIELDS} from "../../ethernet/assets/dhcp/DHCPFilterFields";
import {ARP_FILTER_FIELDS} from "../../ethernet/assets/arp/ARPFilterFields";
import {SSH_FILTER_FIELDS} from "../../ethernet/remote/ssh/SSHFilterFields";
import {SOCKS_FILTER_FIELDS} from "../../ethernet/tunnels/socks/SOCKSFilterFields";
import {VPN_FILTER_FIELDS} from "../../ethernet/tunnels/vpn/VPNFilterFields";
import {TLS_SESSION_FILTER_FIELDS} from "../../ethernet/tls/TLSSessionFilterFields";
import {TLS_CERTIFICATE_FILTER_FIELDS} from "../../ethernet/tls/TLSCertificateFilterFields";
import {RTSP_FILTER_FIELDS} from "../../ethernet/streams/rtsp/RTSPFilterFields";
import {WEBRTC_FILTER_FIELDS} from "../../ethernet/streams/webrtc/WebRTCFilterFields";
import {NTP_FILTER_FIELDS} from "../../ethernet/time/ntp/NTPFilterFields";
import {STUN_DISCOVERY_FILTER_FIELDS} from "../../ethernet/nat/traversal/stun_discoveries/STUNDiscoveriesFilterFields";
import {STUN_CONNECTIONS_FILTER_FIELDS} from "../../ethernet/nat/traversal/stun_connections/STUNConnectionsFilterFields";
import {PORTAL_INTEGRITY_REPORTS_FILTER_FIELDS} from "../../ethernet/portalintegrity/PortalIntegrityReportsFilterFields";

export default function monitorTypeToFilterFields(type) {
  switch (type) {
    case "DOT11_BSSID":
      return BSSID_FILTER_FIELDS
    case "DOT11_CLIENT_CONNECTED":
      return CONNECTED_CLIENT_FILTER_FIELDS
    case "DOT11_CLIENT_DISCONNECTED":
      return DISCONNECTED_CLIENT_FILTER_FIELDS
    case "BLUETOOTH_DEVICE":
      return BLUETOOTH_DEVICES_FILTER_FIELDS
    case "ETHERNET_ASSET":
      return ASSET_FILTER_FIELDS
    case "ETHERNET_L4_SESSION":
      return L4_SESSIONS_FILTER_FIELDS
    case "ETHERNET_DNS_TRANSACTION":
      return DNS_FILTER_FIELDS
    case "ETHERNET_DHCP_TRANSACTION":
      return DHCP_FILTER_FIELDS
    case "ETHERNET_ARP_PACKET":
      return ARP_FILTER_FIELDS
    case "ETHERNET_SSH_SESSION":
      return SSH_FILTER_FIELDS
    case "ETHERNET_SOCKS_TUNNEL":
      return SOCKS_FILTER_FIELDS
    case "ETHERNET_VPN_TUNNEL":
      return VPN_FILTER_FIELDS
    case "ETHERNET_TLS_SESSION":
      return TLS_SESSION_FILTER_FIELDS
    case "ETHERNET_TLS_CERTIFICATE":
      return TLS_CERTIFICATE_FILTER_FIELDS
    case "ETHERNET_RTSP_STREAM":
      return RTSP_FILTER_FIELDS
    case "ETHERNET_WEBRTC_SESSION":
      return WEBRTC_FILTER_FIELDS
    case "ETHERNET_NTP_TRANSACTION":
      return NTP_FILTER_FIELDS
    case "ETHERNET_STUN_DISCOVERY":
      return STUN_DISCOVERY_FILTER_FIELDS
    case "ETHERNET_STUN_CONNECTION":
      return STUN_CONNECTIONS_FILTER_FIELDS
    case "ETHERNET_PORTAL_INTEGRITY_REPORT":
      return PORTAL_INTEGRITY_REPORTS_FILTER_FIELDS
  }
}
