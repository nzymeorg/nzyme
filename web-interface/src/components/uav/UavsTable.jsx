import {Link} from "react-router-dom";
import React, {useContext} from "react";
import {TapContext} from "../../App";
import LoadingSpinner from "../misc/LoadingSpinner";
import UavActiveIndicator from "./util/UavActiveIndicator";
import Paginator from "../misc/Paginator";
import UavType from "./util/UavType";
import UavDetectionSource from "./util/UavDetectionSource";
import SignalStrength from "../shared/SignalStrength";
import UavOperationalStatus from "./util/UavOperationalStatus";
import moment from "moment/moment";
import UavAltitude from "./util/UavAltitude";
import UavSpeed from "./util/UavSpeed";
import ApiRoutes from "../../util/ApiRoutes";
import Designation from "../shared/Designation";
import UavClassification from "./util/UavClassification";
import UavModelType from "./util/UavModelType";
import LongDistance from "../shared/LongDistance";

export default function UavsTable(props) {

  const uavs = props.uavs;
  const page = props.page;
  const perPage = props.perPage;
  const setPage = props.setPage;

  const tapContext = useContext(TapContext);
  const selectedTaps = tapContext.taps;

  if (!uavs) {
    return <LoadingSpinner />
  }

  if (uavs.count === 0) {
    return (
        <div className="alert alert-info mb-2">
          No UAVs recorded in selected time frame.
        </div>
    )
  }

  return (
      <React.Fragment>
        <table className="table table-sm table-hover table-striped">
          <thead>
          <tr>
            <th style={{width: 25}}></th>
            <th style={{width: 70}}>ID</th>
            <th>Designation</th>
            <th>Classification</th>
            <th>Detection Source</th>
            <th>Type</th>
            <th>Category / Model</th>
            <th>Name</th>
            <th>Status</th>
            <th>RSSI</th>
            <th>Distance</th>
            <th>Altitude</th>
            <th>Speed</th>
            <th>Last Seen</th>
          </tr>
          </thead>
          <tbody>
          {uavs.uavs.map((uav, i) => {
            return (
              <tr key={i}>
                <td><UavActiveIndicator active={uav.is_active} /></td>
                <td><Link to={ApiRoutes.UAV.DETAILS(uav.identifier)}>{uav.identifier.substring(0, 7)}</Link></td>
                <td><Designation designation={uav.designation} /></td>
                <td><UavClassification classification={uav.classification} /></td>
                <td><UavDetectionSource source={uav.detection_source} /></td>
                <td><UavType type={uav.uav_type} /></td>
                <td>
                  <UavModelType type={uav.uav_model_type} />{' '}
                  {uav.uav_model_model ? <span>({uav.uav_model_model})</span> : null}
                </td>
                <td>{uav.uav_model_name ? uav.uav_model_name : <span className="text-muted">n/a</span>}</td>
                <td><UavOperationalStatus status={uav.operational_status} /></td>
                <td><SignalStrength strength={uav.rssi_average} selectedTapCount={selectedTaps.length}/></td>
                <td><LongDistance feet={uav.closest_tap_distance} /></td>
                <td><UavAltitude uav={uav} /></td>
                <td><UavSpeed speed={uav.speed} accuracy={uav.accuracy_speed} /></td>
                <td title={uav.last_seen}>{moment(uav.last_seen).fromNow()}</td>
              </tr>
            );
          })}
          </tbody>
        </table>

        <Paginator itemCount={uavs.count} perPage={perPage} setPage={setPage} page={page} />
      </React.Fragment>
  )

}