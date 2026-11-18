import moment from "moment";
import {toast} from "react-toastify";
import {exportCsv, exportSvg, exportSvgAsPng, svgElement, textWidth, timeRangeText} from "../../../shared/viz/VizExport";
import {GRID_SPACING} from "../../../shared/viz/VizTheme";
import {SHOW_OPTIONS} from "./SegmentationOverviewData";

const GRID = "segmentation-export-grid";
const LEGEND_FONT_SIZE = 11;
const LEGEND_ROW = 20;
const LEGEND_PADDING = 10;
const LEGEND_MARK_GAP = 4;
const LEGEND_ENTRY_GAP = 14;
const LEGEND_DIVIDER = 12;

export function exportCaptionLines(viewTitle, show, withExternal, withBroadcast, timeRange) {
  return [
    "Nzyme Segmentation, " + viewTitle + ". Show: " + SHOW_OPTIONS.find(([v]) => v === show)[1] + "."
        + (withExternal ? "" : " External networks and Internet hidden.")
        + (withBroadcast ? "" : " Broadcast and multicast hidden."),
    timeRangeText(timeRange) + ". Exported " + moment().format("YYYY-MM-DD HH:mm") + "."
  ];
}

// Reads the entries of SegmentationLegend from the page.
function drawLegend(legend, top, width, colors) {
  const group = svgElement("g", {});
  let x = LEGEND_PADDING;
  let row = 0;
  let divided = false;

  Array.from(legend.children).forEach((entry) => {
    if (entry.localName === "svg") {
      entry.querySelectorAll("defs").forEach((defs) => group.appendChild(defs.cloneNode(true)));
      return;
    }

    const mark = entry.querySelector("svg");
    const label = entry.textContent.trim();
    if (!mark && !label) {
      divided = true;
      return;
    }

    const markWidth = mark ? parseFloat(mark.getAttribute("width")) + LEGEND_MARK_GAP : 0;
    const entryWidth = markWidth + textWidth(label, LEGEND_FONT_SIZE);
    const first = x === LEGEND_PADDING;

    if (!first && x + (divided ? LEGEND_ENTRY_GAP : 0) + entryWidth > width - LEGEND_PADDING) {
      x = LEGEND_PADDING;
      row++;
    } else if (!first && divided) {
      group.appendChild(svgElement("rect", {
        x: x,
        y: top + LEGEND_PADDING + row * LEGEND_ROW + (LEGEND_ROW - LEGEND_DIVIDER) / 2,
        width: 1,
        height: LEGEND_DIVIDER,
        fill: colors.lines
      }));
      x += LEGEND_ENTRY_GAP;
    }
    divided = false;

    const middle = top + LEGEND_PADDING + row * LEGEND_ROW + LEGEND_ROW / 2;

    if (mark) {
      const copy = mark.cloneNode(true);
      copy.removeAttribute("class");
      copy.setAttribute("x", x);
      copy.setAttribute("y", middle - parseFloat(mark.getAttribute("height")) / 2);
      group.appendChild(copy);
    }

    group.appendChild(svgElement("text", {
      x: x + markWidth,
      y: middle,
      "font-size": LEGEND_FONT_SIZE,
      "dominant-baseline": "middle",
      fill: colors.muted
    }, label));

    x += entryWidth + LEGEND_ENTRY_GAP;
  });

  return {element: group, height: LEGEND_PADDING * 2 + (row + 1) * LEGEND_ROW};
}

function composeDrawing(drawing, legend, colors) {
  const number = (part, attribute) => parseFloat(part.getAttribute(attribute)) || 0;

  const parts = Array.from(drawing.querySelectorAll("svg")).map((svg) => {
    const part = svg.cloneNode(true);
    part.removeAttribute("style");
    part.querySelectorAll("animate").forEach((animation) => animation.remove());
    return part;
  });

  if (parts.length === 0) return null;

  const width = Math.max(drawing.clientWidth, ...parts.map((part) => number(part, "x") + number(part, "width")));
  const height = Math.max(...parts.map((part) => number(part, "y") + number(part, "height")));

  const root = svgElement("svg", {width: width, height: height});

  const pattern = svgElement("pattern", {id: GRID, width: GRID_SPACING, height: GRID_SPACING, patternUnits: "userSpaceOnUse"});
  pattern.appendChild(svgElement("rect", {x: GRID_SPACING / 2, y: GRID_SPACING / 2, width: 1.5, height: 1.5, fill: colors.grid}));
  const defs = svgElement("defs", {});
  defs.appendChild(pattern);
  root.appendChild(defs);
  root.appendChild(svgElement("rect", {x: 0, y: 0, width: width, height: height, fill: "url(#" + GRID + ")"}));

  parts.forEach((part) => root.appendChild(part));

  if (legend) {
    const drawn = drawLegend(legend, height, width, colors);
    root.appendChild(svgElement("rect", {x: 0, y: height, width: width, height: 1, fill: colors.lines}));
    root.appendChild(drawn.element);
    root.setAttribute("height", height + drawn.height);
  }

  return root;
}

export function exportOverviewImage(format, drawing, legend, caption, colors, name) {
  const svg = drawing ? composeDrawing(drawing, legend, colors) : null;
  if (!svg) return false;

  if (format === "svg") {
    exportSvg(svg, caption, colors, name);
  } else {
    exportSvgAsPng(svg, caption, colors, name,
        () => toast.error("The browser could not turn the drawing into an image."));
  }

  return true;
}

export function exportCrossingsCsv(cells, name) {
  const rows = cells.slice().sort((a, b) => b.total - a.total);

  exportCsv([
    ["source", (c) => c.source.name],
    ["source_type", (c) => c.source.type.toLowerCase()],
    ["destination", (c) => c.destination.name],
    ["destination_type", (c) => c.destination.type.toLowerCase()],
    ["sessions", (c) => c.total],
    ["connected", (c) => c.connected],
    ["rejected", (c) => c.rejected],
    ["no_reply", (c) => c.no_reply],
    ["sent", (c) => c.sent],
    ["long_lived", (c) => c.continued],
    ["services", (c) => c.service_count],
    ["policy_coverage", (c) => c.coverage.toLowerCase()],
    ["policies_in_scope", (c) => c.policy_uuids.length],
    ["crossed_findings", (c) => c.crossed_findings],
    ["attempted_findings", (c) => c.attempted_findings],
    ["taps", (c) => c.taps.map((tap) => tap.name).join(", ")],
    ["first_seen", (c) => c.first_seen],
    ["last_seen", (c) => c.last_seen]
  ], rows, name);
}

export const EXPORTS = [["Image (PNG)", "png"], ["Image (SVG)", "svg"], ["Crossings (CSV)", null]];
