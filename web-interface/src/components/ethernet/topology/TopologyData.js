import moment from "moment";
import ApiRoutes from "../../../util/ApiRoutes";
import {serializeTimeRange} from "../../shared/timerange/TimeRangeUrl";
import {Absolute} from "../../shared/timerange/TimeRange";
import {countryName, filteredLink, formatBytes, formatCount, UNRESOLVED_EXTERNAL_LABEL} from "../assets/details/AssetProfileHelpers";
import {countryCentroid} from "../../shared/maps/CountryCentroids";

export const INTERNAL_LEVELS = [["HOST", "Addresses"], ["SUBNET", "Networks"], ["TAP", "Taps"]];
export const EXTERNAL_LEVELS = [["NETWORK", "Autonomous systems"], ["COUNTRY", "Countries"], ["HOST", "Addresses"]];
export const SERVICE_LEVELS = [["PROTOCOL", "Protocols"], ["PORT", "Ports"]];

export const LINK_LIMITS = [100, 250, 500, 1000, 5000, 10000];

export function nodeLabel(node) {
  switch (node.level) {
    case "HOST":
      return node.address;
    case "SUBNET":
      return node.network;
    case "TAP":
      return node.tap_name ? node.tap_name : "Unknown tap";
    case "NETWORK":
      if (node.asn_name) return node.asn_name;
      if (node.asn_number) return "AS" + node.asn_number;
      return node.country_code ? "Unknown network in " + countryName(node.country_code) : UNRESOLVED_EXTERNAL_LABEL;
    case "COUNTRY":
      return node.country_code ? countryName(node.country_code) : "Unknown country";
    default:
      return node.id;
  }
}

export function nodeName(node) {
  return node.type === "INTERNAL" && node.asset && node.asset.name ? node.asset.name : null;
}

export function nodeDetail(node) {
  if (node.type === "INTERNAL") {
    return node.hostname ? node.hostname : null;
  }

  if (node.level === "HOST") {
    if (node.asn_name) return node.asn_name;
    return node.asn_number ? "AS" + node.asn_number : null;
  }

  if (node.level === "NETWORK" && node.asn_name && node.asn_number) {
    return "AS" + node.asn_number;
  }

  return null;
}

export function nodeKind(node) {
  if (node.type === "INTERNAL") {
    switch (node.level) {
      case "SUBNET": return "Internal network";
      case "TAP": return "Internal addresses of a tap";
      default: return "Internal address";
    }
  }

  switch (node.level) {
    case "NETWORK": return "External autonomous system";
    case "COUNTRY": return "External country";
    default: return "External address";
  }
}

export function serviceLabel(service) {
  if (service.l4_type) {
    return service.l4_type + "/" + service.port;
  }
  return service.protocol ? service.protocol : service.id;
}

export function serviceName(service) {
  return service.l4_type && service.protocol ? service.protocol : null;
}

export function linkClass(source, target) {
  const sourceInternal = source.type === "INTERNAL";
  const targetInternal = target.type === "INTERNAL";

  if (sourceInternal && targetInternal) return "INTERNAL";
  if (sourceInternal) return "OUTBOUND";
  if (targetInternal) return "INBOUND";
  return "EXTERNAL";
}

export const LINK_CLASS_TITLES = {
  INTERNAL: "Internal",
  OUTBOUND: "Outbound",
  INBOUND: "Inbound",
  EXTERNAL: "External to external"
};

export function linkClassColor(colors, linkClass) {
  switch (linkClass) {
    case "OUTBOUND": return colors.outbound;
    case "INBOUND": return colors.inbound;
    case "BOTH": return colors.both;
    case "INTERNAL": return colors.internalLink;
    default: return colors.muted;
  }
}

export function windowBuckets(seriesConfiguration, timeWindow) {
  if (!timeWindow || !seriesConfiguration) {
    return null;
  }

  const origin = moment(seriesConfiguration.from).valueOf();
  const size = seriesConfiguration.bucket_size_ms;

  // A bucket is inside the window if it starts in it.
  return {
    first: Math.ceil((timeWindow.from - origin) / size),
    last: Math.ceil((timeWindow.to - origin) / size) - 1
  };
}

// A point is [bucket, open sessions, bytes sent, bytes received, new sessions]. A session is open in every bucket it
// spans, so summing up open sessions would count it several times.
export function sumSeries(series, buckets) {
  let sessions = 0, tx = 0, rx = 0;
  for (let i = 0; i < series.length; i++) {
    const point = series[i];
    if (buckets === null || (point[0] >= buckets.first && point[0] <= buckets.last)) {
      sessions += buckets !== null && point[0] === buckets.first ? point[1] : point[4];
      tx += point[2];
      rx += point[3];
    }
  }
  return {sessions: sessions, bytes_tx: tx, bytes_rx: rx};
}

