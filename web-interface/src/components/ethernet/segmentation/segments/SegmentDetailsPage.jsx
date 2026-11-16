import React, {useContext, useEffect, useState} from "react";
import {Link, Navigate, useParams} from "react-router-dom";
import moment from "moment";
import {toast} from "react-toastify";
import ApiRoutes from "../../../../util/ApiRoutes";
import usePageTitle from "../../../../util/UsePageTitle";
import useSelectedTenant from "../../../system/tenantselector/useSelectedTenant";
import LoadingSpinner from "../../../misc/LoadingSpinner";
import DDTimestamp from "../../../misc/DDTimestamp";
import CardTitleWithControls from "../../../shared/CardTitleWithControls";
import SegmentationService from "../../../../services/ethernet/SegmentationService";
import TapsService from "../../../../services/TapsService";
import {UserContext} from "../../../../App";
import {apiErrorMessage, userHasPermission} from "../../../../util/Tools";
import {deleteSegmentQuestion, SEGMENT_KINDS, segmentTapNames} from "../SegmentationFormat";
import ContextNotes from "../../../context/ContextNotes";

const segmentationService = new SegmentationService();
const tapsService = new TapsService();

export default function SegmentDetailsPage() {

  const {uuid} = useParams();
  const [organizationId, tenantId] = useSelectedTenant();
  const user = useContext(UserContext);
  const canEdit = userHasPermission(user, "ethernet_monitoring_manage");

  const [segment, setSegment] = useState(null);
  const [taps, setTaps] = useState(null);
  const [notFound, setNotFound] = useState(false);
  const [redirect, setRedirect] = useState(false);

  const external = segment && segment.kind === "EXTERNAL";
  const title = external ? "External Network" : "Segment";

  usePageTitle(segment ? `${title}: ${segment.name}` : "Segment Details");

  useEffect(() => {
    setSegment(null);
    segmentationService.findSegment(uuid, organizationId, tenantId, setSegment, () => setNotFound(true));
  }, [uuid, organizationId, tenantId]);

  useEffect(() => {
    setTaps(null);
    tapsService.findAllTapsHighLevel(organizationId, tenantId, (r) => setTaps(r.data.taps));
  }, [organizationId, tenantId]);

  const onDelete = (e) => {
    e.preventDefault();

    const what = external ? "external network" : "segment";

    if (!confirm(deleteSegmentQuestion(segment))) {
      return;
    }

    segmentationService.deleteSegment(uuid, organizationId, tenantId, () => {
      toast.success(external ? "External network deleted." : "Segment deleted.");
      setRedirect(true);
    }, (error) => {
      toast.error(apiErrorMessage(error, "Could not delete the " + what + "."));
    });
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

  if (!segment || !taps) {
    return <LoadingSpinner />
  }

  const kind = SEGMENT_KINDS[segment.kind];
  const networks = segment.members.filter((member) => member.type !== "ASN");
  const asns = segment.members.filter((member) => member.type === "ASN");
  const limitedTo = segmentTapNames(segment, taps);

  return (
    <React.Fragment>
      <div className="row">
        <div className="col-md-12">
          <nav aria-label="breadcrumb">
            <ol className="breadcrumb">
              <li className="breadcrumb-item"><Link to={ApiRoutes.ETHERNET.OVERVIEW}>Ethernet</Link></li>
              <li className="breadcrumb-item"><Link to={ApiRoutes.ETHERNET.SEGMENTATION.INDEX}>Segmentation</Link></li>
              <li className="breadcrumb-item"><Link to={ApiRoutes.ETHERNET.SEGMENTATION.SEGMENTS.INDEX}>Segments</Link></li>
              <li className="breadcrumb-item active" aria-current="page">{segment.name}</li>
            </ol>
          </nav>
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-6">
          <h1>{title} &quot;{segment.name}&quot;</h1>
        </div>

        <div className="col-6 text-end">
          {canEdit ?
            <React.Fragment>
              <a href="#" className="btn btn-danger me-1" onClick={onDelete}
                 title={"Its addresses show up as " + (external ? "Internet" : "Unassigned") + " afterwards."}>
                Delete {title}
              </a>
              <Link to={ApiRoutes.ETHERNET.SEGMENTATION.SEGMENTS.EDIT(segment.uuid)} className="btn btn-primary me-1">
                Edit
              </Link>
            </React.Fragment> : null}
          <Link to={ApiRoutes.ETHERNET.SEGMENTATION.SEGMENTS.INDEX} className="btn btn-secondary">Back</Link>
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-4">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title="Details" />

              <dl className="mb-0">
                <dt>Type</dt>
                <dd>
                  {kind ? kind.title : segment.kind}{' '}
                  {kind ? <span className="text-muted">{kind.description}</span> : null}
                </dd>
                <dt>Description</dt>
                <dd>{segment.description ? segment.description : <span className="text-muted">None</span>}</dd>
                <dt>Used by Policies</dt>
                <dd className="mb-0" title="What a policy refers to cannot be deleted. Remove it from the policy first.">
                  {segment.policies.length > 0 ?
                    segment.policies.map((policy, i) => {
                      return (
                        <React.Fragment key={policy.uuid}>
                          {i > 0 ? ", " : null}
                          <Link to={ApiRoutes.ETHERNET.SEGMENTATION.POLICIES.DETAILS(policy.uuid)}>{policy.name}</Link>
                        </React.Fragment>
                      )
                    })
                    : <span className="text-muted">None</span>}
                </dd>
              </dl>
            </div>
          </div>
        </div>

        <div className="col-4">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title="Networks" />

              <dl className="mb-0">
                <dt>Networks</dt>
                <dd>
                  {networks.length > 0 ?
                    networks.map((member) => {
                      return <div key={member.network}><span className="machine-data">{member.network}</span></div>
                    })
                    : <span className="text-muted">None</span>}
                </dd>
                {asns.length > 0 ?
                  <React.Fragment>
                    <dt>Autonomous Systems</dt>
                    <dd>
                      {asns.map((member) => {
                        return <div key={member.asn}><span className="machine-data">AS{member.asn}</span></div>
                      })}
                    </dd>
                  </React.Fragment> : null}
                <dt>Taps</dt>
                <dd className="mb-0"
                    title={limitedTo ? "Only applies to what these taps record." : "Applies to what any tap records."}>
                  {limitedTo ?
                    limitedTo.map((name, i) => <div key={i}>{name}</div>)
                    : <span className="text-muted">All taps</span>}
                </dd>
              </dl>
            </div>
          </div>
        </div>

        <div className="col-4">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title="Metadata" />

              <dl className="mb-0">
                <dt>Created</dt>
                <DDTimestamp timestamp={segment.created_at} />
                <dt>Last Updated</dt>
                <dd className="mb-0" title={moment(segment.updated_at).format()}>
                  {moment(segment.updated_at).fromNow()}
                </dd>
              </dl>
            </div>
          </div>
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-8">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title="Notes" />

              <ContextNotes notes={segment.notes}
                            emptyText={"This " + title.toLowerCase() + " has no notes attached to it."} />
            </div>
          </div>
        </div>
      </div>
    </React.Fragment>
  )

}
