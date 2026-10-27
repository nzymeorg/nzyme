import {Link} from "react-router-dom";
import React, {useEffect, useState} from "react";
import LoadingSpinner from "../../misc/LoadingSpinner";
import Dot11Service from "../../../services/Dot11Service";

import moment from "moment";
import ApiRoutes from "../../../util/ApiRoutes";
import MonitoredNetworkStatus from "./MonitoredNetworkStatus";
import useSelectedTenant from "../../system/tenantselector/useSelectedTenant";

const dot11Service = new Dot11Service();

function MonitoredNetworksTable() {

  const [organizationId, tenantId] = useSelectedTenant();

  const [ssids, setSSIDs] = useState(null);

  useEffect(() => {
    dot11Service.findAllMonitoredSSIDs(organizationId, tenantId, setSSIDs);
  }, [organizationId, tenantId]);

  if (!ssids) {
    return <LoadingSpinner />
  }

  if (ssids.length === 0) {
    return (
        <div className="alert alert-info">
          No monitored networks created yet.
        </div>
    )
  }

  return (
      <React.Fragment>
        <table className="table table-sm table-hover table-striped">
          <thead>
          <tr>
            <th>SSID</th>
            <th>Status</th>
            <th>Created</th>
            <th>Updated</th>
          </tr>
          </thead>
          <tbody>
          {ssids.map(function (ssid, i) {
            return (
              <tr key={"monitoredssid-" + i}>
                <td><Link to={ApiRoutes.DOT11.MONITORING.SSID_DETAILS(ssid.uuid)}>{ssid.ssid}</Link></td>
                <td><MonitoredNetworkStatus ssid={ssid} /></td>
                <td title={moment(ssid.created_at)}>
                  {moment(ssid.created_at).fromNow()}
                </td>
                <td title={moment(ssid.updated_at)}>
                  {moment(ssid.updated_at).fromNow()}
                </td>
              </tr>
            )
          })}
          </tbody>
        </table>
      </React.Fragment>
  )
}

export default MonitoredNetworksTable;