import React, {useState} from "react";
import {Link, Navigate, useLocation} from "react-router-dom";
import {toast} from "react-toastify";
import ApiRoutes from "../../../../util/ApiRoutes";
import usePageTitle from "../../../../util/UsePageTitle";
import useSelectedTenant from "../../../system/tenantselector/useSelectedTenant";
import SegmentationService from "../../../../services/ethernet/SegmentationService";
import SegmentForm from "./SegmentForm";

const segmentationService = new SegmentationService();

const prefillFromQuery = (query) => {
  const values = {kind: "INTERNAL"};

  if (query.get("network")) {
    values.members = [{type: "CIDR", value: query.get("network")}];
  }

  return values;
}

export default function CreateSegmentPage() {

  usePageTitle("Create Segment");

  const [organizationId, tenantId] = useSelectedTenant();
  const query = new URLSearchParams(useLocation().search);

  const [redirect, setRedirect] = useState(false);

  const create = (payload, onError) => {
    segmentationService.createSegment(organizationId, tenantId, payload, () => {
      toast.success(payload.kind === "EXTERNAL" ? "External network created." : "Segment created.");
      setRedirect(true);
    }, onError);
  }

  if (redirect) {
    return <Navigate to={ApiRoutes.ETHERNET.SEGMENTATION.SEGMENTS.INDEX} />
  }

  return (
    <React.Fragment>
      <div className="row">
        <div className="col-md-12">
          <nav aria-label="breadcrumb">
            <ol className="breadcrumb">
              <li className="breadcrumb-item"><Link to={ApiRoutes.ETHERNET.OVERVIEW}>Ethernet</Link></li>
              <li className="breadcrumb-item"><Link to={ApiRoutes.ETHERNET.SEGMENTATION.INDEX}>Segmentation</Link></li>
              <li className="breadcrumb-item"><Link to={ApiRoutes.ETHERNET.SEGMENTATION.SEGMENTS.INDEX}>Segments</Link></li>
              <li className="breadcrumb-item active" aria-current="page">Create</li>
            </ol>
          </nav>
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-8">
          <h1>Create Segment or External Network</h1>
        </div>

        <div className="col-4 text-end">
          <Link to={ApiRoutes.ETHERNET.SEGMENTATION.SEGMENTS.INDEX} className="btn btn-primary">Back to Segments</Link>
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-xl-12 col-xxl-8">
          <div className="card">
            <div className="card-body">
              <SegmentForm organizationId={organizationId}
                           tenantId={tenantId}
                           initialValues={prefillFromQuery(query)}
                           onSubmit={create} />
            </div>
          </div>
        </div>
      </div>
    </React.Fragment>
  )

}
