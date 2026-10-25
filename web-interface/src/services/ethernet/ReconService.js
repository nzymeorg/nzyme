import RESTClient from "../../util/RESTClient";

const tapsParameter = (taps) => {
  return Array.isArray(taps) ? taps.join(",") : (taps === "*" ? "*" : null);
}

export default class ReconService {

  findAllScans(organizationId, tenantId, taps, category, filters, orderColumn, orderDirection, limit, offset, setScans) {
    RESTClient.get("/ethernet/recon/scans", {
      organization_id: organizationId, tenant_id: tenantId, taps: tapsParameter(taps), category: category,
      filters: filters, order_column: orderColumn, order_direction: orderDirection, limit: limit, offset: offset
    }, (response) => setScans(response.data));
  }

  findScan(uuid, organizationId, tenantId, setScan, onError = undefined) {
    RESTClient.get(`/ethernet/recon/scans/show/${uuid}`, {organization_id: organizationId, tenant_id: tenantId},
      (response) => setScan(response.data), onError);
  }

  findScanTargets(uuid, organizationId, tenantId, filters, orderColumn, orderDirection, limit, offset, setTargets) {
    RESTClient.get(`/ethernet/recon/scans/show/${uuid}/targets`, {
      organization_id: organizationId, tenant_id: tenantId, filters: filters, order_column: orderColumn,
      order_direction: orderDirection, limit: limit, offset: offset
    }, (response) => setTargets(response.data));
  }

  findScanSummary(uuid, organizationId, tenantId, setSummary) {
    RESTClient.get(`/ethernet/recon/scans/show/${uuid}/summary`, {organization_id: organizationId, tenant_id: tenantId},
      (response) => setSummary(response.data));
  }

  findActiveScansHistogram(organizationId, tenantId, taps, category, filters, timeRange, setHistogram) {
    RESTClient.get("/ethernet/recon/scans/histogram/active", {
      organization_id: organizationId, tenant_id: tenantId, taps: tapsParameter(taps), category: category,
      filters: filters, time_range: timeRange
    }, (response) => setHistogram(response.data));
  }

  deleteScan(uuid, organizationId, tenantId, successCallback) {
    RESTClient.delete(`/ethernet/recon/scans/organization/${organizationId}/tenant/${tenantId}/show/${uuid}`,
      successCallback);
  }

  deleteAllScans(organizationId, tenantId, successCallback) {
    RESTClient.delete(`/ethernet/recon/scans/organization/${organizationId}/tenant/${tenantId}`, successCallback);
  }

  findAllExclusions(organizationId, tenantId, filters, orderColumn, orderDirection, limit, offset, setExclusions) {
    RESTClient.get("/ethernet/recon/scans/exclusions", {
      organization_id: organizationId, tenant_id: tenantId, filters: filters, order_column: orderColumn,
      order_direction: orderDirection, limit: limit, offset: offset
    }, (response) => setExclusions(response.data));
  }

  findExclusion(uuid, organizationId, tenantId, setExclusion, onError = undefined) {
    RESTClient.get(`/ethernet/recon/scans/exclusions/show/${uuid}`, {organization_id: organizationId, tenant_id: tenantId},
      (response) => setExclusion(response.data), onError);
  }

  previewExclusion(organizationId, tenantId, exclusion, setImpact, errorCallback) {
    RESTClient.post(`/ethernet/recon/scans/exclusions/organization/${organizationId}/tenant/${tenantId}/preview`,
      exclusion, (response) => setImpact(response.data), errorCallback);
  }

  createExclusion(organizationId, tenantId, exclusion, successCallback, errorCallback) {
    RESTClient.post(`/ethernet/recon/scans/exclusions/organization/${organizationId}/tenant/${tenantId}`,
      exclusion, successCallback, errorCallback);
  }

  updateExclusion(uuid, organizationId, tenantId, exclusion, successCallback, errorCallback) {
    RESTClient.put(`/ethernet/recon/scans/exclusions/organization/${organizationId}/tenant/${tenantId}/show/${uuid}`,
      exclusion, successCallback, errorCallback);
  }

  setExclusionEnabled(uuid, organizationId, tenantId, enabled, successCallback) {
    RESTClient.put(`/ethernet/recon/scans/exclusions/organization/${organizationId}/tenant/${tenantId}/show/${uuid}/enabled/${enabled}`,
      {}, successCallback);
  }

  applyExclusion(uuid, organizationId, tenantId, successCallback) {
    RESTClient.post(`/ethernet/recon/scans/exclusions/organization/${organizationId}/tenant/${tenantId}/show/${uuid}/apply`,
      {}, successCallback);
  }

  deleteExclusion(uuid, organizationId, tenantId, successCallback) {
    RESTClient.delete(`/ethernet/recon/scans/exclusions/organization/${organizationId}/tenant/${tenantId}/show/${uuid}`,
      successCallback);
  }

  getConfiguration(organizationId, tenantId, setConfiguration) {
    RESTClient.get(`/ethernet/recon/scans/organization/${organizationId}/tenant/${tenantId}/configuration`, {},
      (response) => setConfiguration(response.data));
  }

  updateConfiguration(newConfig, organizationId, tenantId, successCallback, errorCallback) {
    RESTClient.put(`/ethernet/recon/scans/organization/${organizationId}/tenant/${tenantId}/configuration`,
      {change: newConfig}, successCallback, errorCallback);
  }

}
