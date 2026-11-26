import React, {useEffect, useState} from "react";
import LoadingSpinner from "../misc/LoadingSpinner";
import ConfigurationValue from "./ConfigurationValue";
import ConfigurationModal from "./modal/ConfigurationModal";

export default function ConfigurationSectionTable({organizationId, tenantId, section, getConfiguration, updateConfiguration}) {

  const [config, setConfig] = useState(null);
  const [revision, setRevision] = useState(new Date());

  useEffect(() => {
    let active = true;

    setConfig(null);
    getConfiguration(organizationId, tenantId, (loaded) => {
      if (active) {
        setConfig(loaded);
      }
    });

    return () => {
      active = false;
    }
  }, [organizationId, tenantId, revision]);

  if (!config) {
    return <LoadingSpinner />
  }

  return (
      <table className="table table-sm table-hover table-striped mt-3 mb-0">
        <thead>
        <tr>
          <th>Configuration</th>
          <th>Value</th>
          <th>Actions</th>
        </tr>
        </thead>
        <tbody>
        {config[section].map((entry) => {
          return (
              <tr key={entry.key}>
                <td>{entry.key_human_readable}</td>
                <td>
                  {entry.value_type === "NUMBER" ?
                      <span>{String(entry.value)}</span>
                      : <ConfigurationValue value={entry.value}
                                            configKey={entry.key}
                                            required={true}
                                            boolean={entry.value_type === "BOOLEAN"} />}
                </td>
                <td>
                  <ConfigurationModal config={entry}
                                      setGlobalConfig={setConfig}
                                      setLocalRevision={setRevision}
                                      organizationId={organizationId}
                                      tenantId={tenantId}
                                      dbUpdateCallback={updateConfiguration} />
                </td>
              </tr>
          )
        })}
        </tbody>
      </table>
  )

}
