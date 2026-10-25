import React, {useEffect, useState} from "react";
import {Link, Navigate, useLocation} from "react-router-dom";
import {toast} from "react-toastify";
import ApiRoutes from "../../../../util/ApiRoutes";
import usePageTitle from "../../../../util/UsePageTitle";
import useSelectedTenant from "../../../system/tenantselector/useSelectedTenant";
import LoadingSpinner from "../../../misc/LoadingSpinner";
import BeaconsService from "../../../../services/ethernet/BeaconsService";
import BeaconExclusionForm from "./BeaconExclusionForm";

const beaconsService = new BeaconsService();

const prefillFromBeacon = (beacon) => {
  const values = {
    name: "Exclude " + beacon.destination,
    description: "Created from beacon \"" + beacon.name + "\"."
  };

  if (beacon.destination_host) {
    values.destination_host = beacon.destination_host;
  } else if (beacon.destination_address) {
    values.destination_cidr = beacon.destination_address;
  }

  if (beacon.destination_port) {
    values.destination_ports = String(beacon.destination_port);
  }

  return values;
}

export default function CreateBeaconExclusionPage() {

  usePageTitle("Create Beacon Exclusion");

  const [organizationId, tenantId] = useSelectedTenant();
  const beaconUuid = new URLSearchParams(useLocation().search).get("beacon");

  const [initialValues, setInitialValues] = useState(beaconUuid ? null : {});
  const [redirect, setRedirect] = useState(false);

  useEffect(() => {
    if (!beaconUuid) {
      return;
    }

    beaconsService.findBeacon(beaconUuid, organizationId, tenantId,
      (beacon) => setInitialValues(prefillFromBeacon(beacon)),
      () => setInitialValues({}));
  }, [beaconUuid, organizationId, tenantId]);

  const create = (payload, onError) => {
    beaconsService.createExclusion(organizationId, tenantId, payload, () => {
      toast.success("Exclusion created.");
      setRedirect(true);
    }, onError);
  }

  if (redirect) {
    return <Navigate to={ApiRoutes.ETHERNET.BEACONS.EXCLUSIONS.INDEX} />
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
          <Link to={ApiRoutes.ETHERNET.BEACONS.EXCLUSIONS.INDEX} className="btn btn-primary">Back to Exclusions</Link>
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-xl-12 col-xxl-8">
          <div className="card">
            <div className="card-body">
              {initialValues ?
                <BeaconExclusionForm organizationId={organizationId}
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
