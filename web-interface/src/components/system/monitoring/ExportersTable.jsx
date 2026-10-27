import {Link} from "react-router-dom";
import React from 'react'
import LoadingSpinner from '../../misc/LoadingSpinner'
import ExporterStatus from './ExporterStatus'
import ApiRoutes from '../../../util/ApiRoutes'

function ExportersTable (props) {
  if (!props.summary || !props.summary.exporters) {
    return <LoadingSpinner />
  }

  const exporters = props.summary.exporters

  return (
        <table className="table table-sm table-hover table-striped">
            <thead>
            <tr>
                <th>Exporter Type</th>
                <th>Status</th>
                <th>&nbsp;</th>
            </tr>
            </thead>
            <tbody>
            <tr>
                <td>Prometheus</td>
                <td><ExporterStatus status={exporters.prometheus} /></td>
                <td>
                    <Link to={ApiRoutes.SYSTEM.MONITORING.PROMETHEUS.INDEX}>Configure</Link>
                </td>
            </tr>
            </tbody>
        </table>
  )
}

export default ExportersTable
