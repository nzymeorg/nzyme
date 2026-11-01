import React from "react";
import Events from "./Events";
import EventSubscriptions from "./subscriptions/EventSubscriptions";
import Actions from "./actions/Actions";
import usePageTitle from "../../../util/UsePageTitle";

function EventsPage() {

  usePageTitle("Events & Actions");

  return (
      <React.Fragment>
        <div className="row">
          <div className="col-12">
            <h1>Events &amp; Actions</h1>
          </div>
        </div>

        <div className="row">
          <div className="col-12">
            <div className="row mt-3">
              <div className="col-xl-8 col-xxl-6">
                <div className="card">
                  <div className="card-body">
                    <h3>Super Administrator Event Subscriptions</h3>

                    <p>
                      This super administrator view of event subscriptions does not include organization-specific
                      event types, which are instead managed on the respective organization management pages.
                    </p>

                    <EventSubscriptions />
                  </div>
                </div>
              </div>
            </div>

            <div className="row mt-3">
              <div className="col-xl-8 col-xxl-6">
                <div className="card">
                  <div className="card-body">
                    <h3>Super Administrator Actions</h3>

                    <p>
                      Events, such as system notifications or detection alerts have the ability to trigger the following
                      actions. This super administrator view of event actions does not include organization-specific
                      actions, which are instead managed on the respective organization management pages.
                    </p>

                    <Actions />
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>

        <div className="row mt-3">
          <div className="col-xl-8 col-xxl-6">
            <div className="card">
              <div className="card-body">
                <h3>All Recorded Events</h3>

                <p>
                  The table below displays all recorded events that can trigger actions within Nzyme. Please note
                  that detection alerts have additionally been organized in a separate section in the navigation panel
                  for easier and streamlined management.
                </p>

                <Events />
              </div>
            </div>
          </div>
        </div>
      </React.Fragment>
  )

}

export default EventsPage;