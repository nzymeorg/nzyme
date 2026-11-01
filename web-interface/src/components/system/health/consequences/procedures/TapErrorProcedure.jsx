import React from "react";
import SolutionCounter from "./layout/SolutionCounter";
import Conditional from "./layout/Conditional";

function TapErrorProcedure(props) {
  return (
      <ol className="consequence-solution-procedure">
        <li>
          <SolutionCounter counter="1" /> Identify taps with channel processing errors using the tap details pages.
        </li>
        <li><SolutionCounter counter="2" /> <Conditional text="For each" /> tap with errors:</li>
        <li className="consequence-solution-sublist">
          <ol>
            <li>
              <SolutionCounter counter="2.1" /> Investigate the local Nzyme tap log file to see what type of errors
              occur in the channels. Please reach out to Nzyme support channels with any questions about such errors.
            </li>
          </ol>
        </li>
        <li>
          <SolutionCounter counter={"3"} /> Indicator will extinguish within 60 seconds after problem resolution
        </li>
      </ol>
  )
}

export default TapErrorProcedure;