import RESTClient from '../util/RESTClient'

class TapService {

  findAllTapsHighLevel(organizationId, tenantId, successCallback, errorCallback = undefined, signal = undefined) {
    RESTClient.get('/taps/highlevel', { organization_id: organizationId, tenant_id: tenantId }, successCallback, errorCallback, signal);
  }

  findTapHighLevel(uuid, organizationId, tenantId, setTap) {
    RESTClient.get(`/taps/show/${uuid}/highlevel`, { organization_id: organizationId, tenant_id: tenantId }, function (response) {
      setTap(response.data)
    })
  }

  findAllTaps(organizationId, tenantId, setTaps) {
    RESTClient.get('/taps', { organization_id: organizationId, tenant_id: tenantId }, function (response) {
      setTaps(response.data.taps)
    })
  }

  findTap(uuid, setTap) {
    RESTClient.get('/taps/show/' + uuid, {}, function (response) {
      setTap(response.data)
    })
  }

  findMetricsOfTap(uuid, setTapMetrics) {
    RESTClient.get('/taps/show/' + uuid + '/metrics', {}, function (response) {
      setTapMetrics(response.data)
    })
  }

  findGaugeMetricHistogramOfTap(uuid, metricName, setTapMetric) {
    RESTClient.get('/taps/show/' + uuid + '/metrics/gauges/' + metricName + '/histogram', {}, function (response) {
      setTapMetric(response.data)
    })
  }

  findTimerMetricHistogramOfTap(uuid, metricName, setTapMetric) {
    RESTClient.get('/taps/show/' + uuid + '/metrics/timers/' + metricName + '/histogram', {}, function (response) {
      setTapMetric(response.data)
    })
  }

  findEngagementLogOfTap(uuid, limit, offset, setLogs) {
    RESTClient.get('/taps/show/' + uuid + '/engagement/logs', { limit: limit, offset: offset }, function (response) {
      setLogs(response.data)
    })
  }

}

export default TapService
