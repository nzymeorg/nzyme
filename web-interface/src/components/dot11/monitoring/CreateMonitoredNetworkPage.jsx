import React, {useEffect, useState} from "react";
import ApiRoutes from "../../../util/ApiRoutes";
import Dot11Service from "../../../services/Dot11Service";
import SSIDSuggestions from "./SSIDSuggestions";
import LoadingSpinner from "../../misc/LoadingSpinner";
import {Link, Navigate} from "react-router-dom";
import useSelectedTenant from "../../system/tenantselector/useSelectedTenant";
import usePageTitle from "../../../util/UsePageTitle";

const dot11Service = new Dot11Service();

function CreateMonitoredNetworkPage() {

  usePageTitle("Create Monitored WiFi Network");

  const [organizationId, tenantId] = useSelectedTenant();

  const [ssidNames, setSSIDNames] = useState(null);
  const [ssid, setSSID] = useState("");

  const [formSubmitting, setFormSubmitting] = useState(false);
  const [submittedSuccessfully, setSubmittedSuccessfully] = useState(false);

  const onSuggestionSelected = function (ssid) {
    setSSID(ssid);
  }

  const onSubmitForm = function () {
    setFormSubmitting(true);

    dot11Service.createMonitoredSSID(ssid, organizationId, tenantId, function () {
      setSubmittedSuccessfully(true);
    }, function () {
      setFormSubmitting(false);
    })
  }

  const formIsReady = function() {
    return ssid && ssid.trim().length > 0;
  }

  useEffect(() => {
    dot11Service.findAllSSIDNames(setSSIDNames, organizationId, tenantId);
  }, [organizationId, tenantId])

  if (submittedSuccessfully) {
    return <Navigate to={ApiRoutes.DOT11.MONITORING.INDEX} />
  }

  if (ssidNames === null) {
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
                <li className="breadcrumb-item active" aria-current="page">Create Monitored Network</li>
              </ol>
            </nav>
          </div>

          <div className="col-md-2">
            <Link className="btn btn-primary float-end" to={ApiRoutes.DOT11.MONITORING.INDEX}>Back</Link>
          </div>

          <div className="row">
            <div className="col-xl-12 col-xxl-6">
              <div className="card">
                <div className="card-body">
                  <h3>Create Monitored Network</h3>

                  <form className="mt-3">
                    <div className="mb-3">
                      <label htmlFor="ssid" className="form-label">
                        SSID{' '}
                        <span className="badge bg-warning" style={{position: "relative", top: -2, right: -3}}>
                          Case-sensitive
                        </span>
                      </label>

                      <input type="text"
                             className="form-control"
                             id="ssid" value={ssid} onChange={(e) => { setSSID(e.target.value) }}/>

                      <div className="form-text">
                        The SSID (network name) of the network you want to monitor. You will add details of the
                        new monitored network after it has been created.
                      </div>
                    </div>

                    <SSIDSuggestions input={ssid}
                                     ssidNames={ssidNames}
                                     onSuggestionSelected={onSuggestionSelected} />

                    <button type="button" className="btn btn-primary" onClick={onSubmitForm} disabled={!formIsReady()}>
                      {formSubmitting ? "Please wait ..." : "Create Monitored Network"}
                    </button>
                  </form>
                </div>
              </div>
            </div>
          </div>
        </div>
      </React.Fragment>
  )

}

export default CreateMonitoredNetworkPage;