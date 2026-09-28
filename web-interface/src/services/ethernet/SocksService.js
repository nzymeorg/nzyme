import RESTClient from '../../util/RESTClient'

export default class SocksService {

  findAllTunnels(organizationId, tenantId, timeRange, filters, orderColumn, orderDirection, taps, limit, offset, setTunnels) {
    const tapsList = Array.isArray(taps) ? taps.join(",") : (taps === "*" ? "*" : null)

    RESTClient.get("/ethernet/socks/tunnels", { organization_id: organizationId, tenant_id: tenantId, time_range: timeRange, filters: filters, order_column: orderColumn, order_direction: orderDirection, taps: tapsList, limit: limit, offset: offset },
        (response) => setTunnels(response.data)
    )
  }

  findTunnel(sessionId, organizationId, tenantId, taps, setTunnel) {
    const tapsList = Array.isArray(taps) ? taps.join(",") : (taps === "*" ? "*" : null)

    RESTClient.get(`/ethernet/socks/tunnels/show/${sessionId}`, { organization_id: organizationId, tenant_id: tenantId, taps: tapsList },
      (response) => setTunnel(response.data)
    )
  }

  getActiveTunnelCountHistogram(setHistogram, timeRange, filters, taps) {
    const tapsList = Array.isArray(taps) ? taps.join(",") : (taps === "*" ? "*" : null)

    RESTClient.get("/ethernet/socks/tunnels/active/histogram", {
        filters: filters,
        time_range: timeRange,
        taps: tapsList,
      }, (response) => setHistogram(response.data)
    )
  }


  getTopClientsHistogram(setHistogram, organizationId, tenantId, timeRange, orderColumn, orderDirection, limit, offset, filters, taps) {
    const tapsList = Array.isArray(taps) ? taps.join(",") : (taps === "*" ? "*" : null)

    RESTClient.get("/ethernet/socks/tunnels/clients/top/histogram", {
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

    RESTClient.get("/ethernet/socks/tunnels/servers/top/histogram", {
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