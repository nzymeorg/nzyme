import {Link} from "react-router-dom";
import React, {useEffect, useState} from "react";
import LoadingSpinner from "../../../misc/LoadingSpinner";
import {truncate} from "../../../../util/Tools";
import ApiRoutes from "../../../../util/ApiRoutes";
import Paginator from "../../../misc/Paginator";
import Dot11Service from "../../../../services/Dot11Service";
import useSelectedTenant from "../../../system/tenantselector/useSelectedTenant";

const dot11Service = new Dot11Service();

function CustomBanditsTable() {

  const [organizationId, tenantId] = useSelectedTenant();

  const [bandits, setBandits] = useState(null);

  const perPage = 25;
  const [page, setPage] = useState(1);

  useEffect(() => {
    setBandits(null);
    dot11Service.findCustomBandits(organizationId, tenantId, perPage, (page-1)*perPage, setBandits)
  }, [page, organizationId, tenantId]);

  if (!bandits) {
    return <LoadingSpinner />
  }

  if (bandits.bandits.length === 0) {
    return <div className="alert alert-info mt-3">No custom bandits defined yet.</div>
  }

  return (
      <React.Fragment>
        <table className="table table-sm table-hover table-striped">
          <thead>
          <tr>
            <th>Name</th>
            <th>Description</th>
          </tr>
          </thead>
          <tbody>
          {bandits.bandits.map((bandit, i) => {
            return (
                <tr key={"bandit-" + i}>
                  <td><Link to={ApiRoutes.DOT11.MONITORING.BANDITS.CUSTOM_DETAILS(bandit.id)}>{bandit.name}</Link></td>
                  <td>{truncate(bandit.description, 200, true)}</td>
                </tr>
            )
          })}
          </tbody>
        </table>

        <Paginator itemCount={bandits.total} perPage={perPage} setPage={setPage} page={page} />
      </React.Fragment>
  )

}

export default CustomBanditsTable;