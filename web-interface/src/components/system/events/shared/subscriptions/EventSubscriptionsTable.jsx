import {Link} from "react-router-dom";
import React from "react";
import ApiRoutes from "../../../../../util/ApiRoutes";

function EventSubscriptionsTable(props) {

  const organizationId = props.organizationId;
  const subscriptions = props.subscriptions;
  const onUnsubscribeClick = props.onUnsubscribeClick;

  if (!subscriptions || subscriptions.length === 0) {
    return (
        <div className="alert alert-info mb-0">
          There are no actions subscribed to this event type.
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
            <th>&nbsp;</th>
          </tr>
          </thead>
          <tbody>
          {subscriptions.sort((a, b) => a.action_type_human_readable.localeCompare(b.action_type_human_readable)).map((sub, i) => {
            return (
                <tr key={"actionsub-" + i}>
                  <td>
                    <Link to={organizationId
                        ? ApiRoutes.SYSTEM.AUTHENTICATION.MANAGEMENT.ORGANIZATIONS.EVENTS.ACTIONS.DETAILS(organizationId, sub.action_id)
                        : ApiRoutes.SYSTEM.EVENTS.ACTIONS.DETAILS(sub.action_id)}>
                      {sub.action_name}
                    </Link>
                  </td>
                  <td title={sub.action_type}>{sub.action_type_human_readable}</td>
                  <td>
                    <a href="#" onClick={() => onUnsubscribeClick(sub.subscription_id)}>Unsubscribe</a>
                  </td>
                </tr>
            )
          })}
          </tbody>
        </table>
      </React.Fragment>
  )

}

export default EventSubscriptionsTable;