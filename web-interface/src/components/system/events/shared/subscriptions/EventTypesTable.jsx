import {Link} from "react-router-dom";
import React from "react";
import Paginator from "../../../../misc/Paginator";
import LoadingSpinner from "../../../../misc/LoadingSpinner";
import ApiRoutes from "../../../../../util/ApiRoutes";

function EventTypesTable(props) {

  const organizationId = props.organizationId;
  const eventTypes = props.eventTypes;
  const perPage = props.perPage;
  const setPage = props.setPage;
  const page = props.page;

  if (!eventTypes) {
    return <LoadingSpinner />
  }

  return (
      <React.Fragment>
        <table className="table table-sm table-hover table-striped">
          <thead>
          <tr>
            <th>Category</th>
            <th>Name</th>
            <th>Subscriptions</th>
            <th>&nbsp;</th>
          </tr>
          </thead>
          <tbody>
          {eventTypes.types.sort((a, b) => a.category_id.localeCompare(b.category_id)).map((type, i) => {
            return (
                <tr key={"eventtype-" + i}>
                  <td title={type.category_id}>{type.category_name}</td>
                  <td title={type.id}>{type.name}</td>
                  <td>{type.subscriptions.length}</td>
                  <td>
                    <Link to={organizationId ? ApiRoutes.SYSTEM.AUTHENTICATION.MANAGEMENT.ORGANIZATIONS.EVENTS.SUBSCRIPTIONS.DETAILS(organizationId, type.id.toLowerCase()) : ApiRoutes.SYSTEM.EVENTS.SUBSCRIPTIONS.DETAILS(type.id.toLowerCase())}>Manage</Link>
                  </td>
                </tr>
            )
          })}
          </tbody>
        </table>

        <Paginator itemCount={eventTypes.count} perPage={perPage} setPage={setPage} page={page} />
      </React.Fragment>
  )

}

export default EventTypesTable;