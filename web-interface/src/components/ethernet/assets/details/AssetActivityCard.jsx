import React, {useEffect, useMemo, useState} from "react";
import Plot from "react-plotly.js";
import moment from "moment";
import CardTitleWithControls from "../../../shared/CardTitleWithControls";
import LoadingSpinner from "../../../misc/LoadingSpinner";
import AssetsService from "../../../../services/ethernet/AssetsService";
import Store from "../../../../util/Store";
import {Relative} from "../../../shared/timerange/TimeRange";
import {formatBytes, formatCount} from "./AssetProfileHelpers";

const assetsService = new AssetsService();

const WEEKDAYS = ["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"];

const hoverLines = (bucket) => formatCount(bucket.events) + " network events, " + formatBytes(bucket.bytes);

const emptyBucket = () => ({events: 0, bytes: 0});
const HOURS = Array.from({length: 24}, (_, i) => i);

const DEFAULT_TIME_RANGE = Relative(90 * 24 * 60, "Last 90 Days");
const MAX_DAYS = 730;

const daysOf = (timeRange) => {
  let days;
  switch (timeRange.type) {
    case "relative":
      days = Math.ceil(timeRange.minutes / 1440);
      break;
    case "named":
      switch (timeRange.name) {
        case "today": days = 1; break;
        case "yesterday": days = 2; break;
        case "week_to_date": days = moment().isoWeekday(); break;
        case "month_to_date": days = moment().date(); break;
        default: days = MAX_DAYS;
      }
      break;
    case "absolute":
      days = Math.ceil(moment().diff(moment(timeRange.from), "days", true));
      break;
    default:
      days = 90;
  }
  return Math.max(1, Math.min(days, MAX_DAYS));
}

// Linear up to the 95th percentile of the cells, so one exceptional hour does not flatten the rest.
const colorValue = (cell, byBytes) => Number(byBytes ? cell.bytes : cell.events) || 0;

const colorCeiling = (z) => {
  const values = z.flat().filter((v) => v !== null && v > 0).sort((a, b) => a - b);
  if (values.length === 0) {
    return 1;
  }
  return values[Math.min(values.length - 1, Math.floor(values.length * 0.95))];
}

const chartColors = () => {
  if (Store.get("dark_mode")) {
    return {
      background: "#1c1c22", text: "#c4c4d4", lines: "#33333d", grid: "#2a2a33",
      scale: [[0, "#26262e"], [0.001, "#1e3a8a"], [0.25, "#2f74c0"], [0.5, "#38bdf8"], [0.75, "#facc15"], [1, "#fb923c"]]
    };
  }

  return {
    background: "#f9f9f9", text: "#111111", lines: "#373737", grid: "#e6e6e6",
    scale: [[0, "#ececec"], [0.001, "#1e40af"], [0.25, "#2563eb"], [0.5, "#0ea5e9"], [0.75, "#eab308"], [1, "#ea580c"]]
  };
}

