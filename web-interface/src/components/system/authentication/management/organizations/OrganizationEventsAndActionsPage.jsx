import React, {useEffect, useState} from "react";
import AuthenticationManagementService from "../../../../../services/AuthenticationManagementService";
import {useParams} from "react-router-dom";
import LoadingSpinner from "../../../../misc/LoadingSpinner";
import OrganizationHeader from "./OrganizationHeader";
import SectionMenuBar from "../../../../shared/SectionMenuBar";
import {ORGANIZATION_MENU_ITEMS} from "./OrganizationMenuItems";
import ApiRoutes from "../../../../../util/ApiRoutes";
import OrganizationEventSubscriptions from "./events/subscriptions/OrganizationEventSubscriptions";
import OrganizationActions from "./events/actions/OrganizationActions";
import WildcardEventSubscriptions from "../../../events/shared/subscriptions/WildcardEventSubscriptions";
import OrganizationEvents from "./events/OrganizationEvents";
import CardTitleWithControls from "../../../../shared/CardTitleWithControls";
import usePageTitle from "../../../../../util/UsePageTitle";

const authenticationManagementService = new AuthenticationManagementService();

export default function OrganizationEventsAndActionsPage() {

  usePageTitle("Events & Actions of Organization");

  const { organizationId } = useParams();

  const [organization, setOrganization] = useState(null);

  useEffect(() => {
    authenticationManagementService.findOrganization(organizationId, setOrganization);
  }, [organizationId])

  if (!organization) {
    return <LoadingSpinner />
  }

  return (
      <React.Fragment>
        <OrganizationHeader organization={organization}/>

        <div className="row">
          <div className="col-md-12">
            <SectionMenuBar items={ORGANIZATION_MENU_ITEMS(organization.id)}
                            activeRoute={ApiRoutes.SYSTEM.AUTHENTICATION.MANAGEMENT.ORGANIZATIONS.EVENTS_PAGE(organizationId)}/>
          </div>
        </div>

        <div className="row mt-3">
          <div className="col-md-12">
            <h1>Events &amp; Actions of Organization &quot;{organization.name}&quot;</h1>
          </div>
        </div>

        <div className="row">
          <div className="col-xl-12 col-xxl-8">
            <div className="row mt-3">
              <div className="col-12">
                <div className="card">
                  <div className="card-body">
                    <CardTitleWithControls title="Organization Event Subscriptions" slim={true} />

                    <p>
                      This table shows all system events that can be triggered within the organization, together with
                      their respective subscribed actions.
                    </p>

                    <OrganizationEventSubscriptions organizationId={organizationId}/>
                  </div>
                </div>
              </div>
            </div>

            <div className="row mt-3">
              <div className="col-12">
                <div className="card">
                  <div className="card-body">
                    <CardTitleWithControls title="Organization Wildcard Event Subscriptions"
                                           slim={true}
                                           helpLink="https://go.nzyme.org/system-events-wildcard-subscriptions" />

                    <p>
                      Actions subscribed here are triggered by every system event of this organization.
                    </p>

                    <WildcardEventSubscriptions organizationId={organizationId} />
                  </div>
                </div>
              </div>
            </div>

            <div className="row mt-3">
              <div className="col-md-12">
                <div className="card">
                  <div className="card-body">
                    <CardTitleWithControls title="Organization Event Actions" slim={true} />

                    <p>
                      Events, such as system notifications or detection alerts, within this organization, have the
                      ability to trigger the following actions. It is important to note that tenants of this
                      organization must be assigned access to individual event actions.
                    </p>

                    <OrganizationActions organizationId={organization.id}/>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>

        <div className="row mt-3">
          <div className="col-xl-12 col-xxl-8">
            <div className="card">
              <div className="card-body">
                <CardTitleWithControls title="All Recorded Organization Events" slim={true} />

                <p>
                  The table below displays all recorded events that can trigger actions within this organization. Please
                  note that detection alerts have additionally been organized in a separate section in the navigation
                  panel
                  for easier and streamlined management.
                </p>

                <OrganizationEvents organizationId={organization.id}/>
              </div>
            </div>
          </div>
        </div>

      </React.Fragment>
  )

}