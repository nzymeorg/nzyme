import {FILTER_TYPE} from "../../../shared/filtering/Filters";
import {selectorLabel} from "../SegmentationFormat";
import {isUnbounded, servicesText} from "./PolicyDefinition";

// An asset reads as its UUID: its name is only looked up where it is shown.
const selectorsText = (selectors, options, any) => {
  if (isUnbounded(selectors)) {
    return any;
  }

  return selectors
    .map((selector) => selector.type === "ASSET" && selector.value ? "Asset " + selector.value : selectorLabel(selector, options))
    .join(" or ");
}

export const allowedCrossingRows = (allowed, options, unused) => {
  return allowed.map((rule, index) => {
    return {
      index: index,
      rule: rule,
      source: selectorsText(rule.sources, options, "Anything in scope"),
      destination: selectorsText(rule.destinations, options, "anything in scope"),
      service: servicesText(rule.services),
      unused: unused ? unused.includes(index) : null
    };
  });
}

export const POLICY_ALLOWED_CROSSINGS_FILTER_FIELDS = {
  source: { title: "Source", type: FILTER_TYPE.STRING, value: (row) => row.source },
  destination: { title: "Destination", type: FILTER_TYPE.STRING, value: (row) => row.destination },
  service: { title: "Service", type: FILTER_TYPE.STRING, value: (row) => row.service }
}

export const POLICY_ALLOWED_CROSSINGS_WITH_USE_FILTER_FIELDS = {
  ...POLICY_ALLOWED_CROSSINGS_FILTER_FIELDS,
  unused: { title: "Unused", type: FILTER_TYPE.BOOLEAN, value: (row) => row.unused }
}

export const POLICY_ALLOWED_CROSSINGS_SORT_COLUMNS = {
  index: (row) => row.index,
  source: (row) => row.source,
  destination: (row) => row.destination,
  service: (row) => row.service
}
