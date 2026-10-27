import React, {useContext, useEffect, useState} from "react";
import {TapContext, UserContext} from "../../App";
import {Link, useParams} from "react-router-dom";
import UavService from "../../services/UavService";
import LoadingSpinner from "../misc/LoadingSpinner";
import ApiRoutes from "../../util/ApiRoutes";
import CardTitleWithControls from "../shared/CardTitleWithControls";
import {Presets} from "../shared/timerange/TimeRange";
import UavDetectionSource from "./util/UavDetectionSource";
import UavType from "./util/UavType";
import SignalStrength from "../shared/SignalStrength";
import {disableTapSelector, enableTapSelector} from "../misc/TapSelector";
import moment from "moment/moment";
import UavOperationalStatus from "./util/UavOperationalStatus";
import UavMap from "./UavMap";
import LatitudeLongitude from "../shared/LatitudeLongitude";
import UavActiveIndicator from "./util/UavActiveIndicator";
import UavOperatorLocationType from "./util/UavOperatorLocationType";
import UavOperatorAltitude from "./util/UavOperatorAltitude";
import UavAltitude from "./util/UavAltitude";
import UavSpeed from "./util/UavSpeed";
import UavVerticalSpeed from "./util/UavVerticalSpeed";
import DDTimestamp from "../misc/DDTimestamp";
import UavInactiveWarning from "./util/UavInactiveWarning";
import UavClassification from "./util/UavClassification";
import UavOperatorDistanceToUav from "./util/UavOperatorDistanceToUav";
import UavTimelineTable from "./UavTimelineTable";
import {userHasPermission} from "../../util/Tools";
import UavModelType from "./util/UavModelType";
import useSelectedTenant from "../system/tenantselector/useSelectedTenant";
import LongDistance from "../shared/LongDistance";
import usePageTitle from "../../util/UsePageTitle";

const uavService = new UavService();

