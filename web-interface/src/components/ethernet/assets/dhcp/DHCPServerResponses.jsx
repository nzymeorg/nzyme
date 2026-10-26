import React from "react";
import moment from "moment";
import numeral from "numeral";
import EthernetMacAddress from "../../../shared/context/macs/EthernetMacAddress";
import {DhcpOfferedValue} from "../../spoofing/DhcpOfferedParameters";

export default function DHCPServerResponses({responses, selectedServerAddress}) {

  if (!responses || responses.length === 0) {
    return <div className="alert alert-info mb-0">No server answered this transaction.</div>
  }

  const selected = (r) => {
    return selectedServerAddress && (selectedServerAddress === r.server_address
      || selectedServerAddress === r.server_identifier);
  }

  return (
    <table className="table table-sm table-hover table-striped mb-0">
      <thead>
      <tr>
        <th>Message</th>
        <th>Server</th>
        <th>Server Address</th>
        <th>Offered Address</th>
        <th>Gateway</th>
        <th>DNS Servers</th>
        <th>Domain</th>
        <th>Count</th>
        <th>Last Seen</th>
      </tr>
      </thead>
      <tbody>
      {responses.map((r, i) => {
        return (
          <tr key={i}>
            <td>
              {r.message_type}
              {selected(r) ? <span className="badge bg-success ms-1" title="The client chose this server">Selected</span> : null}
            </td>
            <td>
              {r.server_mac_data ? <EthernetMacAddress addressWithContext={r.server_mac_data} withAssetLink withAssetName />
                : r.server_mac ? <span className="machine-data">{r.server_mac}</span>
                  : <span className="text-muted">n/a</span>}
            </td>
            <td>
              <span className="machine-data">{r.server_address}</span>
              {r.server_identifier && r.server_identifier !== r.server_address ?
                <span className="text-muted" title="Server identifier (DHCP option 54)">
                  {' '}(ID <span className="machine-data">{r.server_identifier}</span>)
                </span> : null}
            </td>
            <td>{r.offered_address ? <span className="machine-data">{r.offered_address}</span> : <span className="text-muted">n/a</span>}</td>
            <td><DhcpOfferedValue parameters={r} field="routers" /></td>
            <td><DhcpOfferedValue parameters={r} field="dns_servers" /></td>
            <td><DhcpOfferedValue parameters={r} field="domain_name" /></td>
            <td>{numeral(r.count).format("0,0")}</td>
            <td title={moment(r.last_seen).format()}>{moment(r.last_seen).fromNow()}</td>
          </tr>
        )
      })}
      </tbody>
    </table>
  )

}
