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

  findAllDhcpFindings(organizationId, tenantId, filters, timeRange, orderColumn, orderDirection, limit, offset, setFindings) {
    RESTClient.get("/ethernet/spoofing/dhcp", {
      organization_id: organizationId, tenant_id: tenantId, filters: filters, time_range: timeRange,
      order_column: orderColumn, order_direction: orderDirection, limit: limit, offset: offset
    }, (response) => setFindings(response.data));
  }

  findActiveDhcpFindingsHistogram(organizationId, tenantId, filters, timeRange, setHistogram) {
    RESTClient.get("/ethernet/spoofing/dhcp/histogram/active", {
      organization_id: organizationId, tenant_id: tenantId, filters: filters, time_range: timeRange
    }, (response) => setHistogram(response.data));
  }

  findDhcpFinding(uuid, organizationId, tenantId, setFinding, onError = undefined) {
    RESTClient.get(`/ethernet/spoofing/dhcp/show/${uuid}`, {organization_id: organizationId, tenant_id: tenantId},
      (response) => setFinding(response.data), onError);
  }

  deleteDhcpFinding(uuid, organizationId, tenantId, successCallback) {
    RESTClient.delete(`/ethernet/spoofing/dhcp/organization/${organizationId}/tenant/${tenantId}/show/${uuid}`,
      successCallback);
  }

  deleteAllDhcpFindings(organizationId, tenantId, successCallback) {
    RESTClient.delete(`/ethernet/spoofing/dhcp/organization/${organizationId}/tenant/${tenantId}`, successCallback);
  }

  findAllDhcpServers(organizationId, tenantId, filters, orderColumn, orderDirection, limit, offset, setServers) {
    RESTClient.get("/ethernet/spoofing/dhcp/servers", {
      organization_id: organizationId, tenant_id: tenantId, filters: filters, order_column: orderColumn,
      order_direction: orderDirection, limit: limit, offset: offset
    }, (response) => setServers(response.data));
  }

  setDhcpServerTrusted(uuid, organizationId, tenantId, trusted, successCallback) {
    RESTClient.put(`/ethernet/spoofing/dhcp/servers/organization/${organizationId}/tenant/${tenantId}/show/${uuid}/trusted/${trusted}`,
      {}, successCallback);
  }

  acceptDhcpServerConfiguration(uuid, organizationId, tenantId, successCallback) {
    RESTClient.put(`/ethernet/spoofing/dhcp/servers/organization/${organizationId}/tenant/${tenantId}/show/${uuid}/baseline`,
      {}, successCallback);
  }

  restartDhcpLearningPeriod(organizationId, tenantId, successCallback) {
    RESTClient.put(`/ethernet/spoofing/dhcp/servers/organization/${organizationId}/tenant/${tenantId}/learning/restart`,
      {}, successCallback);
  }

  endDhcpLearningPeriod(organizationId, tenantId, successCallback) {
    RESTClient.put(`/ethernet/spoofing/dhcp/servers/organization/${organizationId}/tenant/${tenantId}/learning/end`,
      {}, successCallback);
  }

  deleteDhcpServer(uuid, organizationId, tenantId, successCallback) {
    RESTClient.delete(`/ethernet/spoofing/dhcp/servers/organization/${organizationId}/tenant/${tenantId}/show/${uuid}`,
      successCallback);
  }

  findAllNameResolutionFindings(organizationId, tenantId, filters, timeRange, orderColumn, orderDirection, limit, offset, setFindings) {
    RESTClient.get("/ethernet/spoofing/nameres", {
      organization_id: organizationId, tenant_id: tenantId, filters: filters, time_range: timeRange,
      order_column: orderColumn, order_direction: orderDirection, limit: limit, offset: offset
    }, (response) => setFindings(response.data));
  }

  findActiveNameResolutionFindingsHistogram(organizationId, tenantId, filters, timeRange, setHistogram) {
    RESTClient.get("/ethernet/spoofing/nameres/histogram/active", {
      organization_id: organizationId, tenant_id: tenantId, filters: filters, time_range: timeRange
    }, (response) => setHistogram(response.data));
  }

  findNameResolutionFinding(uuid, organizationId, tenantId, setFinding, onError = undefined) {
    RESTClient.get(`/ethernet/spoofing/nameres/show/${uuid}`, {organization_id: organizationId, tenant_id: tenantId},
      (response) => setFinding(response.data), onError);
  }

  deleteNameResolutionFinding(uuid, organizationId, tenantId, successCallback) {
    RESTClient.delete(`/ethernet/spoofing/nameres/organization/${organizationId}/tenant/${tenantId}/show/${uuid}`,
      successCallback);
  }

  deleteAllNameResolutionFindings(organizationId, tenantId, successCallback) {
    RESTClient.delete(`/ethernet/spoofing/nameres/organization/${organizationId}/tenant/${tenantId}`, successCallback);
  }

  findAllDnsFindings(organizationId, tenantId, filters, timeRange, orderColumn, orderDirection, limit, offset, setFindings) {
    RESTClient.get("/ethernet/spoofing/dns", {
      organization_id: organizationId, tenant_id: tenantId, filters: filters, time_range: timeRange,
      order_column: orderColumn, order_direction: orderDirection, limit: limit, offset: offset
    }, (response) => setFindings(response.data));
  }

  findActiveDnsFindingsHistogram(organizationId, tenantId, filters, timeRange, setHistogram) {
    RESTClient.get("/ethernet/spoofing/dns/histogram/active", {
      organization_id: organizationId, tenant_id: tenantId, filters: filters, time_range: timeRange
    }, (response) => setHistogram(response.data));
  }

  findDnsFinding(uuid, organizationId, tenantId, setFinding, onError = undefined) {
    RESTClient.get(`/ethernet/spoofing/dns/show/${uuid}`, {organization_id: organizationId, tenant_id: tenantId},
      (response) => setFinding(response.data), onError);
  }

  deleteDnsFinding(uuid, organizationId, tenantId, successCallback) {
    RESTClient.delete(`/ethernet/spoofing/dns/organization/${organizationId}/tenant/${tenantId}/show/${uuid}`,
      successCallback);
  }

  deleteAllDnsFindings(organizationId, tenantId, successCallback) {
    RESTClient.delete(`/ethernet/spoofing/dns/organization/${organizationId}/tenant/${tenantId}`, successCallback);
  }

  findNameResolutionResponders(organizationId, tenantId, timeRange, limit, offset, setResponders) {
    RESTClient.get("/ethernet/spoofing/nameres/responders", {
      organization_id: organizationId, tenant_id: tenantId, time_range: timeRange, limit: limit, offset: offset
    }, (response) => setResponders(response.data));
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
