const wave = (base, swing, length = 144) => {
  return Array.from({length: length}, (_, i) => {
    const t = i*24/length;

    return {
      bucket: null,
      value: Math.max(0, Math.round(base + swing*Math.sin(t/3) + swing*0.4*Math.cos(t*1.7) + swing*0.15*Math.sin(i*2.3)))
    };
  });
}

const METRIC_SAMPLES = {
  ALERTS_ACTIVE: {aggregation: "LATEST", current: 2, previous: null, samples: []},
  ALERTS_NEW: {aggregation: "SUM", current: 7, previous: 5, samples: wave(1, 1, 72)},
  THROUGHPUT: {aggregation: "AVERAGE", current: 184000000, previous: 171000000, unit: "BITS_PER_SECOND",
    samples: wave(180000000, 40000000)},
  ETHERNET_ACTIVE_ASSETS: {aggregation: "DISTINCT", current: 412, previous: 398, samples: wave(380, 30, 24)},
  DOT11_BSSIDS: {aggregation: "PEAK", current: 219, previous: 208, samples: wave(200, 15)},
  DOT11_SSIDS: {aggregation: "PEAK", current: 103, previous: 101, samples: wave(98, 5)},
  DOT11_DISCONNECTIONS: {aggregation: "SUM", current: 181, previous: 214, samples: wave(8, 6, 72)},
  BLUETOOTH_DEVICES: {aggregation: "PEAK", current: 256, previous: 183, samples: wave(190, 50)}
};

const sampleNode = (id, type, address, extra = {}) => ({id: id, type: type, level: "HOST", address: address, ...extra});

const sampleLink = (source, target, bytes, tcp = true) => ({
  source: source, target: target, service: null, sessions: 3, sessions_tcp: tcp ? 3 : 0, sessions_udp: tcp ? 0 : 3,
  bytes_tx: Math.round(bytes*0.2), bytes_rx: Math.round(bytes*0.8), first_seen: "2026-01-01T12:00:00.000Z",
  last_seen: "2026-01-01T12:00:30.000Z", series: [[0, 3, Math.round(bytes*0.2), Math.round(bytes*0.8), 3]]
});

// In the shape of the links of the topology API.
export const SAMPLE_TOPOLOGY = {
  nodes: [
    sampleNode("i1", "INTERNAL", "10.1.0.10", {hostname: "fileserver"}),
    sampleNode("i2", "INTERNAL", "10.1.0.21"),
    sampleNode("i3", "INTERNAL", "10.1.0.22"),
    sampleNode("i4", "INTERNAL", "10.1.0.35", {hostname: "build-01"}),
    sampleNode("i5", "INTERNAL", "10.1.0.53", {hostname: "dns"}),
    sampleNode("i6", "INTERNAL", "10.1.0.80"),
    sampleNode("e1", "EXTERNAL", "192.0.2.10", {asn_name: "Example Cloud", country_code: "US"}),
    sampleNode("e2", "EXTERNAL", "192.0.2.44", {asn_name: "Example Cloud", country_code: "US"}),
    sampleNode("e3", "EXTERNAL", "198.51.100.7", {asn_name: "Example CDN", country_code: "DE"}),
    sampleNode("e4", "EXTERNAL", "198.51.100.90", {asn_name: "Example CDN", country_code: "NL"}),
    sampleNode("e5", "EXTERNAL", "203.0.113.5", {asn_name: "Example Transit", country_code: "JP"}),
    sampleNode("e6", "EXTERNAL", "203.0.113.77", {asn_name: "Example Hosting", country_code: "BR"})
  ],
  services: [],
  links: [
    sampleLink("i2", "i1", 48000000), sampleLink("i3", "i1", 9000000), sampleLink("i4", "i1", 21000000),
    sampleLink("i2", "i5", 40000, false), sampleLink("i3", "i5", 52000, false), sampleLink("i4", "i5", 31000, false),
    sampleLink("i6", "i5", 28000, false), sampleLink("i2", "e1", 6200000), sampleLink("i2", "e3", 900000),
    sampleLink("i3", "e2", 2400000), sampleLink("i4", "e1", 15000000), sampleLink("i4", "e4", 700000),
    sampleLink("i5", "e5", 120000, false), sampleLink("i6", "e3", 3100000), sampleLink("e6", "i6", 260000)
  ],
  total_links: 15,
  total_sessions: 45,
  total_bytes: 106891000,
  limit: 100,
  series: {from: "2026-01-01T12:00:00.000Z", bucket_size_ms: 30000, count: 1}
};

const minutesAgo = (minutes) => new Date(Date.now() - minutes*60*1000).toISOString();

const sampleAlert = (title, subsystem, tap, minutes) => {
  return {title: title, subsystem: subsystem, details: "Details of the alert", tap_name: tap,
    first_seen: minutesAgo(minutes), last_seen: minutesAgo(1)};
}

