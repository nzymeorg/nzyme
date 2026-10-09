import React from "react";
import TimerRow from "../../../../misc/metrics/TimerRow";

function NodeTimers(props) {

  const timers = props.timers;

  return (
      <table className="table table-sm table-hover table-striped">
        <thead>
        <tr>
          <th>Metric</th>
          <th>Max</th>
          <th>Min</th>
          <th>Mean</th>
          <th>P99</th>
          <th>StdDev</th>
          <th>Calls</th>
        </tr>
        </thead>
        <tbody>
          <TimerRow title="Password Hashing" timer={timers.password_hashing} />
          <TimerRow title="Mac Address Context Lookup" timer={timers.context_mac_lookup} />
          <TimerRow title="Database Pool: Wait for Connection" timer={timers.database_pool_wait} />
          <TimerRow title="Database Pool: Connection Usage" timer={timers.database_pool_usage} />
          <TimerRow title="Report Processing: TCP" timer={timers.report_processing_tcp} />
          <TimerRow title="Report Processing: DNS" timer={timers.report_processing_dns} />
          <TimerRow title="Report Processing: SSH" timer={timers.report_processing_ssh} />
          <TimerRow title="Report Processing: SOCKS" timer={timers.report_processing_socks} />
          <TimerRow title="Report Processing: 802.11 / WiFi" timer={timers.report_processing_dot11} />
        </tbody>
      </table>
  )

}

export default NodeTimers;