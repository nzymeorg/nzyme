import React, {useEffect, useState} from "react";
import {Link, Navigate, useParams} from "react-router-dom";
import Dot11Service from "../../../../services/Dot11Service";
import LoadingSpinner from "../../../misc/LoadingSpinner";
import ApiRoutes from "../../../../util/ApiRoutes";
import MonitoredNetworkConfigurationImportDialog from "./MonitoredNetworkConfigurationImportDialog";
import {toast} from "react-toastify";
import usePageTitle from "../../../../util/UsePageTitle";

const dot11Service = new Dot11Service();
function MonitoredNetworkConfigurationImportPage() {

  const {uuid} = useParams();

  const [ssid, setSSID] = useState(null);

  const [submitted, setSubmitted] = useState(false);

  usePageTitle(ssid ? `Import Configuration: ${ssid.ssid}` : "Import Configuration");

  useEffect(() => {
    dot11Service.findMonitoredSSID(uuid, setSSID, function() {
      // noop
    });
  }, [uuid]);

  const onSubmit = () => {
    toast.success("Import completed.");
    setSubmitted(true);
  }

  if (submitted) {
    return <Navigate to={ApiRoutes.DOT11.MONITORING.SSID_DETAILS(ssid.uuid)} />
  }

  if (!ssid) {
    return <LoadingSpinner />
  }

  return (
      <React.Fragment>
        <div className="row">
          <div className="col-md-10">
            <nav aria-label="breadcrumb">
              <ol className="breadcrumb">
                <li className="breadcrumb-item"><Link to={ApiRoutes.DOT11.OVERVIEW}>WiFi</Link></li>
                <li className="breadcrumb-item"><Link to={ApiRoutes.DOT11.MONITORING.INDEX}>Monitoring</Link></li>
                <li className="breadcrumb-item">Monitored Networks</li>
                <li className="breadcrumb-item">
                  <Link to={ApiRoutes.DOT11.MONITORING.SSID_DETAILS(ssid.uuid)}>{ssid.ssid}</Link>
                </li>
                <li className="breadcrumb-item active" aria-current="page">Configuration Import</li>
              </ol>
            </nav>
          </div>
        </div>

        <div className="row">
          <div className="col-md-10">
            <h1>
              Import Monitoring Configuration for Network &quot;{ssid.ssid}&quot;
            </h1>
          </div>

          <div className="col-md-2">
            <Link className="btn btn-primary float-end" to={ApiRoutes.DOT11.MONITORING.SSID_DETAILS(ssid.uuid)}>Back</Link>
          </div>
        </div>

        <div className="row mt-3">
          <div className="col-xl-12 col-xxl-6">
            <div className="card">
              <div className="card-body">
                <h3>Import</h3>

                <p className="text-muted">
                  Setting up network monitoring manually for large networks with multiple access points can be
                  labor-intensive. To streamline this process, nzyme's import functionality allows you to import
                  recorded configurations.
                </p>

                <h4>It is crucial to consider a few things before importing data:</h4>

                <ul className="text-muted">
                  <li>
                    Always cross-check the imported configuration with a known-good state to prevent the accidental
                    import of BSSIDs and other data associated with potential bad actors already active within the
                    network environment.
                  </li>
                  <li>
                    Allow nzyme to gather data over several hours to ensure it comprehensively captures all available
                    network information.
                  </li>
                  <li>
                    The import process is a one-time operation, but you have the freedom to manually modify the
                    information later or repeat the import procedure as frequently as you wish.
                  </li>
                  <li>
                    Values currently present in the monitoring configuration can be imported without concern. The import
                    mechanism is designed to only add new values, ensuring no duplication occurs. If you import a BSSID
                    that is already being monitored, the process will incorporate new fingerprints, but it will not
                    eliminate any existing fingerprints.
                  </li>
                  <li>
                    The data displayed is based on the information collected by all accessible taps over the past 24 hours.
                  </li>
                </ul>

                <hr />

                <MonitoredNetworkConfigurationImportDialog uuid={ssid.uuid} onSubmit={onSubmit} />
              </div>
            </div>
          </div>
        </div>
      </React.Fragment>
  )

}

export default MonitoredNetworkConfigurationImportPage;