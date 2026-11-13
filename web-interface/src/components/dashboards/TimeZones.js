import moment from "moment-timezone";

// Legacy aliases without a region, like EST or ROC, are not known to the node.
export const TIME_ZONES = moment.tz.names().filter((zone) => zone.includes("/") || zone === "UTC");
