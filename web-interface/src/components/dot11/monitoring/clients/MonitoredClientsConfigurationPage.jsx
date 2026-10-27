import React, {useEffect, useState} from "react";
import {Link, useParams} from "react-router-dom";
import Dot11Service from "../../../../services/Dot11Service";
import LoadingSpinner from "../../../misc/LoadingSpinner";
import ApiRoutes from "../../../../util/ApiRoutes";
import ConfigurationValue from "../../../configuration/ConfigurationValue";
import ConfigurationModal from "../../../configuration/modal/ConfigurationModal";
import Paginator from "../../../misc/Paginator";
import MonitoredClientsTable from "./MonitoredClientsTable";
import {toast} from "react-toastify";
import usePageTitle from "../../../../util/UsePageTitle";

const dot11Service = new Dot11Service();

export default function MonitoredClientsConfigurationPage() {

  const { uuid } = useParams();

  const [ssid, setSSID] = useState(null);

  const [clients, setClients] = useState(null);
  const [configuration, setConfiguration] = useState(null);

  const [revision, setRevision] = useState(0);

  const perPage = 25;
  const [page, setPage] = useState(1);

  usePageTitle(ssid ? `Allowed Clients: ${ssid.ssid}` : "Allowed Clients Configuration");

  useEffect(() => {
    dot11Service.findMonitoredSSID(uuid, setSSID, () => {  });
  }, [uuid]);

  useEffect(() => {
    if (ssid) {
      dot11Service.getMonitoredClientsConfiguration(uuid, setConfiguration);
      dot11Service.findMonitoredClients(uuid, perPage, (page-1)*perPage, setClients);
    }
  }, [ssid, revision]);

  // We are using a custom update callback because we have to pass the SSID UUID.
  const onConfigurationUpdate = (newConfig, successCallback, errorCallback) => {
    dot11Service.updateMonitoredClientsConfiguration(newConfig, uuid, successCallback, errorCallback);
  }

  const onChange = () => setRevision(prevRev => prevRev + 1)

  const onDeleteAll = (e) => {
    e.preventDefault();

    if (!confirm("Really delete all clients? Each will reappear as unapproved client " +
      "next time nzyme records it.")) {
      return;
    }

    dot11Service.deleteAllMonitoredClients(uuid, () => {
      toast.success('All known clients deleted.');
      onChange();
    });
  }

  if (!ssid || !configuration || !clients) {
    return <LoadingSpinner />
  }

  return (
      <React.Fragment>
        <div className="row">
          <div className="col-md-7">
            <nav aria-label="breadcrumb">
              <ol className="breadcrumb">
                <li className="breadcrumb-item"><Link to={ApiRoutes.DOT11.OVERVIEW}>WiFi</Link></li>
                <li className="breadcrumb-item"><Link to={ApiRoutes.DOT11.MONITORING.INDEX}>Monitoring</Link></li>
                <li className="breadcrumb-item">Monitored Networks</li>
                <li className="breadcrumb-item">
                  <Link to={ApiRoutes.DOT11.MONITORING.SSID_DETAILS(ssid.uuid)}>{ssid.ssid}</Link>
                </li>
                <li className="breadcrumb-item active" aria-current="page">Allowed Clients</li>
              </ol>
            </nav>
          </div>
          <div className="col-md-5">
            <span className="float-end">
              <a className="btn btn-secondary" href="https://go.nzyme.org/wifi-client-monitoring">Help</a>&nbsp;
              <Link className="btn btn-primary" to={ApiRoutes.DOT11.MONITORING.SSID_DETAILS(uuid)}>Back</Link>
            </span>
          </div>
        </div>

        <div className="col-md-12">
          <h1>Allowed Clients</h1>
        </div>

        <div className="row mt-3">
          <div className="col-xl-12 col-xxl-6">
          <div className="card">
              <div className="card-body">
                <h3>Configuration</h3>

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
                                          setLocalRevision={setRevision}
                                          dbUpdateCallback={onConfigurationUpdate}/>
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
                                          setLocalRevision={setRevision}
                                          dbUpdateCallback={onConfigurationUpdate}/>
                    </td>
                  </tr>
                  </tbody>
                </table>
              </div>
            </div>
          </div>
        </div>

        <div className="row mt-3">
          <div className="col-xl-12 col-xxl-6">
            <div className="card">
              <div className="card-body">
                <h3 style={{display: "inline-block"}}>Clients</h3>

                <button className="btn btn-danger btn-sm float-end" onClick={onDeleteAll}>Delete All</button>
                <br style={{clear: "both"}}/>

                <p>
                  All clients that have ever been detected as connected and communicating with an access point (BSSID)
                  of this monitored network.
                </p>

                <MonitoredClientsTable clients={clients.clients}
                                       monitoredNetwork={ssid}
                                       onChange={onChange}/>

                {clients.total > 0 ?
                  <Paginator page={page} setPage={setPage} perPage={perPage} itemCount={clients.total}/> : null}
              </div>
            </div>
          </div>
        </div>
      </React.Fragment>
  )

}