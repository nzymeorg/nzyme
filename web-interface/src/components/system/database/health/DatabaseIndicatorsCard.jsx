import React from "react";
import {Link} from "react-router-dom";
import LoadingSpinner from "../../../misc/LoadingSpinner";
import Indicator from "../../health/Indicator";
import ApiRoutes from "../../../../util/ApiRoutes";

const DATABASE_INDICATORS = [
  {id: "db_configuration", name: "Configuration"},
  {id: "db_vacuum", name: "Vacuum"},
  {id: "db_bloat", name: "Index Bloat"},
  {id: "db_performance", name: "Performance"},
  {id: "db_transactions", name: "Transactions"},
  {id: "db_clock", name: "Clock"}
];

export default function DatabaseIndicatorsCard({indicators}) {

  if (!indicators) {
    return <LoadingSpinner />
  }

  const isPending = (indicator) => !indicator || indicator.expired || indicator.level === "UNAVAILABLE";

  const illuminated = DATABASE_INDICATORS.filter((i) => {
    const indicator = indicators[i.id];
    return indicator && indicator.active && !indicator.expired && (indicator.level === "RED" || indicator.level === "ORANGE");
  });

  const pending = DATABASE_INDICATORS.filter((i) => {
    const indicator = indicators[i.id];
    return !indicator || (indicator.active && isPending(indicator));
  });

  const pendingTitle = "Not calculated yet.";

  let alert;
  if (illuminated.length > 0) {
    alert = <div className="alert alert-warning mt-3 mb-0">
      {illuminated.length} database health {illuminated.length === 1 ? "indicator is" : "indicators are"}{' '}
      illuminated. <Link to={ApiRoutes.SYSTEM.HEALTH.INDEX}>Open the health console</Link> for the resolution
      procedures.{pending.length > 0 ? " " + pending.length + " more " + (pending.length === 1 ? "has" : "have") + " not been calculated yet." : ""}
    </div>
  } else if (pending.length > 0) {
    alert = <div className="alert alert-info mt-3 mb-0">
      {pending.length} of {DATABASE_INDICATORS.length} database health indicators have not been calculated yet.
      This page refreshes automatically.
    </div>
  } else {
    alert = <div className="alert alert-success mt-3 mb-0">All database health indicators are green.</div>
  }

  return (
    <React.Fragment>
      <div className="health-console">
        <div className="hc-row">
          {DATABASE_INDICATORS.map((i) => <Indicator key={i.id} indicator={indicators[i.id]} name={i.name} unavailableTitle={pendingTitle} />)}
        </div>
        <div style={{clear: "both"}} />
      </div>

      {alert}
    </React.Fragment>
  )

}
