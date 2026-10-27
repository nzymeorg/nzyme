import {Link} from "react-router-dom";
import React from "react";
import SolutionCounter from "./layout/SolutionCounter";
import Conditional from "./layout/Conditional";
import ApiRoutes from "../../../../../util/ApiRoutes";
import Indicator from "./layout/Indicator";

function MessageStuckProcedure() {
  return (
      <ol className="consequence-solution-procedure">
        <li>
          <SolutionCounter counter="1" /> Identify stuck message bus messages using
          the <Link to={ApiRoutes.SYSTEM.CLUSTER.MESSAGING.INDEX}>Cluster Messaging</Link> page. A message is considered stuck
          if it has been acknowledged but did not report a success or failure result.
        </li>
        <li>
          <SolutionCounter counter="2" /> <Conditional text="For each" /> stuck message perform:
        </li>
        <li className="consequence-solution-sublist">
          <ol>
            <li><SolutionCounter counter="2.1" /> Restart the nzyme node that handles the stuck message to stop processing.
              Note that the message will report as failed after restart and the <Indicator text="Message Failure" />{' '}
              indicator will illuminate. Follow the instruction for that indicator as well.</li>
            <li><SolutionCounter counter="2.2" /> Review stuck message and it's consequences. Consider manually restarting
              the failed action if that is possible. For example, restarting the node after processing a HTTP server restart
              message failed should have resolved the situation.</li>
          </ol>
        </li>
        <li>
          <SolutionCounter counter="3" /> The indicator will extinguish within 60 seconds after no stuck messages are
          found.
        </li>
      </ol>
  )
}

export default MessageStuckProcedure;