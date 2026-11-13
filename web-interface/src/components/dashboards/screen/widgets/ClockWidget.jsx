import React from "react";
import moment from "moment-timezone";

export const clockTitle = (timeZone) => {
  return timeZone ? timeZone.split("/").pop().replace(/_/g, " ") : "Clock";
}

export const clockOffset = (timeZone, now) => {
  if (!timeZone || !moment.tz.zone(timeZone)) {
    return null;
  }

  const minutes = moment.tz(now, timeZone).utcOffset();
  const hours = Math.floor(Math.abs(minutes)/60);
  const rest = Math.abs(minutes) % 60;

  return "UTC" + (minutes < 0 ? "-" : "+") + hours + (rest ? ":" + String(rest).padStart(2, "0") : "");
}

export default function ClockWidget({timeZone, now, clockFormat}) {

  if (!timeZone || !moment.tz.zone(timeZone)) {
    return <div className="dashboard-widget-note">Not available</div>
  }

  const time = moment.tz(now, timeZone);

  return (
    <div className="dashboard-clock">
      <div className="dashboard-clock-time">
        {time.format(clockFormat === "H12" ? "h:mm" : "HH:mm")}
        {clockFormat === "H12" ? <small>{time.format("A")}</small> : null}
      </div>
      <div className="dashboard-clock-date">{time.format("ddd D MMM")}<br />{time.format("z")}</div>
    </div>
  )

}
