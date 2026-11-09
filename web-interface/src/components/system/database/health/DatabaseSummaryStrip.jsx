import React from "react";
import numeral from "numeral";
import moment from "moment";

const DATABASE_INDICATOR_IDS = ["db_configuration", "db_vacuum", "db_bloat", "db_performance", "db_transactions", "db_clock"];

export default function DatabaseSummaryStrip({health, indicators}) {

  const fact = (label, value, detail = null, last = false, explanation = null) => (
    <div className={"py-1 " + (last ? "" : "pe-4 me-4 border-end")}>
      <div className="small text-muted text-uppercase">{label}</div>
      <div>
        {value}
        {detail ? <span className="text-muted"> ({detail})</span> : null}
      </div>
      {explanation ? <div className="small text-muted">{explanation}</div> : null}
    </div>
  )

  const bar = (facts) => (
    <div className="row mt-3">
      <div className="col-12">
        <div className="card">
          <div className="card-body py-2">
            <div className="d-flex flex-wrap align-items-center">
              {facts}
            </div>
          </div>
        </div>
      </div>
    </div>
  )

  if (!health) {
    const pending = <span className="text-muted">&hellip;</span>;
    return bar(
      <React.Fragment>
        {fact("Health", pending)}
        {fact("PostgreSQL", pending)}
        {fact("Cache Hit Ratio", pending)}
        {fact("Dead Rows", pending)}
        {fact("Index Bloat", pending)}
        {fact("Maintenance", pending, null, true)}
      </React.Fragment>
    );
  }

  let healthValue;
  let healthDetail = null;
  if (!indicators) {
    healthValue = <span className="text-muted">&hellip;</span>;
  } else {
    const active = DATABASE_INDICATOR_IDS.map((id) => indicators[id]).filter((i) => !i || i.active);
    const pending = active.filter((i) => !i || i.expired || i.level === "UNAVAILABLE").length;
    const red = active.filter((i) => i && !i.expired && i.level === "RED").length;
    const orange = active.filter((i) => i && !i.expired && i.level === "ORANGE").length;

    if (red > 0 && orange > 0) {
      healthValue = <span><span className="text-danger">{red} red</span> <span className="text-warning">{orange} orange</span></span>;
    } else if (red > 0) {
      healthValue = <span className="text-danger">{red} red</span>;
    } else if (orange > 0) {
      healthValue = <span className="text-warning">{orange} orange</span>;
    } else if (pending > 0) {
      healthValue = <span className="text-muted">Not calculated yet</span>;
    } else {
      healthValue = <span className="text-success">All green</span>;
    }

    if (pending > 0 && (red > 0 || orange > 0)) {
      healthDetail = "of " + active.length + " indicators, " + pending + " not calculated yet";
    } else if (pending > 0) {
      healthDetail = pending + " of " + active.length + " indicators";
    } else {
      healthDetail = "of " + active.length + " indicators";
    }
  }

  const server = health.server;

  const hit = server.cache_hit_ratio_recent;
  const warmingUp = server.window_minutes < 5 || server.warming_up;
  const hitValue = warmingUp ? <span className="text-muted">Warming up</span>
    : (hit < 0 ? <span className="text-muted">No reads</span>
      : <span className={hit < 70 ? "text-danger" : (hit < 90 ? "text-warning" : "")}>{numeral(hit).format("0.0")}%</span>);
  const hitDetail = server.warming_up ? "PostgreSQL restarted " + moment(server.server_started_at).fromNow()
    : (warmingUp ? "needs 5 min of samples" : "last " + server.window_minutes + " min");

  const large = health.tables.filter((t) => t.total_bytes >= 100 * 1024 * 1024);
  const worstTable = large.reduce((a, t) => (!a || t.dead_ratio_percent > a.dead_ratio_percent) ? t : a, null);
  const deadValue = worstTable
    ? <span className={worstTable.dead_ratio_percent >= 50 ? "text-danger" : (worstTable.dead_ratio_percent >= 20 ? "text-warning" : "")}>
        {numeral(worstTable.dead_ratio_percent).format("0.0")}%
      </span>
    : <span className="text-muted">n/a</span>;
  const deadDetail = worstTable ? "worst table" : null;

  const largeIndexes = health.indexes.filter((i) => i.bytes >= 100 * 1024 * 1024 && i.bloat_percent !== null);
  const worstIndex = largeIndexes.reduce((a, i) => (!a || i.bloat_percent > a.bloat_percent) ? i : a, null);
  const wasted = health.indexes.reduce((sum, i) => sum + (i.bloat_bytes || 0), 0);
  const bloatValue = worstIndex
    ? <span className={worstIndex.bloat_percent >= 85 ? "text-danger" : (worstIndex.bloat_percent >= 60 ? "text-warning" : "")}>
        {numeral(worstIndex.bloat_percent).format("0")}%
      </span>
    : <span className="text-muted">n/a</span>;
  const bloatDetail = worstIndex
    ? <span>worst index, {numeral(wasted).format("0.0b")} wasted in total</span>
    : null;

  const maintenanceValue = health.running_action
    ? <span><i className="fa-solid fa-play text-success" /> Running</span>
    : <span className="text-muted">Idle</span>;
  const maintenanceDetail = health.running_action
    ? "since " + moment(health.running_action.started_at).fromNow()
    : (health.queued_actions.length > 0 ? health.queued_actions.length + " queued" : "window " + (health.in_maintenance_window ? "open" : "closed"));

  return bar(
    <React.Fragment>
      {fact("Health", healthValue, healthDetail)}
      {fact("PostgreSQL", server.version, numeral(server.database_size_bytes).format("0.0b"))}
      {fact("Cache Hit Ratio", hitValue, hitDetail)}
      {fact("Dead Rows", deadValue, deadDetail)}
      {fact("Index Bloat", bloatValue, bloatDetail)}
      {fact("Maintenance", maintenanceValue, maintenanceDetail, true)}
    </React.Fragment>
  );

}
