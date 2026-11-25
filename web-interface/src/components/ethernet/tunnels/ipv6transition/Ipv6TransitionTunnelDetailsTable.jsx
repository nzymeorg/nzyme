import React from "react";

export default function Ipv6TransitionTunnelDetailsTable({details}) {

  if (!details || Object.keys(details).length === 0) {
    return <p className="text-muted mb-0">No details recorded.</p>
  }

  return (
    <table className="table table-sm table-hover table-striped mb-0">
      <thead>
      <tr>
        <th>Detail</th>
        <th>Value</th>
      </tr>
      </thead>
      <tbody>
      {Object.keys(details).sort().map((key) => (
        <tr key={key}>
          <td>{key}</td>
          <td>{details[key]}</td>
        </tr>
      ))}
      </tbody>
    </table>
  )

}
