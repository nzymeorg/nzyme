import React from "react";
import {parseIPv6} from "../../../util/Tools";

const SHORTEN_ABOVE = 20;

export const shortenIpAddress = (ip, above = SHORTEN_ABOVE) => {
  if (ip.length <= above || !ip.includes(":") || ip.includes(".")) {
    return ip;
  }

  const bytes = parseIPv6(ip);
  if (!bytes) {
    return ip;
  }

  const groups = [];
  for (let i = 0; i < 16; i += 2) {
    groups.push(((bytes[i] << 8) | bytes[i + 1]).toString(16));
  }

  // An address like 2001:db8:1200:1234::1 was assigned by hand. Such addresses differ in the network, not the host.
  if (groups.slice(4, 7).every((group) => group === "0")) {
    return ip;
  }

  const head = groups[1] === "0" ? groups[0] : groups.slice(0, 2).join(":");

  return head + "…" + groups.slice(-2).join(":");
}

export default function IPAddress(props) {

  const ip = props.ip;
  const full = props.full;

  if (!ip) {
    return <span className="text-muted">n/a</span>
  }

  const shown = full ? ip : shortenIpAddress(ip);

  return (
      <span className="ip-address" title={shown === ip ? undefined : ip}>
        {shown}
      </span>
  )

}