export function applyTimeWindow(items, seriesConfiguration, timeWindow) {
  const buckets = windowBuckets(seriesConfiguration, timeWindow);

  return items.map((item) => {
    const full = item.bytes_tx + item.bytes_rx;
    if (buckets === null) {
      return {...item, full_bytes: full, active: true};
    }

    const sum = sumSeries(item.series, buckets);
    return {...item, ...sum, full_bytes: full, active: sum.sessions > 0};
  });
}

export function nodeFilter(node) {
  switch (node.level) {
    case "HOST":
      return {field: "address", operator: "equals", value: node.address};
    case "SUBNET":
      return {field: "address", operator: "in_cidr", value: node.network};
    case "NETWORK":
      return node.asn_number ? {field: "address_geo_asn_number", operator: "equals_numeric", value: String(node.asn_number)} : null;
    case "COUNTRY":
      return node.country_code ? {field: "address_geo_country_code", operator: "equals", value: node.country_code} : null;
    default:
      return null;
  }
}

export function nodeSessionFilters(node, side) {
  const single = (field, operator, value) => ({[field]: [{field: field, operator: operator, value: value}]});

  switch (node.level) {
    case "HOST":
      return single(side + "_address", "equals", node.address);
    case "SUBNET":
      return single(side + "_address", "in_cidr", node.network);
    case "NETWORK":
      return node.asn_number
          ? single(side + "_address_geo_asn_number", "equals_numeric", String(node.asn_number)) : null;
    case "COUNTRY":
      return node.country_code ? single(side + "_address_geo_country_code", "equals", node.country_code) : null;
    default:
      return null;
  }
}

export function serviceSessionFilters(service) {
  if (service.l4_type) {
    return {
      l4_type: [{field: "l4_type", operator: "equals", value: service.l4_type}],
      destination_port: [{field: "destination_port", operator: "equals_numeric", value: String(service.port)}]
    };
  }

  // A protocol is one or more tags that a session carries all of. The filter takes one.
  const tag = service.protocol ? service.protocol.split("/")[0] : null;
  return tag ? {tags: [{field: "tags", operator: "contains", value: tag}]} : null;
}

// Extra filters win over page filters on the same field.
export function sessionsLink(pageFilters, extraFilters, timeRange, timeWindow) {
  const filters = {};
  Object.keys(pageFilters || {}).forEach((field) => {
    filters[field] = pageFilters[field].map((f) => ({field: f.field, operator: f.operator, value: f.value}));
  });
  Object.assign(filters, extraFilters || {});

  const range = timeWindow ? Absolute(new Date(timeWindow.from), new Date(timeWindow.to)) : timeRange;

  return filteredLink(ApiRoutes.ETHERNET.L4.SESSIONS, filters)
      + "&tr_timerange=" + encodeURIComponent(JSON.stringify(serializeTimeRange(range)));
}

export function formatWindowTime(ms) {
  return moment(ms).format("YYYY-MM-DD HH:mm");
}

const pairClass = (a, b, forward, backward) => {
  const aInternal = a.type === "INTERNAL";
  const bInternal = b.type === "INTERNAL";

  if (aInternal && bInternal) return "INTERNAL";
  if (!aInternal && !bInternal) return "EXTERNAL";

  const internalInitiates = aInternal ? forward : backward;
  const externalInitiates = aInternal ? backward : forward;
  if (internalInitiates && externalInitiates) return "BOTH";
  return internalInitiates ? "OUTBOUND" : "INBOUND";
}

