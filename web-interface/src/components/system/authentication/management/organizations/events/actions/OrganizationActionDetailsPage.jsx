import React, {useEffect, useState} from "react";
import {Link, Navigate, useParams} from "react-router-dom";
import ApiRoutes from "../../../../../../../util/ApiRoutes";
import LoadingSpinner from "../../../../../../misc/LoadingSpinner";
import AuthenticationManagementService from "../../../../../../../services/AuthenticationManagementService";
import EventActionsService from "../../../../../../../services/EventActionsService";
import {toast} from "react-toastify";
import ActionDetailsProxy from "../../../../../events/shared/details/ActionDetailsProxy";
import ActionDetails from "../../../../../events/shared/ActionDetails";
import SystemSubscriptionsOfActionTable from "../../../../../events/shared/subscriptions/SystemSubscriptionsOfActionTable";
import DetectionSubscriptionsOfActionTable
  from "../../../../../events/shared/subscriptions/DetectionSubscriptionsOfActionTable";
import useSelectedTenant from "../../../../../tenantselector/useSelectedTenant";
import usePageTitle from "../../../../../../../util/UsePageTitle";

const authenticationMgmtService = new AuthenticationManagementService();
const eventActionsService = new EventActionsService();

function OrganizationActionDetailsPage() {

  usePageTitle("Organization Action Details");

  const [organizationId, ignored] = useSelectedTenant();
  const { actionId } = useParams();

  const [organization, setOrganization] = useState(null);
  const [action, setAction] = useState(null);

  const [deleted, setDeleted] = useState(false);

  useEffect(() => {
    authenticationMgmtService.findOrganization(organizationId, setOrganization);
    eventActionsService.findActionOfOrganization(organizationId, actionId, setAction)
  }, [organizationId, actionId])

  const onDelete = function() {
    if (!confirm("Really delete action?")) {
      return;
    }

    eventActionsService.deleteAction(action.id, function() {
      setDeleted(true);
      toast.success('Action deleted.');
    })
  }

  if (deleted) {
    return <Navigate to={ApiRoutes.SYSTEM.AUTHENTICATION.MANAGEMENT.ORGANIZATIONS.EVENTS_PAGE(organization.id)} />
  }

  if (!organization || !action) {
    return <LoadingSpinner />
  }

  return (
      <React.Fragment>
        <div className="row">
          <div className="col-md-9">
            <nav aria-label="breadcrumb">
              <ol className="breadcrumb">
                <li className="breadcrumb-item">
                  <Link to={ApiRoutes.SYSTEM.AUTHENTICATION.MANAGEMENT.INDEX}>Authentication &amp; Authorization</Link>
                </li>
                <li className="breadcrumb-item">Organizations</li>
                <li className="breadcrumb-item">
                  <Link to={ApiRoutes.SYSTEM.AUTHENTICATION.MANAGEMENT.ORGANIZATIONS.DETAILS(organization.id)}>
                    {organization.name}
                  </Link>
                </li>
                <li className="breadcrumb-item">
                  <Link to={ApiRoutes.SYSTEM.AUTHENTICATION.MANAGEMENT.ORGANIZATIONS.EVENTS_PAGE(organization.id)}>
                    Events &amp; Actions
                  </Link>
                </li>
                <li className="breadcrumb-item">Actions</li>
                <li className="breadcrumb-item active" aria-current="page">{action.name}</li>
              </ol>
            </nav>
          </div>

          <div className="col-md-3">
            <span className="float-end">
            <Link className="btn btn-secondary"
               to={ApiRoutes.SYSTEM.AUTHENTICATION.MANAGEMENT.ORGANIZATIONS.EVENTS_PAGE(organization.id)}>
              Back
            </Link>{' '}
            <Link className="btn btn-primary"
               to={ApiRoutes.SYSTEM.AUTHENTICATION.MANAGEMENT.ORGANIZATIONS.EVENTS.ACTIONS.EDIT(organization.id, action.id)}>
              Edit Action
            </Link>
            </span>
          </div>
        </div>

        <div className="row">
          <div className="col-md-12">
            <h1>Event Action &quot;{action.name}&quot;</h1>
          </div>
        </div>

        <div className="row mt-3">
          <div className="col-md-8">
            <div className="row">
              <div className="col-md-12">
                <div className="card">
                  <div className="card-body">
                    <h3>Description</h3>

                    <p className="mb-0">
                      {action.description}
                    </p>
                  </div>
                </div>
              </div>
            </div>

            <div className="row mt-3">
              <div className="col-md-12">
                <div className="card">
                  <div className="card-body">
                    <h3>Details</h3>

                    <ActionDetails action={action} />
                  </div>
                </div>
              </div>
            </div>

            <div className="row mt-3">
              <div className="col-md-12">
                <div className="card">
                  <div className="card-body">
                    <h3>Configuration</h3>

                    <ActionDetailsProxy action={action} />
                  </div>
                </div>
              </div>
            </div>

            <div className="row mt-3">
              <div className="col-md-12">
                <div className="card">
                  <div className="card-body">
                    <h3>Subscriptions</h3>

                    <p>This action is subscribed to the following events:</p>

                    <h4>System Events</h4>
                    <SystemSubscriptionsOfActionTable subscriptions={action.subscribed_to_system_events}
                                                      organizationId={organization.id} />

                    <h4 className="mt-4">Detection Events</h4>
                    <DetectionSubscriptionsOfActionTable subscriptions={action.subscribed_to_detection_events} />
                  </div>
                </div>
              </div>
            </div>
          </div>

          <div className="col-md-4">
            <div className="row">
              <div className="col-md-12">
                <div className="card">
                  <div className="card-body">
                    <h3>Delete Action</h3>

                    <p>
                      <strong>Note:</strong> You can only delete actions that are not currently subscribed to any events
                      like system notifications or detection alerts.
                    </p>

                    <button type="button"
                            className="btn btn-danger"
                            onClick={onDelete}
                            disabled={action.subscribed_to_system_events.length > 0 || action.subscribed_to_detection_events.length > 0}>
                      Delete Action
                    </button>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </React.Fragment>
  )

}

export default OrganizationActionDetailsPage;