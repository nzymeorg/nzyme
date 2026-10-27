import React, {useContext, useEffect, useState} from "react";
import numeral from "numeral";
import {toast} from "react-toastify";
import useSelectedTenant from "../../../system/tenantselector/useSelectedTenant";
import SectionMenuBar from "../../../shared/SectionMenuBar";
import ApiRoutes from "../../../../util/ApiRoutes";
import CardTitleWithControls from "../../../shared/CardTitleWithControls";
import usePageTitle from "../../../../util/UsePageTitle";
import LoadingSpinner from "../../../misc/LoadingSpinner";
import {DNS_MENU_ITEMS} from "../DNSMenuItems";
import DnsTunnelConfiguration from "./DnsTunnelConfiguration";
import DnsTunnelsService from "../../../../services/ethernet/DnsTunnelsService";
import {UserContext} from "../../../../App";
import {userHasPermission} from "../../../../util/Tools";

const dnsTunnelsService = new DnsTunnelsService();

export default function DnsTunnelSettingsPage() {

  usePageTitle("Ethernet DNS Tunnel Settings");

  const [organizationId, tenantId] = useSelectedTenant();
  const user = useContext(UserContext);
  const canManage = userHasPermission(user, "ethernet_monitoring_manage");

  const [config, setConfig] = useState(null);
  const [revision, setRevision] = useState(0);

  useEffect(() => {
    setConfig(null);
    dnsTunnelsService.getConfiguration(organizationId, tenantId, setConfig);
  }, [organizationId, tenantId, revision]);

  const onDeleteAll = (e) => {
    e.preventDefault();

    if (!confirm("Really delete ALL DNS tunnel findings of this tenant? This cannot be undone. Findings are " +
        "detected again if the clients keep querying the domains.")) {
      return;
    }

    dnsTunnelsService.deleteAllFindings(organizationId, tenantId, (response) => {
      toast.success(numeral(response.data.deleted).format("0,0") + " findings deleted.");
    });
  }

  const sectionCard = (section) => {
    return (
      <div className="card mb-3" key={section.key}>
        <div className="card-body">
          <CardTitleWithControls title={section.title} />

          <p className="text-muted mb-2">
            Runs every minute over the DNS queries of all taps of the tenant. A client that looked up at least the
            minimum number of distinct names under one registered domain inside the detection window is examined.
            It becomes a finding when the names show at least one of these indicators: their average entropy
            below the registered domain is at or above the entropy threshold, their average length below the
            registered domain is at or above the length threshold, or the share of TXT, NULL, CNAME, MX, KEY and
            SRV queries is at or above the record type share threshold. A finding stays open while the client
            keeps querying the domain inside the window.
          </p>
          <p className="text-muted">
            The entropy threshold is configured in tenths of a bit per character, so 35 means 3.5 bits per
            character. Antivirus and reputation lookups, some CDNs and other services that encode data in DNS
            names on purpose will look like tunnels; add an exclusion for their domains.
          </p>

          <h4 className="mt-3">Detection</h4>
          <DnsTunnelConfiguration organizationId={organizationId}
                                  tenantId={tenantId}
                                  entries={section.detection}
                                  setConfig={setConfig}
                                  setRevision={setRevision} />

          <h4 className="mt-4">Alerting</h4>
          <p className="text-muted mb-0">
            An alert is raised once per finding when it is first detected, not again while it continues.
          </p>
          <DnsTunnelConfiguration organizationId={organizationId}
                                  tenantId={tenantId}
                                  entries={section.alerting}
                                  setConfig={setConfig}
                                  setRevision={setRevision} />

          {canManage ?
            <React.Fragment>
              <h4 className="mt-4">Delete All Findings</h4>
              <p className="text-muted">
                Deletes every DNS tunnel finding of this tenant. Exclusions and the settings above are kept.
                Findings are detected again if the clients keep querying the domains, unless an exclusion
                matches them.
              </p>
              <button className="btn btn-sm btn-danger" onClick={onDeleteAll}>
                Delete All {section.title} Findings
              </button>
            </React.Fragment> : null}
        </div>
      </div>
    )
  }

  return (
    <React.Fragment>
      <div className="row">
        <div className="col-md-10">
          <SectionMenuBar items={DNS_MENU_ITEMS}
                          activeRoute={ApiRoutes.ETHERNET.DNS.TUNNELS.SETTINGS} />
        </div>

        <div className="col-md-2">
          <a href="https://go.nzyme.org/ethernet-dns-tunnels-config" className="btn btn-secondary float-end">Help</a>
        </div>
      </div>

      {!config ?
        <div className="row mt-3">
          <div className="col-12"><LoadingSpinner /></div>
        </div>
        : <div className="row mt-3">
          <div className="col-xl-12 col-xxl-8">
            {config.sections.map(sectionCard)}
          </div>
        </div>}
    </React.Fragment>
  )

}
