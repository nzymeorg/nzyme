import React, {useContext} from "react";
import numeral from "numeral";
import {toast} from "react-toastify";
import useSelectedTenant from "../../system/tenantselector/useSelectedTenant";
import SectionMenuBar from "../../shared/SectionMenuBar";
import ApiRoutes from "../../../util/ApiRoutes";
import CardTitleWithControls from "../../shared/CardTitleWithControls";
import usePageTitle from "../../../util/UsePageTitle";
import {RECON_MENU_ITEMS} from "./ReconMenuItems";
import ReconConfiguration from "./ReconConfiguration";
import ReconService from "../../../services/ethernet/ReconService";
import {UserContext} from "../../../App";
import {userHasPermission} from "../../../util/Tools";

const reconService = new ReconService();

export default function ReconSettingsPage() {

  usePageTitle("Ethernet Recon Settings");

  const [organizationId, tenantId] = useSelectedTenant();
  const user = useContext(UserContext);
  const canManage = userHasPermission(user, "ethernet_monitoring_manage");

  const onDeleteAll = (e) => {
    e.preventDefault();

    if (!confirm("Really delete ALL scans of this tenant, including their targets? This cannot be undone. " +
        "Scans are detected again if the devices keep probing.")) {
      return;
    }

    reconService.deleteAllScans(organizationId, tenantId, (response) => {
      toast.success(numeral(response.data.deleted_count).format("0,0") + " scans deleted.");
    });
  }

  return (
    <React.Fragment>
      <div className="row">
        <div className="col-md-10">
          <SectionMenuBar items={RECON_MENU_ITEMS}
                          activeRoute={ApiRoutes.ETHERNET.RECON.SCANS.SETTINGS} />
        </div>

        <div className="col-md-2">
          <a href="https://go.nzyme.org/ethernet-recon-config" className="btn btn-secondary float-end">Help</a>
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-xl-12 col-xxl-6">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title="Detection"
                                     helpLink="https://go.nzyme.org/ethernet-recon-config" />

              <p className="text-muted mb-2">
                Detection runs every minute and looks at all connections in the detection window.
              </p>

              <ReconConfiguration organizationId={organizationId} tenantId={tenantId} section="detection" />
            </div>
          </div>
        </div>

        <div className="col-xl-12 col-xxl-6">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title="Data Sources"
                                     helpLink="https://go.nzyme.org/ethernet-recon-config" />

              <p className="text-muted mb-2">These settings control which traffic is analyzed for scans.</p>

              <ReconConfiguration organizationId={organizationId} tenantId={tenantId} section="sources" />
            </div>
          </div>

          <div className="card mt-3">
            <div className="card-body">
              <CardTitleWithControls title="Alerting" />

              <p className="text-muted">
                Detection alerts raised by scan detection. An alert is raised once per scan when it is first
                detected, not again while it continues.
              </p>

              <ReconConfiguration organizationId={organizationId} tenantId={tenantId} section="alerting" />
            </div>
          </div>

          {canManage ?
            <div className="card mt-3">
              <div className="card-body">
                <CardTitleWithControls title="Delete All Scans" slim={true} />

                <p>
                  Deletes every scan of this tenant, including its targets. Device and asset data is not affected.
                  Exclusions and the settings on this page are kept. Scans are detected again if the devices keep
                  probing, unless an exclusion matches them.
                </p>

                <button className="btn btn-sm btn-danger" onClick={onDeleteAll}>Delete All Scans</button>
              </div>
            </div>
            : null}
        </div>
      </div>
    </React.Fragment>
  )

}
