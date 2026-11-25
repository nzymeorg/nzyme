import React from "react";

export default function Ipv6TransitionTunnelType({type}) {

  switch (type) {
    case "Teredo":
      return <span title="Teredo carries IPv6 in UDP over IPv4.">Teredo</span>
    case "6in4":
      return <span title="6in4 carries IPv6 directly in IPv4 between two configured endpoints.">6in4</span>
    case "6to4":
      return <span title="6to4 carries IPv6 directly in IPv4. The IPv6 addresses are derived from the IPv4 address of the router.">6to4</span>
    case "ISATAP":
      return <span title="ISATAP carries IPv6 directly in IPv4 inside a site. The IPv6 addresses are derived from the IPv4 address of the host.">ISATAP</span>
    default:
      return <span className="text-muted">{type}</span>
  }

}
