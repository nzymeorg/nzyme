import React, {useEffect, useState} from "react";
import SystemService from "../../../../services/SystemService";
import LoadingSpinner from "../../../misc/LoadingSpinner";
import ConfigurationValue from "../../../configuration/ConfigurationValue";
import ConfigurationModal from "../../../configuration/modal/ConfigurationModal";

const systemService = new SystemService();

const CLASS_ENTRIES = [
  {key: "vacuum_scale_percent", title: "Vacuum when this percent of rows is dead"},
  {key: "vacuum_threshold", title: "Minimum dead rows before vacuum"},
  {key: "analyze_scale_percent", title: "Refresh statistics when this percent of rows changed"},
  {key: "fillfactor", title: "Page fill factor (percent)"}
];

export default function TableClassSettings() {

  const [configuration, setConfiguration] = useState(null);
  const [localRevision, setLocalRevision] = useState(0);

  useEffect(() => {
    systemService.getDatabaseMaintenanceConfiguration(setConfiguration);
  }, [localRevision]);

  if (!configuration) {
    return <LoadingSpinner />
  }

  return (
    <div className="row">
      {configuration.classes.map((c) => (
        <div className="col-xl-4" key={c.key}>
          <div className="card mb-3">
            <div className="card-body">
              <h3>{c.title}</h3>
              <p>{c.description}</p>
              <p className="text-muted small" title={c.tables.join(", ")}>
                {c.tables.length} tables: {c.tables.slice(0, 6).join(", ")}{c.tables.length > 6 ? ", …" : ""}
              </p>

              <table className="table table-sm table-hover table-striped mb-0">
                <thead>
                <tr>
                  <th>Configuration</th>
                  <th>Value</th>
                  <th>Actions</th>
                </tr>
                </thead>
                <tbody>
                {CLASS_ENTRIES.map((entry) => {
                  const config = c.entries[entry.key];
                  return (
                    <tr key={config.key}>
                      <td>{entry.title}</td>
                      <td>
                        <ConfigurationValue value={config.value} configKey={config.key} required={true} />
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
            </div>
          </div>
        </div>
      ))}
    </div>
  )

}
