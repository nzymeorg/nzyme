import {DIMENSION_TITLES} from "../../../ethernet/assets/details/AssetProfileHelpers";

const EVENT_TYPES = [
  {name: "First Seen", value: "ASSET_FIRST_SEEN"},
  {name: "Hostname", value: "HOSTNAME"},
  {name: "IP Address", value: "IP_ADDRESS"},
  {name: "Alert", value: "ALERT"}
];

export const ASSET_CHANGE_TYPE_SELECT_OPTIONS = EVENT_TYPES.concat(
  Object.keys(DIMENSION_TITLES)
    .map((dimension) => ({name: DIMENSION_TITLES[dimension], value: "ATTRIBUTE:" + dimension}))
    .sort((a, b) => a.name.localeCompare(b.name))
);

export const ALLOWED_ASSET_CHANGE_TYPE_VALUES = ASSET_CHANGE_TYPE_SELECT_OPTIONS.map((o) => o.value);
