import React, {useState} from "react";
import moment from "moment";
import CardTitleWithControls from "../../../shared/CardTitleWithControls";
import LoadingSpinner from "../../../misc/LoadingSpinner";
import SortableTable from "./SortableTable";
import {formatBytes, formatCount, roleTitle} from "./AssetProfileHelpers";

export default function AssetRolesCard({attributes, summary, onRefresh}) {

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
  const inboundPorts = attributes.attributes["INBOUND_PORT"] ? attributes.attributes["INBOUND_PORT"] : [];
  const inboundPortProtocols = attributes.attributes["INBOUND_PORT_PROTOCOL"] ? attributes.attributes["INBOUND_PORT_PROTOCOL"] : [];
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

  const protocolColumns = [
    { key: "value", label: "Protocol", value: (v) => v.value, render: (v) => v.value },
    { key: "count", label: "Sessions", value: (v) => v.count, render: (v) => formatCount(v.count) },
    { key: "bytes", label: "Bytes", value: (v) => v.bytes || 0, render: (v) => formatBytes(v.bytes || 0) },
    { key: "first_seen", label: "First Seen", value: (v) => moment(v.first_seen).valueOf(), render: (v) => <span title={moment(v.first_seen).format()}>{moment(v.first_seen).fromNow()}</span> },
    { key: "last_seen", label: "Last Seen", value: (v) => moment(v.last_seen).valueOf(), render: (v) => <span title={moment(v.last_seen).format()}>{moment(v.last_seen).fromNow()}</span> },
  ];

  const roleColumns = [
    { key: "role", label: "Role", value: (r) => roleTitle(r.value), render: (r) => roleTitle(r.value) },
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
            : <SortableTable columns={protocolColumns} rows={clientProtocols} rowKey={(v) => v.value} defaultColumn="count" />}
        </div>
        <div className="col-md-6">
          <h6>Protocols Served</h6>
          {serverProtocols.length === 0
            ? <span className="text-muted">No tagged inbound sessions observed.</span>
            : <SortableTable columns={protocolColumns} rows={serverProtocols} rowKey={(v) => v.value} defaultColumn="count" />}
        </div>
      </div>

      <h6 className="mt-3">Accepted Inbound Sessions</h6>
      <InboundPorts ports={inboundPorts} portProtocols={inboundPortProtocols} />
    </React.Fragment>
  )

}

const TOP_PORTS = 10;
const PROTOCOLS_SHOWN = 3;

function InboundPorts({ports, portProtocols}) {

  const [showAll, setShowAll] = useState(false);

  if (ports.length === 0) {
    return <span className="text-muted">No inbound sessions observed.</span>;
  }

  // Values look like "TCP/22 SSH".
  const protocolsByPort = {};
  (portProtocols || []).forEach((v) => {
    const separator = v.value.indexOf(" ");
    if (separator < 0) {
      return;
    }
    const port = v.value.substring(0, separator);
    const protocol = v.value.substring(separator + 1);
    (protocolsByPort[port] || (protocolsByPort[port] = [])).push({ name: protocol, count: v.count });
  });
  Object.values(protocolsByPort).forEach((list) => list.sort((a, b) => b.count - a.count || a.name.localeCompare(b.name)));

  const rows = ports.map((v) => {
    const [transport, port] = v.value.split("/");
    const number = parseInt(port, 10);
    return { key: v.value, transport: transport, port: isNaN(number) ? 0 : number, count: v.count, bytes: v.bytes || 0,
             first_seen: v.first_seen, last_seen: v.last_seen, protocols: protocolsByPort[v.value] || [] };
  });

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

  const ranked = rows.slice().sort((a, b) => b.count - a.count || a.port - b.port);
  const total = ranked.reduce((sum, r) => sum + r.count, 0);
  const max = ranked[0].count;
  const shown = showAll ? ranked : ranked.slice(0, TOP_PORTS);
  const tail = showAll ? [] : ranked.slice(TOP_PORTS);

  const columns = [
    { key: "port", label: "Port", value: (r) => r.port, render: (r) => r.transport + "/" + r.port },
    { key: "protocols", label: "Protocols", value: (r) => r.protocols.length > 0 ? r.protocols[0].name : "", render: protocolsCell },
    { key: "count", label: "Sessions", value: (r) => r.count, render: (r) => (
        <div className="d-flex align-items-center">
          <span className="share-bar me-2" style={{width: "90px", flex: "none"}}>
            <i style={{width: Math.max(2, Math.round(r.count / max * 100)) + "%"}} />
          </span>
          {formatCount(r.count)}
        </div>
      ) },
    { key: "share", label: "Share", value: (r) => r.count, render: (r) => (total > 0 ? Math.round(r.count / total * 1000) / 10 : 0) + "%" },
    { key: "bytes", label: "Bytes", value: (r) => r.bytes, render: (r) => formatBytes(r.bytes) },
    { key: "first_seen", label: "First Seen", value: (r) => moment(r.first_seen).valueOf(), render: (r) => <span title={moment(r.first_seen).format()}>{moment(r.first_seen).fromNow()}</span> },
    { key: "last_seen", label: "Last Seen", value: (r) => moment(r.last_seen).valueOf(), render: (r) => <span title={moment(r.last_seen).format()}>{moment(r.last_seen).fromNow()}</span> },
  ];

  const tailByTransport = {};
  tail.forEach((r) => {
    const t = tailByTransport[r.transport] || (tailByTransport[r.transport] = { ports: 0, min: r.port, max: r.port });
    t.ports += 1;
    t.min = Math.min(t.min, r.port);
    t.max = Math.max(t.max, r.port);
  });
  const tailSessions = tail.reduce((sum, r) => sum + r.count, 0);
  const tailBytes = tail.reduce((sum, r) => sum + r.bytes, 0);
  const tailRanges = Object.keys(tailByTransport).sort().map((transport) => {
    const t = tailByTransport[transport];
    return transport + " " + (t.min === t.max ? t.min : t.min + "–" + t.max) + " (" + formatCount(t.ports) + (t.ports === 1 ? " port" : " ports") + ")";
  }).join(", ");

  return (
    <React.Fragment>
      <SortableTable columns={columns} rows={shown} rowKey={(r) => r.key} defaultColumn="count" />

      {tail.length > 0
        ? <div className="text-muted small mt-2">
            {formatCount(tail.length)} more {tail.length === 1 ? "port" : "ports"} with {formatCount(tailSessions)}{' '}
            {tailSessions === 1 ? "session" : "sessions"} and {formatBytes(tailBytes)} in total: {tailRanges}.{' '}
            <a href="#" onClick={(e) => { e.preventDefault(); setShowAll(true); }}>Show all ports</a>
          </div>
        : null}

      {showAll && ranked.length > TOP_PORTS
        ? <div className="text-muted small mt-2">
            <a href="#" onClick={(e) => { e.preventDefault(); setShowAll(false); }}>Show only the {TOP_PORTS} busiest ports</a>
          </div>
        : null}
    </React.Fragment>
  );

}
