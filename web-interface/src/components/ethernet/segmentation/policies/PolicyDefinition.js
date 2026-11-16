import {FOLDED_PORT, SELECTOR_TYPES, ruleServiceLabel} from "../SegmentationFormat";
import {asnProblem, networkProblem} from "../NetworkProblems";

// A rule has the shape {sources: [selector], destinations: [selector], services: [service]}. An empty list means any.

export const emptyRule = () => {
  return {sources: [], destinations: [], services: []};
}

export const normalizeRule = (rule) => {
  return {
    sources: rule && rule.sources ? rule.sources : [],
    destinations: rule && rule.destinations ? rule.destinations : [],
    services: rule && rule.services ? rule.services : []
  };
}

export const isUnbounded = (selectors) => {
  return !selectors || selectors.length === 0 || selectors.some((s) => s.type === "ANY");
}

export const scopeIsValid = (scope) => {
  return !!scope && !(isUnbounded(scope.sources) && isUnbounded(scope.destinations));
}

export const ruleIsEmpty = (rule) => {
  return rule.sources.length === 0 && rule.destinations.length === 0 && rule.services.length === 0;
}

// The node takes this one form, and refuses a crossing that allows everything by naming nothing.
export const everythingInScope = () => {
  return {sources: [{type: "ANY", value: null}], destinations: [{type: "ANY", value: null}], services: []};
}

export const EVERYTHING_IN_SCOPE_NOTE = "Every connection in scope is allowed. The policy reports no findings.";

const isAnything = (selectors) => {
  return !!selectors && selectors.length === 1 && selectors[0].type === "ANY";
}

export const isEverythingInScope = (rule) => {
  return isAnything(rule.sources) && isAnything(rule.destinations) && (!rule.services || rule.services.length === 0);
}

export const namesNothing = (rule) => {
  return !isEverythingInScope(rule) && isUnbounded(rule.sources) && isUnbounded(rule.destinations)
    && (!rule.services || rule.services.length === 0);
}

export const sameSelector = (a, b) => {
  return a.type === b.type && (a.value ? String(a.value) : "") === (b.value ? String(b.value) : "");
}

const blankToNull = (v) => v === undefined || v === "" ? null : v;

export const sameService = (a, b) => {
  return blankToNull(a.l4_type) === blankToNull(b.l4_type)
    && blankToNull(a.port_from) === blankToNull(b.port_from)
    && blankToNull(a.port_to) === blankToNull(b.port_to)
    && blankToNull(a.protocol) === blankToNull(b.protocol);
}

export const sameRule = (a, b) => {
  const sameList = (x, y, same) => x.length === y.length && x.every((item, i) => same(item, y[i]));

  return sameList(a.sources, b.sources, sameSelector)
    && sameList(a.destinations, b.destinations, sameSelector)
    && sameList(a.services, b.services, sameService);
}

export const draftProblem = (draft) => {
  switch (draft ? draft.type : null) {
    case "ADDRESS":
      return networkProblem(draft.value, "10.1.0.0/16", true);
    case "ASN":
      return asnProblem(draft.value);
    default:
      return null;
  }
}

export const selectorOfDraft = (draft) => {
  if (!draft || !SELECTOR_TYPES[draft.type]) {
    return null;
  }

  if (!SELECTOR_TYPES[draft.type].hasValue) {
    return {type: draft.type, value: null};
  }

  const value = draft.value ? String(draft.value).trim() : "";
  if (value === "") {
    return null;
  }

  switch (draft.type) {
    case "ADDRESS":
      return draftProblem(draft) ? null : {type: "ADDRESS", value: value};
    case "ASN":
      return draftProblem(draft) ? null : {type: "ASN", value: value};
    case "COUNTRY":
      return /^[A-Za-z]{2}$/.test(value) ? {type: "COUNTRY", value: value.toUpperCase()} : null;
    default:
      return {type: draft.type, value: value};
  }
}

export const serviceOfDraft = (draft) => {
  const l4Type = draft.l4_type === "TCP" || draft.l4_type === "UDP" ? draft.l4_type : null;
  const protocol = draft.protocol ? draft.protocol : null;
  const ports = draft.ports ? draft.ports.trim() : "";

  let portFrom = null;
  let portTo = null;

  if (ports !== "") {
    const match = /^([0-9]{1,5})(?:\s*-\s*([0-9]{1,5}))?$/.exec(ports);
    if (!match) {
      return null;
    }

    portFrom = parseInt(match[1], 10);
    portTo = match[2] === undefined ? null : parseInt(match[2], 10);

    if (portFrom > 65535 || (portTo !== null && (portTo > 65535 || portTo < portFrom))) {
      return null;
    }

    if (portTo === portFrom) {
      portTo = null;
    }
  }

  if (l4Type === null && portFrom === null && protocol === null) {
    return null;
  }

  return {l4_type: l4Type, port_from: portFrom, port_to: portTo, protocol: protocol};
}

export const allowedCrossingOfPreviewRow = (row) => {
  return {
    sources: [row.source.selector],
    destinations: [row.destination.selector],
    services: [row.port === FOLDED_PORT ? {l4_type: row.l4_type}
      : {l4_type: row.l4_type, port_from: row.port, port_to: null, protocol: null}]
  };
}

export const servicesText = (services) => {
  if (!services || services.length === 0) {
    return "any service";
  }

  return services.map(ruleServiceLabel).join(" or ");
}

export const alertSettingLabel = (policy) => {
  if (policy.alert_on_connected && policy.alert_on_attempted) {
    return "Connections and attempts";
  }

  if (policy.alert_on_connected) {
    return "Connections";
  }

  if (policy.alert_on_attempted) {
    return "Attempts";
  }

  return "No alerts";
}

export const alertSettingDescription = (policy) => {
  if (policy.alert_on_connected && policy.alert_on_attempted) {
    return "Alerts on accepted connections and on rejected or timed out connections.";
  }

  if (policy.alert_on_connected) {
    return "Alerts on accepted connections.";
  }

  if (policy.alert_on_attempted) {
    return "Alerts on rejected or timed out connections.";
  }

  return "Raises no alerts.";
}

export const policyRequest = (policy, changes = {}) => {
  return {
    name: policy.name,
    description: policy.description,
    status: policy.status,
    alert_on_connected: policy.alert_on_connected,
    alert_on_attempted: policy.alert_on_attempted,
    scope: policy.scope,
    allowed: policy.allowed,
    ...changes
  };
}
