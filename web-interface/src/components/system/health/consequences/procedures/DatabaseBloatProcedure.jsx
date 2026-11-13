import {Link} from "react-router-dom";
import React from "react";
import SolutionCounter from "./layout/SolutionCounter";
import Conditional from "./layout/Conditional";
import ApiRoutes from "../../../../../util/ApiRoutes";

function DatabaseBloatProcedure() {
  return (
      <ol className="consequence-solution-procedure">
        <li>
          <SolutionCounter counter="1" /> Identify indexes marked as overdue in the <em>Indexes</em> section of
          the <Link to={ApiRoutes.SYSTEM.DATABASE.HEALTH}>database health page</Link>.
        </li>
        <li>
          <SolutionCounter counter="2" /> <Conditional text="If" /> automatic index rebuilds are disabled, enable them
          in the <Link to={ApiRoutes.SYSTEM.DATABASE.SETTINGS}>database settings</Link>.
        </li>
        <li>
          <SolutionCounter counter="3" /> <Conditional text="If" /> the maintenance log shows failed rebuilds, free
          disk space for a copy of the index.
        </li>
        <li>
          <SolutionCounter counter="4" /> <Conditional text="If" /> rebuilds ran until the end of the maintenance
          window, extend the window.
        </li>
        <li>
          <SolutionCounter counter="5" /> <Conditional text="Or" /> rebuild the index now using
          the <em>Rebuild</em> button. The rebuild needs free disk space for a copy of the index.
        </li>
        <li>
          <SolutionCounter counter="6" /> <Conditional text="If" /> bloat recurs, follow the <em>Database
          Vacuum</em> procedure.
        </li>
        <li>
          <SolutionCounter counter="7" /> Indicator will extinguish within 2 minutes after problem resolution
        </li>
      </ol>
  )
}

export default DatabaseBloatProcedure;
