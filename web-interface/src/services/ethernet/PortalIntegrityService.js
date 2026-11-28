import RESTClient from '../../util/RESTClient'

export default class PortalIntegrityService {

  findAllReports(organizationId, tenantId, timeRange, filters, orderColumn, orderDirection, taps, limit, offset, setReports) {
    const tapsList = Array.isArray(taps) ? taps.join(",") : (taps === "*" ? "*" : null)

    RESTClient.get("/ethernet/portalintegrity/reports", { organization_id: organizationId, tenant_id: tenantId, time_range: timeRange, filters: filters, order_column: orderColumn, order_direction: orderDirection, taps: tapsList, limit: limit, offset: offset },
      (response) => setReports(response.data)
    )
  }

  findAllControlUrls(organizationId, tenantId, taps, limit, offset, setControlUrls, errorCallback = undefined) {
    const tapsList = Array.isArray(taps) ? taps.join(",") : (taps === "*" ? "*" : null)

    RESTClient.get("/ethernet/portalintegrity/controlurls", { organization_id: organizationId, tenant_id: tenantId, taps: tapsList, limit: limit, offset: offset },
      (response) => setControlUrls(response.data), errorCallback
    )
  }

  findOneControlUrl(uuid, organizationId, tenantId, setControlUrl, errorCallback = undefined) {
    RESTClient.get(`/ethernet/portalintegrity/controlurls/show/${uuid}`, { organization_id: organizationId, tenant_id: tenantId },
      (response) => setControlUrl(response.data), errorCallback
    )
  }

  findObservedState(uuid, organizationId, tenantId, timeRange, setObservedState, errorCallback = undefined) {
    RESTClient.get(`/ethernet/portalintegrity/controlurls/show/${uuid}/observed`, { organization_id: organizationId, tenant_id: tenantId, time_range: timeRange },
      (response) => setObservedState(response.data), errorCallback
    )
  }

  setExpectation(uuid, organizationId, tenantId, expectation, successCallback, errorCallback = undefined) {
    RESTClient.put(`/ethernet/portalintegrity/controlurls/organization/${organizationId}/tenant/${tenantId}/show/${uuid}/expectation`,
      expectation, successCallback, errorCallback);
  }

  addExpectedValues(uuid, organizationId, tenantId, values, successCallback, errorCallback = undefined) {
    RESTClient.put(`/ethernet/portalintegrity/controlurls/organization/${organizationId}/tenant/${tenantId}/show/${uuid}/expected`,
      values, successCallback, errorCallback);
  }

  deleteControlUrl(uuid, organizationId, tenantId, successCallback, errorCallback = undefined) {
    RESTClient.delete(`/ethernet/portalintegrity/controlurls/organization/${organizationId}/tenant/${tenantId}/show/${uuid}`,
      successCallback, errorCallback);
  }

  findOneReport(uuid, organizationId, tenantId, taps, setReport, errorCallback = undefined) {
    const tapsList = Array.isArray(taps) ? taps.join(",") : (taps === "*" ? "*" : null)

    RESTClient.get(`/ethernet/portalintegrity/reports/show/${uuid}`, { organization_id: organizationId, tenant_id: tenantId, taps: tapsList },
      (response) => setReport(response.data), errorCallback
    )
  }

}