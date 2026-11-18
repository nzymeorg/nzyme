import numeral from "numeral";
import ApiRoutes from "../../../util/ApiRoutes";
import {filteredLink} from "../assets/details/AssetProfileHelpers";

export const FOLDED_PORT = -1;

// Group IDs of the matrix that are no segment.
const GROUP_UNASSIGNED = 0;
const GROUP_INTERNET = -1;
const GROUP_BROADCAST = -2;

export const OUTCOMES = {
  CONNECTED: {title: "Connected", description: "The target answered."},
  REJECTED: {title: "Rejected", description: "A reset or an ICMP destination unreachable came back."},
  NO_REPLY: {title: "No reply", description: "Nothing came back."},
  SENT: {title: "Sent", description: "Sent to a broadcast or multicast address."}
};

export const FINDING_CLASSES = {
  CROSSED: {
    title: "Crossed",
    description: "A connection that is not allowed was answered, or was sent to a broadcast or multicast address."
  },
  ATTEMPTED: {title: "Attempted", description: "A connection that is not allowed was rejected or got no reply."}
};

export const POLICY_STATES = {
  HOLDING: {title: "Holding", description: "Taps see traffic in scope and all of it was allowed."},
  CROSSED: FINDING_CLASSES.CROSSED,
  ATTEMPTED: FINDING_CLASSES.ATTEMPTED,
  NOT_OBSERVABLE: {
    title: "Not observable",
    description: "No tap recorded traffic of both sources and destinations in scope."
  },
  PAUSED: {title: "Paused", description: "The policy is not evaluated."}
};

export const POLICY_STATUSES = {
  ACTIVE: {title: "Active", description: "Evaluated. Records findings and raises the alerts that are turned on for it."},
  AUDIT: {title: "Audit", description: "Evaluated. Records findings but never raises alerts."},
  PAUSED: {title: "Paused", description: "Not evaluated."}
};

export const RESTAMP_NOTICE = "Applying segment changes to stored flows. Traffic can still show under previous " +
  "segments.";

export const BACKFILL_NOTICE = "Judging stored sessions against new or changed policies. Findings are still being " +
  "added.";

export const POLICY_BACKFILL_NOTICE = "Stored sessions are being judged against this policy. Findings are still " +
  "being added and the state can change.";

export const EXCLUSION_NOTE_MAX_LENGTH = 500;

// A single address is stored as a network that only holds it.
export const exclusionDestination = (exclusion) => exclusion.destination.replace(/\/(32|128)$/, "");

export const EXCLUSION_DESCRIPTION = "Connection attempts from the segment to an excluded destination that are " +
  "rejected or get no reply are not recorded. Connections that the destination answers are recorded as usual.";

export const tapName = (taps, uuid, unknown = "Deleted tap") => {
  const tap = taps ? taps.find((t) => t.uuid === uuid) : null;
  return tap ? tap.name : unknown;
}

// The taps a segment is limited to, or null if it applies to all taps.
export const segmentTapNames = (segment, taps) => {
  const names = (segment.tap_uuids ? segment.tap_uuids : []).map((uuid) => tapName(taps, uuid));

  if (segment.inaccessible_taps > 0) {
    names.push(segment.inaccessible_taps + " not accessible");
  }

  return names.length > 0 ? names : null;
}

export const targetCountText = (finding) => {
  return numeral(finding.target_count).format("0,0") + (finding.target_count > finding.targets_limit ? "+" : "");
}

export const targetCountNote = (finding) => {
  return finding.target_count > finding.targets_limit
    ? "At least this many. Limited to the " + finding.targets_limit + " most recent targets." : undefined;
}

export const SEGMENT_KINDS = {
  INTERNAL: {title: "Segment", description: "A named part of your network."},
  EXTERNAL: {title: "External network", description: "A named group of addresses outside of your network."}
};

export const deleteSegmentQuestion = (segment) => {
  const external = segment.kind === "EXTERNAL";

  return "Really delete the " + (external ? "external network" : "segment") + " \"" + segment.name + "\"? Its " +
    "addresses show up as " + (external ? "Internet" : "Unassigned") + " afterwards.";
}

export const VERDICTS = {
  ALLOWED: {title: "Allowed", description: "An allowed crossing covers this traffic."},
  VIOLATION: {title: "Not allowed", description: "In scope and not covered by an allowed crossing."},
  INDETERMINATE: {
    title: "Cannot be judged",
    description: "External address selectors cannot be evaluated against stored flows."
  }
};

