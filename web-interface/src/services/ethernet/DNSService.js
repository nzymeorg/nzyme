import RESTClient from '../../util/RESTClient'

export default class DNSService {

  getGlobalStatisticsValue(timeRange, taps, type, setValue) {
    const tapsList = Array.isArray(taps) ? taps.join(",") : (taps === "*" ? "*" : null)

    RESTClient.get("/ethernet/dns/global/statistics/" + type, { time_range: timeRange, taps: tapsList },
        (response) => setValue(response.data.value)
    )
  }

  getGlobalChart(timeRange, taps, type, setValue) {
    const tapsList = Array.isArray(taps) ? taps.join(",") : (taps === "*" ? "*" : null)

    RESTClient.get("/ethernet/dns/global/charts/" + type, {time_range: timeRange, taps: tapsList},
        (response) => setValue(response.data)
    )
  }

  getGlobalPairs(organizationId, tenantId, timeRange, taps, limit, offset, setPairs) {
    const tapsList = Array.isArray(taps) ? taps.join(",") : (taps === "*" ? "*" : null)

    RESTClient.get("/ethernet/dns/global/pairs", { organization_id: organizationId, tenant_id: tenantId, time_range: timeRange, taps: tapsList, limit: limit, offset: offset },
        (response) => setPairs(response.data)
    )
  }

  findAllTransactions(organizationId, tenantId, timeRange, filters, taps, limit, offset, setTransactions) {
    const tapsList = Array.isArray(taps) ? taps.join(",") : (taps === "*" ? "*" : null)

    RESTClient.get("/ethernet/dns/transactions/log", { organization_id: organizationId, tenant_id: tenantId, time_range: timeRange, filters: filters, taps: tapsList, limit: limit, offset: offset },
        (response) => setTransactions(response.data)
    )
  }

  findResponsesOfTransaction(organizationId, tenantId, transactionId, transactionTimestamp, taps, setResponses) {
    const tapsList = Array.isArray(taps) ? taps.join(",") : (taps === "*" ? "*" : null)

    RESTClient.get(`/ethernet/dns/transactions/log/${transactionId}/responses`,
        { organization_id: organizationId, tenant_id: tenantId, taps: tapsList, transaction_timestamp: transactionTimestamp},
        (response) => setResponses(response.data)
    )
  }

  getTransactionCountChart(timeRange, filters, taps, setHistogram) {
    const tapsList = Array.isArray(taps) ? taps.join(",") : (taps === "*" ? "*" : null)

    RESTClient.get("/ethernet/dns/transactions/charts/count", { time_range: timeRange, filters: filters, taps: tapsList },
        (response) => setHistogram(response.data)
    )
  }

}