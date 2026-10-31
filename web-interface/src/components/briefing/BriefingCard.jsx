import React from "react";
import CardTitleWithControls from "../shared/CardTitleWithControls";

export default function BriefingCard({title, count, countClass, hint, internalLink, className, children}) {

  const titleNode = (
    <React.Fragment>
      {title}
      {count !== undefined && count !== null ? <span className={"briefing-section-count " + (countClass || "")}>{count}</span> : null}
    </React.Fragment>
  );

  return (
    <div className={"card briefing-card " + (className || "")}>
      <div className="card-body">
        <CardTitleWithControls title={titleNode} slim={true} internalLink={internalLink} />
        {hint ? <p className="briefing-card-hint">{hint}</p> : null}
        {children}
      </div>
    </div>
  )

}
