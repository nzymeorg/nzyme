import React, {useCallback, useContext, useMemo, useRef, useState} from "react";
import {toast} from "react-toastify";
import {TapContext} from "../../../App";
import useSelectedTenant from "../../system/tenantselector/useSelectedTenant";
import CardTitleWithControls from "../../shared/CardTitleWithControls";
import useUrlParams, {valueIn} from "../../shared/useUrlParams";
import {withFilter} from "../../shared/filtering/FilterQueryParameters";
import VizDisplayMenu from "../../shared/viz/VizDisplayMenu";
import VizExportMenu from "../../shared/viz/VizExportMenu";
import VizOptionSelect from "../../shared/viz/VizOptionSelect";
import {timeRangeText} from "../../shared/viz/VizExport";
import GenericWidgetLoadingSpinner from "../../widgets/GenericWidgetLoadingSpinner";
import TopologyService from "../../../services/ethernet/TopologyService";
import {formatCount} from "../assets/details/AssetProfileHelpers";
import TopologyGraph from "../topology/TopologyGraph";
import TopologyMatrix from "../topology/TopologyMatrix";
import TopologyFlow from "../topology/TopologyFlow";
import TopologyMap from "../topology/TopologyMap";
import TopologyCoverageNote from "../topology/TopologyCoverageNote";
import useTopologyRequest from "../topology/useTopologyRequest";
import {
  exportCaptionLines,
  EXPORTS,
  exportViewData,
  exportViewImage,
  MAP_EXPORT_NOTE
} from "../topology/TopologyExport";
import {
  ARRANGEMENTS,
  buildFlowModel,
  buildGeoModel,
  buildLinkModel,
  buildNodeScale,
  coverageNoun,
  coverageOf,
  EXTERNAL_LEVELS,
  FLOW_LIMIT,
  GEO_LIMIT,
  hiddenLinksText,
  INTERNAL_LEVELS,
  nodeFilter,
  PLACES,
  SERVICE_LEVELS,
  topologyLink,
  viewOptions,
  VIEWS
} from "../topology/TopologyData";
import {L4_SESSIONS_FILTER_FIELDS} from "./L4SessionFilterFields";

const topologyService = new TopologyService();

const HEIGHT = 460;
const CARD_LINK_LIMITS = [25, 50, 100, 250];

const URL_OPTIONS = {
  topology: null,
  topology_view: "graph",
  topology_internal: "HOST",
  // No fixed default, like on the topology page: the views group external nodes differently.
  topology_external: null,
  topology_service: "PROTOCOL",
  topology_links: "25",
  topology_arrange: "force",
  topology_places: "city"
};

