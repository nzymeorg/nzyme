import {Link} from "react-router-dom";
import React from "react";
import ApiRoutes from "../../../../../util/ApiRoutes";
import SolutionCounter from "./layout/SolutionCounter";
import Conditional from "./layout/Conditional";

function NodeHeapProcedure() {
  return (
      <ol className="consequence-solution-procedure">
        <li>
          <SolutionCounter counter="1" /> Identify Nzyme nodes with a heap warning on the{' '}
          <Link to={ApiRoutes.SYSTEM.CLUSTER.INDEX}>nodes overview</Link> page
        </li>
        <li>
          <SolutionCounter counter="2" /> <Conditional text="For each" /> node with a heap warning, open its details
          page and perform:
        </li>
        <li className="consequence-solution-sublist">
          <ol>
            <li>
              <SolutionCounter counter="2.1" /> <Conditional text="If" /> the node reports no measurements, check
              its log for errors
            </li>
            <li>
              <SolutionCounter counter="2.2" /> <Conditional text="If" /> <em>Time Stalled Waiting for Memory</em> is
              at or above 10%:
            </li>
            <li className="consequence-solution-sublist">
              <ol>
                <li>
                  <SolutionCounter counter="2.2.1" /> Reduce the{' '}
                  <a href="https://go.nzyme.org/node-heap-size" target="_blank" rel="noopener">heap size</a> as far as
                  the heap in use after garbage collection allows
                </li>
                <li className="consequence-solution-or">
                  &mdash; <Conditional text="or" /> &mdash;
                </li>
                <li>
                  <SolutionCounter counter="2.2.2" /> Add memory to the node host
                </li>
              </ol>
            </li>
            <li>
              <SolutionCounter counter="2.3" /> <Conditional text="If" /> it is below 10% or not measured, increase
              the <a href="https://go.nzyme.org/node-heap-size" target="_blank" rel="noopener">heap size</a> to a size
              that fits into the free memory of the node host
            </li>
            <li>
              <SolutionCounter counter="2.4" /> Restart the node
            </li>
          </ol>
        </li>
        <li>
          <SolutionCounter counter={"3"} /> Indicator will extinguish within 10 minutes after problem resolution
        </li>
      </ol>
  )
}

export default NodeHeapProcedure;