export function buildLinkModel(data, timeWindow) {
  const buckets = windowBuckets(data.series, timeWindow);

  const nodes = new Map();
  data.nodes.forEach((n) => {
    nodes.set(n.id, {
      id: n.id, type: n.type, level: n.level, data: n, label: nodeLabel(n), name: nodeName(n), detail: nodeDetail(n),
      sessions: 0, bytes_sent: 0, bytes_received: 0, bytes: 0, self_bytes: 0, self_sessions: 0, full_bytes: 0,
      full_sessions: 0, neighbors: new Set(), full_neighbors: new Set(), first_seen: null, last_seen: null,
      active: false
    });
  });

  const links = new Map();
  data.links.forEach((l) => {
    if (!nodes.has(l.source) || !nodes.has(l.target)) {
      return;
    }

    const id = l.source + ">" + l.target;
    let link = links.get(id);
    if (!link) {
      link = {
        id: id, source: l.source, target: l.target, cls: linkClass(nodes.get(l.source), nodes.get(l.target)),
        sessions: 0, bytes_tx: 0, bytes_rx: 0, bytes: 0, full_bytes: 0, full_sessions: 0, tcp: false, udp: false,
        first_seen: l.first_seen, last_seen: l.last_seen, active: false
      };
      links.set(id, link);
    }

    const sum = sumSeries(l.series, buckets);
    link.sessions += sum.sessions;
    link.bytes_tx += sum.bytes_tx;
    link.bytes_rx += sum.bytes_rx;
    link.bytes += sum.bytes_tx + sum.bytes_rx;
    link.full_bytes += l.bytes_tx + l.bytes_rx;
    link.full_sessions += l.sessions;
    if (l.sessions_tcp > 0) link.tcp = true;
    if (l.sessions_udp > 0) link.udp = true;
    if (l.first_seen < link.first_seen) link.first_seen = l.first_seen;
    if (l.last_seen > link.last_seen) link.last_seen = l.last_seen;
    if (sum.sessions > 0) link.active = true;
  });

  const pairs = new Map();
  const directed = [];

  links.forEach((link) => {
    const source = nodes.get(link.source);
    const target = nodes.get(link.target);

    const seen = (node) => {
      if (!node.first_seen || link.first_seen < node.first_seen) node.first_seen = link.first_seen;
      if (!node.last_seen || link.last_seen > node.last_seen) node.last_seen = link.last_seen;
    }

    source.full_bytes += link.full_bytes;
    source.full_sessions += link.full_sessions;
    source.sessions += link.sessions;
    source.bytes_sent += link.bytes_tx;
    source.bytes_received += link.bytes_rx;
    if (link.active) source.active = true;
    seen(source);

    // Traffic between two endpoints of the same node, like two addresses of one network.
    if (source === target) {
      source.self_bytes += link.bytes;
      source.self_sessions += link.sessions;
      return;
    }

    target.full_bytes += link.full_bytes;
    target.full_sessions += link.full_sessions;
    source.full_neighbors.add(target.id);
    target.full_neighbors.add(source.id);
    target.sessions += link.sessions;
    target.bytes_sent += link.bytes_rx;
    target.bytes_received += link.bytes_tx;
    seen(target);
    if (link.active) {
      target.active = true;
      source.neighbors.add(target.id);
      target.neighbors.add(source.id);
    }

    directed.push(link);

    const forward = link.source < link.target;
    const a = forward ? link.source : link.target;
    const b = forward ? link.target : link.source;
    const pairId = a + "|" + b;
    let pair = pairs.get(pairId);
    if (!pair) {
      pair = {
        id: pairId, a: a, b: b, forward: null, backward: null, sessions: 0, bytes: 0, full_bytes: 0,
        tcp: false, udp: false, active: false
      };
      pairs.set(pairId, pair);
    }
    if (forward) pair.forward = link; else pair.backward = link;
    pair.sessions += link.sessions;
    pair.bytes += link.bytes;
    pair.full_bytes += link.full_bytes;
    if (link.tcp) pair.tcp = true;
    if (link.udp) pair.udp = true;
    if (link.active) pair.active = true;
  });

  nodes.forEach((n) => {
    n.bytes = n.bytes_sent + n.bytes_received;
    n.peers = n.neighbors.size;
    n.full_peers = n.full_neighbors.size;
  });

  pairs.forEach((p) => {
    const a = nodes.get(p.a);
    const b = nodes.get(p.b);
    // The class follows the directions that are active in the window, or all of them if none is.
    const forward = p.forward && (p.forward.active || !p.active);
    const backward = p.backward && (p.backward.active || !p.active);
    p.cls = pairClass(a, b, Boolean(forward), Boolean(backward));
  });

  return {
    nodes: nodes,
    links: directed,
    pairs: Array.from(pairs.values()),
    windowed: buckets !== null
  };
}

export function pairIdOf(a, b) {
  return a < b ? a + "|" + b : b + "|" + a;
}

