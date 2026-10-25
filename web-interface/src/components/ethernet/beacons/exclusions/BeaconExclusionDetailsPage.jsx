import React, {useContext, useEffect, useState} from "react";
import {Link, Navigate, useParams} from "react-router-dom";
import moment from "moment";
import numeral from "numeral";
import {toast} from "react-toastify";
import ApiRoutes from "../../../../util/ApiRoutes";
import usePageTitle from "../../../../util/UsePageTitle";
import useSelectedTenant from "../../../system/tenantselector/useSelectedTenant";
import LoadingSpinner from "../../../misc/LoadingSpinner";
import CardTitleWithControls from "../../../shared/CardTitleWithControls";
import BeaconsService from "../../../../services/ethernet/BeaconsService";
import {UserContext} from "../../../../App";
import {userHasPermission} from "../../../../util/Tools";
import BeaconExclusionImpact from "./BeaconExclusionImpact";

const beaconsService = new BeaconsService();

export default function BeaconExclusionDetailsPage() {

  const {uuid} = useParams();
  const [organizationId, tenantId] = useSelectedTenant();
  const user = useContext(UserContext);
  const canEdit = userHasPermission(user, "ethernet_monitoring_manage");

  const [exclusion, setExclusion] = useState(null);
  const [notFound, setNotFound] = useState(false);
  const [redirect, setRedirect] = useState(false);
  const [revision, setRevision] = useState(new Date());

  usePageTitle(exclusion ? `Beacon Exclusion: ${exclusion.name}` : "Beacon Exclusion Details");

  useEffect(() => {
    setExclusion(null);
    beaconsService.findExclusion(uuid, organizationId, tenantId, setExclusion, () => setNotFound(true));
  }, [uuid, organizationId, tenantId, revision]);

  const onToggleEnabled = (e) => {
    e.preventDefault();

    beaconsService.setExclusionEnabled(uuid, organizationId, tenantId, !exclusion.enabled, () => {
      toast.success(exclusion.enabled ? "Exclusion disabled." : "Exclusion enabled.");
      setRevision(new Date());
    });
  }

  const onApply = (e) => {
    e.preventDefault();

    const impact = exclusion.impact;
    if (!confirm("Delete the " + impact.matching_beacons + " existing " +
        (impact.matching_beacons === 1 ? "beacon" : "beacons") + " matching this exclusion now? Beacons where " +
        "only some devices match lose those devices. This cannot be undone.")) {
      return;
    }

    beaconsService.applyExclusion(uuid, organizationId, tenantId, (response) => {
      const result = response.data;
      toast.success("Deleted " + numeral(result.complete_beacons).format("0,0") + " beacons and " +
        numeral(result.matching_sources).format("0,0") + " devices.");
      setRevision(new Date());
    });
  }

  const onDelete = (e) => {
    e.preventDefault();

    if (!confirm("Really delete this exclusion? Traffic it matched is detected again with the next detection run.")) {
      return;
    }

    beaconsService.deleteExclusion(uuid, organizationId, tenantId, () => {
      toast.success("Exclusion deleted.");
      setRedirect(true);
    });
  }

  if (redirect) {
    return <Navigate to={ApiRoutes.ETHERNET.BEACONS.EXCLUSIONS.INDEX} />
  }

  if (notFound) {
    return (
      <div className="alert alert-warning">
        Exclusion not found. <Link to={ApiRoutes.ETHERNET.BEACONS.EXCLUSIONS.INDEX}>Back to Exclusions</Link>
      </div>
    )
  }

  if (!exclusion) {
    return <LoadingSpinner />
  }

  const criterion = (label, value) => {
    if (!value) {
      return null;
    }

    return (
      <React.Fragment>
        <dt>{label}</dt>
        <dd className="machine-data">{value}</dd>
      </React.Fragment>
    )
  }

  const hasMatches = exclusion.impact && exclusion.impact.matching_beacons > 0;

  return (
    <React.Fragment>
      <div className="row">
        <div className="col-md-12">
          <nav aria-label="breadcrumb">
            <ol className="breadcrumb">
              <li className="breadcrumb-item"><Link to={ApiRoutes.ETHERNET.OVERVIEW}>Ethernet</Link></li>
              <li className="breadcrumb-item"><Link to={ApiRoutes.ETHERNET.BEACONS.INDEX}>Beacons</Link></li>
              <li className="breadcrumb-item"><Link to={ApiRoutes.ETHERNET.BEACONS.EXCLUSIONS.INDEX}>Exclusions</Link></li>
              <li className="breadcrumb-item active" aria-current="page">{exclusion.name}</li>
            </ol>
          </nav>
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-6">
          <h1>Exclusion &quot;{exclusion.name}&quot;</h1>
        </div>

        <div className="col-6 text-end">
          <Link to={ApiRoutes.ETHERNET.BEACONS.EXCLUSIONS.INDEX} className="btn btn-secondary me-1">Back</Link>
          {canEdit ?
            <Link to={ApiRoutes.ETHERNET.BEACONS.EXCLUSIONS.EDIT(exclusion.uuid)} className="btn btn-primary">Edit</Link>
            : null}
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-6">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title="Details" />

              <dl className="mb-0">
                <dt>Name</dt>
                <dd>{exclusion.name}</dd>
                <dt>Description</dt>
                <dd>
                  {exclusion.description ?
                    <span style={{whiteSpace: "pre-wrap"}}>{exclusion.description}</span>
                    : <span className="text-muted">No description.</span>}
                </dd>
                <dt>Status</dt>
                <dd>
                  {exclusion.enabled ? "Enabled" : <span className="text-warning">Disabled</span>}
                  {exclusion.enabled ? null :
                    <span className="text-muted"> (kept, but not applied until enabled again)</span>}
                </dd>
                <dt>Last Matched</dt>
                <dd>
                  {exclusion.last_matched_at ?
                    <span title={moment(exclusion.last_matched_at).format()}>
                      {moment(exclusion.last_matched_at).fromNow()}
                    </span>
                    : <span className="text-muted">Never</span>}
                </dd>
                <dt>Suppressed Candidates</dt>
                <dd title="Device and destination pairs that were kept out of detection, counted once per detection run">
                  {numeral(exclusion.match_count).format("0,0")}
                </dd>
                <dt>Matching Existing Beacons</dt>
                <dd>
                  {exclusion.impact ? numeral(exclusion.impact.matching_beacons).format("0,0") : <span className="text-muted">n/a</span>}
                </dd>
                <dt>Created</dt>
                <dd title={moment(exclusion.created_at).format()}>{moment(exclusion.created_at).fromNow()}</dd>
                <dt>Last Updated</dt>
                <dd className="mb-0" title={moment(exclusion.updated_at).format()}>
                  {moment(exclusion.updated_at).fromNow()}
                </dd>
              </dl>
            </div>
          </div>
        </div>

        <div className="col-6">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title="Criteria" />

              <p className="text-muted">
                Traffic is excluded when all of these match.
              </p>

              <dl className="mb-0">
                {criterion("Beacon Types", exclusion.beacon_types ? exclusion.beacon_types.join(", ") : null)}
                {criterion("Destination Host", exclusion.destination_host)}
                {criterion("Destination Address", exclusion.destination_cidr)}
                {criterion("Destination Ports", exclusion.destination_ports)}
                {criterion("Device Address", exclusion.source_cidr)}
                {criterion("Device MAC", exclusion.source_mac)}
              </dl>
            </div>
          </div>

          {canEdit ?
            <div className="row mt-3">
              <div className="col-6">
                <div className="card">
                  <div className="card-body">
                    <CardTitleWithControls title={exclusion.enabled ? "Disable Exclusion" : "Enable Exclusion"} slim={true} />

                    <p>
                      {exclusion.enabled ?
                        "A disabled exclusion is kept but no longer applied. Traffic it matched is detected again " +
                        "with the next detection run."
                        : "Enable the exclusion to apply it again with the next detection run."}
                    </p>

                    <button className={"btn btn-sm " + (exclusion.enabled ? "btn-warning" : "btn-success")}
                            onClick={onToggleEnabled}>
                      {exclusion.enabled ? "Disable Exclusion" : "Enable Exclusion"}
                    </button>
                  </div>
                </div>
              </div>

              <div className="col-6">
                <div className="card">
                  <div className="card-body">
                    <CardTitleWithControls title="Delete Exclusion" slim={true} />

                    <p>
                      Deleting the exclusion does not restore beacons that were deleted with it. Traffic it matched
                      is detected again with the next detection run.
                    </p>

                    <button className="btn btn-sm btn-danger" onClick={onDelete}>Delete Exclusion</button>
                  </div>
                </div>
              </div>
            </div> : null}
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-12">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title="Existing Beacons"
                                     refreshAction={() => setRevision(new Date())} />

              <p className="text-muted">
                Beacons that already exist and match this exclusion. They no longer receive check-ins and do not
                raise alerts, but are kept for reference until you delete them.
              </p>

              <BeaconExclusionImpact impact={exclusion.impact} />

              {canEdit && hasMatches ?
                <p className="mt-3 mb-0">
                  <a href="#" className="btn btn-sm btn-danger" onClick={onApply}>Delete Matching Beacons</a>
                </p> : null}
            </div>
          </div>
        </div>
      </div>
    </React.Fragment>
  )

}
