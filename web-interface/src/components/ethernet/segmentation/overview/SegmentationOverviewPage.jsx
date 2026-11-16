import React, {useCallback, useContext, useEffect, useMemo, useRef, useState} from "react";
import {Link} from "react-router-dom";
import {toast} from "react-toastify";
import Store from "../../../../util/Store";
import ApiRoutes from "../../../../util/ApiRoutes";
import usePageTitle from "../../../../util/UsePageTitle";
import {userHasPermission} from "../../../../util/Tools";
import {UserContext} from "../../../../App";
import useSelectedTenant from "../../../system/tenantselector/useSelectedTenant";
import SectionMenuBar from "../../../shared/SectionMenuBar";
import CardTitleWithControls from "../../../shared/CardTitleWithControls";
import Filters from "../../../shared/filtering/Filters";
import useUrlParams from "../../../shared/useUrlParams";
import {Presets} from "../../../shared/timerange/TimeRange";
import {useUrlTimeRange} from "../../../shared/timerange/TimeRangeSelector";
import GenericWidgetLoadingSpinner from "../../../widgets/GenericWidgetLoadingSpinner";
import SegmentationService from "../../../../services/ethernet/SegmentationService";
import {formatCount} from "../../assets/details/AssetProfileHelpers";
import useTopologyRequest from "../../topology/useTopologyRequest";
import {SEGMENTATION_MENU_ITEMS} from "../SegmentationMenuItems";
import SegmentationStatusStrip from "./SegmentationStatusStrip";
import SegmentationMatrix from "./SegmentationMatrix";
import SegmentationBoundaryMap from "./SegmentationBoundaryMap";
import SegmentationSelectionPanel from "./SegmentationSelectionPanel";
import SegmentationFlowsTable from "./SegmentationFlowsTable";
import ExcludeDestinationDialog from "./ExcludeDestinationDialog";
import {SEGMENTATION_FLOWS_FILTER_FIELDS} from "./SegmentationFlowsFilterFields";
import SegmentationAddressesTable from "./SegmentationAddressesTable";
import {SEGMENTATION_ADDRESSES_FILTER_FIELDS} from "./SegmentationAddressesFilterFields";
import SegmentationLegend from "./SegmentationLegend";
import {
  buildOverviewModel,
  cellId,
  crossingTitle,
  filterOverview,
  hasAddresses,
  SHOW_OPTIONS
} from "./SegmentationOverviewData";
import {overviewColors} from "./SegmentationOverviewTheme";

const segmentationService = new SegmentationService();

const VIEW_HEIGHT = 580;
const FLOWS_PER_PAGE = 25;
const ADDRESSES_PER_PAGE = 25;

// On the whole screen, the view takes the height that the controls above it and the legend below it leave.
const FULLSCREEN_CHROME = 170;
const MIN_FULLSCREEN_HEIGHT = 360;

const canFullscreen = () => typeof document.documentElement.requestFullscreen === "function";

const VIEWS = [["map", "Map"], ["matrix", "Matrix"]];
const DEFAULT_VIEW = "map";

const URL_OPTIONS = {
  view: DEFAULT_VIEW,
  show: "all",
  external: "1",
  broadcast: "1"
};

const valueIn = (options, value, fallback) => (options.some(([v]) => String(v) === value) ? value : fallback);

