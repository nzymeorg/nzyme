import React, {useState} from "react";
import {Link, Navigate} from "react-router-dom";
import {toast} from "react-toastify";
import ApiRoutes from "../../../../util/ApiRoutes";
import usePageTitle from "../../../../util/UsePageTitle";
import useSelectedTenant from "../../../system/tenantselector/useSelectedTenant";
import IcmpService from "../../../../services/ethernet/IcmpService";
import IcmpExclusionForm from "./IcmpExclusionForm";

const icmpService = new IcmpService();

export default function CreateIcmpExclusionPage() {

  usePageTitle("Create ICMP Exclusion");

  const [organizationId, tenantId] = useSelectedTenant();
  const [redirect, setRedirect] = useState(false);

  const create = (payload, onError) => {
    icmpService.createExclusion(organizationId, tenantId, payload, () => {
      toast.success("Exclusion created.");
      setRedirect(true);
    }, onError);
  }

  if (redirect) {
    return <Navigate to={ApiRoutes.ETHERNET.ICMP.EXCLUSIONS.INDEX} />
  }

  return (
    <React.Fragment>
      <div className="row">
        <div className="col-md-12">
          <nav aria-label="breadcrumb">
            <ol className="breadcrumb">
              <li className="breadcrumb-item"><Link to={ApiRoutes.ETHERNET.OVERVIEW}>Ethernet</Link></li>
              <li className="breadcrumb-item"><Link to={ApiRoutes.ETHERNET.ICMP.OVERVIEW}>ICMP</Link></li>
              <li className="breadcrumb-item"><Link to={ApiRoutes.ETHERNET.ICMP.EXCLUSIONS.INDEX}>Exclusions</Link></li>
              <li className="breadcrumb-item active" aria-current="page">Create</li>
            </ol>
          </nav>
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-8"><h1>Create Exclusion</h1></div>
        <div className="col-4 text-end">
          <Link to={ApiRoutes.ETHERNET.ICMP.EXCLUSIONS.INDEX} className="btn btn-primary">Back to Exclusions</Link>
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-xl-12 col-xxl-8">
          <div className="card">
            <div className="card-body">
              <IcmpExclusionForm submitText="Create Exclusion" onSubmit={create} />
            </div>
          </div>
        </div>
      </div>
    </React.Fragment>
  )

}
