import RESTClient from "../../util/RESTClient";
import {filtersApplied} from "../../util/Tools";

const tenant = (organizationId, tenantId) => ({organization_id: organizationId, tenant_id: tenantId});
const scoped = (organizationId, tenantId) => `/ethernet/segmentation/organization/${organizationId}/tenant/${tenantId}`;

// Without filters, every row is meant.
const filteredQuery = (filters, timeRange = null, policy = null) => {
  const params = new URLSearchParams();

  if (filtersApplied(filters)) {
    params.set("filters", JSON.stringify(filters));
  }

  if (timeRange) {
    params.set("time_range", JSON.stringify(timeRange));
  }

  if (policy) {
    params.set("policy", policy);
  }

  const query = params.toString();
  return query ? "?" + query : "";
};

export default class SegmentationService {

  getStatus(organizationId, tenantId, setStatus) {
    RESTClient.get("/ethernet/segmentation/status", tenant(organizationId, tenantId),
      (response) => setStatus(response.data));
  }

  getConfiguration(organizationId, tenantId, setConfiguration) {
    RESTClient.get(`${scoped(organizationId, tenantId)}/configuration`, {},
      (response) => setConfiguration(response.data));
  }

  updateConfiguration(newConfig, organizationId, tenantId, successCallback, errorCallback) {
    RESTClient.put(`${scoped(organizationId, tenantId)}/configuration`, {change: newConfig},
      successCallback, errorCallback);
  }

  findAllSegments(organizationId, tenantId, filters, orderColumn, orderDirection, limit, offset, setSegments,
                  onError = undefined) {
    RESTClient.get("/ethernet/segmentation/segments", {
      ...tenant(organizationId, tenantId), filters: filters, order_column: orderColumn,
      order_direction: orderDirection, limit: limit, offset: offset
    }, (response) => setSegments(response.data), onError);
  }

  findSegmentSuggestions(organizationId, tenantId, setSuggestions) {
    RESTClient.get("/ethernet/segmentation/segments/suggestions", tenant(organizationId, tenantId),
      (response) => setSuggestions(response.data.suggestions,
        response.data.limited ? response.data.limit_per_source : null));
  }

  findSegment(uuid, organizationId, tenantId, setSegment, onError = undefined) {
    RESTClient.get(`/ethernet/segmentation/segments/show/${uuid}`, tenant(organizationId, tenantId),
      (response) => setSegment(response.data), onError);
  }

  createSegment(organizationId, tenantId, segment, successCallback, errorCallback) {
    RESTClient.post(`${scoped(organizationId, tenantId)}/segments`, segment,
      (response) => successCallback(response.data), errorCallback);
  }

  updateSegment(uuid, organizationId, tenantId, segment, successCallback, errorCallback) {
    RESTClient.put(`${scoped(organizationId, tenantId)}/segments/show/${uuid}`, segment, successCallback, errorCallback);
  }

  importNetworkContext(organizationId, tenantId, successCallback, errorCallback) {
    RESTClient.post(`${scoped(organizationId, tenantId)}/segments/import/context`, {},
      (response) => successCallback(response.data.created), errorCallback);
  }

  deleteSegment(uuid, organizationId, tenantId, successCallback, errorCallback) {
    RESTClient.delete(`${scoped(organizationId, tenantId)}/segments/show/${uuid}`, successCallback, errorCallback);
  }

  deleteSegments(uuids, organizationId, tenantId, successCallback, errorCallback) {
    RESTClient.put(`${scoped(organizationId, tenantId)}/segments/many/delete`, {uuids: uuids},
      (response) => successCallback(response.data), errorCallback);
  }

  deleteFilteredSegments(organizationId, tenantId, filters, successCallback, errorCallback) {
    RESTClient.put(`${scoped(organizationId, tenantId)}/segments/filtered/delete${filteredQuery(filters)}`, {},
      (response) => successCallback(response.data), errorCallback);
  }

  findSegmentExclusions(uuid, organizationId, tenantId, setExclusions) {
    RESTClient.get(`/ethernet/segmentation/segments/show/${uuid}/exclusions`, tenant(organizationId, tenantId),
      (response) => setExclusions(response.data));
  }

