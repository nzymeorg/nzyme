import {Link} from "react-router-dom";
import React from 'react'
import LoadingSpinner from '../../misc/LoadingSpinner'
import TapRow from './TapRow'
import ApiRoutes from "../../../util/ApiRoutes";

function TapsTable(props) {

  const taps = props.taps

  if (taps === undefined || taps === null) {
    return <LoadingSpinner />
  }

  if (taps.length === 0) {
    return <div className="alert alert-warning mb-0">
      No nzyme taps configured yet. They are managed in the{' '}
      <Link to={ApiRoutes.SYSTEM.AUTHENTICATION.MANAGEMENT.INDEX}>authentication section</Link> and can also be added
      directly from this page using the button above.
    </div>
  }

  return (
    <div className="row">
      <div className="col-md-12">
        <h3 style={{display: "inline-block"}}>All Taps</h3>

        <table className="table table-sm table-hover table-striped">
          <thead>
            <tr>
              <th>Name</th>
              <th>Location</th>
              <th>Throughput</th>
              <th>CPU Load</th>
              <th>Memory Used</th>
              <th>Clock</th>
              <th>Version</th>
              <th>Last Seen</th>
            </tr>
          </thead>
          <tbody>
            {Object.keys(taps.sort((a, b) => a.name.localeCompare(b.name))).map(function (key, i) {
              return <TapRow key={'tap-' + i} tap={taps[i]} />
            })}
          </tbody>
        </table>
      </div>
    </div>
  )
}

export default TapsTable
