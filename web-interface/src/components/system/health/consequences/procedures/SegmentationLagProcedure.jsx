import {Link} from "react-router-dom";
import React from "react";
import SolutionCounter from "./layout/SolutionCounter";
import Conditional from "./layout/Conditional";
import Indicator from "./layout/Indicator";
import ApiRoutes from "../../../../../util/ApiRoutes";

function SegmentationLagProcedure() {
  return (
      <ol className="consequence-solution-procedure">
        <li>
          <SolutionCounter counter="1" /> Check the Nzyme node logs for errors of the segmentation thread.
        </li>
        <li>
          <SolutionCounter counter="2" /> Check the <em>Segmentation: Evaluation Window</em> timer on the details
          page of each node in the <Link to={ApiRoutes.SYSTEM.CLUSTER.INDEX}>nodes overview</Link>.
        </li>
        <li>
          <SolutionCounter counter="3" /> <Conditional text="If" /> the timer is in the range of seconds,
          fix <Indicator text="DB Performance" /> <Conditional text="And" /> <Indicator text="DB Vacuum" /> first.
        </li>
        <li>
          <SolutionCounter counter="4" /> Indicator will extinguish within 60 seconds after problem resolution
        </li>
      </ol>
  )
}

export default SegmentationLagProcedure;
