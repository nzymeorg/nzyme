import React, {useEffect, useState} from "react";
import {Navigate, useParams} from "react-router-dom";
import ClusterService from "../../../../../services/ClusterService";
import LoadingSpinner from "../../../../misc/LoadingSpinner";
import NodeInactiveWarning from "./NodeInactiveWarning";
import Routes from "../../../../../util/ApiRoutes";
import moment from "moment/moment";
import numeral from "numeral";
import ProcessArguments from "./ProcessArguments";
import CpuLoadIndicator from "./CpuLoadIndicator";
import MemoryUseIndicator from "./MemoryUseIndicator";
import TapReportStatisticsChart from "./TapReportStatisticsChart";
import NodeClockWarning from "./NodeClockWarning";
import {toast} from "react-toastify";
import NodeDeletedWarning from "./NodeDeletedWarning";
import NodeTimers from "./NodeTimers";
import NodeGauges from "./NodeGauges";
import DatabasePoolCard from "./DatabasePoolCard";
import usePageTitle from "../../../../../util/UsePageTitle";

const clusterService = new ClusterService()

function fetchData (uuid, setNode) {
  clusterService.findNode(uuid, setNode)
}

function NodeDetailsPage() {

  usePageTitle("Node Details");

  const { uuid } = useParams()

  const [node, setNode] = useState(null)
  const [justDeleted, setJustDeleted] = useState(false)

  useEffect(() => {
    fetchData(uuid, setNode)
    const id = setInterval(() => fetchData(uuid, setNode), 5000)
    return () => clearInterval(id)
  }, [uuid, setNode])

  const deleteNode = function () {
    if (confirm("Really delete node?")) {
      clusterService.deleteNode(uuid, function () {
        setJustDeleted(true)
        toast.success('Node deleted.')
      })
    }
  }

  if (justDeleted) {
    return <Navigate to={Routes.SYSTEM.CLUSTER.INDEX} />
  }

  if (!node) {
    return <LoadingSpinner />
  }

  return (
      <div>
        <div className="row">
          <div className="col-10">
            <nav aria-label="breadcrumb">
              <ol className="breadcrumb">
                <li className="breadcrumb-item"><a href={Routes.SYSTEM.CLUSTER.INDEX}>Nodes</a></li>
                <li className="breadcrumb-item active" aria-current="page">{node.name}</li>
              </ol>
            </nav>
          </div>

          <div className="col-2">
            <a className="btn btn-primary float-end" href={Routes.SYSTEM.CLUSTER.INDEX}>Back</a>
          </div>

          <div className="col-md-12">
            <h1>
              Node &quot;{node.name}&quot;{' '}
              {node.is_ephemeral ? <i className="fa-regular fa-clock" title="Ephemeral Node" /> : null}
            </h1>
          </div>
        </div>

        <NodeDeletedWarning node={node} />
        <NodeInactiveWarning node={node} />
        <NodeClockWarning node={node} />

        <div className="row mt-3">
          <div className="col-6">
            <div className="card">
              <div className="card-body">
                <h3>Node Information</h3>
                <dl className="mb-0">
                  <dt>Last Seen</dt>
                  <dd>
                    <span title={moment(node.last_seen).format()}>
                      {moment(node.last_seen).fromNow()}{' '}
                      <span className="text-muted">
                        ({moment(node.last_seen).format()})
                      </span>
                    </span>
                  </dd>
                  <dt>Start Time</dt>
                  <dd>
                    <span title={moment(node.process_start_time).format()}>
                      {moment(node.process_start_time).fromNow()}{' '}
                      <span className="text-muted">
                        ({moment(node.process_start_time).format()})
                        (Cycle {node.cycle})
                      </span>
                    </span>
                  </dd>
                  <dt>nzyme Version</dt>
                  <dd>{node.version}</dd>
                </dl>
              </div>
            </div>
          </div>

          <div className="col-6">
            <div className="card">
              <div className="card-body">
                <h3>Host Information</h3>
                <dl className="mb-0">
                  <dt>Operating System</dt>
                  <dd>{node.os_information}</dd>
                  <dt>Maximum CPU Threads</dt>
                  <dd>{node.cpu_thread_count}</dd>
                  <dt>Drift From nzyme Clock</dt>
                  <dd>{numeral(node.clock_drift_ms).format("0,0")} ms</dd>
                </dl>
              </div>
            </div>
          </div>
        </div>

        <div className="row mt-3">
          <div className="col-4">
            <div className="card">
              <div className="card-body">
                <h3>System CPU Load</h3>
                <CpuLoadIndicator load={node.cpu_system_load} />

                <div className="node-metrics-gauge-summary">
                  {numeral(node.cpu_system_load).format("0")}% system CPU load
                </div>
              </div>
            </div>
          </div>

          <div className="col-4">
            <div className="card">
              <div className="card-body">
                <h3>System Memory Use</h3>
                <MemoryUseIndicator total={node.memory_bytes_total} used={node.memory_bytes_used} />

                <div className="node-metrics-gauge-summary">
                  {numeral(node.memory_bytes_used).format("0.0b")} of {' '}
                  {numeral(node.memory_bytes_total).format("0.0b")} system memory used
                </div>
              </div>
            </div>
          </div>

          <div className="col-4">
            <div className="card">
              <div className="card-body">
                <h3>JVM Heap Memory Use</h3>
                <MemoryUseIndicator total={node.heap_bytes_total} used={node.heap_bytes_used} />

                <div className="node-metrics-gauge-summary">
                  {numeral(node.heap_bytes_used).format("0.0b")} of {' '}
                  {numeral(node.heap_bytes_total).format("0.0b")} JVM heap memory used
                </div>
              </div>
            </div>
          </div>
        </div>

        <div className="row mt-3">
          <div className="col-12">
            <div className="card">
              <div className="card-body">
                <h3>Total Data Received from Taps</h3>

                <TapReportStatisticsChart nodeId={node.uuid} />
              </div>
            </div>
          </div>
        </div>

        <div className="row mt-3">
          <div className="col-12">
            <DatabasePoolCard node={node} />
          </div>
        </div>

        <div className="row mt-3">
          <div className="col-12">
            <div className="card">
              <div className="card-body">
                <h3>Process Arguments</h3>

                <ProcessArguments arguments={node.process_arguments} />
              </div>
            </div>
          </div>
        </div>


        <div className="row mt-3">
          <div className="col-6">
            <div className="row">
              <div className="col-12">
                <div className="card">
                  <div className="card-body">
                    <h3>HTTP Configuration</h3>

                    <p>
                      The listen address is what the built-in HTTP server for REST API and web interface is listening on. The
                      external address is what other nodes will use to connect to this node.
                    </p>

                    <dl className="mb-0">
                      <dt>External Address</dt>
                      <dd>{node.http_external_uri}</dd>
                      <dt>Listen Address</dt>
                      <dd>{node.http_listen_uri}</dd>
                      <dt>TLS Certificate</dt>
                      <dd>
                        Expires {node.tls_cert_expiration_date}{' '}
                        ({node.tls_cert_fingerprint.substring(0, 16).match(/.{1,2}/g).join(' ').toUpperCase()})
                      </dd>
                    </dl>
                  </div>
                </div>
              </div>
            </div>

            <div className="row mt-3">
              <div className="col-12">
                <div className="card">
                  <div className="card-body">
                    <h3>Metrics</h3>

                    <h4>Timers</h4>
                    <NodeTimers timers={node.metrics_timers} />

                    <h4>Gauges</h4>
                    <NodeGauges gauges={node.metrics_gauges} />
                  </div>
                </div>
              </div>
            </div>
          </div>

          <div className="col-6">
            <div className="row">
              <div className="col-12">
                <div className="card">
                  <div className="card-body">
                    <h3>Node Actions</h3>

                    <p>
                      <strong>You should delete this node if you no longer plan to use it.</strong> Note that it will re-appear
                      if you don&apos;t shut it down. All metrics and related information will remain until it is retention cleaned.
                    </p>

                    <button className="btn btn-danger" onClick={deleteNode} disabled={node.deleted}>Delete Node</button>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
  )

}

export default NodeDetailsPage;
