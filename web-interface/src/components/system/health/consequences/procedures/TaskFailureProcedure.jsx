import {Link} from "react-router-dom";
import React from "react";
import SolutionCounter from "./layout/SolutionCounter";
import Conditional from "./layout/Conditional";
import ApiRoutes from "../../../../../util/ApiRoutes";

function TaskFailureProcedure() {
  return (
      <ol className="consequence-solution-procedure">
        <li>
          <SolutionCounter counter="1" /> Identify failed cluster tasks using
          the <Link to={ApiRoutes.SYSTEM.CLUSTER.MESSAGING.INDEX}>Cluster Messaging</Link> page.
        </li>
        <li>
          <SolutionCounter counter="2" /> <Conditional text="For each" /> failed cluster task perform:
        </li>
        <li className="consequence-solution-sublist">
          <ol>
            <li><SolutionCounter counter="2.1" /> Review failed task and it's consequences. Consider manually restarting
              the failed action if that is possible. For example, manually trigger a new report run if a report task
              failed.</li>
            <li><SolutionCounter counter="2.2" /> Mark the failed task as acknowledged to silence this indicator.</li>
          </ol>
        </li>
        <li>
          <SolutionCounter counter="3" /> The indicator will extinguish within 60 seconds after no failed tasks are
          found.
        </li>
      </ol>
  )
}

export default TaskFailureProcedure;