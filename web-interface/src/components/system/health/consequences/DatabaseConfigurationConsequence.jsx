import React from "react";
import Consequence from "../Consequence";
import DatabaseConfigurationProcedure from "./procedures/DatabaseConfigurationProcedure";

function DatabaseConfigurationConsequence(props) {

  if (!props.show) {
    return null
  }

  return (
      <Consequence
          indicator="Database Configuration"
          color="orange"
          problem="One or more PostgreSQL server settings are below the Nzyme requirements."
          acceptableRange={[
            "All required settings on the database health page are met",
            "PostgreSQL 15 or newer",
            "Red: autovacuum is off"
          ]}
          consequences={[
            "Slow queries and detection runs",
            "Vacuum falls behind, tables and indexes grow"
          ]}
          procedure={<DatabaseConfigurationProcedure />}
      />
  )

}

export default DatabaseConfigurationConsequence;
