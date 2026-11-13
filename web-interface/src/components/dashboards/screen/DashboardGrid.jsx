import React from "react";
import DashboardWidget from "./widgets/DashboardWidget";
import {WINDOWS} from "./widgets/WidgetCatalog";

export default function DashboardGrid({widgets, data, now, windowMinutes, clockFormat = "H24", stale = false}) {

  const timeWindow = WINDOWS.find((w) => w.minutes === windowMinutes);

  return (
    <div className={"dashboard-grid" + (stale ? " dashboard-grid-stale" : "")}>
      {widgets.map((widget) => {
        return (
          <div key={widget.id} className="dashboard-grid-cell"
               style={{gridColumn: `${widget.x + 1} / span ${widget.w}`, gridRow: `${widget.y + 1} / span ${widget.h}`}}>
            <DashboardWidget widget={widget}
                             data={data ? data[widget.id] : undefined}
                             now={now}
                             clockFormat={clockFormat}
                             windowLabel={timeWindow ? timeWindow.short : null} />
          </div>
        )
      })}
    </div>
  )

}
