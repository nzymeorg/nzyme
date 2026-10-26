import RESTClient from "../../util/RESTClient";

export default class BeaconsService {

  findAllBeacons(organizationId, tenantId, filters, timeRange, orderColumn, orderDirection, limit, offset, setBeacons) {
    RESTClient.get("/ethernet/beacons", {
      organization_id: organizationId, tenant_id: tenantId, filters: filters, time_range: timeRange,
      order_column: orderColumn, order_direction: orderDirection, limit: limit, offset: offset
    }, (response) => setBeacons(response.data));
  }

  findBeacon(uuid, organizationId, tenantId, setBeacon, onError = undefined) {
    RESTClient.get(`/ethernet/beacons/show/${uuid}`, {organization_id: organizationId, tenant_id: tenantId},
      (response) => setBeacon(response.data), onError);
  }

  findBeaconSources(uuid, organizationId, tenantId, timeRange, orderColumn, orderDirection, limit, offset, setSources) {
    RESTClient.get(`/ethernet/beacons/show/${uuid}/sources`, {
      organization_id: organizationId, tenant_id: tenantId, time_range: timeRange, order_column: orderColumn,
      order_direction: orderDirection, limit: limit, offset: offset
    }, (response) => setSources(response.data));
  }

  findActiveBeaconsHistogram(organizationId, tenantId, filters, timeRange, setHistogram) {
    RESTClient.get("/ethernet/beacons/histogram/active", {
      organization_id: organizationId, tenant_id: tenantId, filters: filters, time_range: timeRange
    }, (response) => setHistogram(response.data));
  }

  findBeaconCheckinHistogram(uuid, organizationId, tenantId, timeRange, sourceUuid, setHistogram) {
    RESTClient.get(`/ethernet/beacons/show/${uuid}/checkins/histogram`, {
      organization_id: organizationId, tenant_id: tenantId, time_range: timeRange, source_uuid: sourceUuid
    }, (response) => setHistogram(response.data));
  }

  findBeaconCheckinBytesHistogram(uuid, organizationId, tenantId, timeRange, sourceUuid, setHistogram) {
    RESTClient.get(`/ethernet/beacons/show/${uuid}/checkins/histogram/bytes`, {
      organization_id: organizationId, tenant_id: tenantId, time_range: timeRange, source_uuid: sourceUuid
    }, (response) => setHistogram(response.data));
  }

  findBeaconDestinationConnections(uuid, organizationId, tenantId, timeRange, setConnections) {
    RESTClient.get(`/ethernet/beacons/show/${uuid}/destination/connections`, {
      organization_id: organizationId, tenant_id: tenantId, time_range: timeRange
    }, (response) => setConnections(response.data));
  }

  findBeaconCheckinPoints(uuid, organizationId, tenantId, timeRange, filters, setPoints) {
    RESTClient.get(`/ethernet/beacons/show/${uuid}/checkins/points`, {
      organization_id: organizationId, tenant_id: tenantId, time_range: timeRange, filters: filters
    }, (response) => setPoints(response.data));
  }

  findBeaconNotes(uuid, organizationId, tenantId, setNotes) {
    RESTClient.get(`/ethernet/beacons/show/${uuid}/notes`, {organization_id: organizationId, tenant_id: tenantId},
      (response) => setNotes(response.data));
  }

  createBeaconNote(uuid, organizationId, tenantId, note, successCallback, errorCallback) {
    RESTClient.post(`/ethernet/beacons/organization/${organizationId}/tenant/${tenantId}/show/${uuid}/notes`,
      {note: note}, successCallback, errorCallback);
  }

  deleteBeaconNote(uuid, noteUuid, organizationId, tenantId, successCallback) {
    RESTClient.delete(`/ethernet/beacons/organization/${organizationId}/tenant/${tenantId}/show/${uuid}/notes/${noteUuid}`,
      successCallback);
  }

  findBeaconCheckins(uuid, organizationId, tenantId, timeRange, filters, limit, offset, setCheckins) {
    RESTClient.get(`/ethernet/beacons/show/${uuid}/checkins`, {
      organization_id: organizationId, tenant_id: tenantId, time_range: timeRange, filters: filters,
      limit: limit, offset: offset
    }, (response) => setCheckins(response.data));
  }

