import RESTClient from "../../util/RESTClient";

const tapsList = (taps) => Array.isArray(taps) ? taps.join(",") : (taps === "*" ? "*" : null);

const selectionParameters = (selection) => ({
  organization_id: selection.organizationId,
  tenant_id: selection.tenantId,
  taps: tapsList(selection.taps),
  time_range: selection.timeRange,
  filters: selection.filters,
  nodes: selection.nodes && selection.nodes.length > 0 ? selection.nodes : null,
  exclude_nodes: selection.excludedNodes && selection.excludedNodes.length > 0 ? selection.excludedNodes : null
});

export default class TopologyService {

  getSummary(selection, setData, onError = undefined) {
    RESTClient.get("/ethernet/topology/summary", selectionParameters(selection),
        (response) => setData(response.data), onError);
  }

  getTimeline(selection, setData, onError = undefined) {
    RESTClient.get("/ethernet/topology/timeline", selectionParameters(selection),
        (response) => setData(response.data), onError);
  }

  getLinks(selection, internalLevel, externalLevel, serviceLevel, limit, setData, onError = undefined) {
    RESTClient.get("/ethernet/topology/links", {
          ...selectionParameters(selection),
          internal_level: internalLevel,
          external_level: externalLevel,
          service_level: serviceLevel,
          limit: limit
        },
        (response) => setData(response.data), onError);
  }

  getGeoFlows(selection, limit, setData, onError = undefined) {
    RESTClient.get("/ethernet/topology/geo", {...selectionParameters(selection), limit: limit},
        (response) => setData(response.data), onError);
  }

  getRanking(selection, dimension, orderColumn, orderDirection, limit, offset, setData, onError = undefined) {
    RESTClient.get("/ethernet/topology/ranking", {
          ...selectionParameters(selection),
          dimension: dimension,
          order_column: orderColumn,
          order_direction: orderDirection,
          limit: limit,
          offset: offset
        },
        (response) => setData(response.data), onError);
  }

}
