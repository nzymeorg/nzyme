import {Link} from "react-router-dom";
import React from "react";
import LoadingSpinner from "../../../misc/LoadingSpinner";
import ApiRoutes from "../../../../util/ApiRoutes";
import Paginator from "../../../misc/Paginator";

function ActionsTable(props) {

  const organizationId = props.organizationId;
  const actions = props.actions;
  const perPage = props.perPage;
  const page = props.page;
  const setPage = props.setPage;

  if (!actions) {
    return <LoadingSpinner />
  }

  if (actions.actions.length === 0) {
    return (
        <div className="alert alert-info">
          No event actions configured.
        </div>
    )
  }

  return (
      <React.Fragment>
        <table className="table table-sm table-hover table-striped">
          <thead>
          <tr>
            <th>Name</th>
            <th>Type</th>
          </tr>
          </thead>
          <tbody>
          {actions.actions.sort((a, b) => a.action_type_human_readable.localeCompare(b.action_type_human_readable)).map((action, i) => {
            return (
                <tr key={"eventaction-" + i}>
                  <td>
                    <Link to={organizationId
                        ? ApiRoutes.SYSTEM.AUTHENTICATION.MANAGEMENT.ORGANIZATIONS.EVENTS.ACTIONS.DETAILS(organizationId, action.id)
                        : ApiRoutes.SYSTEM.EVENTS.ACTIONS.DETAILS(action.id) }>
                      {action.name}
                    </Link>
                  </td>
                  <td title={action.action_type}>
                    {action.action_type_human_readable}
                  </td>
                </tr>
            )
          })}
          </tbody>
        </table>

        <Paginator itemCount={actions.count} perPage={perPage} setPage={setPage} page={page} />
      </React.Fragment>
  )

}

export default ActionsTable;