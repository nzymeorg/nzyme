import React from "react";
import SolutionCounter from "./layout/SolutionCounter";
import Conditional from "./layout/Conditional";

function TapFilePermissionsProcedure(props) {
  return (
      <ol className="consequence-solution-procedure">
        <li>
          <SolutionCounter counter="1" /> Identify affected taps by looking for &quot;Insecure file permissions&quot;
          warnings in the local Nzyme tap log. The warning names the file and the command to fix it.
        </li>
        <li><SolutionCounter counter="2" /> <Conditional text="For each" /> affected tap:</li>
        <li className="consequence-solution-sublist">
          <ol>
            <li>
              <SolutionCounter counter="2.1" /> Make the file readable by its owner only, for example
              with <code>chmod 640 /etc/nzyme/nzyme-tap.conf</code> and <code>chown root:root /etc/nzyme/nzyme-tap.conf</code>.
              The file must not be readable by other users or writable by its group.
            </li>
            <li>
              <SolutionCounter counter="2.2" /> Consider the tap secret compromised if untrusted users had access
              to the host. Rotate it by creating a new tap in the Nzyme web interface and updating the tap configuration.
            </li>
          </ol>
        </li>
        <li>
          <SolutionCounter counter={"3"} /> Indicator will extinguish within 60 seconds after problem resolution
        </li>
      </ol>
  )
}

export default TapFilePermissionsProcedure;
