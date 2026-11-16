import RESTClient from "../../util/RESTClient";

const tenant = (organizationId, tenantId) => ({organization_id: organizationId, tenant_id: tenantId});
const scoped = (organizationId, tenantId) => `/ethernet/assets/tags/organization/${organizationId}/tenant/${tenantId}`;

// The largest page the node hands out.
const MAX_PAGE_SIZE = 250;

export default class AssetTagsService {

  findAllTagsUnpaged(organizationId, tenantId, setTags) {
    const readFrom = (tags) => {
      RESTClient.get("/ethernet/assets/tags", {
        ...tenant(organizationId, tenantId), limit: MAX_PAGE_SIZE, offset: tags.length
      }, (response) => {
        const read = tags.concat(response.data.tags);

        if (response.data.tags.length === 0 || read.length >= response.data.total) {
          setTags(read);
        } else {
          readFrom(read);
        }
      });
    }

    readFrom([]);
  }

  findTags(organizationId, tenantId, filters, orderColumn, orderDirection, limit, offset, setTags,
           onError = undefined) {
    RESTClient.get("/ethernet/assets/tags", {
      ...tenant(organizationId, tenantId), filters: filters, order_column: orderColumn,
      order_direction: orderDirection, limit: limit, offset: offset
    }, (response) => setTags(response.data), onError);
  }

  findTag(uuid, organizationId, tenantId, setTag, onError = undefined) {
    RESTClient.get(`/ethernet/assets/tags/show/${uuid}`, tenant(organizationId, tenantId),
      (response) => setTag(response.data), onError);
  }

  findSuggestions(organizationId, tenantId, setSuggestions) {
    RESTClient.get("/ethernet/assets/tags/suggestions", tenant(organizationId, tenantId),
      (response) => setSuggestions(response.data));
  }

  findTagsOfAsset(assetUuid, organizationId, tenantId, setTags) {
    RESTClient.get(`/ethernet/assets/tags/asset/${assetUuid}`, tenant(organizationId, tenantId),
      (response) => setTags(response.data.tags));
  }

  findAssetsOfTag(uuid, organizationId, tenantId, limit, offset, setAssets) {
    RESTClient.get(`/ethernet/assets/tags/show/${uuid}/assets`,
      {...tenant(organizationId, tenantId), limit: limit, offset: offset}, (response) => setAssets(response.data));
  }

  createTag(organizationId, tenantId, name, description, successCallback, errorCallback) {
    RESTClient.post(scoped(organizationId, tenantId), {name: name, description: description},
      (response) => successCallback(response.data), errorCallback);
  }

  updateTag(uuid, organizationId, tenantId, name, description, successCallback, errorCallback) {
    RESTClient.put(`${scoped(organizationId, tenantId)}/show/${uuid}`, {name: name, description: description},
      successCallback, errorCallback);
  }

  deleteTag(uuid, organizationId, tenantId, successCallback, errorCallback) {
    RESTClient.delete(`${scoped(organizationId, tenantId)}/show/${uuid}`, successCallback, errorCallback);
  }

  deleteTags(uuids, organizationId, tenantId, successCallback, errorCallback) {
    RESTClient.put(`${scoped(organizationId, tenantId)}/many/delete`, {uuids: uuids},
      (response) => successCallback(response.data), errorCallback);
  }

  // Without filters, every tag is meant.
  deleteFilteredTags(organizationId, tenantId, filters, successCallback, errorCallback) {
    const query = filters ? "?filters=" + encodeURIComponent(JSON.stringify(filters)) : "";

    RESTClient.put(`${scoped(organizationId, tenantId)}/filtered/delete${query}`, {},
      (response) => successCallback(response.data), errorCallback);
  }

  assignTag(uuid, assetUuid, organizationId, tenantId, successCallback, errorCallback = undefined) {
    RESTClient.put(`${scoped(organizationId, tenantId)}/show/${uuid}/assets/${assetUuid}`, {}, successCallback,
      errorCallback);
  }

  unassignTag(uuid, assetUuid, organizationId, tenantId, successCallback, errorCallback = undefined) {
    RESTClient.delete(`${scoped(organizationId, tenantId)}/show/${uuid}/assets/${assetUuid}`, successCallback,
      errorCallback);
  }

  unassignTagFromAssets(uuid, assetUuids, organizationId, tenantId, successCallback, errorCallback) {
    RESTClient.put(`${scoped(organizationId, tenantId)}/show/${uuid}/assets/many/remove`, {uuids: assetUuids},
      (response) => successCallback(response.data), errorCallback);
  }

  unassignTagFromAllAssets(uuid, organizationId, tenantId, successCallback, errorCallback) {
    RESTClient.put(`${scoped(organizationId, tenantId)}/show/${uuid}/assets/all/remove`, {},
      (response) => successCallback(response.data), errorCallback);
  }

}
