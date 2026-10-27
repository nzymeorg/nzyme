import React from "react";
import numeral from "numeral";
import moment from "moment";
import LoadingSpinner from "../../misc/LoadingSpinner";
import Paginator from "../../misc/Paginator";
import EthernetMacAddress from "../../shared/context/macs/EthernetMacAddress";
import NameResolutionProtocols from "./NameResolutionProtocol";

const addressList = (addresses) => {
  if (!addresses || addresses.length === 0) {
    return <span className="text-muted">Unknown</span>
  }

  return addresses.map((a) => <div key={a} className="text-nowrap"><span className="machine-data">{a}</span></div>);
}

export default function NameResolutionRespondersTable({responders, page, setPage, perPage}) {

  if (!responders) {
    return <LoadingSpinner />
  }

  if (responders.responders.length === 0) {
    return <div className="alert alert-info mb-0">
      No device answered name queries in the selected time range. Answers appear here once a tap records LLMNR,
      NBT-NS or mDNS traffic.
    </div>
  }

  return (
    <React.Fragment>
      <table className="table table-sm table-hover table-striped">
        <thead>
        <tr>
          <th>Responder</th>
          <th>Addresses</th>
          <th>Protocols</th>
          <th>Names</th>
          <th>Self-Pointing Names</th>
          <th>Responses</th>
          <th>Example Names</th>
          <th>First Seen</th>
          <th>Last Seen</th>
        </tr>
        </thead>
        <tbody>
        {responders.responders.map((r) => {
          return (
            <tr key={r.mac}>
              <td>
                {r.mac_data ?
                  <EthernetMacAddress addressWithContext={r.mac_data} withAssetLink withAssetName />
                  : <span className="machine-data">{r.mac}</span>}
              </td>
              <td>{addressList(r.addresses)}</td>
              <td><NameResolutionProtocols protocols={r.protocols} /></td>
              <td>{numeral(r.name_count).format("0,0")}</td>
              <td title="Names the device answered with one of its own addresses. A legitimate device has one or two, a poisoner has many.">
                {r.self_pointing_name_count > 0 ?
                  <span className={r.self_pointing_name_count >= 3 ? "text-danger" : ""}>
                    {numeral(r.self_pointing_name_count).format("0,0")}
                  </span>
                  : <span className="text-muted">0</span>}
              </td>
              <td title={numeral(r.unicast_count).format("0,0") + " sent to a single client"}>
                {numeral(r.response_count).format("0,0")}
              </td>
              <td>
                {r.names_sample.map((n, i) => <span key={n}>{i > 0 ? ", " : ""}<span className="machine-data">{n}</span></span>)}
                {r.name_count > r.names_sample.length ? <span className="text-muted">, &hellip;</span> : null}
              </td>
              <td title={moment(r.first_seen).format()}>{moment(r.first_seen).fromNow()}</td>
              <td title={moment(r.last_seen).format()}>{moment(r.last_seen).fromNow()}</td>
            </tr>
          )
        })}
        </tbody>
      </table>

      <Paginator itemCount={responders.total} perPage={perPage} setPage={setPage} page={page} />
    </React.Fragment>
  )

}
