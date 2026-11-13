import RESTClient from "../util/RESTClient";

export default class DashboardsService {

  findAllDashboards(organizationId, tenantId, limit, offset, setDashboards) {
    RESTClient.get("/dashboards", {organization_id: organizationId, tenant_id: tenantId, limit: limit, offset: offset},
      (response) => setDashboards(response.data));
  }

  findDashboard(uuid, organizationId, tenantId, setDashboard, onError = undefined) {
    RESTClient.get(`/dashboards/show/${uuid}`, {organization_id: organizationId, tenant_id: tenantId},
      (response) => setDashboard(response.data), onError);
  }

  findDashboardContent(uuid, organizationId, tenantId, setContent, onError = undefined) {
    RESTClient.get(`/dashboards/show/${uuid}/content`, {organization_id: organizationId, tenant_id: tenantId},
      (response) => setContent(response.data), onError);
  }

  findDashboardTopology(uuid, widgetId, organizationId, tenantId, setTopology, onError = undefined) {
    RESTClient.get(`/dashboards/show/${uuid}/widgets/${widgetId}/topology`,
      {organization_id: organizationId, tenant_id: tenantId}, (response) => setTopology(response.data), onError);
  }

  findScopeOptions(organizationId, tenantId, setOptions) {
    RESTClient.get("/dashboards/scope/options", {organization_id: organizationId, tenant_id: tenantId},
      (response) => setOptions(response.data));
  }

  createDashboard(organizationId, tenantId, dashboard, successCallback, errorCallback) {
    RESTClient.post(`/dashboards/organization/${organizationId}/tenant/${tenantId}`, dashboard,
      (response) => successCallback(response.data), errorCallback);
  }

  updateDashboard(uuid, organizationId, tenantId, dashboard, successCallback, errorCallback) {
    RESTClient.put(`/dashboards/organization/${organizationId}/tenant/${tenantId}/show/${uuid}`, dashboard,
      successCallback, errorCallback);
  }

  deleteDashboard(uuid, organizationId, tenantId, successCallback) {
    RESTClient.delete(`/dashboards/organization/${organizationId}/tenant/${tenantId}/show/${uuid}`, successCallback);
  }

  findAllDisplays(organizationId, tenantId, dashboardId, locationId, limit, offset, setDisplays) {
    RESTClient.get("/dashboards/displays", {
      organization_id: organizationId, tenant_id: tenantId, dashboard_id: dashboardId, location_id: locationId,
      limit: limit, offset: offset
    }, (response) => setDisplays(response.data));
  }

  findDisplay(uuid, organizationId, tenantId, setDisplay, onError = undefined) {
    RESTClient.get(`/dashboards/displays/show/${uuid}`, {organization_id: organizationId, tenant_id: tenantId},
      (response) => setDisplay(response.data), onError);
  }

  createDisplay(organizationId, tenantId, display, successCallback, errorCallback) {
    RESTClient.post(`/dashboards/displays/organization/${organizationId}/tenant/${tenantId}`, display,
      (response) => successCallback(response.data), errorCallback);
  }

  updateDisplay(uuid, organizationId, tenantId, display, successCallback, errorCallback) {
    RESTClient.put(`/dashboards/displays/organization/${organizationId}/tenant/${tenantId}/show/${uuid}`, display,
      successCallback, errorCallback);
  }

  replaceDisplayToken(uuid, organizationId, tenantId, successCallback) {
    RESTClient.post(`/dashboards/displays/organization/${organizationId}/tenant/${tenantId}/show/${uuid}/token`, {},
      (response) => successCallback(response.data));
  }

  deleteDisplay(uuid, organizationId, tenantId, successCallback) {
    RESTClient.delete(`/dashboards/displays/organization/${organizationId}/tenant/${tenantId}/show/${uuid}`,
      successCallback);
  }

}
