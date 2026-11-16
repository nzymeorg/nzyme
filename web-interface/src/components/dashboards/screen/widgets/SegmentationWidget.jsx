import React from "react";
import numeral from "numeral";

const pad = (value) => numeral(value).format(value < 100 ? "00" : "0,0");

const STATES = {CROSSED: "Crossed", ATTEMPTED: "Attempted"};

export default function SegmentationWidget({data}) {

  if (data.crossed + data.attempted + data.holding + data.not_observable + data.audit + data.paused === 0) {
    return <div className="dashboard-widget-note">No policies</div>
  }

  const more = data.crossed + data.attempted - (data.policies ? data.policies.length : 0);

  return (
    <div className="dashboard-segmentation">
      <div className="dashboard-segmentation-readouts">
        <div>
          <span className={"v" + (data.crossed > 0 ? " dashboard-text-alert" : "")}>{pad(data.crossed)}</span>
          <span className="l">Crossed</span>
        </div>
        <div>
          <span className={"v" + (data.attempted > 0 ? " dashboard-text-warn" : "")}>{pad(data.attempted)}</span>
          <span className="l">Attempted</span>
        </div>
        <div>
          <span className="v">{pad(data.holding)}</span>
          <span className="l">Holding</span>
        </div>
        <div>
          <span className={"v" + (data.not_observable > 0 ? " dashboard-text-warn" : "")}>
            {pad(data.not_observable)}
          </span>
          <span className="l">Not observable</span>
        </div>
      </div>

      {data.policies && data.policies.length > 0 ?
        <div className="dashboard-segmentation-policies">
          {data.policies.map((policy) => {
            return (
              <div key={policy.name}>
                <span className={policy.state === "CROSSED" ? "dashboard-text-alert" : "dashboard-text-warn"}>
                  {STATES[policy.state] ? STATES[policy.state] : policy.state}
                </span>{" "}
                {policy.name}
              </div>
            )
          })}
          {more > 0 ? <div>and {more} more</div> : null}
        </div> : null}
    </div>
  )

}