  createSegmentExclusion(uuid, organizationId, tenantId, destination, note, successCallback, errorCallback) {
    RESTClient.post(`${scoped(organizationId, tenantId)}/segments/show/${uuid}/exclusions`,
      {destination: destination, note: note}, (response) => successCallback(response.data), errorCallback);
  }

  deleteSegmentExclusion(uuid, exclusionUuid, organizationId, tenantId, successCallback, errorCallback) {
    RESTClient.delete(`${scoped(organizationId, tenantId)}/segments/show/${uuid}/exclusions/show/${exclusionUuid}`,
      successCallback, errorCallback);
  }

  getMatrix(organizationId, tenantId, timeRange, setMatrix) {
    RESTClient.get("/ethernet/segmentation/matrix", {...tenant(organizationId, tenantId), time_range: timeRange},
      (response) => setMatrix(response.data));
  }

  findFlows(organizationId, tenantId, timeRange, sourceGroup, destinationGroup, filters, orderColumn, orderDirection,
            limit, offset, setFlows) {
    RESTClient.get("/ethernet/segmentation/matrix/flows", {
      ...tenant(organizationId, tenantId), time_range: timeRange, source_group: sourceGroup,
      destination_group: destinationGroup, filters: filters, order_column: orderColumn,
      order_direction: orderDirection, limit: limit, offset: offset
    }, (response) => setFlows(response.data));
  }

  findGroupAddresses(organizationId, tenantId, timeRange, group, filters, orderColumn, orderDirection, limit, offset,
                     setAddresses) {
    RESTClient.get("/ethernet/segmentation/matrix/addresses", {
      ...tenant(organizationId, tenantId), time_range: timeRange, group: group, filters: filters,
      order_column: orderColumn, order_direction: orderDirection, limit: limit, offset: offset
    }, (response) => setAddresses(response.data));
  }

  findSelectorOptions(organizationId, tenantId, setOptions) {
    RESTClient.get("/ethernet/segmentation/selectors", tenant(organizationId, tenantId),
      (response) => setOptions(response.data));
  }

  findAllPolicies(organizationId, tenantId, filters, timeRange, orderColumn, orderDirection, limit, offset, setPolicies,
                  onError = undefined) {
    RESTClient.get("/ethernet/segmentation/policies", {
      ...tenant(organizationId, tenantId), filters: filters, time_range: timeRange, order_column: orderColumn,
      order_direction: orderDirection, limit: limit, offset: offset
    }, (response) => setPolicies(response.data), onError);
  }

  findPolicy(uuid, organizationId, tenantId, timeRange, setPolicy, onError = undefined) {
    RESTClient.get(`/ethernet/segmentation/policies/show/${uuid}`,
      {...tenant(organizationId, tenantId), time_range: timeRange}, (response) => setPolicy(response.data), onError);
  }

  createPolicy(organizationId, tenantId, policy, successCallback, errorCallback) {
    RESTClient.post(`${scoped(organizationId, tenantId)}/policies`, policy,
      (response) => successCallback(response.data), errorCallback);
  }

  updatePolicy(uuid, organizationId, tenantId, policy, successCallback, errorCallback) {
    RESTClient.put(`${scoped(organizationId, tenantId)}/policies/show/${uuid}`, policy, successCallback, errorCallback);
  }

  deletePolicy(uuid, organizationId, tenantId, successCallback) {
    RESTClient.delete(`${scoped(organizationId, tenantId)}/policies/show/${uuid}`, successCallback);
  }

  /*
   * Bulk actions on policies. The filters of the policy list can refer to the findings of the time range, so the
   * filtered ones take the time range the list was loaded with.
   */
  setPoliciesStatus(uuids, status, organizationId, tenantId, successCallback, errorCallback) {
    RESTClient.put(`${scoped(organizationId, tenantId)}/policies/many/status/${status}`, {uuids: uuids},
      (response) => successCallback(response.data), errorCallback);
  }

