import React from "react";
import Consequence from "../Consequence";
import AssetIdentityBacklogProcedure from "./procedures/AssetIdentityBacklogProcedure";

function AssetIdentityBacklogConsequence(props) {

  if (!props.show) {
    return null
  }

  return (
      <Consequence
          indicator="Asset Identity Backlog"
          color="orange"
          problem="MAC change detection schedules an evaluation for every new asset and processes a bounded number of
                  evaluations per minute. Scheduled evaluations have been waiting for longer than expected, so the
                  detection is not keeping up with the number of new assets, or the asset identity thread is not
                  running."
          acceptableRange={[
            "A scheduled evaluation is processed within 15 minutes of becoming due"
          ]}
          consequences={[
            "MAC changes are detected late. The orange state means a delay of more than 15 minutes, the red state " +
            "a delay of more than two hours.",
            "No evaluation is skipped. The backlog is worked off oldest first once the detection catches up."
          ]}
          procedure={<AssetIdentityBacklogProcedure />}
      />
  )

}

export default AssetIdentityBacklogConsequence;
