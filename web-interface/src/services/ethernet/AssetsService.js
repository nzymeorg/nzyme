import RESTClient from "../../util/RESTClient";

export default class AssetsService {

  findAllAssets(organizationId, tenantId, timeRange, orderColumn, orderDirection, filters, limit, offset, setAssets) {
    RESTClient.get("/ethernet/assets", {
      organization_id: organizationId, tenant_id: tenantId, time_range: timeRange, order_column: orderColumn, order_direction: orderDirection, filters: filters, limit: limit, offset: offset },
        (response) => setAssets(response.data)
    )
  }

  getActiveAssetsHistogram(organizationId, tenantId, timeRange, setHistogram) {
    RESTClient.get("/ethernet/assets/active/histogram", {
      organization_id: organizationId, tenant_id: tenantId, time_range: timeRange },
      (response) => setHistogram(response.data)
    )
  }

  getLatestAssetsHistogram(organizationId, tenantId, timeRange, filters, limit, offset, setHistogram) {
    RESTClient.get("/ethernet/assets/latest/histogram", {
      organization_id: organizationId, tenant_id: tenantId, time_range: timeRange, filters: filters, limit: limit, offset: offset },
      (response) => setHistogram(response.data)
    )
  }

  getRecentlyDisappearedAssetsHistogram(organizationId, tenantId, timeRange, filters, limit, offset, setHistogram) {
    RESTClient.get("/ethernet/assets/disappeared/histogram", {
      organization_id: organizationId, tenant_id: tenantId, time_range: timeRange, filters: filters, limit: limit, offset: offset },
      (response) => setHistogram(response.data)
    )
  }

  findAsset(uuid, organizationId, tenantId, setAsset) {
    RESTClient.get(`/ethernet/assets/show/${uuid}`, { organization_id: organizationId, tenant_id: tenantId },
        (response) => setAsset(response.data)
    )
  }

  findAssetHostnames(assetId, organizationId, tenantId, timeRange, orderColumn, orderDirection, limit, offset, setHostnames) {
    RESTClient.get(`/ethernet/assets/show/${assetId}/hostnames`, {
      organization_id: organizationId, tenant_id: tenantId, time_range: timeRange, order_column: orderColumn, order_direction: orderDirection, limit: limit, offset: offset },
        (response) => setHostnames(response.data)
    )
  }

  deleteAssetHostname(hostnameId, assetId, organizationId, tenantId, onSuccess) {
    RESTClient.delete(`/ethernet/assets/show/${assetId}/hostnames/${hostnameId}/organization/${organizationId}/tenant/${tenantId}`, onSuccess)
  }

  findAssetIpAddresses(assetId, organizationId, tenantId, timeRange, orderColumn, orderDirection, limit, offset, setIpAddresses) {
    RESTClient.get(`/ethernet/assets/show/${assetId}/ip_addresses`, {
      organization_id: organizationId, tenant_id: tenantId, time_range: timeRange, order_column: orderColumn, order_direction: orderDirection, limit: limit, offset: offset },
        (response) => setIpAddresses(response.data)
    )
  }

  deleteAssetIpAddress(addressId, assetId, organizationId, tenantId, onSuccess) {
    RESTClient.delete(`/ethernet/assets/show/${assetId}/ip_addresses/${addressId}/organization/${organizationId}/tenant/${tenantId}`, onSuccess)
  }

  findAllDHCPTransactions(organizationId, tenantId, timeRange, orderColumn, orderDirection, filters, taps, limit, offset, setTransactions) {
    const tapsList = Array.isArray(taps) ? taps.join(",") : (taps === "*" ? "*" : null)

    RESTClient.get("/ethernet/dhcp/transactions", {
      organization_id: organizationId, tenant_id: tenantId, time_range: timeRange, order_column: orderColumn, order_direction: orderDirection, filters: filters, taps: tapsList, limit: limit, offset: offset },
        (response) => setTransactions(response.data)
    )
  }

  getDHCPStatistics(timeRange, filters, taps, setStatistics) {
    const tapsList = Array.isArray(taps) ? taps.join(",") : (taps === "*" ? "*" : null)

    RESTClient.get("/ethernet/dhcp/transactions/statistics",
        { time_range: timeRange, filters: filters, taps: tapsList },
        (response) => setStatistics(response.data)
    )
  }

  findDHCPTransaction(organizationId, tenantId, transactionId, transactionTime, taps, setTransaction) {
    const tapsList = Array.isArray(taps) ? taps.join(",") : (taps === "*" ? "*" : null)

    RESTClient.get(`/ethernet/dhcp/transactions/show/${transactionId}`, {
      organization_id: organizationId, tenant_id: tenantId, transaction_time: transactionTime, taps: tapsList },
        (response) => setTransaction(response.data)
    )
  }

  findAllArpPackets(organizationId, tenantId, timeRange, filters, orderColumn, orderDirection, taps, limit, offset, setPackets) {
    const tapsList = Array.isArray(taps) ? taps.join(",") : (taps === "*" ? "*" : null)

    RESTClient.get("/ethernet/arp/packets", { organization_id: organizationId, tenant_id: tenantId, time_range: timeRange, filters: filters, order_column: orderColumn, order_direction: orderDirection, taps: tapsList, limit: limit, offset: offset },
        (response) => setPackets(response.data)
    )
  }

  getArpStatistics(timeRange, filters, taps, setStatistics) {
    const tapsList = Array.isArray(taps) ? taps.join(",") : (taps === "*" ? "*" : null)

    RESTClient.get("/ethernet/arp/statistics", { time_range: timeRange, filters: filters, taps: tapsList },
        (response) => setStatistics(response.data)
    )
  }

  getArpRequesterPairs(organizationId, tenantId, timeRange, orderColumn, orderDirection, filters, limit, offset, taps, setRequesterPairs) {
    const tapsList = Array.isArray(taps) ? taps.join(",") : (taps === "*" ? "*" : null)

    RESTClient.get("/ethernet/arp/histograms/requesters/pairs", {
      organization_id: organizationId, tenant_id: tenantId, time_range: timeRange, order_column: orderColumn, order_direction: orderDirection, filters: filters, limit: limit, offset: offset, taps: tapsList },
        (response) => setRequesterPairs(response.data)
    )
  }

  getArpResponderPairs(organizationId, tenantId, timeRange, orderColumn, orderDirection, filters, limit, offset, taps, setRequesterPairs) {
    const tapsList = Array.isArray(taps) ? taps.join(",") : (taps === "*" ? "*" : null)

    RESTClient.get("/ethernet/arp/histograms/responders/pairs", {
      organization_id: organizationId, tenant_id: tenantId, time_range: timeRange, order_column: orderColumn, order_direction: orderDirection, filters: filters, limit: limit, offset: offset, taps: tapsList },
        (response) => setRequesterPairs(response.data)
    )
  }

  getAssetsConfiguration(organizationId, tenantId, setConfiguration) {
    RESTClient.get(`/ethernet/assets/organization/${organizationId}/tenant/${tenantId}/configuration`, {},
      (response) => setConfiguration(response.data)
    )
  }

  updateAssetsConfiguration(newConfig, organizationId, tenantId, successCallback, errorCallback) {
    RESTClient.put(`/ethernet/assets/organization/${organizationId}/tenant/${tenantId}/configuration`,
      { change: newConfig }, successCallback, errorCallback)
  }

}