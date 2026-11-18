import moment from "moment";
import {toast} from "react-toastify";
import Store from "../../../util/Store";
import {
  EXPORT_SCALE,
  exportCanvasAsPng,
  exportCsv,
  exportSvg,
  exportSvgAsPng,
  filtersText
} from "../../shared/viz/VizExport";
import {colorsForMode} from "../../shared/viz/VizTheme";
import {formatCount} from "../assets/details/AssetProfileHelpers";
import {L4_SESSIONS_FILTER_FIELDS} from "../l4/L4SessionFilterFields";
import {applyTimeWindow, EXTERNAL_LEVELS, INTERNAL_LEVELS, nodeLabel, serviceLabel} from "./TopologyData";

const title = (options, value) => options.find(([v]) => v === value)[1];

export function exportCaptionLines(heading, internalLevel, externalLevel, when, selectedTaps, filters) {
  const tapCount = Array.isArray(selectedTaps) ? selectedTaps.length : null;
  const filterText = filtersText(filters, L4_SESSIONS_FILTER_FIELDS);

  return [
    heading + ". Internal nodes: " + title(INTERNAL_LEVELS, internalLevel)
        + ". External nodes: " + title(EXTERNAL_LEVELS, externalLevel) + ".",
    when + ". Exported " + moment().format("YYYY-MM-DD HH:mm") + ".",
    (tapCount === null ? "All taps" : formatCount(tapCount) + (tapCount === 1 ? " tap" : " taps")) + ". "
        + (filterText ? "Filters: " + filterText + "." : "No filters.")
  ];
}

function exportConnectionsCsv(linkModel, fileName) {
  const node = (id) => linkModel.nodes.get(id);
  const name = (n) => (n.name ? n.name : (n.detail ? n.detail : ""));
  const type = (n) => n.type.toLowerCase();
  const rows = linkModel.links.filter((l) => l.active).sort((a, b) => b.bytes - a.bytes);

  exportCsv([
    ["source", (l) => node(l.source).label],
    ["source_name", (l) => name(node(l.source))],
    ["source_type", (l) => type(node(l.source))],
    ["destination", (l) => node(l.target).label],
    ["destination_name", (l) => name(node(l.target))],
    ["destination_type", (l) => type(node(l.target))],
    ["sessions", (l) => l.sessions],
    ["bytes_sent", (l) => l.bytes_tx],
    ["bytes_received", (l) => l.bytes_rx],
    ["first_seen", (l) => l.first_seen],
    ["last_seen", (l) => l.last_seen]
  ], rows, fileName);
}

function exportFlowsCsv(flows, timeWindow, fileName) {
  const type = (node) => (node ? node.type.toLowerCase() : "");
  const nodes = new Map(flows.nodes.map((n) => [n.id, n]));
  const services = new Map(flows.services.map((s) => [s.id, s]));
  const rows = applyTimeWindow(flows.links, flows.series, timeWindow)
      .filter((l) => l.active && nodes.has(l.source) && nodes.has(l.target))
      .sort((a, b) => (b.bytes_tx + b.bytes_rx) - (a.bytes_tx + a.bytes_rx));

  exportCsv([
    ["source", (l) => nodeLabel(nodes.get(l.source))],
    ["source_type", (l) => type(nodes.get(l.source))],
    ["service", (l) => (services.has(l.service) ? serviceLabel(services.get(l.service)) : "")],
    ["destination", (l) => nodeLabel(nodes.get(l.target))],
    ["destination_type", (l) => type(nodes.get(l.target))],
    ["sessions", (l) => l.sessions],
    ["bytes_sent", (l) => l.bytes_tx],
    ["bytes_received", (l) => l.bytes_rx]
  ], rows, fileName);
}

function exportGeoFlowsCsv(geoModel, fileName) {
  const sites = new Map(geoModel.origins.map((o) => [o.id, o]));
  const points = new Map(geoModel.points.map((p) => [p.id, p]));
  const rows = geoModel.flows.slice().sort((a, b) => b.bytes - a.bytes);

  exportCsv([
    ["site", (f) => (sites.has(f.originId) ? sites.get(f.originId).label : "")],
    ["place", (f) => (points.has(f.pointId) ? points.get(f.pointId).label : "")],
    ["country_code", (f) => (points.has(f.pointId) ? points.get(f.pointId).country_code : "")],
    ["direction", (f) => f.direction.toLowerCase()],
    ["sessions", (f) => f.sessions],
    ["bytes", (f) => f.bytes]
  ], rows, fileName);
}

export function exportViewData(view, {linkModel, flows, geoModel, timeWindow}, namePrefix) {
  if (view === "map") {
    if (!geoModel) return false;
    exportGeoFlowsCsv(geoModel, namePrefix + "-map-flows");
  } else if (view === "flow") {
    if (!flows) return false;
    exportFlowsCsv(flows, timeWindow, namePrefix + "-flows");
  } else {
    if (!linkModel) return false;
    exportConnectionsCsv(linkModel, namePrefix + "-connections");
  }

  return true;
}

export function exportViewImage(view, format, renderGraph, drawing, caption, name) {
  const colors = colorsForMode(Boolean(Store.get("dark_mode")));

  if (view === "graph") {
    const rendered = renderGraph ? renderGraph(EXPORT_SCALE) : null;
    if (!rendered) return false;

    exportCanvasAsPng(rendered, caption, colors, name);
    return true;
  }

  const svg = drawing ? drawing.querySelector("svg") : null;
  if (!svg) return false;

  if (format === "svg") {
    exportSvg(svg, caption, colors, name);
  } else {
    exportSvgAsPng(svg, caption, colors, name,
        () => toast.error("The browser could not turn the drawing into an image."));
  }

  return true;
}

// What a view can be exported as: [label, image format], without a format for its data.
export const EXPORTS = {
  graph: [["Image (PNG)", "png"], ["Connections (CSV)", null]],
  matrix: [["Image (PNG)", "png"], ["Image (SVG)", "svg"], ["Connections (CSV)", null]],
  flow: [["Image (PNG)", "png"], ["Image (SVG)", "svg"], ["Flows (CSV)", null]],
  map: [["Flows (CSV)", null]]
};

export const MAP_EXPORT_NOTE = "The map cannot be exported as an image.";
