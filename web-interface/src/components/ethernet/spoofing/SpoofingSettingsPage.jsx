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
  arp: (
    <React.Fragment>
      <p className="text-muted mb-2">
        Runs every minute over the ARP packets and the NDP messages of all taps. Every ARP packet
        carries a claim that an IPv4 address belongs to a MAC address, and NDP messages do the same for IPv6
        addresses. A finding is raised when two different MAC addresses claimed the same
        address inside the detection window, the device that claimed it first over the owner lookback was still
        claiming it after the other device started, and the other device claimed it at least the minimum number
        of times inside the window.
      </p>
      <p className="text-muted">
        The overlap requirement separates poisoning from an address that simply changed hands. Router redundancy
        protocols, failover clusters and virtual machine migration can legitimately move an address between MAC
        addresses; add an exclusion for them.
      </p>
    </React.Fragment>
  ),
  dhcp: (
    <React.Fragment>
      <p className="text-muted mb-2">
        Runs over the DHCP and DHCPv6 transactions and the IPv6 router advertisements of all taps.
        Every server that answers clients and every router that advertises itself is learned together with the
        configuration it hands out. The ones seen inside the learning period are trusted automatically; a learning
        period of zero trusts nothing until you do. DHCP, DHCPv6 and router advertisements each have a learning
        period that starts when the first one is seen. A server or router that is seen while it is not trusted is
        a rogue server finding. A trusted one that hands out a gateway, DNS server, domain, subnet mask or prefix
        different from its baseline is a changed configuration finding.
      </p>
      <p className="text-muted">
        End the learning period on the DHCP and Routers tab once every expected server and router is listed, and
        restart it there after replacing one. Transactions are analyzed a few minutes after they end, once all taps have reported
        them.
      </p>
    </React.Fragment>
  ),
  name_resolution: (
    <React.Fragment>
      <p className="text-muted mb-2">
        Runs over the LLMNR, NBT-NS and mDNS answers of all taps. A legitimate device answers
        queries for its own name only. A device that answered more different names than the threshold of the
        protocol allows inside the detection window, pointing them at one of its own addresses, is a finding. A
        device whose answers all point at the same one or two addresses is treated the same way.
      </p>
      <p className="text-muted">
        mDNS gets a higher threshold than LLMNR and NBT-NS because reflectors and devices with several service
        names are common. Raise it further if a legitimate device keeps showing up, or add an exclusion for it.
      </p>
    </React.Fragment>
  ),
  dns: (
    <React.Fragment>
      <p className="text-muted mb-2">
        Runs every minute over the DNS transactions of all taps. Every reply a tap records carries
        the MAC address it actually came from. For every resolver address, the MAC address that delivered its
        replies first over the owner lookback is the legitimate owner: the resolver itself or the gateway in front
        of it. A reply with the resolver&apos;s address from another MAC address, while the owner is still
        answering, is a forged reply. A device that sent at least the minimum number of forged replies inside the
        detection window is a finding.
      </p>
      <p className="text-muted">
        The owner&apos;s reply to the same query, when a tap saw one, decides whether the forged reply won the
        race. Router redundancy, load balancers with direct server return and a replaced gateway can legitimately
        deliver a resolver&apos;s replies from more than one MAC address; add an exclusion for them.
      </p>
    </React.Fragment>
  )
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
          <CardTitleWithControls title={section.title} />

          {DESCRIPTIONS[section.key]}

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
