import React from "react";

function Indicator(props) {

  const indicator = props.indicator;

  if (indicator && !indicator.active) {
    return <div className="hc-indicator hc-disabled">{props.name}</div>
  }

  // UNAVAILABLE means not applicable, for example Connect while Connect is off.
  if (indicator && indicator.level === "UNAVAILABLE") {
    return <div className="hc-indicator hc-disabled" title="Not applicable to this cluster">{props.name}</div>
  }

  if (!indicator || indicator.expired) {
    return <div className="hc-indicator hc-invalid">{props.name}</div>
  }

  return <div className={"hc-indicator hc-" + indicator.level.toLowerCase()}>{props.name}</div>

}

export default Indicator;