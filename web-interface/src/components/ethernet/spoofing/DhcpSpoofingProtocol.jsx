export const DHCP_SPOOFING_PROTOCOLS = ["DHCP", "DHCP6", "RA"];

export const dhcpSpoofingProtocolToName = (protocol) => {
  switch (protocol) {
    case "DHCP": return "DHCP";
    case "DHCP6": return "DHCPv6";
    case "RA": return "Router Advertisement";
    default: return protocol;
  }
}

export const dhcpSpoofingServerNoun = (protocol) => {
  switch (protocol) {
    case "DHCP6": return "DHCPv6 server";
    case "RA": return "IPv6 router";
    default: return "DHCP server";
  }
}

export const isRouterAdvertisement = (protocol) => protocol === "RA";
