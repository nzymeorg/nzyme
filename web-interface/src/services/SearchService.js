import RESTClient from "../util/RESTClient";

class SearchService {

  search(query, organizationId, tenantId, limit, timeRange, signal, successCallback, errorCallback) {
    RESTClient.get("/search", {
      query: query,
      organization_id: organizationId,
      tenant_id: tenantId,
      limit: limit,
      time_range: timeRange
    }, (response) => successCallback(response.data), errorCallback, signal)
  }

  searchSource(source, query, organizationId, tenantId, limit, offset, timeRange, signal, successCallback, errorCallback) {
    RESTClient.get("/search/sources/" + source, {
      query: query,
      organization_id: organizationId,
      tenant_id: tenantId,
      limit: limit,
      offset: offset,
      time_range: timeRange
    }, (response) => successCallback(response.data), errorCallback, signal)
  }

}

export default SearchService;
