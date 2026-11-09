import React, {useEffect, useState} from "react";
import moment from "moment";
import SystemService from "../../../../services/SystemService";
import LoadingSpinner from "../../../misc/LoadingSpinner";
import Paginator from "../../../misc/Paginator";
import {formatDurationMs} from "../../../../util/Tools";

const systemService = new SystemService();
const PER_PAGE = 25;

function actionTitle(entry) {
  switch (entry.action) {
    case "VACUUM_TABLE": return <span>Vacuum <span className="machine-data">{entry.target}</span></span>;
    case "REINDEX_TABLE": return <span>Rebuild all indexes of <span className="machine-data">{entry.target}</span></span>;
    case "REINDEX_INDEX": return <span>Rebuild index <span className="machine-data">{entry.target}</span></span>;
    case "STORAGE_PARAMETERS": return "Table storage parameters";
    case "USAGE_STATISTICS": return "Database usage row counts";
    default: return entry.action;
  }
}

function initiator(value) {
  switch (value) {
    case "AUTOMATIC": return "Automatic";
    case "MANUAL": return "User";
    case "STARTUP": return "Startup";
    default: return value;
  }
}

function result(entry) {
  if (entry.success === null || entry.success === undefined) {
    return <span className="text-muted">Running</span>
  }

  return entry.success ? <span className="text-success">Success</span> : <span className="text-danger">Failed</span>
}

function Details({text}) {
  const [expanded, setExpanded] = useState(false);

  if (!text) {
    return <span className="text-muted">None</span>
  }

  const cut = text.indexOf(": ");
  if (cut < 0 || text.length < 120) {
    return text;
  }

  return (
    <span>
      {text.substring(0, cut)}.{' '}
      <a href="#" onClick={(e) => { e.preventDefault(); setExpanded(!expanded); }}>{expanded ? "Hide" : "Show"}</a>
      {expanded ? <div className="text-muted small mt-1">{text.substring(cut + 2)}</div> : null}
    </span>
  )
}

export default function MaintenanceLogTable({revision}) {

  const [log, setLog] = useState(null);
  const [page, setPage] = useState(1);

  useEffect(() => {
    setLog(null);
    systemService.getDatabaseMaintenanceLog(PER_PAGE, (page - 1) * PER_PAGE, setLog);
  }, [page, revision]);

  if (!log) {
    return <LoadingSpinner />
  }

  if (log.entries.length === 0) {
    return <div className="alert alert-info mb-0">No maintenance actions have run yet.</div>
  }

  return (
    <React.Fragment>
      <table className="table table-sm table-hover table-striped">
        <thead>
        <tr>
          <th>Started</th>
          <th>Completed</th>
          <th>Duration</th>
          <th>Action</th>
          <th>Trigger</th>
          <th>Node</th>
          <th>Result</th>
          <th>Details</th>
        </tr>
        </thead>
        <tbody>
        {log.entries.map((e) => (
          <tr key={e.id}>
            <td className="text-nowrap" title={moment(e.started_at).format()}>{moment(e.started_at).fromNow()}</td>
            <td className="text-nowrap" title={e.finished_at ? moment(e.finished_at).format() : null}>
              {e.finished_at ? moment(e.finished_at).fromNow() : <span className="text-muted">Running</span>}
            </td>
            <td className="text-nowrap">
              {e.finished_at ? formatDurationMs(moment(e.finished_at).diff(moment(e.started_at))) : <span className="text-muted">n/a</span>}
            </td>
            <td className="text-nowrap">{actionTitle(e)}</td>
            <td className="text-nowrap">{initiator(e.initiator)}</td>
            <td className="text-nowrap">{e.node_name ? e.node_name : <span className="text-muted machine-data">{e.node_id}</span>}</td>
            <td className="text-nowrap">{result(e)}</td>
            <td><Details text={e.details} /></td>
          </tr>
        ))}
        </tbody>
      </table>

      <Paginator itemCount={log.total} perPage={PER_PAGE} setPage={setPage} page={page} />
    </React.Fragment>
  )

}