export default function UavDetailsPage() {

  const [organizationId, tenantId] = useSelectedTenant();

  const tapContext = useContext(TapContext);
  const selectedTaps = tapContext.taps;

  const {identifierParam} = useParams();

  const user = useContext(UserContext);

  const [uav, setUav] = useState(null);
  const [plottedTrack, setPlottedTrack] = useState(null);
  const [plottedTrackLoading, setPlottedTrackLoading] = useState(null);

  const [revision, setRevision] = useState(new Date());
  const [mapRevision, setMapRevision] = useState(new Date());

  const [timeline, setTimeline] = useState(null);
  const [timelineTimeRange, setTimelineTimeRange] = useState(Presets.ALL_TIME);
  const [timelinePage, setTimelinePage] = useState(1);
  const perPageTimeline = 5;

  usePageTitle(uav ? `UAV: ${uav.summary.designation}` : "UAV Details");

  useEffect(() => {
    enableTapSelector(tapContext);

    return () => {
      disableTapSelector(tapContext);
    }
  }, [tapContext]);

  useEffect(() => {
    setUav(null);
    if (organizationId && tenantId) {
      uavService.findOne(setUav, organizationId, tenantId, identifierParam, selectedTaps);
    }
  }, [selectedTaps, revision, organizationId, tenantId, identifierParam]);

  useEffect(() => {
    setTimeline(null);
    if (organizationId && tenantId) {
      uavService.findTimeline(
        setTimeline,
        organizationId,
        tenantId,
        timelineTimeRange,
        identifierParam,
        selectedTaps,
        perPageTimeline,
        (timelinePage - 1) * perPageTimeline
      );
    }
  }, [revision, mapRevision, organizationId, tenantId, timelinePage, timelineTimeRange, identifierParam]);

  const onPlotTrack = (e, timelineId) => {
    e.preventDefault();

    setPlottedTrack(null);
    setPlottedTrackLoading(timelineId)
    uavService.findTimelineVectors(
        setPlottedTrack, identifierParam, timelineId, organizationId, tenantId, () => {
          setPlottedTrackLoading(null);
        }
    );
  }

  const lastKnownOperatorPosition = () => {
    if (!uav.summary.operator_latitude
        || !uav.summary.operator_longitude
        || !uav.summary.latest_operator_location_timestamp) {
      return null;
    }

    return {
      lat: uav.summary.operator_latitude,
      lon: uav.summary.operator_longitude,
      timestamp: uav.summary.latest_operator_location_timestamp
    }
  }

  if (!uav) {
    return <LoadingSpinner />
  }

  return (
    <React.Fragment>
      <div className="row">
        <div className="col-12">
          <nav aria-label="breadcrumb">
            <ol className="breadcrumb">
              <li className="breadcrumb-item"><Link to={ApiRoutes.UAV.INDEX}>UAVs</Link></li>
              <li className="breadcrumb-item">{uav.summary.identifier.substring(0, 7)}</li>
              <li className="breadcrumb-item active" aria-current="page">Details</li>
            </ol>
          </nav>
        </div>
      </div>

      <div className="row mt-2">
        <div className="col-12">
          <h1>
            <UavActiveIndicator active={uav.summary.is_active} />{' '}
            UAV &quot;{uav.summary.identifier.substring(0, 7)}&quot;
          </h1>
        </div>
      </div>

      <UavInactiveWarning show={!uav.summary.is_active} />

      <div className="row mt-3">
        <div className="col-4">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title="UAV Information"
                                     fixedAppliedTimeRange={Presets.ALL_TIME}/>

              <dl className="mb-0">
                <dt>Designation</dt>
                <dd>{uav.summary.designation}</dd>
                <dt>Classification</dt>
                <dd>
                  <UavClassification classification={uav.summary.classification}
                                     uavIdentifier={uav.summary.identifier}
                                     enableEditMode={userHasPermission(user, "uav_monitoring_manage")}
                                     organizationId={organizationId}
                                     tenantId={tenantId}
                                     onChange={() => setRevision(new Date()) }/>
                </dd>
                <dt>Operational Status</dt>
                <dd><UavOperationalStatus status={uav.summary.operational_status} /></dd>
                <dt>UAV Type</dt>
                <dd><UavType type={uav.summary.uav_type} /></dd>
                <dt>Signal Strength</dt>
                <dd><SignalStrength strength={uav.summary.rssi_average} selectedTapCount={selectedTaps.length}/></dd>
                <dt>Category / Model</dt>
                <dd>
                  <UavModelType type={uav.summary.uav_model_type} />{' '}
                  {uav.summary.uav_model_model ? <span>({uav.summary.uav_model_model})</span> : null}
                </dd>
                <dt>Name</dt>
                <dd>{uav.summary.uav_model_name ? uav.summary.uav_model_name : <span className="text-muted">n/a</span>}</dd>
              </dl>
            </div>
          </div>
        </div>

        <div className="col-4">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title="IDs and License"
                                     fixedAppliedTimeRange={Presets.ALL_TIME}/>

              <dl className="mb-0">
                <dt>UAV Serial Number</dt>
                <dd>{uav.summary.id_serial ? uav.summary.id_serial : <span className="text-muted">n/a</span>}</dd>
                <dt>UAV Registration</dt>
                <dd>{uav.summary.id_registration ? uav.summary.id_registration : <span className="text-muted">n/a</span>}</dd>
                <dt>UAV Traffic Management ID</dt>
                <dd>{uav.summary.id_utm ? uav.summary.id_utm : <span className="text-muted">n/a</span>}</dd>
                <dt>UAV Session ID</dt>
                <dd>{uav.summary.id_session ? uav.summary.id_session : <span className="text-muted">n/a</span>}</dd>
                <dt>Operator License ID</dt>
                <dd>{uav.summary.operator_id ? uav.summary.operator_id : <span className="text-muted">n/a</span>}</dd>
              </dl>
            </div>
          </div>
        </div>

        <div className="col-4">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title="Metadata"
                                     fixedAppliedTimeRange={Presets.ALL_TIME}/>

              <dl className="mb-0">
                <dt>Detection Source</dt>
                <dd><UavDetectionSource source={uav.summary.detection_source} /></dd>
                <dt>First Seen</dt>
                <dd>
                  {moment(uav.summary.first_seen).format()}{' '}
                  <span className="text-muted">
                    (Note: This value is affected by data retention times.)
                  </span>
                </dd>
                <dt>Last Seen</dt>
                <dd>{moment(uav.summary.last_seen).format()}</dd>
              </dl>
            </div>
          </div>
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-12">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title="Map View"
                                     fixedAppliedTimeRange={Presets.ALL_TIME}/>

              <UavMap uav={uav}
                      containerHeight={500}
                      plottedTrack={plottedTrack}
                      lastKnownUavPosition={{
                        lat: uav.summary.latitude,
                        lon: uav.summary.longitude,
                        timestamp: uav.summary.latest_vector_timestamp
                      }}
                      lastKnownOperatorPosition={lastKnownOperatorPosition()}
                      onRefresh={() => setMapRevision(new Date()) } />
            </div>
          </div>
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-12">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title="Timeline &amp; Tracks"
                                     timeRange={timelineTimeRange}
                                     setTimeRange={setTimelineTimeRange} />

              <UavTimelineTable timeline={timeline}
                                page={timelinePage}
                                onPlotTrack={onPlotTrack}
                                plottedTrackLoading={plottedTrackLoading}
                                setPage={setTimelinePage}
                                perPage={perPageTimeline} />
            </div>
          </div>
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-4">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title="UAV Vector"
                                     fixedAppliedTimeRange={Presets.ALL_TIME}/>

              <dl>
                <dt>Last Known UAV Position</dt>
                <dd>
                  <LatitudeLongitude latitude={uav.summary.latitude}
                                     longitude={uav.summary.longitude}
                                     accuracy={uav.summary.accuracy_horizontal} />
                </dd>
                <dt>Timestamp</dt>
                <DDTimestamp timestamp={uav.summary.latest_vector_timestamp} />
                <dt>Altitude</dt>
                <dd><UavAltitude uav={uav.summary} /></dd>
                <dt>Vertical Speed</dt>
                <dd>
                  <UavVerticalSpeed verticalSpeed={uav.summary.vertical_speed} accuracy={uav.summary.accuracy_speed} />
                </dd>
                <dt>Speed</dt>
                <dd><UavSpeed speed={uav.summary.speed} accuracy={uav.summary.accuracy_speed} /></dd>
              </dl>
            </div>
          </div>
        </div>

        <div className="col-4">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title="Operator Position"
                                     fixedAppliedTimeRange={Presets.ALL_TIME}/>

              <dl>
                <dt>Last Known Operator Position</dt>
                <dd>
                  <LatitudeLongitude latitude={uav.summary.operator_latitude}
                                     longitude={uav.summary.operator_longitude}
                                     skipAccuracy={true} />
                </dd>
                <dt>Timestamp</dt>
                <DDTimestamp timestamp={uav.summary.latest_operator_location_timestamp} />
                <dt>Operator Location Type</dt>
                <dd><UavOperatorLocationType type={uav.summary.operator_location_type} /></dd>
                <dt>Operator Altitude</dt>
                <dd><UavOperatorAltitude altitude={uav.summary.operator_altitude} /></dd>
                <dt>Operator Distance To UAV</dt>
                <dd><UavOperatorDistanceToUav distance={uav.summary.operator_distance_to_uav} /></dd>
              </dl>
            </div>
          </div>
        </div>

        <div className="col-4">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title="Distance From Taps"
                                     fixedAppliedTimeRange={Presets.ALL_TIME}/>

              <p className="help-text">
                Distance of last vector information from taps.
              </p>

              <table className="table table-sm table-hover table-striped">
                <thead>
                <tr>
                  <th>Tap</th>
                  <th>Distance</th>
                </tr>
                </thead>
                <tbody>
                {Object.keys(uav.summary.tap_distances).map((uuid, i) => {
                  return (
                      <tr key={i}>
                        <td>{uav.summary.tap_distances[uuid].tap.name}</td>
                        <td><LongDistance feet={uav.summary.tap_distances[uuid].distance_feet} /></td>
                      </tr>
                  )
                })}
                </tbody>
              </table>
            </div>
          </div>
        </div>
      </div>
    </React.Fragment>
  )

}