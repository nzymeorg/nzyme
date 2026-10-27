import React, {useEffect, useState} from "react";
import {Link, Navigate, useLocation} from "react-router-dom";
import {toast} from "react-toastify";
import ApiRoutes from "../../../../../util/ApiRoutes";
import usePageTitle from "../../../../../util/UsePageTitle";
import useSelectedTenant from "../../../../system/tenantselector/useSelectedTenant";
import LoadingSpinner from "../../../../misc/LoadingSpinner";
import DnsTunnelsService from "../../../../../services/ethernet/DnsTunnelsService";
import DnsTunnelExclusionForm from "./DnsTunnelExclusionForm";

const dnsTunnelsService = new DnsTunnelsService();

const prefillFromFinding = (finding) => {
  return {
    name: "Exclude " + finding.domain + " queried by " + finding.client_address,
    description: "Created from DNS tunnel finding of " + finding.domain + " from " + finding.client_address + ".",
    domain: finding.domain,
    client_address_cidr: finding.client_address
  };
}

export default function CreateDnsTunnelExclusionPage() {

  usePageTitle("Create DNS Tunnel Exclusion");

  const [organizationId, tenantId] = useSelectedTenant();
  const query = new URLSearchParams(useLocation().search);
  const findingUuid = query.get("finding");

  const [initialValues, setInitialValues] = useState(findingUuid ? null : {});
  const [redirect, setRedirect] = useState(false);

  useEffect(() => {
    if (findingUuid) {
      dnsTunnelsService.findFinding(findingUuid, organizationId, tenantId,
        (finding) => setInitialValues(prefillFromFinding(finding)),
        () => setInitialValues({}));
    }
  }, [findingUuid, organizationId, tenantId]);

  const create = (payload, onError) => {
    dnsTunnelsService.createExclusion(organizationId, tenantId, payload, () => {
      toast.success("Exclusion created.");
      setRedirect(true);
    }, onError);
  }

  if (redirect) {
    return <Navigate to={ApiRoutes.ETHERNET.DNS.TUNNELS.EXCLUSIONS.INDEX} />
  }

  return (
    <React.Fragment>
      <div className="row">
        <div className="col-md-12">
          <nav aria-label="breadcrumb">
            <ol className="breadcrumb">
              <li className="breadcrumb-item"><Link to={ApiRoutes.ETHERNET.OVERVIEW}>Ethernet</Link></li>
              <li className="breadcrumb-item"><Link to={ApiRoutes.ETHERNET.DNS.INDEX}>DNS</Link></li>
              <li className="breadcrumb-item"><Link to={ApiRoutes.ETHERNET.DNS.TUNNELS.INDEX}>Tunnels</Link></li>
              <li className="breadcrumb-item"><Link to={ApiRoutes.ETHERNET.DNS.TUNNELS.EXCLUSIONS.INDEX}>Exclusions</Link></li>
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
          <Link to={ApiRoutes.ETHERNET.DNS.TUNNELS.EXCLUSIONS.INDEX} className="btn btn-primary">Back to Exclusions</Link>
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-xl-12 col-xxl-8">
          <div className="card">
            <div className="card-body">
              {initialValues ?
                <DnsTunnelExclusionForm organizationId={organizationId}
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