export default function L4SessionsTopologyCard({filters, setFilters, timeRange, revision, onRefresh}) {

  const [organizationId, tenantId] = useSelectedTenant();
  const selectedTaps = useContext(TapContext).taps;

  const [urlOptions, setUrlOptions] = useUrlParams(URL_OPTIONS);

  const built = urlOptions.topology === "1";
  const view = valueIn(VIEWS, urlOptions.topology_view, "graph");
  const internalLevel = valueIn(INTERNAL_LEVELS, urlOptions.topology_internal, "HOST");
  const externalLevel = valueIn(EXTERNAL_LEVELS, urlOptions.topology_external, view === "graph" ? "HOST" : "NETWORK");
  const serviceLevel = valueIn(SERVICE_LEVELS, urlOptions.topology_service, "PROTOCOL");
  const linkLimit = valueIn(CARD_LINK_LIMITS.map((l) => [l]), urlOptions.topology_links, "25");
  const arrangement = valueIn(ARRANGEMENTS, urlOptions.topology_arrange, "force");
  const places = valueIn(PLACES, urlOptions.topology_places, "city");

  const [minBytes, setMinBytes] = useState(0);
  const [sizeBy, setSizeBy] = useState("bytes");
  const [labels, setLabels] = useState("auto");
  const [matrixOrder, setMatrixOrder] = useState("traffic");
  const [flowEntries, setFlowEntries] = useState(12);
  const [builds, setBuilds] = useState(0);

  const drawingRef = useRef(null);
  const graphExportRef = useRef(null);

  const request = useMemo(() => ({
    organizationId: organizationId,
    tenantId: tenantId,
    taps: selectedTaps,
    timeRange: timeRange,
    filters: filters
  }), [organizationId, tenantId, selectedTaps, timeRange, filters]);

  const requestKey = JSON.stringify([request, revision, builds]);

  const links = useTopologyRequest(
      JSON.stringify([requestKey, internalLevel, externalLevel, linkLimit]),
      built && (view === "graph" || view === "matrix"),
      (setData) => topologyService.getLinks(request, internalLevel, externalLevel, null, linkLimit, setData));
  const flows = useTopologyRequest(
      JSON.stringify([requestKey, internalLevel, externalLevel, serviceLevel]),
      built && view === "flow",
      (setData) => topologyService.getLinks(request, internalLevel, externalLevel, serviceLevel, FLOW_LIMIT, setData));
  const geo = useTopologyRequest(requestKey, built && view === "map",
      (setData) => topologyService.getGeoFlows(request, GEO_LIMIT, setData));

  const linkModel = useMemo(() => (links ? buildLinkModel(links, null) : null), [links]);
  const nodeScale = useMemo(() => (links ? buildNodeScale(links, null) : null), [links]);
  const flowModel = useMemo(() => (flows ? buildFlowModel(flows, null, flowEntries) : null), [flows, flowEntries]);
  const geoModel = useMemo(() => (geo ? buildGeoModel(geo, null, places) : null), [geo, places]);

  const filterForNode = useCallback((node) => {
    const added = withFilter(filters, nodeFilter(node), L4_SESSIONS_FILTER_FIELDS);
    if (added) setFilters(added);
  }, [filters, setFilters]);

  const linkData = view === "flow" ? flows : links;

  const options = viewOptions("l4-topology", view, {
    internal: {value: internalLevel, onChange: (v) => setUrlOptions({topology_internal: v})},
    external: {value: externalLevel, onChange: (v) => setUrlOptions({topology_external: v})},
    limit: {value: linkLimit, onChange: (v) => setUrlOptions({topology_links: v}),
      options: CARD_LINK_LIMITS.map((l) => [String(l), "Top " + formatCount(l)]),
      after: linkData ? formatCount(linkData.total_links) + " in total" : null},
    service: {value: serviceLevel, onChange: (v) => setUrlOptions({topology_service: v})},
    places: {value: places, onChange: (v) => setUrlOptions({topology_places: v})},
    arrange: {value: arrangement, onChange: (v) => setUrlOptions({topology_arrange: v})},
    size: {value: sizeBy, onChange: setSizeBy},
    labels: {value: labels, onChange: setLabels},
    hide: {value: minBytes, onChange: setMinBytes},
    order: {value: matrixOrder, onChange: setMatrixOrder},
    entries: {value: flowEntries, onChange: setFlowEntries}
  });

  const viewSelect = {id: "l4-topology-view", label: "View", value: view,
    onChange: (v) => setUrlOptions({topology_view: v}), options: VIEWS};

  const coverageText = () => {
    const data = view === "map" ? geo : linkData;
    if (!data) return null;

    const shown = coverageOf(view === "map" ? data.flows : data.links,
        view === "map" ? data.total_flows : data.total_links, data.total_bytes, coverageNoun(view));

    return shown
        ? {text: shown.text + (view === "graph" ? hiddenLinksText(minBytes) : ""), limited: shown.limited}
        : null;
  }

  const exportCaption = () => {
    const shown = coverageText();

    return exportCaptionLines("Nzyme TCP/UDP Sessions, Topology " + VIEWS.find(([v]) => v === view)[1], internalLevel,
        externalLevel, timeRangeText(timeRange), selectedTaps, filters).concat(shown && shown.limited ? [shown.text] : []);
  }

  const nothingToExport = () => toast.info("There is nothing to export yet.");

  const exportImage = (format) => {
    const renderGraph = linkModel ? graphExportRef.current : null;
    if (!exportViewImage(view, format, renderGraph, drawingRef.current, exportCaption(), "sessions-topology-" + view)) {
      nothingToExport();
    }
  }

  const exportData = () => {
    const shown = coverageText();
    if (shown && shown.limited) toast.info("The export is limited like the view. " + shown.text);

    if (!exportViewData(view, {linkModel, flows, geoModel, timeWindow: null}, "sessions-topology")) {
      nothingToExport();
    }
  }

  const coverage = () => {
    const shown = coverageText();
    const adjustable = shown && shown.limited && (view === "graph" || view === "matrix");
    const next = adjustable ? CARD_LINK_LIMITS.find((l) => l > parseInt(linkLimit, 10)) : undefined;

    return <TopologyCoverageNote shown={shown} next={next}
                                 onShowMore={(limit) => setUrlOptions({topology_links: String(limit)})} />;
  }

  const loading = () => (
      <div className="border rounded" style={{height: HEIGHT + "px"}}>
        <GenericWidgetLoadingSpinner height={HEIGHT - 2} />
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
                             onFilter={filterForNode}
                             height={HEIGHT}
                             exportRef={graphExportRef} />
            : loading();
      case "matrix":
        return linkModel
            ? <TopologyMatrix model={linkModel} order={matrixOrder} height={HEIGHT} />
            : loading();
      case "flow":
        return flowModel
            ? <TopologyFlow model={flowModel} height={HEIGHT} />
            : loading();
      default:
        return geoModel
            ? <TopologyMap model={geoModel} whole={geoModel} selection={null} onSelect={() => {}} focus={null} height={HEIGHT} />
            : loading();
    }
  }

  const pageOptions = {view: view, internal: internalLevel, service: serviceLevel, arrange: arrangement, places: places};
  // Only a grouping that was picked by hand: the topology page has a default per view, too.
  if (urlOptions.topology_external !== null) {
    pageOptions.external = externalLevel;
  }

  return (
      <div className="card">
        <div className="card-body">
          <CardTitleWithControls title="Topology"
                                 timeRange={timeRange}
                                 internalLink={topologyLink(filters, timeRange, pageOptions)}
                                 helpLink="https://go.nzyme.org/ethernet-topology-sessions"
                                 refreshAction={onRefresh} />

          {built ?
              <React.Fragment>
                <div className="d-flex flex-wrap align-items-center">
                  {[viewSelect].concat(options.data).map((o) => <VizOptionSelect key={o.id} option={o} />)}
                  <div className="ms-auto mb-2 d-flex align-items-center">
                    {options.display.length > 0
                        ? <div className="me-2"><VizDisplayMenu options={options.display} /></div> : null}
                    <div className="me-2">
                      <VizExportMenu items={EXPORTS[view].map(([label, format]) => (
                                         {label: label, onClick: () => (format ? exportImage(format) : exportData())}
                                     ))}
                                     note={view === "map" ? MAP_EXPORT_NOTE : null} />
                    </div>
                    <button type="button" className="btn btn-sm btn-outline-secondary"
                            onClick={() => setUrlOptions({topology: null})}>
                      Hide
                    </button>
                  </div>
                </div>

                <div ref={drawingRef}>{visualization()}</div>
                {coverage()}
              </React.Fragment>
              :
              <React.Fragment>
                <p className="help-text">The topology is built on request from the sessions that match the filters.</p>

                <button type="button" className="btn btn-sm btn-primary"
                        onClick={() => { setBuilds((b) => b + 1); setUrlOptions({topology: "1"}); }}>
                  Build Topology
                </button>
              </React.Fragment>}
        </div>
      </div>
  )

}
