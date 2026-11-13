import React, {useEffect, useState} from "react";
import SystemService from "../../../../services/SystemService";
import LoadingSpinner from "../../../misc/LoadingSpinner";
import ConfigurationValue from "../../../configuration/ConfigurationValue";
import ConfigurationModal from "../../../configuration/modal/ConfigurationModal";

const systemService = new SystemService();

const GENERAL = [
  {key: "table_tuning_enabled", title: "Manage table storage parameters", boolean: true,
    help: "Turning this off resets all tables to the PostgreSQL defaults."},
  {key: "auto_vacuum_enabled", title: "Vacuum tables with too many dead rows", boolean: true,
    help: "Tables of at least 50 MB with more than 20% dead rows. Runs at any time of day."},
  {key: "auto_reindex_enabled", title: "Rebuild bloated indexes in the maintenance window", boolean: true,
    help: "One index at a time with REINDEX CONCURRENTLY while the window is open."},
  {key: "reindex_bloat_threshold_percent", title: "Rebuild indexes estimated to be more than this percent empty",
    help: "The DB Bloat health indicator turns on when such an index was not rebuilt in a full maintenance window."},
  {key: "reindex_minimum_index_mb", title: "Only rebuild indexes of at least this size (MB)",
    help: "Small indexes are not worth a rebuild."},
  {key: "session_tuning_enabled", title: "Apply session settings to Nzyme connections", boolean: true,
    help: "Sets work_mem and disables JIT on every connection. Takes effect after a node restart."},
  {key: "session_work_mem_mb", title: "work_mem of Nzyme connections (MB)",
    help: "Takes effect after a node restart."}
];

export default function MaintenanceSettings() {

  const [configuration, setConfiguration] = useState(null);
  const [localRevision, setLocalRevision] = useState(0);

  useEffect(() => {
    systemService.getDatabaseMaintenanceConfiguration(setConfiguration);
  }, [localRevision]);

  if (!configuration) {
    return <LoadingSpinner />
  }

  return (
    <React.Fragment>
      <table className="table table-sm table-hover table-striped">
        <thead>
        <tr>
          <th>Configuration</th>
          <th>Value</th>
          <th>Actions</th>
        </tr>
        </thead>
        <tbody>
        {GENERAL.map((entry) => {
          const config = configuration.entries[entry.key];
          return (
            <tr key={config.key}>
              <td>
                {entry.title}
                {entry.help ? <div className="text-muted small">{entry.help}</div> : null}
              </td>
              <td>
                <ConfigurationValue value={config.value} configKey={config.key} required={true} boolean={entry.boolean} />
              </td>
              <td>
                <ConfigurationModal config={config}
                                    setGlobalConfig={setConfiguration}
                                    setLocalRevision={setLocalRevision}
                                    dbUpdateCallback={systemService.updateDatabaseMaintenanceConfiguration} />
              </td>
            </tr>
          )
        })}
        </tbody>
      </table>
    </React.Fragment>
  )

}
