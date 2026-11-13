import React from "react";
import Consequence from "../Consequence";
import DatabaseBloatProcedure from "./procedures/DatabaseBloatProcedure";

function DatabaseBloatConsequence(props) {

  if (!props.show) {
    return null
  }

  return (
      <Consequence
          indicator="Database Index Bloat"
          color="orange"
          problem="One or more bloated indexes were not rebuilt in the last maintenance window."
          acceptableRange={[
            "Every index above the rebuild threshold is rebuilt in the next maintenance window",
            "Red: an index that was not rebuilt is over 85% empty"
          ]}
          consequences={[
            "Wasted disk space",
            "Slower lookups on the affected indexes"
          ]}
          procedure={<DatabaseBloatProcedure />}
      />
  )

}

export default DatabaseBloatConsequence;
