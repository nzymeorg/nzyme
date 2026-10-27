import {Link} from "react-router-dom";
import React, {useEffect, useState} from "react";
import LoadingSpinner from "../../../../misc/LoadingSpinner";
import AuthenticationManagementService from "../../../../../services/AuthenticationManagementService";
import moment from "moment";
import ApiRoutes from "../../../../../util/ApiRoutes";
import Paginator from "../../../../misc/Paginator";

const authenticationMgmtService = new AuthenticationManagementService();

function TapPermissionsTable(props) {

  const organizationId = props.organizationId;
  const tenantId = props.tenantId;

  const perPage = 20;
  const [page, setPage] = useState(1);

  const [taps, setTaps] = useState(null);

  useEffect(() => {
    setTaps(null);
    authenticationMgmtService.findAllTapPermissions(organizationId, tenantId, setTaps, perPage, (page-1)*perPage);
  }, [page]);

  if (!taps) {
    return <LoadingSpinner />
  }

  if (taps.taps.length === 0) {
    return (
        <div className="alert alert-info mb-2">
          This tenant does not have any taps.
        </div>
    )
  }

  return (
      <React.Fragment>
        <table className="table table-sm table-hover table-striped">
          <thead>
          <tr>
            <th>Name</th>
            <th>Last Report</th>
          </tr>
          </thead>
          <tbody>
          {taps.taps.map(function (key, i) {
            return (
                <tr key={"tap-" + i}>
                  <td>
                    <Link to={ApiRoutes.SYSTEM.AUTHENTICATION.MANAGEMENT.TAPS.DETAILS(
                        organizationId, tenantId, taps.taps[i].uuid)}>
                      {taps.taps[i].name}
                    </Link>
                  </td>
                  <td>{taps.taps[i].last_report ? moment(taps.taps[i].last_report).format() : <span className="text-warning">None</span>}</td>
                </tr>
            )
          })}
          </tbody>
        </table>

        <Paginator itemCount={taps.count} perPage={perPage} setPage={setPage} page={page} />
      </React.Fragment>
  )

}

export default TapPermissionsTable;