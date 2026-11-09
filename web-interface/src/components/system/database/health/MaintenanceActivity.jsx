import React, {useEffect, useState} from "react";
import moment from "moment";

function actionTitle(action) {
  switch (action.type) {
    case "VACUUM_TABLE": return "Vacuum of table \"" + action.target + "\"";
    case "REINDEX_TABLE": return "Rebuild of all indexes of table \"" + action.target + "\"";
    case "REINDEX_INDEX": return "Rebuild of index \"" + action.target + "\"";
    case "STORAGE_PARAMETERS": return "Table storage parameters";
    case "USAGE_STATISTICS": return "Database usage row counts";
    default: return action.type;
  }
}

function initiator(action) {
  switch (action.initiator) {
    case "AUTOMATIC": return "automatic";
    case "MANUAL": return "requested by a user";
    case "STARTUP": return "node startup";
    default: return action.initiator.toLowerCase();
  }
}

export default function MaintenanceActivity({health}) {

  const [now, setNow] = useState(moment());

  useEffect(() => {
    const id = setInterval(() => setNow(moment()), 1000);
    return () => clearInterval(id);
  }, []);

  const start = moment.utc().startOf("day").hour(health.maintenance_window_start_hour);
  const end = moment(start).add(health.maintenance_window_hours, "hours");
  const localZone = Intl.DateTimeFormat().resolvedOptions().timeZone;

  const running = health.running_action
    ? <span>
        <i className="fa-solid fa-play text-success" /> {actionTitle(health.running_action)}{' '}
        <span className="text-muted">
          ({initiator(health.running_action)}, started{' '}
          <span title={moment(health.running_action.started_at).format()}>{moment(health.running_action.started_at).fromNow()}</span>)
        </span>
      </span>
    : <span className="text-muted">Nothing</span>;

  const queued = health.queued_actions.length === 0
    ? <span className="text-muted">Nothing</span>
    : <ul className="mb-0 ps-3">
        {health.queued_actions.map((a, i) => <li key={i}>{actionTitle(a)}{' '}
          <span className="text-muted">({initiator(a)}, queued <span title={moment(a.queued_at).format()}>{moment(a.queued_at).fromNow()}</span>)</span></li>)}
      </ul>;

  const enabled = (flag, text) => flag
    ? <span className="text-success">{text}</span>
    : <span className="text-warning">Disabled</span>;

  return (
    <table className="table table-sm table-striped mb-0">
      <tbody>
      <tr>
        <td className="text-nowrap">Running now</td>
        <td>{running}</td>
      </tr>
      <tr>
        <td className="text-nowrap">Queued</td>
        <td>{queued}</td>
      </tr>
      <tr>
        <td className="text-nowrap">Maintenance window</td>
        <td>
          {start.format("HH:mm")} to {end.utc().format("HH:mm")} UTC{' '}
          <span className="text-muted">
            (that is {moment(start).local().format("HH:mm")} to {moment(end).local().format("HH:mm")} in {localZone})
          </span>
        </td>
      </tr>
      <tr>
        <td className="text-nowrap">Window state</td>
        <td>
          {health.in_maintenance_window ? <span className="text-success">Open</span> : <span className="text-muted">Closed</span>}
          {' '}<span className="text-muted">(current time {moment.utc(now).format("HH:mm")} UTC)</span>
        </td>
      </tr>
      <tr>
        <td className="text-nowrap">Table storage parameters</td>
        <td>{enabled(health.table_tuning_enabled, "Managed by Nzyme")}</td>
      </tr>
      <tr>
        <td className="text-nowrap">Vacuum of tables with too many dead rows</td>
        <td>{enabled(health.auto_vacuum_enabled, "Automatic, at any time")}</td>
      </tr>
      <tr>
        <td className="text-nowrap">Rebuild of bloated indexes</td>
        <td>{enabled(health.auto_reindex_enabled, "Automatic, in the maintenance window")}</td>
      </tr>
      </tbody>
    </table>
  )

}
