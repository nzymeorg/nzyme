import React from "react";
import moment from "moment-timezone";
import numeral from "numeral";
import {temperatureInUserUnitSystem} from "../../../../util/UserUnitSystem";
import useSizeEm from "./useSizeEm";

const ROW_HEIGHT_EM = 1.9;
const MORE_HEIGHT_EM = 1.3;

// A screen has no user, so both units are shown.
const weather = (environment) => {
  if (!environment) {
    return null;
  }

  if (environment.alerts && environment.alerts.length > 0) {
    const more = environment.alerts.length - 1;

    return <span className="dashboard-text-warn">{environment.alerts[0]}{more > 0 ? " +" + more : ""}</span>
  }

  if (!environment.condition) {
    return null;
  }

  return (
    <span>
      {environment.condition.display_name}{' '}
      {numeral(environment.temperature).format("0")}&deg;C{' '}
      {numeral(temperatureInUserUnitSystem(environment.temperature, {unit_system: "imperial"})).format("0")}&deg;F
    </span>
  )
}

export default function LocationsWidget({data, now, clockFormat}) {

  const [ref, size] = useSizeEm();

  if (data.total === 0) {
    return <div className="dashboard-widget-note">No locations</div>
  }

  const fitting = Math.floor(size.height/ROW_HEIGHT_EM);
  const shown = data.total > fitting ? Math.max(0, Math.floor((size.height - MORE_HEIGHT_EM)/ROW_HEIGHT_EM)) : fitting;
  const locations = data.locations.slice(0, shown);
  const hidden = data.total - locations.length;

  return (
    <div className="dashboard-locations">
      <div className="dashboard-locations-head">
        <span>Location</span>
        <span>Taps</span>
        <span>Alerts</span>
        <span className="dashboard-locations-column-time">Time</span>
        <span className="dashboard-locations-column-weather">Weather</span>
      </div>

      <div className="dashboard-locations-rows" ref={ref}>
        {locations.map((location, i) => {
          const offline = location.taps_total - location.taps_online;
          const time = location.time_zone && moment.tz.zone(location.time_zone)
            ? moment.tz(now, location.time_zone).format(clockFormat === "H12" ? "h:mm A" : "HH:mm") : null;

          return (
            <div className="dashboard-locations-row" key={i}>
              <span className="dashboard-locations-name">{location.name}</span>
              <span className={location.taps_total === 0 ? "dashboard-text-faint" : (offline > 0 ? "dashboard-text-warn" : "")}>
                {numeral(location.taps_online).format("0,0")}/{numeral(location.taps_total).format("0,0")}
              </span>
              <span className={location.active_alerts > 0 ? "dashboard-text-alert" : "dashboard-text-faint"}>
                {numeral(location.active_alerts).format("0,0")}
              </span>
              <span className="dashboard-locations-column-time">{time}</span>
              <span className="dashboard-locations-column-weather dashboard-locations-weather">
                {weather(location.environment)}
              </span>
            </div>
          )
        })}

        {hidden > 0 ?
          <div className="dashboard-widget-more">+{numeral(hidden).format("0,0")} more</div> : null}
      </div>
    </div>
  )

}
