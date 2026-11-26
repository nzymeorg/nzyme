import React, {useContext, useEffect, useState} from "react";
import {useNavigate} from "react-router-dom";
import {TapContext} from "../../../App";
import {disableTapSelector, enableTapSelector} from "../../misc/TapSelector";
import {Presets} from "../../shared/timerange/TimeRange";
import {useUrlTimeRange} from "../../shared/timerange/TimeRangeSelector";
import CardTitleWithControls from "../../shared/CardTitleWithControls";
import SectionMenuBar from "../../shared/SectionMenuBar";
import Filters from "../../shared/filtering/Filters";
import useUrlFilters from "../../shared/filtering/useUrlFilters";
import useSelectedTenant from "../../system/tenantselector/useSelectedTenant";
import usePageTitle from "../../../util/UsePageTitle";
import GenericWidgetLoadingSpinner from "../../widgets/GenericWidgetLoadingSpinner";
import Dot11Service from "../../../services/Dot11Service";
import FrameSignalChart from "./FrameSignalChart";
import FrameSignalFinding from "./FrameSignalFinding";
import FrameSignalAddressForm from "./FrameSignalAddressForm";
import FrameSignalTransmitterSelection from "./FrameSignalTransmitterSelection";
import FrameSignalError from "./FrameSignalError";
import {FrameSignalInvestigationContext} from "./FrameSignalInvestigation";
import {frameSignalChartKey} from "./FrameSignalChart";
import FrameSignalChartNotes, {frameSignalChartTitle} from "./FrameSignalChartNotes";

const dot11Service = new Dot11Service();

