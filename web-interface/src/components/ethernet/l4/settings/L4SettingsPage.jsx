import React, {useState} from "react";
import useSelectedTenant from "../../../system/tenantselector/useSelectedTenant";
import SectionMenuBar from "../../../shared/SectionMenuBar";
import ApiRoutes from "../../../../util/ApiRoutes";
import CardTitleWithControls from "../../../shared/CardTitleWithControls";
import usePageTitle from "../../../../util/UsePageTitle";
import {L4_MENU_ITEMS} from "../L4MenuItems";
import L4Configuration from "./L4Configuration";
import L4TapSettings from "./L4TapSettings";

export default function L4SettingsPage() {

  usePageTitle("TCP/UDP Settings");

  const [organizationId, tenantId] = useSelectedTenant();

  const [revision, setRevision] = useState(0);

  return (
    <React.Fragment>
      <div className="row">
        <div className="col-md-12">
          <SectionMenuBar items={L4_MENU_ITEMS}
                          activeRoute={ApiRoutes.ETHERNET.L4.SETTINGS} />
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-xl-12 col-xxl-8">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title="TCP/UDP Settings"
                                     helpLink="https://go.nzyme.org/ethernet-internal-addresses" />

              <p className="text-muted mb-2">
                These settings control which addresses are recorded as internal. Taps have the same setting,
                which should be set to the same value.
              </p>

              <L4Configuration organizationId={organizationId}
                               tenantId={tenantId}
                               revision={revision}
                               setRevision={setRevision} />
            </div>
          </div>
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-xl-12 col-xxl-8">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title="Taps With a Different Value"
                                     helpLink="https://go.nzyme.org/ethernet-l4-config-taps" />

              <L4TapSettings organizationId={organizationId} tenantId={tenantId} revision={revision} />
            </div>
          </div>
        </div>
      </div>
    </React.Fragment>
  )

}
