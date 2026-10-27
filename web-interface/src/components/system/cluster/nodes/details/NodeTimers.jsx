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
          <TimerRow title="Beacon Detection: Run per Tenant" timer={timers.beacon_detection_run} />
          <TimerRow title="Beacon Detection: Candidate Discovery" timer={timers.beacon_candidate_discovery} />
          <TimerRow title="Recon Detection: Run per Tenant" timer={timers.recon_detection_run} />
          <TimerRow title="Recon Detection: Candidate Discovery" timer={timers.recon_candidate_discovery} />
          <TimerRow title="ARP Spoofing Detection: Run per Tenant" timer={timers.spoofing_arp_detection_run} />
          <TimerRow title="DHCP Spoofing Detection: Run per Tenant" timer={timers.spoofing_dhcp_detection_run} />
          <TimerRow title="Name Resolution Poisoning Detection: Run per Tenant" timer={timers.spoofing_nameres_detection_run} />
          <TimerRow title="DNS Spoofing Detection: Run per Tenant" timer={timers.spoofing_dns_detection_run} />
        </tbody>
      </table>
  )

}

export default NodeTimers;