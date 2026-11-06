import React, {useEffect, useState} from "react";
import moment from "moment";
import CardTitleWithControls from "../../../shared/CardTitleWithControls";
import ColumnSorting from "../../../shared/ColumnSorting";
import LoadingSpinner from "../../../misc/LoadingSpinner";
import Paginator from "../../../misc/Paginator";
import AssetsService from "../../../../services/ethernet/AssetsService";
import SortableTable from "./SortableTable";
import PivotLink from "../../../shared/PivotLink";
import ApiRoutes from "../../../../util/ApiRoutes";
import {filteredLink, formatBytes, formatCount, macFilter, roleTitle} from "./AssetProfileHelpers";

const tagFilter = (tag) => ({ tags: [{ field: "tags", operator: "contains", value: tag }] });

const l4Pivot = (mac, tag, served) => ({
  title: (served ? "Inbound " : "Outbound ") + tag + " sessions",
  to: filteredLink(ApiRoutes.ETHERNET.L4.SESSIONS, {...macFilter(served ? "destination_mac" : "source_mac", mac), ...tagFilter(tag)})
});

const rolePivot = (role, mac) => {
  switch (role) {
    case "GATEWAY":
      return { title: "Sessions via this gateway", to: filteredLink(ApiRoutes.ETHERNET.L4.SESSIONS, macFilter("destination_mac", mac)) };
    case "DHCP_SERVER":
      return { title: "DHCP transactions served", to: filteredLink(ApiRoutes.ETHERNET.ASSETS.DHCP.INDEX, macFilter("server_mac", mac)) };
    case "DNS_SERVER":
      return { title: "DNS responses served", to: filteredLink(ApiRoutes.ETHERNET.DNS.TRANSACTION_LOGS, macFilter("server_mac", mac)) };
    case "NTP_SERVER":
      return { title: "NTP responses served", to: filteredLink(ApiRoutes.ETHERNET.TIME.NTP.INDEX, macFilter("server_mac", mac)) };
    case "SSH_SERVER":
      return { title: "Inbound SSH sessions", to: filteredLink(ApiRoutes.ETHERNET.REMOTE.SSH.INDEX, macFilter("server_mac", mac)) };
    case "TLS_SERVER":
      return { title: "TLS sessions as server", to: filteredLink(ApiRoutes.ETHERNET.TLS.SESSIONS.INDEX, macFilter("server_mac", mac)) };
    case "SOCKS_SERVER":
      return { title: "Inbound SOCKS tunnels", to: filteredLink(ApiRoutes.ETHERNET.TUNNELS.SOCKS.INDEX, macFilter("server_mac", mac)) };
    case "VPN_SERVER":
      return { title: "Inbound VPN connections", to: filteredLink(ApiRoutes.ETHERNET.TUNNELS.VPN.INDEX, macFilter("server_mac", mac)) };
    case "RTSP_SERVER":
      return { title: "RTSP streams served", to: filteredLink(ApiRoutes.ETHERNET.STREAMS.RTSP.INDEX, macFilter("stream_source_mac", mac)) };
    case "STUN_SERVER":
      return { title: "STUN connections served", to: filteredLink(ApiRoutes.ETHERNET.NAT.TRAVERSAL.STUN_CONNECTIONS.INDEX, macFilter("destination_mac", mac)) };
    case "HTTP_SERVER":
      return l4Pivot(mac, "HTTP", true);
    case "TURN_SERVER":
      return l4Pivot(mac, "TURN", true);
    default:
      // Name resolution responses have no log page of their own.
      return null;
  }
}

