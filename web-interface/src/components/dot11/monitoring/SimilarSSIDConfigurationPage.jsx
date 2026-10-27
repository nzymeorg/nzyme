import React, {useEffect, useState} from "react";
import {Link, Navigate, useParams} from "react-router-dom";
import Dot11Service from "../../../services/Dot11Service";
import ApiRoutes from "../../../util/ApiRoutes";
import LoadingSpinner from "../../misc/LoadingSpinner";
import SimilarSSIDSimulator from "./SimilarSSIDSimulator";
import {toast} from "react-toastify";
import usePageTitle from "../../../util/UsePageTitle";

const dot11Service = new Dot11Service();

function SimilarSSIDConfigurationPage() {

  const { uuid } = useParams();

  const [ssid, setSSID] = useState(null);

  const [threshold, setThreshold] = useState(0);

  const [revision, setRevision] = useState(0);
  const [redirect, setRedirect] = useState(false);

  usePageTitle(ssid ? `Similar SSID Monitor: ${ssid.ssid}` : "Similar SSID Monitor Configuration");

  useEffect(() => {
    dot11Service.findMonitoredSSID(uuid, setSSID, () => {});
  }, [uuid]);

  useEffect(() => {
    if (ssid) {
      setThreshold(ssid.similar_looking_ssid_threshold);
    }
  }, [ssid]);

  const onSubmit = () => {
    dot11Service.setSimilarSSIDMonitorConfiguration(uuid, threshold, () => {
      toast.success("Configuration updated.");
      setRedirect(true);
    })
  }

  if (redirect) {
    return <Navigate to={ApiRoutes.DOT11.MONITORING.SSID_DETAILS(uuid)} />
  }

  if (!ssid) {
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
                <li className="breadcrumb-item active" aria-current="page">Similar SSID Monitor</li>
              </ol>
            </nav>
          </div>
          <div className="col-md-5">
            <Link className="btn btn-primary float-end" to={ApiRoutes.DOT11.MONITORING.SSID_DETAILS(uuid)}>Back</Link>
          </div>
        </div>

        <div className="col-md-12">
          <h1>
            Similar SSID Monitor
          </h1>
        </div>

        <div className="row mt-3">
          <div className="col-xl-12 col-xxl-6">
            <div className="card">
              <div className="card-body">
                <h3>Configure Similar SSID Monitor</h3>

                <p className="mb-3">
                  Note that other monitored networks never trigger a similar SSID alarm because they are considered
                  known and not malicious. Comparison is performed in a case-insensitive way.
                </p>
                
                <label htmlFor="threshold" className="form-label">Threshold</label>
                <div className="input-group mb-3" style={{width: 110}}>
                  <input type="number"
                         className="form-control"
                         id="threshold"
                         value={threshold}
                         min={0}
                         max={100}
                         onChange={(e) => setThreshold(e.target.value)}/>
                  <span className="input-group-text">%</span>
                </div>

                <button className="btn btn-secondary" onClick={(e) => {e.preventDefault(); setRevision(revision => revision+1)}}>
                  Simulate
                </button>{' '}

                <button className="btn btn-primary" onClick={onSubmit}>
                  Save Configuration
                </button>
              </div>
            </div>
          </div>
        </div>

        <SimilarSSIDSimulator threshold={threshold} uuid={uuid} revision={revision} />
      </React.Fragment>
  )

}

export default SimilarSSIDConfigurationPage;