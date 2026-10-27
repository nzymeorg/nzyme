import {Link} from "react-router-dom";
import React from "react";
import ApiRoutes from "../../../../../util/ApiRoutes";

function SystemSubscriptionsOfActionTable(props) {

  const subscriptions = props.subscriptions;
  const organizationId = props.organizationId;

  if (!subscriptions || subscriptions.length === 0) {
    return <div className="alert alert-info mt-0 mb-0">Action is not subscribed to any system events.</div>
  }

  return (
      <table className="table table-sm table-hover table-striped">
        <thead>
        <tr>
          <th>System Event Name</th>
          <th>Category</th>
        </tr>
        </thead>
        <tbody>
        {subscriptions.sort((a, b) => a.category_id.localeCompare(b.category_id)).map((type, i) => {
          return (
              <tr key={"eventtype-" + i}>
                <td title={type.id}>
                  <Link to={organizationId ?
                      ApiRoutes.SYSTEM.AUTHENTICATION.MANAGEMENT.ORGANIZATIONS.EVENTS.SUBSCRIPTIONS.DETAILS(organizationId, type.id)
                      : ApiRoutes.SYSTEM.EVENTS.SUBSCRIPTIONS.DETAILS(type.id)}>
                    {type.name}
                  </Link>
                </td>
                <td title={type.category_id}>{type.category_name}</td>
              </tr>
          )
        })}
        </tbody>
      </table>
  )

}

export default SystemSubscriptionsOfActionTable;