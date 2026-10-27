import {Link} from "react-router-dom";
import React, {useEffect, useState} from "react";
import LoadingSpinner from "../../../../../../misc/LoadingSpinner";
import Paginator from "../../../../../../misc/Paginator";
import AuthenticationManagementService from "../../../../../../../services/AuthenticationManagementService";

import numeral from "numeral";
import ApiRoutes from "../../../../../../../util/ApiRoutes";

const authenticationMgmtService = new AuthenticationManagementService();

function FloorsTable(props) {

  const organizationId = props.organizationId;
  const tenantId = props.tenantId;
  const locationId = props.locationId;

  const perPage = 20;
  const [page, setPage] = useState(1);

  const [floors, setFloors] = useState(null);

  useEffect(() => {
    setFloors(null);
    authenticationMgmtService.findAllFloorsOfTenantLocation(organizationId, tenantId, locationId, setFloors, perPage, (page-1)*perPage);
  }, [page]);

  if (!floors) {
    return <LoadingSpinner />
  }

  if (floors.floors.length === 0) {
    return (
        <div className="alert alert-info mb-2">
          This location does not have any floors.
        </div>
    )
  }

  return (
      <React.Fragment>
        <p className="mb-2">Total floors: {floors.count}</p>

        <table className="table table-sm table-hover table-striped">
          <thead>
          <tr>
            <th style={{width: 65}}>Number</th>
            <th>Name</th>
            <th>Plan Uploaded</th>
            <th>Placed Taps</th>
          </tr>
          </thead>
          <tbody>
          {floors.floors.map(function (key, i) {
            return (
                <tr key={i}>
                  <td>{floors.floors[i].number}</td>
                  <td>
                    <Link to={ApiRoutes.SYSTEM.AUTHENTICATION.MANAGEMENT.TENANTS.LOCATIONS.FLOORS.DETAILS(organizationId, tenantId, locationId, floors.floors[i].id)}>
                      {floors.floors[i].name}
                    </Link>
                  </td>
                  <td>
                    {floors.floors[i].has_floor_plan ? <span><i className="fa-solid fa-circle-check text-success"></i> Yes</span>
                      : <span><i className="fa-solid fa-triangle-exclamation text-warning"></i> No</span>
                          }</td>
                  <td>{numeral(floors.floors[i].tap_count).format("0,0")}</td>
                </tr>
            )
          })}
          </tbody>
        </table>

        <Paginator itemCount={floors.count} perPage={perPage} setPage={setPage} page={page} />
      </React.Fragment>
  )

}

export default FloorsTable;