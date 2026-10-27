import {Link} from "react-router-dom";
import React from 'react'
import FormattedGauge from './FormattedGauge'
import ApiRoutes from '../../../../../util/ApiRoutes'

function TapMetricsGauges (props) {
  if (!props.gauges || Object.keys(props.gauges).length === 0) {
    return <div className="alert alert-warning">
      No recent data.
    </div>
  }

  return (
        <table className="table table-sm table-hover table-striped">
            <thead>
            <tr>
                <th>Gauge</th>
                <th>Value</th>
                <th>&nbsp;</th>
            </tr>
            </thead>
            <tbody>
            {Object.keys(props.gauges).sort((a, b) => a.localeCompare(b)).map(function (key, i) {
              return (
                    <tr key={'metric-gauge-' + i}>
                        <td>{props.gauges[key].metric_name}</td>
                        <td><FormattedGauge name={props.gauges[key].metric_name} value={props.gauges[key].metric_value} /></td>
                        <td>
                            <Link to={ApiRoutes.SYSTEM.TAPS.METRICDETAILS(props.tap.uuid, 'gauge', props.gauges[key].metric_name)}>
                                Chart
                            </Link>
                        </td>
                    </tr>
              )
            })}
            </tbody>
        </table>
  )
}

export default TapMetricsGauges