// The result depends on the width of the window only, not on its position, so it can be kept while the window moves.
export function buildNodeScale(data, width) {
  const count = data.series ? data.series.count : 0;
  if (count <= 0) {
    return {bytes: 0, sessions: 0, peers: 0};
  }

  const w = Math.max(1, Math.min(count, width === null ? count : width));
  const positions = count - w + 1;
  const known = new Set(data.nodes.map((n) => n.id));

  const nodes = new Map();
  const nodeOf = (id) => {
    let node = nodes.get(id);
    if (!node) {
      node = {
        bytes: new Float64Array(count), sessions: new Float64Array(count), newSessions: new Float64Array(count),
        peers: new Int32Array(positions)
      };
      nodes.set(id, node);
    }
    return node;
  }

  const pairs = new Map();

  data.links.forEach((l) => {
    if (!known.has(l.source) || !known.has(l.target)) {
      return;
    }

    const source = nodeOf(l.source);
    const target = l.source === l.target ? null : nodeOf(l.target);

    let pair = null;
    if (target) {
      const pairId = pairIdOf(l.source, l.target);
      pair = pairs.get(pairId);
      if (!pair) {
        pair = {a: source, b: target, sessions: new Float64Array(count)};
        pairs.set(pairId, pair);
      }
    }

    l.series.forEach((point) => {
      const i = point[0];
      if (i < 0 || i >= count) return;

      source.sessions[i] += point[1];
      source.newSessions[i] += point[4];
      source.bytes[i] += point[2] + point[3];
      if (target) {
        target.sessions[i] += point[1];
        target.newSessions[i] += point[4];
        target.bytes[i] += point[2] + point[3];
        pair.sessions[i] += point[1];
      }
    });
  });

  // Like sumSeries: the first bucket of a window counts in full, the others add what is new in them.
  const slide = (values, added, callback) => {
    let sum = 0;
    for (let i = 0; i < count; i++) {
      sum += added[i];
      if (i >= w - 1) {
        const first = i - w + 1;
        sum -= added[first];
        callback(values[first] + sum, first);
      }
    }
  }

  pairs.forEach((pair) => {
    slide(pair.sessions, pair.sessions, (sessions, position) => {
      if (sessions > 0) {
        pair.a.peers[position]++;
        pair.b.peers[position]++;
      }
    });
  });

  const scale = {bytes: 0, sessions: 0, peers: 0};
  nodes.forEach((node) => {
    slide(node.bytes, node.bytes, (bytes) => { if (bytes > scale.bytes) scale.bytes = bytes; });
    slide(node.sessions, node.newSessions, (sessions) => { if (sessions > scale.sessions) scale.sessions = sessions; });
    for (let position = 0; position < positions; position++) {
      if (node.peers[position] > scale.peers) scale.peers = node.peers[position];
    }
  });

  return scale;
}

export function windowWidth(seriesConfiguration, timeWindow) {
  const buckets = windowBuckets(seriesConfiguration, timeWindow);
  return buckets === null ? null : Math.max(1, buckets.last - buckets.first + 1);
}

export function describeNodeId(id) {
  const separator = id.indexOf(":");
  const kind = id.substring(0, separator);
  const value = id.substring(separator + 1);

  switch (kind) {
    case "host":
    case "ext":
    case "subnet":
      return value;
    case "tap":
      return "Tap " + value.substring(0, 8);
    case "asn":
      return "AS" + value;
    case "noasn":
      return value === "-" ? UNRESOLVED_EXTERNAL_LABEL : "Unknown network in " + countryName(value);
    case "cc":
      return value === "-" ? "Unknown country" : countryName(value);
    case "loc":
      return "Coordinates " + value.replace(",", ", ");
    default:
      return id;
  }
}

const directionOf = (x) => (x.inbound && x.outbound ? "BOTH" : (x.inbound ? "INBOUND" : "OUTBOUND"));