const protocolPivot = (tag, served, mac) => {
  const side = served ? "server_mac" : "client_mac";
  switch (tag) {
    case "DNS":
      return { title: served ? "DNS responses served" : "DNS queries", to: filteredLink(ApiRoutes.ETHERNET.DNS.TRANSACTION_LOGS, macFilter(side, mac)) };
    case "DHCP4":
      return { title: served ? "DHCP transactions served" : "DHCP transactions", to: filteredLink(ApiRoutes.ETHERNET.ASSETS.DHCP.INDEX, macFilter(side, mac)) };
    case "NTP":
      return { title: served ? "NTP responses served" : "NTP requests", to: filteredLink(ApiRoutes.ETHERNET.TIME.NTP.INDEX, macFilter(side, mac)) };
    case "SSH":
      return { title: (served ? "Inbound" : "Outbound") + " SSH sessions", to: filteredLink(ApiRoutes.ETHERNET.REMOTE.SSH.INDEX, macFilter(side, mac)) };
    case "TLS":
      return { title: "TLS sessions as " + (served ? "server" : "client"), to: filteredLink(ApiRoutes.ETHERNET.TLS.SESSIONS.INDEX, macFilter(side, mac)) };
    case "SOCKS":
      return { title: (served ? "Inbound" : "Outbound") + " SOCKS tunnels", to: filteredLink(ApiRoutes.ETHERNET.TUNNELS.SOCKS.INDEX, macFilter(side, mac)) };
    case "WireGuard":
    case "OpenVPN":
      return { title: (served ? "Inbound" : "Outbound") + " VPN connections", to: filteredLink(ApiRoutes.ETHERNET.TUNNELS.VPN.INDEX, macFilter(side, mac)) };
    case "RTSP":
      return { title: served ? "RTSP streams served" : "RTSP streams received", to: filteredLink(ApiRoutes.ETHERNET.STREAMS.RTSP.INDEX, macFilter(served ? "stream_source_mac" : "stream_destination_mac", mac)) };
    case "STUN":
      return { title: served ? "STUN connections served" : "STUN connections", to: filteredLink(ApiRoutes.ETHERNET.NAT.TRAVERSAL.STUN_CONNECTIONS.INDEX, macFilter(served ? "destination_mac" : "source_mac", mac)) };
    default:
      return l4Pivot(mac, tag, served);
  }
}

const pivotLink = (pivot) => (pivot
  ? <PivotLink to={pivot.to} title={pivot.title} className="ms-2 small" icon={true} />
  : null);

const assetsService = new AssetsService();