export const SELECTOR_TYPES = {
  ANY: {title: "Anything", hasValue: false},
  INTERNAL: {
    title: "Internal",
    hasValue: false,
    description: "Every address in a segment, and every private address that is in no external network."
  },
  EXTERNAL: {
    title: "Internet",
    hasValue: false,
    description: "Every address in an external network, and every public address that is in no segment."
  },
  UNASSIGNED: {title: "Unassigned", hasValue: false, description: "Internal addresses that are in no segment."},
  BROADCAST: {
    title: "Broadcast or multicast",
    hasValue: false,
    description: "Every broadcast and multicast address. Only as a destination."
  },
  SEGMENT: {title: "Segment", hasValue: true},
  TAG: {title: "Tag", hasValue: true},
  ASSET: {title: "Asset", hasValue: true},
  ADDRESS: {title: "Address or CIDR", hasValue: true},
  ASN: {title: "Autonomous system", hasValue: true},
  COUNTRY: {title: "Country", hasValue: true}
};

const serviceLabel = (l4Type, portFrom, portTo, protocol) => {
  const l4 = l4Type ? l4Type.toLowerCase() : null;
  let label;

  if (portFrom === FOLDED_PORT) {
    label = "many ports" + (l4 ? "/" + l4 : "");
  } else if (portFrom === null || portFrom === undefined) {
    label = l4 ? l4 + " (any port)" : (protocol ? protocol + " (any port)" : "any service");
    return l4 && protocol ? label + " (" + protocol + ")" : label;
  } else {
    const ports = portTo !== null && portTo !== undefined && portTo !== portFrom ? portFrom + "-" + portTo : portFrom;
    label = ports + (l4 ? "/" + l4 : "");
  }

  return protocol ? label + " (" + protocol + ")" : label;
};

export const flowServiceLabel = (flow) => serviceLabel(flow.l4_type, flow.port, null, flow.protocol);

export const ruleServiceLabel = (service) =>
  serviceLabel(service.l4_type, service.port_from, service.port_to, service.protocol);

// The service of a port as alerts and asset profiles write it: "TCP/22".
export const portServiceLabel = (port) => {
  if (!port) {
    return null;
  }

  const parts = port.split("/");
  const number = parts.length === 2 ? parseInt(parts[1], 10) : NaN;

  return isNaN(number) ? port : serviceLabel(parts[0], number, null, null);
};

export const groupSelector = (group) => {
  if (group.id === GROUP_UNASSIGNED) {
    return {type: "UNASSIGNED", value: null};
  }

  if (group.id === GROUP_INTERNET) {
    return {type: "EXTERNAL", value: null};
  }

  if (group.id === GROUP_BROADCAST) {
    return {type: "BROADCAST", value: null};
  }

  return {type: "SEGMENT", value: group.segment_uuid};
};

export const selectorLabel = (selector, options) => {
  if (!selector) {
    return "";
  }

  switch (selector.type) {
    case "SEGMENT": {
      const segment = options && options.segments ? options.segments.find((s) => s.uuid === selector.value) : null;
      return segment ? segment.name : "Deleted segment";
    }
    case "TAG": {
      const tag = options && options.tags ? options.tags.find((t) => t.uuid === selector.value) : null;
      return tag ? tag.name : "Deleted tag";
    }
    case "ASSET":
      return "Asset";
    case "ADDRESS":
      return selector.value;
    case "ASN":
      return "AS" + selector.value;
    case "COUNTRY":
      return selector.value ? selector.value.toUpperCase() : "";
    default:
      return SELECTOR_TYPES[selector.type] ? SELECTOR_TYPES[selector.type].title : selector.type;
  }
};

export const createPolicyLink = (source, destination) => {
  const params = new URLSearchParams();
  if (source) {
    params.set("source_type", source.type);
    if (source.value) {
      params.set("source_value", source.value);
    }
  }

  if (destination) {
    params.set("destination_type", destination.type);
    if (destination.value) {
      params.set("destination_value", destination.value);
    }
  }

  return ApiRoutes.ETHERNET.SEGMENTATION.POLICIES.CREATE + "?" + params.toString();
};

export const policyFindingsLink = (policyName, findingClass = null) => {
  const filters = {policy_name: [{field: "policy_name", operator: "equals", value: policyName}]};

  if (findingClass) {
    filters.finding_class = [
      {field: "finding_class", operator: "segmentation_finding_class_equals", value: findingClass}
    ];
  }

  return filteredLink(ApiRoutes.ETHERNET.SEGMENTATION.FINDINGS.INDEX, filters);
};
