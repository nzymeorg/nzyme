import {sessionsLink} from "../topology/TopologyData";
import {FOLDED_PORT} from "./SegmentationFormat";

/*
 * A link is only offered where the filters of the sessions page can say exactly what was clicked. Filters on the
 * same field are alternatives, filters on different fields all have to match.
 */

const filter = (field, operator, value) => ({field: field, operator: operator, value: String(value)});

const BROADCAST_RANGES = [
  filter("destination_address", "in_cidr", "224.0.0.0/4"),
  filter("destination_address", "in_cidr", "ff00::/8"),
  filter("destination_address", "equals", "255.255.255.255")
];

const inBroadcastRanges = (address) => {
  if (address.includes(":")) {
    return address.toLowerCase().startsWith("ff");
  }

  const first = parseInt(address.split(".")[0], 10);
  return address === "255.255.255.255" || (first >= 224 && first <= 239);
}

const serviceFilters = (l4Type, port) => {
  const filters = {};
  if (l4Type) {
    filters.l4_type = [filter("l4_type", "equals", l4Type)];
  }
  if (port !== null && port !== undefined && port !== FOLDED_PORT) {
    filters.destination_port = [filter("destination_port", "equals_numeric", port)];
  }

  return filters;
}

const addressFilters = (side, address) => ({[side + "_address"]: [filter(side + "_address", "equals", address)]});

// The folded destinations of a sweep stand for many addresses and cannot be filtered for.
const flowSideFilters = (flowSide, side) => {
  if (flowSide.address && flowSide.address.address) {
    return addressFilters(side, flowSide.address.address);
  }

  if (flowSide.internal || flowSide.broadcast) {
    return null;
  }

  const filters = {};
  if (flowSide.asn !== null && flowSide.asn !== undefined) {
    const field = side + "_address_geo_asn_number";
    filters[field] = [filter(field, "equals_numeric", flowSide.asn)];
  }
  if (flowSide.country_code) {
    const field = side + "_address_geo_country_code";
    filters[field] = [filter(field, "equals", flowSide.country_code)];
  }

  return Object.keys(filters).length > 0 ? filters : null;
}

export const flowSessionsLink = (flow, timeRange) => {
  const source = flowSideFilters(flow.source, "source");
  const destination = flowSideFilters(flow.destination, "destination");
  if (!source || !destination) {
    return null;
  }

  return sessionsLink(null, {...source, ...destination, ...serviceFilters(flow.l4_type, flow.port)}, timeRange, null);
}

export const addressSessionsLink = (address, side, timeRange) => sessionsLink(null, addressFilters(side, address),
    timeRange, null);

// What is left and right of the time a finding names, so that sessions that started just before it are found.
const TARGET_WINDOW_MARGIN_MS = 5 * 60 * 1000;

export const targetSessionsLink = (sourceAddress, target) => {
  if (!sourceAddress || !target.address_text) {
    return null;
  }

  const window = {
    from: new Date(target.first_seen).getTime() - TARGET_WINDOW_MARGIN_MS,
    to: new Date(target.last_seen).getTime() + TARGET_WINDOW_MARGIN_MS
  };

  return sessionsLink(null, {
    ...addressFilters("source", sourceAddress),
    ...addressFilters("destination", target.address_text),
    ...serviceFilters(target.l4_type, target.port)
  }, null, window);
}

const previewSideFilters = (previewSide, hosts, sample, side) => {
  if (hosts > 0 && sample && hosts === sample.length) {
    const field = side + "_address";
    return {[field]: sample.map((address) => filter(field, "equals", address))};
  }

  const selector = previewSide.selector;
  if (selector && selector.type === "ASN" && selector.value) {
    const field = side + "_address_geo_asn_number";
    return {[field]: [filter(field, "equals_numeric", selector.value)]};
  }

  if (selector && selector.type === "COUNTRY" && selector.value) {
    const field = side + "_address_geo_country_code";
    return {[field]: [filter(field, "equals", selector.value)]};
  }

  return null;
}

export const previewRowSessionsLink = (row, timeRange) => {
  const source = previewSideFilters(row.source, row.source_hosts, row.source_sample, "source");
  const destination = previewSideFilters(row.destination, row.destination_hosts, row.destination_sample, "destination");
  if (!source || !destination) {
    return null;
  }

  return sessionsLink(null, {...source, ...destination, ...serviceFilters(row.l4_type, row.port)}, timeRange, null);
}

/*
 * Everything that is in no segment cannot be filtered for, because the filters cannot say "in none of these
 * networks". The same goes for a segment that has both networks and autonomous systems, which are different fields.
 */
const groupFilters = (group, side, broadcastAddresses = []) => {
  if (group.type === "BROADCAST") {
    if (side !== "destination") {
      return null;
    }

    const seen = broadcastAddresses.filter((address) => !inBroadcastRanges(address))
        .map((address) => filter("destination_address", "equals", address));
    return {destination_address: BROADCAST_RANGES.concat(seen)};
  }

  const networks = group.networks || [];
  const asns = group.asns || [];

  if (networks.length > 0 && asns.length === 0) {
    const field = side + "_address";
    return {[field]: networks.map((network) => filter(field, "in_cidr", network))};
  }

  if (asns.length > 0 && networks.length === 0) {
    const field = side + "_address_geo_asn_number";
    return {[field]: asns.map((asn) => filter(field, "equals_numeric", asn))};
  }

  return null;
}

// A segment that lies inside of the networks of another one is part of the sessions of the larger one here.
export const crossingSessionsLink = (cell, timeRange, broadcastAddresses, service = null) => {
  const source = groupFilters(cell.source, "source", broadcastAddresses);
  const destination = groupFilters(cell.destination, "destination", broadcastAddresses);
  if (!source || !destination) {
    return null;
  }

  return sessionsLink(null, {
    ...source,
    ...destination,
    ...(service ? serviceFilters(service.l4_type, service.port) : {})
  }, timeRange, null);
}

export const groupSessionsLink = (group, side, timeRange, broadcastAddresses) => {
  const filters = groupFilters(group, side, broadcastAddresses);
  return filters ? sessionsLink(null, filters, timeRange, null) : null;
}

export const broadcastAddressesOf = (flows) => {
  const addresses = new Set();
  (flows || []).forEach((flow) => {
    if (flow.destination.broadcast && flow.destination.address && flow.destination.address.address) {
      addresses.add(flow.destination.address.address);
    }
  });

  return Array.from(addresses).sort();
}
