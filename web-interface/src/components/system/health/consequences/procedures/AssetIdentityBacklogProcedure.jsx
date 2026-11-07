import {Link} from "react-router-dom";
import React from "react";
import SolutionCounter from "./layout/SolutionCounter";
import ApiRoutes from "../../../../../util/ApiRoutes";

function AssetIdentityBacklogProcedure() {
  return (
      <ol className="consequence-solution-procedure">
        <li>
          <SolutionCounter counter="1" /> A short backlog right after an upgrade or after enabling MAC change
          detection is expected: every asset that appeared inside the lookback window is scheduled at once. Wait
          for the indicator to extinguish on its own before taking further steps.
        </li>
        <li>
          <SolutionCounter counter="2" /> Check the Nzyme node logs for errors of the asset identity thread. A
          database error on every run leaves all evaluations due.
        </li>
        <li>
          <SolutionCounter counter="3" /> Review how many new assets the affected tenants see. Networks with many
          randomized MAC addresses, for example guest Wi-Fi, create a new asset for every visit. Consider reducing
          the lookback window of MAC change detection in the{' '}
          <Link to={ApiRoutes.ETHERNET.ASSETS.SETTINGS}>asset settings</Link>, or excluding such address ranges
          through the Identity Exclusions of the assets section.
        </li>
        <li>
          <SolutionCounter counter="4" /> If MAC change detection is not needed for a tenant, disable it in the
          asset settings. Pending evaluations of a disabled tenant are discarded.
        </li>
        <li>
          <SolutionCounter counter="5" /> The indicator will extinguish within 60 seconds after no evaluation is
          overdue.
        </li>
      </ol>
  )
}

export default AssetIdentityBacklogProcedure;
