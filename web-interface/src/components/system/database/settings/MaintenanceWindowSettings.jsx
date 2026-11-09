import React, {useEffect, useState} from "react";
import moment from "moment";
import SystemService from "../../../../services/SystemService";
import LoadingSpinner from "../../../misc/LoadingSpinner";
import ConfigurationModal from "../../../configuration/modal/ConfigurationModal";

const systemService = new SystemService();

export default function MaintenanceWindowSettings() {

  const [configuration, setConfiguration] = useState(null);
  const [localRevision, setLocalRevision] = useState(0);
  const [now, setNow] = useState(moment());

  useEffect(() => {
    systemService.getDatabaseMaintenanceConfiguration(setConfiguration);
  }, [localRevision]);

  useEffect(() => {
    const id = setInterval(() => setNow(moment()), 1000);
    return () => clearInterval(id);
  }, []);

  if (!configuration) {
    return <LoadingSpinner />
  }

  const startHour = parseInt(configuration.entries.window_start_hour.value, 10);
  const hours = parseInt(configuration.entries.window_hours.value, 10);
  const start = moment.utc().startOf("day").hour(startHour);
  const end = moment(start).add(hours, "hours");
  const localZone = Intl.DateTimeFormat().resolvedOptions().timeZone;
  const localOffset = moment().format("Z");
  const open = ((moment.utc(now).hour() - startHour) % 24 + 24) % 24 < hours;

  const editButton = (key) => (
    <ConfigurationModal config={configuration.entries[key]}
                        setGlobalConfig={setConfiguration}
                        setLocalRevision={setLocalRevision}
                        dbUpdateCallback={systemService.updateDatabaseMaintenanceConfiguration} />
  );

  return (
    <React.Fragment>
      <p>
        Automatic index rebuilds run inside this window. Configured in full hours in UTC for the whole cluster.
      </p>

      <table className="table table-sm table-hover table-striped mb-0">
        <thead>
        <tr>
          <th>&nbsp;</th>
          <th>UTC</th>
          <th>{localZone} <span className="text-muted fw-normal">(UTC{localOffset})</span></th>
          <th>Actions</th>
        </tr>
        </thead>
        <tbody>
        <tr>
          <td>Current time</td>
          <td className="machine-data">{moment.utc(now).format("HH:mm")}</td>
          <td className="machine-data">{moment(now).local().format("HH:mm")}</td>
          <td>
            {open ? <span className="badge bg-success">Window open</span> : <span className="badge bg-secondary">Window closed</span>}
          </td>
        </tr>
        <tr>
          <td>Window start</td>
          <td className="machine-data">{start.format("HH:mm")}</td>
          <td className="machine-data">{moment(start).local().format("HH:mm")}</td>
          <td>{editButton("window_start_hour")}</td>
        </tr>
        <tr>
          <td>Window end <span className="text-muted">({hours} {hours === 1 ? "hour" : "hours"} later)</span></td>
          <td className="machine-data">{end.utc().format("HH:mm")}</td>
          <td className="machine-data">
            {moment(end).local().format("HH:mm")}
          </td>
          <td>{editButton("window_hours")}</td>
        </tr>
        </tbody>
      </table>
    </React.Fragment>
  )

}