export default function AssetActivityCard({asset, organizationId, tenantId, revision, onRefresh}) {

  const [timeRange, setTimeRange] = useState(DEFAULT_TIME_RANGE);
  const [activity, setActivity] = useState(null);

  const days = daysOf(timeRange);

  // Minutes east of UTC, which is the inverse of what the browser reports.
  const timezoneOffset = -new Date().getTimezoneOffset();

  useEffect(() => {
    setActivity(null);
    assetsService.getAssetProfileActivity(asset.uuid, organizationId, tenantId, days, timezoneOffset, setActivity);
  }, [asset.uuid, organizationId, tenantId, days, timezoneOffset, revision]);

  const colors = chartColors();

  const heatmap = useMemo(() => {
    if (!activity) {
      return null;
    }

    const buckets = WEEKDAYS.map(() => HOURS.map(emptyBucket));
    activity.heatmap.forEach((cell) => {
      const bucket = buckets[cell.day_of_week - 1][cell.hour];
      bucket.events += cell.events;
      bucket.bytes += cell.bytes;
    });

    const byBytes = activity.total_bytes > 0;
    const z = buckets.map((row) => row.map((b) => colorValue(b, byBytes)));
    const text = buckets.map((row, d) => row.map((b, h) =>
      WEEKDAYS[d] + " " + String(h).padStart(2, "0") + ":00<br>" + hoverLines(b)
    ));

    return {z, text};
  }, [activity]);

  const calendar = useMemo(() => {
    if (!activity) {
      return null;
    }

    const byBytes = activity.total_bytes > 0;
    const byDay = {};
    activity.calendar.forEach((d) => byDay[d.day] = d);

    const end = moment().startOf("day");
    const start = moment(end).subtract(days - 1, "days");
    const gridStart = moment(start).startOf("isoWeek");

    const weeks = Math.ceil(moment(end).diff(gridStart, "days") / 7) + 1;
    const z = WEEKDAYS.map(() => Array.from({length: weeks}, () => null));
    const text = WEEKDAYS.map(() => Array.from({length: weeks}, () => ""));
    const x = Array.from({length: weeks}, (_, w) => moment(gridStart).add(w, "weeks").format("YYYY-MM-DD"));

    for (let cursor = moment(start); !cursor.isAfter(end); cursor.add(1, "day")) {
      const week = Math.floor(cursor.diff(gridStart, "days") / 7);
      const dow = cursor.isoWeekday() - 1;
      const key = cursor.format("YYYY-MM-DD");
      const day = byDay[key];
      z[dow][week] = day ? colorValue(day, byBytes) : 0;
      text[dow][week] = key + "<br>" + (day ? hoverLines(day) : "No activity");
    }

    return {z, text, x};
  }, [activity, days]);

  const layoutBase = (height, marginLeft) => ({
    height: height,
    margin: {l: marginLeft, r: 10, b: 30, t: 10, pad: 0},
    font: {family: "'Nunito Sans', sans-serif", size: 11, color: colors.text},
    paper_bgcolor: colors.background,
    plot_bgcolor: colors.background,
    hoverlabel: {font: {size: 11}, namelength: -1},
    xaxis: {fixedrange: true, linecolor: colors.lines, gridcolor: colors.grid, zeroline: false, showgrid: false},
    yaxis: {fixedrange: true, linecolor: colors.lines, gridcolor: colors.grid, zeroline: false, showgrid: false, autorange: "reversed"}
  });

  const config = {displayModeBar: false, responsive: true, showTips: false, scrollZoom: false};

  const title = (
    <React.Fragment>
      <CardTitleWithControls title="Activity"
                             timeRange={timeRange}
                             setTimeRange={setTimeRange}
                             timeRangeGranularity="day"
                             doNotPersistTimeRange={true}
                             refreshAction={onRefresh} />

    </React.Fragment>
  );

  if (!activity || !heatmap || !calendar) {
    return <React.Fragment>{title}<LoadingSpinner /></React.Fragment>
  }

  const fact = (label, value, detail = null, last = false) => (
    <div className={"py-1 " + (last ? "" : "pe-4 me-4 border-end")}>
      <div className="small text-muted text-uppercase">{label}</div>
      <div>
        {value}
        {detail ? <span className="text-muted"> ({detail})</span> : null}
      </div>
    </div>
  )

  const retention = activity.retention_days;
  const exceedsRetention = retention !== undefined && retention !== null && days > retention;

  const strip = (
    <div className="d-flex flex-wrap align-items-center">
      {fact("Active Days", formatCount(activity.active_days) + " of " + days)}
      {fact("Network Events", formatCount(activity.total_events))}
      {fact("Traffic", formatBytes(activity.total_bytes))}
      {fact("Longest Absence", activity.longest_gap_days > 0
        ? activity.longest_gap_days + (activity.longest_gap_days === 1 ? " day" : " days")
        : <span className="text-muted">None</span>,
        activity.longest_gap_days > 0 ? "returned " + moment(activity.longest_gap_end).format("YYYY-MM-DD") : null)}
      {fact("Retention", retention !== undefined && retention !== null
        ? <span className={exceedsRetention ? "text-warning" : ""}>{formatCount(retention)} days</span>
        : <span className="text-muted">n/a</span>,
        exceedsRetention ? "shorter than the selected time range" : null, true)}
    </div>
  )

  return (
    <React.Fragment>
      {title}

      {strip}

      {activity.total_events === 0
        ? <div className="alert alert-info mb-0">No activity recorded in the last {days} days.</div>
        : <React.Fragment>
            <h6 className="mb-1 mt-3">Hour of Day by Weekday</h6>
            <Plot style={{width: "100%"}}
                  data={[{
                    type: "heatmap",
                    x: HOURS,
                    y: WEEKDAYS,
                    z: heatmap.z,
                    zmin: 0,
                    zmax: colorCeiling(heatmap.z),
                    text: heatmap.text,
                    hoverinfo: "text",
                    colorscale: colors.scale,
                    showscale: false,
                    xgap: 2,
                    ygap: 2
                  }]}
                  layout={{...layoutBase(210, 40), xaxis: {...layoutBase(0, 0).xaxis, dtick: 1, tickfont: {size: 9}}}}
                  config={config} />

            <h6 className="mb-1 mt-3">Daily Activity</h6>
            <Plot style={{width: "100%"}}
                  data={[{
                    type: "heatmap",
                    x: calendar.x,
                    y: WEEKDAYS,
                    z: calendar.z,
                    zmin: 0,
                    zmax: colorCeiling(calendar.z),
                    text: calendar.text,
                    hoverinfo: "text",
                    colorscale: colors.scale,
                    showscale: false,
                    xgap: 2,
                    ygap: 2
                  }]}
                  layout={{...layoutBase(190, 40), xaxis: {...layoutBase(0, 0).xaxis, type: "category", nticks: 12, tickfont: {size: 9}}}}
                  config={config} />

            <p className="text-muted small mb-0 mt-2">
              Cells are colored by traffic volume, or by events for assets that moved no payload. A network event is one observed interaction of this asset on the
              network, like a TCP or UDP session, a DNS query or a DHCP transaction.
            </p>
          </React.Fragment>}
    </React.Fragment>
  )

}