export default function AssetRolesCard({asset, organizationId, tenantId, attributes, summary, revision, onRefresh}) {

  if (!attributes) {
    return (
      <React.Fragment>
        <CardTitleWithControls title="Roles and Protocols"
                               refreshAction={onRefresh} />
        <LoadingSpinner />
      </React.Fragment>
    )
  }

  const roles = attributes.attributes["ROLE"] ? attributes.attributes["ROLE"] : [];
  const clientProtocols = attributes.attributes["PROTOCOL_CLIENT"] ? attributes.attributes["PROTOCOL_CLIENT"] : [];
  const serverProtocols = attributes.attributes["PROTOCOL_SERVER"] ? attributes.attributes["PROTOCOL_SERVER"] : [];
  const nameResolution = attributes.attributes["NAME_RESOLUTION_PROTOCOL"] ? attributes.attributes["NAME_RESOLUTION_PROTOCOL"] : [];

  const roleDetailParts = (role) => {
    const parts = [];

    const match = summary ? summary.roles.find((r) => r.role === role) : null;
    if (match && match.dependents !== null && match.dependents !== undefined) {
      parts.push(formatCount(match.dependents) + " dependent " + (match.dependents === 1 ? "asset" : "assets"));
    }

    if (role === "NAME_RESOLUTION_RESPONDER" && nameResolution.length > 0) {
      parts.push("Answers " + nameResolution.map((n) => n.value).join(", "));
    }

    return parts;
  }

  const roleDetailsText = (role) => roleDetailParts(role).join(", ");

  const roleDetails = (role) => {
    const parts = roleDetailParts(role);
    return parts.length === 0 ? <span className="text-muted">n/a</span> : parts.join(", ");
  }

  const mac = asset.mac.address;

  const protocolColumns = (served) => [
    { key: "value", label: "Protocol", value: (v) => v.value, render: (v) => <span>{v.value}{pivotLink(protocolPivot(v.value, served, mac))}</span> },
    { key: "count", label: "Sessions", value: (v) => v.count, render: (v) => formatCount(v.count) },
    { key: "bytes", label: "Bytes", value: (v) => v.bytes || 0, render: (v) => formatBytes(v.bytes || 0) },
    { key: "first_seen", label: "First Seen", value: (v) => moment(v.first_seen).valueOf(), render: (v) => <span title={moment(v.first_seen).format()}>{moment(v.first_seen).fromNow()}</span> },
    { key: "last_seen", label: "Last Seen", value: (v) => moment(v.last_seen).valueOf(), render: (v) => <span title={moment(v.last_seen).format()}>{moment(v.last_seen).fromNow()}</span> },
  ];

  const roleColumns = [
    { key: "role", label: "Role", value: (r) => roleTitle(r.value), render: (r) => <span>{roleTitle(r.value)}{pivotLink(rolePivot(r.value, mac))}</span> },
    { key: "count", label: "Observed", value: (r) => r.count, render: (r) => formatCount(r.count) + (r.count === 1 ? " time" : " times") },
    { key: "first_seen", label: "First Seen", value: (r) => moment(r.first_seen).valueOf(), render: (r) => <span title={moment(r.first_seen).format()}>{moment(r.first_seen).fromNow()}</span> },
    { key: "last_seen", label: "Last Seen", value: (r) => moment(r.last_seen).valueOf(), render: (r) => <span title={moment(r.last_seen).format()}>{moment(r.last_seen).fromNow()}</span> },
    { key: "details", label: "Details", value: (r) => roleDetailsText(r.value), render: (r) => roleDetails(r.value) },
  ];

  return (
    <React.Fragment>
      <CardTitleWithControls title="Roles and Protocols"
                             refreshAction={onRefresh} />

      <h6>Roles</h6>
      {roles.length === 0
        ? <span className="text-muted">None. This asset was only observed as a client.</span>
        : <SortableTable columns={roleColumns} rows={roles} rowKey={(r) => r.value} defaultColumn="count" />}

      <div className="row mt-3">
        <div className="col-md-6">
          <h6>Protocols Used as Client</h6>
          {clientProtocols.length === 0
            ? <span className="text-muted">No tagged client sessions observed.</span>
            : <SortableTable columns={protocolColumns(false)} rows={clientProtocols} rowKey={(v) => v.value} defaultColumn="count" />}
        </div>
        <div className="col-md-6">
          <h6>Protocols Served</h6>
          {serverProtocols.length === 0
            ? <span className="text-muted">No tagged inbound sessions observed.</span>
            : <SortableTable columns={protocolColumns(true)} rows={serverProtocols} rowKey={(v) => v.value} defaultColumn="count" />}
        </div>
      </div>

      <h6 className="mt-3">Accepted Inbound Sessions</h6>
      <InboundPorts asset={asset} organizationId={organizationId} tenantId={tenantId} revision={revision} />
    </React.Fragment>
  )

}

const PORTS_PER_PAGE = 10;
const PROTOCOLS_SHOWN = 3;

const inboundPortPivot = (mac, transport, port) => ({
  title: "Inbound " + transport + "/" + port + " sessions",
  to: filteredLink(ApiRoutes.ETHERNET.L4.SESSIONS, {
    ...macFilter("destination_mac", mac),
    destination_port: [{ field: "destination_port", operator: "equals_numeric", value: String(port) }],
    l4_type: [{ field: "l4_type", operator: "equals", value: transport }]
  })
});

