import React, {useEffect, useState} from "react";
import SystemService from "../../../../services/SystemService";
import LoadingSpinner from "../../../misc/LoadingSpinner";
import SimpleLineChart from "../../../widgets/charts/SimpleLineChart";

const systemService = new SystemService();

const METRICS = {
  database_size_bytes: {title: "Database size", format: "0.0b", tickformat: ".3s"},
  database_index_bytes: {title: "Index size", format: "0.0b", tickformat: ".3s"},
  database_cache_hit_ratio: {title: "Cache hit ratio (%)", tickformat: ".1f"},
  database_temp_bytes_per_minute: {title: "Temporary file bytes per minute", tickformat: ".3s"},
  database_checkpoints_requested_ratio: {title: "Forced checkpoints (%)", tickformat: ".1f"},
  database_io_read_wait_ms_per_minute: {title: "Disk read wait per minute (ms, needs track_io_timing)", tickformat: ".3s"},
  database_io_write_wait_ms_per_minute: {title: "Disk write wait per minute (ms, needs track_io_timing)", tickformat: ".3s"},
  database_dead_tuple_ratio_max: {title: "Highest dead row share of any table (%)", tickformat: ".1f"},
  database_vacuum_lag_minutes_max: {title: "Longest time since vacuum of a table with dead rows (minutes)", tickformat: ".0f"},
  database_index_bloat_ratio_max: {title: "Highest estimated bloat of any index (%)", tickformat: ".1f"},
  database_index_bloat_bytes: {title: "Estimated wasted index space", tickformat: ".3s"},
  database_hot_update_ratio: {title: "Updates staying on the same page (%)", tickformat: ".1f"},
  database_connections: {title: "Connections", tickformat: ".0f"},
  database_xid_age: {title: "Transaction ID age", tickformat: ".3s"},
  database_oldest_transaction_seconds: {title: "Oldest open transaction (seconds)", tickformat: ".0f"},
  database_deadlocks_per_minute: {title: "Deadlocks per minute", tickformat: ".2f"}
};

function formatData(data) {
  const result = {};
  Object.keys(data).sort().forEach((key) => {
    result[key] = data[key].average;
  });
  return result;
}

export default function DatabaseTrendsCard() {

  const [metric, setMetric] = useState("database_cache_hit_ratio");
  const [histogram, setHistogram] = useState(null);
  const [empty, setEmpty] = useState(false);

  useEffect(() => {
    setHistogram(null);
    setEmpty(false);
    const load = () => systemService.getDatabaseMetricHistogram(metric, setHistogram);
    load();
    // The endpoint returns 404 until the first values were recorded.
    const emptyTimer = setTimeout(() => setHistogram((h) => { if (!h) setEmpty(true); return h; }), 5000);
    const id = setInterval(load, 60000);
    return () => { clearInterval(id); clearTimeout(emptyTimer); };
  }, [metric]);

  return (
    <React.Fragment>
      <p>
        The chart shows the last 24 hours in one minute buckets, averaged across all nodes.
      </p>

      <div className="mb-2">
        <select className="form-select form-select-sm w-auto" value={metric} onChange={(e) => setMetric(e.target.value)}>
          {Object.keys(METRICS).map((key) => <option key={key} value={key}>{METRICS[key].title}</option>)}
        </select>
      </div>

      {histogram ?
        <SimpleLineChart height={200}
                         data={formatData(histogram.values)}
                         customMarginLeft={85}
                         customMarginRight={25}
                         tickformat={METRICS[metric].tickformat} />
        : (empty ? <div className="alert alert-info mb-0">No values recorded for this metric yet.</div> : <LoadingSpinner />)}
    </React.Fragment>
  )

}
