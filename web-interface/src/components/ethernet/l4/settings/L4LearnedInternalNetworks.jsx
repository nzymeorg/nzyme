import React from "react";
import LoadingSpinner from "../../../misc/LoadingSpinner";
import numeral from "numeral";

export default function L4LearnedInternalNetworks({config}) {

  if (!config) {
    return <LoadingSpinner />
  }

  if (config.learn_internal_networks && config.learn_internal_networks.value === false) {
    return <div className="alert alert-info mb-0">Learning internal networks is turned off.</div>
  }

  const networks = config.learned_internal_networks || [];
  const limit = (
    <p className="text-muted mb-2">
      Up to {numeral(config.learned_internal_networks_limit).format("0,0")} networks of /48 or longer are learned.
    </p>
  );

  if (networks.length === 0) {
    return (
      <React.Fragment>
        {limit}
        <div className="alert alert-info mb-0">No internal networks were learned yet.</div>
      </React.Fragment>
    )
  }

  return (
    <React.Fragment>
      {limit}

      <p className="mb-2"><strong>Total:</strong> {numeral(networks.length).format("0,0")}</p>

      <div style={{maxHeight: 400, overflowY: "auto"}}>
        <table className="table table-sm table-hover table-striped mb-0">
          <thead>
          <tr>
            <th>Network</th>
          </tr>
          </thead>
          <tbody>
          {networks.map((n) => (
            <tr key={n}>
              <td><span className="machine-data">{n}</span></td>
            </tr>
          ))}
          </tbody>
        </table>
      </div>
    </React.Fragment>
  )

}
