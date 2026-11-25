import RESTClient from "../../util/RESTClient";

const tapsParameter = (taps) => Array.isArray(taps) ? taps.join(",") : (taps === "*" ? "*" : null);

export default class NdpService {

  findAllMessages(organizationId, tenantId, timeRange, filters, orderColumn, orderDirection, taps, limit, offset, setMessages) {
    RESTClient.get("/ethernet/ndp/messages", {
          organization_id: organizationId, tenant_id: tenantId, time_range: timeRange, filters: filters,
          order_column: orderColumn, order_direction: orderDirection, taps: tapsParameter(taps), limit: limit, offset: offset
        },
        (response) => setMessages(response.data)
    )
  }

  getStatistics(timeRange, filters, taps, setStatistics) {
    RESTClient.get("/ethernet/ndp/messages/statistics",
        { time_range: timeRange, filters: filters, taps: tapsParameter(taps) },
        (response) => setStatistics(response.data)
    )
  }

  findAllRouterAdvertisements(organizationId, tenantId, timeRange, filters, orderColumn, orderDirection, taps, limit, offset, setRouterAdvertisements) {
    RESTClient.get("/ethernet/ndp/router-advertisements", {
          organization_id: organizationId, tenant_id: tenantId, time_range: timeRange, filters: filters,
          order_column: orderColumn, order_direction: orderDirection, taps: tapsParameter(taps), limit: limit, offset: offset
        },
        (response) => setRouterAdvertisements(response.data)
    )
  }

}
