import React from "react";
import Consequence from "../Consequence";
import SegmentationLagProcedure from "./procedures/SegmentationLagProcedure";

function SegmentationLagConsequence(props) {

  if (!props.show) {
    return null
  }

  return (
      <Consequence
          indicator="Segmentation Lag"
          color="orange"
          problem="The evaluation of recorded TCP/UDP sessions is behind and not catching up for at least one tenant,
                  or has stopped for all tenants."
          acceptableRange={[
            "Evaluation at most 20 minutes behind for every tenant",
            "Behind but catching up, as on first start or after a node restart",
            "Red: no tenant evaluated for 15 minutes"
          ]}
          consequences={[
            "Segmentation overview, findings and alerts are late",
            "Sessions that leave the retention time of TCP/UDP session data before they are evaluated are never " +
            "evaluated"
          ]}
          procedure={<SegmentationLagProcedure />}
      />
  )

}

export default SegmentationLagConsequence;
