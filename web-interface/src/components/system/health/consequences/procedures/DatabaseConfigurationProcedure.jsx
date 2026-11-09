import {Link} from "react-router-dom";
import React from "react";
import SolutionCounter from "./layout/SolutionCounter";
import ApiRoutes from "../../../../../util/ApiRoutes";

function DatabaseConfigurationProcedure() {
  return (
      <ol className="consequence-solution-procedure">
        <li>
          <SolutionCounter counter="1" /> Identify the required settings in the <em>Server Configuration</em> section
          of the <Link to={ApiRoutes.SYSTEM.DATABASE.HEALTH}>database health page</Link>.
        </li>
        <li>
          <SolutionCounter counter="2" /> Change the listed parameters in <code>postgresql.conf</code> on the database
          server. Run <code>SHOW config_file;</code> to locate the file.
        </li>
        <li>
          <SolutionCounter counter="3" /> Restart PostgreSQL.
        </li>
        <li>
          <SolutionCounter counter="4" /> Indicator will extinguish within 2 minutes after problem resolution
        </li>
      </ol>
  )
}

export default DatabaseConfigurationProcedure;
