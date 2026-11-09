import {Link} from "react-router-dom";
import React from "react";
import SolutionCounter from "./layout/SolutionCounter";
import Conditional from "./layout/Conditional";
import ApiRoutes from "../../../../../util/ApiRoutes";

function DatabaseTransactionsProcedure() {
  return (
      <ol className="consequence-solution-procedure">
        <li>
          <SolutionCounter counter="1" /> Identify the oldest open transaction and the transaction ID age in
          the <em>Server</em> section of the <Link to={ApiRoutes.SYSTEM.DATABASE.HEALTH}>database health page</Link>.
        </li>
        <li>
          <SolutionCounter counter="2" /> <Conditional text="If" /> a transaction is open for a long time, find it
          with <code>SELECT pid, xact_start, left(query, 80) FROM pg_stat_activity WHERE xact_start IS NOT NULL ORDER BY xact_start;</code> and
          close the client <Conditional text="Or" /> end it with <code>SELECT pg_terminate_backend(pid);</code>.
        </li>
        <li>
          <SolutionCounter counter="3" /> <Conditional text="If" /> the transaction ID age is high, confirm that
          autovacuum is on and run <code>VACUUM FREEZE;</code> as the database owner.
        </li>
        <li>
          <SolutionCounter counter="4" /> <Conditional text="If" /> the age keeps growing, drop unused replication
          slots listed by <code>SELECT * FROM pg_replication_slots;</code>.
        </li>
        <li>
          <SolutionCounter counter="5" /> Indicator will extinguish within 2 minutes after problem resolution
        </li>
      </ol>
  )
}

export default DatabaseTransactionsProcedure;
