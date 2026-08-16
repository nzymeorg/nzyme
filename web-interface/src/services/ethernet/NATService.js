import RESTClient from '../../util/RESTClient'

export default class NATService {

  findOneSTUNDiscovery(id, organizationId, tenantId, taps, setDiscovery) {
    const tapsList = Array.isArray(taps) ? taps.join(",") : (taps === "*" ? "*" : null)

    RESTClient.get(`/ethernet/nat/traversal/stun/discoveries/show/${id}`, { organization_id: organizationId, tenant_id: tenantId, taps: tapsList },
      (response) => setDiscovery(response.data)
    )
  }

  findAllSTUNDiscoveries(organizationId, tenantId, timeRange, filters, orderColumn, orderDirection, taps, limit, offset, setDiscoveries) {
    const tapsList = Array.isArray(taps) ? taps.join(",") : (taps === "*" ? "*" : null)

    RESTClient.get("/ethernet/nat/traversal/stun/discoveries", { organization_id: organizationId, tenant_id: tenantId, time_range: timeRange, filters: filters, order_column: orderColumn, order_direction: orderDirection, taps: tapsList, limit: limit, offset: offset },
        (response) => setDiscoveries(response.data)
    )
  }

  getSTUNDiscoveriesHistogram(timeRange, filters, taps, setHistogram) {
    const tapsList = Array.isArray(taps) ? taps.join(",") : (taps === "*" ? "*" : null)

    RESTClient.get("/ethernet/nat/traversal/stun/discoveries/histogram", {
        time_range: timeRange,
        filters: filters,
        taps: tapsList
      },
      (response) => setHistogram(response.data)
    )
  }

  getSTUNTopClientsHistogram(organizationId, tenantId, timeRange, filters, taps, limit, offset, setHistogram) {
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

  getSTUNTopServersHistogram(organizationId, tenantId, timeRange, filters, taps, limit, offset, setHistogram) {
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

  findOneSTUNConnection(id, organizationId, tenantId, taps, setConnection) {
    const tapsList = Array.isArray(taps) ? taps.join(",") : (taps === "*" ? "*" : null)

    RESTClient.get(`/ethernet/nat/traversal/stun/connections/show/${id}`, { organization_id: organizationId, tenant_id: tenantId, taps: tapsList },
      (response) => setConnection(response.data)
    )
  }

  findAllSTUNConnections(organizationId, tenantId, timeRange, filters, orderColumn, orderDirection, taps, limit, offset, setNegotiations) {
    const tapsList = Array.isArray(taps) ? taps.join(",") : (taps === "*" ? "*" : null)

    RESTClient.get("/ethernet/nat/traversal/stun/connections", { organization_id: organizationId, tenant_id: tenantId, time_range: timeRange, filters: filters, order_column: orderColumn, order_direction: orderDirection, taps: tapsList, limit: limit, offset: offset },
      (response) => setNegotiations(response.data)
    )
  }

}