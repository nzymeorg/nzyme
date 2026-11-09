import {Link} from "react-router-dom";
import React from "react";
import SolutionCounter from "./layout/SolutionCounter";
import Conditional from "./layout/Conditional";
import ApiRoutes from "../../../../../util/ApiRoutes";

function DatabaseVacuumProcedure() {
  return (
      <ol className="consequence-solution-procedure">
        <li>
          <SolutionCounter counter="1" /> Identify tables with dead rows in the <em>Tables</em> section of
          the <Link to={ApiRoutes.SYSTEM.DATABASE.HEALTH}>database health page</Link>.
        </li>
        <li>
          <SolutionCounter counter="2" /> <Conditional text="If" /> automatic vacuum is enabled, wait for the
          maintenance log to show the vacuum.
        </li>
        <li>
          <SolutionCounter counter="3" /> <Conditional text="Or" /> vacuum the table now using
          the <em>Vacuum</em> button.
        </li>
        <li>
          <SolutionCounter counter="4" /> <Conditional text="If" /> the indicator recurs, apply the autovacuum settings
          listed in the <em>Server Configuration</em> section <Conditional text="And" /> check the <em>Database
          Transactions</em> indicator for a long open transaction.
        </li>
        <li>
          <SolutionCounter counter="5" /> Indicator will extinguish within 2 minutes after problem resolution
        </li>
      </ol>
  )
}

export default DatabaseVacuumProcedure;
