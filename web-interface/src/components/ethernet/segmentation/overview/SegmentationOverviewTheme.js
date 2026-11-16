import {colorsForMode} from "../../../shared/viz/VizTheme";
import {FINDING_CLASSES, OUTCOMES} from "../SegmentationFormat";

// Strongest first.
export const STATUS_ORDER = ["CROSSED", "ATTEMPTED", "NONE", "PARTIAL", "COVERED"];

export const STATUSES = {
  CROSSED: FINDING_CLASSES.CROSSED,
  ATTEMPTED: FINDING_CLASSES.ATTEMPTED,
  NONE: {
    title: "No policy",
    description: "No policy has this crossing in scope."
  },
  PARTIAL: {
    title: "Partly covered",
    description: "Policies have part of this crossing in scope. What they cover is allowed."
  },
  COVERED: {
    title: "Covered",
    description: "Policies have all of this crossing in scope and allow what was seen."
  }
};

export const LONG_LIVED_DESCRIPTION = "Sessions that were opened in an earlier hour and were still open.";

export const COVERAGE_TEXT = {
  FULL: "Fully covered by policies",
  PARTIAL: "Partly covered",
  NONE: "No policy covers this"
};

export const OUTCOME_FIELDS = [["CONNECTED", "connected"], ["REJECTED", "rejected"], ["NO_REPLY", "no_reply"], ["SENT", "sent"]];

const HOST_OUTCOME_FIELDS = OUTCOME_FIELDS.filter(([outcome]) => outcome !== "SENT");
const BROADCAST_OUTCOME_FIELDS = OUTCOME_FIELDS.filter(([outcome]) => outcome === "SENT");

export const outcomeFieldsOf = (cell) => (cell.destination.type === "BROADCAST" ? BROADCAST_OUTCOME_FIELDS : HOST_OUTCOME_FIELDS);

export const sessionsOf = (counts) => OUTCOME_FIELDS.reduce((sum, [, field]) => sum + (counts[field] || 0), 0);

export const outcomeTitle = (outcome) => (OUTCOMES[outcome] ? OUTCOMES[outcome].title : outcome);

export const cellStatus = (cell) => {
  if (cell.crossed_findings > 0) return "CROSSED";
  if (cell.attempted_findings > 0) return "ATTEMPTED";

  switch (cell.coverage) {
    case "FULL": return "COVERED";
    case "PARTIAL": return "PARTIAL";
    default: return "NONE";
  }
}

export const isFinding = (status) => status === "CROSSED" || status === "ATTEMPTED";

export const overviewColors = (darkMode) => {
  const colors = colorsForMode(darkMode);

  if (darkMode) {
    return {
      ...colors,
      crossed: "#fb5c78",
      attempted: "#e0a526",
      covered: "#4aa3e8",
      unjudged: "#8b95a7",
      connected: "#aab5c7",
      rejected: "#667185",
      noReply: "#383f4e",
      sent: "#b0a3d4",
      box: "#25252d",
      boxBorder: "#4a4a58"
    }
  }

  return {
    ...colors,
    crossed: "#e11d48",
    attempted: "#c2850a",
    covered: "#0b76b8",
    unjudged: "#64748b",
    connected: "#5b6b82",
    rejected: "#a3afc0",
    noReply: "#cdd5df",
    sent: "#7c6f9f",
    box: "#ffffff",
    boxBorder: "#b4bfce"
  }
}

export const statusColor = (colors, status) => {
  switch (status) {
    case "CROSSED": return colors.crossed;
    case "ATTEMPTED": return colors.attempted;
    case "NONE": return colors.unjudged;
    default: return colors.covered;
  }
}

export const outcomeColor = (colors, outcome) => {
  switch (outcome) {
    case "CONNECTED": return colors.connected;
    case "REJECTED": return colors.rejected;
    case "SENT": return colors.sent;
    default: return colors.noReply;
  }
}

export const volumeOf = (sessions, most) => {
  if (!sessions || sessions <= 0) return 0;
  if (!most || most <= 1) return 1;
  return Math.max(0, Math.min(1, Math.log(sessions) / Math.log(most)));
}

export const statusPaint = (colors, status, volume) => {
  switch (status) {
    case "CROSSED":
      return {fill: colors.crossed, fillOpacity: 0.62 + 0.38 * volume, stroke: null, hatch: 0, half: false};
    case "ATTEMPTED":
      return {fill: colors.attempted, fillOpacity: 0.16 + 0.3 * volume, stroke: colors.attempted, hatch: 0, half: false};
    case "NONE":
      return {fill: null, fillOpacity: 0, stroke: colors.unjudged, hatch: 0.5 + 0.5 * volume, half: false};
    case "PARTIAL":
      return {fill: colors.covered, fillOpacity: 0.16 + 0.5 * volume, stroke: null, hatch: 0.5 + 0.5 * volume, half: true};
    default:
      return {fill: colors.covered, fillOpacity: 0.16 + 0.5 * volume, stroke: null, hatch: 0, half: false};
  }
}

export const gridBackground = (colors) => ({
  backgroundColor: colors.background,
  backgroundImage: "radial-gradient(" + colors.grid + " 1px, transparent 1.2px)",
  backgroundSize: "24px 24px"
});
