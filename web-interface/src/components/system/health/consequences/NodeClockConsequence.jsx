import React from "react";
import Consequence from "../Consequence";
import NodeClockProcedure from "./procedures/NodeClockProcedure";

function NodeClockConsequence(props) {

  if (!props.show) {
    return null
  }

  return (
      <Consequence
          indicator="Node Clock"
          color="red"
          problem="At least one Nzyme node has a local system clock that is not synchronized with reference world time."
          acceptableRange={[
            String.fromCharCode(177) + "5000 ms of drift from reference world time"
          ]}
          consequences={[
            "Node will not reliably participate in cluster task work queue processing",
            "Node will not reliably be detected as online or offline"
          ]}
          procedure={<NodeClockProcedure />}
      />
  )

}

export default NodeClockConsequence;