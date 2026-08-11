import RESTClient from '../../util/RESTClient'

export default class NATService {

  findAllTraversalDiscoveries(organizationId, tenantId, timeRange, filters, orderColumn, orderDirection, taps, limit, offset, setDiscoveries) {
    const tapsList = Array.isArray(taps) ? taps.join(",") : (taps === "*" ? "*" : null)

    RESTClient.get("/ethernet/nat/traversal/discoveries", { organization_id: organizationId, tenant_id: tenantId, time_range: timeRange, filters: filters, order_column: orderColumn, order_direction: orderDirection, taps: tapsList, limit: limit, offset: offset },
        (response) => setDiscoveries(response.data)
    )
  }

}