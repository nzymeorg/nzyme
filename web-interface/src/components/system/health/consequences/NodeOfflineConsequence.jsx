import React from "react";
import Consequence from "../Consequence";
import NodeOfflineProcedure from "./procedures/NodeOfflineProcedure";

function NodeOfflineConsequence(props) {

  if (!props.show) {
    return null
  }

  return (
      <Consequence
          indicator="Node Offline"
          color="orange"
          problem="At least one Nzyme node that was active in the last 24 hours is currently offline."
          acceptableRange={[
            "n/a"
          ]}
          consequences={[
            "Node will not be available in cluster and can cause scaling or connectivity issues in certain situations and architectures"
          ]}
          procedure={<NodeOfflineProcedure />}
      />
  )

}

export default NodeOfflineConsequence;