export default function SegmentationOverviewPage() {

  usePageTitle("Ethernet Segmentation");

  const [organizationId, tenantId] = useSelectedTenant();
  const user = useContext(UserContext);
  const canManage = userHasPermission(user, "ethernet_monitoring_manage");

  const [timeRange, setTimeRange] = useUrlTimeRange(Presets.RELATIVE_HOURS_24);
  const [urlOptions, setUrlOptions] = useUrlParams(URL_OPTIONS);
  const [revision, setRevision] = useState(0);
  const [selection, setSelection] = useState(null);

  const fullscreenRef = useRef(null);
  const [fullscreen, setFullscreen] = useState(false);
  const [viewHeight, setViewHeight] = useState(VIEW_HEIGHT);
  const [viewReset, setViewReset] = useState(0);

  useEffect(() => {
    // The screen can also be left with Escape, so the state follows the browser and not the button.
    const onChange = () => {
      setFullscreen(fullscreenRef.current !== null && document.fullscreenElement === fullscreenRef.current);
      setViewReset((count) => count + 1);
    };

    document.addEventListener("fullscreenchange", onChange);
    return () => document.removeEventListener("fullscreenchange", onChange);
  }, []);

  useEffect(() => {
    if (!fullscreen) {
      setViewHeight(VIEW_HEIGHT);
      return undefined;
    }

    const fit = () => setViewHeight(Math.max(MIN_FULLSCREEN_HEIGHT, window.innerHeight - FULLSCREEN_CHROME));
    fit();

    window.addEventListener("resize", fit);
    return () => window.removeEventListener("resize", fit);
  }, [fullscreen]);

  const toggleFullscreen = () => {
    if (document.fullscreenElement) {
      document.exitFullscreen();
    } else if (fullscreenRef.current) {
      // Refused where the browser does not allow it. The view then stays as it is.
      fullscreenRef.current.requestFullscreen().catch(() => {});
    }
  }

  // The filters of the flows belong to the selected crossing and not to the page, so they are not kept in the URL.
  const [flowsFilters, setFlowsFilters] = useState({});
  const [flowsOrderColumn, setFlowsOrderColumn] = useState("sessions");
  const [flowsOrderDirection, setFlowsOrderDirection] = useState("DESC");
  const [flowsPage, setFlowsPage] = useState(1);
  const [excluding, setExcluding] = useState(null);

  const [addressesFilters, setAddressesFilters] = useState({});
  const [addressesOrderColumn, setAddressesOrderColumn] = useState("sessions_to");
  const [addressesOrderDirection, setAddressesOrderDirection] = useState("DESC");
  const [addressesPage, setAddressesPage] = useState(1);

  const view = valueIn(VIEWS, urlOptions.view, DEFAULT_VIEW);
  const show = valueIn(SHOW_OPTIONS, urlOptions.show, "all");
  const withExternal = urlOptions.external !== "0";
  const withBroadcast = urlOptions.broadcast !== "0";

  const refresh = useCallback(() => setRevision((r) => r + 1), []);

  const tenantKey = JSON.stringify([organizationId, tenantId, revision]);
  const requestKey = JSON.stringify([organizationId, tenantId, timeRange, revision]);

  const status = useTopologyRequest(tenantKey, true,
      (setData) => segmentationService.getStatus(organizationId, tenantId, setData));
  const matrix = useTopologyRequest(requestKey, true,
      (setData) => segmentationService.getMatrix(organizationId, tenantId, timeRange, setData));

  const model = useMemo(() => (matrix ? buildOverviewModel(matrix) : null), [matrix]);
  const shown = useMemo(() => (model ? filterOverview(model, show, withExternal, withBroadcast) : null),
      [model, show, withExternal, withBroadcast]);

  const selected = useMemo(() => {
    if (!selection || !shown) return null;

    if (selection.kind === "cell") {
      return shown.cellsById.has(cellId(selection.source, selection.destination)) ? selection : null;
    }

    return shown.groups.some((group) => group.id === selection.id) ? selection : null;
  }, [selection, shown]);

  const selectedCell = selected && selected.kind === "cell"
      ? model.cellsById.get(cellId(selected.source, selected.destination)) : null;

  const flowsKey = selectedCell
      ? JSON.stringify([requestKey, selectedCell.id, flowsFilters, flowsOrderColumn, flowsOrderDirection, flowsPage])
      : null;
  const flows = useTopologyRequest(flowsKey, flowsKey !== null,
      (setData) => segmentationService.findFlows(organizationId, tenantId, timeRange, selectedCell.source.id,
          selectedCell.destination.id, flowsFilters, flowsOrderColumn, flowsOrderDirection, FLOWS_PER_PAGE,
          (flowsPage-1)*FLOWS_PER_PAGE, setData));

  const selectedGroup = selected && selected.kind === "group" ? model.groupsById.get(selected.id) : null;
  const addressesGroup = selectedGroup && hasAddresses(selectedGroup) ? selectedGroup : null;

  const addressesKey = addressesGroup
      ? JSON.stringify([requestKey, addressesGroup.id, addressesFilters, addressesOrderColumn, addressesOrderDirection,
        addressesPage])
      : null;
  const addresses = useTopologyRequest(addressesKey, addressesKey !== null,
      (setData) => segmentationService.findGroupAddresses(organizationId, tenantId, timeRange, addressesGroup.id,
          addressesFilters, addressesOrderColumn, addressesOrderDirection, ADDRESSES_PER_PAGE,
          (addressesPage-1)*ADDRESSES_PER_PAGE, setData));

  const select = (newSelection) => {
    const same = selection && newSelection && selection.kind === "cell" && newSelection.kind === "cell"
        && selection.source === newSelection.source && selection.destination === newSelection.destination;

    if (!same) {
      setFlowsFilters({});
      setFlowsPage(1);
    }

    const sameGroup = selection && newSelection && selection.kind === "group" && newSelection.kind === "group"
        && selection.id === newSelection.id;

    if (!sameGroup) {
      setAddressesFilters({});
      setAddressesPage(1);
    }

    setSelection(newSelection);
  }

  const applyTimeRange = (newTimeRange) => {
    setFlowsPage(1);
    setAddressesPage(1);
    setTimeRange(newTimeRange);
  }

  const applyAddressesFilters = (newFilters) => {
    setAddressesPage(1);
    setAddressesFilters(newFilters ? newFilters : {});
  }

  const applyFlowsFilters = (newFilters) => {
    setFlowsPage(1);
    setFlowsFilters(newFilters ? newFilters : {});
  }

  const colors = overviewColors(Boolean(Store.get("dark_mode")));

  const optionSelect = (o) => (
      <div key={o.id} className="d-flex align-items-center me-3 mb-2">
        <label htmlFor={o.id} className="me-2 small text-muted mb-0">{o.label}</label>
        <select id={o.id} className="form-select form-select-sm w-auto" value={o.value}
                onChange={(e) => o.onChange(e.target.value)}>
          {o.options.map(([v, title]) => <option key={v} value={v}>{title}</option>)}
        </select>
      </div>
  )

  const loading = () => (
      <div className="border" style={{height: VIEW_HEIGHT + "px"}}>
        <GenericWidgetLoadingSpinner height={VIEW_HEIGHT - 2} />
      </div>
  );

  const shownText = () => {
    const total = shown.cells.length + shown.hiddenCells;

    return "Showing " + formatCount(shown.cells.length) + " of " + formatCount(total)
        + (total === 1 ? " crossing." : " crossings.");
  }

  const visualization = () => (view === "map"
      ? <SegmentationBoundaryMap model={model} groups={shown.groups} cells={shown.cells} selection={selected}
                                 onSelect={select} filtered={shown.filtered} height={viewHeight} resetKey={viewReset} />
      : <SegmentationMatrix model={model} groups={shown.filtered ? shown.involvedGroups : shown.groups}
                            cells={shown.cells} selection={selected} onSelect={select}
                            filtered={shown.filtered} height={viewHeight} />);

  const content = () => {
    if (!model || !shown) {
      return loading();
    }

    if (model.segments === 0) {
      return (
          <div className="alert alert-info mb-0">
            No segments defined.{' '}
            <Link to={ApiRoutes.ETHERNET.SEGMENTATION.SEGMENTS.INDEX}>Create segments</Link>
          </div>
      );
    }

    if (model.cells.length === 0) {
      return (
          <div className="alert alert-info mb-0">
            No traffic between or inside segments in the selected time range.
          </div>
      );
    }

    return (
        <div ref={fullscreenRef} className="segmentation-overview-view">
          <div className="d-flex flex-wrap align-items-center">
            {optionSelect({id: "segmentation-view", label: "View", value: view, onChange: (v) => setUrlOptions({view: v}),
              options: VIEWS})}
            {optionSelect({id: "segmentation-show", label: "Show", value: show, onChange: (v) => setUrlOptions({show: v}),
              options: SHOW_OPTIONS})}
            <div className="form-check form-switch mb-2">
              <input className="form-check-input"
                     type="checkbox"
                     role="switch"
                     id="segmentation-external"
                     onChange={(e) => setUrlOptions({external: e.target.checked ? "1" : "0"})}
                     checked={withExternal} />
              <label className="form-check-label small text-muted" htmlFor="segmentation-external">
                External networks and Internet
              </label>
            </div>
            <div className="form-check form-switch mb-2 ms-3">
              <input className="form-check-input"
                     type="checkbox"
                     role="switch"
                     id="segmentation-broadcast"
                     onChange={(e) => setUrlOptions({broadcast: e.target.checked ? "1" : "0"})}
                     checked={withBroadcast} />
              <label className="form-check-label small text-muted" htmlFor="segmentation-broadcast"
                     title="Traffic to broadcast and multicast addresses, like mDNS, SSDP or DHCP.">
                Broadcast and multicast
              </label>
            </div>

            <div className="ms-auto mb-2 text-nowrap">
              {view === "map" ?
                  <button className="btn btn-sm btn-outline-secondary" type="button"
                          onClick={() => setViewReset((count) => count + 1)}>
                    Reset view
                  </button> : null}
              {canFullscreen() ?
                  <button className="btn btn-sm btn-outline-secondary ms-2" type="button" onClick={toggleFullscreen}>
                    <i className={"fa-solid " + (fullscreen ? "fa-compress" : "fa-expand")} />{' '}
                    {fullscreen ? "Exit fullscreen" : "Fullscreen"}
                  </button> : null}
            </div>
          </div>

          <div className="row">
            <div className="col-md-9">
              {visualization()}
              <div className="text-muted small mt-1">{shownText()}</div>
              <SegmentationLegend view={view} />
            </div>
            <div className="col-md-3">
              <div style={{position: "sticky", top: "12px"}}>
                <SegmentationSelectionPanel selection={selected}
                                            view={view}
                                            model={model}
                                            policies={matrix.policies}
                                            timeRange={timeRange}
                                            flows={flows ? flows.flows : null}
                                            canManage={canManage}
                                            colors={colors}
                                            onSelect={select}
                                            height={viewHeight} />
              </div>
            </div>
          </div>
        </div>
    );
  }

  return (
      <React.Fragment>
        <div className="row">
          <div className="col-md-12">
            <SectionMenuBar items={SEGMENTATION_MENU_ITEMS}
                            activeRoute={ApiRoutes.ETHERNET.SEGMENTATION.INDEX} />
          </div>
        </div>

        <SegmentationStatusStrip status={status}
                                 restampPending={Boolean(matrix && matrix.restamp_pending)}
                                 findingsLimit={matrix && matrix.findings_limited ? matrix.findings_limit : null} />

        <div className="row mt-3">
          <div className="col-md-12">
            <div className="card">
              <div className="card-body">
                <CardTitleWithControls title="Crossings Between Segments"
                                       timeRange={timeRange}
                                       setTimeRange={applyTimeRange}
                                       refreshAction={refresh} />

                {content()}
              </div>
            </div>
          </div>
        </div>

        {selectedCell ?
            <div className="row mt-3">
              <div className="col-md-12">
                <div className="card">
                  <div className="card-body">
                    <CardTitleWithControls title={"Flows: " + crossingTitle(selectedCell)}
                                           fixedAppliedTimeRange={timeRange} />

                    <p className="text-muted">
                      Flows of the selected crossing: one row per source, destination, service and outcome.
                    </p>

                    <div className="mb-4">
                      <Filters filters={flowsFilters}
                               setFilters={applyFlowsFilters}
                               fields={SEGMENTATION_FLOWS_FILTER_FIELDS}
                               embedded={true}
                               onMonitorsReady={() => {}} />
                    </div>

                    <SegmentationFlowsTable flows={flows}
                                            page={flowsPage}
                                            setPage={setFlowsPage}
                                            perPage={FLOWS_PER_PAGE}
                                            orderColumn={flowsOrderColumn}
                                            setOrderColumn={(c) => { setFlowsPage(1); setFlowsOrderColumn(c); }}
                                            orderDirection={flowsOrderDirection}
                                            setOrderDirection={(d) => { setFlowsPage(1); setFlowsOrderDirection(d); }}
                                            filterFields={SEGMENTATION_FLOWS_FILTER_FIELDS}
                                            filters={flowsFilters}
                                            setFilters={applyFlowsFilters}
                                            colors={colors}
                                            timeRange={timeRange}
                                            onExclude={canManage ? setExcluding : null} />
                  </div>
                </div>
              </div>
            </div> : null}

        {excluding ?
            <ExcludeDestinationDialog organizationId={organizationId}
                                      tenantId={tenantId}
                                      flow={excluding}
                                      onClose={() => setExcluding(null)}
                                      onComplete={() => {
                                        setExcluding(null);
                                        toast.success("Destination excluded.");
                                        refresh();
                                      }} /> : null}

        {addressesGroup ?
            <div className="row mt-3">
              <div className="col-md-12">
                <div className="card">
                  <div className="card-body">
                    <CardTitleWithControls title={"Addresses: " + addressesGroup.name}
                                           fixedAppliedTimeRange={timeRange} />

                    <p className="text-muted">
                      Addresses of the selected group that taps recorded traffic of in the time range.
                    </p>

                    <div className="mb-4">
                      <Filters filters={addressesFilters}
                               setFilters={applyAddressesFilters}
                               fields={SEGMENTATION_ADDRESSES_FILTER_FIELDS}
                               embedded={true}
                               onMonitorsReady={() => {}} />
                    </div>

                    <SegmentationAddressesTable addresses={addresses}
                                                tapsOf={model.tapsOf}
                                                page={addressesPage}
                                                setPage={setAddressesPage}
                                                perPage={ADDRESSES_PER_PAGE}
                                                orderColumn={addressesOrderColumn}
                                                setOrderColumn={(c) => { setAddressesPage(1); setAddressesOrderColumn(c); }}
                                                orderDirection={addressesOrderDirection}
                                                setOrderDirection={(d) => { setAddressesPage(1); setAddressesOrderDirection(d); }}
                                                filterFields={SEGMENTATION_ADDRESSES_FILTER_FIELDS}
                                                filters={addressesFilters}
                                                setFilters={applyAddressesFilters}
                                                timeRange={timeRange} />
                  </div>
                </div>
              </div>
            </div> : null}
      </React.Fragment>
  )

}
