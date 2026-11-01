import React from "react";
import Consequence from "../Consequence";
import TapThroughputProcedure from "./procedures/TapThroughputProcedure";

function TapOfflineConsequence(props) {

  if (!props.show) {
    return null
  }

  return (
      <Consequence
          indicator="Tap Throughput"
          color="orange"
          problem="At least one Nzyme tap is connected, but not reporting any throughput."
          acceptableRange={[
            "n/a"
          ]}
          consequences={[
            "No data is being recorded and Nzyme is not able to analyze any traffic on the affected tap(s)"
          ]}
          procedure={<TapThroughputProcedure />}
      />
  )

}

export default TapOfflineConsequence;