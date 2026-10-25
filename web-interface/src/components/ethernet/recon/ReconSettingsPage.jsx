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
              <CardTitleWithControls title="Detection" />

              <p className="text-muted mb-2">
                Detection runs every minute and looks at all connections in the detection window. A device is
                flagged when:
              </p>
              <ul className="text-muted">
                <li><strong>Port scanning:</strong> it probed at least the minimum number of ports on one host.</li>
                <li><strong>Host sweeping:</strong> it probed at least the minimum number of hosts.</li>
                <li><strong>ARP sweeps:</strong> it asked for at least the minimum number of addresses. ARP sweeps
                  are always local.</li>
              </ul>
              <p className="text-muted mb-0">
                All detections also require that at least the minimum share of probes got no answer or was refused,
                which separates scanning from a client that simply talks to many servers. A scan stays open while
                the device keeps probing within the window. Scans from devices outside your site-local networks are
                ignored by default, because the internet constantly probes any reachable address. Scans against
                external targets are ignored by default as well, because mail servers trying a provider&apos;s pool
                of addresses, clients reaching CDNs and peer-to-peer software all look like sweeps of the internet;
                enable it to also catch internal devices scanning the internet.
              </p>

              <ReconConfiguration organizationId={organizationId} tenantId={tenantId} section="detection" />
            </div>
          </div>
        </div>

        <div className="col-xl-12 col-xxl-6">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title="Data Sources" />

              <p className="text-muted mb-2">Which traffic is analyzed for scans:</p>
              <ul className="text-muted mb-0">
                <li><strong>TCP sessions</strong> reveal port scans and host sweeps, including whether a port
                  answered, was refused or stayed silent.</li>
                <li><strong>UDP conversations</strong> only reveal whether a target answered. Because one-way UDP
                  is common in normal traffic, probes to the dynamic port range and probes carrying DNS, NTP, STUN
                  and similar payloads are never counted.</li>
                <li><strong>ARP requests</strong> reveal sweeps of the local network segment.</li>
              </ul>

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
