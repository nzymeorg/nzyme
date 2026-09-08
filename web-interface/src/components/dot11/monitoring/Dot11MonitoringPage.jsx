import React from "react";
import ApiRoutes from "../../../util/ApiRoutes";
import MonitoredNetworksTable from "./MonitoredNetworksTable";
import SectionMenuBar from "../../shared/SectionMenuBar";
import {MONITORING_MENU_ITEMS} from "./Dot11MonitoringMenuItems";
import usePageTitle from "../../../util/UsePageTitle";

function Dot11MonitoringPage() {

  usePageTitle("WiFi Monitoring");

  return (
    <React.Fragment>
      <div className="row">
        <div className="col-md-10">
          <SectionMenuBar items={MONITORING_MENU_ITEMS}
                          activeRoute={ApiRoutes.DOT11.MONITORING.INDEX} />
        </div>

        <div className="col-md-2">
          <a href="https://go.nzyme.org/wifi-network-monitoring" className="btn btn-secondary float-end">Help</a>
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-xl-12 col-xxl-6">
          <div className="card">
            <div className="card-body">
              <h3>All Monitored Networks</h3>

              <MonitoredNetworksTable />

              <a href={ApiRoutes.DOT11.MONITORING.CREATE} className="btn btn-secondary btn-sm">
                Create Monitored Network
              </a>
            </div>
          </div>
        </div>
      </div>
    </React.Fragment>
  )
}

export default Dot11MonitoringPage;