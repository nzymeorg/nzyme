import React from "react";

function SignalStrength(props) {

  const strength = parseFloat(props.strength);
  const selectedTapCount = props.selectedTapCount;
  const filterElement = props.filterElement;

  let strengthClassName;
  if (strength > -50) {
    strengthClassName = "signal-strength-good";
  } else if (strength <= -50 && strength >= -71) {
    strengthClassName = "signal-strength-ok";
  } else if (strength < -71) {
    strengthClassName = "signal-strength-bad";
  }

  if (selectedTapCount === null || selectedTapCount > 1) {
    return (
        <span title="Not available when multiple taps are selected." style={{cursor: "help"}}>
          <i className="fa-solid fa-signal text-muted"></i> N/A
        </span>
    )
  }

  return (
      <React.Fragment>
        <i className={"fa-solid fa-signal " + strengthClassName }></i>{' '}
        {Math.round(strength)} dBm
        {filterElement ? filterElement : null}
      </React.Fragment>
  )

}

export default SignalStrength;