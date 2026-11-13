import {subsystemName} from "../../../briefing/briefingFormat";

export const GRID_COLUMNS = 12;
export const GRID_ROWS = 6;
export const MAXIMUM_WIDGETS = 24;

export const WIDGET_TYPES = {
  STATUS: {title: "Condition", w: 4, h: 2, minimumW: 2, minimumH: 1},
  ALERTS: {title: "Active Alerts", w: 6, h: 4, minimumW: 3, minimumH: 1},
  TAPS: {title: "Taps", w: 6, h: 4, minimumW: 2, minimumH: 1},
  METRIC: {title: "Metric", w: 2, h: 2, minimumW: 2, minimumH: 1},
  CLOCK: {title: "Clock", w: 2, h: 2, minimumW: 2, minimumH: 1},
  LOCATIONS: {title: "Locations", w: 6, h: 3, minimumW: 4, minimumH: 1},
  TEXT: {title: "Text", w: 4, h: 2, minimumW: 2, minimumH: 1},
  TOPOLOGY: {title: "Live Topology", w: 6, h: 4, minimumW: 4, minimumH: 2}
};

export const WINDOWS = [
  {minutes: 60, title: "Last Hour", short: "1H"},
  {minutes: 240, title: "Last 4 Hours", short: "4H"},
  {minutes: 1440, title: "Last 24 Hours", short: "24H"},
  {minutes: 10080, title: "Last 7 Days", short: "7D"}
];

export const TEXT_SIZES = [
  {key: "SMALL", title: "Small"},
  {key: "MEDIUM", title: "Medium"},
  {key: "LARGE", title: "Large"}
];

export const MAXIMUM_TEXT_LENGTH = 2000;

export const TOPOLOGY_CONNECTIONS = [50, 100, 250];

export const ALERT_METRICS = ["ALERTS_ACTIVE", "ALERTS_NEW"];

export const SUBSYSTEMS = ["ETHERNET", "DOT11", "BLUETOOTH"].map((key) => ({key: key, title: subsystemName(key)}));

export const takesSubsystem = (widget) => {
  return widget.type === "ALERTS" || (widget.type === "METRIC" && ALERT_METRICS.includes(widget.options.metric));
}

// "Active Alerts" of a subsystem are "Active WiFi Alerts".
export const alertTitle = (title, widget) => {
  const subsystem = takesSubsystem(widget) ? SUBSYSTEMS.find((s) => s.key === widget.options.subsystem) : null;
  return subsystem ? title.replace("Alerts", subsystem.title + " Alerts") : title;
}

// Shown while a metric has no data. With data, the title comes from the node.
export const METRIC_TITLES = {
  ALERTS_ACTIVE: "Active Alerts",
  ALERTS_NEW: "New Alerts",
  THROUGHPUT: "Throughput",
  ETHERNET_ACTIVE_ASSETS: "Active Assets",
  DOT11_BSSIDS: "BSSIDs",
  DOT11_SSIDS: "SSIDs",
  DOT11_DISCONNECTIONS: "Disconnection Frames",
  BLUETOOTH_DEVICES: "Bluetooth Devices"
};

export const widgetShowsAlerts = (widget) => {
  return widget.type === "STATUS" || widget.type === "ALERTS" || widget.type === "LOCATIONS"
    || (widget.type === "METRIC" && ALERT_METRICS.includes(widget.options.metric));
}

export const overlaps = (a, b) => {
  return a.x < b.x + b.w && b.x < a.x + a.w && a.y < b.y + b.h && b.y < a.y + a.h;
}

export const fits = (candidate, widgets) => {
  return candidate.x >= 0 && candidate.y >= 0 && candidate.w >= 1 && candidate.h >= 1
    && candidate.x + candidate.w <= GRID_COLUMNS && candidate.y + candidate.h <= GRID_ROWS
    && !widgets.some((other) => other.id !== candidate.id && overlaps(candidate, other));
}

export const firstFreePosition = (w, h, widgets) => {
  for (let y = 0; y + h <= GRID_ROWS; y++) {
    for (let x = 0; x + w <= GRID_COLUMNS; x++) {
      if (fits({id: null, x: x, y: y, w: w, h: h}, widgets)) {
        return {x: x, y: y};
      }
    }
  }

  return null;
}

