import RESTClient from '../util/RESTClient'

class SystemService {

  getVersionInfo () {
    const self = this

    RESTClient.get('/system/version', {}, function (response) {
      self.setState({ versionInfo: response.data })
    })
  }

  getHealthIndicators(setHealthIndicators) {
    RESTClient.get('/system/health/indicators', {}, function (response) {
      setHealthIndicators(response.data.indicators)
    })
  }

  updateHealthIndicatorsConfiguration(configuration, successCallback) {
    RESTClient.put('/system/health/indicators/configuration', configuration, successCallback)
  }

  findAllEvents(setEvents, limit, offset, eventTypes, organizationId = undefined) {
    const params = {limit: limit, offset: offset, event_types: eventTypes.join(",")};

    if (organizationId) {
      params["organization_id"] = organizationId;
    }

    RESTClient.get('/system/events',
        params, function (response) {
          setEvents(response.data);
        })
  }

  findAllEventTypes(setEventTypes, limit, offset, categories) {
    RESTClient.get('/system/events/types',
        {limit: limit, offset: offset, categories: categories.join(",")}, function (response) {
          setEventTypes(response.data);
        })
  }

  findAllEventTypesOfOrganization(setEventTypes, organizationId, limit, offset, categories) {
    RESTClient.get('/system/events/types',
        {organization_id: organizationId, limit: limit, offset: offset, categories: categories.join(",")},
        function (response) {
      setEventTypes(response.data);
        })
  }

  getDatabaseGlobalSizes(setSizes) {
    RESTClient.get("/system/database/sizes/global", {}, (response) => setSizes(response.data))
  }

  getDatabaseOrganizationSizes(setSizes, organizationId) {
    RESTClient.get(`/system/database/sizes/organization/${organizationId}`, {}, (response) => setSizes(response.data))
  }

  getDatabaseTenantSizes(setSizes, organizationId, tenantId) {
    RESTClient.get(`/system/database/sizes/organization/${organizationId}/tenants/${tenantId}`, {}, (response) => setSizes(response.data))
  }

  purgeDatabaseGlobalCategory(category, onSuccess) {
    RESTClient.post(`/system/database/purge/category/${category}`, {}, onSuccess)
  }

  purgeDatabaseOrganizationCategory(category, organizationId, onSuccess) {
    RESTClient.post(`/system/database/purge/organization/${organizationId}/category/${category}`, {}, onSuccess)
  }

  purgeDatabaseTenantCategory(category, organizationId, tenantId, onSuccess) {
    RESTClient.post(`/system/database/purge/organization/${organizationId}/tenant/${tenantId}/category/${category}`, {}, onSuccess)
  }

  setDatabaseCategoryRetentionTime(category, organizationId, tenantId, retentionTimeDays, onSuccess) {
    RESTClient.put(`/system/database/configuration/organization/${organizationId}/tenant/${tenantId}/category/${category}/retention`, {retention_time_days: retentionTimeDays}, onSuccess)
  }

  recalculateDatabaseUsage(onSuccess, onError) {
    RESTClient.post("/system/database/usage/recalculate", {}, onSuccess, onError)
  }

  getDatabaseHealth(setHealth, onNotReady) {
    RESTClient.get("/system/database/maintenance/health", {}, (response) => setHealth(response.data), onNotReady)
  }

  getDatabaseMetricHistogram(metric, setHistogram) {
    RESTClient.get(`/system/database/maintenance/histogram/${metric}`, {}, (response) => setHistogram(response.data))
  }

  getDatabaseMaintenanceLog(limit, offset, setLog) {
    RESTClient.get("/system/database/maintenance/log", {limit: limit, offset: offset}, (response) => setLog(response.data))
  }

  getDatabaseMaintenanceConfiguration(setConfiguration) {
    RESTClient.get("/system/database/maintenance/configuration", {}, (response) => setConfiguration(response.data))
  }

  updateDatabaseMaintenanceConfiguration(newConfig, successCallback, errorCallback) {
    RESTClient.put("/system/database/maintenance/configuration", {change: newConfig}, successCallback, errorCallback)
  }

  vacuumDatabaseTable(table, onSuccess) {
    RESTClient.post(`/system/database/maintenance/actions/tables/${table}/vacuum`, {}, onSuccess)
  }

  reindexDatabaseTable(table, onSuccess) {
    RESTClient.post(`/system/database/maintenance/actions/tables/${table}/reindex`, {}, onSuccess)
  }

  reindexDatabaseIndex(index, onSuccess) {
    RESTClient.post(`/system/database/maintenance/actions/indexes/${index}/reindex`, {}, onSuccess)
  }

  getSidebarTitle(setSidebarTitle, setSidebarSubtitle) {
    RESTClient.get('/system/lookandfeel/sidebartitle', {}, function (response) {
      setSidebarTitle(response.data.title);
      setSidebarSubtitle(response.data.subtitle);
    })
  }

  setSidebarTitle(sidebarTitle, sidebarSubtitle, onSuccess, onError) {
    RESTClient.put('/system/lookandfeel/sidebartitle', {title: sidebarTitle, subtitle: sidebarSubtitle}, onSuccess, onError);
  }

  uploadLoginImage(formData, successCallback, errorCallback) {
    RESTClient.postMultipart('/system/lookandfeel/loginimage', formData, false, successCallback, errorCallback);
  }

  resetLoginImage(successCallback) {
    RESTClient.delete('/system/lookandfeel/loginimage', successCallback);
  }

  getSubsystemsConfiguration(setConfiguration) {
    RESTClient.get(
      "/system/subsystems/configuration",
      {},
      (response) => setConfiguration(response.data)
    )
  }

  updateSubsystemsConfiguration(newConfig, successCallback, errorCallback) {
    RESTClient.put("/system/subsystems/configuration", { change: newConfig }, successCallback, errorCallback)
  }

}

export default SystemService
