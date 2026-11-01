import React, {useEffect, useState} from "react";
import ConfigurationValue from "../../configuration/ConfigurationValue";
import ConfigurationModal from "../../configuration/modal/ConfigurationModal";
import EncryptedConfigurationValue from "../../configuration/EncryptedConfigurationValue";
import LoadingSpinner from "../../misc/LoadingSpinner";
import ConnectService from "../../../services/ConnectService";
import ConnectStatus from "./ConnectStatus";
import ProvidedServices from "./ProvidedServices";
import usePageTitle from "../../../util/UsePageTitle";

const connectService = new ConnectService();

export default function ConnectPage() {

  usePageTitle("Nzyme Connect");

  const [configuration, setConfiguration] = useState(null)
  const [localRevision, setLocalRevision] = useState(0)

  const [status, setStatus] = useState(null);

  useEffect(() => {
    connectService.getConfiguration(setConfiguration)
    connectService.getStatus(setStatus);
  }, [localRevision])

  if (!configuration || !status) {
    return <LoadingSpinner />
  }

  return (
      <React.Fragment>
        <div className="row">
          <div className="col-md-12">
            <h1>Nzyme Connect</h1>
          </div>
        </div>

        <div className="row mt-3">
          <div className="col-md-3">
            <ConnectStatus status={status}/>
          </div>
          <div className="col-md-9">
            <div className="card">
              <div className="card-body">
                <p className="mb-3">
                  The optional Nzyme Connect APIs provide a link to up-to-date information and cluster improvements,
                  including:
                </p>

                <ul>
                  <li>Data enrichment lookup tables (incl. GeoIP and vendor information)</li>
                  <li>Alert signatures and definitions</li>
                  <li>Health monitoring for all of your clusters</li>
                </ul>

                <p className="mb-0">
                  You can get your free API key
                  at <a href="https://connect.nzyme.org/" target="_blank">https://connect.nzyme.org/</a>. Please refer
                  to the <a href="https://connect.nzyme.org/faq" target="_blank">frequently asked questions</a> to
                  learn more.
                </p>
              </div>
            </div>
          </div>
        </div>

        <div className="row mt-3">
          <div className="col-xl-12 col-xxl-6">
            <div className="card">
              <div className="card-body">
                <h3>Provided Services</h3>

                <p>
                  The following services are enabled for <strong>this</strong> Nzyme cluster. Services are managed
                  using your account at <a href="https://connect.nzyme.org/" target="_blank">https://connect.nzyme.org/</a>.
                </p>

                {status.connection_summary !== "ok" ? <p>
                  Note that this information may be outdated because your cluster is not currently connected
                  to <em>Connect</em>.
                </p> : null}

                <ProvidedServices status={status}/>
              </div>
            </div>
          </div>
        </div>

        <div className="row mt-3">
          <div className="col-xl-12 col-xxl-6">
            <div className="card">
              <div className="card-body">
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
                    <td>Nzyme Connect enabled</td>
                    <td>
                      <ConfigurationValue value={configuration.connect_enabled.value}
                                          configKey={configuration.connect_enabled.key}
                                          required={true}
                                          boolean={true}/>
                    </td>
                    <td>
                      <ConfigurationModal config={configuration.connect_enabled}
                                          setGlobalConfig={setConfiguration}
                                          setLocalRevision={setLocalRevision}
                                          dbUpdateCallback={connectService.updateConfiguration}/>
                    </td>
                  </tr>
                  <tr>
                    <td>API Key</td>
                    <td>
                      <EncryptedConfigurationValue isSet={configuration.connect_api_key.value_is_set}
                                                   configKey={configuration.connect_api_key.key}
                                                   required={true}/>
                    </td>
                    <td>
                      <ConfigurationModal config={configuration.connect_api_key}
                                          setGlobalConfig={setConfiguration}
                                          setLocalRevision={setLocalRevision}
                                          dbUpdateCallback={connectService.updateConfiguration}/>
                    </td>
                  </tr>
                  </tbody>
                </table>
              </div>
            </div>
          </div>
        </div>
      </React.Fragment>
  )

}