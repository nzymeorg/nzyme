import React, {useEffect, useState} from "react";
import L4Service from "../../../../services/ethernet/L4Service";
import LoadingSpinner from "../../../misc/LoadingSpinner";
import ConfigurationValue from "../../../configuration/ConfigurationValue";
import ConfigurationModal from "../../../configuration/modal/ConfigurationModal";

const l4Service = new L4Service();

const ENTRIES = [
  "cgnat_is_internal"
];

export default function L4Configuration({organizationId, tenantId, revision, setRevision}) {

  const [config, setConfig] = useState(null);

  useEffect(() => {
    setConfig(null);
    l4Service.getConfiguration(organizationId, tenantId, setConfig);
  }, [organizationId, tenantId, revision]);

  if (!config) {
    return <LoadingSpinner />
  }

  return (
    <table className="table table-sm table-hover table-striped mb-0">
      <thead>
      <tr>
        <th>Configuration</th>
        <th>Value</th>
        <th>Actions</th>
      </tr>
      </thead>
      <tbody>
      {ENTRIES.filter((key) => config[key]).map((key) => {
        const entry = config[key];

        return (
          <tr key={key}>
            <td>{entry.key_human_readable}</td>
            <td>
              <ConfigurationValue value={entry.value}
                                  configKey={entry.key}
                                  required={true}
                                  boolean={entry.value_type === "BOOLEAN"} />
            </td>
            <td>
              <ConfigurationModal config={entry}
                                  setGlobalConfig={setConfig}
                                  setLocalRevision={setRevision}
                                  organizationId={organizationId}
                                  tenantId={tenantId}
                                  dbUpdateCallback={l4Service.updateConfiguration} />
            </td>
          </tr>
        )
      })}
      </tbody>
    </table>
  )

}
