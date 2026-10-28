import React from "react";
import ConfigurationValue from "../../../configuration/ConfigurationValue";
import ConfigurationModal from "../../../configuration/modal/ConfigurationModal";
import VpnService from "../../../../services/ethernet/VpnService";

const vpnService = new VpnService();

export default function VPNConfiguration({organizationId, tenantId, entries, setConfig, setRevision}) {

  if (!entries || entries.length === 0) {
    return null;
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
      {entries.map((entry, i) => {
        return (
          <tr key={i}>
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
                                  dbUpdateCallback={vpnService.updateConfiguration} />
            </td>
          </tr>
        )
      })}
      </tbody>
    </table>
  )

}
