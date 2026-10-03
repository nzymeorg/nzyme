import React from "react";
import GaugeRow from "../../../../misc/metrics/GaugeRow";
import numeral from "numeral"

function NodeGauges(props) {

  const gauges = props.gauges;
  
  return (
      <table className="table table-sm table-hover table-striped">
        <thead>
        <tr>
          <th>Metric</th>
          <th>Value</th>
        </tr>
        </thead>
        <tbody>
          <GaugeRow title="GeoIP Cache Size" numberFormat="0,0" gauge={gauges.geoip_cache_size} />
          <GaugeRow title="MAC Address Context Cache Size" numberFormat="0,0" gauge={gauges.context_mac_cache_size} />
          <GaugeRow title="Network Context Cache Size" numberFormat="0,0" gauge={gauges.context_network_cache_size} />
          <tr>
            <td>
              Internal logs written in last minute (Warning/Error/Fatal)
            </td>
            <td>
              {numeral(gauges.log_counts_warn.value).format("0,0")}/
              {numeral(gauges.log_counts_error.value).format("0,0")}/
              {numeral(gauges.log_counts_fatal.value).format("0,0")}
            </td>
          </tr>

        </tbody>
      </table>
  )

}

export default NodeGauges;