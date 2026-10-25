import React, {useEffect, useState} from "react";
import {Link, Navigate, useParams} from "react-router-dom";
import {toast} from "react-toastify";
import ApiRoutes from "../../../../util/ApiRoutes";
import usePageTitle from "../../../../util/UsePageTitle";
import useSelectedTenant from "../../../system/tenantselector/useSelectedTenant";
import LoadingSpinner from "../../../misc/LoadingSpinner";
import BeaconsService from "../../../../services/ethernet/BeaconsService";
import BeaconExclusionForm from "./BeaconExclusionForm";

const beaconsService = new BeaconsService();

export default function EditBeaconExclusionPage() {

  usePageTitle("Edit Beacon Exclusion");

  const {uuid} = useParams();
  const [organizationId, tenantId] = useSelectedTenant();

  const [exclusion, setExclusion] = useState(null);
  const [notFound, setNotFound] = useState(false);
  const [redirect, setRedirect] = useState(false);

  useEffect(() => {
    setExclusion(null);
    beaconsService.findExclusion(uuid, organizationId, tenantId, setExclusion, () => setNotFound(true));
  }, [uuid, organizationId, tenantId]);

  const save = (payload, onError) => {
    beaconsService.updateExclusion(uuid, organizationId, tenantId, payload, () => {
      toast.success("Exclusion updated.");
      setRedirect(true);
    }, onError);
  }

  if (redirect) {
    return <Navigate to={ApiRoutes.ETHERNET.BEACONS.EXCLUSIONS.DETAILS(uuid)} />
  }

  if (notFound) {
    return (
      <div className="alert alert-warning">
        Exclusion not found. <Link to={ApiRoutes.ETHERNET.BEACONS.EXCLUSIONS.INDEX}>Back to Exclusions</Link>
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
              <li className="breadcrumb-item"><Link to={ApiRoutes.ETHERNET.BEACONS.INDEX}>Beacons</Link></li>
              <li className="breadcrumb-item"><Link to={ApiRoutes.ETHERNET.BEACONS.EXCLUSIONS.INDEX}>Exclusions</Link></li>
              {exclusion ?
                <li className="breadcrumb-item"><Link to={ApiRoutes.ETHERNET.BEACONS.EXCLUSIONS.DETAILS(exclusion.uuid)}>{exclusion.name}</Link></li>
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
          <Link to={ApiRoutes.ETHERNET.BEACONS.EXCLUSIONS.DETAILS(uuid)} className="btn btn-primary">Back</Link>
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-xl-12 col-xxl-8">
          <div className="card">
            <div className="card-body">
              {exclusion ?
                <BeaconExclusionForm organizationId={organizationId}
                                     tenantId={tenantId}
                                     initialValues={{
                                       name: exclusion.name,
                                       description: exclusion.description ? exclusion.description : "",
                                       enabled: exclusion.enabled,
                                       destination_cidr: exclusion.destination_cidr ? exclusion.destination_cidr : "",
                                       destination_host: exclusion.destination_host ? exclusion.destination_host : "",
                                       destination_ports: exclusion.destination_ports ? exclusion.destination_ports : "",
                                       beacon_types: exclusion.beacon_types ? exclusion.beacon_types : [],
                                       source_cidr: exclusion.source_cidr ? exclusion.source_cidr : "",
                                       source_mac: exclusion.source_mac ? exclusion.source_mac : ""
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
