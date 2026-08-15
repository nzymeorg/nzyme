import RESTClient from '../../util/RESTClient'

export default class NATService {

  findOneTraversalDiscovery(id, organizationId, tenantId, taps, setDiscovery) {
    const tapsList = Array.isArray(taps) ? taps.join(",") : (taps === "*" ? "*" : null)

    RESTClient.get(`/ethernet/nat/traversal/stun/discoveries/show/${id}`, { organization_id: organizationId, tenant_id: tenantId, taps: tapsList },
      (response) => setDiscovery(response.data)
    )
  }

  findAllTraversalDiscoveries(organizationId, tenantId, timeRange, filters, orderColumn, orderDirection, taps, limit, offset, setDiscoveries) {
    const tapsList = Array.isArray(taps) ? taps.join(",") : (taps === "*" ? "*" : null)

    RESTClient.get("/ethernet/nat/traversal/stun/discoveries", { organization_id: organizationId, tenant_id: tenantId, time_range: timeRange, filters: filters, order_column: orderColumn, order_direction: orderDirection, taps: tapsList, limit: limit, offset: offset },
        (response) => setDiscoveries(response.data)
    )
  }

  getTraversalDiscoveriesHistogram(timeRange, filters, taps, setHistogram) {
    const tapsList = Array.isArray(taps) ? taps.join(",") : (taps === "*" ? "*" : null)

    RESTClient.get("/ethernet/nat/traversal/stun/discoveries/histogram", {
        time_range: timeRange,
        filters: filters,
        taps: tapsList
      },
      (response) => setHistogram(response.data)
    )
  }

  getTraversalTopClientsHistogram(organizationId, tenantId, timeRange, filters, taps, limit, offset, setHistogram) {
    const tapsList = Array.isArray(taps) ? taps.join(",") : (taps === "*" ? "*" : null)

    RESTClient.get("/ethernet/nat/traversal/stun/clients/histogram", {
        organization_id: organizationId,
        tenant_id: tenantId,
        limit: limit,
        offset: offset,
        time_range: timeRange,
        filters: filters,
        taps: tapsList
      },
      (response) => setHistogram(response.data)
    )
  }

  getTraversalTopServersHistogram(organizationId, tenantId, timeRange, filters, taps, limit, offset, setHistogram) {
    const tapsList = Array.isArray(taps) ? taps.join(",") : (taps === "*" ? "*" : null)

    RESTClient.get("/ethernet/nat/traversal/stun/servers/histogram", {
        organization_id: organizationId,
        tenant_id: tenantId,
        limit: limit,
        offset: offset,
        time_range: timeRange,
        filters: filters,
        taps: tapsList
      },
      (response) => setHistogram(response.data)
    )
  }

  findAllSTUNNegotiations(organizationId, tenantId, timeRange, filters, orderColumn, orderDirection, taps, limit, offset, setNegotiations) {
    const tapsList = Array.isArray(taps) ? taps.join(",") : (taps === "*" ? "*" : null)

    RESTClient.get("/ethernet/nat/traversal/stun/negotiations", { organization_id: organizationId, tenant_id: tenantId, time_range: timeRange, filters: filters, order_column: orderColumn, order_direction: orderDirection, taps: tapsList, limit: limit, offset: offset },
      (response) => setNegotiations(response.data)
    )
  }

}