import React, {useMemo} from "react";
import moment from "moment";
import Flag from "../../misc/Flag";
import PivotLink from "../../shared/PivotLink";
import FilterValueIcon from "../../shared/filtering/FilterValueIcon";
import VizSelectionActions from "../../shared/viz/VizSelectionActions";
import ApiRoutes from "../../../util/ApiRoutes";
import TopologyService from "../../../services/ethernet/TopologyService";
import useTopologyRequest from "./useTopologyRequest";
import {countryName, formatBytes, formatCount} from "../assets/details/AssetProfileHelpers";
import {
  buildBreakdown,
  LINK_CLASS_TITLES,
  nodeFilter,
  nodeKind,
  nodeSessionFilters,
  OTHER,
  serviceLabel,
  serviceName,
  serviceSessionFilters,
  sessionsLink
} from "./TopologyData";

const topologyService = new TopologyService();

const LIST_LENGTH = 8;
const BREAKDOWN_LIMIT = 2000;

const EMPTY_TEXT = {
  graph: "Click a node or a connection in the graph to see its details here.",
  matrix: "Click a cell or a label in the matrix to see its details here.",
  flow: "Click a source, service or destination in the diagram to see its details here.",
  map: "Click a site or a place on the map to see its details here."
};

const sessionsText = (count) => formatCount(count) + (count === 1 ? " session" : " sessions");

const seenText = (first, last) => moment(first).format("YYYY-MM-DD HH:mm") + " to " + moment(last).format("YYYY-MM-DD HH:mm");

function PanelList({title, rows, empty = null}) {
  if (rows === null) {
    return (
        <React.Fragment>
          <h6 className="mb-1">{title}</h6>
          <div className="text-muted small mb-2">Loading.</div>
        </React.Fragment>
    );
  }

  if (rows.length === 0) {
    return empty ? (
        <React.Fragment>
          <h6 className="mb-1">{title}</h6>
          <div className="text-muted small mb-2">{empty}</div>
        </React.Fragment>
    ) : null;
  }

  return (
      <React.Fragment>
        <h6 className="mb-1">{title}</h6>
        <ul className="list-unstyled mb-2">
          {rows.map((row) => (
              <li key={row.key} className="d-flex justify-content-between small" title={row.title}>
                <span className="text-truncate me-2">
                  {row.onClick
                      ? <a href="#" onClick={(e) => { e.preventDefault(); row.onClick(); }}>{row.label}</a>
                      : row.label}
                  {row.detail ? <span className="text-muted ms-1">{row.detail}</span> : null}
                </span>
                <span className="text-nowrap">{row.value}</span>
              </li>
          ))}
        </ul>
      </React.Fragment>
  );
}