const SAMPLE_ALERTS = [
  sampleAlert("WiFi Network Monitor: Unexpected BSSID detected", "DOT11", "HQ-01", 42),
  sampleAlert("Beacon detected", "ETHERNET", "HQ-05", 180),
  sampleAlert("Bluetooth Monitor: Device detected", "BLUETOOTH", "HQ-02", 260)
];

// Tap metrics are kept for a day, so the node has no throughput for a longer time window.
const THROUGHPUT_MAXIMUM_WINDOW_MINUTES = 1440;

export default function sampleData(widgets, metrics, windowMinutes) {
  const data = {};

  widgets.forEach((widget) => {
    switch (widget.type) {
      case "STATUS":
        data[widget.id] = {level: "ALERT", active_alerts: 2, taps_online: 23, taps_total: 24};
        break;
      case "ALERTS": {
        const alerts = SAMPLE_ALERTS.filter((alert) => !widget.options.subsystem
          || alert.subsystem === widget.options.subsystem);
        data[widget.id] = {total: alerts.length, alerts: alerts};
        break;
      }
      case "TAPS":
        data[widget.id] = {
          total: 24,
          online: 23,
          offline: 1,
          degraded: 1,
          alerting: 2,
          locations: [
            {name: "Warehouse", total: 4, online: 3, states: "XOOO"},
            {name: "Headquarters", total: 8, online: 8, states: "AAOOOOOO"},
            {name: "Datacenter", total: 6, online: 6, states: "DOOOOO"},
            {name: "Branch Office", total: 6, online: 6, states: "OOOOOO"}
          ],
          cells: [
            ...[1, 2, 3, 4, 5, 6].map((i) => ({name: "BR-0" + i, state: "OK"})),
            ...[1, 2, 3, 4, 5, 6].map((i) => ({name: "DC-0" + i, state: i === 3 ? "DEGRADED" : "OK"})),
            ...[1, 2, 3, 4, 5, 6, 7, 8].map((i) => ({name: "HQ-0" + i, state: i === 1 || i === 5 ? "ALERT" : "OK"})),
            ...[1, 2, 3, 4].map((i) => ({name: "WH-0" + i, state: i === 2 ? "OFFLINE" : "OK"}))
          ],
          problems: [
            {name: "WH-02", location_name: "Warehouse", state: "OFFLINE", reason: "OFFLINE",
              last_report: minutesAgo(5760), clock_drift_ms: 2, dropped: 0, captures_running: 0,
              captures_total: 0, active_alerts: 0},
            {name: "HQ-01", location_name: "Headquarters", state: "ALERT", reason: "ALERTS",
              last_report: minutesAgo(0), clock_drift_ms: 2, dropped: 0, captures_running: 3,
              captures_total: 3, active_alerts: 1},
            {name: "HQ-05", location_name: "Headquarters", state: "ALERT", reason: "ALERTS",
              last_report: minutesAgo(0), clock_drift_ms: 2, dropped: 0, captures_running: 3,
              captures_total: 3, active_alerts: 1},
            {name: "DC-03", location_name: "Datacenter", state: "DEGRADED", reason: "DROPS",
              last_report: minutesAgo(0), clock_drift_ms: 2, dropped: 1840, captures_running: 3,
              captures_total: 3, active_alerts: 0}
          ]
        };
        break;
      case "LOCATIONS":
        data[widget.id] = {
          total: 4,
          locations: [
            {name: "Headquarters", taps_total: 8, taps_online: 8, active_alerts: 2, time_zone: "America/Chicago",
              environment: {condition: {display_name: "Clear"}, temperature: 12, alerts: []}},
            {name: "Warehouse", taps_total: 4, taps_online: 3, active_alerts: 0, time_zone: "America/New_York",
              environment: {condition: {display_name: "Rain"}, temperature: 8, alerts: ["Flood Warning"]}},
            {name: "Branch Office", taps_total: 6, taps_online: 6, active_alerts: 0, time_zone: "Europe/Berlin",
              environment: {condition: {display_name: "Overcast"}, temperature: 6, alerts: []}},
            {name: "Datacenter", taps_total: 6, taps_online: 6, active_alerts: 0, time_zone: null, environment: null}
          ]
        };
        break;
      case "METRIC": {
        const metric = metrics ? metrics.find((m) => m.metric === widget.options.metric) : null;
        const unavailable = (metric && !metric.available)
          || (widget.options.metric === "THROUGHPUT" && windowMinutes > THROUGHPUT_MAXIMUM_WINDOW_MINUTES);
        data[widget.id] = unavailable ? null : {
          metric: widget.options.metric,
          title: metric ? metric.title : "Metric",
          unit: null,
          ...METRIC_SAMPLES[widget.options.metric]
        };
        break;
      }
    }
  });

  return data;
}
