import React, {useContext, useEffect, useMemo, useState} from "react";

import DetectionAlertsService from "../../services/DetectionAlertsService";
import LoadingSpinner from "../misc/LoadingSpinner";
import AlertsTableRow from "./AlertsTableRow";
import Paginator from "../misc/Paginator";
import AlertActionMultiSelector from "./AlertActionMultiSelector";
import {userHasPermission} from "../../util/Tools";
import {UserContext} from "../../App";
import RenderConditionally from "../misc/RenderConditionally";
import useSelectedTenant from "../system/tenantselector/useSelectedTenant";
import {toast} from "react-toastify";
import ColumnSorting from "../shared/ColumnSorting";
import {Presets} from "../shared/timerange/TimeRange";
import {queryParametersToFilters} from "../shared/filtering/FilterQueryParameters";
import {ALERT_FILTER_FIELDS} from "./AlertFilterFields";

const detectionAlertsService = new DetectionAlertsService();

function AlertsTable(props) {

  const [organizationId, tenantId] = useSelectedTenant();

  const user = useContext(UserContext);

  const perPage = props.perPage ? props.perPage : 25;
  const hideControls = props.hideControls ? props.hideControls : false;

  // Optional.
  const subsystem = props.subsystem;
  const timeRange = props.timeRange ? props.timeRange : Presets.ALL_TIME;
  const setFilters = props.setFilters;
  const revision = props.revision;
  const onLoaded = props.onLoaded;

  // Memoized so that a table without page filters does not get a new filter object on every render.
  const filters = useMemo(() => props.filters ? props.filters : (subsystem
      ? queryParametersToFilters(
          JSON.stringify({subsystem: [{field: "subsystem", operator: "equals", value: subsystem}]}),
          ALERT_FILTER_FIELDS)
      : {}), [props.filters, subsystem]);

  const [alerts, setAlerts] = useState(null);
  const [selectedRows, setSelectedRows] = useState([]);
  const [allRowsSelected, setAllRowsSelected] = useState(false);

  const [orderColumn, setOrderColumn] = useState("last_seen");
  const [orderDirection, setOrderDirection] = useState("DESC");

  const [page, setPage] = useState(1);

  const [localRevision, setLocalRevision] = useState(0);

  const loadData = () => {
    detectionAlertsService.findAllAlerts((data) => {
      setAlerts(data);
      if (onLoaded) {
        onLoaded(data);
      }
    }, organizationId, tenantId, timeRange, filters, orderColumn, orderDirection, perPage, (page-1)*perPage);
  }

  const reload = () => {
    setLocalRevision(prevRev => prevRev + 1);
    setSelectedRows([]);
    setAllRowsSelected(false);
  }

  const onRowSelect = (uuid) => {
    const copy = [...selectedRows];

    if (copy.includes(uuid)) {
      // Unselect.
      const idx = copy.indexOf(uuid);
      if (idx > -1) {
        copy.splice(idx, 1);
      }
    } else {
      copy.push(uuid);
    }

    setSelectedRows(copy);
  }

  const deleteSelected = (e) => {
    e.preventDefault();

    if (!confirm("Really delete selected alerts?")) {
      return;
    }

    detectionAlertsService.deleteAlerts(selectedRows, () => {
      reload();
      toast.success("Selected alerts deleted.")
    })
  }

  const resolveSelected = (e) => {
    e.preventDefault();
    detectionAlertsService.markAlertsAsResolved(selectedRows, () => {
      reload();
      toast.success("Selected alerts marked as resolved.")
    })
  }

  const deleteMatching = (e) => {
    e.preventDefault();

    if (!confirm("Really delete all alerts that match the current filters and time range? This cannot be undone.")) {
      return;
    }

    detectionAlertsService.deleteFilteredAlerts(organizationId, tenantId, timeRange, filters, () => {
      reload();
      toast.success("Matching alerts deleted.")
    })
  }

  const resolveMatching = (e) => {
    e.preventDefault();

    if (!confirm("Really mark all alerts that match the current filters and time range as resolved?")) {
      return;
    }

    detectionAlertsService.markFilteredAlertsAsResolved(organizationId, tenantId, timeRange, filters, () => {
      reload();
      toast.success("Matching alerts marked as resolved.")
    })
  }

  const handleAllRowsSelected = () => {
    if (allRowsSelected) {
      // Unselect
      setAllRowsSelected(false);
      setSelectedRows([]);
    } else {
      // Select
      setAllRowsSelected(true);

      const selected = [];
      alerts.alerts.forEach((alert) => {
        selected.push(alert.id);
      })

      setSelectedRows(selected);
    }
  }

  const columnSorting = (columnName) => {
    return <ColumnSorting thisColumn={columnName}
                          orderColumn={orderColumn}
                          setOrderColumn={setOrderColumn}
                          orderDirection={orderDirection}
                          setOrderDirection={setOrderDirection} />
  }

  useEffect(() => {
    setPage(1);
  }, [timeRange, filters, orderColumn, orderDirection]);

  useEffect(() => {
    setAlerts(null);
    loadData();
  }, [page, revision, localRevision, organizationId, tenantId, timeRange, filters, orderColumn, orderDirection])

  if (!alerts) {
    return <LoadingSpinner />
  }

  if (alerts.alerts.length === 0) {
    return (
        <div className="alert alert-info mb-0">
          No alerts match the current filters and time range.
        </div>
    )
  }

  return (
      <React.Fragment>
        <div className="mb-2">
          <div className="float-start">
            <AlertActionMultiSelector
                show={userHasPermission(user, "alerts_manage") && !hideControls}
                selectedRows={selectedRows}
                onDeleteSelected={deleteSelected}
                onResolveSelected={resolveSelected}
                matchingCount={alerts.total}
                onDeleteMatching={deleteMatching}
                onResolveMatching={resolveMatching} />
          </div>

          <div style={{clear: "both"}}/>
        </div>

        <table className="table table-sm table-hover table-striped">
          <thead>
          <tr>
            <RenderConditionally render={!hideControls}>
              <th>
                <input className="form-check-input"
                       type="checkbox"
                       checked={allRowsSelected}
                       onChange={handleAllRowsSelected}/>
              </th>
            </RenderConditionally>
            <th className="text-nowrap">Status {columnSorting("status")}</th>
            <th>Details {columnSorting("details")}</th>
            <th>Type {columnSorting("detection_type")}</th>
            <th>Subsystem {columnSorting("subsystem")}</th>
            <th>Affected {columnSorting("affected_address")}</th>
            <th>Re-activations {columnSorting("reactivations")}</th>
            <th>First Seen {columnSorting("created_at")}</th>
            <th>Last Seen {columnSorting("last_seen")}</th>
            <th>Source</th>
          </tr>
          </thead>
          <tbody>
          {alerts.alerts.map(function(alert){
            return <AlertsTableRow key={"alert-" + alert.id}
                                   hideControls={hideControls}
                                   alert={alert}
                                   onSelect={onRowSelect}
                                   isSelected={selectedRows.includes(alert.id)}
                                   setFilters={setFilters} />
          })}
          </tbody>
        </table>

        <Paginator itemCount={alerts.total} perPage={perPage} setPage={setPage} page={page} />
      </React.Fragment>
  )

}

export default AlertsTable;
