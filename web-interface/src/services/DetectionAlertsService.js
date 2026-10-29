import RESTClient from "../util/RESTClient";

class DetectionAlertsService {

  findAllAlertTypes(setAlertTypes, organizationUUID, limit, offset) {
    RESTClient.get('/alerts/detections/types',
        {limit: limit, offset: offset, organization_uuid: organizationUUID}, function (response) {
      setAlertTypes(response.data)
    })
  }

  findAllAlerts(setAlerts, organizationId, tenantId, timeRange, filters, orderColumn, orderDirection, limit, offset) {
    RESTClient.get('/alerts', {
      organization_id: organizationId,
      tenant_id: tenantId,
      time_range: timeRange,
      filters: filters,
      order_column: orderColumn,
      order_direction: orderDirection,
      limit: limit,
      offset: offset
    }, function (response) {
      setAlerts(response.data)
    })
  }

  findAlert(uuid, setAlert) {
    RESTClient.get('/alerts/show/' + uuid, {}, function (response) {
      setAlert(response.data)
    })
  }

  getActiveAlertsHistogram(setHistogram, organizationId, tenantId, timeRange, filters) {
    RESTClient.get('/alerts/histograms/active',
        {organization_id: organizationId, tenant_id: tenantId, time_range: timeRange, filters: filters},
        (response) => setHistogram(response.data))
  }

  getNewAlertsHistogram(setHistogram, organizationId, tenantId, timeRange, filters) {
    RESTClient.get('/alerts/histograms/new',
        {organization_id: organizationId, tenant_id: tenantId, time_range: timeRange, filters: filters},
        (response) => setHistogram(response.data))
  }

  getTopHistogram(aggregation, setHistogram, organizationId, tenantId, timeRange, filters, orderColumn, orderDirection, limit, offset) {
    RESTClient.get('/alerts/histograms/' + aggregation + '/top', {
      organization_id: organizationId,
      tenant_id: tenantId,
      time_range: timeRange,
      filters: filters,
      order_column: orderColumn,
      order_direction: orderDirection,
      limit: limit,
      offset: offset
    }, (response) => setHistogram(response.data))
  }

  deleteAlert(uuid, successCallback) {
    RESTClient.delete('/alerts/show/' + uuid, successCallback);
  }

  deleteAlerts(uuids, successCallback) {
    RESTClient.put('/alerts/many/delete', {uuids: uuids}, successCallback);
  }

  deleteFilteredAlerts(organizationId, tenantId, timeRange, filters, successCallback) {
    RESTClient.put('/alerts/filtered/delete?' + this.filteredQuery(organizationId, tenantId, timeRange, filters),
        {}, successCallback);
  }

  markAlertAsResolved(uuid, successCallback) {
    RESTClient.put('/alerts/show/' + uuid + '/resolve', {}, successCallback);
  }

  markAlertsAsResolved(uuids, successCallback) {
    RESTClient.put('/alerts/many/resolve', {uuids: uuids}, successCallback);
  }

  markFilteredAlertsAsResolved(organizationId, tenantId, timeRange, filters, successCallback) {
    RESTClient.put('/alerts/filtered/resolve?' + this.filteredQuery(organizationId, tenantId, timeRange, filters),
        {}, successCallback);
  }

  findAlertTimeline(uuid, setTimeline, limit, offset) {
    RESTClient.get('/alerts/show/' + uuid + '/timeline', {limit: limit, offset: offset}, function (response) {
      setTimeline(response.data)
    })
  }

  // PUT requests carry no query parameters through the REST client, so the search scope is encoded here.
  filteredQuery(organizationId, tenantId, timeRange, filters) {
    const params = new URLSearchParams();
    params.set("organization_id", organizationId);
    params.set("tenant_id", tenantId);
    params.set("time_range", JSON.stringify(timeRange));
    if (filters) {
      params.set("filters", JSON.stringify(filters));
    }

    return params.toString();
  }

}

export default DetectionAlertsService;
