import SectionMenuBar from "../../shared/SectionMenuBar";
import ApiRoutes from "../../../util/ApiRoutes";
import React, {useEffect, useState} from "react";
import {BLUETOOTH_MONITORING_MENU_ITEMS} from "./BluetoothMonitoringMenuItems";
import usePageTitle from "../../../util/UsePageTitle";
import useSelectedTenant from "../../system/tenantselector/useSelectedTenant";
import MonitorsService from "../../../services/MonitorsService";
import MonitorsTable from "../../monitors/shared/MonitorsTable";

const monitorsService = new MonitorsService();

export default function BluetoothMonitorsPage() {

  usePageTitle("Bluetooth Monitors");

  const [organizationId, tenantId] = useSelectedTenant();

  const [monitors, setMonitors] = useState(null);
  const [page, setPage] = useState(1);

  const perPage = 25;

  useEffect(() => {
    monitorsService.findAllOfType(
      "BLUETOOTH_DEVICE", organizationId, tenantId, perPage, (page-1)*perPage, setMonitors
    )
  }, [page, organizationId, tenantId])

  return (
    <React.Fragment>
      <div className="row">
        <div className="col-md-10">
          <SectionMenuBar items={BLUETOOTH_MONITORING_MENU_ITEMS}
                          activeRoute={ApiRoutes.BLUETOOTH.MONITORING.MONITORS.INDEX}/>
        </div>

        <div className="col-md-2">
          <a href="https://go.nzyme.org/bluetooth-monitors" className="btn btn-secondary float-end">Help</a>
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-xl-12 col-xxl-6">
          <div className="card">
            <div className="card-body">
              <h3 style={{display: "inline-block"}}>Device Monitors</h3>

              <MonitorsTable monitors={monitors} page={page} setPage={setPage} perPage={perPage} />
            </div>
          </div>
        </div>
      </div>
    </React.Fragment>
  )

}