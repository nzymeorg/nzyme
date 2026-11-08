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

  getAssetProfileSummary(assetId, organizationId, tenantId, setSummary) {
    RESTClient.get(`/ethernet/assets/show/${assetId}/profile/summary`, { organization_id: organizationId, tenant_id: tenantId },
        (response) => setSummary(response.data)
    )
  }

  getAssetProfileAttributes(assetId, organizationId, tenantId, excludeDimensions, setAttributes) {
    RESTClient.get(`/ethernet/assets/show/${assetId}/profile/attributes`, {
      organization_id: organizationId, tenant_id: tenantId, exclude_dimensions: excludeDimensions.join(",") },
        (response) => setAttributes(response.data)
    )
  }

  getAssetProfileInboundPorts(assetId, organizationId, tenantId, orderBy, orderDirection, limit, offset, setPorts) {
    RESTClient.get(`/ethernet/assets/show/${assetId}/profile/inbound-ports`, {
      organization_id: organizationId, tenant_id: tenantId, order_by: orderBy, order_direction: orderDirection,
      limit: limit, offset: offset },
        (response) => setPorts(response.data)
    )
  }

  getAssetProfileActivity(assetId, organizationId, tenantId, days, timezoneOffset, setActivity) {
    RESTClient.get(`/ethernet/assets/show/${assetId}/profile/activity`, {
      organization_id: organizationId, tenant_id: tenantId, days: days, tz_offset: timezoneOffset },
        (response) => setActivity(response.data)
    )
  }

  getAssetProfileCommunication(assetId, organizationId, tenantId, timeRange, limit, setCommunication, onError = undefined) {
    RESTClient.get(`/ethernet/assets/show/${assetId}/profile/communication`, {
      organization_id: organizationId, tenant_id: tenantId, time_range: timeRange, limit: limit },
        (response) => setCommunication(response.data),
        onError
    )
  }

  getAssetProfileTimeline(assetId, organizationId, tenantId, timeRange, filters, orderBy, orderDirection, limit, offset, setTimeline) {
    RESTClient.get(`/ethernet/assets/show/${assetId}/profile/timeline`, {
      organization_id: organizationId, tenant_id: tenantId, time_range: timeRange, filters: filters,
      order_by: orderBy, order_direction: orderDirection, limit: limit, offset: offset },
        (response) => setTimeline(response.data)
    )
  }

  getAssetProfileTimelineHistogram(assetId, organizationId, tenantId, timeRange, filters, setHistogram) {
    RESTClient.get(`/ethernet/assets/show/${assetId}/profile/timeline/histogram`, {
      organization_id: organizationId, tenant_id: tenantId, time_range: timeRange, filters: filters },
        (response) => setHistogram(response.data)
    )
  }

  getAssetProfileAlerts(assetId, organizationId, tenantId, setAlerts) {
    RESTClient.get(`/ethernet/assets/show/${assetId}/profile/alerts`, { organization_id: organizationId, tenant_id: tenantId },
        (response) => setAlerts(response.data)
    )
  }

  getAssetProfileFindings(assetId, organizationId, tenantId, setFindings) {
    RESTClient.get(`/ethernet/assets/show/${assetId}/profile/findings`, { organization_id: organizationId, tenant_id: tenantId },
        (response) => setFindings(response.data)
    )
  }

  getAssetProfileExclusions(assetId, organizationId, tenantId, setExclusions) {
    RESTClient.get(`/ethernet/assets/show/${assetId}/profile/exclusions`, { organization_id: organizationId, tenant_id: tenantId },
        (response) => setExclusions(response.data)
    )
  }

  getAssetProfileRisks(assetId, organizationId, tenantId, timeRange, setRisks) {
    RESTClient.get(`/ethernet/assets/show/${assetId}/profile/risks`, {
      organization_id: organizationId, tenant_id: tenantId, time_range: timeRange },
        (response) => setRisks(response.data)
    )
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

  findDHCPFlakyFingerprintAssets(organizationId, tenantId, timeRange, taps, limit, offset, setAssets) {
    const tapsList = Array.isArray(taps) ? taps.join(",") : (taps === "*" ? "*" : null)

    RESTClient.get("/ethernet/dhcp/fingerprints/flaky", {
      organization_id: organizationId, tenant_id: tenantId, time_range: timeRange, taps: tapsList, limit: limit, offset: offset },
        (response) => setAssets(response.data)
    )
  }

  findAllDHCPFingerprintExclusions(organizationId, tenantId, filters, orderColumn, orderDirection, limit, offset, setExclusions) {
    RESTClient.get("/ethernet/dhcp/fingerprints/exclusions", {
      organization_id: organizationId, tenant_id: tenantId, filters: filters, order_column: orderColumn,
      order_direction: orderDirection, limit: limit, offset: offset
    }, (response) => setExclusions(response.data));
  }

  findDHCPFingerprintExclusion(uuid, organizationId, tenantId, setExclusion, onError = undefined) {
    RESTClient.get(`/ethernet/dhcp/fingerprints/exclusions/show/${uuid}`, {organization_id: organizationId, tenant_id: tenantId},
      (response) => setExclusion(response.data), onError);
  }

  previewDHCPFingerprintExclusion(organizationId, tenantId, exclusion, setImpact, errorCallback) {
    RESTClient.post(`/ethernet/dhcp/fingerprints/exclusions/organization/${organizationId}/tenant/${tenantId}/preview`,
      exclusion, (response) => setImpact(response.data), errorCallback);
  }

  createDHCPFingerprintExclusion(organizationId, tenantId, exclusion, successCallback, errorCallback) {
    RESTClient.post(`/ethernet/dhcp/fingerprints/exclusions/organization/${organizationId}/tenant/${tenantId}`,
      exclusion, successCallback, errorCallback);
  }

  updateDHCPFingerprintExclusion(uuid, organizationId, tenantId, exclusion, successCallback, errorCallback) {
    RESTClient.put(`/ethernet/dhcp/fingerprints/exclusions/organization/${organizationId}/tenant/${tenantId}/show/${uuid}`,
      exclusion, successCallback, errorCallback);
  }

  setDHCPFingerprintExclusionEnabled(uuid, organizationId, tenantId, enabled, successCallback) {
    RESTClient.put(`/ethernet/dhcp/fingerprints/exclusions/organization/${organizationId}/tenant/${tenantId}/show/${uuid}/enabled/${enabled}`,
      {}, successCallback);
  }

  deleteDHCPFingerprintExclusion(uuid, organizationId, tenantId, successCallback) {
    RESTClient.delete(`/ethernet/dhcp/fingerprints/exclusions/organization/${organizationId}/tenant/${tenantId}/show/${uuid}`,
      successCallback);
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

  getAssetIdentityConfiguration(organizationId, tenantId, setConfiguration) {
    RESTClient.get(`/ethernet/assets/identity/organization/${organizationId}/tenant/${tenantId}/configuration`, {},
      (response) => setConfiguration(response.data));
  }

  updateAssetIdentityConfiguration(newConfig, organizationId, tenantId, successCallback, errorCallback) {
    RESTClient.put(`/ethernet/assets/identity/organization/${organizationId}/tenant/${tenantId}/configuration`,
      {change: newConfig}, successCallback, errorCallback);
  }

  findAllDuplicateMacFindings(organizationId, tenantId, filters, timeRange, orderColumn, orderDirection, limit, offset, setFindings) {
    RESTClient.get("/ethernet/assets/identity/duplicates", {
      organization_id: organizationId, tenant_id: tenantId, filters: filters, time_range: timeRange,
      order_column: orderColumn, order_direction: orderDirection, limit: limit, offset: offset
    }, (response) => setFindings(response.data));
  }

  findDuplicateMacFinding(uuid, organizationId, tenantId, setFinding, onError = undefined) {
    RESTClient.get(`/ethernet/assets/identity/duplicates/show/${uuid}`, {organization_id: organizationId, tenant_id: tenantId},
      (response) => setFinding(response.data), onError);
  }

  deleteDuplicateMacFinding(uuid, organizationId, tenantId, successCallback) {
    RESTClient.delete(`/ethernet/assets/identity/duplicates/organization/${organizationId}/tenant/${tenantId}/show/${uuid}`,
      successCallback);
  }

  deleteDuplicateMacFindings(uuids, organizationId, tenantId, successCallback) {
    RESTClient.put(`/ethernet/assets/identity/duplicates/organization/${organizationId}/tenant/${tenantId}/many/delete`,
      {uuids: uuids}, successCallback);
  }

  deleteFilteredDuplicateMacFindings(organizationId, tenantId, filters, timeRange, successCallback) {
    RESTClient.put(`/ethernet/assets/identity/duplicates/organization/${organizationId}/tenant/${tenantId}/filtered/delete?`
      + this.identityFilteredQuery(filters, timeRange), {}, successCallback);
  }

  findAllIdentityChangeFindings(organizationId, tenantId, filters, timeRange, orderColumn, orderDirection, limit, offset, setFindings) {
    RESTClient.get("/ethernet/assets/identity/changes", {
      organization_id: organizationId, tenant_id: tenantId, filters: filters, time_range: timeRange,
      order_column: orderColumn, order_direction: orderDirection, limit: limit, offset: offset
    }, (response) => setFindings(response.data));
  }

  findIdentityChangeFinding(uuid, organizationId, tenantId, setFinding, onError = undefined) {
    RESTClient.get(`/ethernet/assets/identity/changes/show/${uuid}`, {organization_id: organizationId, tenant_id: tenantId},
      (response) => setFinding(response.data), onError);
  }

  deleteIdentityChangeFinding(uuid, organizationId, tenantId, successCallback) {
    RESTClient.delete(`/ethernet/assets/identity/changes/organization/${organizationId}/tenant/${tenantId}/show/${uuid}`,
      successCallback);
  }

  deleteIdentityChangeFindings(uuids, organizationId, tenantId, successCallback) {
    RESTClient.put(`/ethernet/assets/identity/changes/organization/${organizationId}/tenant/${tenantId}/many/delete`,
      {uuids: uuids}, successCallback);
  }

  deleteFilteredIdentityChangeFindings(organizationId, tenantId, filters, timeRange, successCallback) {
    RESTClient.put(`/ethernet/assets/identity/changes/organization/${organizationId}/tenant/${tenantId}/filtered/delete?`
      + this.identityFilteredQuery(filters, timeRange), {}, successCallback);
  }

  findAllAssetLinks(organizationId, tenantId, filters, timeRange, orderColumn, orderDirection, limit, offset, setLinks) {
    RESTClient.get("/ethernet/assets/identity/links", {
      organization_id: organizationId, tenant_id: tenantId, filters: filters, time_range: timeRange,
      order_column: orderColumn, order_direction: orderDirection, limit: limit, offset: offset
    }, (response) => setLinks(response.data));
  }

  findAssetLink(uuid, organizationId, tenantId, setLink, onError = undefined) {
    RESTClient.get(`/ethernet/assets/identity/links/show/${uuid}`, {organization_id: organizationId, tenant_id: tenantId},
      (response) => setLink(response.data), onError);
  }

  setAssetLinkStatus(uuid, organizationId, tenantId, status, successCallback) {
    RESTClient.put(`/ethernet/assets/identity/links/organization/${organizationId}/tenant/${tenantId}/show/${uuid}/status/${status}`,
      {}, successCallback);
  }

  deleteAssetLink(uuid, organizationId, tenantId, successCallback) {
    RESTClient.delete(`/ethernet/assets/identity/links/organization/${organizationId}/tenant/${tenantId}/show/${uuid}`,
      successCallback);
  }

  setAssetLinksStatus(uuids, organizationId, tenantId, status, successCallback) {
    RESTClient.put(`/ethernet/assets/identity/links/organization/${organizationId}/tenant/${tenantId}/many/status/${status}`,
      {uuids: uuids}, successCallback);
  }

  setFilteredAssetLinksStatus(organizationId, tenantId, filters, timeRange, status, successCallback) {
    RESTClient.put(`/ethernet/assets/identity/links/organization/${organizationId}/tenant/${tenantId}/filtered/status/${status}?`
      + this.identityFilteredQuery(filters, timeRange), {}, successCallback);
  }

  deleteAssetLinks(uuids, organizationId, tenantId, successCallback) {
    RESTClient.put(`/ethernet/assets/identity/links/organization/${organizationId}/tenant/${tenantId}/many/delete`,
      {uuids: uuids}, successCallback);
  }

  deleteFilteredAssetLinks(organizationId, tenantId, filters, timeRange, successCallback) {
    RESTClient.put(`/ethernet/assets/identity/links/organization/${organizationId}/tenant/${tenantId}/filtered/delete?`
      + this.identityFilteredQuery(filters, timeRange), {}, successCallback);
  }

  identityFilteredQuery(filters, timeRange) {
    const params = new URLSearchParams();
    params.set("time_range", JSON.stringify(timeRange));
    if (filters) {
      params.set("filters", JSON.stringify(filters));
    }

    return params.toString();
  }

  getAssetIdentitySummary(assetId, organizationId, tenantId, setSummary) {
    RESTClient.get(`/ethernet/assets/identity/asset/${assetId}`, {organization_id: organizationId, tenant_id: tenantId},
      (response) => setSummary(response.data));
  }

  findAllAssetIdentityExclusions(organizationId, tenantId, filters, orderColumn, orderDirection, limit, offset, setExclusions) {
    RESTClient.get("/ethernet/assets/identity/exclusions", {
      organization_id: organizationId, tenant_id: tenantId, filters: filters, order_column: orderColumn,
      order_direction: orderDirection, limit: limit, offset: offset
    }, (response) => setExclusions(response.data));
  }

  findAssetIdentityExclusion(uuid, organizationId, tenantId, setExclusion, onError = undefined) {
    RESTClient.get(`/ethernet/assets/identity/exclusions/show/${uuid}`, {organization_id: organizationId, tenant_id: tenantId},
      (response) => setExclusion(response.data), onError);
  }

  previewAssetIdentityExclusion(organizationId, tenantId, exclusion, setImpact, errorCallback) {
    RESTClient.post(`/ethernet/assets/identity/exclusions/organization/${organizationId}/tenant/${tenantId}/preview`,
      exclusion, (response) => setImpact(response.data), errorCallback);
  }

  createAssetIdentityExclusion(organizationId, tenantId, exclusion, successCallback, errorCallback) {
    RESTClient.post(`/ethernet/assets/identity/exclusions/organization/${organizationId}/tenant/${tenantId}`,
      exclusion, successCallback, errorCallback);
  }

  updateAssetIdentityExclusion(uuid, organizationId, tenantId, exclusion, successCallback, errorCallback) {
    RESTClient.put(`/ethernet/assets/identity/exclusions/organization/${organizationId}/tenant/${tenantId}/show/${uuid}`,
      exclusion, successCallback, errorCallback);
  }

  setAssetIdentityExclusionEnabled(uuid, organizationId, tenantId, enabled, successCallback) {
    RESTClient.put(`/ethernet/assets/identity/exclusions/organization/${organizationId}/tenant/${tenantId}/show/${uuid}/enabled/${enabled}`,
      {}, successCallback);
  }

  deleteAssetIdentityExclusion(uuid, organizationId, tenantId, successCallback) {
    RESTClient.delete(`/ethernet/assets/identity/exclusions/organization/${organizationId}/tenant/${tenantId}/show/${uuid}`,
      successCallback);
  }

}
