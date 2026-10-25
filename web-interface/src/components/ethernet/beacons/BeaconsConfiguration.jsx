import React, {useEffect, useState} from "react";
import LoadingSpinner from "../../misc/LoadingSpinner";
import ConfigurationValue from "../../configuration/ConfigurationValue";
import ConfigurationModal from "../../configuration/modal/ConfigurationModal";
import BeaconsService from "../../../services/ethernet/BeaconsService";

const beaconsService = new BeaconsService();

export default function BeaconsConfiguration({organizationId, tenantId, section}) {

  const [config, setConfig] = useState(null);
  const [revision, setRevision] = useState(new Date());

  useEffect(() => {
    setConfig(null);
    beaconsService.getConfiguration(organizationId, tenantId, setConfig);
  }, [organizationId, tenantId, revision]);

  if (!config) {
    return <LoadingSpinner />
  }

  const entries = config[section];

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
      {entries.map((entry, i) => {
        return (
          <tr key={i}>
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
                                  dbUpdateCallback={beaconsService.updateConfiguration} />
            </td>
          </tr>
        )
      })}
      </tbody>
    </table>
  )

}
