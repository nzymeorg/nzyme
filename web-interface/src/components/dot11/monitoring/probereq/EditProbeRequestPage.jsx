import React, {useEffect, useState} from "react";
import {Link, Navigate, useParams} from "react-router-dom";
import {toast} from "react-toastify";
import ApiRoutes from "../../../../util/ApiRoutes";
import ProbeRequestForm from "./ProbeRequestForm";
import Dot11Service from "../../../../services/Dot11Service";
import LoadingSpinner from "../../../misc/LoadingSpinner";
import useSelectedTenant from "../../../system/tenantselector/useSelectedTenant";
import usePageTitle from "../../../../util/UsePageTitle";

const dot11Service = new Dot11Service();

export default function EditProbeRequestPage() {

  const { id } = useParams();

  const [organizationId, tenantId] = useSelectedTenant();

  const [probeRequest, setProbeRequest] = useState(null);

  const [redirect, setRedirect] = useState(false);

  usePageTitle(probeRequest ? `Edit Monitored Probe Request: ${probeRequest.ssid}` : "Edit Monitored Probe Request");

  const create = (ssid, notes) => {
    dot11Service.updateMonitoredProbeRequest(organizationId, tenantId, id, ssid, notes, () => {
      toast.success('Monitored probe request updated.');
      setRedirect(true);
    });
  }

  useEffect(() => {
    dot11Service.findMonitoredProbeRequest(organizationId, tenantId, id, setProbeRequest);
  }, [id, organizationId, tenantId]);

  if (redirect) {
    return <Navigate to={ApiRoutes.DOT11.MONITORING.PROBE_REQUESTS.INDEX} />
  }

  if (!probeRequest) {
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
                <li className="breadcrumb-item">
                  <Link to={ApiRoutes.DOT11.MONITORING.PROBE_REQUESTS.INDEX}>Probe Requests</Link>
                </li>
                <li className="breadcrumb-item">{probeRequest.ssid}</li>
                <li className="breadcrumb-item active" aria-current="page">Edit</li>
              </ol>
            </nav>
          </div>

          <div className="col-md-5">
            <span className="float-end">
              <Link className="btn btn-primary" to={ApiRoutes.DOT11.MONITORING.PROBE_REQUESTS.INDEX}>Back</Link>
            </span>
          </div>
        </div>

        <div className="row">
          <div className="col-md-12">
            <h1>
              Edit Monitored Probe Request &quot;{probeRequest.ssid}&quot;
            </h1>
          </div>
        </div>

        <div className="row mt-3">
          <div className="col-md-6">
            <div className="card">
              <div className="card-body">
                <h3>Edit Monitored Probe Request</h3>

                <ProbeRequestForm ssid={probeRequest.ssid} notes={probeRequest.notes} submitText="Edit Monitored Probe Request" onSubmit={create} />
              </div>
            </div>
          </div>
        </div>
      </React.Fragment>
  )

}