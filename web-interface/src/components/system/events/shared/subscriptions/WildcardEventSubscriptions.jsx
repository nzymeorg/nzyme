import React, {useEffect, useState} from "react";
import {toast} from "react-toastify";
import LoadingSpinner from "../../../../misc/LoadingSpinner";
import EventActionsService from "../../../../../services/EventActionsService";
import EventSubscriptionsTable from "./EventSubscriptionsTable";
import EventSubscriptionActionSelector from "./EventSubscriptionActionSelector";

const eventActionsService = new EventActionsService();

function WildcardEventSubscriptions(props) {

  const organizationId = props.organizationId;

  const [subscriptions, setSubscriptions] = useState(null);
  const [actions, setActions] = useState(null);

  const [revision, setRevision] = useState(0);
  const [subscriptionError, setSubscriptionError] = useState(null);

  useEffect(() => {
    setSubscriptions(null);
    setActions(null);

    eventActionsService.findAllSystemEventWildcardSubscriptions(organizationId, setSubscriptions);

    if (organizationId) {
      eventActionsService.findAllActionsOfOrganization(organizationId, setActions, 9999999, 0);
    } else {
      eventActionsService.findAllActions(setActions, 9999999, 0);
    }
  }, [organizationId, revision]);

  const onActionSelect = function(actionId) {
    eventActionsService.subscribeActionToAllSystemEvents(actionId, organizationId, function() {
      toast.success("Subscribed wildcard action.");
      setSubscriptionError(null);
      setRevision(revision+1);
    }, function(error) {
      setSubscriptionError(error.response.data.message);
    });
  }

  const onUnsubscribeClick = function(subscriptionId) {
    if (!confirm("Really unsubscribe wildcard action?")) {
      return;
    }

    eventActionsService.unsubscribeActionFromAllSystemEvents(subscriptionId, function() {
      toast.success("Unsubscribed wildcard action.");
      setRevision(revision+1);
    })
  }

  if (!subscriptions || !actions) {
    return <LoadingSpinner />
  }

  return (
      <React.Fragment>
        <EventSubscriptionsTable organizationId={organizationId}
                                 subscriptions={subscriptions}
                                 onUnsubscribeClick={onUnsubscribeClick} />

        <h4 className="mt-4 mb-0">Subscribe Action</h4>
        <EventSubscriptionActionSelector onSubmit={onActionSelect}
                                         actions={actions.actions}
                                         subscriptionError={subscriptionError} />
      </React.Fragment>
  )

}

export default WildcardEventSubscriptions;
