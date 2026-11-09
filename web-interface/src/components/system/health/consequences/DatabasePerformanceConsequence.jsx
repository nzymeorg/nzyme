import React from "react";
import Consequence from "../Consequence";
import DatabasePerformanceProcedure from "./procedures/DatabasePerformanceProcedure";

function DatabasePerformanceConsequence(props) {

  if (!props.show) {
    return null
  }

  return (
      <Consequence
          indicator="Database Performance"
          color="orange"
          problem="The PostgreSQL server shows signs of insufficient memory, write-ahead log size or disk speed."
          acceptableRange={[
            "Cache hit ratio of 90% or more",
            "Under 100 MB of temporary files per minute",
            "Under half of all checkpoints forced",
            "Under 30 seconds of disk wait per minute",
            "Red: cache hit ratio below 70%",
            "Not calculated for 15 minutes after a PostgreSQL restart"
          ]}
          consequences={[
            "Slow pages, detection runs and tap report processing",
            "Tap reports can back up"
          ]}
          procedure={<DatabasePerformanceProcedure />}
      />
  )

}

export default DatabasePerformanceConsequence;
