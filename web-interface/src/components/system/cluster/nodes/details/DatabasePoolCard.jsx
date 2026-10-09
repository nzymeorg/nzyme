import React, {useState} from "react";
import numeral from "numeral";
import DatabasePoolChart from "./DatabasePoolChart";

const CHARTS = {
  database_pool_active_connections: "Active Connections",
  database_pool_pending_connections: "Threads Waiting for a Connection",
  database_pool_connection_timeouts: "Connection Timeouts (Total)"
}

function gaugeValue(gauges, name) {
  return gauges && gauges[name] ? gauges[name].value : 0;
}

function DatabasePoolCard(props) {

  const node = props.node;
  const gauges = node.metrics_gauges;

  const [chart, setChart] = useState("database_pool_active_connections")

  const active = gaugeValue(gauges, "database_pool_active_connections");
  const idle = gaugeValue(gauges, "database_pool_idle_connections");
  const pending = gaugeValue(gauges, "database_pool_pending_connections");
  const total = gaugeValue(gauges, "database_pool_total_connections");
  const max = gaugeValue(gauges, "database_pool_max_connections");

  return (
      <div className="card">
        <div className="card-body">
          <h3>Database Connection Pool</h3>

          <p>
            Pooled PostgreSQL connections of this node. Threads waiting for a connection and connection timeouts
            indicate that the pool is exhausted. Consider increasing the <code>database_pool_size</code> performance
            configuration parameter if this happens regularly.
          </p>

          <dl className="mb-3">
            <dt>Open Connections</dt>
            <dd>
              {numeral(total).format("0,0")} of {numeral(max).format("0,0")} maximum{' '}
              <span className="text-muted">({numeral(idle).format("0,0")} idle)</span>
            </dd>
            <dt>Peak Active Connections <span className="text-muted">(last minute)</span></dt>
            <dd>{numeral(active).format("0,0")}</dd>
            <dt>Peak Threads Waiting for a Connection <span className="text-muted">(last minute)</span></dt>
            <dd className={pending > 0 ? "text-warning" : ""}>{numeral(pending).format("0,0")}</dd>
          </dl>

          <div className="mb-2">
            <select className="form-select form-select-sm w-auto"
                    value={chart}
                    onChange={(e) => setChart(e.target.value)}>
              {Object.keys(CHARTS).map((key) =>
                  <option key={key} value={key}>{CHARTS[key]}</option>
              )}
            </select>
          </div>

          <DatabasePoolChart nodeId={node.uuid} metricName={chart} />
        </div>
      </div>
  )

}

export default DatabasePoolCard;