export function buildGeoModel(geo, timeWindow, grouping) {
  const byCountry = grouping === "country";
  const flows = applyTimeWindow(geo.flows, geo.series, timeWindow);

  const sites = new Map();
  geo.sites.forEach((site) => {
    sites.set(site.id, {
      id: site.id, lat: site.latitude, lng: site.longitude, label: site.name, site: site,
      sessions: 0, bytes_tx: 0, bytes_rx: 0, places: new Map()
    });
  });

  const places = new Map();
  const arcs = new Map();
  const unlocated = {sessions: 0, bytes: 0, flows: 0};
  const unplaced = {sessions: 0, bytes: 0};

  flows.forEach((f) => {
    if (!f.active) {
      return;
    }

    const bytes = f.bytes_tx + f.bytes_rx;
    const hasCoordinates = typeof f.latitude === "number" && typeof f.longitude === "number";
    const centroid = countryCentroid(f.country_code);

    let id, nodeId, lat, lng, precision;
    // A place without a country keeps its own coordinates even when grouping by country.
    if (hasCoordinates && (!byCountry || !centroid)) {
      lat = f.latitude;
      lng = f.longitude;
      id = "loc:" + lat + "," + lng;
      nodeId = id;
      precision = "CITY";
    } else if (centroid) {
      lat = centroid.lat;
      lng = centroid.lng;
      id = "country:" + f.country_code;
      nodeId = "cc:" + f.country_code;
      precision = "COUNTRY";
    } else {
      unlocated.sessions += f.sessions;
      unlocated.bytes += bytes;
      unlocated.flows += 1;
      return;
    }

    let place = places.get(id);
    if (!place) {
      place = {
        id: id, node_id: nodeId, lat: lat, lng: lng, precision: precision, city: f.city,
        country_code: f.country_code, sessions: 0, bytes: 0, bytes_tx: 0, bytes_rx: 0, inbound: false,
        outbound: false, first_seen: f.first_seen, last_seen: f.last_seen, sites: new Map()
      };
      places.set(id, place);
    }

    place.sessions += f.sessions;
    place.bytes += bytes;
    place.bytes_tx += f.bytes_tx;
    place.bytes_rx += f.bytes_rx;
    if (f.direction === "INBOUND") place.inbound = true; else place.outbound = true;
    if (precision === "CITY" && !place.city && f.city) place.city = f.city;
    if (f.first_seen < place.first_seen) place.first_seen = f.first_seen;
    if (f.last_seen > place.last_seen) place.last_seen = f.last_seen;

    const site = f.site ? sites.get(f.site) : null;
    if (!site) {
      // Recorded by a tap without a known position: the place shows, a flow to it cannot.
      unplaced.sessions += f.sessions;
      unplaced.bytes += bytes;
      return;
    }

    site.sessions += f.sessions;
    site.bytes_tx += f.bytes_tx;
    site.bytes_rx += f.bytes_rx;
    site.places.set(id, (site.places.get(id) || 0) + bytes);
    place.sites.set(site.id, (place.sites.get(site.id) || 0) + bytes);

    const arcId = site.id + "|" + id;
    let arc = arcs.get(arcId);
    if (!arc) {
      arc = {originId: site.id, pointId: id, bytes: 0, sessions: 0, weight: 0, inbound: false, outbound: false};
      arcs.set(arcId, arc);
    }
    arc.bytes += bytes;
    arc.sessions += f.sessions;
    arc.weight += f.full_bytes;
    if (f.direction === "INBOUND") arc.inbound = true; else arc.outbound = true;
  });

  const points = Array.from(places.values()).map((p) => {
    const country = countryName(p.country_code);
    const siteNames = Array.from(p.sites.keys()).map((id) => sites.get(id).label);
    return {
      ...p,
      label: p.precision === "CITY" && p.city ? p.city + (country ? ", " + country : "") : (country ? country : "Unknown location"),
      detail: sites.size > 1 && siteNames.length > 0
          ? "From " + siteNames.slice(0, 3).join(", ") + (siteNames.length > 3 ? " and " + (siteNames.length - 3) + " more" : "")
          : null,
      direction: directionOf(p)
    };
  });

  const origins = Array.from(sites.values()).map((s) => {
    const taps = s.site.taps.map((t) => t.name);
    return {
      ...s,
      detail: (taps.length === 1 ? "Tap " : "Taps ") + taps.slice(0, 3).join(", ") + (taps.length > 3 ? " and " + (taps.length - 3) + " more" : ""),
      note: formatCount(s.sessions) + (s.sessions === 1 ? " session, " : " sessions, ") + formatBytes(s.bytes_tx + s.bytes_rx)
    };
  });

  return {
    origins: origins,
    points: points,
    flows: Array.from(arcs.values()).map((a) => ({...a, direction: directionOf(a)})),
    unlocated: unlocated,
    unplaced: unplaced,
    unplacedTaps: geo.unplaced_taps,
    external: flows.filter((f) => f.active).length
  };
}

