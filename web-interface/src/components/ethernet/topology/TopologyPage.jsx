import React, {useCallback, useContext, useEffect, useMemo, useRef, useState} from "react";
import moment from "moment";
import {toast} from "react-toastify";
import {TapContext} from "../../../App";
import {disableTapSelector, enableTapSelector} from "../../misc/TapSelector";
import usePageTitle from "../../../util/UsePageTitle";
import useSelectedTenant from "../../system/tenantselector/useSelectedTenant";
import CardTitleWithControls from "../../shared/CardTitleWithControls";
import Filters from "../../shared/filtering/Filters";
import useUrlFilters from "../../shared/filtering/useUrlFilters";
import {withFilter} from "../../shared/filtering/FilterQueryParameters";
import useUrlParams, {valueIn} from "../../shared/useUrlParams";
import {Absolute, Presets, Relative} from "../../shared/timerange/TimeRange";
import {useUrlTimeRange} from "../../shared/timerange/TimeRangeSelector";
import {useApplyTimeRange} from "../../shared/timerange/TimeRangeUrl";
import GenericWidgetLoadingSpinner from "../../widgets/GenericWidgetLoadingSpinner";
import VizDisplayMenu from "../../shared/viz/VizDisplayMenu";
import VizOptionSelect from "../../shared/viz/VizOptionSelect";
import VizExportMenu from "../../shared/viz/VizExportMenu";
import {timeRangeText} from "../../shared/viz/VizExport";
import {L4_SESSIONS_FILTER_FIELDS, L4_SESSIONS_QUICK_FILTERS} from "../l4/L4SessionFilterFields";
import TopologyService from "../../../services/ethernet/TopologyService";
import {formatBytes, formatCount} from "../assets/details/AssetProfileHelpers";
import useTopologyRequest from "./useTopologyRequest";
import {
  exportCaptionLines,
  EXPORTS,
  exportViewData,
  exportViewImage,
  MAP_EXPORT_NOTE
} from "./TopologyExport";
import useTopologyLive from "./useTopologyLive";
import {LIVE_SESSION_LIMIT, LIVE_WINDOW_MS} from "./TopologyLive";
import TopologyGraph from "./TopologyGraph";
import TopologyMatrix from "./TopologyMatrix";
import TopologyFlow from "./TopologyFlow";
import TopologyMap from "./TopologyMap";
import TopologyTimeScrubber from "./TopologyTimeScrubber";
import TopologySelectionPanel from "./TopologySelectionPanel";
import TopologyRankingCard from "./TopologyRankingCard";
import TopologySummaryStrip from "./TopologySummaryStrip";
import TopologyCoverageNote from "./TopologyCoverageNote";
import {
  ARRANGEMENTS,
  buildFlowModel,
  buildGeoModel,
  buildLinkModel,
  buildNodeScale,
  coverageNoun,
  coverageOf,
  describeNodeId,
  EXTERNAL_LEVELS,
  FLOW_LIMIT,
  formatWindowTime,
  GEO_LIMIT,
  hiddenLinksText,
  INTERNAL_LEVELS,
  LINK_LIMITS,
  nodeFilter,
  PLACES,
  SERVICE_LEVELS,
  viewOptions,
  VIEWS,
  windowWidth
} from "./TopologyData";

const topologyService = new TopologyService();

const VIEW_HEIGHT = 580;

const URL_OPTIONS = {
  view: "graph",
  exclude: null,
  internal: "HOST",
  // No fixed default: one that is selected by hand must stay in the URL to hold in every view.
  external: null,
  service: "PROTOCOL",
  links: "250",
  arrange: "force",
  places: "city",
  live: null
};

const LIVE_VIEWS = ["graph", "map"];
const LIVE_SECONDS = LIVE_WINDOW_MS / 1000;
const LIVE_RANGE = Relative(LIVE_WINDOW_MS / 60000, "Live");

const MAX_EXCLUDED_NODES = 25;

