import {Link} from "react-router-dom";
import React from "react";
import numeral from "numeral";
import ApiRoutes from "../../../../../util/ApiRoutes";

function problem(node) {
  const pauses = numeral(node.gc_pause_time_percent).format("0.0") + "%";
  const full = numeral(node.heap_after_gc_percent).format("0.0") + "%";
  const stalled = numeral(node.memory_pressure_percent).format("0.0") + "%";
  const hasCollections = node.heap_after_gc_percent !== null && node.heap_after_gc_percent !== undefined;

  switch (node.heap_status) {
    case "FULL":
      return "The JVM heap of this node was at least " + full + " full after every garbage collection of the last "
          + "10 minutes.";
    case "GC_OVERHEAD":
      return "This node spent " + pauses + " of the last 10 minutes in garbage collection pauses."
          + (hasCollections ? " The lowest JVM heap usage after a collection was " + full + "." : "");
    case "MEMORY_PRESSURE":
      return "This node was stalled waiting for memory during " + stalled + " of the last 10 minutes."
          + (hasCollections ? " The lowest JVM heap usage after a collection was " + full + "." : "");
    case "EXHAUSTED":
      return "This node spent " + pauses + " of the last 10 minutes in garbage collection pauses and its JVM heap "
          + "was at least " + full + " full after every collection.";
    case "NO_MEASUREMENTS":
      return "This node has not reported its heap measurements for the last 10 minutes.";
    default:
      return null;
  }
}

function NodeHeapWarning(props) {

  const node = props.node;

  if (!node || !node.active || node.deleted || !problem(node)) {
    return null;
  }

  return (
      <div className={"alert " + (node.heap_status === "EXHAUSTED" ? "alert-danger" : "alert-warning")}>
        <i className="fa-solid fa-triangle-exclamation"></i> {problem(node)} Please follow instructions in
        the <Link to={ApiRoutes.SYSTEM.HEALTH.INDEX}>Health Console</Link> to fix this issue.
      </div>
  )

}

export default NodeHeapWarning;
