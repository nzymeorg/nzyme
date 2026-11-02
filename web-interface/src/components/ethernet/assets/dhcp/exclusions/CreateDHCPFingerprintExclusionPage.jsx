import React, {useState} from "react";
import {Link, Navigate, useLocation} from "react-router-dom";
import {toast} from "react-toastify";
import ApiRoutes from "../../../../../util/ApiRoutes";
import usePageTitle from "../../../../../util/UsePageTitle";
import useSelectedTenant from "../../../../system/tenantselector/useSelectedTenant";
import AssetsService from "../../../../../services/ethernet/AssetsService";
import DHCPFingerprintExclusionForm from "./DHCPFingerprintExclusionForm";

const assetsService = new AssetsService();

const prefillFromMac = (mac) => {
  return {
    name: "Exclude " + mac,
    description: "Created from the list of assets with flaky DHCP fingerprints.",
    mac: mac
  };
}

export default function CreateDHCPFingerprintExclusionPage() {

  usePageTitle("Create DHCP Fingerprint Exclusion");

  const [organizationId, tenantId] = useSelectedTenant();
  const query = new URLSearchParams(useLocation().search);
  const mac = query.get("mac");

  const [redirect, setRedirect] = useState(false);

  const create = (payload, onError) => {
    assetsService.createDHCPFingerprintExclusion(organizationId, tenantId, payload, () => {
      toast.success("Exclusion created.");
      setRedirect(true);
    }, onError);
  }

  if (redirect) {
    return <Navigate to={ApiRoutes.ETHERNET.ASSETS.DHCP.EXCLUSIONS.INDEX} />
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
          <Link to={ApiRoutes.ETHERNET.ASSETS.DHCP.EXCLUSIONS.INDEX} className="btn btn-primary">Back to Exclusions</Link>
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-xl-12 col-xxl-8">
          <div className="card">
            <div className="card-body">
              <DHCPFingerprintExclusionForm organizationId={organizationId}
                                            tenantId={tenantId}
                                            initialValues={mac ? prefillFromMac(mac) : {}}
                                            submitText="Create Exclusion"
                                            onSubmit={create} />
            </div>
          </div>
        </div>
      </div>
    </React.Fragment>
  )

}
