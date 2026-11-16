import React, {useEffect, useState} from "react";
import {Link, Navigate, useParams} from "react-router-dom";
import {toast} from "react-toastify";
import ApiRoutes from "../../../../util/ApiRoutes";
import usePageTitle from "../../../../util/UsePageTitle";
import useSelectedTenant from "../../../system/tenantselector/useSelectedTenant";
import LoadingSpinner from "../../../misc/LoadingSpinner";
import SegmentationService from "../../../../services/ethernet/SegmentationService";
import SegmentForm, {membersToRows} from "./SegmentForm";

const segmentationService = new SegmentationService();

export default function EditSegmentPage() {

  const {uuid} = useParams();
  const [organizationId, tenantId] = useSelectedTenant();

  const [segment, setSegment] = useState(null);
  const [notFound, setNotFound] = useState(false);
  const [redirect, setRedirect] = useState(false);

  const external = segment && segment.kind === "EXTERNAL";

  usePageTitle(segment ? `Edit ${external ? "External Network" : "Segment"}: ${segment.name}` : "Edit Segment");

  useEffect(() => {
    setSegment(null);
    segmentationService.findSegment(uuid, organizationId, tenantId, setSegment, () => setNotFound(true));
  }, [uuid, organizationId, tenantId]);

  const save = (payload, onError) => {
    segmentationService.updateSegment(uuid, organizationId, tenantId, payload, () => {
      toast.success(payload.kind === "EXTERNAL" ? "External network updated." : "Segment updated.");
      setRedirect(true);
    }, onError);
  }

  if (redirect) {
    return <Navigate to={ApiRoutes.ETHERNET.SEGMENTATION.SEGMENTS.INDEX} />
  }

  if (notFound) {
    return (
      <div className="alert alert-warning">
        Segment not found. <Link to={ApiRoutes.ETHERNET.SEGMENTATION.SEGMENTS.INDEX}>Back to Segments</Link>
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
              <li className="breadcrumb-item"><Link to={ApiRoutes.ETHERNET.SEGMENTATION.SEGMENTS.INDEX}>Segments</Link></li>
              {segment ?
                <li className="breadcrumb-item">
                  <Link to={ApiRoutes.ETHERNET.SEGMENTATION.SEGMENTS.DETAILS(uuid)}>{segment.name}</Link>
                </li> : null}
              <li className="breadcrumb-item active" aria-current="page">Edit</li>
            </ol>
          </nav>
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-8">
          <h1>
            Edit {external ? "External Network" : "Segment"} {segment ? <span>&quot;{segment.name}&quot;</span> : null}
          </h1>
        </div>

        <div className="col-4 text-end">
          <Link to={ApiRoutes.ETHERNET.SEGMENTATION.SEGMENTS.INDEX} className="btn btn-primary">Back to Segments</Link>
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-xl-12 col-xxl-8">
          <div className="card">
            <div className="card-body">
              {segment ?
                <SegmentForm organizationId={organizationId}
                             tenantId={tenantId}
                             initialValues={{
                               kind: segment.kind,
                               name: segment.name,
                               description: segment.description ? segment.description : "",
                               notes: segment.notes ? segment.notes : "",
                               members: membersToRows(segment.members),
                               tap_uuids: segment.tap_uuids ? segment.tap_uuids : []
                             }}
                             kindFixed={true}
                             inaccessibleTaps={segment.inaccessible_taps}
                             submitText={external ? "Save External Network" : "Save Segment"}
                             onSubmit={save} />
                : <LoadingSpinner />}
            </div>
          </div>
        </div>
      </div>
    </React.Fragment>
  )

}
