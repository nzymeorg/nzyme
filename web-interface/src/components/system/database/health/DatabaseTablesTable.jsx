import React, {useState} from "react";
import numeral from "numeral";
import moment from "moment";

const CLASS_TITLES = {
  UPSERT: "Session / Ledger",
  APPEND: "Time Series",
  HOT: "Small, Hot"
};

function deadClass(percent) {
  if (percent >= 50) return "text-danger";
  if (percent >= 20) return "text-warning";
  return "";
}

export default function DatabaseTablesTable({tables, indexes, reindexThresholdPercent, reindexMinimumMb, autoReindexEnabled, onVacuum, onReindex, busy}) {

  const [showAll, setShowAll] = useState(false);

  const visible = showAll ? tables : tables.filter((t) => t.total_bytes >= 10 * 1024 * 1024).slice(0, 12);

  const minimumBytes = reindexMinimumMb * 1024 * 1024;
  const qualifyingByTable = {};
  for (const i of indexes) {
    if (i.valid !== false && i.bytes >= minimumBytes && i.bloat_percent !== null && i.bloat_percent >= reindexThresholdPercent) {
      qualifyingByTable[i.table] = (qualifyingByTable[i.table] || 0) + 1;
    }
  }

  const bloatWarning = (t) => {
    const count = qualifyingByTable[t.name] || 0;
    if (count === 0) {
      return null;
    }

    const what = count === 1 ? "One index of this table is" : count + " indexes of this table are";
    const action = autoReindexEnabled
        ? "Rebuilt automatically in the next maintenance window."
        : "Automatic reindexing is off. Rebuild with the Reindex button.";

    return <span className="text-warning"
                 title={what + " above the " + reindexThresholdPercent + "% bloat threshold. " + action}> <i className="fa-solid fa-triangle-exclamation" /></span>;
  };

  const confirmVacuum = (table) => {
    if (confirm("Vacuum table \"" + table + "\" now? This reclaims dead rows and refreshes statistics. It does not " +
        "block reads or writes and runs in the background.")) {
      onVacuum(table);
    }
  };

  const confirmReindex = (table) => {
    if (confirm("Rebuild all indexes of table \"" + table + "\" now? This does not block reads or writes, but can " +
        "take minutes on a large table and temporarily needs disk space for a second copy of its indexes.")) {
      onReindex(table);
    }
  };

  return (
    <React.Fragment>
      <table className="table table-sm table-hover table-striped mb-2">
        <thead>
        <tr>
          <th>Table</th>
          <th>Class</th>
          <th>Data</th>
          <th>Indexes</th>
          <th>Live Rows</th>
          <th>Dead Rows</th>
          <th>Last Vacuum</th>
          <th>Writes</th>
          <th>&nbsp;</th>
        </tr>
        </thead>
        <tbody>
        {visible.map((t) => (
          <tr key={t.name}>
            <td className="machine-data">{t.name}</td>
            <td>{t.table_class ? <span title={t.table_class_title}>{CLASS_TITLES[t.table_class]}</span> : <span className="text-muted">Default</span>}</td>
            <td>{numeral(t.heap_bytes).format("0.0b")}</td>
            <td>
              {numeral(t.index_bytes).format("0.0b")}
              {bloatWarning(t)}
            </td>
            <td>{numeral(t.live_tuples).format("0,0")}</td>
            <td className={deadClass(t.dead_ratio_percent)}>
              {numeral(t.dead_tuples).format("0,0")} <span className="text-muted">({numeral(t.dead_ratio_percent).format("0.0")}%)</span>
            </td>
            <td title={t.last_vacuum ? moment(t.last_vacuum).format() : "Never"}>
              {t.last_vacuum ? moment(t.last_vacuum).fromNow() : <span className="text-muted">Never</span>}
            </td>
            <td title={"Since statistics started: " + numeral(t.inserts_total).format("0,0") + " inserts, "
                + numeral(t.updates_total).format("0,0") + " updates (" + numeral(t.hot_updates_total).format("0,0")
                + " without touching an index), " + numeral(t.deletes_total).format("0,0") + " deletes"}>
              {numeral(t.inserts_total).format("0a")} ins / {numeral(t.updates_total).format("0a")} upd / {numeral(t.deletes_total).format("0a")} del
            </td>
            <td className="text-end text-nowrap">
              <button className="btn btn-sm btn-outline-secondary me-1" disabled={busy} onClick={() => confirmVacuum(t.name)}>Vacuum</button>
              <button className="btn btn-sm btn-outline-secondary" disabled={busy} onClick={() => confirmReindex(t.name)}>Reindex</button>
            </td>
          </tr>
        ))}
        </tbody>
      </table>

      <button className="btn btn-sm btn-outline-secondary" onClick={() => setShowAll(!showAll)}>
        {showAll ? "Show only the largest tables" : "Show all " + tables.length + " tables"}
      </button>
    </React.Fragment>
  )

}
