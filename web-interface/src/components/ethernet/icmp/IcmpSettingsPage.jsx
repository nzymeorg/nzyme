import React from "react";
import useSelectedTenant from "../../system/tenantselector/useSelectedTenant";
import SectionMenuBar from "../../shared/SectionMenuBar";
import ApiRoutes from "../../../util/ApiRoutes";
import CardTitleWithControls from "../../shared/CardTitleWithControls";
import usePageTitle from "../../../util/UsePageTitle";
import {ICMP_MENU_ITEMS} from "./IcmpMenuItems";
import IcmpConfiguration from "./IcmpConfiguration";

export default function IcmpSettingsPage() {

  usePageTitle("ICMP Settings");

  const [organizationId, tenantId] = useSelectedTenant();

  return (
    <React.Fragment>
      <div className="row">
        <div className="col-md-10">
          <SectionMenuBar items={ICMP_MENU_ITEMS} activeRoute={ApiRoutes.ETHERNET.ICMP.SETTINGS} />
        </div>

        <div className="col-md-2">
          <a href="https://go.nzyme.org/ethernet-icmp-config" className="btn btn-secondary float-end">Help</a>
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-xl-12 col-xxl-6">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title="Detection" />

              <p className="text-muted mb-2">
                Detection runs every minute over the traffic in the detection window and raises these findings:
              </p>
              <ul className="text-muted">
                <li><strong>Redirects:</strong> every sender, advertised gateway and told host combination is one finding.</li>
                <li><strong>Traceroutes:</strong> time exceeded messages from at least the minimum number of routers,
                  all for the same destination. A traceroute stays open while the host keeps tracing within the window.</li>
                <li><strong>Tunnels:</strong> echo flows whose payloads are larger than the minimum size and that
                  reach the minimum packet count.</li>
              </ul>
              <p className="text-muted mb-0">
                Findings are kept for the retention time. Ping sweeps and OS fingerprinting are detected by recon
                detection and configured there.
              </p>

              <IcmpConfiguration organizationId={organizationId} tenantId={tenantId} section="detection" />
            </div>
          </div>
        </div>

        <div className="col-xl-12 col-xxl-6">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title="Alerting" />

              <p className="text-muted">
                Redirect alerts are on by default because redirects almost never appear legitimately. Traceroute
                alerts are off by default, since administrators run traceroutes routinely.
              </p>

              <IcmpConfiguration organizationId={organizationId} tenantId={tenantId} section="alerting" />
            </div>
          </div>
        </div>
      </div>
    </React.Fragment>
  )

}
