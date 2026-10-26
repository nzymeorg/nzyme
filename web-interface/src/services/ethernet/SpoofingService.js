import RESTClient from "../../util/RESTClient";

export default class SpoofingService {

  findAllArpFindings(organizationId, tenantId, filters, timeRange, orderColumn, orderDirection, limit, offset, setFindings) {
    RESTClient.get("/ethernet/spoofing/arp", {
      organization_id: organizationId, tenant_id: tenantId, filters: filters, time_range: timeRange,
      order_column: orderColumn, order_direction: orderDirection, limit: limit, offset: offset
    }, (response) => setFindings(response.data));
  }

  findActiveArpFindingsHistogram(organizationId, tenantId, filters, timeRange, setHistogram) {
    RESTClient.get("/ethernet/spoofing/arp/histogram/active", {
      organization_id: organizationId, tenant_id: tenantId, filters: filters, time_range: timeRange
    }, (response) => setHistogram(response.data));
  }

  findArpFinding(uuid, organizationId, tenantId, setFinding, onError = undefined) {
    RESTClient.get(`/ethernet/spoofing/arp/show/${uuid}`, {organization_id: organizationId, tenant_id: tenantId},
      (response) => setFinding(response.data), onError);
  }

  deleteArpFinding(uuid, organizationId, tenantId, successCallback) {
    RESTClient.delete(`/ethernet/spoofing/arp/organization/${organizationId}/tenant/${tenantId}/show/${uuid}`,
      successCallback);
  }

  deleteAllArpFindings(organizationId, tenantId, successCallback) {
    RESTClient.delete(`/ethernet/spoofing/arp/organization/${organizationId}/tenant/${tenantId}`, successCallback);
  }

  findAllExclusions(organizationId, tenantId, filters, orderColumn, orderDirection, limit, offset, setExclusions) {
    RESTClient.get("/ethernet/spoofing/exclusions", {
      organization_id: organizationId, tenant_id: tenantId, filters: filters, order_column: orderColumn,
      order_direction: orderDirection, limit: limit, offset: offset
    }, (response) => setExclusions(response.data));
  }

  findExclusion(uuid, organizationId, tenantId, setExclusion, onError = undefined) {
    RESTClient.get(`/ethernet/spoofing/exclusions/show/${uuid}`, {organization_id: organizationId, tenant_id: tenantId},
      (response) => setExclusion(response.data), onError);
  }

  previewExclusion(organizationId, tenantId, exclusion, setImpact, errorCallback) {
    RESTClient.post(`/ethernet/spoofing/exclusions/organization/${organizationId}/tenant/${tenantId}/preview`,
      exclusion, (response) => setImpact(response.data), errorCallback);
  }

  createExclusion(organizationId, tenantId, exclusion, successCallback, errorCallback) {
    RESTClient.post(`/ethernet/spoofing/exclusions/organization/${organizationId}/tenant/${tenantId}`,
      exclusion, successCallback, errorCallback);
  }

  updateExclusion(uuid, organizationId, tenantId, exclusion, successCallback, errorCallback) {
    RESTClient.put(`/ethernet/spoofing/exclusions/organization/${organizationId}/tenant/${tenantId}/show/${uuid}`,
      exclusion, successCallback, errorCallback);
  }

  setExclusionEnabled(uuid, organizationId, tenantId, enabled, successCallback) {
    RESTClient.put(`/ethernet/spoofing/exclusions/organization/${organizationId}/tenant/${tenantId}/show/${uuid}/enabled/${enabled}`,
      {}, successCallback);
  }

  applyExclusion(uuid, organizationId, tenantId, successCallback) {
    RESTClient.post(`/ethernet/spoofing/exclusions/organization/${organizationId}/tenant/${tenantId}/show/${uuid}/apply`,
      {}, successCallback);
  }

  deleteExclusion(uuid, organizationId, tenantId, successCallback) {
    RESTClient.delete(`/ethernet/spoofing/exclusions/organization/${organizationId}/tenant/${tenantId}/show/${uuid}`,
      successCallback);
  }

  getConfiguration(organizationId, tenantId, setConfiguration) {
    RESTClient.get(`/ethernet/spoofing/organization/${organizationId}/tenant/${tenantId}/configuration`, {},
      (response) => setConfiguration(response.data));
  }

  updateConfiguration(newConfig, organizationId, tenantId, successCallback, errorCallback) {
    RESTClient.put(`/ethernet/spoofing/organization/${organizationId}/tenant/${tenantId}/configuration`,
      {change: newConfig}, successCallback, errorCallback);
  }

}
