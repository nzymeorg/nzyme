import React from "react";
import numeral from "numeral";

// Roman numerals, because a digit next to "Alert" reads as a number of alerts.
const LADDER = [
  {level: "ALERT", label: "Alert", numeral: "III", className: "dashboard-ladder-alert"},
  {level: "WARN", label: "Attention", numeral: "II", className: "dashboard-ladder-warn"},
  {level: "OK", label: "Normal", numeral: "I", className: ""}
];

const pad = (value) => numeral(value).format(value < 100 ? "00" : "0,0");

export default function StatusWidget({data}) {

  const level = LADDER.some((step) => step.level === data.level) ? data.level : "WARN";
  const tapsOffline = data.taps_total - data.taps_online;

  return (
    <div className="dashboard-condition">
      <div className="dashboard-ladder">
        {LADDER.map((step) => {
          return (
            <div key={step.level}
                 className={step.level === level ? "dashboard-ladder-on " + step.className : ""}>
              {step.label}<span>{step.numeral}</span>
            </div>
          )
        })}
      </div>

      <div className="dashboard-readouts">
        {data.active_alerts === null ? null :
          <div>
            <span className={"v" + (data.active_alerts > 0 ? " dashboard-text-alert" : "")}>
              {pad(data.active_alerts)}
            </span>
            <span className="l">Active alerts</span>
          </div>}
        <div>
          <span className={"v" + (tapsOffline > 0 || data.taps_total === 0 ? " dashboard-text-warn" : "")}>
            {pad(data.taps_online)}/{pad(data.taps_total)}
          </span>
          <span className="l">Taps online</span>
        </div>
      </div>
    </div>
  )

}
