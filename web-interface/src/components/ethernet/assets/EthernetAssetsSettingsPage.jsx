import React from "react";
import useSelectedTenant from "../../system/tenantselector/useSelectedTenant";
import {ASSETS_MENU_ITEMS} from "./AssetsMenuItems";
import CardTitleWithControls from "../../shared/CardTitleWithControls";
import SectionMenuBar from "../../shared/SectionMenuBar";
import ApiRoutes from "../../../util/ApiRoutes";
import AssetsConfiguration from "./AssetsConfiguration";
import usePageTitle from "../../../util/UsePageTitle";
import AssetIdentitySettings from "./identity/AssetIdentitySettings";

export default function EthernetAssetsSettingsPage() {

  usePageTitle("Ethernet Assets Settings");

  const [organizationId, tenantId] = useSelectedTenant();

  return (
    <React.Fragment>
      <div className="row">
        <div className="col-md-12">
          <SectionMenuBar items={ASSETS_MENU_ITEMS}
                          activeRoute={ApiRoutes.ETHERNET.ASSETS.SETTINGS} />
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-xl-12 col-xxl-6">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title="Asset Management Settings" />

              <AssetsConfiguration organizationId={organizationId} tenantId={tenantId} />
            </div>
          </div>
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-12">
          <h2>Identity Detection</h2>
          <p className="text-muted">
            Detections of who is behind a MAC address. Each detection can be switched off, tuned and excluded on
            its own, and each decides for itself whether a new finding raises an alert.
          </p>
        </div>
      </div>

      <div className="row">
        <div className="col-xl-12 col-xxl-8">
          <AssetIdentitySettings organizationId={organizationId} tenantId={tenantId} />
        </div>
      </div>
    </React.Fragment>
  )

}