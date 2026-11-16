import React, {useState} from "react";
import {Link, Navigate, useLocation} from "react-router-dom";
import {toast} from "react-toastify";
import ApiRoutes from "../../../../util/ApiRoutes";
import usePageTitle from "../../../../util/UsePageTitle";
import useSelectedTenant from "../../../system/tenantselector/useSelectedTenant";
import SegmentationService from "../../../../services/ethernet/SegmentationService";
import {selectorOfDraft} from "./PolicyDefinition";
import PolicyForm from "./PolicyForm";

const segmentationService = new SegmentationService();

const selectorsFromQuery = (query, side) => {
  const type = query.get(side + "_type");
  if (!type || type === "ANY") {
    return [];
  }

  const selector = selectorOfDraft({type: type, value: query.get(side + "_value")});
  return selector ? [selector] : [];
}

export default function CreateSegmentationPolicyPage() {

  usePageTitle("Create Segmentation Policy");

  const [organizationId, tenantId] = useSelectedTenant();
  const query = new URLSearchParams(useLocation().search);

  const [created, setCreated] = useState(null);
  const [templatesOpen, setTemplatesOpen] = useState(false);

  const create = (payload, onError) => {
    segmentationService.createPolicy(organizationId, tenantId, payload, (policy) => {
      toast.success("Policy created.");
      setCreated(policy);
    }, onError);
  }

  if (created) {
    return <Navigate to={ApiRoutes.ETHERNET.SEGMENTATION.POLICIES.DETAILS(created.uuid)} />
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
              <li className="breadcrumb-item active" aria-current="page">Create</li>
            </ol>
          </nav>
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-6">
          <h1>Create Policy</h1>
        </div>

        <div className="col-6 text-end">
          <button type="button" className="btn btn-secondary me-1" onClick={() => setTemplatesOpen(true)}>
            Use a Template
          </button>
          <Link to={ApiRoutes.ETHERNET.SEGMENTATION.POLICIES.INDEX} className="btn btn-primary">Back to Policies</Link>
        </div>
      </div>

      <PolicyForm organizationId={organizationId}
                  tenantId={tenantId}
                  initialValues={{
                    scope: {
                      sources: selectorsFromQuery(query, "source"),
                      destinations: selectorsFromQuery(query, "destination"),
                      services: []
                    }
                  }}
                  submitText="Create Policy"
                  onSubmit={create}
                  templatesOpen={templatesOpen}
                  onTemplatesClose={() => setTemplatesOpen(false)} />
    </React.Fragment>
  )

}
