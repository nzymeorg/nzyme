import {Link} from "react-router-dom";
import React, {useEffect, useState} from "react";
import LoadingSpinner from "../../misc/LoadingSpinner";
import DetectionAlertsService from "../../../services/DetectionAlertsService";
import Paginator from "../../misc/Paginator";
import Subsystem from "../../misc/Subsystem";
import ApiRoutes from "../../../util/ApiRoutes";
import useSelectedTenant from "../../system/tenantselector/useSelectedTenant";

const detectionAlertService = new DetectionAlertsService();

function AlertSubscriptionsTable() {

  const [organizationId, ignored] = useSelectedTenant();

  const [types, setTypes] = useState(null);

  const perPage = 25;
  const [page, setPage] = useState(1);

  useEffect(() => {
    setTypes(null);
    detectionAlertService.findAllAlertTypes(setTypes, organizationId, perPage, (page-1)*perPage)
  }, [organizationId, page]);

  if (!types) {
    return <LoadingSpinner />
  }

  return (
      <React.Fragment>
        <table className="table table-sm table-hover table-striped">
          <thead>
          <tr>
            <th>Detection Name</th>
            <th>Subsystem</th>
            <th>Subscriptions</th>
          </tr>
          </thead>
          <tbody>
          {types.types.map(function(type, i) {
            return (
                <tr key={"at-" + i}>
                  <td>
                    <Link to={ApiRoutes.ALERTS.SUBSCRIPTIONS.DETAILS(type.name.toLowerCase())}>
                      {type.title}
                    </Link>
                  </td>
                  <td><Subsystem subsystem={type.subsystem} /></td>
                  <td>{type.subscriptions.length}</td>
                </tr>
            )
          })}
          </tbody>
        </table>

        <Paginator itemCount={types.total} perPage={perPage} setPage={setPage} page={page} />
      </React.Fragment>
  )

}

export default AlertSubscriptionsTable;