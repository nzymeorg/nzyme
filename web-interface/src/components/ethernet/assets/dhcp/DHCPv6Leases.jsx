import React from "react";
import numeral from "numeral";

const lifetime = (seconds) => {
  // All bits set means the lifetime never ends.
  if (seconds >= 4294967295) {
    return "Infinite";
  }

  return numeral(seconds).format("0,0") + " s";
}

export default function DHCPv6Leases({addresses, prefixes}) {

  const leases = [
    ...(addresses || []).map((a) => ({type: "Address", value: a.address, lease: a})),
    ...(prefixes || []).map((p) => ({type: "Delegated Prefix", value: p.prefix + "/" + p.prefix_length, lease: p}))
  ];

  if (leases.length === 0) {
    return <div className="alert alert-info mb-0">The server assigned no addresses or prefixes in this transaction.</div>
  }

  return (
    <table className="table table-sm table-hover table-striped mb-0">
      <thead>
      <tr>
        <th>Type</th>
        <th>Address or Prefix</th>
        <th>Preferred Lifetime</th>
        <th>Valid Lifetime</th>
      </tr>
      </thead>
      <tbody>
      {leases.map((l, i) => {
        return (
          <tr key={i}>
            <td>{l.type}</td>
            <td><span className="machine-data">{l.value}</span></td>
            <td>{lifetime(l.lease.preferred_lifetime)}</td>
            <td>{lifetime(l.lease.valid_lifetime)}</td>
          </tr>
        )
      })}
      </tbody>
    </table>
  )

}
