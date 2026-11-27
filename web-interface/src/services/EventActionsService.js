import RESTClient from '../util/RESTClient'

class EventActionsService {

  findSystemEventType(eventTypeName, setEventType, organizationId = undefined) {
    const params = organizationId ? {organization_id: organizationId} : {};
    RESTClient.get("/system/events/types/system/show/" + eventTypeName, params, function(response) {
      setEventType(response.data);
    })
  }

  findDetectionType(detectionName, setDetectionType, organizationId) {
    RESTClient.get("/alerts/detections/types/show/" + detectionName,
        {organization_uuid: organizationId}, function(response) {
      setDetectionType(response.data);
    })
  }

  findAllActions(setActions, limit, offset) {
    RESTClient.get("/system/events/actions",
        {limit: limit, offset: offset}, function(response) {
          setActions(response.data);
        })
  }

  findAllActionsOfOrganization(organizationId, setActions, limit, offset) {
    RESTClient.get("/system/authentication/mgmt/organizations/show/" + organizationId + "/events/actions",
        {limit: limit, offset: offset}, function(response) {
      setActions(response.data);
    })
  }

  findAction(actionId, setAction) {
    RESTClient.get("/system/events/actions/show/" + actionId, {}, function(response) {
          setAction(response.data);
    })
  }

  findActionOfOrganization(organizationId, actionId, setAction) {
    RESTClient.get("/system/authentication/mgmt/organizations/show/" + organizationId + "/events/actions/show/" + actionId,
        {}, function(response) {
          setAction(response.data);
        })
  }

  deleteAction(actionId, successCallback) {
    RESTClient.delete("/system/events/actions/show/" + actionId, successCallback);
  }

  subscribeActionToDetectionEvent(detectionName, actionId, organizationId, successCallback, errorCallback) {
    RESTClient.post("/alerts/detections/types/show/" + detectionName + "/subscriptions",
        {organization_id: organizationId, action_id: actionId}, successCallback, errorCallback);
  }

  unsubscribeActionFromDetectionEvent(detectionName, subscriptionId, successCallback) {
    RESTClient.delete("/alerts/detections/types/show/ " + detectionName + " /subscriptions/show/" + subscriptionId,
        successCallback);
  }

  findAllDetectionAlertWildcardSubscriptions(organizationUUID, setSubscriptions) {
    RESTClient.get("/alerts/detections/subscriptions/wildcard", {organization_uuid: organizationUUID},
        (response) => {
          setSubscriptions(response.data)
        })
  }

  subscribeWildcardAction(actionId, organizationId, successCallback, errorCallback) {
    RESTClient.post("/alerts/detections/subscriptions/wildcard",
        {organization_id: organizationId, action_id: actionId}, successCallback, errorCallback);
  }

  unsubscribeWildcardAction(subscriptionId, successCallback) {
    RESTClient.delete("/alerts/detections/subscriptions/wildcard/show/" + subscriptionId, successCallback);
  }

  subscribeActionToEvent(eventTypeName, actionId, organizationId, successCallback, errorCallback) {
    const data = organizationId ? {organization_id: organizationId, action_id: actionId} : {action_id: actionId};

    RESTClient.post("/system/events/types/system/show/" + eventTypeName + "/subscriptions",
        data, successCallback, errorCallback);
  }

  unsubscribeActionFromEvent(eventTypeName, subscriptionId, successCallback) {
    RESTClient.delete("/system/events/types/system/show/" + eventTypeName + "/subscriptions/show/" + subscriptionId,
        successCallback);
  }

  findAllSystemEventWildcardSubscriptions(organizationId, setSubscriptions) {
    const params = organizationId ? {organization_id: organizationId} : {};
    RESTClient.get("/system/events/types/system/wildcard/subscriptions", params, (response) => {
      setSubscriptions(response.data);
    })
  }

  subscribeActionToAllSystemEvents(actionId, organizationId, successCallback, errorCallback) {
    const data = organizationId ? {organization_id: organizationId, action_id: actionId} : {action_id: actionId};

    RESTClient.post("/system/events/types/system/wildcard/subscriptions", data, successCallback, errorCallback);
  }

  unsubscribeActionFromAllSystemEvents(subscriptionId, successCallback) {
    RESTClient.delete("/system/events/types/system/wildcard/subscriptions/show/" + subscriptionId, successCallback);
  }

  createEmailAction(organizationId, name, description, subjectPrefix, receivers, successCallback) {
    RESTClient.post("/system/events/actions/email", {
      "organization_id": organizationId,
      "name": name,
      "description": description,
      "subject_prefix": subjectPrefix,
      "receivers": receivers
    }, successCallback);
  }

  updateEmailAction(actionId, name, description, subjectPrefix, receivers, successCallback) {
    RESTClient.put("/system/events/actions/email/" + actionId, {
      "name": name,
      "description": description,
      "subject_prefix": subjectPrefix,
      "receivers": receivers
    }, successCallback);
  }

  createWebhookAction(organizationId, name, description, url, allowInsecure, bearerToken, successCallback) {
    RESTClient.post("/system/events/actions/webhook", {
      "organization_id": organizationId,
      "name": name,
      "description": description,
      "url": url,
      "allow_insecure": allowInsecure,
      "bearer_token": bearerToken
    }, successCallback);
  }

  updateWebhookAction(actionId, name, description, url, allowInsecure, bearerToken, successCallback) {
    RESTClient.put("/system/events/actions/webhook/" + actionId, {
      "name": name,
      "description": description,
      "url": url,
      "allow_insecure": allowInsecure,
      "bearer_token": bearerToken
    }, successCallback);
  }

  createSyslogAction(organizationId, name, description, protocol, syslogHostname, host, port, successCallback) {
    RESTClient.post("/system/events/actions/syslog", {
      "organization_id": organizationId,
      "name": name,
      "description": description,
      "protocol": protocol,
      "syslog_hostname": syslogHostname,
      "host": host,
      "port": port
    }, successCallback);
  }

  updateSyslogAction(actionId, name, description, protocol, syslogHostname, host, port, successCallback) {
    RESTClient.put("/system/events/actions/syslog/" + actionId, {
      "name": name,
      "description": description,
      "protocol": protocol,
      "syslog_hostname": syslogHostname,
      "host": host,
      "port": port
    }, successCallback);
  }

}

export default EventActionsService;