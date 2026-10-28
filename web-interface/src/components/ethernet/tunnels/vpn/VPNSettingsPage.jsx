import React, {useEffect, useState} from "react";
import useSelectedTenant from "../../../system/tenantselector/useSelectedTenant";
import SectionMenuBar from "../../../shared/SectionMenuBar";
import ApiRoutes from "../../../../util/ApiRoutes";
import CardTitleWithControls from "../../../shared/CardTitleWithControls";
import usePageTitle from "../../../../util/UsePageTitle";
import LoadingSpinner from "../../../misc/LoadingSpinner";
import {TUNNELS_MENU_ITEMS} from "../TunnelsMenuItems";
import VPNConfiguration from "./VPNConfiguration";
import VpnService from "../../../../services/ethernet/VpnService";

const vpnService = new VpnService();

export default function VPNSettingsPage() {

  usePageTitle("VPN Connection Settings");

  const [organizationId, tenantId] = useSelectedTenant();

  const [config, setConfig] = useState(null);
  const [revision, setRevision] = useState(0);

  useEffect(() => {
    setConfig(null);
    vpnService.getConfiguration(organizationId, tenantId, setConfig);
  }, [organizationId, tenantId, revision]);

  return (
    <React.Fragment>
      <div className="row">
        <div className="col-md-10">
          <SectionMenuBar items={TUNNELS_MENU_ITEMS}
                          activeRoute={ApiRoutes.ETHERNET.TUNNELS.VPN.SETTINGS} />
        </div>

        <div className="col-md-2">
          <a href="https://go.nzyme.org/ethernet-vpn-config" className="btn btn-secondary float-end">Help</a>
        </div>
      </div>

      {!config ?
        <div className="row mt-3">
          <div className="col-12"><LoadingSpinner /></div>
        </div>
        : <div className="row mt-3">
          <div className="col-xl-12 col-xxl-8">
            <div className="card">
              <div className="card-body">
                <CardTitleWithControls title="VPN Connections" />

                <h4 className="mt-3">Display</h4>
                <p className="text-muted mb-0">
                  Tailscale is identified from its discovery packets on WireGuard connections and from its DERP
                  relay connections. When enabled, the VPN connections page opens with a filter that hides
                  connections identified as Tailscale. The filter can be removed like any other, and saved monitors
                  are not affected.
                </p>
                <VPNConfiguration organizationId={organizationId}
                                  tenantId={tenantId}
                                  entries={config.display}
                                  setConfig={setConfig}
                                  setRevision={setRevision} />
              </div>
            </div>
          </div>
        </div>}
    </React.Fragment>
  )

}
