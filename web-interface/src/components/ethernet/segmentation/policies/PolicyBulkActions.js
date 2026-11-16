import SegmentationService from "../../../../services/ethernet/SegmentationService";

const segmentationService = new SegmentationService();

const ALERT_ON_CONNECTED = {
  alert: "connected",
  connections: "accepted connections",
  title: "Accepted Connections",
  when: "is accepted"
};

const ALERT_ON_ATTEMPTED = {
  alert: "attempted",
  connections: "rejected or timed out connections",
  title: "Rejected or Timed Out Connections",
  when: "is rejected or times out"
};

export const policyBulkActions = (bulk) => {
  const statusAction = (status, texts) => {
    return {
      key: "status-" + status,
      group: "Status",
      destructive: false,
      ...texts,
      onChecked: (uuids, onSuccess, onError) => {
        segmentationService.setPoliciesStatus(uuids, status, bulk.organizationId, bulk.tenantId, onSuccess, onError);
      },
      onFiltered: (filters, onSuccess, onError) => {
        segmentationService.setFilteredPoliciesStatus(status, bulk.organizationId, bulk.tenantId, filters,
          bulk.timeRange, onSuccess, onError);
      }
    };
  }

  const alertAction = (option, enabled) => {
    const turn = enabled ? "on" : "off";

    return {
      key: "alert-" + option.alert + "-" + turn,
      group: "Alerts",
      label: "Turn " + turn + " for " + option.connections,
      title: "Turn " + (enabled ? "On" : "Off") + " Alerts on " + option.title,
      description: enabled
        ? "Alert when a connection that is not allowed " + option.when + ". Only active policies alert."
        : "No alert when a connection that is not allowed " + option.when + ". Findings are still recorded.",
      confirm: (what) => "Turn " + turn + " alerts for " + what,
      running: "Turning " + turn + " ...",
      destructive: false,
      done: (what) => "Alerts on " + option.connections + " turned " + turn + " for " + what + ".",
      onChecked: (uuids, onSuccess, onError) => {
        segmentationService.setPoliciesAlert(uuids, option.alert, enabled, bulk.organizationId, bulk.tenantId,
          onSuccess, onError);
      },
      onFiltered: (filters, onSuccess, onError) => {
        segmentationService.setFilteredPoliciesAlert(option.alert, enabled, bulk.organizationId, bulk.tenantId, filters,
          bulk.timeRange, onSuccess, onError);
      }
    };
  }

  return [
    statusAction("ACTIVE", {
      label: "Activate",
      title: "Activate Policies",
      description: "Sets the status to active.",
      confirm: (what) => "Activate " + what,
      running: "Activating ...",
      done: (what) => what + " activated."
    }),
    statusAction("AUDIT", {
      label: "Set to audit",
      title: "Set Policies to Audit",
      description: "Sets the status to audit. Findings are recorded, no alerts.",
      confirm: (what) => "Set " + what + " to audit",
      running: "Setting to audit ...",
      done: (what) => what + " set to audit."
    }),
    statusAction("PAUSED", {
      label: "Pause",
      title: "Pause Policies",
      description: "Sets the status to paused. Policies are not evaluated. Existing findings are kept.",
      confirm: (what) => "Pause " + what,
      running: "Pausing ...",
      done: (what) => what + " paused."
    }),
    alertAction(ALERT_ON_CONNECTED, true),
    alertAction(ALERT_ON_CONNECTED, false),
    alertAction(ALERT_ON_ATTEMPTED, true),
    alertAction(ALERT_ON_ATTEMPTED, false),
    {
      key: "delete",
      label: "Delete",
      title: "Delete Policies",
      description: "Deletes policies and their findings. This cannot be undone.",
      confirm: (what) => "Delete " + what,
      running: "Deleting ...",
      destructive: true,
      done: (what) => what + " deleted.",
      onChecked: (uuids, onSuccess, onError) => {
        segmentationService.deletePolicies(uuids, bulk.organizationId, bulk.tenantId, onSuccess, onError);
      },
      onFiltered: (filters, onSuccess, onError) => {
        segmentationService.deleteFilteredPolicies(bulk.organizationId, bulk.tenantId, filters, bulk.timeRange,
          onSuccess, onError);
      }
    }
  ];
}
