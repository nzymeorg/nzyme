import React, {useEffect, useState} from "react";
import {Link, Navigate, useLocation} from "react-router-dom";
import {toast} from "react-toastify";
import ApiRoutes from "../../../../util/ApiRoutes";
import usePageTitle from "../../../../util/UsePageTitle";
import useSelectedTenant from "../../../system/tenantselector/useSelectedTenant";
import LoadingSpinner from "../../../misc/LoadingSpinner";
import SpoofingService from "../../../../services/ethernet/SpoofingService";
import SpoofingExclusionForm from "./SpoofingExclusionForm";

const spoofingService = new SpoofingService();

const prefillFromFinding = (details) => {
  const finding = details.finding;

  return {
    name: "Exclude " + finding.address + " claimed by " + finding.spoofer_mac,
    description: "Created from ARP poisoning finding of " + finding.address + " by " + finding.spoofer_mac + ".",
    types: ["ARP"],
    address_cidr: finding.address,
    mac: finding.spoofer_mac
  };
}

export default function CreateSpoofingExclusionPage() {

  usePageTitle("Create Spoofing Exclusion");

  const [organizationId, tenantId] = useSelectedTenant();
  const findingUuid = new URLSearchParams(useLocation().search).get("finding");

  const [initialValues, setInitialValues] = useState(findingUuid ? null : {});
  const [redirect, setRedirect] = useState(false);

  useEffect(() => {
    if (!findingUuid) {
      return;
    }

    spoofingService.findArpFinding(findingUuid, organizationId, tenantId,
      (details) => setInitialValues(prefillFromFinding(details)),
      () => setInitialValues({}));
  }, [findingUuid, organizationId, tenantId]);

  const create = (payload, onError) => {
    spoofingService.createExclusion(organizationId, tenantId, payload, () => {
      toast.success("Exclusion created.");
      setRedirect(true);
    }, onError);
  }

  if (redirect) {
    return <Navigate to={ApiRoutes.ETHERNET.SPOOFING.EXCLUSIONS.INDEX} />
  }

  return (
    <React.Fragment>
      <div className="row">
        <div className="col-md-12">
          <nav aria-label="breadcrumb">
            <ol className="breadcrumb">
              <li className="breadcrumb-item"><Link to={ApiRoutes.ETHERNET.OVERVIEW}>Ethernet</Link></li>
              <li className="breadcrumb-item"><Link to={ApiRoutes.ETHERNET.SPOOFING.ARP.INDEX}>Spoofing</Link></li>
              <li className="breadcrumb-item"><Link to={ApiRoutes.ETHERNET.SPOOFING.EXCLUSIONS.INDEX}>Exclusions</Link></li>
              <li className="breadcrumb-item active" aria-current="page">Create</li>
            </ol>
          </nav>
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-8">
          <h1>Create Exclusion</h1>
        </div>

        <div className="col-4 text-end">
          <Link to={ApiRoutes.ETHERNET.SPOOFING.EXCLUSIONS.INDEX} className="btn btn-primary">Back to Exclusions</Link>
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-xl-12 col-xxl-8">
          <div className="card">
            <div className="card-body">
              {initialValues ?
                <SpoofingExclusionForm organizationId={organizationId}
                                       tenantId={tenantId}
                                       initialValues={initialValues}
                                       submitText="Create Exclusion"
                                       onSubmit={create}
                                       isCreate={true} />
                : <LoadingSpinner />}
            </div>
          </div>
        </div>
      </div>
    </React.Fragment>
  )

}
