import {Link} from "react-router-dom";
import React, {useContext, useEffect, useState} from "react";
import useSelectedTenant from "../system/tenantselector/useSelectedTenant";
import LoadingSpinner from "../misc/LoadingSpinner";
import LocationsService from "../../services/LocationsService";

import numeral from "numeral";
import LocationTemperature from "./shared/LocationTemperature";
import LocationWind from "./shared/LocationWind";
import LocationVisibility from "./shared/LocationVisibility";
import ApiRoutes from "../../util/ApiRoutes";
import LocationWeatherCondition from "./shared/LocationWeatherCondition";
import {UserContext} from "../../App";
import {userHasPermission} from "../../util/Tools";
import {alertsAtLocationLink} from "../alerts/AlertFilterFields";

const locationsService = new LocationsService();

export default function LocationsTable() {

  const [organizationId, tenantId] = useSelectedTenant();

  const user = useContext(UserContext);

  const [locations, setLocations] = useState(null);
  const [noLocation, setNoLocation] = useState(null);

  useEffect(() => {
    setLocations(null);
    setNoLocation(null);

    locationsService.findAll(organizationId, tenantId, setLocations)
    locationsService.findNoLocation(organizationId, tenantId, setNoLocation)
  }, [organizationId, tenantId]);

  const renderAlertCount = (count) => {
    if (count === null) {
      return <span className="text-muted">n/a</span>
    }
    if (count === 0) {
      return <span className="text-muted">None</span>;
    }
    return <span className="badge text-bg-danger">{numeral(count).format("0,0")}</span>;
  };

  const renderEnvironmentalAlerts = (alerts) => {
    if (alerts === null) {
      return <span className="text-muted">n/a</span>;
    }
    if (alerts.length === 0) {
      return <span className="text-muted">None</span>;
    }

    const shown = alerts.slice(0, 2).map(a => a.event || "Unknown event").join(", ");
    const remaining = alerts.length - 2;

    return (
      <span>
      <span className="text-danger text-bold">{shown}</span>
        {remaining > 0 && <span className="text-muted"> (+{remaining} more)</span>}
    </span>
    );
  };

  if (locations === null || noLocation === null) {
    return <LoadingSpinner />
  }

  const showNoLocation = noLocation.tap_count > 0 || noLocation.alert_count > 0;

  const noLocationsConfigured = (
    <div className="alert alert-info mb-0">No locations configured. Learn more about
      locations in the <a href="https://go.nzyme.org/locations">documentation</a>.</div>
  )

  if (locations.length === 0 && !showNoLocation) {
    return noLocationsConfigured
  }

  return (
    <>
    {locations.length === 0 ? <div className="mb-3">{noLocationsConfigured}</div> : null}

    <table className="table table-sm table-hover table-striped">
      <thead>
      <tr>
        <th>Name</th>
        <th>Taps</th>
        <th>Detection Alerts</th>
        <th>Current Weather Observation</th>
        <th>Severe Environmental Alerts</th>
      </tr>
      </thead>
      <tbody>
      {locations.map((l, i) => {
        return <tr key={i}>
          <td><Link to={ApiRoutes.LOCATIONS.DETAILS(l.id)}>{l.name}</Link></td>
          <td>{numeral(l.tap_count).format("0,0")}</td>
          <td>{renderAlertCount(l.alert_count)}</td>
          <td>
            <LocationWeatherCondition environment={l.environment} />,{' '}
            <LocationTemperature environment={l.environment} />,{' '}
            <LocationWind environment={l.environment} />,{' '}
            <LocationVisibility environment={l.environment} />
          </td>
          <td>{renderEnvironmentalAlerts(l.environment ? l.environment.alerts : null)}</td>
        </tr>
      })}
      {showNoLocation ?
        <tr>
          <td>
            {userHasPermission(user, "alerts_view")
              ? <Link to={alertsAtLocationLink(noLocation.name)} className="text-muted">{noLocation.name}</Link>
              : <span className="text-muted">{noLocation.name}</span>}
          </td>
          <td>{numeral(noLocation.tap_count).format("0,0")}</td>
          <td>{renderAlertCount(noLocation.alert_count)}</td>
          <td><span className="text-muted">n/a</span></td>
          <td><span className="text-muted">n/a</span></td>
        </tr> : null}
      </tbody>
    </table>
    </>
  )

}