  updateBeacon(uuid, organizationId, tenantId, name, description, successCallback, errorCallback) {
    RESTClient.put(`/ethernet/beacons/organization/${organizationId}/tenant/${tenantId}/show/${uuid}`,
      {name: name, description: description}, successCallback, errorCallback);
  }

  classifyBeacon(uuid, organizationId, tenantId, classification, successCallback, errorCallback) {
    RESTClient.put(`/ethernet/beacons/organization/${organizationId}/tenant/${tenantId}/show/${uuid}/classify/${classification}`,
      {}, successCallback, errorCallback);
  }

  classifyBeacons(organizationId, tenantId, filters, timeRange, classification, successCallback, errorCallback) {
    RESTClient.put(`/ethernet/beacons/organization/${organizationId}/tenant/${tenantId}/classify/${classification}`
      + "?filters=" + encodeURIComponent(JSON.stringify(filters ? filters : {}))
      + "&time_range=" + encodeURIComponent(JSON.stringify(timeRange)), {}, successCallback, errorCallback);
  }

  deleteAllBeacons(organizationId, tenantId, successCallback) {
    RESTClient.delete(`/ethernet/beacons/organization/${organizationId}/tenant/${tenantId}`, successCallback);
  }

  deleteBeacon(uuid, organizationId, tenantId, successCallback) {
    RESTClient.delete(`/ethernet/beacons/organization/${organizationId}/tenant/${tenantId}/show/${uuid}`,
      successCallback);
  }

  findAllExclusions(organizationId, tenantId, filters, orderColumn, orderDirection, limit, offset, setExclusions) {
    RESTClient.get("/ethernet/beacons/exclusions", {
      organization_id: organizationId, tenant_id: tenantId, filters: filters, order_column: orderColumn,
      order_direction: orderDirection, limit: limit, offset: offset
    }, (response) => setExclusions(response.data));
  }

  findExclusion(uuid, organizationId, tenantId, setExclusion, onError = undefined) {
    RESTClient.get(`/ethernet/beacons/exclusions/show/${uuid}`, {organization_id: organizationId, tenant_id: tenantId},
      (response) => setExclusion(response.data), onError);
  }

  previewExclusion(organizationId, tenantId, exclusion, setImpact, errorCallback) {
    RESTClient.post(`/ethernet/beacons/exclusions/organization/${organizationId}/tenant/${tenantId}/preview`,
      exclusion, (response) => setImpact(response.data), errorCallback);
  }

  createExclusion(organizationId, tenantId, exclusion, successCallback, errorCallback) {
    RESTClient.post(`/ethernet/beacons/exclusions/organization/${organizationId}/tenant/${tenantId}`,
      exclusion, successCallback, errorCallback);
  }

  updateExclusion(uuid, organizationId, tenantId, exclusion, successCallback, errorCallback) {
    RESTClient.put(`/ethernet/beacons/exclusions/organization/${organizationId}/tenant/${tenantId}/show/${uuid}`,
      exclusion, successCallback, errorCallback);
  }

  setExclusionEnabled(uuid, organizationId, tenantId, enabled, successCallback) {
    RESTClient.put(`/ethernet/beacons/exclusions/organization/${organizationId}/tenant/${tenantId}/show/${uuid}/enabled/${enabled}`,
      {}, successCallback);
  }

  applyExclusion(uuid, organizationId, tenantId, successCallback) {
    RESTClient.post(`/ethernet/beacons/exclusions/organization/${organizationId}/tenant/${tenantId}/show/${uuid}/apply`,
      {}, successCallback);
  }

  deleteExclusion(uuid, organizationId, tenantId, successCallback) {
    RESTClient.delete(`/ethernet/beacons/exclusions/organization/${organizationId}/tenant/${tenantId}/show/${uuid}`,
      successCallback);
  }

  getConfiguration(organizationId, tenantId, setConfiguration) {
    RESTClient.get(`/ethernet/beacons/organization/${organizationId}/tenant/${tenantId}/configuration`, {},
      (response) => setConfiguration(response.data));
  }

  updateConfiguration(newConfig, organizationId, tenantId, successCallback, errorCallback) {
    RESTClient.put(`/ethernet/beacons/organization/${organizationId}/tenant/${tenantId}/configuration`,
      {change: newConfig}, successCallback, errorCallback);
  }

}
