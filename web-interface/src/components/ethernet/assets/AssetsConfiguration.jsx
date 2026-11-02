import React, {useEffect, useState} from "react";
import AssetsService from "../../../services/ethernet/AssetsService";
import LoadingSpinner from "../../misc/LoadingSpinner";
import ConfigurationValue from "../../configuration/ConfigurationValue";
import ConfigurationModal from "../../configuration/modal/ConfigurationModal";

const assetsService = new AssetsService();

export default function AssetsConfiguration({organizationId, tenantId}) {

  const [config, setConfig] = useState(null);

  const [revision, setRevision] = useState(new Date());

  useEffect(() => {
    setConfig(null);
    assetsService.getAssetsConfiguration(organizationId, tenantId, setConfig);
  }, [organizationId, tenantId, revision]);

  if (!config) {
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
        <tr>
          <td>Asset Statistics Retention Time (Days)</td>
          <td>
            <ConfigurationValue value={config.statistics_retention_time_days.value}
                                configKey={config.statistics_retention_time_days.key}
                                required={true} />
          </td>
          <td>
            <ConfigurationModal config={config.statistics_retention_time_days}
                                setGlobalConfig={setConfig}
                                setLocalRevision={setRevision}
                                organizationId={organizationId}
                                tenantId={tenantId}
                                dbUpdateCallback={assetsService.updateAssetsConfiguration} />
          </td>
        </tr>
        <tr>
          <td>DHCP Fingerprint Monitoring Enabled</td>
          <td>
            <ConfigurationValue value={config.dhcp_fingerprint_monitoring_enabled.value}
                                configKey={config.dhcp_fingerprint_monitoring_enabled.key}
                                required={true}
                                boolean={true} />
          </td>
          <td>
            <ConfigurationModal config={config.dhcp_fingerprint_monitoring_enabled}
                                setGlobalConfig={setConfig}
                                setLocalRevision={setRevision}
                                organizationId={organizationId}
                                tenantId={tenantId}
                                dbUpdateCallback={assetsService.updateAssetsConfiguration} />
          </td>
        </tr>
        </tbody>
      </table>
    </React.Fragment>
  )

}