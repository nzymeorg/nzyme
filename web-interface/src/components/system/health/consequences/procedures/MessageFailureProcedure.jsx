import {Link} from "react-router-dom";
import React from "react";
import SolutionCounter from "./layout/SolutionCounter";
import Conditional from "./layout/Conditional";
import ApiRoutes from "../../../../../util/ApiRoutes";

function MessageFailureProcedure() {
  return (
      <ol className="consequence-solution-procedure">
        <li>
          <SolutionCounter counter="1" /> Identify failed message bus messages using
          the <Link to={ApiRoutes.SYSTEM.CLUSTER.MESSAGING.INDEX}>Cluster Messaging</Link> page.
        </li>
        <li>
          <SolutionCounter counter="2" /> <Conditional text="For each" /> failed message perform:
        </li>
        <li className="consequence-solution-sublist">
          <ol>
            <li><SolutionCounter counter="2.1" /> Review failed message and it's consequences. Consider manually restarting
              the failed action if that is possible. For example, manually trigger a restart if a HTTP server restart
              message failed.</li>
            <li><SolutionCounter counter="2.2" /> Mark the failed message as acknowledged to silence this indicator.</li>
          </ol>
        </li>
        <li>
          <SolutionCounter counter="3" /> The indicator will extinguish within 60 seconds after no failed messages are
          found.
        </li>
      </ol>
  )
}

export default MessageFailureProcedure;