export function buildBreakdown(data, timeWindow, by) {
  const nodes = new Map(data.nodes.map((n) => [n.id, n]));
  const services = new Map((data.services || []).map((v) => [v.id, v]));
  const entries = new Map();

  applyTimeWindow(data.links, data.series, timeWindow).forEach((l) => {
    if (!l.active) return;

    const ids = by === "service"
        ? [l.service].filter((id) => services.has(id))
        : Array.from(new Set([l.source, l.target])).filter((id) => nodes.has(id) && nodes.get(id).type === "EXTERNAL");

    ids.forEach((id) => {
      let entry = entries.get(id);
      if (!entry) {
        entry = {id: id, service: services.get(id), node: nodes.get(id), sessions: 0, bytes_tx: 0, bytes_rx: 0};
        entries.set(id, entry);
      }
      entry.sessions += l.sessions;
      entry.bytes_tx += l.bytes_tx;
      entry.bytes_rx += l.bytes_rx;
    });
  });

  return {
    rows: Array.from(entries.values())
        .sort((a, b) => (b.bytes_tx + b.bytes_rx) - (a.bytes_tx + a.bytes_rx) || (a.id < b.id ? -1 : 1)),
    truncated: data.total_links > data.links.length
  };
}

export const OTHER = "__other";

export function buildFlowModel(data, timeWindow, perColumn) {
  const nodes = new Map(data.nodes.map((n) => [n.id, n]));
  const services = new Map(data.services.map((v) => [v.id, v]));
  const links = applyTimeWindow(data.links, data.series, timeWindow)
      .filter((l) => l.active && nodes.has(l.source) && nodes.has(l.target) && services.has(l.service));

  const totals = [new Map(), new Map(), new Map()];
  links.forEach((l) => {
    const bytes = l.bytes_tx + l.bytes_rx;
    totals[0].set(l.source, (totals[0].get(l.source) || 0) + bytes);
    totals[1].set(l.service, (totals[1].get(l.service) || 0) + bytes);
    totals[2].set(l.target, (totals[2].get(l.target) || 0) + bytes);
  });

  const kept = totals.map((column) => new Set(
      Array.from(column.entries()).sort((a, b) => b[1] - a[1]).slice(0, perColumn).map((e) => e[0])
  ));
  const keyOf = (column, id) => (kept[column].has(id) ? id : OTHER);

  const columns = [new Map(), new Map(), new Map()];
  const entry = (column, key) => {
    let e = columns[column].get(key);
    if (!e) {
      e = {column: column, key: key, id: key === OTHER ? null : key, other: key === OTHER, value: 0, sessions: 0, folded: new Set()};
      if (key === OTHER) {
        e.label = "Other";
      } else if (column === 1) {
        const service = services.get(key);
        e.service = service;
        e.label = serviceLabel(service);
        e.name = serviceName(service);
      } else {
        const node = nodes.get(key);
        e.node = node;
        e.label = nodeLabel(node);
        e.name = nodeName(node) || nodeDetail(node);
      }
      columns[column].set(key, e);
    }
    return e;
  }

  const bands = [new Map(), new Map()];
  const paths = new Map();

  links.forEach((l) => {
    const bytes = l.bytes_tx + l.bytes_rx;
    const keys = [keyOf(0, l.source), keyOf(1, l.service), keyOf(2, l.target)];
    const cls = linkClass(nodes.get(l.source), nodes.get(l.target));

    [l.source, l.service, l.target].forEach((id, column) => {
      const e = entry(column, keys[column]);
      e.value += bytes;
      e.sessions += l.sessions;
      if (e.other) e.folded.add(id);
    });

    const pathId = keys.join("|");
    let path = paths.get(pathId);
    if (!path) {
      path = {keys: keys, value: 0, sessions: 0};
      paths.set(pathId, path);
    }
    path.value += bytes;
    path.sessions += l.sessions;

    [0, 1].forEach((side) => {
      const bandId = keys[side] + "|" + keys[side + 1];
      let band = bands[side].get(bandId);
      if (!band) {
        band = {id: side + ":" + bandId, side: side, from: keys[side], to: keys[side + 1], value: 0, sessions: 0, classes: {}};
        bands[side].set(bandId, band);
      }
      band.value += bytes;
      band.sessions += l.sessions;
      band.classes[cls] = (band.classes[cls] || 0) + bytes;
    });
  });

  bands.forEach((side) => side.forEach((band) => {
    band.cls = Object.keys(band.classes).sort((a, b) => band.classes[b] - band.classes[a])[0];
  }));

  const order = (column) => Array.from(column.values())
      .sort((a, b) => (a.other ? 1 : 0) - (b.other ? 1 : 0) || b.value - a.value);

  return {
    columns: columns.map(order),
    bands: bands.map((side) => Array.from(side.values())),
    paths: Array.from(paths.values()),
    total: links.reduce((sum, l) => sum + l.bytes_tx + l.bytes_rx, 0)
  };
}
