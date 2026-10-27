import React, {useEffect, useState} from "react";
import ApiRoutes from "../../../util/ApiRoutes";
import {Link, useParams} from "react-router-dom";
import {toast} from "react-toastify";
import LoadingSpinner from "../../misc/LoadingSpinner";
import Dot11Service from "../../../services/Dot11Service";
import RestricedSubstringsTable from "./RestricedSubstringsTable";
import usePageTitle from "../../../util/UsePageTitle";

const dot11Service = new Dot11Service();

function RestrictedSubstringsConfigurationPage() {

  const { uuid } = useParams();

  const [ssid, setSSID] = useState(null);
  const [substrings, setSubstrings] = useState(null);

  const [substring, setSubstring] = useState("");

  const [revision, setRevision] = useState(0);

  usePageTitle(ssid ? `Restricted Substring Monitor: ${ssid.ssid}` : "Restricted SSID Substring Monitor Configuration");

  useEffect(() => {
    dot11Service.findMonitoredSSID(uuid, setSSID, () => {});
  }, [uuid, revision]);

  useEffect(() => {
    if (ssid) {
      setSubstrings(ssid.restricted_ssid_substrings);
    }
  }, [ssid]);

  const onCreateSubstring = (e) => {
    e.preventDefault();
    setSubstrings(null);

    dot11Service.addRestrictedSSIDSubstring(uuid, substring, () => {
      setRevision(revision => revision+1)
      toast.success("Restricted substring added.");
    })
  }

  const onDeleteSubstring = (e, substringUUID) => {
    e.preventDefault();

    if (!confirm("Really delete substring?")) {
      return;
    }

    setSubstrings(null);

    dot11Service.deleteRestrictedSSIDSubstring(uuid, substringUUID, () => {
      setRevision(revision => revision+1)
      toast.success("Restricted substring deleted.");
    })
  }

  const formIsReady = () => {
    return substring.trim().length > 0;
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
                <li className="breadcrumb-item active" aria-current="page">Restricted SSID Substrings</li>
              </ol>
            </nav>
          </div>
          <div className="col-md-5">
            <Link className="btn btn-primary float-end" to={ApiRoutes.DOT11.MONITORING.SSID_DETAILS(uuid)}>Back</Link>
          </div>
        </div>

        <div className="col-md-12">
          <h1>
            Restricted SSID Substring Monitor
          </h1>
        </div>

        <div className="row mt-3">
          <div className="col-xl-12 col-xxl-6">
            <div className="card">
              <div className="card-body">
                <h3>Configure Restricted Substrings</h3>

                <p className="mb-3">
                  Note that other monitored networks never trigger a restricted substring alarm because they are
                  considered known and not malicious.
                </p>

                <label htmlFor="substring" className="form-label">Substring (case-insensitive)</label>
                <div className="input-group mb-3">
                  <input type="text" className="form-control" id="substring"
                         onChange={(e) => setSubstring(e.target.value)}/>
                  <button className="btn btn-primary" type="button"
                          disabled={!formIsReady()}
                          onClick={onCreateSubstring}>
                    Add Substring
                  </button>
                </div>

                <RestricedSubstringsTable substrings={substrings} onDeleteSubstring={onDeleteSubstring} />
              </div>
            </div>
          </div>
        </div>
      </React.Fragment>
  )

}

export default RestrictedSubstringsConfigurationPage;