const EXTERNAL_RANKINGS = [["EXTERNAL_NETWORK", "Autonomous systems"], ["EXTERNAL_COUNTRY", "Countries"], ["EXTERNAL_HOST", "Addresses"]];
const SERVICE_RANKINGS = [["SERVICE_PROTOCOL", "Protocols"], ["SERVICE_PORT", "Ports"]];
const RARE_PRESETS = [
  ["rare", "Fewest peers", "PEERS", "ASC"],
  ["new", "Most recently first seen", "FIRST_SEEN", "DESC"]
];

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

  const live = urlOptions.live === "1";
  const selectedView = valueIn(VIEWS, urlOptions.view, "graph");
  const view = live && !LIVE_VIEWS.includes(selectedView) ? "graph" : selectedView;
  const internalLevel = valueIn(INTERNAL_LEVELS, urlOptions.internal, "HOST");
  const externalLevel = valueIn(EXTERNAL_LEVELS, urlOptions.external, view === "graph" ? "HOST" : "NETWORK");
  const serviceLevel = valueIn(SERVICE_LEVELS, urlOptions.service, "PROTOCOL");
  const linkLimit = valueIn(LINK_LIMITS.map((l) => [l]), urlOptions.links, "250");
  const arrangement = valueIn(ARRANGEMENTS, urlOptions.arrange, "force");
  const places = valueIn(PLACES, urlOptions.places, "city");
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

  const ranged = filtersReady && !live;
  const summary = useTopologyRequest(requestKey, ranged,
      (setData) => topologyService.getSummary(request, setData));
  const timeline = useTopologyRequest(requestKey, ranged,
      (setData) => topologyService.getTimeline(request, setData));
  const rangedLinks = useTopologyRequest(
      JSON.stringify([requestKey, internalLevel, externalLevel, linkLimit]),
      ranged && (view === "graph" || view === "matrix"),
      (setData) => topologyService.getLinks(request, internalLevel, externalLevel, null, linkLimit, setData));
  const flows = useTopologyRequest(
      JSON.stringify([requestKey, internalLevel, externalLevel, serviceLevel]),
      ranged && view === "flow",
      (setData) => topologyService.getLinks(request, internalLevel, externalLevel, serviceLevel, FLOW_LIMIT, setData));
  const rangedGeo = useTopologyRequest(requestKey, ranged && view === "map",
      (setData) => topologyService.getGeoFlows(request, GEO_LIMIT, setData));

  const liveKey = JSON.stringify([organizationId, tenantId, selectedTaps, filters, excludedNodes, internalLevel,
    externalLevel, revision]);
  const liveFrame = useTopologyLive(liveKey, live && filtersReady,
      (seconds, setData, onError) => topologyService.getLive(request, internalLevel, externalLevel, seconds,
          LIVE_SESSION_LIMIT, setData, onError),
      {limit: parseInt(linkLimit, 10), geo: view === "map", flowLimit: GEO_LIMIT});

  const links = live ? (liveFrame ? liveFrame.links : null) : rangedLinks;
  const geo = live ? (liveFrame ? liveFrame.geo : null) : rangedGeo;

  // The window and the selection belong to the data they were made on.
  useEffect(() => {
    setTimeWindow(null);
  }, [requestKey]);

  useEffect(() => {
    setSelection(null);
    setFocus(null);
  }, [requestKey, view, internalLevel, externalLevel, live]);

  const linkData = view === "flow" ? flows : links;
  const linkModel = useMemo(() => (linkData ? buildLinkModel(linkData, timeWindow) : null), [linkData, timeWindow]);
  // Depends on how wide the window is, not on where it is, so it holds while the window moves.
  const nodeScaleWidth = linkData ? windowWidth(linkData.series, timeWindow) : null;
  const rangedScale = useMemo(() => (linkData && !live ? buildNodeScale(linkData, nodeScaleWidth) : null),
      [linkData, nodeScaleWidth, live]);
  const nodeScale = live ? (liveFrame ? liveFrame.scale : null) : rangedScale;
  const flowModel = useMemo(() => (flows ? buildFlowModel(flows, timeWindow, flowEntries) : null),
      [flows, timeWindow, flowEntries]);
  const geoWhole = useMemo(() => (geo ? buildGeoModel(geo, null, places) : null), [geo, places]);
  const geoModel = useMemo(() => (geo && timeWindow ? buildGeoModel(geo, timeWindow, places) : geoWhole),
      [geo, timeWindow, places, geoWhole]);

  const addFilter = useCallback((filter) => {
    const added = withFilter(filters, filter, L4_SESSIONS_FILTER_FIELDS);
    if (added) setFilters(added);
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

  const locate = useCallback((node) => {
    if (viewRef.current) {
      viewRef.current.scrollIntoView({behavior: "smooth", block: "nearest"});
    }
    // The node only exists in a view that groups its side the way the ranking does.
    const options = node.type === "EXTERNAL" ? {external: node.level} : {internal: node.level};
    if (view === "map" || view === "flow") {
      options.view = "graph";
    }
    setUrlOptions(options);
    setPendingLocate(node.id);
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

  const toggleLive = () => {
    setTimeWindow(null);
    setUrlOptions({live: live ? null : "1"});
  }

  const selectFromPanel = useCallback((s) => {
    setSelection(s);
    if (s && (s.kind === "site" || s.kind === "place")) {
      setFocus({id: s.id, at: Date.now()});
    }
  }, []);

  const viewSelect = {id: "topology-view", label: "View", value: view, onChange: (v) => setUrlOptions({view: v}),
    options: VIEWS.map(([key, title]) => [key, title, live && !LIVE_VIEWS.includes(key)])};

  const shownOptions = viewOptions("topology", view, {
    internal: {value: internalLevel, onChange: (v) => setUrlOptions({internal: v})},
    external: {value: externalLevel, onChange: (v) => setUrlOptions({external: v})},
    limit: {value: linkLimit, onChange: (v) => setUrlOptions({links: v}),
      options: LINK_LIMITS.map((l) => [String(l), "Top " + formatCount(l)]),
      after: linkData ? formatCount(linkData.total_links) + " in total" : null},
    service: {value: serviceLevel, onChange: (v) => setUrlOptions({service: v})},
    places: {value: places, onChange: (v) => setUrlOptions({places: v})},
    arrange: {value: arrangement, onChange: (v) => setUrlOptions({arrange: v})},
    size: {value: sizeBy, onChange: setSizeBy},
    labels: {value: labels, onChange: setLabels},
    hide: {value: minBytes, onChange: setMinBytes},
    order: {value: matrixOrder, onChange: setMatrixOrder},
    entries: {value: flowEntries, onChange: setFlowEntries}
  });

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
                             dataKey={live ? liveKey : links}
                             minBytes={minBytes}
                             arrangement={arrangement}
                             sizeBy={sizeBy}
                             labels={labels}
                             selectedId={selection && (selection.kind === "node" || selection.kind === "link") ? selection.id : null}
                             onSelect={live ? null : setSelection}
                             onFilter={filterForNode}
                             focus={focus}
                             height={VIEW_HEIGHT}
                             exportRef={graphExportRef}
                             live={live}
                             pulses={live && liveFrame ? liveFrame.pulses : null} />
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
            ? <TopologyMap model={geoModel} whole={geoWhole} selection={selection} onSelect={live ? () => {} : setSelection} focus={focus} height={VIEW_HEIGHT} live={live} />
            : loading();
    }
  }

  // What of the result the view shows, as {text, limited}, or null if there is nothing.
  const coverageText = () => {
    const data = view === "map" ? geo : linkData;
    if (!data) return null;

    const items = view === "map" ? data.flows : data.links;
    const total = view === "map" ? data.total_flows : data.total_links;
    const what = coverageNoun(view);

    let hidden = view === "graph" ? hiddenLinksText(minBytes) : "";
    let when = "";

    if (live) {
      when = " the " + LIVE_SECONDS + " seconds up to " + moment(liveFrame.playhead).format("HH:mm:ss");
      if (liveFrame.limited) {
        hidden += " Only the " + formatCount(liveFrame.sessionLimit) + " most recently active sessions are loaded.";
      }
      if (liveFrame.stale) hidden += " New data is not arriving.";

      if (total === 0) return {text: "No " + what(0) + " in" + when + "." + hidden, limited: false};
      when = " of" + when;
    }

    const shown = coverageOf(items, total, data.total_bytes, what, when);
    return shown ? {text: shown.text + hidden, limited: shown.limited} : null;
  }

  const coverage = () => {
    const shown = coverageText();
    const adjustable = shown && shown.limited && (view === "graph" || view === "matrix");
    const next = adjustable ? LINK_LIMITS.find((l) => l > parseInt(linkLimit, 10)) : undefined;

    return <TopologyCoverageNote shown={shown} next={next} onShowMore={(limit) => setUrlOptions({links: String(limit)})} />;
  }

  const exportCaption = () => {
    const shown = coverageText();
    const when = live ? "Live" : (timeWindow
        ? "Time window " + formatWindowTime(timeWindow.from) + " to " + formatWindowTime(timeWindow.to)
        : timeRangeText(timeRange));

    const lines = exportCaptionLines("Nzyme Topology, " + VIEWS.find(([v]) => v === view)[1], internalLevel,
        externalLevel, when, selectedTaps, filters);
    if (excludedNodes.length > 0) {
      lines[2] += " Excluded: " + excludedNodes.map(excludedLabel).join(", ") + ".";
    }

    return lines.concat(shown && (shown.limited || live) ? [shown.text] : []);
  }

  const nothingToExport = () => toast.info("There is nothing to export yet.");

  const exportImage = (format) => {
    const renderGraph = linkModel ? graphExportRef.current : null;
    if (!exportViewImage(view, format, renderGraph, drawingRef.current, exportCaption(), "topology-" + view)) {
      nothingToExport();
    }
  }

  const exportData = () => {
    // A file does not say that it is not everything, so the export does.
    const shown = coverageText();
    if (shown && shown.limited) toast.info("The export is limited like the view. " + shown.text);

    if (!exportViewData(view, {linkModel, flows, geoModel, timeWindow}, "topology")) {
      nothingToExport();
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

  const totals = timeline ? timeline.totals : null;
  const summaryFacts = live
      ? [
        ["Internal Nodes", liveFrame ? formatCount(liveFrame.summary.internal) : null],
        ["External Nodes", liveFrame ? formatCount(liveFrame.summary.external) : null],
        ["Connections", liveFrame ? formatCount(liveFrame.summary.connections) : null],
        ["Sessions", liveFrame ? formatCount(liveFrame.summary.sessions) : null],
        ["Traffic", liveFrame ? formatBytes(liveFrame.summary.bytes) : null]
      ]
      : [
        ["Internal Addresses", summary ? formatCount(summary.internal_hosts) : null],
        ["External Addresses", summary ? formatCount(summary.external_hosts) : null],
        ["Autonomous Systems", summary ? formatCount(summary.external_networks) : null],
        ["Countries", summary ? formatCount(summary.external_countries) : null],
        ["Sessions", totals ? formatCount(totals.sessions_internal + totals.sessions_outbound
            + totals.sessions_inbound + totals.sessions_external) : null],
        ["Traffic", totals ? formatBytes(totals.bytes_internal + totals.bytes_outbound + totals.bytes_inbound
            + totals.bytes_external) : null]
      ];

  return (
      <React.Fragment>
        <div className="row">
          <div className="col-md-12">
            <div className="card">
              <div className="card-body">
                <CardTitleWithControls title="Topology"
                                       timeRange={live ? LIVE_RANGE : timeRange}
                                       setTimeRange={live ? undefined : setTimeRange}
                                       helpLink="https://go.nzyme.org/ethernet-topology"
                                       refreshAction={refresh} />

                <Filters filters={filters}
                         setFilters={setFilters}
                         fields={L4_SESSIONS_FILTER_FIELDS}
                         quickFilters={L4_SESSIONS_QUICK_FILTERS}
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

        <TopologySummaryStrip facts={summaryFacts} />

        {filtersReady ?
            <React.Fragment>
              <div className="row mt-3" ref={viewRef}>
                <div className="col-md-12">
                  <div className="card">
                    <div className="card-body">
                      <CardTitleWithControls title="Connections"
                                             timeRange={live ? LIVE_RANGE : timeRange}
                                             helpLink={"https://go.nzyme.org/ethernet-topology-" + (live ? "live" : "views")}
                                             refreshAction={refresh} />

                      <div className="d-flex flex-wrap align-items-center">
                        {[viewSelect].concat(shownOptions.data).map((o) => <VizOptionSelect key={o.id} option={o} />)}
                        <div className="ms-auto mb-2 d-flex align-items-center">
                          <button type="button"
                                  className={"btn btn-sm me-2 " + (live ? "btn-secondary" : "btn-outline-secondary")}
                                  title={"Follows the last " + LIVE_SECONDS + " seconds instead of the time range"}
                                  onClick={toggleLive}>
                            Live
                          </button>
                          {shownOptions.display.length > 0
                              ? <div className="me-2"><VizDisplayMenu options={shownOptions.display} /></div> : null}
                          <VizExportMenu items={EXPORTS[view].map(([label, format]) => (
                                             {label: label, onClick: () => (format ? exportImage(format) : exportData())}
                                         ))}
                                         note={view === "map" ? MAP_EXPORT_NOTE : null} />
                        </div>
                      </div>

                      <div className="row">
                        <div className={live ? "col-md-12" : "col-md-9"}>
                          <div ref={drawingRef}>{visualization()}</div>
                          {coverage()}
                        </div>
                        {live ? null :
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
                            </div>}
                      </div>

                      {live ? null :
                          <div className="mt-3">
                            {timeline
                                ? <TopologyTimeScrubber timeline={timeline}
                                                        timeWindow={timeWindow}
                                                        setTimeWindow={setTimeWindow}
                                                        onApply={(w) => applyTimeRange(Absolute(new Date(w.from), new Date(w.to)))} />
                                : <GenericWidgetLoadingSpinner height={130} />}
                          </div>}
                    </div>
                  </div>
                </div>
              </div>

              {live ? null :
                  <React.Fragment>
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
                  </React.Fragment>}
            </React.Fragment> : null}
      </React.Fragment>
  )

}
