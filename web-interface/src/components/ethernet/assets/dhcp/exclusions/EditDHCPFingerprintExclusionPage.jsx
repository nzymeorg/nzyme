import React, {useEffect, useState} from "react";
import {Link, Navigate, useParams} from "react-router-dom";
import {toast} from "react-toastify";
import ApiRoutes from "../../../../../util/ApiRoutes";
import usePageTitle from "../../../../../util/UsePageTitle";
import useSelectedTenant from "../../../../system/tenantselector/useSelectedTenant";
import LoadingSpinner from "../../../../misc/LoadingSpinner";
import AssetsService from "../../../../../services/ethernet/AssetsService";
import DHCPFingerprintExclusionForm from "./DHCPFingerprintExclusionForm";

const assetsService = new AssetsService();

export default function EditDHCPFingerprintExclusionPage() {

  usePageTitle("Edit DHCP Fingerprint Exclusion");

  const {uuid} = useParams();
  const [organizationId, tenantId] = useSelectedTenant();

  const [exclusion, setExclusion] = useState(null);
  const [notFound, setNotFound] = useState(false);
  const [redirect, setRedirect] = useState(false);

  useEffect(() => {
    setExclusion(null);
    assetsService.findDHCPFingerprintExclusion(uuid, organizationId, tenantId, setExclusion, () => setNotFound(true));
  }, [uuid, organizationId, tenantId]);

  const save = (payload, onError) => {
    assetsService.updateDHCPFingerprintExclusion(uuid, organizationId, tenantId, payload, () => {
      toast.success("Exclusion updated.");
      setRedirect(true);
    }, onError);
  }

  if (redirect) {
    return <Navigate to={ApiRoutes.ETHERNET.ASSETS.DHCP.EXCLUSIONS.DETAILS(uuid)} />
  }

  if (notFound) {
    return (
      <div className="alert alert-warning">
        Exclusion not found. <Link to={ApiRoutes.ETHERNET.ASSETS.DHCP.EXCLUSIONS.INDEX}>Back to Exclusions</Link>
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
              <li className="breadcrumb-item"><Link to={ApiRoutes.ETHERNET.ASSETS.INDEX}>Assets</Link></li>
              <li className="breadcrumb-item"><Link to={ApiRoutes.ETHERNET.ASSETS.DHCP.INDEX}>DHCP</Link></li>
              <li className="breadcrumb-item"><Link to={ApiRoutes.ETHERNET.ASSETS.DHCP.EXCLUSIONS.INDEX}>Exclusions</Link></li>
              {exclusion ?
                <li className="breadcrumb-item"><Link to={ApiRoutes.ETHERNET.ASSETS.DHCP.EXCLUSIONS.DETAILS(exclusion.uuid)}>{exclusion.name}</Link></li>
                : null}
              <li className="breadcrumb-item active" aria-current="page">Edit</li>
            </ol>
          </nav>
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-8">
          <h1>Edit Exclusion {exclusion ? <span>&quot;{exclusion.name}&quot;</span> : null}</h1>
        </div>

        <div className="col-4 text-end">
          <Link to={ApiRoutes.ETHERNET.ASSETS.DHCP.EXCLUSIONS.DETAILS(uuid)} className="btn btn-primary">Back</Link>
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-xl-12 col-xxl-8">
          <div className="card">
            <div className="card-body">
              {exclusion ?
                <DHCPFingerprintExclusionForm organizationId={organizationId}
                                              tenantId={tenantId}
                                              initialValues={{
                                                name: exclusion.name,
                                                description: exclusion.description ? exclusion.description : "",
                                                enabled: exclusion.enabled,
                                                mac: exclusion.mac,
                                                transaction_types: exclusion.transaction_types ? exclusion.transaction_types : []
                                              }}
                                              submitText="Save Exclusion"
                                              onSubmit={save} />
                : <LoadingSpinner />}
            </div>
          </div>
        </div>
      </div>
    </React.Fragment>
  )

}
