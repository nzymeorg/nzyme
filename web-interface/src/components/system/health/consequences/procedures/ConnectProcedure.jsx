import {Link} from "react-router-dom";
import React from "react";
import SolutionCounter from "./layout/SolutionCounter";
import ApiRoutes from "../../../../../util/ApiRoutes";

function ConnectProcedure() {
  return (
      <ol className="consequence-solution-procedure">
        <li>
          <SolutionCounter counter="1" /> Open the <Link to={ApiRoutes.SYSTEM.CONNECT}>Connect</Link> page and check
          when the last report was accepted and whether the API key is still valid.
        </li>
        <li>
          <SolutionCounter counter="2" /> Make sure every nzyme node can reach the Connect API over HTTPS. Check
          firewalls, proxies and DNS resolution on the nodes.
        </li>
        <li>
          <SolutionCounter counter="3" /> Check the node logs for errors of the Connect status reporter.
        </li>
        <li>
          <SolutionCounter counter="4" /> If you no longer want to use Connect, disable it on the Connect page. The
          indicator is not applicable while Connect is disabled.
        </li>
      </ol>
  )
}

export default ConnectProcedure;
