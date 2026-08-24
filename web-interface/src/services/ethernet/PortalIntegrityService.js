import RESTClient from '../../util/RESTClient'

export default class PortalIntegrityService {

  findAllReports(organizationId, tenantId, timeRange, filters, orderColumn, orderDirection, taps, limit, offset, setReports) {
    const tapsList = Array.isArray(taps) ? taps.join(",") : (taps === "*" ? "*" : null)

    RESTClient.get("/ethernet/portalintegrity/reports", { organization_id: organizationId, tenant_id: tenantId, time_range: timeRange, filters: filters, order_column: orderColumn, order_direction: orderDirection, taps: tapsList, limit: limit, offset: offset },
      (response) => setReports(response.data)
    )
  }

}