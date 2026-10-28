import RESTClient from '../../util/RESTClient'

export default class VpnService {

  getDisplaySettings(organizationId, tenantId, setSettings) {
    RESTClient.get(`/ethernet/vpn/organization/${organizationId}/tenant/${tenantId}/display`, {},
      (response) => setSettings(response.data));
  }

  getConfiguration(organizationId, tenantId, setConfiguration) {
    RESTClient.get(`/ethernet/vpn/organization/${organizationId}/tenant/${tenantId}/configuration`, {},
      (response) => setConfiguration(response.data));
  }

  updateConfiguration(newConfig, organizationId, tenantId, successCallback, errorCallback) {
    RESTClient.put(`/ethernet/vpn/organization/${organizationId}/tenant/${tenantId}/configuration`,
      {change: newConfig}, successCallback, errorCallback);
  }

  findAllTunnels(organizationId, tenantId, timeRange, filters, orderColumn, orderDirection, taps, limit, offset, setTunnels) {
    const tapsList = Array.isArray(taps) ? taps.join(",") : (taps === "*" ? "*" : null)

    RESTClient.get("/ethernet/vpn/tunnels", { organization_id: organizationId, tenant_id: tenantId, time_range: timeRange, filters: filters, order_column: orderColumn, order_direction: orderDirection, taps: tapsList, limit: limit, offset: offset },
        (response) => setTunnels(response.data)
    )
  }

  findTunnel(sessionId, organizationId, tenantId, taps, setTunnel) {
    const tapsList = Array.isArray(taps) ? taps.join(",") : (taps === "*" ? "*" : null)

    RESTClient.get(`/ethernet/vpn/tunnels/show/${sessionId}`, { organization_id: organizationId, tenant_id: tenantId, taps: tapsList },
      (response) => setTunnel(response.data)
    )
  }

  findTunnelOfL4Session(l4Type, sessionKey, startTime, organizationId, tenantId, taps, setTunnel, onNotFound) {
    const tapsList = Array.isArray(taps) ? taps.join(",") : (taps === "*" ? "*" : null)

    RESTClient.get(`/ethernet/vpn/tunnels/l4/${l4Type}/${sessionKey}`, { start_time: startTime, organization_id: organizationId, tenant_id: tenantId, taps: tapsList },
      (response) => setTunnel(response.data),
      (error) => {
        if (error.response && error.response.status === 404 && onNotFound) {
          onNotFound();
        }
      }
    )
  }

  getActiveTunnelCountHistogram(setHistogram, timeRange, filters, taps) {
    const tapsList = Array.isArray(taps) ? taps.join(",") : (taps === "*" ? "*" : null)

    RESTClient.get("/ethernet/vpn/tunnels/active/histogram", {
        filters: filters,
        time_range: timeRange,
        taps: tapsList,
      }, (response) => setHistogram(response.data)
    )
  }

  getTopClientsHistogram(setHistogram, organizationId, tenantId, timeRange, orderColumn, orderDirection, limit, offset, filters, taps) {
    const tapsList = Array.isArray(taps) ? taps.join(",") : (taps === "*" ? "*" : null)

    RESTClient.get("/ethernet/vpn/tunnels/clients/top/histogram", {
        organization_id: organizationId,
        tenant_id: tenantId,
        filters: filters,
        time_range: timeRange,
        taps: tapsList,
        order_column: orderColumn,
        order_direction: orderDirection,
        limit: limit,
        offset: offset
      }, (response) => setHistogram(response.data)
    )
  }

  getTopServersHistogram(setHistogram, organizationId, tenantId, timeRange, orderColumn, orderDirection, limit, offset, filters, taps) {
    const tapsList = Array.isArray(taps) ? taps.join(",") : (taps === "*" ? "*" : null)

    RESTClient.get("/ethernet/vpn/tunnels/servers/top/histogram", {
        organization_id: organizationId,
        tenant_id: tenantId,
        filters: filters,
        time_range: timeRange,
        taps: tapsList,
        order_column: orderColumn,
        order_direction: orderDirection,
        limit: limit,
        offset: offset
      }, (response) => setHistogram(response.data)
    )
  }

}
