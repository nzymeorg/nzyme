import React, {useEffect, useState} from "react";
import usePageTitle from "../../util/UsePageTitle";
import LoadingSpinner from "../misc/LoadingSpinner";
import {Link, useParams} from "react-router-dom";
import useSelectedTenant from "../system/tenantselector/useSelectedTenant";
import LocationsService from "../../services/LocationsService";
import ApiRoutes from "../../util/ApiRoutes";
import LocationWeatherCondition from "./shared/LocationWeatherCondition";
import CardTitleWithControls from "../shared/CardTitleWithControls";
import LocationTemperature from "./shared/LocationTemperature";
import LocationWind from "./shared/LocationWind";
import LocationVisibility from "./shared/LocationVisibility";
import LatLonMap from "../shared/LatLonMap";
import EnvironmentalAlertsList from "./EnvironmentalAlertsList";
import LatitudeLongitude from "../shared/LatitudeLongitude";
import moment from "moment";
import momentTimezone from "moment-timezone";
import Clock from "./Clock";
import WithPermission from "../misc/WithPermission";
import AlertsTableRow from "../alerts/AlertsTableRow";
import numeral from "numeral";
import * as L from "leaflet";

const locationsService = new LocationsService();

export default function LocationDetailsPage() {

  const {uuid} = useParams();

  const [organizationId, tenantId] = useSelectedTenant();

  const [location, setLocation] = useState(null);

  const [now, setNow] = useState(new Date());

  const locationIcon = L.icon({
    iconUrl: window.appConfig.assetsUri + 'static/leaflet/icon-location.png',
    iconSize: [40, 52],
    iconAnchor: [20, 52],
    tooltipAnchor: [0, -52]
  });

  usePageTitle(location ? `Location: ${location.name}` : "Location Details");

  useEffect(() => {
    const id = setInterval(() => setNow(new Date()), 1000);
    return () => clearInterval(id);
  }, []);

  useEffect(() => {
    locationsService.findOne(uuid, organizationId, tenantId, setLocation);
  }, [uuid, organizationId, tenantId]);

  if (!location) {
    return <LoadingSpinner />;
  }

  return (
    <>
      <div className="row">
        <div className="col-md-9">
          <nav aria-label="breadcrumb">
            <ol className="breadcrumb">
              <li className="breadcrumb-item"><Link to={ApiRoutes.LOCATIONS.INDEX}>Locations</Link></li>
              <li className="breadcrumb-item active" aria-current="page">{location.name}</li>
            </ol>
          </nav>
        </div>
        <div className="col-md-3">
              <span className="float-end">
                <Link className="btn btn-secondary" to={ApiRoutes.LOCATIONS.INDEX}>Back</Link>
              </span>
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-md-12">
          <h1>Location &quot;{location.name}&quot;</h1>
        </div>
      </div>

      { location.description ?
        <div className="row mt-3">
          <div className="col-md-12">
            <div className="card">
              <div className="card-body">
                <CardTitleWithControls title="Description" slim={true}/>
                {location.description}
              </div>
            </div>
          </div>
        </div> : null }

      <div className="row mt-3">
        <div className="col-md-12">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title="Coordinates" slim={true} />

              { location.latitude && location.longitude ?
                <><LatLonMap editMode={false}
                           containerHeight={400}
                           defaultZoomLevel={10}
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
                </> : <span className="text-muted">No coordinates defined for this location.</span>}
            </div>
          </div>
        </div>
      </div>

      { location.timezone ?
        <div className="row mt-3">
          <Clock title="Local Time" timezone={location.timezone} referenceTimezone={moment.tz.guess()} now={now} />
          <Clock title="Your Time" timezone={moment.tz.guess()} referenceTimezone={moment.tz.guess()} now={now} />
        </div> : null }

      <div className="row mt-3">
        <div className="col-md-4">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title="Current Weather Observation"
                                     helpLink="https://go.nzyme.org/environmental-monitoring"
                                     slim={true}/>

              <dl className="mb-0 mt-2">
                <dt>Condition</dt>
                <dd><LocationWeatherCondition environment={location.environment} /></dd>
                <dt>Temperature</dt>
                <dd><LocationTemperature environment={location.environment} /></dd>
                <dt>Wind</dt>
                <dd><LocationWind environment={location.environment} /></dd>
                <dt>Visibility</dt>
                <dd><LocationVisibility environment={location.environment} /></dd>
              </dl>
            </div>
          </div>
        </div>
        <div className="col-md-8">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title="Environmental Alerts"
                                     helpLink="https://go.nzyme.org/environmental-monitoring"
                                     slim={true}/>

              <div style={{maxHeight: 195, overflowY: "auto"}}>
                <EnvironmentalAlertsList environment={location.environment} />
              </div>
            </div>
          </div>
        </div>
      </div>

      { location.environment && location.environment.metar ?
        <div className="row mt-3">
          <div className="col-md-12">
            <div className="card">
              <div className="card-body">
                <CardTitleWithControls title="METAR"
                                       helpLink="https://go.nzyme.org/environmental-monitoring"
                                       slim={true} />

                <div className="metar mb-0">{location.environment.metar}</div>
              </div>
            </div>
          </div>
        </div> : null }

      <div className="row mt-3">
        <div className="col-md-12">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title="Taps" slim={true} />

              {(location.taps && location.taps.length > 0) ?
                <>
                  <p className="text-muted">The following taps are present at this location:</p>

                  <table className="table table-sm table-hover table-striped">
                    <thead>
                    <tr>
                      <th>Tap</th>
                      <th>Online</th>
                    </tr>
                    </thead>
                    <tbody>
                    {location.taps.map((tap, i) => {
                      return (
                        <tr key={i}>
                          <td>{tap.name}</td>
                          <td>
                            {tap.is_online ? <span className="text-success"><i className="fa fa-circle"></i> Online</span>
                              : <span className="text-danger"><i className="fa fa-circle"></i> Offline</span>}
                          </td>
                        </tr>
                      )
                    })}
                    </tbody>
                  </table>
                </>
                : <div className="alert alert-info mb-0">No taps present at this location.</div> }
            </div>
          </div>
        </div>
      </div>

      <WithPermission permission="alerts_view">
        <div className="row mt-3">
          <div className="col-md-12">
            <div className="card">
              <div className="card-body">
                <CardTitleWithControls title="Detection Alerts at This Location" slim={true} />

                <p className="text-muted">Last 15 active alerts. Note that not all alerts can be limited to a single location. Always check
                the alerts overview page for all alerts.</p>

                { (location.alerts && location.alerts.length > 0) ?
                <table className="table table-sm table-hover table-striped">
                  <thead>
                  <tr>
                    <th>&nbsp;</th>
                    <th>Details</th>
                    <th>Type</th>
                    <th>Subsystem</th>
                    <th>First seen</th>
                    <th>Last seen</th>
                  </tr>
                  </thead>
                  <tbody>
                  {location.alerts.map(function(alert, i){
                    return <AlertsTableRow key={"alert-" + i}
                                           hideControls={true}
                                           alert={alert} />
                  })}
                  </tbody>
                </table> : <div className="alert alert-info mb-0">No active alerts at this location.</div> }
              </div>
            </div>
          </div>
        </div>
      </WithPermission>

      <div className="row mt-3">
        <div className="col-md-12">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title="Floors" slim={true} />

              {(location.floors && location.floors.length > 0) ?
                <>
                  <p className="text-muted">The following floors are configured for this location:</p>

                  <table className="table table-sm table-hover table-striped">
                    <thead>
                    <tr>
                      <th>Number</th>
                      <th>Name</th>
                      <th>Floor Plan Uploaded</th>
                      <th>Taps Placed on Floor Plan</th>
                    </tr>
                    </thead>
                    <tbody>
                    {location.floors.map((floor, i) => {
                      return (
                        <tr key={i}>
                          <td>{floor.number}</td>
                          <td>{floor.name}</td>
                          <td>
                            {floor.has_floor_plan ? <span><i className="fa-solid fa-circle-check text-success"></i> Yes</span>
                              : <span><i className="fa-solid fa-triangle-exclamation text-warning"></i> No</span>}
                          </td>
                          <td>{numeral(floor.tap_count).format()}</td>
                        </tr>
                      )
                    })}
                    </tbody>
                  </table>
                </>
                : <div className="alert alert-info mb-0">No floors configured for this location.</div> }
            </div>
          </div>
        </div>
      </div>

    </>

  )

}