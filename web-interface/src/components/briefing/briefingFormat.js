import moment from "moment";
import numeral from "numeral";

export const SEVERITY = {
  RED:    { label: "Health indicator red",    icon: "fa-solid fa-triangle-exclamation", rank: 0 },
  ORANGE: { label: "Warning",                 icon: "fa-solid fa-circle-exclamation",   rank: 1 },
  ALERT:  { label: "Alert",                   icon: "fa-solid fa-bell",                 rank: 2 },
  NOTICE: { label: "Notice",                  icon: "fa-solid fa-circle-dot",           rank: 3 },
  INFO:   { label: "Info",                    icon: "fa-solid fa-circle-info",          rank: 4 }
}

export const severityMeta = (severity) => SEVERITY[severity] || SEVERITY.INFO;

export const SUBSYSTEM_NAMES = {
  DOT11: "WiFi",
  ETHERNET: "Ethernet",
  BLUETOOTH: "Bluetooth",
  UAV: "UAV",
  GENERIC: "System"
}

export const subsystemName = (subsystem) => SUBSYSTEM_NAMES[subsystem] || subsystem;

export function formatDuration(ms) {
  const totalMinutes = Math.round(ms / 60000);
  if (totalMinutes < 60) {
    return totalMinutes + "m";
  }

  const totalHours = Math.floor(totalMinutes / 60);
  const minutes = totalMinutes % 60;
  if (totalHours < 48) {
    return minutes === 0 ? totalHours + "h" : totalHours + "h " + minutes + "m";
  }

  const days = Math.floor(totalHours / 24);
  const hours = totalHours % 24;
  return hours === 0 ? days + "d" : days + "d " + hours + "h";
}

export function formatCount(value) {
  if (value === null || value === undefined) {
    return "–";
  }
  if (Math.abs(value) >= 10000) {
    return numeral(value).format("0.0a");
  }
  return numeral(value).format("0,0");
}

export function formatBytes(value) {
  if (value === null || value === undefined) {
    return "–";
  }
  if (value === 0) {
    return "0 B";
  }
  return numeral(value).format("0.0 b");
}

const BITRATE_UNITS = ["bit/s", "kbit/s", "Mbit/s", "Gbit/s", "Tbit/s"];

export function bitrateScale(bitsPerSecond) {
  let divisor = 1;
  let i = 0;
  while (bitsPerSecond/divisor >= 1000 && i < BITRATE_UNITS.length - 1) {
    divisor *= 1000;
    i++;
  }
  return {divisor: divisor, unit: BITRATE_UNITS[i]};
}

export function formatBitrate(bitsPerSecond) {
  if (bitsPerSecond === null || bitsPerSecond === undefined) {
    return "–";
  }
  const scale = bitrateScale(bitsPerSecond);
  return numeral(bitsPerSecond/scale.divisor).format(scale.divisor === 1 ? "0" : "0.0") + " " + scale.unit;
}

export function formatValue(value, unit) {
  if (unit === "bytes") {
    return formatBytes(value);
  }
  if (unit === "bitrate") {
    return formatBitrate(value);
  }
  return formatCount(value);
}

export function formatClock(timestamp) {
  const m = moment(timestamp);
  if (m.isSame(moment(), "day")) {
    return m.format("HH:mm");
  }
  return m.format("ddd HH:mm");
}

export function relativeTime(timestamp) {
  return moment(timestamp).fromNow();
}

export function fullTime(timestamp) {
  return moment(timestamp).format();
}

export const plural = (count, singular, pluralForm) => count === 1 ? singular : pluralForm;

export function describeWindow(window) {
  if (!window) {
    return "";
  }

  const start = formatClock(window.from);
  const length = formatDuration(window.duration_ms);

  switch (window.mode) {
    case "LAST_VISIT":
      if (window.is_first_visit) {
        return "This is your first briefing here, so changes and activity cover the last 24 hours: everything since " + start + " until now.";
      }
      if (window.capped) {
        return "Changes and activity cover everything since your last visit, capped at 7 days: since " + start + " until now.";
      }
      return "Changes and activity cover everything since your last visit at " + start + " until now, the last " + length + ".";
    case "LAST_SESSION":
      if (window.is_first_visit) {
        return "No earlier session is recorded, so changes and activity cover the last 24 hours: everything since " + start + " until now.";
      }
      if (window.capped) {
        return "Changes and activity cover everything since your last session ended, capped at 7 days: since " + start + " until now.";
      }
      return "Changes and activity cover everything since your last session ended at " + start + " until now, the last " + length + ".";
    default:
      return "Changes and activity cover the selected time range: " + start + " to " + formatClock(window.to) + ", " + length + ".";
  }
}

export function windowPhrase(window) {
  if (!window) {
    return "in this window";
  }

  const start = formatClock(window.from);
  const length = formatDuration(window.duration_ms);

  switch (window.mode) {
    case "LAST_VISIT":
      if (window.is_first_visit) {
        return "in the last 24 hours because this is your first briefing here";
      }
      if (window.capped) {
        return "in the last 7 days because your last visit was longer ago";
      }
      return "since your last visit at " + start + ", " + length + " ago";
    case "LAST_SESSION":
      if (window.is_first_visit) {
        return "in the last 24 hours because no earlier session is recorded";
      }
      if (window.capped) {
        return "in the last 7 days because your last session ended longer ago";
      }
      return "since your last session ended at " + start + ", " + length + " ago";
    default:
      return "in the selected range, " + start + " to " + formatClock(window.to);
  }
}

export function windowStartLabel(window) {
  if (!window) {
    return "window start";
  }

  switch (window.mode) {
    case "LAST_VISIT":
      if (window.is_first_visit) {
        return "24 hours ago";
      }
      if (window.capped) {
        return "7 days ago";
      }
      return "your last visit";
    case "LAST_SESSION":
      if (window.is_first_visit) {
        return "24 hours ago";
      }
      if (window.capped) {
        return "7 days ago";
      }
      return "last session ended";
    default:
      return "range start";
  }
}
