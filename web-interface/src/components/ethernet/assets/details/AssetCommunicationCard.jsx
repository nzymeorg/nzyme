import React, {Suspense, useCallback, useEffect, useMemo, useState} from "react";
import {toast} from "react-toastify";
import moment from "moment";
import PivotLink from "../../../shared/PivotLink";
import CardTitleWithControls from "../../../shared/CardTitleWithControls";
import SortableTable from "./SortableTable";
import L4Address from "../../shared/L4Address";
import Flag from "../../../misc/Flag";
import EthernetMacAddress from "../../../shared/context/macs/EthernetMacAddress";
import LoadingSpinner from "../../../misc/LoadingSpinner";
import AssetsService from "../../../../services/ethernet/AssetsService";
import ApiRoutes from "../../../../util/ApiRoutes";
import {Presets} from "../../../shared/timerange/TimeRange";
import AssetCommunicationGraph from "./AssetCommunicationGraph";
import {countryName, externalPeerLabel, filteredLink, formatBytes, formatCount, isUnresolvedExternal, macFilter} from "./AssetProfileHelpers";
import {countryCentroid} from "../../../shared/maps/CountryCentroids";

const assetsService = new AssetsService();

const GRAPH_HEIGHT = 520;
const INFRASTRUCTURE_SHOWN = 5;

const EDGE_LIMIT = 500;

const ConnectionGlobe = React.lazy(() => import("../../../shared/maps/ConnectionGlobe"));

// Networks closer together than this many decimals of a degree are one location on the map.
const LOCATION_PRECISION = 2;

const peerLabel = (edge) => {
  if (edge.peer_type === "INTERNAL") {
    return edge.peer_address ? edge.peer_address : edge.peer_mac;
  }

  return externalPeerLabel(edge);
}

