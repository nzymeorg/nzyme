import React, {useEffect, useState} from 'react';
import LoadingSpinner from "../../../misc/LoadingSpinner";
import Dot11Service from "../../../../services/Dot11Service";
import ConfigurationValue from "../../../configuration/ConfigurationValue";
import ConfigurationModal from "../../../configuration/modal/ConfigurationModal";

const dot11Service = new Dot11Service();

export default function SSIDMonitoringConfiguration(props) {

  const organizationUUID = props.organizationUUID;
  const tenantUUID = props.tenantUUID;
  const onLoaded = props.onLoaded;

  const [configuration, setConfiguration] = useState(null);
  const [localRevision, setLocalRevision] = useState(0);

  useEffect(() => {
    let current = true;

    setConfiguration(null);
    dot11Service.getSSIDMonitoringConfiguration(organizationUUID, tenantUUID, (response) => {
      if (current) {
        setConfiguration(response);

        if (onLoaded) {
          onLoaded(response);
        }
      }
    });

    return () => { current = false; }
  }, [localRevision, organizationUUID, tenantUUID]);

  if (!configuration) {
    return <LoadingSpinner />
  }

  return (
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
          <td>Is monitoring enabled</td>
          <td>
            <ConfigurationValue value={configuration.is_enabled.value}
                                configKey={configuration.is_enabled.key}
                                boolean={true}/>
          </td>
          <td>
            <ConfigurationModal config={configuration.is_enabled}
                                setGlobalConfig={setConfiguration}
                                setLocalRevision={setLocalRevision}
                                organizationId={organizationUUID}
                                tenantId={tenantUUID}
                                dbUpdateCallback={dot11Service.updateSSIDMonitoringConfiguration}/>
          </td>
        </tr>

        <tr>
          <td>Is event generation enabled</td>
          <td>
            <ConfigurationValue value={configuration.eventing_is_enabled.value}
                                configKey={configuration.eventing_is_enabled.key}
                                boolean={true}/>
          </td>
          <td>
            <ConfigurationModal config={configuration.eventing_is_enabled}
                                setGlobalConfig={setConfiguration}
                                setLocalRevision={setLocalRevision}
                                organizationId={organizationUUID}
                                tenantId={tenantUUID}
                                dbUpdateCallback={dot11Service.updateSSIDMonitoringConfiguration}/>
          </td>
        </tr>

        <tr>
          <td>24-hour minimum dwell time (Minutes)</td>
          <td>
            <ConfigurationValue value={configuration.dwell_time_minutes.value}
                                configKey={configuration.dwell_time_minutes.key} />
          </td>
          <td>
            <ConfigurationModal config={configuration.dwell_time_minutes}
                                setGlobalConfig={setConfiguration}
                                setLocalRevision={setLocalRevision}
                                organizationId={organizationUUID}
                                tenantId={tenantUUID}
                                dbUpdateCallback={dot11Service.updateSSIDMonitoringConfiguration}/>
          </td>
        </tr>

        <tr>
          <td>Record hidden access points</td>
          <td>
            <ConfigurationValue value={configuration.hidden_access_points_enabled.value}
                                configKey={configuration.hidden_access_points_enabled.key}
                                boolean={true}/>
          </td>
          <td>
            <ConfigurationModal config={configuration.hidden_access_points_enabled}
                                setGlobalConfig={setConfiguration}
                                setLocalRevision={setLocalRevision}
                                organizationId={organizationUUID}
                                tenantId={tenantUUID}
                                dbUpdateCallback={dot11Service.updateSSIDMonitoringConfiguration}/>
          </td>
        </tr>
        </tbody>
      </table>
  )

}