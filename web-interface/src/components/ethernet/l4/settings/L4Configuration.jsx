import React from "react";
import L4Service from "../../../../services/ethernet/L4Service";
import LoadingSpinner from "../../../misc/LoadingSpinner";
import ConfigurationValue from "../../../configuration/ConfigurationValue";
import ConfigurationModal from "../../../configuration/modal/ConfigurationModal";

const l4Service = new L4Service();

const ENTRIES = [
  "cgnat_is_internal",
  "learn_internal_networks"
];

export default function L4Configuration({config, organizationId, tenantId, setRevision}) {

  if (!config) {
    return <LoadingSpinner />
  }

  return (
    <React.Fragment>
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

      {config.pending_rewrites > 0 ?
        <div className="alert alert-info mt-3 mb-0">
          Stored records are being updated to the changed settings. This runs in the background and can take a few
          minutes.
        </div> : null}
    </React.Fragment>
  )

}
