import {Link} from "react-router-dom";
import React, {useEffect, useState} from "react";
import AuthenticationManagementService from "../../../../../../services/AuthenticationManagementService";
import LoadingSpinner from "../../../../../misc/LoadingSpinner";
import Paginator from "../../../../../misc/Paginator";

import numeral from "numeral";
import ApiRoutes from "../../../../../../util/ApiRoutes";

const authenticationMgmtService = new AuthenticationManagementService();

function LocationsTable(props) {

  const organizationId = props.organizationId;
  const tenantId = props.tenantId;

  const perPage = 20;
  const [page, setPage] = useState(1);

  const [locations, setLocations] = useState(null);

  useEffect(() => {
    setLocations(null);
    authenticationMgmtService.findAllTenantLocations(organizationId, tenantId, setLocations, perPage, (page-1)*perPage);
  }, [page]);

  if (!locations) {
    return <LoadingSpinner />
  }

  if (locations.locations.length === 0) {
    return (
        <div className="alert alert-info mb-2">
          This tenant does not have any locations.
        </div>
    )
  }

  return (
      <React.Fragment>
        <table className="table table-sm table-hover table-striped">
          <thead>
          <tr>
            <th>Name</th>
            <th>Floors</th>
            <th>Placed Taps</th>
          </tr>
          </thead>
          <tbody>
          {locations.locations.map(function (key, i) {
            return (
                <tr key={i}>
                  <td>
                    <Link to={ApiRoutes.SYSTEM.AUTHENTICATION.MANAGEMENT.TENANTS.LOCATIONS.DETAILS(organizationId, tenantId, locations.locations[i].id)}>
                      {locations.locations[i].name}
                    </Link>
                  </td>
                  <td>{numeral(locations.locations[i].floor_count).format("0,0")}</td>
                  <td>{numeral(locations.locations[i].tap_count).format("0,0")}</td>
                </tr>
            )
          })}
          </tbody>
        </table>

        <Paginator itemCount={locations.count} perPage={perPage} setPage={setPage} page={page} />
      </React.Fragment>
  )

}

export default LocationsTable;