export default function FrameSignalPage({investigation}) {

  usePageTitle(investigation.pageTitle);

  const [organizationId, tenantId] = useSelectedTenant();
  const tapContext = useContext(TapContext);
  const selectedTaps = tapContext.taps;

  const [timeRange, setTimeRange] = useUrlTimeRange(Presets.RELATIVE_HOURS_24);
  const [filters, setFilters] = useUrlFilters(investigation.filterFields);
  const navigate = useNavigate();

  /*
   * What is loaded belongs to one investigation: an organization, a set of filters and taps, and a time range.
   * Results of another one are never shown, and the pages of the tables start over. The selection of charts
   * outlives a change of the time range, because a chart is a tap and a channel in any time.
   */
  const subject = JSON.stringify([organizationId, tenantId, filters, selectedTaps]);
  const scope = JSON.stringify([subject, timeRange]);

  const [loadedAnalysis, setLoadedAnalysis] = useState(null);
  const [loadedCharts, setLoadedCharts] = useState(null);
  const [chosenFindingsPage, setChosenFindingsPage] = useState(null);
  const [chosenSelection, setChosenSelection] = useState(null);

  const analysis = loadedAnalysis && loadedAnalysis.scope === scope ? loadedAnalysis : null;
  const charts = loadedCharts && loadedCharts.scope === scope ? loadedCharts : null;
  const findingsPage = chosenFindingsPage && chosenFindingsPage.scope === scope ? chosenFindingsPage.page : 1;
  const selection = chosenSelection && chosenSelection.subject === subject ? chosenSelection.keys : null;

  const perPage = 25;

  const [revision, setRevision] = useState(new Date());

  useEffect(() => {
    enableTapSelector(tapContext);

    return () => {
      disableTapSelector(tapContext);
    }
  }, [tapContext]);

  const addressField = investigation.addressField;

  const transmittersOf = (f) => {
    return f && f[addressField] ? f[addressField].filter((t) => t.operator === "equals").map((t) => t.value) : [];
  }

  const applyFilters = (newFilters) => {
    if (transmittersOf(newFilters).length === 0) {
      setFilters(null);
      navigate(investigation.startLink(timeRange));
    } else {
      setFilters(newFilters);
    }
  }

  // A filter icon of the frame records can replace all filters. The transmitter stays, or the investigation is over.
  const applyFrameFilters = (newFilters) => {
    applyFilters(transmittersOf(newFilters).length > 0
        ? newFilters : {...newFilters, [addressField]: filters[addressField]});
  }

  const transmitters = transmittersOf(filters);
  const investigating = transmitters.length > 0;

  useEffect(() => {
    if (!investigating) {
      return;
    }

    let current = true;
    dot11Service.getFrameSignalAnalysis(investigation.apiPath, organizationId, tenantId, timeRange, filters, selectedTaps,
        perPage, (findingsPage - 1) * perPage,
        (data) => current && setLoadedAnalysis({scope: scope, page: findingsPage, data: data}),
        (error) => current && setLoadedAnalysis({scope: scope, page: findingsPage, error: error}));

    return () => {
      current = false;
    }
  }, [scope, findingsPage, revision]);

  useEffect(() => {
    if (!loadedAnalysis || !loadedAnalysis.data || loadedAnalysis.scope !== scope || loadedAnalysis.page !== 1
        || selection !== null) {
      return;
    }

    const keys = [];
    (loadedAnalysis.data.findings || []).forEach((finding) => {
      const key = frameSignalChartKey(finding);
      if (!keys.includes(key) && keys.length < loadedAnalysis.data.max_charts) {
        keys.push(key);
      }
    });

    setChosenSelection({subject: subject, keys: keys});
  }, [loadedAnalysis, scope, selection]);

  const selectionKey = selection === null ? null : selection.join(",");

  useEffect(() => {
    if (!investigating || selection === null) {
      return;
    }

    if (selection.length === 0) {
      setLoadedCharts({scope: scope, selectionKey: selectionKey, data: {charts: []}});
      return;
    }

    let current = true;
    dot11Service.getFrameSignalCharts(investigation.apiPath, organizationId, tenantId, timeRange, filters, selectedTaps, selection,
        (data) => current && setLoadedCharts({scope: scope, selectionKey: selectionKey, data: data}),
        (error) => current && setLoadedCharts({scope: scope, selectionKey: selectionKey, error: error}));

    return () => {
      current = false;
    }
  }, [scope, selectionKey, revision]);

  // A selected tap and channel without frames in a new time range would stay selected without a row to deselect it.
  useEffect(() => {
    if (!charts || !charts.data || selection === null || charts.selectionKey !== selectionKey) {
      return;
    }

    const loaded = charts.data.charts.map(frameSignalChartKey);
    if (selection.some((key) => !loaded.includes(key))) {
      setChosenSelection({subject: subject, keys: selection.filter((key) => loaded.includes(key))});
    }
  }, [charts, selectionKey]);

  const card = (title, key, content, controls = {}) => {
    return (
        <div className="row mt-3" key={key}>
          <div className="col-md-12">
            <div className="card">
              <div className="card-body">
                <CardTitleWithControls title={title} timeRange={timeRange} {...controls} />

                {content}
              </div>
            </div>
          </div>
        </div>
    )
  }

  const compared = analysis && analysis.data && analysis.data.source_count > 0
      && analysis.data.source_count <= analysis.data.max_sources;

  const finding = () => {
    if (!analysis) {
      return <GenericWidgetLoadingSpinner height={150} />
    }

    if (analysis.error) {
      return <FrameSignalError error={analysis.error} />
    }

    if (analysis.data.source_count === 0) {
      return (
          <div className="alert alert-info mb-0">
            The selected taps recorded no {investigation.frames} with a signal strength for these filters.
          </div>
      )
    }

    if (!compared) {
      return (
          <div className="alert alert-info mb-0">
            The filters match frames of more than {analysis.data.max_sources} {investigation.transmitters}. An
            investigation covers one or two.
          </div>
      )
    }

    return <FrameSignalFinding analysis={analysis.data}
                               timeRange={timeRange}
                               setTimeRange={setTimeRange}
                               page={findingsPage}
                               setPage={(newPage) => setChosenFindingsPage({scope: scope, page: newPage})}
                               perPage={perPage}
                               selection={selection === null ? [] : selection}
                               setSelection={(keys) => setChosenSelection({subject: subject, keys: keys})} />
  }

  const evidenceControls = {helpLink: "https://go.nzyme.org/signal-investigation-evidence"};

  const evidence = () => {
    if (!compared) {
      return null;
    }

    if (selection === null || charts === null) {
      return card("Evidence", "loading", <GenericWidgetLoadingSpinner height={280} />, evidenceControls);
    }

    if (charts.error) {
      return card("Evidence", "error", <FrameSignalError error={charts.error} />, evidenceControls);
    }

    if (selection.length === 0) {
      return card("Evidence", "none",
          <div className="alert alert-info mb-0">
            No tap and channel is selected. Select rows in the table above to chart them.
          </div>, evidenceControls);
    }

    const shown = charts.data.charts.filter((chart) => selection.includes(frameSignalChartKey(chart)));

    return (
        <React.Fragment>
          {shown.map((chart) => card("Evidence: " + frameSignalChartTitle(chart), frameSignalChartKey(chart),
              <React.Fragment>
                <FrameSignalChart chart={chart}
                                  sources={analysis.data.sources}
                                  threshold={analysis.data.threshold}
                                  bucketSeconds={charts.data.bucket_seconds}
                                  timeRange={timeRange}
                                  setTimeRange={setTimeRange} />

                <FrameSignalChartNotes chart={chart} charts={charts.data} sources={analysis.data.sources} />
              </React.Fragment>, evidenceControls))}

          {charts.selectionKey !== selectionKey
              ? card("Evidence", "loading", <GenericWidgetLoadingSpinner height={280} />, evidenceControls) : null}
        </React.Fragment>
    )
  }

  const Records = investigation.Records;

  return (
      <FrameSignalInvestigationContext.Provider value={investigation}>
        <div className="row">
          <div className="col-md-12">
            <SectionMenuBar items={investigation.menuItems} activeRoute={investigation.route} />
          </div>
        </div>

        <div className="row mt-3">
          <div className="col-md-12">
            <div className="card">
              <div className="card-body">
                <CardTitleWithControls title="Signal Strength Investigation"
                                       timeRange={timeRange}
                                       setTimeRange={setTimeRange}
                                       helpLink="https://go.nzyme.org/signal-investigation" />

                <p>{investigation.introduction}</p>

                {investigating ?
                    <Filters filters={filters}
                             setFilters={applyFilters}
                             fields={investigation.filterFields}
                             onMonitorsReady={() => {}} />
                    : <FrameSignalAddressForm timeRange={timeRange} />}
              </div>
            </div>
          </div>
        </div>

        {investigating ?
            <React.Fragment>
              {card("Finding", "finding", finding(), {refreshAction: () => setRevision(new Date()),
                helpLink: "https://go.nzyme.org/signal-investigation-finding"})}

              {evidence()}

              {card("Evidence: " + investigation.recordsTitle, "frames",
                  <Records key={scope}
                           timeRange={timeRange}
                           filters={filters}
                           setFilters={applyFrameFilters}
                           taps={selectedTaps}
                           revision={revision} />,
                  {refreshAction: () => setRevision(new Date()),
                    internalLink: investigation.recordsLink(filters, timeRange),
                    helpLink: investigation.recordsHelpLink})}
            </React.Fragment>
            : card(investigation.transmittersTitle, "transmitters",
                <FrameSignalTransmitterSelection timeRange={timeRange} />,
                {helpLink: "https://go.nzyme.org/signal-investigation-start"})}
      </FrameSignalInvestigationContext.Provider>
  )

}
