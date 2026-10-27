import React, { useEffect, useState } from 'react'
import TapsService from '../../../../services/TapsService'
import {Link, useParams} from 'react-router-dom'
import LoadingSpinner from '../../../misc/LoadingSpinner'
import moment from 'moment'
import Routes from '../../../../util/ApiRoutes'
import numeral from 'numeral'
import Buses from './Buses'
import TapInactiveWarning from './TapInactiveWarning'
import CaptureConfiguration from '../capture/CaptureConfiguration'
import TapMetrics from './metrics/TapMetrics'
import TapMetricsChartProxy from './metrics/TapMetricsChartProxy'
import TapClockWarning from "./TapClockWarning";
import {byteAverageToMbit} from "../../../../util/Tools";
import TapDot11CoverageMap from "./TapDot11CoverageMap";
import CardTitleWithControls from "../../../shared/CardTitleWithControls";
import LatitudeLongitude from "../../../shared/LatitudeLongitude";
import LatLonMap from "../../../shared/LatLonMap";
import TapConfiguration from "./TapConfiguration";
import TapEngagementLogsTable from "./TapEngagementLogsTable";
import usePageTitle from "../../../../util/UsePageTitle";

const tapsService = new TapsService()

function fetchData (uuid, setTap, setTapMetrics) {
  tapsService.findTap(uuid, setTap)
  tapsService.findMetricsOfTap(uuid, setTapMetrics)
}

