import React from "react";

function MonitoredNetworkSingleAlertStatus(props) {

  const ssid = props.ssid;
  const parameter = props.parameter;

  // Optional.
  const disabledTitle = props.disabledTitle;
  const warningTitle = props.warningTitle;

  if (ssid.is_enabled && ssid["enabled_" + parameter]) {
    if (ssid["status_" + parameter]) {
      return <i className="fa-solid fa-triangle-exclamation text-danger" title="Active alerts." />
    } else if (warningTitle) {
      return <i className="fa-solid fa-triangle-exclamation text-warning" title={warningTitle} />
    } else {
      return <i className="fa-solid fa-thumbs-up text-success" title="No active alerts." />
    }
  } else {
    return <i className="fa-solid fa-triangle-exclamation text-muted"
              title={disabledTitle ? disabledTitle : "Monitoring is disabled."} />
  }

}

export default MonitoredNetworkSingleAlertStatus;