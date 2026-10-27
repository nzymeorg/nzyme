import React from "react";
import ConfigurationValue from "../../../configuration/ConfigurationValue";
import ConfigurationModal from "../../../configuration/modal/ConfigurationModal";
import DnsTunnelsService from "../../../../services/ethernet/DnsTunnelsService";

const dnsTunnelsService = new DnsTunnelsService();

export default function DnsTunnelConfiguration({organizationId, tenantId, entries, setConfig, setRevision}) {

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
                                  dbUpdateCallback={dnsTunnelsService.updateConfiguration} />
            </td>
          </tr>
        )
      })}
      </tbody>
    </table>
  )

}
