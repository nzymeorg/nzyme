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
          problem="One or more large indexes consist mostly of empty space."
          acceptableRange={[
            "No index of 100 MB or more is estimated over 60% empty",
            "Red: over 85% empty"
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
