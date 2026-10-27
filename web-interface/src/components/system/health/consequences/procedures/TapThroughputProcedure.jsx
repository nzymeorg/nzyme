import {Link} from "react-router-dom";
import React from "react";
import SolutionCounter from "./layout/SolutionCounter";
import ApiRoutes from "../../../../../util/ApiRoutes";
import Conditional from "./layout/Conditional";

function TapThroughputProcedure(props) {
  return (
      <ol className="consequence-solution-procedure">
        <li>
          <SolutionCounter counter="1" /> Use the <Link to={ApiRoutes.SYSTEM.TAPS.INDEX}>taps overview page</Link> to
          identify taps that do not report any throughput.
        </li>
        <li>
          <SolutionCounter counter="2" /> <Conditional text="For each" /> tap that is reporting a <code>0.0 Mbit/sec</code>
          throughput perform:
        </li>
        <li>
          <ol>
            <li className="consequence-solution-sublist">
              <SolutionCounter counter="2.1" /> Ensure that the tap is listening for traffic on the correct local interfaces
              and that the configured local interfaces still exist.
            </li>
            <li className="consequence-solution-sublist">
              <SolutionCounter counter="2.2" /> Ensure that the tap is physically connected to a source it can read traffic from.
              For ethernet captures, make sure intercepting devices or mirror ports are configured correctly. For wireless
              captures, ensure the antenna is connected properly and that the WiFi adapter is in monitor mode.
            </li>
            <li className="consequence-solution-sublist">
              <SolutionCounter counter="2.3" /> Check the local tap log file for any errors or warnings
            </li>
          </ol>
        </li>
      </ol>
  )
}

export default TapThroughputProcedure;