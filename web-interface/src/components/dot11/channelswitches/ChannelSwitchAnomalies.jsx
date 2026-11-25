import React from "react";
import {channelSwitchAnomalyDescription, channelSwitchAnomalyToName} from "./ChannelSwitchNames";

export default function ChannelSwitchAnomalies({anomalies}) {

  if (!anomalies || anomalies.length === 0) {
    return <span className="text-muted">None</span>
  }

  return (
      <React.Fragment>
        {anomalies.map((anomaly, i) => {
          return (
              <React.Fragment key={anomaly}>
                <span title={channelSwitchAnomalyDescription(anomaly)} style={{cursor: "help"}}>
                  {channelSwitchAnomalyToName(anomaly)}
                </span>
                {i < anomalies.length - 1 ? ", " : null}
              </React.Fragment>
          )
        })}
      </React.Fragment>
  )

}
