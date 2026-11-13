import React, {useState} from "react";
import numeral from "numeral";

export default function DatabaseIndexesTable({indexes, reindexThresholdPercent, reindexMinimumMb, autoReindexEnabled, onReindex, busy}) {

  const [showAll, setShowAll] = useState(false);

  const minimumBytes = reindexMinimumMb * 1024 * 1024;

  const qualifies = (i) => i.valid !== false && i.bytes >= minimumBytes && i.bloat_percent !== null
      && i.bloat_percent !== undefined && i.bloat_percent >= reindexThresholdPercent;

  const bloatTitle = (i) => {
    if (!qualifies(i)) {
      return undefined;
    }

    if (!autoReindexEnabled) {
      return "Above the " + reindexThresholdPercent + "% bloat threshold. Automatic reindexing is off. Rebuild with the Rebuild button.";
    }

    return "Above the " + reindexThresholdPercent + "% bloat threshold. " + (i.rebuild_overdue
        ? "Not rebuilt in the last maintenance window."
        : "Rebuilt automatically in the next maintenance window.");
  };

  const visible = showAll ? indexes : indexes.slice(0, 25);

  const confirmReindex = (index) => {
    if (confirm("Rebuild index \"" + index + "\" now? This does not block reads or writes and temporarily needs " +
        "disk space for a second copy of the index.")) {
      onReindex(index);
    }
  };

  return (
    <React.Fragment>
      <table className="table table-sm table-hover table-striped mb-2">
        <thead>
        <tr>
          <th>Index</th>
          <th>Table</th>
          <th>Size</th>
          <th>Estimated Bloat</th>
          <th>Scans</th>
          <th>Type</th>
          <th>&nbsp;</th>
        </tr>
        </thead>
        <tbody>
        {visible.map((i) => (
          <tr key={i.name}>
            <td className="machine-data">{i.name}</td>
            <td className="machine-data">{i.table}</td>
            <td>{numeral(i.bytes).format("0.0b")}</td>
            <td className={i.rebuild_overdue ? "text-danger" : (qualifies(i) ? "text-warning" : "")} title={bloatTitle(i)}>
              {i.bloat_percent === null || i.bloat_percent === undefined ? <span className="text-muted">n/a</span> :
                <span>{numeral(i.bloat_percent).format("0")}% <span className="text-muted">({numeral(i.bloat_bytes).format("0.0b")})</span></span>}
              {i.rebuild_overdue ? " (overdue)" : null}
            </td>
            <td className={i.scans === 0 ? "text-muted" : ""}>{numeral(i.scans).format("0,0")}</td>
            <td>
              {i.primary ? "Primary key" : (i.unique ? "Unique" : "Index")}
              {i.valid === false ? <span className="text-danger" title="Left behind by an interrupted rebuild. Dropped automatically before the next rebuild of this table."> (invalid)</span> : null}
            </td>
            <td className="text-end">
              <button className="btn btn-sm btn-outline-secondary" disabled={busy} onClick={() => confirmReindex(i.name)}>Rebuild</button>
            </td>
          </tr>
        ))}
        </tbody>
      </table>

      <button className="btn btn-sm btn-outline-secondary" onClick={() => setShowAll(!showAll)}>
        {showAll ? "Show only the largest indexes" : "Show all " + indexes.length + " indexes"}
      </button>
    </React.Fragment>
  )

}