  setFilteredPoliciesStatus(status, organizationId, tenantId, filters, timeRange, successCallback, errorCallback) {
    RESTClient.put(
      `${scoped(organizationId, tenantId)}/policies/filtered/status/${status}${filteredQuery(filters, timeRange)}`, {},
      (response) => successCallback(response.data), errorCallback);
  }

  setPoliciesAlert(uuids, alert, enabled, organizationId, tenantId, successCallback, errorCallback) {
    RESTClient.put(`${scoped(organizationId, tenantId)}/policies/many/alerts/${alert}/${!!enabled}`, {uuids: uuids},
      (response) => successCallback(response.data), errorCallback);
  }

  setFilteredPoliciesAlert(alert, enabled, organizationId, tenantId, filters, timeRange, successCallback, errorCallback) {
    RESTClient.put(
      `${scoped(organizationId, tenantId)}/policies/filtered/alerts/${alert}/${!!enabled}${filteredQuery(filters, timeRange)}`,
      {}, (response) => successCallback(response.data), errorCallback);
  }

  deletePolicies(uuids, organizationId, tenantId, successCallback, errorCallback) {
    RESTClient.put(`${scoped(organizationId, tenantId)}/policies/many/delete`, {uuids: uuids},
      (response) => successCallback(response.data), errorCallback);
  }

  deleteFilteredPolicies(organizationId, tenantId, filters, timeRange, successCallback, errorCallback) {
    RESTClient.put(`${scoped(organizationId, tenantId)}/policies/filtered/delete${filteredQuery(filters, timeRange)}`, {},
      (response) => successCallback(response.data), errorCallback);
  }

  allowCrossing(uuid, organizationId, tenantId, rule, successCallback, errorCallback) {
    RESTClient.post(`${scoped(organizationId, tenantId)}/policies/show/${uuid}/allowed`, {rule: rule},
      successCallback, errorCallback);
  }

  previewPolicy(organizationId, tenantId, timeRange, scope, allowed, setPreview, errorCallback) {
    const query = "?time_range=" + encodeURIComponent(JSON.stringify(timeRange));
    RESTClient.post(`${scoped(organizationId, tenantId)}/policies/preview${query}`, {scope: scope, allowed: allowed},
      (response) => setPreview(response.data), errorCallback);
  }

  findAllFindings(organizationId, tenantId, filters, timeRange, policy, orderColumn, orderDirection, limit, offset,
                  setFindings, onError = undefined) {
    RESTClient.get("/ethernet/segmentation/findings", {
      ...tenant(organizationId, tenantId), filters: filters, time_range: timeRange, policy: policy,
      order_column: orderColumn, order_direction: orderDirection, limit: limit, offset: offset
    }, (response) => setFindings(response.data), onError);
  }

  findFinding(uuid, organizationId, tenantId, setFinding, onError = undefined) {
    RESTClient.get(`/ethernet/segmentation/findings/show/${uuid}`, tenant(organizationId, tenantId),
      (response) => setFinding(response.data), onError);
  }

  findFindingTargets(uuid, organizationId, tenantId, filters, orderColumn, orderDirection, limit, offset, setTargets,
                     onError = undefined) {
    RESTClient.get(`/ethernet/segmentation/findings/show/${uuid}/targets`, {
      ...tenant(organizationId, tenantId), filters: filters, order_column: orderColumn,
      order_direction: orderDirection, limit: limit, offset: offset
    }, (response) => setTargets(response.data), onError);
  }

  deleteFinding(uuid, organizationId, tenantId, successCallback) {
    RESTClient.delete(`${scoped(organizationId, tenantId)}/findings/show/${uuid}`, successCallback);
  }

  deleteFindings(uuids, organizationId, tenantId, successCallback, errorCallback) {
    RESTClient.put(`${scoped(organizationId, tenantId)}/findings/many/delete`, {uuids: uuids},
      (response) => successCallback(response.data), errorCallback);
  }

  deleteFilteredFindings(organizationId, tenantId, filters, timeRange, policy, successCallback, errorCallback) {
    RESTClient.put(
      `${scoped(organizationId, tenantId)}/findings/filtered/delete${filteredQuery(filters, timeRange, policy)}`, {},
      (response) => successCallback(response.data), errorCallback);
  }

}
