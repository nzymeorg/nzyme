import React from "react";
import {FINDING_CLASSES} from "../SegmentationFormat";

export const AUDIT_NOTE = "The policy was in audit when the finding was last seen. No alert was raised.";

export default function FindingClass({findingClass, audit = false}) {

  const known = FINDING_CLASSES[findingClass];

  return (
    <React.Fragment>
      <span className={findingClass === "CROSSED" ? "text-danger" : "text-warning"}
            title={known ? known.description : undefined}>
        {known ? known.title : findingClass}
      </span>
      {audit ? <span className="text-muted" title={AUDIT_NOTE}>{' '}(Audit)</span> : null}
    </React.Fragment>
  )

}
