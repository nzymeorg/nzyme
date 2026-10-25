import RESTClient from "../../util/RESTClient";

const tapsParameter = (taps) => {
  return Array.isArray(taps) ? taps.join(",") : (taps === "*" ? "*" : null);
}

export default class IcmpService {

  findAllPackets(organizationId, tenantId, timeRange, filters, orderColumn, orderDirection, taps, limit, offset, setPackets) {
    RESTClient.get("/ethernet/icmp/packets", {
      organization_id: organizationId, tenant_id: tenantId, time_range: timeRange, filters: filters,
      order_column: orderColumn, order_direction: orderDirection, taps: tapsParameter(taps), limit: limit, offset: offset
    }, (response) => setPackets(response.data));
  }

  getStatistics(timeRange, filters, taps, setStatistics) {
    RESTClient.get("/ethernet/icmp/statistics", {time_range: timeRange, filters: filters, taps: tapsParameter(taps)},
      (response) => setStatistics(response.data));
  }

  getTopAddresses(organizationId, tenantId, column, timeRange, filters, orderColumn, orderDirection, limit, offset, taps, setData) {
    RESTClient.get("/ethernet/icmp/histograms/addresses/top", {
      organization_id: organizationId, tenant_id: tenantId, column: column, time_range: timeRange, filters: filters,
      order_column: orderColumn, order_direction: orderDirection, limit: limit, offset: offset, taps: tapsParameter(taps)
    }, (response) => setData(response.data));
  }

  getTypes(organizationId, tenantId, timeRange, filters, orderColumn, orderDirection, limit, offset, taps, setData) {
    RESTClient.get("/ethernet/icmp/histograms/types", {
      organization_id: organizationId, tenant_id: tenantId, time_range: timeRange, filters: filters,
      order_column: orderColumn, order_direction: orderDirection, limit: limit, offset: offset, taps: tapsParameter(taps)
    }, (response) => setData(response.data));
  }

  findAllRedirects(organizationId, tenantId, filters, taps, timeRange, orderColumn, orderDirection, limit, offset, setRedirects) {
    RESTClient.get("/ethernet/icmp/redirects", {
      organization_id: organizationId, tenant_id: tenantId, filters: filters, taps: tapsParameter(taps),
      time_range: timeRange, order_column: orderColumn, order_direction: orderDirection, limit: limit, offset: offset
    }, (response) => setRedirects(response.data));
  }

  findAllTraceroutes(organizationId, tenantId, filters, taps, timeRange, orderColumn, orderDirection, limit, offset, setTraceroutes) {
    RESTClient.get("/ethernet/icmp/traceroutes", {
      organization_id: organizationId, tenant_id: tenantId, filters: filters, taps: tapsParameter(taps),
      time_range: timeRange, order_column: orderColumn, order_direction: orderDirection, limit: limit, offset: offset
    }, (response) => setTraceroutes(response.data));
  }

  getTraceroutesHistogram(organizationId, tenantId, filters, taps, timeRange, setHistogram) {
    RESTClient.get("/ethernet/icmp/traceroutes/histogram", {
      organization_id: organizationId, tenant_id: tenantId, filters: filters, taps: tapsParameter(taps),
      time_range: timeRange
    }, (response) => setHistogram(response.data));
  }

  getTraceroutesBreakdown(organizationId, tenantId, by, filters, taps, timeRange, orderColumn, orderDirection, limit, offset, setData) {
    RESTClient.get("/ethernet/icmp/traceroutes/histograms", {
      organization_id: organizationId, tenant_id: tenantId, by: by, filters: filters, taps: tapsParameter(taps),
      time_range: timeRange, order_column: orderColumn, order_direction: orderDirection, limit: limit, offset: offset
    }, (response) => setData(response.data));
  }

  findAllExclusions(organizationId, tenantId, filters, orderColumn, orderDirection, limit, offset, setExclusions) {
    RESTClient.get("/ethernet/icmp/exclusions", {
      organization_id: organizationId, tenant_id: tenantId, filters: filters, order_column: orderColumn,
      order_direction: orderDirection, limit: limit, offset: offset
    }, (response) => setExclusions(response.data));
  }

  findExclusion(uuid, organizationId, tenantId, setExclusion, onError = undefined) {
    RESTClient.get(`/ethernet/icmp/exclusions/show/${uuid}`, {organization_id: organizationId, tenant_id: tenantId},
      (response) => setExclusion(response.data), onError);
  }

  createExclusion(organizationId, tenantId, exclusion, successCallback, errorCallback) {
    RESTClient.post(`/ethernet/icmp/exclusions/organization/${organizationId}/tenant/${tenantId}`,
      exclusion, successCallback, errorCallback);
  }

  updateExclusion(uuid, organizationId, tenantId, exclusion, successCallback, errorCallback) {
    RESTClient.put(`/ethernet/icmp/exclusions/organization/${organizationId}/tenant/${tenantId}/show/${uuid}`,
      exclusion, successCallback, errorCallback);
  }

  setExclusionEnabled(uuid, organizationId, tenantId, enabled, successCallback) {
    RESTClient.put(`/ethernet/icmp/exclusions/organization/${organizationId}/tenant/${tenantId}/show/${uuid}/enabled/${enabled}`,
      {}, successCallback);
  }

  deleteExclusion(uuid, organizationId, tenantId, successCallback) {
    RESTClient.delete(`/ethernet/icmp/exclusions/organization/${organizationId}/tenant/${tenantId}/show/${uuid}`, successCallback);
  }

