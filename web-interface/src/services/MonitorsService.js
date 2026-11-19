import RESTClient from "../util/RESTClient";

class MonitorsService {

  findOne(id, organizationId, tenantId, setMonitor) {
    RESTClient.get(`/monitors/show/${id}`,
      { organization_id: organizationId, tenant_id: tenantId },
      function (response) {
        setMonitor(response.data);
      })
  }

  findDetectionsTimelineOfMonitor(id, limit, offset, setDetections) {
    RESTClient.get(`/monitors/show/${id}/detections/timeline`,
      { limit: limit, offset: offset },
      function (response) {
        setDetections(response.data);
      })
  }

  findAllOfType(monitorType, organizationId, tenantId, limit, offset, setMonitors) {
    RESTClient.get(`/monitors/type/${monitorType}`,
      { limit: limit, offset: offset, organization_id: organizationId, tenant_id: tenantId },
      function (response) {
        setMonitors(response.data);
      })
  }

  findAllOfSubsystem(subsystem, organizationId, tenantId, filters, orderColumn, orderDirection, limit, offset, setMonitors) {
    RESTClient.get(`/monitors/subsystem/${subsystem}`,
      { limit: limit, offset: offset, organization_id: organizationId, tenant_id: tenantId,
        filters: filters, order_column: orderColumn, order_direction: orderDirection },
      function (response) {
        setMonitors(response.data);
      })
  }

  createMonitor(monitorType, name, description, taps, locationId, trigger_condition, interval, lookback, filters, organizationId, tenantId, successCallback, errorCallback) {
    const tapsParam = (taps && taps === "*") ? null : taps;

    RESTClient.post(`/monitors/type/${monitorType}`,{
      name: name,
      description: description,
      taps: tapsParam,
      location_id: locationId ? locationId : null,
      trigger_condition: trigger_condition,
      interval: interval,
      lookback: lookback,
      filters: JSON.stringify(filters),
      organization_id: organizationId,
      tenant_id: tenantId,
    }, successCallback, errorCallback);
  }

  updateMonitorMetadata(id, name, description, locationId, trigger_condition, interval, lookback, successCallback, errorCallback) {
    RESTClient.put(`/monitors/show/${id}`,{
      name: name,
      description: description,
      location_id: locationId ? locationId : null,
      trigger_condition: trigger_condition,
      interval: interval,
      lookback: lookback,
    }, successCallback, errorCallback);
  }

  updateMonitorFilterInformation(id, taps, filters, successCallback, errorCallback) {
    const tapsParam = (taps && taps === "*") ? null : taps;

    RESTClient.put(`/monitors/show/${id}`,{
      taps: tapsParam,
      filters: JSON.stringify(filters)
    }, successCallback, errorCallback);
  }

  deleteMonitor(id, onSuccess) {
    RESTClient.delete(`/monitors/show/${id}`, onSuccess)
  }

}

export default MonitorsService;