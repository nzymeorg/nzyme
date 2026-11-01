import React from "react";
import Consequence from "../Consequence";
import TapOfflineProcedure from "./procedures/TapOfflineProcedure";

function TapOfflineConsequence(props) {

  if (!props.show) {
    return null
  }

  return (
      <Consequence
          indicator="Tap Offline"
          color="orange"
          problem="At least one Nzyme tap that was active before is currently offline."
          acceptableRange={[
            "n/a"
          ]}
          consequences={[
            "If not running at all, tap will not collect data",
            "If running, but not able to reach Nzyme nodes, tap will report data with a delay once it is back online, except if it runs out of local retention time"
          ]}
          procedure={<TapOfflineProcedure />}
      />
  )

}

export default TapOfflineConsequence;