function TapDetailsPage () {

  usePageTitle("Tap Details");

  const { uuid } = useParams()

  const [tap, setTap] = useState(null)
  const [tapMetrics, setTapMetrics] = useState(null)

  useEffect(() => {
    fetchData(uuid, setTap, setTapMetrics)
    const id = setInterval(() => fetchData(uuid, setTap, setTapMetrics), 5000)
    return () => clearInterval(id)
  }, [uuid])

  if (!tap) {
    return <LoadingSpinner />
  }

  return (
    <div>
      <div className="row">

        <div className="col-md-8">
          <nav aria-label="breadcrumb">
            <ol className="breadcrumb">
              <li className="breadcrumb-item"><Link to={Routes.SYSTEM.TAPS.INDEX}>Taps</Link></li>
              <li className="breadcrumb-item active" aria-current="page">{tap.name}</li>
            </ol>
          </nav>
        </div>

        <div className="col-md-4">
          <span className="float-end">
            <Link className="btn btn-secondary" to={Routes.SYSTEM.TAPS.INDEX}>Back</Link>
            {' '}
            <Link className="btn btn-primary" to={Routes.SYSTEM.AUTHENTICATION.MANAGEMENT.TAPS.DETAILS(tap.organization_id, tap.tenant_id, tap.uuid)}>
              Tap Details &amp; Keys
            </Link>
          </span>
        </div>
      </div>

      <div className="row">
        <div className="col-md-12">
          <h1>Tap &quot;{tap.name}&quot;</h1>
        </div>
      </div>

      <TapInactiveWarning tap={tap} />
      <TapClockWarning tap={tap} />

      <div className="row mt-3">
        <div className="col-md-4">
          <div className="card">
            <div className="card-body">
              <h3>Throughput</h3>

              <dl className="mb-0">
                <dt>Throughput</dt>
                <dd>
                  {tap.active ? byteAverageToMbit(tap.processed_bytes.average) : "n/a" }{' '}
                  {tap.active ? "(" + numeral(tap.processed_bytes.average / 10).format('0 b') + "/sec)" : null}
                </dd>

                <dt>Total data processed since last restart</dt>
                <dd>{tap.active ? numeral(tap.processed_bytes.total).format('0.0 b') : "n/a"}</dd>
              </dl>
            </div>
          </div>
        </div>

        <div className="col-md-4">
          <div className="card">
            <div className="card-body">
              <h3>Metrics</h3>

              <dl className="mb-0">
                <dt>CPU Load</dt>
                <dd>{tap.active ? numeral(tap.cpu_load).format('0.0') + "%" : "n/a"}</dd>

                <dt>System-Wide Memory Usage</dt>
                <dd>
                  {tap.active ? numeral(tap.memory_used).format('0 b') + "/" + numeral(tap.memory_total).format('0 b') : "n/a"}{' '}
                  {tap.active ? "(" + numeral(tap.memory_used / tap.memory_total * 100).format('0.0') + "%)" : null}
                </dd>
              </dl>
            </div>
          </div>
        </div>

        <div className="col-md-4">
          <div className="card">
            <div className="card-body">
              <h3>Details</h3>

              <dl className="mb-0">
                <dt>Last Report</dt>
                <dd>
                  {tap.last_report ?
                      <span title={moment(tap.last_report).format()}> {moment(tap.last_report).fromNow()} (from {tap.remote_address})</span>
                      : "no report yet" }
                </dd>

                <dt>Created at</dt>
                <dd>
                  <span title={moment(tap.created_at).format()}>{moment(tap.created_at).fromNow()}</span>
                </dd>

                <dt>Version</dt>
                <dd>{tap.version ? tap.version : "unknown"}</dd>

                <dt>Raspberry Pi</dt>
                <dd>{tap.rpi ? <span><i className="fa-brands fa-raspberry-pi"></i> {tap.rpi}</span> : "No"}</dd>
              </dl>
            </div>
          </div>
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-md-12">
          <div className="card">
            <div className="card-body">
              <h3>Throughput</h3>

              <TapMetricsChartProxy type="gauge" name="system.captures.throughput_bit_sec" tapUuid={tap.uuid} />
            </div>
          </div>
        </div>
      </div>

      { tap.rpi ?
      <div className="row mt-3">
        <div className="col-md-12">
          <div className="card">
            <div className="card-body">
              <h3>CPU Temperature</h3>

              <TapMetricsChartProxy type="gauge" name="rpi.temperature" tapUuid={tap.uuid} />
            </div>
          </div>
        </div>
      </div>
      : null }

      <div className="row mt-3">
        <div className="col-md-12">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title="Engagement Interface Logs"
                                     slim={true}
                                     helpLink="https://go.nzyme.org/engagement-interfaces"/>

              <p className="help-text">Retention period is 30 days.</p>

              <TapEngagementLogsTable tapUuid={tap.uuid} />
            </div>
          </div>
        </div>
      </div>

      { tap.location_id || (tap.latitude && tap.longitude) ?
      <div className="row mt-3">
        <div className="col-md-12">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title="Location" slim={true}/>
              <dl className="mb-1 tap-location-details">
                <dt>Location</dt>
                <dd>
                  {tap.location_id && tap.location_name ? tap.location_name : <span className="text-muted">n/a</span>}
                </dd>
                <dt>Floor</dt>
                <dd>
                  {tap.location_id && tap.location_name && tap.floor_id && tap.floor_name ? tap.floor_name
                    : <span className="text-muted">n/a</span>}
                </dd>
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
      </div> : null }

      <div className="row mt-3">
        <div className="col-md-12">
          <div className="card">
            <div className="card-body">
              <h3>Capture Configuration</h3>

              <CaptureConfiguration tap={tap} />
            </div>
          </div>
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-md-12">
          <div className="card">
            <div className="card-body">
              <h3>Buses &amp; Channels</h3>

              <Buses tap={tap} />
            </div>
          </div>
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-md-12">
          <div className="card">
            <div className="card-body">
              <h3>802.11 / WiFi Channel Coverage</h3>

              <TapDot11CoverageMap tap={tap} />
            </div>
          </div>
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-md-12">
          <div className="card">
            <div className="card-body">
              <h3>Configuration</h3>

              <TapConfiguration configuration={tap.configuration} />
            </div>
          </div>
        </div>
      </div>

      <TapMetrics tap={tap} metrics={tapMetrics} />
    </div>
  )
}

export default TapDetailsPage
