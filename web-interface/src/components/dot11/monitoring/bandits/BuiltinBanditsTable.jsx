import {Link} from "react-router-dom";
import React, {useEffect, useState} from "react";
import LoadingSpinner from "../../../misc/LoadingSpinner";
import Dot11Service from "../../../../services/Dot11Service";
import ApiRoutes from "../../../../util/ApiRoutes";

const dot11Service = new Dot11Service();

function BuiltinBanditsTable() {

  const [bandits, setBandits] = useState(null);

  useEffect(() => {
    dot11Service.findBuiltinBandits(setBandits);
  }, [])

  if (!bandits) {
    return <LoadingSpinner />
  }

  return (
      <React.Fragment>
        <table className="table table-sm table-hover table-striped mb-0">
          <thead>
          <tr>
            <th>Name</th>
            <th>Description</th>
          </tr>
          </thead>
          <tbody>
          {bandits.map(function(bandit, i) {
            return (
                <tr key={"banditdef-" + i}>
                  <td>
                    <Link to={ApiRoutes.DOT11.MONITORING.BANDITS.BUILTIN_DETAILS(bandit.id)}>
                      {bandit.name}
                    </Link>
                  </td>
                  <td>{bandit.description}</td>
                </tr>
            )
          })}
          </tbody>
        </table>
      </React.Fragment>
  )

}

export default BuiltinBanditsTable;