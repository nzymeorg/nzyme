import React, {useEffect, useState} from "react";
import SystemService from "../../../../services/SystemService";
import LoadingSpinner from "../../../misc/LoadingSpinner";
import ConfigurationValue from "../../../configuration/ConfigurationValue";
import ConfigurationModal from "../../../configuration/modal/ConfigurationModal";

const systemService = new SystemService();

const GENERAL = [
  {key: "table_tuning_enabled", title: "Manage table storage parameters", boolean: true,
    help: "Nzyme sets autovacuum thresholds and page fill factors per table class, see below. Turning this off " +
      "resets all tables to the PostgreSQL defaults."},
  {key: "auto_vacuum_enabled", title: "Vacuum tables with too many dead rows", boolean: true,
    help: "Runs VACUUM (ANALYZE) on a table of at least 50 MB once more than 20% of its rows are dead and " +
      "autovacuum has not touched it for 30 minutes. Runs at any time of day because vacuum does not block anything."},
  {key: "auto_reindex_enabled", title: "Rebuild bloated indexes in the maintenance window", boolean: true,
    help: "Rebuilds one index per minute with REINDEX CONCURRENTLY while the maintenance window is open. Reads " +
      "and writes continue during the rebuild, which temporarily needs disk space for a second copy of the index."},
  {key: "reindex_bloat_threshold_percent", title: "Rebuild indexes estimated to be more than this percent empty",
    help: "The Database Index Bloat health indicator turns orange at 60% and red at 85%."},
  {key: "reindex_minimum_index_mb", title: "Only rebuild indexes of at least this size (MB)",
    help: "Small indexes are not worth a rebuild."},
  {key: "session_tuning_enabled", title: "Apply session settings to Nzyme connections", boolean: true,
    help: "Sets work_mem and disables JIT compilation on every pooled connection. Takes effect after a node restart."},
  {key: "session_work_mem_mb", title: "work_mem of Nzyme connections (MB)",
    help: "Memory a single sort or hash may use before it spills to disk. Raise it if the temporary file volume " +
      "keeps growing. Takes effect after a node restart."}
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
      <p>
        These settings control the automatic database maintenance. All maintenance runs with the privileges of the Nzyme
        database user and does not change the configuration of the PostgreSQL server. Server settings are listed in the
        Server Configuration section of the Health &amp; Maintenance page.
      </p>

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
