import React, {useCallback, useContext, useEffect, useMemo, useRef, useState} from "react";
import moment from "moment";
import {toast} from "react-toastify";
import Store from "../../../util/Store";
import {TapContext} from "../../../App";
import {disableTapSelector, enableTapSelector} from "../../misc/TapSelector";
import usePageTitle from "../../../util/UsePageTitle";
import useSelectedTenant from "../../system/tenantselector/useSelectedTenant";
import CardTitleWithControls from "../../shared/CardTitleWithControls";
import Filters from "../../shared/filtering/Filters";
import useUrlFilters from "../../shared/filtering/useUrlFilters";
import {queryParametersToFilters} from "../../shared/filtering/FilterQueryParameters";
import useUrlParams from "../../shared/useUrlParams";
import {Absolute, Presets} from "../../shared/timerange/TimeRange";
import {useUrlTimeRange} from "../../shared/timerange/TimeRangeSelector";
import {useApplyTimeRange} from "../../shared/timerange/TimeRangeUrl";
import GenericWidgetLoadingSpinner from "../../widgets/GenericWidgetLoadingSpinner";
import VizDisplayMenu from "../../shared/viz/VizDisplayMenu";
import VizExportMenu from "../../shared/viz/VizExportMenu";
import {
  EXPORT_SCALE,
  exportCanvasAsPng,
  exportCsv,
  exportSvg,
  exportSvgAsPng,
  filtersText,
  timeRangeText
} from "../../shared/viz/VizExport";
import {colorsForMode} from "../../shared/viz/VizTheme";
import {L4_SESSIONS_FILTER_FIELDS} from "../l4/L4SessionFilterFields";
import TopologyService from "../../../services/ethernet/TopologyService";
import {formatCount} from "../assets/details/AssetProfileHelpers";
import useTopologyRequest from "./useTopologyRequest";
import TopologyGraph from "./TopologyGraph";
import TopologyMatrix from "./TopologyMatrix";
import TopologyFlow from "./TopologyFlow";
import TopologyMap from "./TopologyMap";
import TopologyTimeScrubber from "./TopologyTimeScrubber";
import TopologySelectionPanel from "./TopologySelectionPanel";
import TopologyRankingCard from "./TopologyRankingCard";
import TopologySummaryStrip from "./TopologySummaryStrip";
import {
  applyTimeWindow,
  buildFlowModel,
  buildGeoModel,
  buildLinkModel,
  buildNodeScale,
  describeNodeId,
  EXTERNAL_LEVELS,
  formatWindowTime,
  INTERNAL_LEVELS,
  LINK_LIMITS,
  nodeFilter,
  nodeLabel,
  serviceLabel,
  SERVICE_LEVELS,
  windowWidth
} from "./TopologyData";

const topologyService = new TopologyService();

const VIEW_HEIGHT = 580;
const FLOW_LIMIT = 400;
const GEO_LIMIT = 500;

const VIEWS = [["graph", "Graph"], ["matrix", "Matrix"], ["flow", "Flow"], ["map", "Map"]];

const URL_OPTIONS = {
  view: "graph",
  exclude: null,
  internal: "HOST",
  external: "NETWORK",
  service: "PROTOCOL",
  links: "250",
  arrange: "force",
  places: "city"
};

const MIN_BYTES = [[0, "Show all"], [10240, "10 KB"], [102400, "100 KB"], [1048576, "1 MB"], [10485760, "10 MB"], [104857600, "100 MB"]];

const MAX_EXCLUDED_NODES = 25;

const EXTERNAL_RANKINGS = [["EXTERNAL_NETWORK", "Autonomous systems"], ["EXTERNAL_COUNTRY", "Countries"], ["EXTERNAL_HOST", "Addresses"]];
const SERVICE_RANKINGS = [["SERVICE_PROTOCOL", "Protocols"], ["SERVICE_PORT", "Ports"]];
const RARE_PRESETS = [
  ["rare", "Fewest peers", "PEERS", "ASC"],
  ["new", "Most recently first seen", "FIRST_SEEN", "DESC"]
];

const valueIn = (options, value, fallback) => (options.some(([v]) => String(v) === value) ? value : fallback);

