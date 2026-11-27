import React from "react";
import Consequence from "../Consequence";
import NodeHeapProcedure from "./procedures/NodeHeapProcedure";

function NodeHeapConsequence(props) {

  if (!props.show) {
    return null
  }

  return (
      <Consequence
          indicator="Node Heap"
          color="orange"
          problem="One or more nodes are short on JVM heap or on memory, or do not report their heap measurements."
          acceptableRange={[
            "Less than 10% of the last 10 minutes spent in garbage collection pauses",
            "Less than 95% of the heap in use after at least one garbage collection of the last 10 minutes",
            "Less than 10% of the last 10 minutes stalled waiting for memory (hosts with pressure stall information)",
            "Every running node reports its measurements",
            "Red: a node exceeds both the pause time and the heap usage"
          ]}
          consequences={[
            "Slow processing of tap reports and slow web interface",
            "Out of memory errors and node crashes"
          ]}
          procedure={<NodeHeapProcedure />}
      />
  )

}

export default NodeHeapConsequence;
