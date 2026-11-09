import React from "react";
import Consequence from "../Consequence";
import DatabaseVacuumProcedure from "./procedures/DatabaseVacuumProcedure";

function DatabaseVacuumConsequence(props) {

  if (!props.show) {
    return null
  }

  return (
      <Consequence
          indicator="Database Vacuum"
          color="orange"
          problem="One or more large tables have too many dead rows."
          acceptableRange={[
            "No table of 100 MB or more has over 20% dead rows",
            "Red: over 50% dead rows"
          ]}
          consequences={[
            "Wasted disk space",
            "Slower queries on the affected tables",
            "Index bloat if the state persists"
          ]}
          procedure={<DatabaseVacuumProcedure />}
      />
  )

}

export default DatabaseVacuumConsequence;
