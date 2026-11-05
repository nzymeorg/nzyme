import React, {useEffect, useState} from "react";
import moment from "moment";
import numeral from "numeral";
import {Link} from "react-router-dom";
import CardTitleWithControls from "../../../shared/CardTitleWithControls";
import ColumnSorting from "../../../shared/ColumnSorting";
import Filters from "../../../shared/filtering/Filters";
import FilterValueIcon from "../../../shared/filtering/FilterValueIcon";
import useUrlFilters from "../../../shared/filtering/useUrlFilters";
import LoadingSpinner from "../../../misc/LoadingSpinner";
import Paginator from "../../../misc/Paginator";
import SimpleBarChart from "../../../widgets/charts/SimpleBarChart";
import AssetsService from "../../../../services/ethernet/AssetsService";
import ApiRoutes from "../../../../util/ApiRoutes";
import {describeChange, timelineKindOf, timelineKindTitle} from "./AssetProfileHelpers";
import {ASSET_TIMELINE_FILTER_FIELDS} from "./AssetTimelineFilterFields";
import {Presets} from "../../../shared/timerange/TimeRange";

const assetsService = new AssetsService();

const PER_PAGE = 15;

export default function AssetTimelineCard({asset, organizationId, tenantId, revision, onRefresh}) {

  const [timeRange, setTimeRange] = useState(Presets.RELATIVE_DAYS_7);
  const [filters, setFilters] = useUrlFilters(ASSET_TIMELINE_FILTER_FIELDS);
  const [timeline, setTimeline] = useState(null);
  const [histogram, setHistogram] = useState(null);
  const [page, setPage] = useState(1);
  const [orderColumn, setOrderColumn] = useState("timestamp");
  const [orderDirection, setOrderDirection] = useState("DESC");

  const firstPage = (f) => (value) => {
    f(value);
    setPage(1);
  }

  useEffect(() => {
    setTimeline(null);
    assetsService.getAssetProfileTimeline(asset.uuid, organizationId, tenantId, timeRange, filters, orderColumn,
      orderDirection, PER_PAGE, (page - 1) * PER_PAGE, setTimeline);
  }, [asset.uuid, organizationId, tenantId, timeRange, filters, orderColumn, orderDirection, page, revision]);

  useEffect(() => {
    setHistogram(null);
    assetsService.getAssetProfileTimelineHistogram(asset.uuid, organizationId, tenantId, timeRange, filters, setHistogram);
  }, [asset.uuid, organizationId, tenantId, timeRange, filters, revision]);

  const describe = (event) => {
    if (event.type === "ALERT") {
      return <Link to={ApiRoutes.ALERTS.DETAILS(event.detail)}>{event.value.replace(/_/g, " ")}</Link>;
    }

    const change = describeChange(event.type, event.value, event.detail);

    return (
      <span>
        <strong>{change.action}</strong>
        {change.value ? <span> {change.machineData ? <span className="machine-data">{change.value}</span> : change.value}</span> : null}
        {change.explanation ? <span className="text-muted"> {change.explanation}</span> : null}
      </span>
    );
  }

  const sorting = (column) => (
    <ColumnSorting thisColumn={column}
                   orderColumn={orderColumn}
                   setOrderColumn={firstPage(setOrderColumn)}
                   orderDirection={orderDirection}
                   setOrderDirection={firstPage(setOrderDirection)} />
  );

  const chart = () => {
    if (!histogram) {
      return <div className="mt-4" style={{height: "175px"}}><LoadingSpinner /></div>;
    }

    return (
      <div className="mt-4">
        <SimpleBarChart height={175}
                        data={histogram}
                        timeRange={timeRange}
                        setTimeRange={firstPage(setTimeRange)}
                        doNotPersistTimeRange={true} />
      </div>
    );
  }

  const body = () => {
    if (!timeline) {
      return <LoadingSpinner />;
    }

    if (timeline.events.length === 0) {
      return <div className="alert alert-info mt-3 mb-0">No changes match the selected time range and filters.</div>;
    }

    return (
      <React.Fragment>
        <table className="table table-sm table-hover table-striped mt-3 mb-3">
          <thead>
          <tr>
            <th className="text-nowrap">Type {sorting("type")}</th>
            <th>Change {sorting("value")}</th>
            <th className="text-nowrap">Time {sorting("timestamp")}</th>
          </tr>
          </thead>
          <tbody>
          {timeline.events.map((event, i) => {
            const kind = timelineKindOf(event);
            return (
              <tr key={"event-" + i}>
                <td className="text-nowrap">
                  {timelineKindTitle(kind)}{' '}
                  <FilterValueIcon setFilters={firstPage(setFilters)}
                                   fields={ASSET_TIMELINE_FILTER_FIELDS}
                                   field="type"
                                   value={kind} />
                </td>
                <td>{describe(event)}</td>
                <td className="text-nowrap" title={moment(event.timestamp).format()}>
                  {moment(event.timestamp).fromNow()}
                </td>
              </tr>
            );
          })}
          </tbody>
        </table>

        <Paginator itemCount={timeline.total} perPage={PER_PAGE} setPage={setPage} page={page} />
      </React.Fragment>
    );
  }

  const retentionNote = () => {
    if (!timeline) {
      return null;
    }

    const retentionDays = timeline.attribute_retention_days === undefined || timeline.attribute_retention_days === null
        ? "" : " of " + numeral(timeline.attribute_retention_days).format("0,0") + " days";

    return (
      <p className="text-muted small mb-0 mt-3">
        First seen, hostname and IP address changes are kept for as long as the asset exists. Attribute changes are
        kept for the attribute retention time{retentionDays} set in the asset settings, counted from when the value
        was last seen, so a value that is still in use keeps its change. Alerts stay until they are deleted.
      </p>
    );
  }

  return (
    <React.Fragment>
      <CardTitleWithControls title="Changes"
                             timeRange={timeRange}
                             setTimeRange={firstPage(setTimeRange)}
                             doNotPersistTimeRange={true}
                             refreshAction={onRefresh} />

      <Filters filters={filters}
               setFilters={firstPage(setFilters)}
               fields={ASSET_TIMELINE_FILTER_FIELDS}
               onMonitorsReady={() => {}} />

      {chart()}
      {body()}
      {retentionNote()}
    </React.Fragment>
  )

}
