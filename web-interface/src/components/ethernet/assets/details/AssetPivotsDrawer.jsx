import React, {useEffect, useState} from "react";
import PivotLink from "../../../shared/PivotLink";
import ApiRoutes from "../../../../util/ApiRoutes";
import {filteredLink, macFilter} from "./AssetProfileHelpers";

const HIDE_DURATION_MS = 300;

const pivotGroups = (mac) => [
  {
    title: "Sessions",
    links: [
      {title: "Outbound TCP/UDP Sessions", to: filteredLink(ApiRoutes.ETHERNET.L4.SESSIONS, macFilter("source_mac", mac))},
      {title: "Inbound TCP/UDP Sessions", to: filteredLink(ApiRoutes.ETHERNET.L4.SESSIONS, macFilter("destination_mac", mac))},
      {title: "TLS Sessions as Client", to: filteredLink(ApiRoutes.ETHERNET.TLS.SESSIONS.INDEX, macFilter("client_mac", mac))},
      {title: "TLS Sessions as Server", to: filteredLink(ApiRoutes.ETHERNET.TLS.SESSIONS.INDEX, macFilter("server_mac", mac))},
      {title: "ICMP Traffic", to: filteredLink(ApiRoutes.ETHERNET.ICMP.OVERVIEW, macFilter("source_mac", mac))}
    ]
  },
  {
    title: "Infrastructure Protocols",
    links: [
      {title: "DNS Queries", to: filteredLink(ApiRoutes.ETHERNET.DNS.TRANSACTION_LOGS, macFilter("client_mac", mac))},
      {title: "DNS Responses Served", to: filteredLink(ApiRoutes.ETHERNET.DNS.TRANSACTION_LOGS, macFilter("server_mac", mac))},
      {title: "DHCP Transactions", to: filteredLink(ApiRoutes.ETHERNET.ASSETS.DHCP.INDEX, macFilter("client_mac", mac))},
      {title: "ARP Packets Sent", to: filteredLink(ApiRoutes.ETHERNET.ASSETS.ARP.INDEX, macFilter("arp_sender_mac", mac))},
      {title: "NTP Requests", to: filteredLink(ApiRoutes.ETHERNET.TIME.NTP.INDEX, macFilter("client_mac", mac))},
      {title: "NTP Responses Served", to: filteredLink(ApiRoutes.ETHERNET.TIME.NTP.INDEX, macFilter("server_mac", mac))}
    ]
  },
  {
    title: "Remote Access, Tunnels and Streams",
    links: [
      {title: "Outbound SSH Sessions", to: filteredLink(ApiRoutes.ETHERNET.REMOTE.SSH.INDEX, macFilter("client_mac", mac))},
      {title: "Inbound SSH Sessions", to: filteredLink(ApiRoutes.ETHERNET.REMOTE.SSH.INDEX, macFilter("server_mac", mac))},
      {title: "Outbound SOCKS Tunnels", to: filteredLink(ApiRoutes.ETHERNET.TUNNELS.SOCKS.INDEX, macFilter("client_mac", mac))},
      {title: "Inbound SOCKS Tunnels", to: filteredLink(ApiRoutes.ETHERNET.TUNNELS.SOCKS.INDEX, macFilter("server_mac", mac))},
      {title: "Outbound VPN Connections", to: filteredLink(ApiRoutes.ETHERNET.TUNNELS.VPN.INDEX, macFilter("client_mac", mac))},
      {title: "Inbound VPN Connections", to: filteredLink(ApiRoutes.ETHERNET.TUNNELS.VPN.INDEX, macFilter("server_mac", mac))},
      {title: "RTSP Streams", to: filteredLink(ApiRoutes.ETHERNET.STREAMS.RTSP.INDEX, macFilter("stream_source_mac", mac))},
      {title: "WebRTC Sessions", to: filteredLink(ApiRoutes.ETHERNET.STREAMS.WEBRTC.INDEX, macFilter("source_mac", mac))},
      {title: "STUN Connections", to: filteredLink(ApiRoutes.ETHERNET.NAT.TRAVERSAL.STUN_CONNECTIONS.INDEX, macFilter("source_mac", mac))}
    ]
  },
  {
    title: "Detections",
    links: [
      {title: "Alerts", to: filteredLink(ApiRoutes.ALERTS.INDEX, macFilter("attribute", mac))},
      {title: "Network Scans", to: filteredLink(ApiRoutes.ETHERNET.RECON.SCANS.INDEX, macFilter("source_mac", mac))},
      {title: "DNS Tunnels", to: filteredLink(ApiRoutes.ETHERNET.DNS.TUNNELS.INDEX, macFilter("client_mac", mac))},
      {title: "ICMP Tunnels", to: filteredLink(ApiRoutes.ETHERNET.ICMP.TUNNELS.INDEX, macFilter("source_mac", mac))},
      {title: "Traceroutes", to: filteredLink(ApiRoutes.ETHERNET.ICMP.TRACEROUTES.INDEX, macFilter("source_mac", mac))},
      {title: "ARP Spoofing", to: filteredLink(ApiRoutes.ETHERNET.SPOOFING.ARP.INDEX, macFilter("spoofer_mac", mac))},
      {title: "DNS Spoofing", to: filteredLink(ApiRoutes.ETHERNET.SPOOFING.DNS.INDEX, macFilter("spoofer_mac", mac))},
      {title: "Name Resolution Spoofing", to: filteredLink(ApiRoutes.ETHERNET.SPOOFING.NAME_RESOLUTION.INDEX, macFilter("responder_mac", mac))},
      {title: "Beacons", to: ApiRoutes.ETHERNET.BEACONS.INDEX}
    ]
  }
];

