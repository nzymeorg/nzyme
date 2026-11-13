import React, {useState} from "react";
import numeral from "numeral";
import {alertTitle, METRIC_TITLES, WIDGET_TYPES} from "./WidgetCatalog";
import StatusWidget from "./StatusWidget";
import AlertsWidget from "./AlertsWidget";
import TapsWidget from "./TapsWidget";
import MetricWidget, {metricUnit} from "./MetricWidget";
import ClockWidget, {clockOffset, clockTitle} from "./ClockWidget";
import LocationsWidget from "./LocationsWidget";
import TextWidget from "./TextWidget";
import TopologyWidget from "./TopologyWidget";

const AGGREGATIONS = {LATEST: "Now", SUM: "Total", AVERAGE: "Avg", PEAK: "Peak", DISTINCT: "Distinct"};

const title = (widget, data) => {
  if (widget.options && widget.options.title) {
    return widget.options.title;
  }

  if (widget.type === "METRIC") {
    return alertTitle(data && data.title ? data.title
      : (METRIC_TITLES[widget.options.metric] ? METRIC_TITLES[widget.options.metric] : WIDGET_TYPES.METRIC.title), widget);
  }

  if (widget.type === "CLOCK") {
    return clockTitle(widget.options.time_zone);
  }

  return WIDGET_TYPES[widget.type] ? alertTitle(WIDGET_TYPES[widget.type].title, widget) : "";
}

const meta = (widget, data, now) => {
  if (widget.type === "CLOCK") {
    return clockOffset(widget.options.time_zone, now);
  }

  if (!data) {
    return null;
  }

  switch (widget.type) {
    case "ALERTS":
      return numeral(data.total).format("0,0") + " active";
    case "TAPS":
      return numeral(data.online).format("0,0") + " of " + numeral(data.total).format("0,0") + " online";
    case "METRIC":
      return [metricUnit(data), AGGREGATIONS[data.aggregation]].filter((part) => part).join(" ");
    case "LOCATIONS":
      return numeral(data.total).format("0,0") + (data.total === 1 ? " location" : " locations");
    default:
      return null;
  }
}

const body = (widget, data, now, clockFormat, windowLabel, setLiveMeta) => {
  // Clocks and texts have no data. The topology gets its own.
  if (widget.type === "CLOCK") {
    return <ClockWidget timeZone={widget.options.time_zone} now={now} clockFormat={clockFormat} />
  }

  if (widget.type === "TEXT") {
    return <TextWidget text={widget.options.text} size={widget.options.size} />
  }

  if (widget.type === "TOPOLOGY") {
    return <TopologyWidget widget={widget} onMeta={setLiveMeta} />
  }

  if (data === undefined) {
    return <div className="dashboard-widget-note">Loading</div>
  }

  if (data === null) {
    return <div className="dashboard-widget-note">Not available</div>
  }

  switch (widget.type) {
    case "STATUS":
      return <StatusWidget data={data} />
    case "ALERTS":
      return <AlertsWidget data={data} now={now} />
    case "TAPS":
      return <TapsWidget data={data} now={now} />
    case "METRIC":
      return <MetricWidget data={data} windowLabel={windowLabel} />
    case "LOCATIONS":
      return <LocationsWidget data={data} now={now} clockFormat={clockFormat} />
    default:
      return <div className="dashboard-widget-note">Not available</div>
  }
}

export default function DashboardWidget({widget, data, now, clockFormat, windowLabel}) {

  const [liveMeta, setLiveMeta] = useState(null);

  return (
    <div className={"dashboard-widget dashboard-widget-" + widget.type.toLowerCase()}>
      <div className="dashboard-widget-head">
        <span className="dashboard-widget-title">{title(widget, data)}</span>
        {liveMeta ?
          <span className={"dashboard-widget-meta" + (liveMeta.warn ? " dashboard-text-warn" : "")}>{liveMeta.text}</span>
          : <span className="dashboard-widget-meta">{meta(widget, data, now)}</span>}
      </div>
      <div className="dashboard-widget-body">{body(widget, data, now, clockFormat, windowLabel, setLiveMeta)}</div>
    </div>
  )

}
