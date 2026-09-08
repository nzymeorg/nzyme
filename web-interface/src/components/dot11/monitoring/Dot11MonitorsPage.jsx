import React, {useEffect, useState} from 'react';
import usePageTitle from "../../../util/UsePageTitle";
import SectionMenuBar from "../../shared/SectionMenuBar";
import {MONITORING_MENU_ITEMS} from "./Dot11MonitoringMenuItems";
import ApiRoutes from "../../../util/ApiRoutes";
import MonitorsTable from "../../monitors/shared/MonitorsTable";
import MonitorsService from "../../../services/MonitorsService";
import useSelectedTenant from "../../system/tenantselector/useSelectedTenant";

const monitorsService = new MonitorsService();

export default function Dot11MonitorsPage() {

  usePageTitle("WiFi Monitors");

  const [organizationId, tenantId] = useSelectedTenant();

  const [bssidMonitors, setBssidMonitors] = useState(null);
  const [connectedClientMonitors, setConnectedClientMonitors] = useState(null);
  const [disconnectedClientMonitors, setDisconnectedClientMonitors] = useState(null);

  const [bssidPage, setBssidPage] = useState(1);
  const [connectedClientPage, setConnectedClientPage] = useState(1);
  const [disconnectedClientPage, setDisconnectedClientPage] = useState(1);

  const perPage = 10;

  useEffect(() => {
    monitorsService.findAllOfType(
      "DOT11_BSSID", organizationId, tenantId, perPage, (bssidPage-1)*perPage, setBssidMonitors
    )
  }, [bssidPage, organizationId, tenantId])

  useEffect(() => {
    monitorsService.findAllOfType(
      "DOT11_CLIENT_CONNECTED", organizationId, tenantId, perPage, (connectedClientPage-1)*perPage, setConnectedClientMonitors
    )
  }, [connectedClientPage, organizationId, tenantId])

  useEffect(() => {
    monitorsService.findAllOfType(
      "DOT11_CLIENT_DISCONNECTED", organizationId, tenantId, perPage, (disconnectedClientPage-1)*perPage, setDisconnectedClientMonitors
    )
  }, [disconnectedClientPage, organizationId, tenantId])

  return (
    <React.Fragment>
      <div className="row">
        <div className="col-md-10">
          <SectionMenuBar items={MONITORING_MENU_ITEMS}
                          activeRoute={ApiRoutes.DOT11.MONITORING.MONITORS.INDEX}/>
        </div>

        <div className="col-md-2">
          <a href="https://go.nzyme.org/wifi-monitors" className="btn btn-secondary float-end">Help</a>
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-xl-12 col-xxl-6">
          <div className="card">
            <div className="card-body">
              <h3 style={{display: "inline-block"}}>Access Point Monitors</h3>

              <MonitorsTable monitors={bssidMonitors} page={bssidPage} setPage={setBssidPage} perPage={perPage} />
            </div>
          </div>
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-xl-12 col-xxl-6">
          <div className="card">
            <div className="card-body">
              <h3 style={{display: "inline-block"}}>Connected Client Monitors</h3>

              <MonitorsTable monitors={connectedClientMonitors} page={connectedClientPage} setPage={setConnectedClientPage} perPage={perPage} />
            </div>
          </div>
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-xl-12 col-xxl-6">
          <div className="card">
            <div className="card-body">
              <h3 style={{display: "inline-block"}}>Disconnected Client Monitors</h3>

              <MonitorsTable monitors={disconnectedClientMonitors} page={disconnectedClientPage} setPage={setDisconnectedClientPage} perPage={perPage} />
            </div>
          </div>
        </div>
      </div>
    </React.Fragment>
  )

}