  getUnreachables(organizationId, tenantId, filters, taps, timeRange, orderColumn, orderDirection, limit, offset, setData) {
    RESTClient.get("/ethernet/icmp/unreachables", {
      organization_id: organizationId, tenant_id: tenantId, filters: filters, taps: tapsParameter(taps),
      time_range: timeRange, order_column: orderColumn, order_direction: orderDirection, limit: limit, offset: offset
    }, (response) => setData(response.data));
  }

  getUnreachablesHistogram(organizationId, tenantId, filters, taps, timeRange, setHistogram) {
    RESTClient.get("/ethernet/icmp/unreachables/histogram", {
      organization_id: organizationId, tenant_id: tenantId, filters: filters, taps: tapsParameter(taps),
      time_range: timeRange
    }, (response) => setHistogram(response.data));
  }

  getUnreachablesBreakdown(organizationId, tenantId, by, filters, taps, timeRange, orderColumn, orderDirection, limit, offset, setData) {
    RESTClient.get("/ethernet/icmp/unreachables/histograms", {
      organization_id: organizationId, tenant_id: tenantId, by: by, filters: filters, taps: tapsParameter(taps),
      time_range: timeRange, order_column: orderColumn, order_direction: orderDirection, limit: limit, offset: offset
    }, (response) => setData(response.data));
  }

  getUnreachablesHostPort(organizationId, tenantId, filters, taps, timeRange, orderColumn, orderDirection, limit, offset, setData) {
    RESTClient.get("/ethernet/icmp/unreachables/histograms/host-port", {
      organization_id: organizationId, tenant_id: tenantId, filters: filters, taps: tapsParameter(taps),
      time_range: timeRange, order_column: orderColumn, order_direction: orderDirection, limit: limit, offset: offset
    }, (response) => setData(response.data));
  }

  getUnreachableHosts(organizationId, tenantId, filters, taps, timeRange, orderColumn, orderDirection, limit, offset, setData) {
    RESTClient.get("/ethernet/icmp/unreachable-hosts", {
      organization_id: organizationId, tenant_id: tenantId, filters: filters, taps: tapsParameter(taps),
      time_range: timeRange, order_column: orderColumn, order_direction: orderDirection, limit: limit, offset: offset
    }, (response) => setData(response.data));
  }

  getUnreachableHostsHistogram(organizationId, tenantId, filters, taps, timeRange, setHistogram) {
    RESTClient.get("/ethernet/icmp/unreachable-hosts/histogram", {
      organization_id: organizationId, tenant_id: tenantId, filters: filters, taps: tapsParameter(taps),
      time_range: timeRange
    }, (response) => setHistogram(response.data));
  }

  getUnreachableHostsBreakdown(organizationId, tenantId, by, filters, taps, timeRange, orderColumn, orderDirection, limit, offset, setData) {
    RESTClient.get("/ethernet/icmp/unreachable-hosts/histograms", {
      organization_id: organizationId, tenant_id: tenantId, by: by, filters: filters, taps: tapsParameter(taps),
      time_range: timeRange, order_column: orderColumn, order_direction: orderDirection, limit: limit, offset: offset
    }, (response) => setData(response.data));
  }

  getTunnels(organizationId, tenantId, filters, taps, timeRange, orderColumn, orderDirection, limit, offset, setData) {
    RESTClient.get("/ethernet/icmp/tunnels", {
      organization_id: organizationId, tenant_id: tenantId, filters: filters, taps: tapsParameter(taps),
      time_range: timeRange, order_column: orderColumn, order_direction: orderDirection, limit: limit, offset: offset
    }, (response) => setData(response.data));
  }

  getTunnelsHistogram(organizationId, tenantId, filters, taps, timeRange, setHistogram) {
    RESTClient.get("/ethernet/icmp/tunnels/histogram", {
      organization_id: organizationId, tenant_id: tenantId, filters: filters, taps: tapsParameter(taps),
      time_range: timeRange
    }, (response) => setHistogram(response.data));
  }

  getTunnelsBreakdown(organizationId, tenantId, by, filters, taps, timeRange, orderColumn, orderDirection, limit, offset, setData) {
    RESTClient.get("/ethernet/icmp/tunnels/histograms", {
      organization_id: organizationId, tenant_id: tenantId, by: by, filters: filters, taps: tapsParameter(taps),
      time_range: timeRange, order_column: orderColumn, order_direction: orderDirection, limit: limit, offset: offset
    }, (response) => setData(response.data));
  }

  findRedirect(uuid, organizationId, tenantId, setData, onError = undefined) {
    RESTClient.get(`/ethernet/icmp/redirects/show/${uuid}`, {organization_id: organizationId, tenant_id: tenantId},
      (response) => setData(response.data), onError);
  }

  findTraceroute(uuid, organizationId, tenantId, setData, onError = undefined) {
    RESTClient.get(`/ethernet/icmp/traceroutes/show/${uuid}`, {organization_id: organizationId, tenant_id: tenantId},
      (response) => setData(response.data), onError);
  }

  findTunnel(uuid, organizationId, tenantId, setData, onError = undefined) {
    RESTClient.get(`/ethernet/icmp/tunnels/show/${uuid}`, {organization_id: organizationId, tenant_id: tenantId},
      (response) => setData(response.data), onError);
  }

  getConfiguration(organizationId, tenantId, setConfiguration) {
    RESTClient.get(`/ethernet/icmp/organization/${organizationId}/tenant/${tenantId}/configuration`, {},
      (response) => setConfiguration(response.data));
  }

  updateConfiguration(newConfig, organizationId, tenantId, successCallback, errorCallback) {
    RESTClient.put(`/ethernet/icmp/organization/${organizationId}/tenant/${tenantId}/configuration`,
      {change: newConfig}, successCallback, errorCallback);
  }

}
