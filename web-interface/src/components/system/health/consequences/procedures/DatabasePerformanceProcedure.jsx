import {Link} from "react-router-dom";
import React from "react";
import SolutionCounter from "./layout/SolutionCounter";
import Conditional from "./layout/Conditional";
import ApiRoutes from "../../../../../util/ApiRoutes";

function DatabasePerformanceProcedure() {
  return (
      <ol className="consequence-solution-procedure">
        <li>
          <SolutionCounter counter="1" /> Identify the affected metric in the <em>Server</em> section of
          the <Link to={ApiRoutes.SYSTEM.DATABASE.HEALTH}>database health page</Link>.
        </li>
        <li>
          <SolutionCounter counter="2" /> <Conditional text="If" /> cache hit ratio is low,
          raise <code>shared_buffers</code> to a quarter of the server memory and restart PostgreSQL.
        </li>
        <li>
          <SolutionCounter counter="3" /> <Conditional text="If" /> temporary file volume is high,
          raise <em>work_mem of Nzyme connections</em> in the database settings and restart all nodes.
        </li>
        <li>
          <SolutionCounter counter="4" /> <Conditional text="If" /> forced checkpoints are frequent,
          set <code>max_wal_size</code> to 8GB and reload PostgreSQL.
        </li>
        <li>
          <SolutionCounter counter="5" /> <Conditional text="If" /> disk wait is high and cache hit ratio is good,
          move the database to faster storage.
        </li>
        <li>
          <SolutionCounter counter="6" /> Indicator will extinguish within 15 minutes after problem resolution
        </li>
      </ol>
  )
}

export default DatabasePerformanceProcedure;