function InboundPorts({asset, organizationId, tenantId, revision}) {

  const [ports, setPorts] = useState(null);
  const [page, setPage] = useState(1);
  const [orderColumn, setOrderColumn] = useState("count");
  const [orderDirection, setOrderDirection] = useState("DESC");

  const firstPage = (setter) => (value) => {
    setPage(1);
    setter(value);
  }

  useEffect(() => {
    setPorts(null);
    assetsService.getAssetProfileInboundPorts(asset.uuid, organizationId, tenantId, orderColumn, orderDirection,
      PORTS_PER_PAGE, (page - 1) * PORTS_PER_PAGE, setPorts);
  }, [asset.uuid, organizationId, tenantId, orderColumn, orderDirection, page, revision]);

  if (!ports) {
    return <LoadingSpinner />;
  }

  if (ports.total === 0) {
    return <span className="text-muted">No inbound sessions observed.</span>;
  }

  const mac = asset.mac.address;

  const sorting = (column) => (
    <ColumnSorting thisColumn={column}
                   orderColumn={orderColumn}
                   setOrderColumn={firstPage(setOrderColumn)}
                   orderDirection={orderDirection}
                   setOrderDirection={firstPage(setOrderDirection)} />
  );

  const shareBar = (value, maximum) => (
    <span className="share-bar me-2" style={{width: "90px", flex: "none"}}>
      <i style={{width: Math.max(2, Math.round(maximum > 0 ? value / maximum * 100 : 0)) + "%"}} />
    </span>
  );

  const protocolsCell = (r) => {
    if (r.protocols.length === 0) {
      return <span className="text-muted">n/a</span>;
    }

    const shown = r.protocols.slice(0, PROTOCOLS_SHOWN).map((p) => p.name).join(", ");
    const more = r.protocols.length - PROTOCOLS_SHOWN;
    const all = r.protocols.map((p) => p.name + " (" + formatCount(p.count) + ")").join(", ");

    return (
      <span title={all}>
        {shown}
        {more > 0 ? <span className="text-muted"> and {more} more</span> : null}
      </span>
    );
  }

  const share = (count) => (ports.total_sessions > 0 ? Math.round(count / ports.total_sessions * 1000) / 10 : 0) + "%";

  return (
    <React.Fragment>
      <table className="table table-sm table-hover table-striped mb-2">
        <thead>
        <tr>
          <th className="text-nowrap">Port {sorting("port")}</th>
          <th>Protocols</th>
          <th className="text-nowrap">Sessions {sorting("count")}</th>
          <th className="text-nowrap">Bytes {sorting("bytes")}</th>
          <th>Share</th>
          <th className="text-nowrap">First Seen {sorting("first_seen")}</th>
          <th className="text-nowrap">Last Seen {sorting("last_seen")}</th>
        </tr>
        </thead>
        <tbody>
        {ports.ports.map((r) => (
          <tr key={r.value}>
            <td className="text-nowrap">
              <span className="machine-data">{r.value}</span>
              {r.port > 0 ? pivotLink(inboundPortPivot(mac, r.transport, r.port)) : null}
            </td>
            <td>{protocolsCell(r)}</td>
            <td>
              <div className="d-flex align-items-center">
                {shareBar(r.count, ports.max_sessions)}
                {formatCount(r.count)}
              </div>
            </td>
            <td>
              <div className="d-flex align-items-center">
                {shareBar(r.bytes, ports.max_bytes)}
                {formatBytes(r.bytes)}
              </div>
            </td>
            <td>{share(r.count)}</td>
            <td className="text-nowrap" title={moment(r.first_seen).format()}>{moment(r.first_seen).fromNow()}</td>
            <td className="text-nowrap" title={moment(r.last_seen).format()}>{moment(r.last_seen).fromNow()}</td>
          </tr>
        ))}
        </tbody>
      </table>

      <div className="text-muted small mb-2">
        {formatCount(ports.total)} {ports.total === 1 ? "port" : "ports"} with {formatCount(ports.total_sessions)}{' '}
        {ports.total_sessions === 1 ? "session" : "sessions"} and {formatBytes(ports.total_bytes)} in total.
      </div>

      <Paginator itemCount={ports.total} perPage={PORTS_PER_PAGE} setPage={setPage} page={page} />
    </React.Fragment>
  );

}
