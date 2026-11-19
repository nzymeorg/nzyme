import MonitorsService from "../../../../services/MonitorsService";

const monitorsService = new MonitorsService();

const onSaveFiltersAsMonitor = (monitorType, organizationId, tenantId) =>
  (name, description, taps, locationId, triggerCondition, interval, lookback, filters, onSuccess, onFailure) => {
    monitorsService.createMonitor(
      monitorType,
      name,
      description,
      taps,
      locationId,
      triggerCondition,
      interval,
      lookback,
      filters,
      organizationId,
      tenantId,
      onSuccess,
      onFailure
    );
  };

export default onSaveFiltersAsMonitor;