import React, {useContext, useEffect, useState} from "react";
import numeral from "numeral";
import {toast} from "react-toastify";
import useSelectedTenant from "../../system/tenantselector/useSelectedTenant";
import SectionMenuBar from "../../shared/SectionMenuBar";
import ApiRoutes from "../../../util/ApiRoutes";
import CardTitleWithControls from "../../shared/CardTitleWithControls";
import usePageTitle from "../../../util/UsePageTitle";
import LoadingSpinner from "../../misc/LoadingSpinner";
import {SPOOFING_MENU_ITEMS} from "./SpoofingMenuItems";
import SpoofingConfiguration from "./SpoofingConfiguration";
import SpoofingService from "../../../services/ethernet/SpoofingService";
import {UserContext} from "../../../App";
import {userHasPermission} from "../../../util/Tools";

const spoofingService = new SpoofingService();

const DESCRIPTIONS = {
  arp: "Detects addresses that two different MAC addresses claim at the same time, over ARP and NDP.",
  dhcp: "Detects DHCP servers, DHCPv6 servers and IPv6 routers that are not trusted or that hand out a changed " +
    "configuration.",
  name_resolution: "Detects devices that answer LLMNR, NBT-NS and mDNS queries for names that are not their own.",
  dns: "Detects devices that answer DNS queries with the address of a resolver that they are not."
};

const HELP_LINKS = {
  arp: "https://go.nzyme.org/ethernet-spoofing-arp",
  dhcp: "https://go.nzyme.org/ethernet-spoofing-dhcp",
  name_resolution: "https://go.nzyme.org/ethernet-spoofing-nameres",
  dns: "https://go.nzyme.org/ethernet-spoofing-dns"
};

const DELETE_DESCRIPTIONS = {
  arp: "Deletes every ARP and NDP poisoning finding, including its victims. Device and asset data is not " +
    "affected. Exclusions and the settings above are kept. Findings are detected again if the devices keep " +
    "claiming addresses, unless an exclusion matches them.",
  dhcp: "Deletes every DHCP, DHCPv6 and router finding, including its victims. Learned servers and routers and " +
    "their trust status are kept, as are exclusions and the settings above. Findings are detected again if " +
    "untrusted servers or routers keep answering.",
  name_resolution: "Deletes every name resolution poisoning finding, including its victims. " +
    "Exclusions and the settings above are kept. Findings are detected again if the devices keep answering name " +
    "queries, unless an exclusion matches them.",
  dns: "Deletes every DNS spoofing finding, including its victims. Exclusions and the settings " +
    "above are kept. Findings are detected again if the devices keep forging replies, unless an exclusion " +
    "matches them."
};

const DELETE_CONFIRMATIONS = {
  arp: "Really delete ALL ARP and NDP poisoning findings, including their victims? This cannot be undone. " +
    "Findings are detected again if the devices keep claiming addresses.",
  dhcp: "Really delete ALL DHCP, DHCPv6 and router findings, including their victims? This cannot be undone. " +
    "Learned servers and routers are kept. Findings are detected again if untrusted servers or routers keep answering.",
  name_resolution: "Really delete ALL name resolution poisoning findings, including their victims? " +
    "This cannot be undone. Findings are detected again if the devices keep answering name queries.",
  dns: "Really delete ALL DNS spoofing findings, including their victims? This cannot be undone. " +
    "Findings are detected again if the devices keep forging replies."
};

export default function SpoofingSettingsPage() {

  usePageTitle("Ethernet Spoofing Settings");

  const [organizationId, tenantId] = useSelectedTenant();
  const user = useContext(UserContext);
  const canManage = userHasPermission(user, "ethernet_monitoring_manage");

  const [config, setConfig] = useState(null);
  const [revision, setRevision] = useState(0);

  useEffect(() => {
    setConfig(null);
    spoofingService.getConfiguration(organizationId, tenantId, setConfig);
  }, [organizationId, tenantId, revision]);

  const deleteAll = (key) => {
    switch (key) {
      case "arp": return spoofingService.deleteAllArpFindings.bind(spoofingService);
      case "dhcp": return spoofingService.deleteAllDhcpFindings.bind(spoofingService);
      case "name_resolution": return spoofingService.deleteAllNameResolutionFindings.bind(spoofingService);
      case "dns": return spoofingService.deleteAllDnsFindings.bind(spoofingService);
      default: return null;
    }
  }

  const onDeleteAll = (e, key) => {
    e.preventDefault();

    const call = deleteAll(key);
    if (!call || !confirm(DELETE_CONFIRMATIONS[key])) {
      return;
    }

    call(organizationId, tenantId, (response) => {
      toast.success(numeral(response.data.deleted_count).format("0,0") + " findings deleted.");
    });
  }

  const sectionCard = (section) => {
    return (
      <div className="card mb-3" key={section.key}>
        <div className="card-body">
          <CardTitleWithControls title={section.title} helpLink={HELP_LINKS[section.key]} />

          <p className="text-muted">{DESCRIPTIONS[section.key]}</p>

          <h4 className="mt-3">Detection</h4>
          <SpoofingConfiguration organizationId={organizationId}
                                 tenantId={tenantId}
                                 entries={section.detection}
                                 setConfig={setConfig}
                                 setRevision={setRevision} />

          <h4 className="mt-4">Alerting</h4>
          <p className="text-muted mb-0">
            An alert is raised once per finding when it is first detected, not again while it continues.
          </p>
          <SpoofingConfiguration organizationId={organizationId}
                                 tenantId={tenantId}
                                 entries={section.alerting}
                                 setConfig={setConfig}
                                 setRevision={setRevision} />

          {canManage ?
            <React.Fragment>
              <h4 className="mt-4">Delete All Findings</h4>
              <p className="text-muted">{DELETE_DESCRIPTIONS[section.key]}</p>
              <button className="btn btn-sm btn-danger" onClick={(e) => onDeleteAll(e, section.key)}>
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
          <SectionMenuBar items={SPOOFING_MENU_ITEMS}
                          activeRoute={ApiRoutes.ETHERNET.SPOOFING.SETTINGS} />
        </div>

        <div className="col-md-2">
          <a href="https://go.nzyme.org/ethernet-spoofing-config" className="btn btn-secondary float-end">Help</a>
        </div>
      </div>

      {!config ?
        <div className="row mt-3">
          <div className="col-12"><LoadingSpinner /></div>
        </div>
        : <div className="row mt-3">
          <div className="col-xl-12 col-xxl-6">
            {config.sections.filter((s, i) => i % 2 === 0).map(sectionCard)}
          </div>
          <div className="col-xl-12 col-xxl-6">
            {config.sections.filter((s, i) => i % 2 === 1).map(sectionCard)}
          </div>
        </div>}
    </React.Fragment>
  )

}
