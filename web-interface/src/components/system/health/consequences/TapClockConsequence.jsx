import React from "react";
import Consequence from "../Consequence";
import TapClockProcedure from "./procedures/TapClockProcedure";

function TapClockConsequence(props) {

  if (!props.show) {
    return null
  }

  return (
      <Consequence
          indicator="Tap Clock"
          color="red"
          problem="At least one Nzyme tap has a local system clock that is not synchronized with reference world time."
          acceptableRange={[
            String.fromCharCode(177) + "5000 ms of drift from reference world time"
          ]}
          consequences={[
            "Tap will report false timestamps on recorded data"
          ]}
          procedure={<TapClockProcedure />}
      />
  )

}

export default TapClockConsequence;