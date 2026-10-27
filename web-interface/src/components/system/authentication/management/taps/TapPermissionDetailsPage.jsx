import React, {useEffect, useState} from "react";
import {Link, Navigate, useParams} from "react-router-dom";
import AuthenticationManagementService from "../../../../../services/AuthenticationManagementService";
import LoadingSpinner from "../../../../misc/LoadingSpinner";
import Routes from "../../../../../util/ApiRoutes";
import ApiRoutes from "../../../../../util/ApiRoutes";
import {toast} from "react-toastify";
import TapSecret from "./TapSecret";
import CardTitleWithControls from "../../../../shared/CardTitleWithControls";
import LatitudeLongitude from "../../../../shared/LatitudeLongitude";
import LatLonMap from "../../../../shared/LatLonMap";
import usePageTitle from "../../../../../util/UsePageTitle";

const authenticationManagementService = new AuthenticationManagementService();

function TapPermissionDetailsPage() {

  usePageTitle("Tap Permissions Details");

  const { organizationId } = useParams();
  const { tenantId } = useParams();
  const { tapUuid } = useParams();

  const [organization, setOrganization] = useState(null);
  const [tenant, setTenant] = useState(null);
  const [tap, setTap] = useState(null);

  const [redirect, setRedirect] = useState(false);
  const [revision, setRevision] = useState(0);

  const deleteTap = function() {
    if (!confirm("Really delete tap?")) {
      return;
    }

    authenticationManagementService.deleteTapPermission(organizationId, tenantId, tapUuid, function() {
      setRedirect(true);
      toast.success('Tap deleted.');
    });
  }

  const cycleSecret = function() {
    if (!confirm("Really cycle tap secret? You will have to change the secret in the tap configuration file to comtinue using the tap.")) {
      return;
    }

    authenticationManagementService.cycleTapSecret(organizationId, tenantId, tapUuid, function() {
      setTap(null);
      setRevision(revision + 1);
      toast.success('Tap secret cycled.');
    });
  }

  useEffect(() => {
    authenticationManagementService.findOrganization(organizationId, setOrganization);
    authenticationManagementService.findTenantOfOrganization(organizationId, tenantId, setTenant);
    authenticationManagementService.findTapPermission(organizationId, tenantId, tapUuid, setTap);
  }, [organizationId, tenantId, revision])

  if (redirect) {
    return <Navigate to={ApiRoutes.SYSTEM.AUTHENTICATION.MANAGEMENT.TENANTS.TAPS_PAGE(organization.id, tenant.id)} />
  }

  if (!organization || !tenant || !tap) {
    return <LoadingSpinner />
  }

  return (
      <React.Fragment>
        <div className="row">
          <div className="col-md-9">
            <nav aria-label="breadcrumb">
              <ol className="breadcrumb">
                <li className="breadcrumb-item">
                  <Link to={Routes.SYSTEM.AUTHENTICATION.MANAGEMENT.INDEX}>Authentication &amp; Authorization</Link>
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
                  <Link to={ApiRoutes.SYSTEM.AUTHENTICATION.MANAGEMENT.TENANTS.TAPS_PAGE(organization.id, tenant.id)}>
                    Taps
                  </Link>
                </li>
                <li className="breadcrumb-item active" aria-current="page">{tap.name}</li>
              </ol>
            </nav>
          </div>

          <div className="col-md-3">
            <span className="float-end">
              <Link className="btn btn-secondary"
                 to={ApiRoutes.SYSTEM.AUTHENTICATION.MANAGEMENT.TENANTS.TAPS_PAGE(organization.id, tenant.id)}>
                Back
              </Link>{' '}
              <Link className="btn btn-primary" to={ApiRoutes.SYSTEM.AUTHENTICATION.MANAGEMENT.TAPS.EDIT(organization.id, tenant.id, tap.uuid)}>
                Edit Tap
              </Link>
            </span>
          </div>
        </div>

        <div className="row">
          <div className="col-md-12">
            <h1>Authentication and Configuration of Tap &quot;{tap.name}&quot;</h1>
          </div>
        </div>

        <div className="row mt-3">
          <div className="col-md-8">
            <div className="row">
              <div className="col-md-12">
                <div className="card">
                  <div className="card-body">
                    <CardTitleWithControls title="Description" slim={true}/>

                    <p className="mb-0">
                      {tap.description}
                    </p>
                  </div>
                </div>
              </div>
            </div>

            <div className="row mt-3">
              <div className="col-md-12">
                <div className="card">
                  <div className="card-body">
                    <CardTitleWithControls title="Tap Secret" slim={true}/>

                    <p>The tap secret is used to authenticate taps when they connect to nzyme. Every tap has a unique
                    secret that must be configured in the tap configuration file. <strong>You must update the secret
                    in your tap configuration file after cycling it.</strong> The secret is encrypted in the database.</p>

                    <TapSecret secret={tap.secret} onCycle={cycleSecret} />
                  </div>
                </div>
              </div>
            </div>
          </div>

          <div className="col-md-4">
            <div className="row">
              <div className="col-md-12">
                <div className="card">
                  <div className="card-body">
                    <CardTitleWithControls title="Delete Tap" slim={true}/>

                    <p>
                      <strong>Warning:</strong> Deleted taps cannot be restored. The deleted tap will not be able to
                      report data until you create a new tap and cycle the tap secret. Existing data will not be
                      removed.
                    </p>

                    <button className="btn btn-sm btn-danger" onClick={deleteTap}>
                      Delete Tap
                    </button>
                  </div>
                </div>
              </div>
            </div>
          </div>

          <div className="row mt-3">
            <div className="col-xl-12 col-xxl-6">
              <div className="card">
                <div className="card-body">
                  <CardTitleWithControls title="Location" slim={true}/>

                  <div className="alert alert-info">
                    Note that latitude/longitude are configured using the <em>Edit Tap</em> button on this page
                    and location/floor are configured on the tenant location pages.
                  </div>

                  <dl className="mb-0">
                    <dt>Location</dt>
                    <dd>{tap.location_id && tap.location_name ?
                        <Link to={ApiRoutes.SYSTEM.AUTHENTICATION.MANAGEMENT.TENANTS.LOCATIONS.DETAILS(tap.organization_id, tap.tenant_id, tap.location_id)}>{tap.location_name}</Link>
                        : <span className="text-muted">n/a</span>}</dd>
                    <dt>Floor</dt>
                    <dd>{tap.location_id && tap.location_name && tap.floor_id && tap.floor_name ?
                        <Link to={ApiRoutes.SYSTEM.AUTHENTICATION.MANAGEMENT.TENANTS.LOCATIONS.FLOORS.DETAILS(tap.organization_id, tap.tenant_id, tap.location_id, tap.floor_id)}>{tap.floor_name}</Link>
                        : <span className="text-muted">n/a</span>}</dd>
                    <dt>Latitude, Longitude</dt>
                    <dd><LatitudeLongitude latitude={tap.latitude} longitude={tap.longitude} skipAccuracy={true} /></dd>
                  </dl>

                  { tap.latitude && tap.longitude ? <LatLonMap editMode={false}
                                                               containerHeight={300}
                                                               defaultZoomLevel={18}
                                                               latitude={tap.latitude}
                                                               longitude={tap.longitude} />
                    : null }
                </div>
              </div>
            </div>
          </div>
        </div>
      </React.Fragment>
  )

}

export default TapPermissionDetailsPage;