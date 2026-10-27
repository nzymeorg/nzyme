import React, {useEffect, useState} from "react";
import {Link, Navigate, useParams} from "react-router-dom";
import AuthenticationManagementService from "../../../../../../../services/AuthenticationManagementService";
import ApiRoutes from "../../../../../../../util/ApiRoutes";
import LoadingSpinner from "../../../../../../misc/LoadingSpinner";
import FloorPlan from "../../../../../../shared/floorplan/FloorPlan";
import {toast} from "react-toastify";
import moment from "moment/moment";
import numeral from "numeral";
import UploadFloorPlanForm from "./UploadFloorPlanForm";
import FloorPlanTapsTable from "./FloorPlanTapsTable";
import usePageTitle from "../../../../../../../util/UsePageTitle";

const authenticationManagementService = new AuthenticationManagementService();
const authenticationMgmtService = new AuthenticationManagementService();

function FloorDetailsPage() {

  usePageTitle("Floor Details");

  const { organizationId } = useParams();
  const { tenantId } = useParams();
  const { locationId } = useParams();
  const { floorId } = useParams();

  const [revision, setRevision] = useState(0);
  const [placedTap, setPlacedTap] = useState(null);

  const [organization, setOrganization] = useState(null);
  const [tenant, setTenant] = useState(null);
  const [location, setLocation] = useState(null);
  const [floor, setFloor] = useState(null);
  const [plan, setPlan] = useState(null);

  const [redirect, setRedirect] = useState(false);

  useEffect(() => {
    setOrganization(null);
    setTenant(null);
    setLocation(null);
    setFloor(null);
    setPlan(null);

    authenticationManagementService.findOrganization(organizationId, setOrganization);
    authenticationManagementService.findTenantOfOrganization(organizationId, tenantId, setTenant);
    authenticationManagementService.findTenantLocation(locationId, organizationId, tenantId, setLocation);
    authenticationManagementService.findFloorOfTenantLocation(organizationId, tenantId, locationId, floorId, setFloor);
  }, [organizationId, tenantId, revision])

  useEffect(() => {
    if (floor && floor.has_floor_plan) {
      authenticationManagementService.findFloorPlan(organizationId, tenantId, locationId, floorId, setPlan)
    }
  }, [floor]);

  const deleteFloor = () => {
    if (!confirm("Really delete floor? This will delete the associated floor plan as well as all tap placements, but " +
        "not the taps themselves.")) {
      return;
    }

    authenticationManagementService.deleteFloorOfTenantLocation(organizationId, tenantId, locationId, floorId, () => {
      toast.success('Floor deleted.');
      setRedirect(true);
    })
  }

  const onTapPlaced = (tap) => {
    setPlacedTap(tap);
  }

  const onTapPlacementComplete = () => {
    setPlacedTap(null);
  }

  const onRemoveTap = (tapId) => {
    if (!confirm("Really remove tap position from this floor?")) {
      return;
    }

    authenticationMgmtService.removeTapFromFloorPlan(organizationId, tenantId, locationId, floorId, tapId, () => {
      setRevision(prevRev => prevRev + 1);
    });
  }

  const onPlanRevisionSaved = (newTapPositions) => {
    if (!confirm("Really save all tap positions on this floor?")) {
      return;
    }

    Object.keys(newTapPositions).map((tapId) => {
      const position = newTapPositions[tapId];

      authenticationManagementService
          .placeTapOnFloorPlan(organizationId, tenantId, locationId, floorId, tapId, position.x, position.y, () => {
            toast.success('Tap positions saved.');
            setRevision(prevRev => prevRev + 1);
          })
    });
  }

  const onPlanDeleted = () => {
    if (!confirm("Really delete floor plan and all tap positions?")) {
      return;
    }

    authenticationMgmtService.deleteFloorPlan(organizationId, tenantId, locationId, floorId, () => {
      toast.success('Plan deleted.');
      setRevision(prevRev => prevRev + 1);
    })
  }

  const dimensions = () => {
    if (!plan) {
      return false;
    }

    return (
        <dl>
          <dt>Width (X Axis)</dt>
          <dd>{numeral(plan.width_meters).format(0)} Meters / {numeral(plan.width_meters * 3.28084).format(0)} Feet</dd>
          <dt>Length (Y Axis)</dt>
          <dd>{numeral(plan.length_meters).format(0)} Meters / {numeral(plan.length_meters * 3.28084).format(0)} Feet</dd>
        </dl>
    )
  }

  if (redirect) {
    return <Navigate to={ApiRoutes.SYSTEM.AUTHENTICATION.MANAGEMENT.TENANTS.LOCATIONS.DETAILS(organization.id, tenant.id, location.id)} />
  }

  if (!organization || !tenant || !location || !floor) {
    return <LoadingSpinner />
  }

  return (
      <div className="row">
        <div className="col-md-9">
          <nav aria-label="breadcrumb">
            <ol className="breadcrumb">
              <li className="breadcrumb-item">
                <Link to={ApiRoutes.SYSTEM.AUTHENTICATION.MANAGEMENT.INDEX}>Authentication &amp; Authorization</Link>
              </li>
              <li className="breadcrumb-item">Organizations</li>
              <li className="breadcrumb-item">
                <Link to={ApiRoutes.SYSTEM.AUTHENTICATION.MANAGEMENT.ORGANIZATIONS.DETAILS(organization.id)}>
                  {organization.name}
                </Link>
              </li>
              <li className="breadcrumb-item">
                <Link to={ApiRoutes.SYSTEM.AUTHENTICATION.MANAGEMENT.ORGANIZATIONS.TENANTS_PAGE(organization.id)}>
                  Tenants
                </Link>
              </li>
              <li className="breadcrumb-item">
                <Link to={ApiRoutes.SYSTEM.AUTHENTICATION.MANAGEMENT.TENANTS.DETAILS(organization.id, tenant.id)}>
                {tenant.name}
                </Link>
              </li>
              <li className="breadcrumb-item">
                <Link to={ApiRoutes.SYSTEM.AUTHENTICATION.MANAGEMENT.TENANTS.LOCATIONS_PAGE(organization.id, tenant.id)}>
                  Locations
                </Link>
              </li>
              <li className="breadcrumb-item">
                <Link
                  to={ApiRoutes.SYSTEM.AUTHENTICATION.MANAGEMENT.TENANTS.LOCATIONS.DETAILS(organization.id, tenant.id, location.id)}>
                  {location.name}
                </Link>
              </li>
              <li className="breadcrumb-item">Floors</li>
              <li className="breadcrumb-item active" aria-current="page">{floor.name} (#{floor.number})</li>
            </ol>
          </nav>
        </div>

        <div className="col-md-3">
          <span className="float-end">
            <Link className="btn btn-secondary"
               to={ApiRoutes.SYSTEM.AUTHENTICATION.MANAGEMENT.TENANTS.LOCATIONS.DETAILS(organization.id, tenant.id, location.id)}>
              Back
            </Link>{' '}
            <Link className="btn btn-primary"
               to={ApiRoutes.SYSTEM.AUTHENTICATION.MANAGEMENT.TENANTS.LOCATIONS.FLOORS.EDIT(organization.id, tenant.id, location.id, floor.id)}>
                Edit Floor
            </Link>
          </span>
        </div>

        <div className="col-md-12">
          <h1>Floor &quot;{floor.name}&quot; (#{floor.number}) of Location &quot;{location.name}&quot;</h1>
        </div>

        <div className="row mt-3">
          <div className="col-md-6">
            <div className="card">
              <div className="card-body">
                <h3>Metadata</h3>

                <dl className="mb-0">
                  <dt>Created At</dt>
                  <dd title={moment(floor.created_at).format()}>{moment(floor.created_at).fromNow()}</dd>
                  <dt>Updated At</dt>
                  <dd title={moment(floor.updated_at).format()}>{moment(floor.updated_at).fromNow()}</dd>
                </dl>
              </div>
            </div>
          </div>

          <div className="col-md-6">
            <div className="card">
              <div className="card-body">
                <h3>Delete Floor</h3>

                <p>
                  Deleting a floor will delete the associated floor plan as well as all tap placements, but never the
                  taps themselves.
                </p>

                <button className="btn btn-sm btn-danger" onClick={deleteFloor}>
                  Delete Floor
                </button>
              </div>
            </div>
          </div>
        </div>

        <div className="row mt-3">
          <div className="col-md-12">
            <div className="card">
              <div className="card-body">
                <h3>Floor Plan</h3>

                {dimensions()}

                <FloorPlan containerHeight={500}
                           floorHasPlan={floor.has_floor_plan}
                           plan={plan}
                           taps={floor.tap_positions}
                           placedTap={placedTap}
                           editModeEnabled={true}
                           onTapPlacementComplete={onTapPlacementComplete}
                           onRevisionSaved={onPlanRevisionSaved}
                           onPlanDeleted={onPlanDeleted} />
              </div>
            </div>
          </div>
        </div>

        <div className="row mt-3">
          <div className="col-xl-12 col-xxl-8">
            <div className="card">
              <div className="card-body">
                <h3>Place Taps</h3>

                <FloorPlanTapsTable organizationId={organizationId}
                                    tenantId={tenantId}
                                    floorId={floorId}
                                    onRemoveTap={onRemoveTap}
                                    onTapPlaced={onTapPlaced} />
              </div>
            </div>
          </div>
        </div>

        <div className="row mt-3">
          <div className="col-xl-12 col-xxl-8">
            <div className="card">
              <div className="card-body">
                <h3>{floor.has_floor_plan ? "Replace" : "Upload"} Floor Plan</h3>

                <UploadFloorPlanForm organizationId={organizationId}
                                     tenantId={tenantId}
                                     locationId={locationId}
                                     floorId={floorId}
                                     hasExistingPlan={floor.has_floor_plan}
                                     onSuccess={() => {
                                       setRevision(prevRev => prevRev + 1)
                                     }}
                                     submitText="Upload Floor Plan"/>
              </div>
            </div>
          </div>
        </div>
      </div>
  )

}

export default FloorDetailsPage;