export default function TopologySelectionPanel({selection, view, linkModel, flowModel, geoModel, request, timeRange,
                                                 timeWindow, pageFilters, setFilters, filterFields, onSelect, onAddFilter,
                                                 onExclude, height}) {

  const filterIcon = (filter) => (filter
      ? <span className="ms-2">
          <FilterValueIcon setFilters={setFilters} fields={filterFields} field={filter.field} value={filter.value}
                           operator={filter.operator} />
        </span>
      : null);

  const breakdown = useMemo(() => {
    if (!selection) return null;

    switch (selection.kind) {
      case "node":
        return {by: "service", nodes: [selection.id]};
      case "link":
        return {by: "service", nodes: [selection.a, selection.b]};
      case "place": {
        const place = geoModel ? geoModel.points.find((p) => p.id === selection.id) : null;
        return place ? {by: "network", nodes: [place.node_id]} : null;
      }
      default:
        return null;
    }
  }, [selection, geoModel]);

  const breakdownRequest = useMemo(() => {
    if (!breakdown) return null;

    const nodes = request.nodes.slice();
    breakdown.nodes.forEach((id) => {
      if (!nodes.includes(id)) nodes.push(id);
    });

    return {...request, nodes: nodes};
  }, [breakdown, request]);

  const breakdownBy = breakdown ? breakdown.by : null;
  const breakdownData = useTopologyRequest(
      breakdownRequest ? JSON.stringify([breakdownBy, breakdownRequest]) : null,
      breakdownRequest !== null,
      (setData) => topologyService.getLinks(breakdownRequest, "SUBNET", breakdownBy === "service" ? "COUNTRY" : "NETWORK",
          breakdownBy === "service" ? "PORT" : null, BREAKDOWN_LIMIT, setData)
  );

  const breakdownRows = useMemo(
      () => (breakdownData && breakdownBy ? buildBreakdown(breakdownData, timeWindow, breakdownBy) : null),
      [breakdownData, breakdownBy, timeWindow]);

  const pivot = (filters) => sessionsLink(pageFilters, filters, timeRange, timeWindow);

  // Set by the section that renders the selection.
  let pivots = [];

  const serviceRows = () => {
    if (breakdownRows === null) return null;
    return breakdownRows.rows.slice(0, LIST_LENGTH).map((row) => ({
      key: row.id,
      label: <span className="machine-data">{serviceLabel(row.service)}</span>,
      detail: serviceName(row.service),
      value: formatBytes(row.bytes_tx + row.bytes_rx),
      title: sessionsText(row.sessions) + ", " + formatBytes(row.bytes_tx) + " from clients, "
          + formatBytes(row.bytes_rx) + " from servers"
    }));
  }

  const moreServices = () => {
    if (breakdownRows === null || breakdownRows.rows.length <= LIST_LENGTH) return null;
    const more = formatCount(breakdownRows.rows.length - LIST_LENGTH);
    return <div className="text-muted small mb-2">{breakdownRows.truncated ? "Over " + more : more} more</div>;
  }

  const empty = () => (
      <div className="d-flex align-items-center justify-content-center text-center text-muted h-100">
        {EMPTY_TEXT[view]}
      </div>
  );

  const nodePanel = (model) => {
    const node = model.nodes.get(selection.id);
    if (!node) return empty();

    const data = node.data;
    const neighbors = model.pairs
        .filter((p) => p.active && (p.a === node.id || p.b === node.id))
        .sort((x, y) => y.bytes - x.bytes);

    const sourceFilters = nodeSessionFilters(data, "source");
    const destinationFilters = nodeSessionFilters(data, "destination");

    pivots = [
      data.asset ? {label: "Open Asset", to: ApiRoutes.ETHERNET.ASSETS.DETAILS(data.asset.uuid)} : null,
      data.level === "HOST" ? {label: "Address Details", to: ApiRoutes.ETHERNET.IP.ADDRESS_DETAILS(data.address)} : null,
      sourceFilters ? {label: "Sessions From", to: pivot(sourceFilters)} : null,
      destinationFilters ? {label: "Sessions To", to: pivot(destinationFilters)} : null
    ].filter((p) => p !== null);

    return (
        <React.Fragment>
          <h6 className="mb-1" style={{wordBreak: "break-all"}}>
            {node.label}
            {node.name ? <span className="context-name context-name-asset ms-1">{node.name}</span> : null}
          </h6>
          <dl className="mb-2">
            <dt>Type</dt>
            <dd>{nodeKind(data)}</dd>
            {data.asset ? <React.Fragment><dt>MAC Address</dt><dd className="machine-data">{data.asset.mac}</dd></React.Fragment> : null}
            {data.hostname ? <React.Fragment><dt>Hostname</dt><dd>{data.hostname}</dd></React.Fragment> : null}
            {data.asn_number ? <React.Fragment><dt>Autonomous System</dt><dd>AS{data.asn_number} {data.asn_name}{filterIcon({field: "address_geo_asn_number", operator: "equals_numeric", value: String(data.asn_number)})}</dd></React.Fragment> : null}
            {data.country_code ? <React.Fragment><dt>Country</dt><dd><Flag code={data.country_code} /> {countryName(data.country_code)}{filterIcon({field: "address_geo_country_code", operator: "equals", value: data.country_code})}</dd></React.Fragment> : null}
            {data.type === "EXTERNAL" && data.level !== "COUNTRY" && !data.asn_number && !data.country_code
                ? <React.Fragment>
                    <dt>Network</dt>
                    <dd className="text-muted">Not resolved. Autonomous system and country need GeoIP data, which Nzyme Connect provides.</dd>
                  </React.Fragment>
                : null}
            <dt>Sessions</dt>
            <dd>{formatCount(node.sessions)}</dd>
            <dt>Traffic</dt>
            <dd>{formatBytes(node.bytes_sent)} sent, {formatBytes(node.bytes_received)} received</dd>
            {node.self_bytes > 0
                ? <React.Fragment><dt>Within the Node</dt><dd>{formatBytes(node.self_bytes)} in {sessionsText(node.self_sessions)}</dd></React.Fragment>
                : null}
            <dt>Connected Nodes</dt>
            <dd>{formatCount(node.peers)}</dd>
            {node.first_seen ? <React.Fragment><dt>Seen</dt><dd className="mb-0">{seenText(node.first_seen, node.last_seen)}</dd></React.Fragment> : null}
          </dl>

          <PanelList title={neighbors.length === 1 ? "Connection" : "Connections (" + formatCount(neighbors.length) + ")"}
                     rows={neighbors.slice(0, LIST_LENGTH).map((p) => {
                       const other = model.nodes.get(p.a === node.id ? p.b : p.a);
                       return {
                         key: p.id,
                         label: other.label,
                         detail: other.name || other.detail,
                         value: formatBytes(p.bytes),
                         title: sessionsText(p.sessions),
                         onClick: () => onSelect({kind: "link", id: p.id, a: p.a, b: p.b})
                       };
                     })}
                     empty={model.windowed ? "No traffic in the time window." : null} />
          {neighbors.length > LIST_LENGTH
              ? <div className="text-muted small mb-2">{formatCount(neighbors.length - LIST_LENGTH)} more</div> : null}

          <PanelList title="Services" rows={serviceRows()} empty="No sessions." />
          {moreServices()}

        </React.Fragment>
    );
  }

  const linkPanel = (model) => {
    const pair = model.pairs.find((p) => p.id === selection.id);
    if (!pair) return empty();

    const a = model.nodes.get(pair.a);
    const b = model.nodes.get(pair.b);

    const direction = (link, from, to) => {
      if (!link) return null;
      const fromFilters = nodeSessionFilters(from.data, "source");
      const toFilters = nodeSessionFilters(to.data, "destination");

      return (
          <React.Fragment>
            <dt style={{wordBreak: "break-all"}}>
              {from.label} to {to.label}
              {fromFilters && toFilters
                  ? <PivotLink to={pivot({...fromFilters, ...toFilters})} title="Sessions of this direction" className="ms-2 small" icon={true} />
                  : null}
            </dt>
            <dd>
              {link.active
                  ? <span>{sessionsText(link.sessions)}, {formatBytes(link.bytes_tx)} sent, {formatBytes(link.bytes_rx)} received</span>
                  : <span className="text-muted">No sessions in the time window.</span>}
            </dd>
          </React.Fragment>
      );
    }

    const firstSeen = [pair.forward, pair.backward].filter((l) => l).map((l) => l.first_seen).sort()[0];
    const lastSeen = [pair.forward, pair.backward].filter((l) => l).map((l) => l.last_seen).sort().reverse()[0];
    const nodeLink = (n) => <a href="#" onClick={(e) => { e.preventDefault(); onSelect({kind: "node", id: n.id}); }}>{n.label}</a>;

    return (
        <React.Fragment>
          <h6 className="mb-1" style={{wordBreak: "break-all"}}>{nodeLink(a)} and {nodeLink(b)}</h6>
          <dl className="mb-2">
            <dt>Type</dt>
            <dd>{pair.cls === "BOTH" ? "Outbound and inbound" : LINK_CLASS_TITLES[pair.cls]}</dd>
            {direction(pair.forward, a, b)}
            {direction(pair.backward, b, a)}
            <dt>Transport</dt>
            <dd>{[pair.tcp ? "TCP" : null, pair.udp ? "UDP" : null].filter((x) => x).join(" and ")}</dd>
            <dt>Seen</dt>
            <dd className="mb-0">{seenText(firstSeen, lastSeen)}</dd>
          </dl>

          <PanelList title="Services" rows={serviceRows()} empty="No sessions." />
          {moreServices()}
        </React.Fragment>
    );
  }

  const servicePanel = () => {
    const entry = flowModel ? flowModel.columns[1].find((e) => e.id === selection.id) : null;
    if (!entry) return empty();

    const through = flowModel.paths.filter((p) => p.keys[1] === entry.key);
    const ends = (column) => {
      const totals = new Map();
      through.forEach((p) => totals.set(p.keys[column], (totals.get(p.keys[column]) || 0) + p.value));
      return Array.from(totals.entries()).sort((x, y) => y[1] - x[1]).slice(0, LIST_LENGTH).map(([key, value]) => {
        const end = flowModel.columns[column].find((e) => e.key === key);
        return {
          key: key,
          label: end.label,
          detail: end.name,
          value: formatBytes(value),
          onClick: key === OTHER ? null : () => onSelect({kind: "node", id: key})
        };
      });
    }

    const filters = serviceSessionFilters(entry.service);
    if (filters) pivots = [{label: "Sessions of Service", to: pivot(filters)}];

    return (
        <React.Fragment>
          <h6 className="mb-1">
            <span className="machine-data">{entry.label}</span>
            {entry.name ? <span className="text-muted ms-1">{entry.name}</span> : null}
          </h6>
          <dl className="mb-2">
            <dt>Type</dt>
            <dd>{entry.service.l4_type ? "Destination port" : "Protocol"}</dd>
            <dt>Sessions</dt>
            <dd>{formatCount(entry.sessions)}</dd>
            <dt>Traffic</dt>
            <dd className="mb-0">
              {formatBytes(entry.value)}
              {flowModel.total > 0
                  ? <span className="text-muted"> ({100 * entry.value / flowModel.total < 0.1 ? "under 0.1" : (100 * entry.value / flowModel.total).toFixed(1)}% of all flows)</span>
                  : null}
            </dd>
          </dl>

          <PanelList title="Sources" rows={ends(0)} />
          <PanelList title="Destinations" rows={ends(2)} />
        </React.Fragment>
    );
  }

  const placePanel = () => {
    const place = geoModel ? geoModel.points.find((p) => p.id === selection.id) : null;
    if (!place) return empty();

    const country = countryName(place.country_code);
    const sites = Array.from(place.sites.entries()).sort((x, y) => y[1] - x[1]);

    if (place.country_code) {
      pivots = [{label: "Sessions to Country", to: pivot({destination_address_geo_country_code: [
        {field: "destination_address_geo_country_code", operator: "equals", value: place.country_code}]})}];
    }

    return (
        <React.Fragment>
          <h6 className="mb-3 d-flex align-items-center">
            <Flag code={place.country_code ? place.country_code : "NONE"} />
            <span className="ms-2">{place.precision === "CITY" && place.city ? place.city : (country ? country : "Unknown location")}</span>
          </h6>
          <dl className="mb-2">
            {country && place.precision === "CITY" && place.city
                ? <React.Fragment><dt>Country</dt><dd>{country} ({place.country_code}){filterIcon({field: "address_geo_country_code", operator: "equals", value: place.country_code})}</dd></React.Fragment>
                : null}
            <dt>Placement</dt>
            {place.precision === "CITY"
                ? <dd className="machine-data">{place.lat}, {place.lng}</dd>
                : <dd>Country level</dd>}
            <dt>Direction</dt>
            <dd>{place.direction === "BOTH" ? "Outbound and inbound" : (place.direction === "INBOUND" ? "Inbound" : "Outbound")}</dd>
            <dt>Sessions</dt>
            <dd>{formatCount(place.sessions)}</dd>
            <dt>Traffic</dt>
            <dd>{formatBytes(place.bytes_tx)} sent, {formatBytes(place.bytes_rx)} received</dd>
            <dt>Seen</dt>
            <dd className="mb-0">{seenText(place.first_seen, place.last_seen)}</dd>
          </dl>

          {geoModel.origins.length > 1
              ? <PanelList title="Sites"
                           rows={sites.map(([id, bytes]) => {
                             const site = geoModel.origins.find((o) => o.id === id);
                             return {
                               key: id, label: site.label, value: formatBytes(bytes),
                               onClick: () => onSelect({kind: "site", id: id})
                             };
                           })} />
              : null}

          <PanelList title="Networks"
                     rows={breakdownRows === null ? null : breakdownRows.rows.slice(0, LIST_LENGTH).map((row) => ({
                       key: row.id,
                       label: row.node.asn_name ? row.node.asn_name : (row.node.asn_number ? "AS" + row.node.asn_number : "Unknown network"),
                       value: formatBytes(row.bytes_tx + row.bytes_rx),
                       title: sessionsText(row.sessions)
                     }))}
                     empty="No sessions." />
          {moreServices()}
        </React.Fragment>
    );
  }

  const sitePanel = () => {
    const site = geoModel ? geoModel.origins.find((o) => o.id === selection.id) : null;
    if (!site) return empty();

    const places = Array.from(site.places.entries()).sort((x, y) => y[1] - x[1]);

    if (site.site.location_uuid) {
      pivots = [{label: "Open Location", to: ApiRoutes.LOCATIONS.DETAILS(site.site.location_uuid)}];
    }

    return (
        <React.Fragment>
          <h6 className="mb-1">{site.label}</h6>
          <dl className="mb-2">
            <dt>Type</dt>
            <dd>{site.site.location_uuid && site.id.startsWith("location:") ? "Location" : "Tap with its own coordinates"}</dd>
            <dt>{site.site.taps.length === 1 ? "Tap" : "Taps"}</dt>
            <dd>{site.site.taps.map((t) => t.name).join(", ")}</dd>
            <dt>Coordinates</dt>
            <dd className="machine-data">{site.lat.toFixed(4)}, {site.lng.toFixed(4)}</dd>
            <dt>Sessions</dt>
            <dd>{formatCount(site.sessions)}</dd>
            <dt>Traffic</dt>
            <dd className="mb-0">{formatBytes(site.bytes_tx)} sent, {formatBytes(site.bytes_rx)} received</dd>
          </dl>

          <PanelList title={places.length === 1 ? "Place" : "Places (" + formatCount(places.length) + ")"}
                     rows={places.slice(0, LIST_LENGTH + 4).map(([id, bytes]) => {
                       const place = geoModel.points.find((p) => p.id === id);
                       return {
                         key: id, label: place ? place.label : id, value: formatBytes(bytes),
                         onClick: () => onSelect({kind: "place", id: id})
                       };
                     })}
                     empty="No traffic in the time window." />
          {places.length > LIST_LENGTH + 4
              ? <div className="text-muted small mb-2">{formatCount(places.length - LIST_LENGTH - 4)} more</div> : null}
        </React.Fragment>
    );
  }

  const content = () => {
    if (!selection) return empty();

    switch (selection.kind) {
      case "node":
        return linkModel ? nodePanel(linkModel) : empty();
      case "link":
        return linkModel ? linkPanel(linkModel) : empty();
      case "service":
        return servicePanel();
      case "place":
        return placePanel();
      case "site":
        return sitePanel();
      default:
        return empty();
    }
  }

  const filterItems = () => {
    if (!selection) return [];

    if (selection.kind === "node" && linkModel && linkModel.nodes.has(selection.id)) {
      const node = linkModel.nodes.get(selection.id);
      const filter = nodeFilter(node.data);
      const label = node.data.level === "NETWORK" && node.detail ? node.label + " " + node.detail : node.label;
      return (filter ? [{label: "Show Only This Node", onClick: () => onAddFilter(filter)}] : [])
          .concat([{label: "Exclude This Node", onClick: () => onExclude(node.id, label)}]);
    }

    if (selection.kind === "place" && geoModel) {
      const place = geoModel.points.find((p) => p.id === selection.id);
      if (!place) return [];

      let filter = null;
      if (place.precision === "CITY" && place.city) {
        filter = {field: "address_geo_city", operator: "equals", value: place.city};
      } else if (place.country_code) {
        filter = {field: "address_geo_country_code", operator: "equals", value: place.country_code};
      }
      return (filter ? [{label: "Show Only This Place", onClick: () => onAddFilter(filter)}] : [])
          .concat([{label: "Exclude This Place", onClick: () => onExclude(place.node_id, place.label)}]);
    }

    return [];
  }

  const title = () => {
    if (!selection) return "Selection";
    switch (selection.kind) {
      case "node": return "Selected Node";
      case "link": return "Selected Connection";
      case "service": return "Selected Service";
      case "place": return "Selected Place";
      case "site": return "Selected Site";
      default: return "Selection";
    }
  }

  // Rendered first: the sections name their pivots while they are.
  const body = content();

  return (
      <div className="border rounded d-flex flex-column" style={{height: height + "px"}}>
        <div className="p-3 flex-grow-1 d-flex flex-column" style={{overflowY: "auto", minHeight: 0}}>
          <div className="small text-muted text-uppercase mb-2">{title()}</div>
          <div className="flex-grow-1 d-flex flex-column">
            {body}
          </div>
        </div>
        <VizSelectionActions filters={filterItems()} pivots={pivots} />
      </div>
  )

}
