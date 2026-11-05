import React, {useEffect, useState} from "react";
import moment from "moment";
import {Link} from "react-router-dom";
import CardTitleWithControls from "../../../shared/CardTitleWithControls";
import LoadingSpinner from "../../../misc/LoadingSpinner";
import AssetsService from "../../../../services/ethernet/AssetsService";
import ApiRoutes from "../../../../util/ApiRoutes";
import ColumnSorting from "../../../shared/ColumnSorting";
import {filteredLink, formatCount, macFilter, alertRoleTitle, sortRows} from "./AssetProfileHelpers";

const assetsService = new AssetsService();

export default function AssetAlertsCard({asset, organizationId, tenantId, revision, onRefresh}) {

  const [alerts, setAlerts] = useState(null);
  const [orderColumn, setOrderColumn] = useState("last_seen");
  const [orderDirection, setOrderDirection] = useState("DESC");

  useEffect(() => {
    setAlerts(null);
    assetsService.getAssetProfileAlerts(asset.uuid, organizationId, tenantId, setAlerts);
  }, [asset.uuid, organizationId, tenantId, revision]);

  const mac = asset.mac.address;

  const title = (
    <React.Fragment>
      <CardTitleWithControls title="Alerts"
                             internalLink={filteredLink(ApiRoutes.ALERTS.INDEX, macFilter("attribute", mac))}
                             refreshAction={onRefresh} />
      <p className="text-muted">Actor: this asset caused the alert. Victim: it was the target. Subject: the alert is about the asset itself.</p>
    </React.Fragment>
  );

  if (!alerts) {
    return <React.Fragment>{title}<LoadingSpinner /></React.Fragment>
  }

  const sorting = (column) => (
    <ColumnSorting thisColumn={column}
                   orderColumn={orderColumn}
                   setOrderColumn={setOrderColumn}
                   orderDirection={orderDirection}
                   setOrderDirection={setOrderDirection} />
  );

  const rows = sortRows(alerts.alerts, orderColumn, orderDirection, {
    alert: (a) => a.detection_type_description || a.detection_type,
    role: (a) => alertRoleTitle(a.role),
    active: (a) => a.active_count,
    total: (a) => a.count,
    last_seen: (a) => a.last_seen
  });

  return (
    <React.Fragment>
      {title}

      {alerts.alerts.length === 0
        ? <div className="alert alert-info mb-0">No alerts involve this asset.</div>
        : <table className="table table-sm table-hover table-striped mb-0">
            <thead>
            <tr>
              <th>Alert {sorting("alert")}</th>
              <th>Role {sorting("role")}</th>
              <th>Active {sorting("active")}</th>
              <th>Total {sorting("total")}</th>
              <th>Last Seen {sorting("last_seen")}</th>
            </tr>
            </thead>
            <tbody>
            {rows.map((a) => (
              <tr key={a.detection_type}>
                <td title={a.detection_type}>
                  <Link to={filteredLink(ApiRoutes.ALERTS.INDEX, {
                    ...macFilter("attribute", mac),
                    type: [{field: "type", operator: "equals", value: a.detection_type}]
                  })}>
                    {a.detection_type_description ? a.detection_type_description : a.detection_type}
                  </Link>
                </td>
                <td>{alertRoleTitle(a.role)}</td>
                <td>{a.active_count > 0 ? <span className="text-danger">{formatCount(a.active_count)}</span> : <span className="text-muted">0</span>}</td>
                <td>{formatCount(a.count)}</td>
                <td title={moment(a.last_seen).format()}>{moment(a.last_seen).fromNow()}</td>
              </tr>
            ))}
            </tbody>
          </table>}
    </React.Fragment>
  )

}
