import {DHCP_SPOOFING_PROTOCOLS, dhcpSpoofingProtocolToName} from "../../../ethernet/spoofing/DhcpSpoofingProtocol";

export const ALLOWED_DHCP_SPOOFING_PROTOCOL_VALUES = DHCP_SPOOFING_PROTOCOLS;

export const DHCP_SPOOFING_PROTOCOL_SELECT_OPTIONS = ALLOWED_DHCP_SPOOFING_PROTOCOL_VALUES.map((protocol) => {
  return {name: dhcpSpoofingProtocolToName(protocol), value: protocol};
});
