import RESTClient from '../../util/RESTClient'

export default class RTSPService {

  findAllStreams(organizationId, tenantId, timeRange, filters, orderColumn, orderDirection, taps, limit, offset, setStreams) {
    const tapsList = Array.isArray(taps) ? taps.join(",") : (taps === "*" ? "*" : null)

    RESTClient.get("/ethernet/rtsp/streams", { organization_id: organizationId, tenant_id: tenantId, time_range: timeRange, filters: filters, order_column: orderColumn, order_direction: orderDirection, taps: tapsList, limit: limit, offset: offset },
      (response) => setStreams(response.data)
    )
  }

  findOneStream(sessionId, organizationId, tenantId, taps, setStream) {
    const tapsList = Array.isArray(taps) ? taps.join(",") : (taps === "*" ? "*" : null)

    RESTClient.get(`/ethernet/rtsp/streams/show/${sessionId}`, { organization_id: organizationId, tenant_id: tenantId, taps: tapsList },
      (response) => setStream(response.data)
    )
  }

}