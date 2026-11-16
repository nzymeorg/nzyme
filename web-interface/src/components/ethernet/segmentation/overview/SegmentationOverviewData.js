import {cellStatus, isFinding, sessionsOf, STATUS_ORDER} from "./SegmentationOverviewTheme";

export const SHOW_OPTIONS = [
  ["all", "All crossings"],
  ["exceptions", "Unjudged and findings"],
  ["findings", "Findings only"]
];

export const GROUP_TYPE_TITLES = {
  INTERNAL: "Segment",
  UNASSIGNED: "Internal addresses that are in no segment",
  EXTERNAL: "External network",
  INTERNET: "External addresses that are in no external network",
  BROADCAST: "Broadcast and multicast addresses"
};

const UNKNOWN_TAP = "Unknown tap";

const LANE_EXTERNAL = {key: "external", title: "External", rank: 0, external: true};
const LANE_MULTIPLE = {key: "multiple", title: "Multiple locations", rank: 2, external: false};
const LANE_NO_LOCATION = {key: "none", title: "No location", rank: 3, external: false};
const LANE_BROADCAST = {key: "broadcast", title: "Broadcast and multicast", rank: 4, external: false, broadcast: true};
const LANE_NOT_SEEN = {key: "unseen", title: "No traffic recorded", rank: 5, external: false};

export const cellId = (source, destination) => source + ">" + destination;

export const isExternal = (group) => group.type === "EXTERNAL" || group.type === "INTERNET";

export const isBroadcast = (group) => group.type === "BROADCAST";

export const compareNames = (a, b) => a.localeCompare(b, undefined, {numeric: true, sensitivity: "base"});

export const locationsOf = (taps) => {
  const names = [];
  let without = false;
  taps.forEach((tap) => {
    if (!tap.location_name) {
      without = true;
    } else if (!names.includes(tap.location_name)) {
      names.push(tap.location_name);
    }
  });

  names.sort(compareNames);
  return without ? names.concat(["No location"]) : names;
}

const laneOf = (group, taps) => {
  if (isExternal(group)) return LANE_EXTERNAL;
  if (isBroadcast(group)) return LANE_BROADCAST;
  if (taps.length === 0) return LANE_NOT_SEEN;

  const locations = new Map();
  taps.forEach((tap) => {
    if (tap.location_uuid) locations.set(tap.location_uuid, tap.location_name ? tap.location_name : "Unnamed location");
  });

  if (locations.size === 0) return LANE_NO_LOCATION;
  if (locations.size > 1) return LANE_MULTIPLE;

  const [uuid, name] = locations.entries().next().value;
  return {key: "location:" + uuid, title: name, rank: 1, external: false};
}

export const strongerStatus = (a, b) => {
  if (!a) return b;
  if (!b) return a;
  return STATUS_ORDER.indexOf(a) <= STATUS_ORDER.indexOf(b) ? a : b;
}

export function buildOverviewModel(matrix) {
  const allTaps = new Map((matrix.taps || []).map((tap) => [tap.uuid, tap]));
  const tapsOf = (uuids) => (uuids || []).map((uuid) => (allTaps.has(uuid)
      ? allTaps.get(uuid)
      : {uuid: uuid, name: UNKNOWN_TAP, location_uuid: null, location_name: null}));

  const groups = (matrix.groups || []).map((group, index) => {
    const taps = tapsOf(group.tap_uuids);
    return {
      ...group,
      index: index,
      taps: taps,
      lane: laneOf(group, taps),
      outgoing: [],
      incoming: [],
      inside: null
    };
  });
  const groupsById = new Map(groups.map((group) => [group.id, group]));

  let most = 0;
  const cells = [];
  (matrix.cells || []).forEach((c) => {
    const source = groupsById.get(c.source_group);
    const destination = groupsById.get(c.destination_group);
    if (!source || !destination) return;

    const cell = {
      ...c,
      id: cellId(c.source_group, c.destination_group),
      source: source,
      destination: destination,
      total: sessionsOf(c),
      status: cellStatus(c),
      inside: source === destination,
      taps: tapsOf(c.tap_uuids)
    };

    most = Math.max(most, cell.total);
    cells.push(cell);

    if (cell.inside) {
      source.inside = cell;
    } else {
      source.outgoing.push(cell);
      destination.incoming.push(cell);
    }
  });

  const bySessions = (a, b) => b.total - a.total;
  groups.forEach((group) => {
    group.outgoing.sort(bySessions);
    group.incoming.sort(bySessions);
  });

  return {
    groups: groups,
    groupsById: groupsById,
    cells: cells,
    cellsById: new Map(cells.map((cell) => [cell.id, cell])),
    most: most,
    segments: groups.filter((group) => group.id > 0).length
  };
}

const shows = (cell, show) => {
  switch (show) {
    case "findings": return isFinding(cell.status);
    case "exceptions": return isFinding(cell.status) || cell.coverage !== "FULL";
    default: return true;
  }
}

export function filterOverview(model, show, withExternal, withBroadcast) {
  const groups = model.groups.filter((group) => (withExternal || !isExternal(group)) && (withBroadcast || !isBroadcast(group)));
  const drawn = new Set(groups.map((group) => group.id));

  const between = model.cells.filter((cell) => drawn.has(cell.source.id) && drawn.has(cell.destination.id));
  const cells = between.filter((cell) => shows(cell, show));

  const involved = new Set();
  cells.forEach((cell) => {
    involved.add(cell.source.id);
    involved.add(cell.destination.id);
  });

  return {
    groups: groups,
    involvedGroups: groups.filter((group) => involved.has(group.id)),
    cells: cells,
    cellsById: new Map(cells.map((cell) => [cell.id, cell])),
    hiddenCells: between.length - cells.length,
    filtered: show !== "all"
  };
}

export const crossingTitle = (cell) => (cell.inside
    ? "Inside " + cell.source.name
    : cell.source.name + " to " + cell.destination.name);
