import React, { useEffect, useState } from 'react'
import {Link, useParams} from 'react-router-dom'
import ApiRoutes from '../../../../../util/ApiRoutes'
import TapInactiveWarning from '../TapInactiveWarning'
import TapsService from '../../../../../services/TapsService'
import LoadingSpinner from '../../../../misc/LoadingSpinner'
import TapMetricsChartProxy from './TapMetricsChartProxy'
import usePageTitle from "../../../../../util/UsePageTitle";

const tapsService = new TapsService()

function fetchData (uuid, setTap) {
  tapsService.findTap(uuid, setTap)
}

function TapMetricsDetailsPage () {

  usePageTitle("Tap Metrics");

  const { uuid, metricType, metricName } = useParams()

  const [tap, setTap] = useState(null)

  useEffect(() => {
    fetchData(uuid, setTap)
    const id = setInterval(() => fetchData(uuid, setTap), 5000)
    return () => clearInterval(id)
  }, [uuid, setTap])

  if (!tap) {
    return <LoadingSpinner />
  }

  return (
        <div>
            <div className="row">
                <div className="col-md-10">
                    <nav aria-label="breadcrumb">
                        <ol className="breadcrumb">
                            <li className="breadcrumb-item"><Link to={ApiRoutes.SYSTEM.TAPS.INDEX}>Taps</Link></li>
                            <li className="breadcrumb-item" aria-current="page">
                                <Link to={ApiRoutes.SYSTEM.TAPS.DETAILS(tap.uuid)}>{tap.name}</Link>
                            </li>
                            <li className="breadcrumb-item" aria-current="page">Metrics</li>
                            <li className="breadcrumb-item active" aria-current="page">{metricName}</li>
                        </ol>
                    </nav>
                </div>
                <div className="col-md-2">
                    <Link className="btn btn-primary float-end" to={ApiRoutes.SYSTEM.TAPS.DETAILS(tap.uuid)}>Back</Link>
                </div>
            </div>

            <div className="row">
                <h1>Tap &quot;{tap.name}&quot; / Metric &quot;{metricName}&quot; <small>Type: {metricType}/average</small></h1>
            </div>

            <TapInactiveWarning tap={tap} />

            <div className="row mt-3">
                <div className="col-md-12">
                    <div className="card">
                        <div className="card-body">
                            <h3>Chart</h3>

                            <TapMetricsChartProxy type={metricType} name={metricName} tapUuid={tap.uuid} />
                        </div>
                    </div>
                </div>
            </div>
        </div>
  )
}

export default TapMetricsDetailsPage
