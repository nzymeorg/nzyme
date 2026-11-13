import React from "react";
import moment from "moment-timezone";
import numeral from "numeral";
import DashboardGrid from "./DashboardGrid";
import {plural} from "../../briefing/briefingFormat";
import {WINDOWS} from "./widgets/WidgetCatalog";

const counted = (count, singular, pluralForm) => numeral(count).format("0,0") + " " + plural(count, singular, pluralForm);

const banner = (condition) => {
  switch (condition.level) {
    case "ALERT":
      return counted(condition.active_alerts, "active alert", "active alerts");
    case "WARN":
      return condition.taps_total === 0 ? "No taps"
        : counted(condition.taps_total - condition.taps_online, "tap offline", "taps offline");
    default:
      return condition.active_alerts === null ? "All taps online" : "No active alerts";
  }
}

const scope = (content) => {
  switch (content.scope_type) {
    case "LOCATIONS":
      return counted(content.tap_count, "tap", "taps") + " at " + counted(content.scope_count, "location", "locations");
    case "TAPS":
      return counted(content.tap_count, "tap", "taps");
    default:
      return "All taps";
  }
}

// Not written as 00:25, which reads as a time of day on a screen full of clocks.
const duration = (ms) => {
  const seconds = Math.max(0, Math.floor(ms/1000));

  if (seconds < 600) {
    return seconds + "s";
  }

  return seconds < 7200 ? Math.floor(seconds/60) + "m" : Math.floor(seconds/3600) + "h";
}

export default function DashboardScreen({name = null, message = null, content = null, stale = false, status = null,
                                          display = null, time = null, timeZone = null, clockFormat = "H24",
                                          scalePercent = 100, footerLeft = null, footerRight = null,
                                          hideCursor = false}) {

  const zone = timeZone && moment.tz.zone(timeZone) ? timeZone : moment.tz.guess();
  const now = time ? moment.tz(time, zone) : null;
  const timeFormat = clockFormat === "H12" ? "h:mm:ss A" : "HH:mm:ss";

  const notice = message ? message
    : (content && content.widgets.length === 0 ? "This dashboard has no widgets." : null);
  const showsContent = content && !notice;

  const timeWindow = content ? WINDOWS.find((w) => w.minutes === content.window_minutes) : null;
  const level = showsContent ? content.condition.level.toLowerCase() : "none";

  return (
    <div className={"dashboard-screen dashboard-level-" + level + (hideCursor ? " dashboard-screen-nocursor" : "")}
         style={{"--dashboard-screen-scale": scalePercent/100}}>
      {showsContent ? <div className="dashboard-banner">{banner(content.condition)}</div> : null}

      {showsContent ?
        <React.Fragment>
          <div className="dashboard-header">
            <div style={{minWidth: 0}}>
              <div className="dashboard-header-name">{name}</div>
              <div className="dashboard-facts">
                {timeWindow ? <span>Window<b>{timeWindow.short}</b></span> : null}
                <span>Scope<b>{scope(content)}</b></span>
                {display ? <span>Display<b>{display}</b></span> : null}
              </div>
            </div>

            {status ?
              <div className="dashboard-header-status">
                {status.rotation ?
                  <React.Fragment>
                    <div>
                      <span className="v">{status.rotation.position}/{status.rotation.count}</span>
                      <span className="l">Dashboard</span>
                    </div>
                    <div>
                      <span className="v">{duration(status.rotation.nextMs)}</span>
                      <span className="l">Next in</span>
                    </div>
                  </React.Fragment> : null}
                <div className={stale || !status.connected ? "dashboard-text-warn" : ""}>
                  <span className="v">{duration(status.dataAgeMs)}</span>
                  <span className="l">{status.connected ? "Data age" : "No contact"}</span>
                </div>
              </div> : null}
          </div>

          <DashboardGrid widgets={content.widgets}
                         data={content.data}
                         now={time}
                         windowMinutes={content.window_minutes}
                         clockFormat={clockFormat}
                         stale={stale} />
        </React.Fragment>
        :
        <div className="dashboard-screen-content">
          <div className="dashboard-screen-title">Dashboard</div>

          {name ? <div className="dashboard-screen-name">{name}</div> : null}

          {notice ? <div className="dashboard-screen-message">{notice}</div> : null}

          {now ?
            <React.Fragment>
              <div className="dashboard-screen-clock">{now.format(timeFormat)}</div>
              <div className="dashboard-screen-date">{now.format("dddd, MMMM D, YYYY")} ({now.format("z")})</div>
            </React.Fragment> : null}
        </div>}

      {footerLeft || footerRight ?
        <div className="dashboard-screen-footer">
          <span>{footerLeft}</span>
          <span>{footerRight}</span>
        </div> : null}

      {showsContent ? <div className="dashboard-banner">{banner(content.condition)}</div> : null}
    </div>
  )

}
