import {Link} from "react-router-dom";
import React from "react";
import SolutionCounter from "./layout/SolutionCounter";
import Conditional from "./layout/Conditional";
import ApiRoutes from "../../../../../util/ApiRoutes";

function DatabaseBloatProcedure() {
  return (
      <ol className="consequence-solution-procedure">
        <li>
          <SolutionCounter counter="1" /> Identify bloated indexes in the <em>Indexes</em> section of
          the <Link to={ApiRoutes.SYSTEM.DATABASE.HEALTH}>database health page</Link>.
        </li>
        <li>
          <SolutionCounter counter="2" /> <Conditional text="If" /> automatic reindexing is enabled, wait for the next
          maintenance window.
        </li>
        <li>
          <SolutionCounter counter="3" /> <Conditional text="Or" /> rebuild the index now using
          the <em>Rebuild</em> button. The rebuild needs free disk space for a copy of the index.
        </li>
        <li>
          <SolutionCounter counter="4" /> <Conditional text="If" /> bloat recurs, follow the <em>Database
          Vacuum</em> procedure.
        </li>
        <li>
          <SolutionCounter counter="5" /> Indicator will extinguish within 2 minutes after problem resolution
        </li>
      </ol>
  )
}

export default DatabaseBloatProcedure;