export default function AssetCommunicationCard({asset, organizationId, tenantId, attributes, revision, onRefresh}) {

  const [timeRange, setTimeRange] = useState(Presets.RELATIVE_DAYS_7);
  const [communication, setCommunication] = useState(null);
  const [minBytes, setMinBytes] = useState(0);
  const [peerType, setPeerType] = useState("ALL");
  const [direction, setDirection] = useState("ALL");
  const [l4Type, setL4Type] = useState("ALL");
  const [protocol, setProtocol] = useState("ALL");
  const [port, setPort] = useState("");
  const [selection, setSelection] = useState(null);
  // Node to select and center in the graph, set from the peer tables. A counter makes repeated clicks on the same
  // row take effect again.
  const [focus, setFocus] = useState(null);
  const [view, setView] = useState("graph");
  const [mapSelection, setMapSelection] = useState(null);
  const [mapFocus, setMapFocus] = useState(null);

  useEffect(() => {
    setCommunication(null);
    setSelection(null);
    setMapSelection(null);
    assetsService.getAssetProfileCommunication(asset.uuid, organizationId, tenantId, timeRange, EDGE_LIMIT, setCommunication);
  }, [asset.uuid, organizationId, tenantId, timeRange, revision]);

  const onExpand = useCallback((peerAsset) => new Promise((resolve, reject) => {
    assetsService.getAssetProfileCommunication(peerAsset.uuid, organizationId, tenantId, timeRange, EDGE_LIMIT,
      (data) => resolve(data.edges),
      (error) => {
        toast.error("Could not load the communication of the peer. (HTTP " + error.response.status + ")");
        reject(error);
      });
  }), [organizationId, tenantId, timeRange]);

  const onSelect = useCallback((s) => setSelection(s), []);

  const allEdges = useMemo(() => communication ? communication.edges : [], [communication]);

  // The server joins the tags of a session like "HTTP/WebSocket".
  const tagsOf = (e) => (e.protocol ? e.protocol.split("/") : []);

  const protocols = useMemo(() => {
    const names = new Set();
    let untagged = false;
    allEdges.forEach((e) => {
      const tags = tagsOf(e);
      if (tags.length === 0) untagged = true;
      tags.forEach((t) => names.add(t));
    });
    const sorted = Array.from(names).sort((a, b) => a.localeCompare(b));
    return { names: sorted, untagged: untagged };
  }, [allEdges]);

  const portNumber = port.trim() === "" ? null : parseInt(port.trim(), 10);
  const portValid = portNumber === null || (!isNaN(portNumber) && portNumber >= 0 && portNumber <= 65535);

  const edges = useMemo(() => allEdges.filter((e) =>
    (peerType === "ALL" || e.peer_type === peerType)
      && (direction === "ALL" || e.direction === direction)
      && (l4Type === "ALL" || e.l4_type === l4Type)
      && (protocol === "ALL" || (protocol === "NONE" ? tagsOf(e).length === 0 : tagsOf(e).includes(protocol)))
      && (portNumber === null || !portValid || e.port === portNumber)
  ), [allEdges, peerType, direction, l4Type, protocol, portNumber, portValid]);

  const filtersActive = peerType !== "ALL" || direction !== "ALL" || l4Type !== "ALL" || protocol !== "ALL"
    || (portNumber !== null && portValid) || minBytes > 0;

  const filterSelect = (id, label, value, setValue, options) => (
    <div className="d-flex align-items-center me-3 mb-2">
      <label htmlFor={id} className="me-2 small text-muted mb-0">{label}</label>
      <select id={id} className="form-select form-select-sm w-auto" value={value} onChange={(e) => setValue(e.target.value)}>
        {options.map(([v, title]) => <option key={v} value={v}>{title}</option>)}
      </select>
    </div>
  )

  const peers = useMemo(() => {
    const byKey = {};
    edges.forEach((e) => {
      const key = e.peer_type + ":" + e.peer_key;
      if (!byKey[key]) {
        byKey[key] = {...e, sessions: 0, bytes_rx: 0, bytes_tx: 0, edge_count: 0, inbound: false, outbound: false, first_seen: e.first_seen, last_seen: e.last_seen};
      }
      const p = byKey[key];
      p.edge_count += 1;
      p.sessions += e.sessions;
      p.bytes_rx += e.bytes_rx;
      p.bytes_tx += e.bytes_tx;
      if (e.direction === "INBOUND") p.inbound = true;
      if (e.direction === "OUTBOUND") p.outbound = true;
      if (moment(e.first_seen).isBefore(p.first_seen)) p.first_seen = e.first_seen;
      if (moment(e.last_seen).isAfter(p.last_seen)) p.last_seen = e.last_seen;
      if (!p.peer_asset && e.peer_asset) p.peer_asset = e.peer_asset;
    });

    // The traffic threshold works per peer, like the graph hides links per peer, so strip, tables and graph agree.
    const all = Object.values(byKey)
      .filter((p) => p.bytes_rx + p.bytes_tx >= minBytes)
      .sort((a, b) => (b.bytes_rx + b.bytes_tx) - (a.bytes_rx + a.bytes_tx));
    return {
      edgeCount: all.reduce((sum, p) => sum + p.edge_count, 0),
      internal: all.filter((p) => p.peer_type === "INTERNAL").slice(0, 10),
      external: all.filter((p) => p.peer_type === "EXTERNAL").slice(0, 10),
      externalAll: all.filter((p) => p.peer_type === "EXTERNAL"),
      internalCount: all.filter((p) => p.peer_type === "INTERNAL").length,
      // Only resolved networks count. Without GeoIP data all external traffic is one unresolved peer.
      externalCount: all.filter((p) => p.peer_type === "EXTERNAL" && !isUnresolvedExternal(p)).length,
      hasUnresolvedExternal: all.some(isUnresolvedExternal),
      sessions: all.reduce((sum, p) => sum + p.sessions, 0),
      bytes: all.reduce((sum, p) => sum + p.bytes_rx + p.bytes_tx, 0),
      dependents: all.filter((p) => p.peer_type === "INTERNAL" && p.inbound).length,
      externalInbound: all.filter((p) => p.peer_type === "EXTERNAL" && p.inbound).length
    };
  }, [edges, minBytes]);

  const geo = useMemo(() => {
    const external = peers.externalAll;
    const byLocation = {};
    let unlocated = 0;
    let unresolved = 0;

    external.forEach((p) => {
      let id, lat, lng, precision;
      if (typeof p.peer_latitude === "number" && typeof p.peer_longitude === "number") {
        lat = Number(p.peer_latitude.toFixed(LOCATION_PRECISION));
        lng = Number(p.peer_longitude.toFixed(LOCATION_PRECISION));
        id = "loc:" + lat + "," + lng;
        precision = "CITY";
      } else {
        const centroid = countryCentroid(p.peer_country_code);
        if (!centroid) {
          unlocated += 1;
          if (isUnresolvedExternal(p)) unresolved += 1;
          return;
        }
        lat = centroid.lat;
        lng = centroid.lng;
        id = "country:" + p.peer_country_code;
        precision = "COUNTRY";
      }

      if (!byLocation[id]) {
        byLocation[id] = {
          id: id, lat: lat, lng: lng, precision: precision, city: p.peer_city, country_code: p.peer_country_code,
          networks: [], sessions: 0, bytes: 0, bytes_rx: 0, bytes_tx: 0, inbound: false, outbound: false,
          first_seen: p.first_seen, last_seen: p.last_seen
        };
      }

      const l = byLocation[id];
      l.networks.push(p);
      l.sessions += p.sessions;
      l.bytes += p.bytes_rx + p.bytes_tx;
      l.bytes_rx += p.bytes_rx;
      l.bytes_tx += p.bytes_tx;
      if (p.inbound) l.inbound = true;
      if (p.outbound) l.outbound = true;
      if (l.precision === "CITY" && !l.city && p.peer_city) l.city = p.peer_city;
      if (moment(p.first_seen).isBefore(l.first_seen)) l.first_seen = p.first_seen;
      if (moment(p.last_seen).isAfter(l.last_seen)) l.last_seen = p.last_seen;
    });

    const locations = Object.values(byLocation).map((l) => {
      l.networks.sort((a, b) => (b.bytes_rx + b.bytes_tx) - (a.bytes_rx + a.bytes_tx));
      const country = countryName(l.country_code);
      return {
        ...l,
        label: l.precision === "CITY" && l.city ? l.city + (country ? ", " + country : "") : (country ? country : "Unknown location"),
        detail: formatCount(l.networks.length) + (l.networks.length === 1 ? " network: " : " networks: ")
          + l.networks.slice(0, 3).map(externalPeerLabel).join(", ") + (l.networks.length > 3 ? ", …" : ""),
        direction: l.inbound && l.outbound ? "BOTH" : (l.inbound ? "INBOUND" : "OUTBOUND")
      };
    });

    return { locations: locations, external: external.length, unlocated: unlocated, unresolved: unresolved };
  }, [peers]);

  const selectedDays = useMemo(() => {
    switch (timeRange.type) {
      case "relative": return Math.ceil(timeRange.minutes / 1440);
      case "absolute": return Math.ceil(moment(timeRange.to).diff(moment(timeRange.from), "days", true));
      default: return null;
    }
  }, [timeRange]);

  const fact = (label, value, detail = null, last = false) => (
    <div className={"py-1 " + (last ? "" : "pe-4 me-4 border-end")}>
      <div className="small text-muted text-uppercase">{label}</div>
      <div>
        {value}
        {detail ? <span className="text-muted"> ({detail})</span> : null}
      </div>
    </div>
  )

  const strip = () => {
    const retention = communication.retention_days;
    const hasRetention = retention !== undefined && retention !== null;
    const exceedsRetention = hasRetention && selectedDays !== null && selectedDays > retention;

    return (
      <div className="d-flex flex-wrap align-items-center mb-3">
        {fact("Internal Peers", formatCount(peers.internalCount))}
        {fact("External Networks", formatCount(peers.externalCount), peers.hasUnresolvedExternal ? "plus unresolved traffic, no GeoIP data" : null)}
        {fact("Sessions", formatCount(peers.sessions))}
        {fact("Traffic", formatBytes(peers.bytes))}
        {fact("Inbound From", formatCount(peers.dependents) + " internal " + (peers.dependents === 1 ? "peer" : "peers"),
          peers.externalInbound > 0
            ? <span className="text-warning">{formatCount(peers.externalInbound)} external {peers.externalInbound === 1 ? "network" : "networks"}</span>
            : null)}
        {fact("Edges",
          <span>
            {filtersActive ? formatCount(peers.edgeCount) + " of " + formatCount(allEdges.length) : formatCount(allEdges.length)}
            {allEdges.length >= EDGE_LIMIT
              ? <i className="fa-solid fa-triangle-exclamation text-warning ms-2"
                   title={"Limit reached. Only the " + EDGE_LIMIT + " edges with the most traffic are loaded, ranked by bytes sent and received."} />
              : <i className="fa-regular fa-circle-question text-muted ms-2"
                   title={"All edges are loaded. At most the " + EDGE_LIMIT + " edges with the most traffic are shown, ranked by bytes sent and received."} />}
          </span>)}
        {fact("Retention", hasRetention
          ? <span className={exceedsRetention ? "text-warning" : ""}>{formatCount(retention)} days</span>
          : <span className="text-muted">n/a</span>,
          exceedsRetention ? "shorter than the selected time range" : null, true)}
      </div>
    )
  }

  const INFRASTRUCTURE = [
    ["GATEWAY_MAC", "Gateway"], ["DNS_SERVER", "DNS Server"], ["DHCP_SERVER", "DHCP Server"], ["NTP_SERVER", "NTP Server"]
  ];

  const allInfrastructureRows = useMemo(() => {
    if (!attributes) {
      return [];
    }
    return INFRASTRUCTURE.flatMap(([dimension, role]) =>
      (attributes.attributes[dimension] || []).map((v) => ({
        key: dimension + ":" + v.value, role: role, value: v.value, count: v.count, first_seen: v.first_seen, last_seen: v.last_seen,
        l4_address: v.l4_address, mac_address: v.mac_address
      }))
    );
  }, [attributes]);

  const [expandedRoles, setExpandedRoles] = useState({});

  const infrastructure = useMemo(() => {
    const shown = [];
    const folded = [];
    INFRASTRUCTURE.forEach(([, role]) => {
      const rows = allInfrastructureRows.filter((r) => r.role === role).sort((a, b) => b.count - a.count);
      if (rows.length <= INFRASTRUCTURE_SHOWN || expandedRoles[role]) {
        shown.push(...rows);
        return;
      }
      shown.push(...rows.slice(0, INFRASTRUCTURE_SHOWN));
      const rest = rows.slice(INFRASTRUCTURE_SHOWN);
      folded.push({ role: role, count: rest.length, observations: rest.reduce((sum, r) => sum + r.count, 0) });
    });
    return { rows: shown, folded: folded };
  }, [allInfrastructureRows, expandedRoles]);

  const infrastructureColumns = [
    { key: "role", label: "Role", value: (r) => r.role, render: (r) => r.role },
    { key: "value", label: "Address", value: (r) => r.value, render: (r) => {
        if (r.l4_address) return <L4Address address={r.l4_address} hidePort={true} withAssetName={true} />;
        if (r.mac_address) return <EthernetMacAddress addressWithContext={r.mac_address} withAssetLink={true} withAssetName={true} hideActiveIndicator={true} />;
        return <span className="machine-data">{r.value}</span>;
      } },
    { key: "count", label: "Observed", value: (r) => r.count, render: (r) => formatCount(r.count) + (r.count === 1 ? " time" : " times") },
    { key: "first_seen", label: "First Seen", value: (r) => moment(r.first_seen).valueOf(), render: (r) => <span title={moment(r.first_seen).format()}>{moment(r.first_seen).fromNow()}</span> },
    { key: "last_seen", label: "Last Seen", value: (r) => moment(r.last_seen).valueOf(), render: (r) => <span title={moment(r.last_seen).format()}>{moment(r.last_seen).fromNow()}</span> },
  ];

  const directionText = (p) => (p.outbound && p.inbound ? "Both" : (p.outbound ? "Outbound" : "Inbound"));

  const peerCell = (p) => {
    if (p.peer_type === "INTERNAL") {
      if (p.peer) {
        return <L4Address address={p.peer} hidePort={true} hideFlag={true} withAssetName={true} />;
      }
      return (
        <span>
          {peerLabel(p)}
          {p.peer_asset && p.peer_asset.name
            ? <span className="context-name context-name-asset ms-1">{p.peer_asset.name}</span> : null}
        </span>
      );
    }

    return (
      <span>
        <Flag code={p.peer_country_code ? p.peer_country_code : "NONE"} />{' '}
        {peerLabel(p)}
      </span>
    );
  }

  const switchView = (v) => {
    setView(v);
    setSelection(null);
    setMapSelection(null);
  }

  const locate = (p) => {
    if (view === "map" && p.peer_type === "EXTERNAL") {
      const location = geo.locations.find((l) => l.networks.some((n) => n.peer_key === p.peer_key));
      if (location) {
        setMapSelection(location);
        setMapFocus({id: location.id, at: Date.now()});
        return;
      }
    }

    if (view !== "graph") {
      switchView("graph");
    }
    setFocus({id: p.peer_type + ":" + p.peer_key, at: Date.now()});
  }

  const locateButton = (p) => (
    <button type="button"
            className="btn btn-link btn-sm p-0 me-2 align-baseline"
            title={view === "map" ? "Show on map" : "Show in graph"}
            onClick={(e) => { e.preventDefault(); locate(p); }}>
      <i className="fa-solid fa-crosshairs" />
    </button>
  );

  const peerColumns = [
    { key: "peer", label: "Peer", value: (p) => peerLabel(p), render: (p) => <span>{locateButton(p)}{peerCell(p)}</span> },
    { key: "direction", label: "Direction", value: (p) => directionText(p), render: (p) => directionText(p) },
    { key: "sessions", label: "Sessions", value: (p) => p.sessions, render: (p) => formatCount(p.sessions) },
    { key: "bytes_tx", label: "Sent", value: (p) => p.bytes_tx, render: (p) => formatBytes(p.bytes_tx) },
    { key: "bytes_rx", label: "Received", value: (p) => p.bytes_rx, render: (p) => formatBytes(p.bytes_rx) },
    { key: "last_seen", label: "Last Seen", value: (p) => moment(p.last_seen).valueOf(),
      render: (p) => <span title={moment(p.last_seen).format()}>{moment(p.last_seen).fromNow()}</span> },
  ];

  const peersTable = (rows, emptyText) => {
    if (rows.length === 0) {
      return <div className="text-muted">{emptyText}</div>;
    }

    return <SortableTable columns={peerColumns} rows={rows} rowKey={(p) => p.peer_type + ":" + p.peer_key} defaultColumn="bytes_rx" />;
  }

  const l4SessionsLink = (field, value, operator = "equals") => filteredLink(ApiRoutes.ETHERNET.L4.SESSIONS, {
    ...macFilter("source_mac", asset.mac.address),
    [field]: [{field: field, operator: operator, value: value}]
  });

  const selectionPanel = () => {
    if (!selection) {
      return (
        <div className="d-flex align-items-center justify-content-center text-center text-muted h-100">
          Click a node in the graph to see its details here.
        </div>
      );
    }

    const node = selection.node;
    let sessions = 0, bytesIn = 0, bytesOut = 0;
    let firstSeen = null, lastSeen = null;
    const l4 = new Set();
    selection.links.forEach((e) => {
      sessions += e.sessions;
      bytesIn += e.bytes_rx;
      bytesOut += e.bytes_tx;
      l4.add(e.l4_type);
      if (!firstSeen || moment(e.first_seen).isBefore(firstSeen)) firstSeen = e.first_seen;
      if (!lastSeen || moment(e.last_seen).isAfter(lastSeen)) lastSeen = e.last_seen;
    });

    return (
      <React.Fragment>
        <h6 className="mb-1">
          {node.label}
          {node.name ? <span className="context-name context-name-asset ms-1">{node.name}</span> : null}
        </h6>
        <dl className="mb-2">
          <dt>Type</dt>
          <dd>{node.type === "ROOT" ? "Asset" : node.type === "INTERNAL" ? "Internal peer" : "External network"}</dd>
          {node.mac ? <React.Fragment><dt>MAC Address</dt><dd className="machine-data">{node.mac}</dd></React.Fragment> : null}
          {node.address ? <React.Fragment><dt>IP Address</dt><dd className="machine-data">{node.address}</dd></React.Fragment> : null}
          {node.asn_number ? <React.Fragment><dt>Autonomous System</dt><dd>AS{node.asn_number} {node.asn_name}</dd></React.Fragment> : null}
          {node.country_code ? <React.Fragment><dt>Country</dt><dd><Flag code={node.country_code} /> {node.country_code}</dd></React.Fragment> : null}
          {node.type === "EXTERNAL" && !node.asn_number && !node.country_code
            ? <React.Fragment>
                <dt>Network</dt>
                <dd className="text-muted">Not resolved. Autonomous system and country need GeoIP data, which Nzyme Connect provides.</dd>
              </React.Fragment>
            : null}
          <dt>Sessions</dt>
          <dd>{formatCount(sessions)} ({Array.from(l4).join(", ")})</dd>
          <dt>Traffic</dt>
          <dd>{formatBytes(bytesOut)} sent, {formatBytes(bytesIn)} received</dd>
          {firstSeen ? <React.Fragment><dt>Seen</dt><dd className="mb-0">{moment(firstSeen).format("YYYY-MM-DD HH:mm")} to {moment(lastSeen).format("YYYY-MM-DD HH:mm")}</dd></React.Fragment> : null}
        </dl>
        {node.type === "INTERNAL" && node.asset
          ? <PivotLink to={ApiRoutes.ETHERNET.ASSETS.DETAILS(node.asset.uuid)} button="btn btn-sm btn-outline-primary" className="me-1">Open Asset</PivotLink>
          : null}
        {node.type === "INTERNAL" && node.address
          ? <PivotLink to={l4SessionsLink("destination_address", node.address)} button="btn btn-sm btn-outline-secondary" className="me-1">Sessions to Peer</PivotLink>
          : null}
        {node.type === "EXTERNAL" && node.asn_number
          ? <PivotLink to={l4SessionsLink("destination_address_geo_asn_number", String(node.asn_number), "equals_numeric")} button="btn btn-sm btn-outline-secondary">Sessions to Network</PivotLink>
          : null}
      </React.Fragment>
    )
  }

  const mapSelectionPanel = () => {
    const l = mapSelection ? geo.locations.find((x) => x.id === mapSelection.id) : null;
    if (!l) {
      return (
        <div className="d-flex align-items-center justify-content-center text-center text-muted h-100">
          Click a location on the map to see its details here.
        </div>
      );
    }

    const country = countryName(l.country_code);

    return (
      <React.Fragment>
        <h6 className="mb-3 d-flex align-items-center">
          <Flag code={l.country_code ? l.country_code : "NONE"} />
          <span className="ms-2">{l.precision === "CITY" && l.city ? l.city : (country ? country : "Unknown location")}</span>
        </h6>
        <dl className="mb-2">
          {country && l.precision === "CITY" && l.city
            ? <React.Fragment><dt>Country</dt><dd>{country} ({l.country_code})</dd></React.Fragment>
            : null}
          <dt>Placement</dt>
          {l.precision === "CITY"
            ? <dd className="machine-data">{l.lat}, {l.lng}</dd>
            : <dd>Country level, GeoIP knows no city for these networks</dd>}
          <dt>Direction</dt>
          <dd>{directionText(l)}</dd>
          <dt>Sessions</dt>
          <dd>{formatCount(l.sessions)}</dd>
          <dt>Traffic</dt>
          <dd>{formatBytes(l.bytes_tx)} sent, {formatBytes(l.bytes_rx)} received</dd>
          <dt>Seen</dt>
          <dd className="mb-0">{moment(l.first_seen).format("YYYY-MM-DD HH:mm")} to {moment(l.last_seen).format("YYYY-MM-DD HH:mm")}</dd>
        </dl>
        <h6 className="mb-1">{l.networks.length === 1 ? "Network" : "Networks (" + formatCount(l.networks.length) + ")"}</h6>
        <ul className="list-unstyled mb-2">
          {l.networks.slice(0, 10).map((n) => (
            <li key={n.peer_key} className="d-flex justify-content-between small">
              <span className="text-truncate me-2" title={externalPeerLabel(n)}>{externalPeerLabel(n)}</span>
              <span className="text-nowrap">{formatBytes(n.bytes_rx + n.bytes_tx)}</span>
            </li>
          ))}
          {l.networks.length > 10
            ? <li className="text-muted small">{formatCount(l.networks.length - 10)} more</li>
            : null}
        </ul>
        {l.country_code
          ? <PivotLink to={l4SessionsLink("destination_address_geo_country_code", l.country_code)} button="btn btn-sm btn-outline-secondary">Sessions to Country</PivotLink>
          : null}
      </React.Fragment>
    )
  }

  const mapInfo = (text) => (
    <div className="border rounded d-flex align-items-center justify-content-center text-muted text-center p-4"
         style={{height: GRAPH_HEIGHT + "px"}}>
      <div style={{maxWidth: "34rem"}}>{text}</div>
    </div>
  );

  const noCommunication = () => mapInfo("No communication was recorded for this asset in the selected time range.");

  const mapView = () => {
    if (geo.external === 0) {
      return mapInfo(filtersActive
        ? "No external communication matches the selected filters. The map shows external networks only."
        : "All communication in the selected time range is internal. The map shows external networks only.");
    }

    if (geo.locations.length === 0) {
      if (geo.unresolved === geo.external) {
        return mapInfo("No GeoIP data. Placing external networks on the map needs their country, which Nzyme Connect provides.");
      }
      return mapInfo("No country is known for the external networks in this selection.");
    }

    if (!communication.origin) {
      return mapInfo("The location of this asset is unknown, so no flows can be drawn. Assign the tap that observes "
        + "it to a location with coordinates, or give the tap coordinates, and the map shows where its traffic goes.");
    }

    const origin = {
      lat: communication.origin.latitude,
      lng: communication.origin.longitude,
      label: asset.name ? asset.name : asset.mac.address,
      detail: communication.origin.location_name
        ? communication.origin.location_name + ", tap " + communication.origin.tap_name
        : "Tap " + communication.origin.tap_name
    };

    return (
      <React.Fragment>
        <Suspense fallback={
          <div className="border rounded d-flex align-items-center justify-content-center" style={{height: GRAPH_HEIGHT + "px"}}>
            <LoadingSpinner />
          </div>}>
          <ConnectionGlobe origin={origin}
                           points={geo.locations}
                           selectedId={mapSelection ? mapSelection.id : null}
                           onSelect={setMapSelection}
                           focus={mapFocus}
                           height={GRAPH_HEIGHT} />
        </Suspense>
        {geo.unlocated > 0
          ? <div className="text-muted small mt-1">
              {formatCount(geo.unlocated)} external {geo.unlocated === 1 ? "network" : "networks"} without a known
              location {geo.unlocated === 1 ? "is" : "are"} not on the map.
            </div>
          : null}
      </React.Fragment>
    );
  }

  return (
    <React.Fragment>
      <CardTitleWithControls title="Communication"
                             timeRange={timeRange}
                             setTimeRange={setTimeRange}
                             timeRangeGranularity="day"
                             doNotPersistTimeRange={true}
                             refreshAction={onRefresh} />

      {!communication ? <LoadingSpinner /> :
        <React.Fragment>
          {strip()}

          <div className="d-flex flex-wrap align-items-center">
            {filterSelect("asset-comm-peer-type", "Peers", peerType, setPeerType,
              [["ALL", "All"], ["INTERNAL", "Internal"], ["EXTERNAL", "External"]])}
            {filterSelect("asset-comm-direction", "Direction", direction, setDirection,
              [["ALL", "Both"], ["OUTBOUND", "Outbound"], ["INBOUND", "Inbound"]])}
            {filterSelect("asset-comm-l4", "Transport", l4Type, setL4Type,
              [["ALL", "All"], ["TCP", "TCP"], ["UDP", "UDP"]])}
            {filterSelect("asset-comm-protocol", "Protocol", protocol, setProtocol,
              [["ALL", "All"]].concat(protocols.names.map((n) => [n, n]))
                .concat(protocols.untagged ? [["NONE", "Untagged"]] : []))}
            <div className="d-flex align-items-center me-3 mb-2">
              <label htmlFor="asset-comm-port" className="me-2 small text-muted mb-0">Port</label>
              <input id="asset-comm-port"
                     type="text"
                     inputMode="numeric"
                     className={"form-control form-control-sm" + (portValid ? "" : " is-invalid")}
                     style={{width: "5.5rem"}}
                     placeholder="Any"
                     value={port}
                     onChange={(e) => setPort(e.target.value)} />
            </div>
            <div className="d-flex align-items-center me-3 mb-2">
              <label htmlFor="asset-comm-min-bytes" className="me-2 small text-muted mb-0">Hide links below</label>
              <select id="asset-comm-min-bytes"
                      className="form-select form-select-sm w-auto"
                      value={minBytes}
                      onChange={(e) => setMinBytes(parseInt(e.target.value, 10))}>
                <option value={0}>Show all</option>
                <option value={10240}>10 KB</option>
                <option value={102400}>100 KB</option>
                <option value={1048576}>1 MB</option>
                <option value={10485760}>10 MB</option>
                <option value={104857600}>100 MB</option>
              </select>
            </div>
            <div className="ms-auto mb-2">
              <div className="btn-group btn-group-sm" role="group" aria-label="View">
                <button type="button"
                        className={"btn " + (view === "graph" ? "btn-secondary" : "btn-outline-secondary")}
                        onClick={() => switchView("graph")}>
                  Graph
                </button>
                <button type="button"
                        className={"btn " + (view === "map" ? "btn-secondary" : "btn-outline-secondary")}
                        onClick={() => switchView("map")}>
                  Map
                </button>
              </div>
            </div>
          </div>

          <div className="row">
            <div className="col-md-9">
              {view === "graph"
                ? (allEdges.length === 0 ? noCommunication() : <AssetCommunicationGraph rootMac={asset.mac.address}
                                           rootLabel={asset.name}
                                           edges={edges}
                                           minBytes={minBytes}
                                           onExpand={onExpand}
                                           onSelect={onSelect}
                                           focus={focus}
                                           height={GRAPH_HEIGHT} />)
                : (allEdges.length === 0 ? noCommunication() : mapView())}
            </div>
            <div className="col-md-3">
              <div className="border rounded p-3 d-flex flex-column" style={{height: GRAPH_HEIGHT + "px", overflowY: "auto"}}>
                <div className="small text-muted text-uppercase mb-2">{view === "graph" ? "Selected Node" : "Selected Location"}</div>
                <div className="flex-grow-1 d-flex flex-column">
                  {view === "graph" ? selectionPanel() : mapSelectionPanel()}
                </div>
              </div>
            </div>
          </div>

          <div className="row mt-3">
            <div className="col-md-6">
              <h6 className="mb-1">Top Internal Peers</h6>
              {peersTable(peers.internal, "No internal peers in the selected time range.")}
            </div>
            <div className="col-md-6">
              <h6 className="mb-1">Top External Networks</h6>
              {peersTable(peers.external, "No external communication in the selected time range.")}
            </div>
          </div>

          <div className="row mt-3">
            <div className="col-md-12">
              <h6 className="mb-1">Infrastructure Used</h6>
              <p className="text-muted small mb-2">Long-term, not limited to the selected time range.</p>
              {allInfrastructureRows.length === 0
                ? <div className="text-muted">No gateway, DNS, DHCP or NTP server observed for this asset.</div>
                : <React.Fragment>
                    <SortableTable columns={infrastructureColumns} rows={infrastructure.rows} rowKey={(r) => r.key} defaultColumn="role" defaultDirection="ASC" />
                    {infrastructure.folded.map((f) => (
                      <div key={f.role} className="text-muted small mt-2">
                        {f.role}: {formatCount(f.count)} more {f.count === 1 ? "address" : "addresses"} with{' '}
                        {formatCount(f.observations)} {f.observations === 1 ? "observation" : "observations"} in total.{' '}
                        <a href="#" onClick={(e) => { e.preventDefault(); setExpandedRoles({...expandedRoles, [f.role]: true}); }}>Show all</a>
                      </div>
                    ))}
                    {Object.keys(expandedRoles).some((role) => expandedRoles[role])
                      ? <div className="text-muted small mt-2">
                          <a href="#" onClick={(e) => { e.preventDefault(); setExpandedRoles({}); }}>Show only the busiest addresses per role</a>
                        </div>
                      : null}
                  </React.Fragment>}
            </div>
          </div>
        </React.Fragment>}
    </React.Fragment>
  )

}