const widget = (id, type, x, y, w, h, options = {}) => ({id: id, type: type, x: x, y: y, w: w, h: h, options: options});

const clocks = () => [
  widget("clock-shanghai", "CLOCK", 0, 0, 2, 1, {time_zone: "Asia/Shanghai"}),
  widget("clock-pyongyang", "CLOCK", 2, 0, 2, 1, {time_zone: "Asia/Pyongyang"}),
  widget("clock-chicago", "CLOCK", 4, 0, 2, 1, {time_zone: "America/Chicago"}),
  widget("clock-berlin", "CLOCK", 6, 0, 2, 1, {time_zone: "Europe/Berlin"}),
  widget("clock-moscow", "CLOCK", 8, 0, 2, 1, {time_zone: "Europe/Moscow"}),
  widget("clock-tehran", "CLOCK", 10, 0, 2, 1, {time_zone: "Asia/Tehran"})
];

// A template with a subsystem is only offered where that subsystem is enabled. The others hold nothing that
// depends on a subsystem, because not everyone runs all of them.
export const TEMPLATES = [
  {
    key: "SOC_OVERVIEW",
    title: "SOC Overview",
    widgets: [
      ...clocks(),
      widget("status", "STATUS", 0, 1, 4, 2),
      widget("alerts-new", "METRIC", 4, 1, 4, 2, {metric: "ALERTS_NEW"}),
      widget("throughput", "METRIC", 8, 1, 4, 2, {metric: "THROUGHPUT"}),
      widget("alerts", "ALERTS", 0, 3, 7, 3),
      widget("taps", "TAPS", 7, 3, 5, 3)
    ]
  },
  {
    key: "ETHERNET",
    title: "Ethernet",
    subsystem: "ETHERNET",
    widgets: [
      widget("assets", "METRIC", 0, 0, 2, 2, {metric: "ETHERNET_ACTIVE_ASSETS"}),
      widget("alerts-new", "METRIC", 2, 0, 3, 2, {metric: "ALERTS_NEW", subsystem: "ETHERNET"}),
      widget("alerts", "ALERTS", 0, 2, 5, 4, {subsystem: "ETHERNET"}),
      widget("topology", "TOPOLOGY", 5, 0, 7, 6, {internal_level: "HOST", external_level: "HOST", connections: 100})
    ]
  },
  {
    key: "WIFI",
    title: "WiFi",
    subsystem: "DOT11",
    widgets: [
      widget("bssids", "METRIC", 0, 0, 3, 2, {metric: "DOT11_BSSIDS"}),
      widget("ssids", "METRIC", 3, 0, 3, 2, {metric: "DOT11_SSIDS"}),
      widget("disconnections", "METRIC", 6, 0, 3, 2, {metric: "DOT11_DISCONNECTIONS"}),
      widget("alerts-new", "METRIC", 9, 0, 3, 2, {metric: "ALERTS_NEW", subsystem: "DOT11"}),
      widget("alerts", "ALERTS", 0, 2, 7, 4, {subsystem: "DOT11"}),
      widget("taps", "TAPS", 7, 2, 5, 4)
    ]
  },
  {
    key: "BLUETOOTH",
    title: "Bluetooth",
    subsystem: "BLUETOOTH",
    widgets: [
      widget("devices", "METRIC", 0, 0, 6, 2, {metric: "BLUETOOTH_DEVICES"}),
      widget("alerts-new", "METRIC", 6, 0, 6, 2, {metric: "ALERTS_NEW", subsystem: "BLUETOOTH"}),
      widget("alerts", "ALERTS", 0, 2, 7, 4, {subsystem: "BLUETOOTH"}),
      widget("taps", "TAPS", 7, 2, 5, 4)
    ]
  },
  {
    key: "SENSOR_HEALTH",
    title: "Sensor Health",
    widgets: [
      widget("taps", "TAPS", 0, 0, 8, 6),
      widget("throughput", "METRIC", 8, 0, 4, 6, {metric: "THROUGHPUT"})
    ]
  },
  {key: "EMPTY", title: "Empty", widgets: []}
];
