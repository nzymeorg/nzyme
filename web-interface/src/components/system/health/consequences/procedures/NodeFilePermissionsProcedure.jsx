import React from "react";
import SolutionCounter from "./layout/SolutionCounter";
import Conditional from "./layout/Conditional";

function NodeFilePermissionsProcedure(props) {
  return (
      <ol className="consequence-solution-procedure">
        <li>
          <SolutionCounter counter="1" /> Identify affected nodes by looking for &quot;Insecure permissions&quot;
          warnings in the node log. The warning names each file and the exact command to fix it.
        </li>
        <li><SolutionCounter counter="2" /> <Conditional text="For each" /> affected node:</li>
        <li className="consequence-solution-sublist">
          <ol>
            <li>
              <SolutionCounter counter="2.1" /> Run the commands from the log as root. With default paths, these
              are <code>chmod 700 /usr/share/nzyme/crypto</code>, <code>chmod 600 /usr/share/nzyme/crypto/pgp_private.pgp</code> and <code>chmod 640 /etc/nzyme/nzyme.conf</code>.
              Everything should be owned by the user nzyme runs as.
            </li>
            <li>
              <SolutionCounter counter="2.2" /> Consider the exposed secrets compromised if untrusted users had
              access to the host. Rotate the database password and, if the PGP key was exposed, follow the
              documentation on regenerating the cluster PGP key.
            </li>
          </ol>
        </li>
        <li>
          <SolutionCounter counter={"3"} /> Indicator will extinguish within 60 seconds after problem resolution
        </li>
      </ol>
  )
}

export default NodeFilePermissionsProcedure;
