import React, {useEffect, useState} from "react";
import IntegrationsService from "../../../../services/IntegrationsService";
import LoadingSpinner from "../../../misc/LoadingSpinner";
import ConfigurationValue from "../../../configuration/ConfigurationValue";
import ConfigurationModal from "../../../configuration/modal/ConfigurationModal";
import EncryptedConfigurationValue from "../../../configuration/EncryptedConfigurationValue";

const integrationsService = new IntegrationsService();

function SmtpIntegration() {

  const [configuration, setConfiguration] = useState(null);

  const [localRevision, setLocalRevision] = useState(0);

  useEffect(() => {
    setConfiguration(null);
    integrationsService.getSmtpConfiguration(setConfiguration);
  }, [localRevision])

  if (!configuration) {
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
          <td>Transport Strategy</td>
          <td>
            <ConfigurationValue value={configuration.smtp_transport_strategy.value}
                                configKey={configuration.smtp_transport_strategy.key}
                                required={true} />
          </td>
          <td>
            <ConfigurationModal config={configuration.smtp_transport_strategy}
                                setGlobalConfig={setConfiguration}
                                setLocalRevision={setLocalRevision}
                                dbUpdateCallback={integrationsService.updateSmtpConfiguration} />
          </td>
        </tr>
        <tr>
          <td>Hostname</td>
          <td>
            <ConfigurationValue value={configuration.smtp_host.value}
                                configKey={configuration.smtp_host.key}
                                required={true} />
          </td>
          <td>
            <ConfigurationModal config={configuration.smtp_host}
                                setGlobalConfig={setConfiguration}
                                setLocalRevision={setLocalRevision}
                                dbUpdateCallback={integrationsService.updateSmtpConfiguration} />
          </td>
        </tr>
        <tr>
          <td>Port</td>
          <td>
            <ConfigurationValue value={configuration.smtp_port.value}
                                configKey={configuration.smtp_port.key}
                                required={true} />
          </td>
          <td>
            <ConfigurationModal config={configuration.smtp_port}
                                setGlobalConfig={setConfiguration}
                                setLocalRevision={setLocalRevision}
                                dbUpdateCallback={integrationsService.updateSmtpConfiguration} />
          </td>
        </tr>
        <tr>
          <td>Username</td>
          <td>
            <ConfigurationValue value={configuration.smtp_username.value}
                                configKey={configuration.smtp_username.key}
                                required={true} />
          </td>
          <td>
            <ConfigurationModal config={configuration.smtp_username}
                                setGlobalConfig={setConfiguration}
                                setLocalRevision={setLocalRevision}
                                dbUpdateCallback={integrationsService.updateSmtpConfiguration} />
          </td>
        </tr>
        <tr>
          <td>Password</td>
          <td>
            <EncryptedConfigurationValue isSet={configuration.smtp_password.value_is_set}
                                         configKey={configuration.smtp_password.key}
                                         required={true} />
          </td>
          <td>
            <ConfigurationModal config={configuration.smtp_password}
                                setGlobalConfig={setConfiguration}
                                setLocalRevision={setLocalRevision}
                                dbUpdateCallback={integrationsService.updateSmtpConfiguration} />
          </td>
        </tr>
        <tr>
          <td>From Address</td>
          <td>
            <ConfigurationValue value={configuration.smtp_from_address.value}
                                configKey={configuration.smtp_from_address.key}
                                required={true} />
          </td>
          <td>
            <ConfigurationModal config={configuration.smtp_from_address}
                                setGlobalConfig={setConfiguration}
                                setLocalRevision={setLocalRevision}
                                dbUpdateCallback={integrationsService.updateSmtpConfiguration} />
          </td>
        </tr>
        <tr>
          <td>URL of Nzyme Web Interface</td>
          <td>
            <ConfigurationValue value={configuration.web_interface_url.value
                ? configuration.web_interface_url.value : configuration.web_interface_url.default_value}
                                configKey={configuration.web_interface_url.key}
                                required={true} />
          </td>
          <td>
            <ConfigurationModal config={configuration.web_interface_url}
                                setGlobalConfig={setConfiguration}
                                setLocalRevision={setLocalRevision}
                                dbUpdateCallback={integrationsService.updateSmtpConfiguration} />
          </td>
        </tr>
        </tbody>
      </table>
    </React.Fragment>
  )

}

export default SmtpIntegration;