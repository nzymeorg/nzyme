import React, {useEffect, useState} from "react";
import AuthenticationManagementService from "../../../../../../services/AuthenticationManagementService";
import {Link, Navigate, useParams} from "react-router-dom";
import LoadingSpinner from "../../../../../misc/LoadingSpinner";
import ApiRoutes from "../../../../../../util/ApiRoutes";
import moment from "moment";
import FloorsTable from "./floors/FloorsTable";
import {toast} from "react-toastify";
import numeral from "numeral";
import usePageTitle from "../../../../../../util/UsePageTitle";
import LatLonMap from "../../../../../shared/LatLonMap";
import LatitudeLongitude from "../../../../../shared/LatitudeLongitude";
import * as L from "leaflet";
import CardTitleWithControls from "../../../../../shared/CardTitleWithControls";

const authenticationManagementService = new AuthenticationManagementService();

function LocationManagementDetailsPage() {

  usePageTitle("Tenant Location Details");

  const { organizationId } = useParams();
  const { tenantId } = useParams();
  const { locationId } = useParams();

  const [organization, setOrganization] = useState(null);
  const [tenant, setTenant] = useState(null);

  const [location, setLocation] = useState(null);
  const [environmentalAlertEventingEnabled, setEnvironmentalAlertEventingEnabled] = useState(null);

  const [redirect, setRedirect] = useState(false);

  const locationIcon = L.icon({
    iconUrl: window.appConfig.assetsUri + 'static/leaflet/icon-location.png',
    iconSize: [40, 52],
    iconAnchor: [20, 52],
    tooltipAnchor: [0, -52]
  });

  useEffect(() => {
    authenticationManagementService.findOrganization(organizationId, setOrganization);
    authenticationManagementService.findTenantOfOrganization(organizationId, tenantId, setTenant);
    authenticationManagementService.findTenantLocation(locationId, organizationId, tenantId, setLocation)
  }, [organizationId, tenantId, locationId])

  useEffect(() => {
    if (location) {
      setEnvironmentalAlertEventingEnabled(location.environmental_alert_eventing_enabled);
    }
  }, [location])


  const onEnvironmentalAlertEventingToggle = (e) => {
    setEnvironmentalAlertEventingEnabled(e.target.checked);

    authenticationManagementService.setEnvironmentalAlertEventingOfTenantLocation(
      locationId, organizationId, tenantId, e.target.checked, () => {
        toast.success("Environmental monitoring settings updated.");
      }
    );
  }

  const deleteLocation = () => {
    if (!confirm("Really delete location?")) {
      return;
    }

    authenticationManagementService.deleteTenantLocation(locationId, organizationId, tenantId, () => {
      toast.success('Location deleted.');
      setRedirect(true);
    })
  }

  if (redirect) {
    return <Navigate to={ApiRoutes.SYSTEM.AUTHENTICATION.MANAGEMENT.TENANTS.LOCATIONS_PAGE(organization.id, tenant.id)} />
  }

  if (!organization || !tenant || !location || environmentalAlertEventingEnabled === null) {
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
              <li className="breadcrumb-item active" aria-current="page">{location.name}</li>
            </ol>
          </nav>
        </div>

        <div className="col-md-3">
          <span className="float-end">
            <Link className="btn btn-secondary"
               to={ApiRoutes.SYSTEM.AUTHENTICATION.MANAGEMENT.TENANTS.LOCATIONS_PAGE(organization.id, tenant.id)}>
              Back
            </Link>{' '}
            <Link className="btn btn-primary"
               to={ApiRoutes.SYSTEM.AUTHENTICATION.MANAGEMENT.TENANTS.LOCATIONS.EDIT(organization.id, tenant.id, location.id)}>
                Edit Location
            </Link>
          </span>
        </div>

        <div className="col-md-12">
          <h1>Location &quot;{location.name}&quot;</h1>
        </div>

        <div className="row mt-3">
          <div className="col-md-8">
            <div className="row">
              <div className="col-md-12">
                <div className="card">
                  <div className="card-body">
                    <CardTitleWithControls title="Description" slim={true} />

                    <p>
                      {location.description ? location.description : <em>No Description.</em>}
                    </p>

                    <dl className="mb-0">
                      <dt>Taps placed at this location</dt>
                      <dd>{numeral(location.tap_count).format("0,0")}</dd>
                    </dl>
                  </div>
                </div>
              </div>
            </div>

            { location.latitude && location.longitude ? <div className="row mt-3">
              <div className="col-md-12">
                <div className="card">
                  <div className="card-body">
                    <CardTitleWithControls title="Position" slim={true} />

                     <LatLonMap editMode={false}
                                containerHeight={300}
                                defaultZoomLevel={18}
                                icon={locationIcon}
                                latitude={location.latitude}
                                longitude={location.longitude} />

                    <dl className="mt-3 mb-0">
                      <dt>Latitude, Longitude</dt>
                      <dd>
                        <LatitudeLongitude latitude={location.latitude}
                                           longitude={location.longitude}
                                           skipAccuracy={true} />
                      </dd>
                    </dl>
                  </div>
                </div>
              </div>
            </div> : null }

            <div className="row mt-3">
              <div className="col-md-12">
                <div className="card">
                  <div className="card-body">
                    <CardTitleWithControls title="Floors" slim={true} />

                    <FloorsTable organizationId={organizationId} tenantId={tenantId} locationId={locationId} />

                    <Link className="btn btn-sm btn-secondary" to={ApiRoutes.SYSTEM.AUTHENTICATION.MANAGEMENT.TENANTS.LOCATIONS.FLOORS.CREATE(organization.id, tenant.id, location.id)}>
                      Create Floor
                    </Link>
                  </div>
                </div>
              </div>
            </div>

            <div className="row mt-3">
              <div className="col-md-12">
                <div className="card">
                  <div className="card-body">
                    <CardTitleWithControls title="Environmental Monitoring"
                                           slim={true}
                                           helpLink="https://go.nzyme.org/environmental-monitoring" />

                    <div className="form-check form-switch mt-2">
                      <input className="form-check-input"
                             checked={environmentalAlertEventingEnabled}
                             onChange={onEnvironmentalAlertEventingToggle}
                             type="checkbox"
                             id="enableEnvironmentalAlertEventing"/>
                      <label className="form-check-label" htmlFor="enableEnvironmentalAlertEventing">
                        Create detection events for severe environmental alerts
                      </label>
                    </div>
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
                    <CardTitleWithControls title="Delete Location" slim={true} />

                    <p>
                      You can only delete a location if it has no floors.
                    </p>

                    <button className="btn btn-sm btn-danger" onClick={deleteLocation} disabled={location.floor_count !== 0}>
                      Delete Location
                    </button>
                  </div>
                </div>
              </div>
            </div>

            <div className="row mt-3">
              <div className="col-md-12">
                <div className="card">
                  <div className="card-body">
                    <CardTitleWithControls title="Metadata" slim={true} />

                    <dl className="mb-0">
                      <dt>Created At</dt>
                      <dd title={moment(location.created_at).format()}>{moment(location.created_at).fromNow()}</dd>
                      <dt>Updated At</dt>
                      <dd title={moment(location.updated_at).format()}>{moment(location.updated_at).fromNow()}</dd>
                    </dl>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
  )

}

export default LocationManagementDetailsPage;