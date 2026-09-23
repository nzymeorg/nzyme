import RESTClient from '../../util/RESTClient'

export default class WebRTCService {

  findAllSessions(organizationId, tenantId, timeRange, filters, orderColumn, orderDirection, taps, limit, offset, setSessions) {
    const tapsList = Array.isArray(taps) ? taps.join(",") : (taps === "*" ? "*" : null)

    RESTClient.get("/ethernet/webrtc/sessions", { organization_id: organizationId, tenant_id: tenantId, time_range: timeRange, filters: filters, order_column: orderColumn, order_direction: orderDirection, taps: tapsList, limit: limit, offset: offset },
      (response) => setSessions(response.data)
    )
  }

  findOneSessions(negotiationKeySha256, organizationId, tenantId, taps, setSession) {
    const tapsList = Array.isArray(taps) ? taps.join(",") : (taps === "*" ? "*" : null)

    RESTClient.get(`/ethernet/webrtc/sessions/show/${negotiationKeySha256}`, { organization_id: organizationId, tenant_id: tenantId, taps: tapsList },
      (response) => setSession(response.data)
    )
  }

  getActiveSessionCountHistogram(setHistogram, timeRange, filters, taps) {
    const tapsList = Array.isArray(taps) ? taps.join(",") : (taps === "*" ? "*" : null)

    RESTClient.get("/ethernet/webrtc/sessions/active/histogram", {
        filters: filters,
        time_range: timeRange,
        taps: tapsList,
      }, (response) => setHistogram(response.data)
    )
  }

  getTopPeerAddressPairHistogram(setHistogram, organizationId, tenantId, timeRange, orderColumn, orderDirection, limit, offset, filters, taps) {
    const tapsList = Array.isArray(taps) ? taps.join(",") : (taps === "*" ? "*" : null)

    RESTClient.get("/ethernet/webrtc/sessions/peers/addresses/top/histogram", {
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