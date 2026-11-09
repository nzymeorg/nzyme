import React from "react";
import numeral from "numeral";
import moment from "moment";

function ratio(value, suffix = "%") {
  if (value === null || value === undefined || value < 0) {
    return <span className="text-muted">n/a</span>
  }

  return numeral(value).format("0.0") + suffix;
}

function hitRatioClass(value) {
  if (value < 0) return "";
  if (value < 70) return "text-danger";
  if (value < 90) return "text-warning";
  return "text-success";
}

function duration(seconds) {
  if (seconds < 60) return seconds + " s";
  return moment.duration(seconds, "seconds").humanize();
}

export default function DatabaseServerCard({server, collectedAt, nodeName}) {

  const window = server.window_minutes;
  const collecting = window < 5;

  const windowed = (content, className = "") => collecting
    ? <dd className="text-muted">Collecting.</dd>
    : <dd className={className}>{content}</dd>;

  return (
    <React.Fragment>
      <p className="text-muted">
        Snapshot taken {moment(collectedAt).fromNow()} by node {nodeName}. Rates and ratios cover the
        last {window} {window === 1 ? "minute" : "minutes"}.
      </p>

      <dl className="mb-0">
        <dt>PostgreSQL</dt>
        <dd>
          {server.version}, started <span title={moment(server.server_started_at).format()}>{moment(server.server_started_at).fromNow()}</span>
        </dd>

        <dt>Size</dt>
        <dd>
          {numeral(server.database_size_bytes).format("0.0b")} total,{' '}
          {numeral(server.heap_bytes).format("0.0b")} table data,{' '}
          {numeral(server.index_bytes).format("0.0b")} indexes
        </dd>

        <dt>Connections</dt>
        <dd>{numeral(server.connections).format("0,0")} of {numeral(server.max_connections).format("0,0")}</dd>

        <dt>Cache hit ratio <span className="text-muted">(shared_buffers)</span></dt>
        {server.warming_up ?
          <dd className="text-muted">
            Warming up. PostgreSQL restarted {moment(server.server_started_at).fromNow()}.
          </dd>
          : windowed(
            <span>
              {ratio(server.cache_hit_ratio_recent)} of block reads were served from memory.
            </span>,
            hitRatioClass(server.cache_hit_ratio_recent))}

        <dt>Temporary files <span className="text-muted">(work_mem)</span></dt>
        {windowed(
          <span>
            {numeral(server.temp_bytes_per_minute).format("0.0b")} per minute in{' '}
            {numeral(server.temp_files_per_minute).format("0.0")} files.
          </span>,
          server.temp_bytes_per_minute >= 100 * 1024 * 1024 ? "text-warning" : "")}

        <dt>Forced checkpoints <span className="text-muted">(max_wal_size)</span></dt>
        {windowed(
          server.checkpoints_recent === 0 ? "No checkpoints in the last " + window + " minutes" :
            ratio(server.checkpoints_requested_ratio_recent) + " of " + server.checkpoints_recent + " checkpoints were forced by write-ahead log volume",
          server.checkpoints_recent >= 3 && server.checkpoints_requested_ratio_recent >= 50 ? "text-warning" : "")}

        <dt>Disk wait <span className="text-muted">(track_io_timing)</span></dt>
        {!server.io_timing_enabled ?
          <dd className="text-muted">Not measured. Requires track_io_timing.</dd>
          : (server.warming_up ?
            <dd className="text-muted">Warming up.</dd>
            : windowed(
              <span>
                {numeral(server.io_read_wait_ms_per_minute / 1000).format("0.0")} s reading,{' '}
                {numeral(server.io_write_wait_ms_per_minute / 1000).format("0.0")} s writing per minute, summed over
                all connections.
              </span>,
              server.io_read_wait_ms_per_minute + server.io_write_wait_ms_per_minute >= 30000 ? "text-warning" : ""))}

        <dt>Updates staying on the same page <span className="text-muted">(HOT)</span></dt>
        {windowed(<span>{ratio(server.hot_update_ratio_recent)} of recent row updates did not have to touch an index.</span>)}

        <dt>Deadlocks</dt>
        {windowed(<span>{numeral(server.deadlocks_per_minute).format("0.00")} per minute</span>)}

        <dt>Transaction ID age</dt>
        <dd className={server.xid_age >= 1000000000 ? "text-danger" : ""}>
          {numeral(server.xid_age).format("0,0")} of 2,147,483,647.
        </dd>

        <dt>Oldest open transaction</dt>
        <dd className={server.oldest_transaction_seconds >= 3600 ? "text-warning" : ""}>
          {server.oldest_transaction_seconds === 0 ? "None" : duration(server.oldest_transaction_seconds)}
        </dd>
      </dl>
    </React.Fragment>
  )

}
