import React, {useEffect, useState} from "react";
import SegmentationService from "../../../../services/ethernet/SegmentationService";
import LoadingSpinner from "../../../misc/LoadingSpinner";
import ConfigurationValue from "../../../configuration/ConfigurationValue";
import ConfigurationModal from "../../../configuration/modal/ConfigurationModal";

const segmentationService = new SegmentationService();

const ENTRIES = [
  "enabled",
  "settle_seconds",
  "initial_backfill_hours",
  "flow_retention_time_days",
  "finding_retention_time_days"
];

export default function SegmentationConfiguration({organizationId, tenantId}) {

  const [config, setConfig] = useState(null);

  const [revision, setRevision] = useState(new Date());

  useEffect(() => {
    setConfig(null);
    segmentationService.getConfiguration(organizationId, tenantId, setConfig);
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
                                  dbUpdateCallback={segmentationService.updateConfiguration} />
            </td>
          </tr>
        )
      })}
      </tbody>
    </table>
  )

}
