import React from "react";
import numeral from "numeral";
import useSizeEm from "./useSizeEm";

const ROW_HEIGHT_EM = 2.5;
const MORE_HEIGHT_EM = 1.3;

const two = (value) => String(value).padStart(2, "0");

export const alertAge = (firstSeen, now) => {
  const seconds = Math.max(0, Math.floor((now - new Date(firstSeen).getTime())/1000));
  const hours = Math.floor(seconds/3600);

  if (hours >= 100) {
    return "T+" + Math.floor(hours/24) + "D " + two(hours % 24) + "H";
  }

  return "T+" + two(hours) + ":" + two(Math.floor(seconds/60) % 60) + ":" + two(seconds % 60);
}

export default function AlertsWidget({data, now}) {

  const [ref, size] = useSizeEm();

  if (data.total === 0) {
    return <div className="dashboard-widget-note">No active alerts</div>
  }

  const redacted = data.alerts.every((alert) => !alert.details);

  const fitting = Math.floor(size.height/ROW_HEIGHT_EM);
  const shown = data.total > fitting ? Math.max(0, Math.floor((size.height - MORE_HEIGHT_EM)/ROW_HEIGHT_EM)) : fitting;
  const alerts = data.alerts.slice(0, shown);
  const hidden = data.total - alerts.length;

  return (
    <div className={"dashboard-alerts" + (redacted ? " dashboard-alerts-redacted" : "")}>
      <div className="dashboard-alerts-head">
        <span>Age</span>
        <span>Alert</span>
        {redacted ? null : <span className="dashboard-alerts-column-detail">Detail</span>}
      </div>

      <div className="dashboard-alerts-rows" ref={ref}>
        {alerts.map((alert, i) => {
          return (
            <div className="dashboard-alerts-row" key={i}>
              <span className="dashboard-alerts-age">
                <i className="dashboard-mark"></i>{alertAge(alert.first_seen, now)}
              </span>
              <span className="dashboard-alerts-title">{alert.title}</span>
              {redacted ? null :
                <span className="dashboard-alerts-detail dashboard-alerts-column-detail">
                  {alert.details ? alert.details.replace(/\.$/, "") : null}
                </span>}
            </div>
          )
        })}

        {hidden > 0 ?
          <div className="dashboard-widget-more">+{numeral(hidden).format("0,0")} more</div> : null}
      </div>
    </div>
  )

}