export default function TopologyPage() {

  usePageTitle("Topology");

  const [organizationId, tenantId] = useSelectedTenant();
  const tapContext = useContext(TapContext);
  const selectedTaps = tapContext.taps;

  useEffect(() => {
    enableTapSelector(tapContext);

    return () => {
      disableTapSelector(tapContext);
    }
  }, [tapContext]);

  const [timeRange, setTimeRange] = useUrlTimeRange(Presets.RELATIVE_HOURS_24);
  const applyTimeRange = useApplyTimeRange(setTimeRange);
  const [filters, setFilters] = useUrlFilters(L4_SESSIONS_FILTER_FIELDS);
  const [filtersReady, setFiltersReady] = useState(false);
  const [urlOptions, setUrlOptions] = useUrlParams(URL_OPTIONS);
  const [revision, setRevision] = useState(0);

  const view = valueIn(VIEWS, urlOptions.view, "graph");
  const internalLevel = valueIn(INTERNAL_LEVELS, urlOptions.internal, "HOST");
  const externalLevel = valueIn(EXTERNAL_LEVELS, urlOptions.external, "NETWORK");
  const serviceLevel = valueIn(SERVICE_LEVELS, urlOptions.service, "PROTOCOL");
  const linkLimit = valueIn(LINK_LIMITS.map((l) => [l]), urlOptions.links, "250");
  const arrangement = valueIn([["force"], ["perimeter"], ["clusters"]], urlOptions.arrange, "force");
  const places = valueIn([["city"], ["country"]], urlOptions.places, "city");
  const excludedNodes = useMemo(() => {
    if (!urlOptions.exclude) return [];
    try {
      const ids = JSON.parse(urlOptions.exclude);
      return Array.isArray(ids) ? ids.filter((id) => typeof id === "string").slice(0, MAX_EXCLUDED_NODES) : [];
    } catch {
      return [];
    }
  }, [urlOptions.exclude]);
  // What excluded nodes were called while they were still on the page. Their data is gone once they are excluded.
  const excludedLabels = useRef(new Map());

  const [minBytes, setMinBytes] = useState(0);
  const [sizeBy, setSizeBy] = useState("bytes");
  const [labels, setLabels] = useState("auto");
  const [matrixOrder, setMatrixOrder] = useState("traffic");
  const [flowEntries, setFlowEntries] = useState(12);

  const [timeWindow, setTimeWindow] = useState(null);
  const [selection, setSelection] = useState(null);
  const [focus, setFocus] = useState(null);
  const [pendingLocate, setPendingLocate] = useState(null);

  const viewRef = useRef(null);
  const drawingRef = useRef(null);
  const graphExportRef = useRef(null);

  const refresh = useCallback(() => setRevision((r) => r + 1), []);

  const request = useMemo(() => ({
    organizationId: organizationId,
    tenantId: tenantId,
    taps: selectedTaps,
    timeRange: timeRange,
    filters: filters,
    nodes: [],
    excludedNodes: excludedNodes
  }), [organizationId, tenantId, selectedTaps, timeRange, filters, excludedNodes]);

  const requestKey = JSON.stringify([request, revision]);

  const summary = useTopologyRequest(requestKey, filtersReady,
      (setData) => topologyService.getSummary(request, setData));
  const timeline = useTopologyRequest(requestKey, filtersReady,
      (setData) => topologyService.getTimeline(request, setData));
  const links = useTopologyRequest(
      JSON.stringify([requestKey, internalLevel, externalLevel, linkLimit]),
      filtersReady && (view === "graph" || view === "matrix"),
      (setData) => topologyService.getLinks(request, internalLevel, externalLevel, null, linkLimit, setData));
  const flows = useTopologyRequest(
      JSON.stringify([requestKey, internalLevel, externalLevel, serviceLevel]),
      filtersReady && view === "flow",
      (setData) => topologyService.getLinks(request, internalLevel, externalLevel, serviceLevel, FLOW_LIMIT, setData));
  const geo = useTopologyRequest(requestKey, filtersReady && view === "map",
      (setData) => topologyService.getGeoFlows(request, GEO_LIMIT, setData));

  // The window and the selection belong to the data they were made on.
  useEffect(() => {
    setTimeWindow(null);
  }, [requestKey]);

  useEffect(() => {
    setSelection(null);
    setFocus(null);
  }, [requestKey, view, internalLevel, externalLevel]);

  const linkData = view === "flow" ? flows : links;
  const linkModel = useMemo(() => (linkData ? buildLinkModel(linkData, timeWindow) : null), [linkData, timeWindow]);
  // Depends on how wide the window is, not on where it is, so it holds while the window moves.
  const nodeScaleWidth = linkData ? windowWidth(linkData.series, timeWindow) : null;
  const nodeScale = useMemo(() => (linkData ? buildNodeScale(linkData, nodeScaleWidth) : null), [linkData, nodeScaleWidth]);
  const flowModel = useMemo(() => (flows ? buildFlowModel(flows, timeWindow, flowEntries) : null),
      [flows, timeWindow, flowEntries]);
  const geoWhole = useMemo(() => (geo ? buildGeoModel(geo, null, places) : null), [geo, places]);
  const geoModel = useMemo(() => (geo && timeWindow ? buildGeoModel(geo, timeWindow, places) : geoWhole),
      [geo, timeWindow, places, geoWhole]);

  const addFilter = useCallback((filter) => {
    if (!filter) return;

    const current = filters || {};
    if ((current[filter.field] || []).some((f) => f.operator === filter.operator && String(f.value) === filter.value)) {
      return;
    }

    const added = queryParametersToFilters(JSON.stringify({[filter.field]: [filter]}), L4_SESSIONS_FILTER_FIELDS);
    setFilters({...current, [filter.field]: (current[filter.field] || []).concat(added[filter.field])});
  }, [filters, setFilters]);

  const filterForNode = useCallback((node) => addFilter(nodeFilter(node)), [addFilter]);

  const exclude = useCallback((nodeId, label) => {
    if (excludedNodes.includes(nodeId)) return;
    if (excludedNodes.length >= MAX_EXCLUDED_NODES) {
      toast.info("No more than " + MAX_EXCLUDED_NODES + " nodes can be excluded.");
      return;
    }

    if (label) excludedLabels.current.set(nodeId, label);
    setUrlOptions({exclude: JSON.stringify(excludedNodes.concat([nodeId]))});
    setSelection(null);
  }, [excludedNodes, setUrlOptions]);

  const include = (nodeId) => {
    const remaining = excludedNodes.filter((id) => id !== nodeId);
    setUrlOptions({exclude: remaining.length > 0 ? JSON.stringify(remaining) : null});
  }

  const excludedLabel = (nodeId) => (excludedLabels.current.has(nodeId)
      ? excludedLabels.current.get(nodeId) : describeNodeId(nodeId));

  const locate = useCallback((nodeId) => {
    if (viewRef.current) {
      viewRef.current.scrollIntoView({behavior: "smooth", block: "nearest"});
    }
    if (view === "map" || view === "flow") {
      setUrlOptions({view: "graph"});
    }
    setPendingLocate(nodeId);
  }, [view, setUrlOptions]);

  useEffect(() => {
    if (pendingLocate === null || (view !== "graph" && view !== "matrix") || !linkModel) {
      return;
    }

    if (linkModel.nodes.has(pendingLocate)) {
      setSelection({kind: "node", id: pendingLocate});
      setFocus({id: pendingLocate, at: Date.now()});
    } else {
      toast.info("This node is not part of the shown connections. Add a filter for it to see it.");
    }
    setPendingLocate(null);
  }, [pendingLocate, view, linkModel]);

  const selectFromPanel = useCallback((s) => {
    setSelection(s);
    if (s && (s.kind === "site" || s.kind === "place")) {
      setFocus({id: s.id, at: Date.now()});
    }
  }, []);

  const optionSelect = (o) => (
      <div key={o.id} className="d-flex align-items-center me-3 mb-2">
        <label htmlFor={o.id} className="me-2 small text-muted mb-0">{o.label}</label>
        <select id={o.id} className="form-select form-select-sm w-auto" value={o.value}
                onChange={(e) => o.onChange(e.target.value)}>
          {o.options.map(([v, title]) => <option key={v} value={v}>{title}</option>)}
        </select>
        {o.after ? <span className="ms-2 small text-muted text-nowrap">{o.after}</span> : null}
      </div>
  )

  const viewOptions = () => {
    const internal = {id: "topology-internal", label: "Internal nodes", value: internalLevel,
      onChange: (v) => setUrlOptions({internal: v}), options: INTERNAL_LEVELS};
    const external = {id: "topology-external", label: "External nodes", value: externalLevel,
      onChange: (v) => setUrlOptions({external: v}), options: EXTERNAL_LEVELS};
    const limit = {id: "topology-links", label: "Connections", value: linkLimit,
      onChange: (v) => setUrlOptions({links: v}), options: LINK_LIMITS.map((l) => [String(l), "Top " + formatCount(l)]),
      after: linkData ? formatCount(linkData.total_links) + " in total" : null};

    switch (view) {
      case "graph":
        return {
          data: [internal, external, limit],
          display: [
            {id: "topology-arrange", label: "Arrange", value: arrangement, onChange: (v) => setUrlOptions({arrange: v}),
              options: [["force", "By connections"], ["perimeter", "Internal inside, external around"], ["clusters", "By network and country"]]},
            {id: "topology-size", label: "Node size", value: sizeBy, onChange: setSizeBy,
              options: [["bytes", "Traffic"], ["sessions", "Sessions"], ["peers", "Connected nodes"]]},
            {id: "topology-labels", label: "Labels", value: labels, onChange: setLabels,
              options: [["auto", "Busiest nodes"], ["all", "All nodes"], ["hover", "On hover"]]},
            {id: "topology-min-bytes", label: "Hide links below", value: minBytes, onChange: (v) => setMinBytes(parseInt(v, 10)),
              options: MIN_BYTES}
          ]
        };
      case "matrix":
        return {
          data: [internal, external, limit],
          display: [
            {id: "topology-matrix-order", label: "Order", value: matrixOrder, onChange: setMatrixOrder,
              options: [["traffic", "By traffic"], ["name", "By name"]]}
          ]
        };
      case "flow":
        return {
          data: [
            internal,
            external,
            {id: "topology-service", label: "Services", value: serviceLevel, onChange: (v) => setUrlOptions({service: v}),
              options: SERVICE_LEVELS}
          ],
          display: [
            {id: "topology-flow-entries", label: "Entries per column", value: flowEntries,
              onChange: (v) => setFlowEntries(parseInt(v, 10)), options: [[8, "8"], [12, "12"], [16, "16"], [24, "24"]]}
          ]
        };
      default:
        return {
          data: [
            {id: "topology-places", label: "Map points", value: places, onChange: (v) => setUrlOptions({places: v}),
              options: [["city", "Cities"], ["country", "Countries"]]}
          ],
          display: []
        };
    }
  }

  const loading = () => (
      <div className="border rounded" style={{height: VIEW_HEIGHT + "px"}}>
        <GenericWidgetLoadingSpinner height={VIEW_HEIGHT - 2} />
      </div>
  );

  const visualization = () => {
    switch (view) {
      case "graph":
        return linkModel
            ? <TopologyGraph model={linkModel}
                             scale={nodeScale}
                             dataKey={links}
                             minBytes={minBytes}
                             arrangement={arrangement}
                             sizeBy={sizeBy}
                             labels={labels}
                             selectedId={selection && (selection.kind === "node" || selection.kind === "link") ? selection.id : null}
                             onSelect={setSelection}
                             onFilter={filterForNode}
                             focus={focus}
                             height={VIEW_HEIGHT}
                             exportRef={graphExportRef} />
            : loading();
      case "matrix":
        return linkModel
            ? <TopologyMatrix model={linkModel} order={matrixOrder} selection={selection} onSelect={setSelection} height={VIEW_HEIGHT} />
            : loading();
      case "flow":
        return flowModel
            ? <TopologyFlow model={flowModel} selection={selection} onSelect={setSelection} height={VIEW_HEIGHT} />
            : loading();
      default:
        return geoModel
            ? <TopologyMap model={geoModel} whole={geoWhole} selection={selection} onSelect={setSelection} focus={focus} height={VIEW_HEIGHT} />
            : loading();
    }
  }

  // What of the result the view shows, as {text, limited}, or null if there is nothing.
  const coverageText = () => {
    const data = view === "map" ? geo : linkData;
    if (!data) return null;

    const items = view === "map" ? data.flows : data.links;
    const total = view === "map" ? data.total_flows : data.total_links;
    const what = (n) => (view === "graph" || view === "matrix"
        ? (n === 1 ? "connection" : "connections") : (n === 1 ? "flow" : "flows"));

    const hidden = view === "graph" && minBytes > 0
        ? " Connections below " + MIN_BYTES.find(([bytes]) => bytes === minBytes)[1] + " are hidden."
        : "";

    if (total === 0) return null;
    if (total <= items.length) {
      return {
        text: "Showing " + (total === 1 ? "the only " + what(1) : "all " + formatCount(total) + " " + what(total)) + "." + hidden,
        limited: false
      };
    }

    const shown = items.reduce((sum, i) => sum + i.bytes_tx + i.bytes_rx, 0);
    const share = data.total_bytes > 0 ? 100 * shown / data.total_bytes : 100;
    // Rounded down, so that a part is never called all of the traffic.
    const shareText = share >= 99.95 ? "over 99.9" : share.toFixed(1);

    return {
      text: "Showing " + formatCount(items.length) + " of " + formatCount(total) + " " + what(total)
          + " (" + shareText + "% of traffic)." + hidden,
      limited: true
    };
  }

  const coverage = () => {
    const shown = coverageText();
    if (!shown) return null;

    const adjustable = shown.limited && (view === "graph" || view === "matrix");
    const next = adjustable ? LINK_LIMITS.find((l) => l > parseInt(linkLimit, 10)) : undefined;

    return (
        <div className="text-muted small mt-1">
          {shown.text}
          {next !== undefined ?
              <button type="button" className="btn btn-link p-0 ms-1 align-baseline" style={{fontSize: "inherit"}}
                      onClick={() => setUrlOptions({links: String(next)})}>
                Show top {formatCount(next)}
              </button> : null}
        </div>
    );
  }

  const exportCaption = () => {
    const title = (options, value) => options.find(([v]) => v === value)[1];
    const tapCount = Array.isArray(selectedTaps) ? selectedTaps.length : null;
    const filterText = filtersText(filters, L4_SESSIONS_FILTER_FIELDS);
    const shown = coverageText();

    return [
      "Nzyme Topology, " + title(VIEWS, view) + ". Internal nodes: " + title(INTERNAL_LEVELS, internalLevel)
          + ". External nodes: " + title(EXTERNAL_LEVELS, externalLevel) + ".",
      (timeWindow
          ? "Time window " + formatWindowTime(timeWindow.from) + " to " + formatWindowTime(timeWindow.to)
          : timeRangeText(timeRange))
          + ". Exported " + moment().format("YYYY-MM-DD HH:mm") + ".",
      (tapCount === null ? "All taps" : formatCount(tapCount) + (tapCount === 1 ? " tap" : " taps")) + ". "
          + (filterText ? "Filters: " + filterText + "." : "No filters.")
          + (excludedNodes.length > 0 ? " Excluded: " + excludedNodes.map(excludedLabel).join(", ") + "." : "")
    ].concat(shown && shown.limited ? [shown.text] : []);
  }

  const nothingToExport = () => toast.info("There is nothing to export yet.");

  const exportImage = (format) => {
    const colors = colorsForMode(Boolean(Store.get("dark_mode")));
    const name = "topology-" + view;

    if (view === "graph") {
      const rendered = linkModel && graphExportRef.current ? graphExportRef.current(EXPORT_SCALE) : null;
      if (rendered) exportCanvasAsPng(rendered, exportCaption(), colors, name); else nothingToExport();
      return;
    }

    const svg = drawingRef.current ? drawingRef.current.querySelector("svg") : null;
    if (!svg) {
      nothingToExport();
    } else if (format === "svg") {
      exportSvg(svg, exportCaption(), colors, name);
    } else {
      exportSvgAsPng(svg, exportCaption(), colors, name,
          () => toast.error("The browser could not turn the drawing into an image."));
    }
  }

  const exportData = () => {
    const type = (node) => (node ? node.type.toLowerCase() : "");

    // A file does not say that it is not everything, so the export does.
    const shown = coverageText();
    if (shown && shown.limited) toast.info("The export is limited like the view. " + shown.text);

    if (view === "map") {
      if (!geoModel) return nothingToExport();

      const sites = new Map(geoModel.origins.map((o) => [o.id, o]));
      const points = new Map(geoModel.points.map((p) => [p.id, p]));
      const rows = geoModel.flows.slice().sort((a, b) => b.bytes - a.bytes);
      return exportCsv([
        ["site", (f) => (sites.has(f.originId) ? sites.get(f.originId).label : "")],
        ["place", (f) => (points.has(f.pointId) ? points.get(f.pointId).label : "")],
        ["country_code", (f) => (points.has(f.pointId) ? points.get(f.pointId).country_code : "")],
        ["direction", (f) => f.direction.toLowerCase()],
        ["sessions", (f) => f.sessions],
        ["bytes", (f) => f.bytes]
      ], rows, "topology-map-flows");
    }

    if (view === "flow") {
      if (!flows) return nothingToExport();

      const nodes = new Map(flows.nodes.map((n) => [n.id, n]));
      const services = new Map(flows.services.map((s) => [s.id, s]));
      const rows = applyTimeWindow(flows.links, flows.series, timeWindow)
          .filter((l) => l.active && nodes.has(l.source) && nodes.has(l.target))
          .sort((a, b) => (b.bytes_tx + b.bytes_rx) - (a.bytes_tx + a.bytes_rx));
      return exportCsv([
        ["source", (l) => nodeLabel(nodes.get(l.source))],
        ["source_type", (l) => type(nodes.get(l.source))],
        ["service", (l) => (services.has(l.service) ? serviceLabel(services.get(l.service)) : "")],
        ["destination", (l) => nodeLabel(nodes.get(l.target))],
        ["destination_type", (l) => type(nodes.get(l.target))],
        ["sessions", (l) => l.sessions],
        ["bytes_sent", (l) => l.bytes_tx],
        ["bytes_received", (l) => l.bytes_rx]
      ], rows, "topology-flows");
    }

    if (!linkModel) return nothingToExport();

    const node = (id) => linkModel.nodes.get(id);
    const name = (n) => (n.name ? n.name : (n.detail ? n.detail : ""));
    const rows = linkModel.links.filter((l) => l.active).sort((a, b) => b.bytes - a.bytes);
    return exportCsv([
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
    ], rows, "topology-connections");
  }

  const exportItems = () => {
    switch (view) {
      case "graph":
        return [
          {label: "Image (PNG)", onClick: () => exportImage("png")},
          {label: "Connections (CSV)", onClick: exportData}
        ];
      case "matrix":
        return [
          {label: "Image (PNG)", onClick: () => exportImage("png")},
          {label: "Image (SVG)", onClick: () => exportImage("svg")},
          {label: "Connections (CSV)", onClick: exportData}
        ];
      case "flow":
        return [
          {label: "Image (PNG)", onClick: () => exportImage("png")},
          {label: "Image (SVG)", onClick: () => exportImage("svg")},
          {label: "Flows (CSV)", onClick: exportData}
        ];
      default:
        return [{label: "Flows (CSV)", onClick: exportData}];
    }
  }

  const rankingProps = {
    request: request,
    revision: revision,
    timeRange: timeRange,
    onRefresh: refresh,
    pageFilters: filters,
    onLocate: locate,
    setFilters: setFilters,
    filterFields: L4_SESSIONS_FILTER_FIELDS
  };

  const shownOptions = viewOptions();

  return (
      <React.Fragment>
        <div className="row">
          <div className="col-md-12">
            <div className="card">
              <div className="card-body">
                <CardTitleWithControls title="Topology"
                                       timeRange={timeRange}
                                       setTimeRange={setTimeRange}
                                       helpLink="https://go.nzyme.org/ethernet-topology"
                                       refreshAction={refresh} />

                <Filters filters={filters}
                         setFilters={setFilters}
                         fields={L4_SESSIONS_FILTER_FIELDS}
                         onMonitorsReady={() => setFiltersReady(true)} />

                {excludedNodes.length > 0 ?
                    <React.Fragment>
                      <h5 className="applied-filters-headline mt-3">Excluded Nodes</h5>
                      <div className="applied-filters mt-1">
                        {excludedNodes.map((nodeId, i) => (
                            <React.Fragment key={nodeId}>
                              <div className="applied-filters-filtergroup">
                                <span className="applied-filter-chip">
                                  <span className="applied-filter-name">Node</span>
                                  <span className="applied-filter-operator">!=</span>
                                  <span className="applied-filter-value">{excludedLabel(nodeId)}</span>
                                  <a className="applied-filter-remove" title="Include this node again"
                                     onClick={(e) => { e.preventDefault(); include(nodeId); }}>
                                    <i className="fa-solid fa-xmark" />
                                  </a>
                                </span>
                              </div>
                              {i < excludedNodes.length - 1
                                  ? <div className="applied-filter-connector applied-filter-connector--and">AND</div> : null}
                            </React.Fragment>
                        ))}
                      </div>
                    </React.Fragment> : null}
              </div>
            </div>
          </div>
        </div>

        <TopologySummaryStrip summary={summary} timeline={timeline} />

        {filtersReady ?
            <React.Fragment>
              <div className="row mt-3" ref={viewRef}>
                <div className="col-md-12">
                  <div className="card">
                    <div className="card-body">
                      <CardTitleWithControls title="Connections"
                                             timeRange={timeRange}
                                             helpLink="https://go.nzyme.org/ethernet-topology-views"
                                             refreshAction={refresh} />

                      <div className="d-flex flex-wrap align-items-center">
                        {shownOptions.data.map(optionSelect)}
                        <div className="ms-auto mb-2 d-flex align-items-center">
                          {shownOptions.display.length > 0
                              ? <div className="me-2"><VizDisplayMenu options={shownOptions.display} /></div> : null}
                          <div className="me-2">
                            <VizExportMenu items={exportItems()}
                                           note={view === "map" ? "The map cannot be exported as an image." : null} />
                          </div>
                          <div className="btn-group btn-group-sm" role="group" aria-label="View">
                            {VIEWS.map(([key, title]) => (
                                <button key={key} type="button"
                                        className={"btn " + (view === key ? "btn-secondary" : "btn-outline-secondary")}
                                        onClick={() => setUrlOptions({view: key})}>
                                  {title}
                                </button>
                            ))}
                          </div>
                        </div>
                      </div>

                      <div className="row">
                        <div className="col-md-9">
                          <div ref={drawingRef}>{visualization()}</div>
                          {coverage()}
                        </div>
                        <div className="col-md-3">
                          <TopologySelectionPanel selection={selection}
                                                  view={view}
                                                  linkModel={linkModel}
                                                  flowModel={flowModel}
                                                  geoModel={geoModel}
                                                  request={request}
                                                  timeRange={timeRange}
                                                  timeWindow={timeWindow}
                                                  pageFilters={filters}
                                                  setFilters={setFilters}
                                                  filterFields={L4_SESSIONS_FILTER_FIELDS}
                                                  onSelect={selectFromPanel}
                                                  onAddFilter={addFilter}
                                                  onExclude={exclude}
                                                  height={VIEW_HEIGHT} />
                        </div>
                      </div>

                      <div className="mt-3">
                        {timeline
                            ? <TopologyTimeScrubber timeline={timeline}
                                                    timeWindow={timeWindow}
                                                    setTimeWindow={setTimeWindow}
                                                    onApply={(w) => applyTimeRange(Absolute(new Date(w.from), new Date(w.to)))} />
                            : <GenericWidgetLoadingSpinner height={130} />}
                      </div>
                    </div>
                  </div>
                </div>
              </div>

              <div className="row mt-3">
                <div className="col-12">
                  <TopologyRankingCard title="Services" levels={SERVICE_RANKINGS} {...rankingProps} />
                </div>
              </div>

              <div className="row mt-3">
                <div className="col-12">
                  <TopologyRankingCard title="Rare and New External Nodes"
                                       help="Shows external nodes with few internal peers or a recent first appearance."
                                       levels={EXTERNAL_RANKINGS}
                                       presets={RARE_PRESETS}
                                       {...rankingProps} />
                </div>
              </div>
            </React.Fragment> : null}
      </React.Fragment>
  )

}