export default function AssetPivotsDrawer({asset}) {

  const [open, setOpen] = useState(false);
  // Bootstrap hides the offcanvas as soon as "show" is gone. "hiding" keeps it visible for the slide-out.
  const [hiding, setHiding] = useState(false);

  const close = () => {
    if (!open || hiding) return;
    setHiding(true);
  }

  useEffect(() => {
    if (!hiding) return undefined;
    const timer = window.setTimeout(() => {
      setHiding(false);
      setOpen(false);
    }, HIDE_DURATION_MS);
    return () => window.clearTimeout(timer);
  }, [hiding]);

  useEffect(() => {
    if (!open) return undefined;
    const onKey = (e) => {
      if (e.key === "Escape") close();
    };
    document.addEventListener("keydown", onKey);
    return () => document.removeEventListener("keydown", onKey);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open, hiding]);

  const groups = pivotGroups(asset.mac.address);

  return (
    <React.Fragment>
      <button type="button"
              className="asset-pivots-tab"
              title="Pivot: protocol pages pre-filtered to this asset"
              aria-controls="asset-pivots-drawer"
              aria-expanded={open}
              onClick={() => setOpen(true)}>
        Pivot
      </button>

      {open && <div className={"offcanvas-backdrop fade" + (hiding ? "" : " show")} onClick={close} />}

      <div id="asset-pivots-drawer"
           className={"offcanvas offcanvas-end asset-pivots-drawer" + (open ? " show" : "") + (hiding ? " hiding" : "")}
           tabIndex={-1}
           role="dialog"
           aria-modal={open}
           aria-labelledby="asset-pivots-drawer-title">
        <div className="offcanvas-header">
          <h3 className="offcanvas-title" id="asset-pivots-drawer-title">
            Pivot
          </h3>
          <button type="button" className="btn-close" aria-label="Close" onClick={close} />
        </div>
        <div className="offcanvas-body">
          {groups.map((group) => (
            <div className="asset-pivots-group" key={group.title}>
              <h6>{group.title}</h6>
              <ul className="list-unstyled">
                {group.links.map((link) => (
                  <li key={link.title}><PivotLink to={link.to}>{link.title}</PivotLink></li>
                ))}
              </ul>
            </div>
          ))}
        </div>
      </div>
    </React.Fragment>
  )

}
