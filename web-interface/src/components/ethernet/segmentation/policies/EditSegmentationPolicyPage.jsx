import React, {useEffect, useState} from "react";
import {Link, Navigate, useParams} from "react-router-dom";
import {toast} from "react-toastify";
import ApiRoutes from "../../../../util/ApiRoutes";
import usePageTitle from "../../../../util/UsePageTitle";
import useSelectedTenant from "../../../system/tenantselector/useSelectedTenant";
import LoadingSpinner from "../../../misc/LoadingSpinner";
import {Presets} from "../../../shared/timerange/TimeRange";
import SegmentationService from "../../../../services/ethernet/SegmentationService";
import {normalizeRule} from "./PolicyDefinition";
import PolicyForm from "./PolicyForm";

const segmentationService = new SegmentationService();

export default function EditSegmentationPolicyPage() {

  const {uuid} = useParams();
  const [organizationId, tenantId] = useSelectedTenant();

  const [policy, setPolicy] = useState(null);
  const [notFound, setNotFound] = useState(false);
  const [redirect, setRedirect] = useState(false);

  usePageTitle(policy ? `Edit Segmentation Policy: ${policy.name}` : "Edit Segmentation Policy");

  useEffect(() => {
    setPolicy(null);
    segmentationService.findPolicy(uuid, organizationId, tenantId, Presets.RELATIVE_HOURS_24, setPolicy,
      () => setNotFound(true));
  }, [uuid, organizationId, tenantId]);

  const save = (payload, onError) => {
    segmentationService.updatePolicy(uuid, organizationId, tenantId, payload, () => {
      toast.success("Policy updated.");
      setRedirect(true);
    }, onError);
  }

  if (redirect) {
    return <Navigate to={ApiRoutes.ETHERNET.SEGMENTATION.POLICIES.DETAILS(uuid)} />
  }

  if (notFound) {
    return (
      <div className="alert alert-warning">
        Policy not found. <Link to={ApiRoutes.ETHERNET.SEGMENTATION.POLICIES.INDEX}>Back to Policies</Link>
      </div>
    )
  }

  return (
    <React.Fragment>
      <div className="row">
        <div className="col-md-12">
          <nav aria-label="breadcrumb">
            <ol className="breadcrumb">
              <li className="breadcrumb-item"><Link to={ApiRoutes.ETHERNET.OVERVIEW}>Ethernet</Link></li>
              <li className="breadcrumb-item"><Link to={ApiRoutes.ETHERNET.SEGMENTATION.INDEX}>Segmentation</Link></li>
              <li className="breadcrumb-item"><Link to={ApiRoutes.ETHERNET.SEGMENTATION.POLICIES.INDEX}>Policies</Link></li>
              {policy ?
                <li className="breadcrumb-item">
                  <Link to={ApiRoutes.ETHERNET.SEGMENTATION.POLICIES.DETAILS(policy.uuid)}>{policy.name}</Link>
                </li> : null}
              <li className="breadcrumb-item active" aria-current="page">Edit</li>
            </ol>
          </nav>
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-8">
          <h1>Edit Policy {policy ? <span>&quot;{policy.name}&quot;</span> : null}</h1>
        </div>

        <div className="col-4 text-end">
          <Link to={ApiRoutes.ETHERNET.SEGMENTATION.POLICIES.DETAILS(uuid)} className="btn btn-primary">Back</Link>
        </div>
      </div>

      {policy ?
        <PolicyForm organizationId={organizationId}
                    tenantId={tenantId}
                    initialValues={{
                      name: policy.name,
                      description: policy.description ? policy.description : "",
                      status: policy.status,
                      alert_on_connected: policy.alert_on_connected,
                      alert_on_attempted: policy.alert_on_attempted,
                      scope: normalizeRule(policy.scope),
                      allowed: policy.allowed ? policy.allowed.map(normalizeRule) : []
                    }}
                    submitText="Save Policy"
                    onSubmit={save} />
        : <div className="row mt-3"><div className="col-12"><LoadingSpinner /></div></div>}
    </React.Fragment>
  )

}
