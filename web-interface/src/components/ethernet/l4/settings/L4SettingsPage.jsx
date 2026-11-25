import React, {useEffect, useState} from "react";
import useSelectedTenant from "../../../system/tenantselector/useSelectedTenant";
import SectionMenuBar from "../../../shared/SectionMenuBar";
import ApiRoutes from "../../../../util/ApiRoutes";
import CardTitleWithControls from "../../../shared/CardTitleWithControls";
import usePageTitle from "../../../../util/UsePageTitle";
import {L4_MENU_ITEMS} from "../L4MenuItems";
import L4Configuration from "./L4Configuration";
import L4TapSettings from "./L4TapSettings";
import L4InternalNetworks from "./L4InternalNetworks";
import L4LearnedInternalNetworks from "./L4LearnedInternalNetworks";
import {Link} from "react-router-dom";
import L4Service from "../../../../services/ethernet/L4Service";

const l4Service = new L4Service();

const PENDING_REFRESH_SECONDS = 10;

export default function L4SettingsPage() {

  usePageTitle("TCP/UDP Settings");

  const [organizationId, tenantId] = useSelectedTenant();

  const [revision, setRevision] = useState(0);
  const [config, setConfig] = useState(null);

  useEffect(() => {
    setConfig(null);
    l4Service.getConfiguration(organizationId, tenantId, setConfig);
  }, [organizationId, tenantId, revision]);

  useEffect(() => {
    if (!config || config.pending_rewrites === 0) {
      return;
    }

    const timeout = setTimeout(
        () => l4Service.getConfiguration(organizationId, tenantId, setConfig), PENDING_REFRESH_SECONDS * 1000
    );

    return () => clearTimeout(timeout);
  }, [config, organizationId, tenantId]);

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
                These settings control which addresses are recorded as internal. A change also applies to records
                that are stored already. Taps have the CGNAT setting and the internal networks as well, which
                should be set to the same values.
              </p>

              <L4Configuration config={config}
                               organizationId={organizationId}
                               tenantId={tenantId}
                               setRevision={setRevision} />
            </div>
          </div>
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-xl-12 col-xxl-8">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title="Internal Networks"
                                     helpLink="https://go.nzyme.org/ethernet-internal-addresses" />

              <p className="text-muted mb-2">
                Private, unique local and link-local addresses are always internal. Addresses in these networks
                are recorded as internal as well.
              </p>

              <L4InternalNetworks config={config}
                                  organizationId={organizationId}
                                  tenantId={tenantId}
                                  setRevision={setRevision} />
            </div>
          </div>
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-xl-12 col-xxl-8">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title="Learned Internal Networks"
                                     helpLink="https://go.nzyme.org/ethernet-internal-addresses" />

              <p className="text-muted mb-2">
                The IPv6 prefixes that trusted routers advertise are recorded as internal as well. Routers are
                trusted on the <Link to={ApiRoutes.ETHERNET.SPOOFING.DHCP.INDEX}>DHCP and Routers</Link> page of the
                spoofing section. A router that is not trusted adds nothing here. Taps do not know these networks.
              </p>

              <L4LearnedInternalNetworks config={config} />
            </div>
          </div>
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-xl-12 col-xxl-8">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title="Taps With Different Values"
                                     helpLink="https://go.nzyme.org/ethernet-l4-config-taps" />

              <L4TapSettings organizationId={organizationId} tenantId={tenantId} revision={revision} />
            </div>
          </div>
        </div>
      </div>
    </React.Fragment>
  )

}
