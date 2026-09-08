import SectionMenuBar from "../../shared/SectionMenuBar";
import ApiRoutes from "../../../util/ApiRoutes";
import React from "react";
import {BLUETOOTH_MONITORING_MENU_ITEMS} from "./BluetoothMonitoringMenuItems";

export default function BluetoothMonitorsPage() {

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
            </div>
          </div>
        </div>
      </div>
    </React.Fragment>
  )

}