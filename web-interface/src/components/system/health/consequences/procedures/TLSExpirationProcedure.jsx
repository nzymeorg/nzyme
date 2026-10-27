import {Link} from "react-router-dom";
import React from "react";
import SolutionCounter from "./layout/SolutionCounter";
import Conditional from "./layout/Conditional";
import ApiRoutes from "../../../../../util/ApiRoutes";

function TLSExpirationProcedure(props) {
  return (
      <ol className="consequence-solution-procedure">
        <li>
          <SolutionCounter counter="1" /> Identify nzyme nodes with expiring or expired TLS certificates using
          the <Link to={ApiRoutes.SYSTEM.CRYPTO.INDEX}>Keys &amp; Certificates</Link> page.
        </li>
        <li>
          <SolutionCounter counter="2" /> <Conditional text="For each" /> node with expiring or expired TLS
          certificate, perform:
        </li>
        <li className="consequence-solution-sublist">
          <ol>
            <li><SolutionCounter counter="2.1" /> Install a new certificate or re-generate self-generated certificate.</li>
          </ol>
        </li>
      </ol>
  )
}

export default TLSExpirationProcedure;