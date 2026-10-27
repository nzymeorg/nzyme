import React, {useContext} from "react";
import numeral from "numeral";
import {toast} from "react-toastify";
import useSelectedTenant from "../../system/tenantselector/useSelectedTenant";
import SectionMenuBar from "../../shared/SectionMenuBar";
import ApiRoutes from "../../../util/ApiRoutes";
import CardTitleWithControls from "../../shared/CardTitleWithControls";
import usePageTitle from "../../../util/UsePageTitle";
import {SPOOFING_MENU_ITEMS} from "./SpoofingMenuItems";
import SpoofingConfiguration from "./SpoofingConfiguration";
import SpoofingService from "../../../services/ethernet/SpoofingService";
import {UserContext} from "../../../App";
import {userHasPermission} from "../../../util/Tools";

const spoofingService = new SpoofingService();

export default function SpoofingSettingsPage() {

  usePageTitle("Ethernet Spoofing Settings");

  const [organizationId, tenantId] = useSelectedTenant();
  const user = useContext(UserContext);
  const canManage = userHasPermission(user, "ethernet_monitoring_manage");

  const onDeleteAllArp = (e) => {
    e.preventDefault();

    if (!confirm("Really delete ALL ARP poisoning findings of this tenant, including their victims? This cannot " +
        "be undone. Findings are detected again if the devices keep claiming addresses.")) {
      return;
    }

    spoofingService.deleteAllArpFindings(organizationId, tenantId, (response) => {
      toast.success(numeral(response.data.deleted_count).format("0,0") + " findings deleted.");
    });
  }

  const onDeleteAllDhcp = (e) => {
    e.preventDefault();

    if (!confirm("Really delete ALL DHCP spoofing findings of this tenant, including their victims? This cannot " +
        "be undone. Learned servers are kept. Findings are detected again if untrusted servers keep answering.")) {
      return;
    }

    spoofingService.deleteAllDhcpFindings(organizationId, tenantId, (response) => {
      toast.success(numeral(response.data.deleted_count).format("0,0") + " findings deleted.");
    });
  }

  const onDeleteAllNameResolution = (e) => {
    e.preventDefault();

    if (!confirm("Really delete ALL name resolution poisoning findings of this tenant, including their victims? " +
        "This cannot be undone. Findings are detected again if the devices keep answering name queries.")) {
      return;
    }

    spoofingService.deleteAllNameResolutionFindings(organizationId, tenantId, (response) => {
      toast.success(numeral(response.data.deleted_count).format("0,0") + " findings deleted.");
    });
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

      <div className="row mt-3">
        <div className="col-xl-12 col-xxl-6">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title="Detection" />

              <p className="text-muted mb-2">
                Detection runs every minute over the ARP packets of all taps of the tenant. Every ARP packet
                carries a claim that an address belongs to a MAC address. A finding is raised when:
              </p>
              <ul className="text-muted">
                <li>two different MAC addresses claimed the same address inside the detection window,</li>
                <li>the device that claimed the address first, over the owner lookback period, was still claiming
                  it after the other device started, and</li>
                <li>the other device claimed it at least the minimum number of times inside the window.</li>
              </ul>
              <p className="text-muted">
                The overlap requirement is what separates poisoning from an address that simply changed hands, for
                example after a DHCP lease expired. A finding stays open while the spoofer keeps claiming the
                address within the window. Router redundancy protocols, failover clusters and virtual machine
                migration can legitimately move an address between MAC addresses; add an exclusion for them.
              </p>

              <p className="text-muted mb-2">
                DHCP spoofing detection runs over the DHCP transactions of all taps of the tenant. Every server
                that answers clients is learned together with the configuration it hands out:
              </p>
              <ul className="text-muted">
                <li>Servers that answer inside the learning period after the first server was seen are trusted
                  automatically. A learning period of zero trusts nothing until you do. End the learning period on
                  the DHCP tab once every expected server is listed, and restart it there after replacing a
                  server.</li>
                <li>A server that answers while it is not trusted is a rogue server finding. Trust it on the DHCP
                  tab if it is legitimate.</li>
                <li>A trusted server that hands out a gateway, DNS server, domain or subnet mask different from its
                  baseline is a changed configuration finding. Accept the new configuration on the DHCP tab if the
                  change was intended.</li>
              </ul>
              <p className="text-muted">
                A finding stays open while the server keeps answering within the detection window. Transactions are
                analyzed a few minutes after they end, once all taps have reported them.
              </p>

              <p className="text-muted mb-2">
                Name resolution poisoning detection runs over the LLMNR, NBT-NS and mDNS answers of all taps of the
                tenant. A legitimate device answers queries for its own name only:
              </p>
              <ul className="text-muted">
                <li>A device that answered more different names than the threshold of the protocol allows inside the
                  detection window, pointing them at one of its own addresses, is a finding.</li>
                <li>A device whose answers all point at the same one or two addresses is treated the same way, which
                  covers a poisoner that hands out the address of another machine.</li>
                <li>mDNS gets a higher threshold than LLMNR and NBT-NS because reflectors and devices with several
                  service names are common. Raise it further if a legitimate device keeps showing up, or add an
                  exclusion for it.</li>
              </ul>
              <p className="text-muted mb-0">
                A finding stays open while the device keeps answering within the detection window.
              </p>

              <SpoofingConfiguration organizationId={organizationId} tenantId={tenantId} section="detection" />
            </div>
          </div>
        </div>

        <div className="col-xl-12 col-xxl-6">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title="Alerting" />

              <p className="text-muted">
                Detection alerts raised by spoofing detection. An alert is raised once per finding when it is
                first detected, not again while it continues.
              </p>

              <SpoofingConfiguration organizationId={organizationId} tenantId={tenantId} section="alerting" />
            </div>
          </div>

          {canManage ?
            <div className="card mt-3">
              <div className="card-body">
                <CardTitleWithControls title="Delete All ARP Poisoning Findings" slim={true} />

                <p>
                  Deletes every ARP poisoning finding of this tenant, including its victims. Device and asset data
                  is not affected. Exclusions and the settings on this page are kept. Findings are detected again
                  if the devices keep claiming addresses, unless an exclusion matches them.
                </p>

                <button className="btn btn-sm btn-danger" onClick={onDeleteAllArp}>Delete All Findings</button>
              </div>
            </div>
            : null}

          {canManage ?
            <div className="card mt-3">
              <div className="card-body">
                <CardTitleWithControls title="Delete All DHCP Spoofing Findings" slim={true} />

                <p>
                  Deletes every DHCP spoofing finding of this tenant, including its victims. Learned servers and
                  their trust status are kept, as are exclusions and the settings on this page. Findings are
                  detected again if untrusted servers keep answering clients.
                </p>

                <button className="btn btn-sm btn-danger" onClick={onDeleteAllDhcp}>Delete All Findings</button>
              </div>
            </div>
            : null}

          {canManage ?
            <div className="card mt-3">
              <div className="card-body">
                <CardTitleWithControls title="Delete All Name Resolution Poisoning Findings" slim={true} />

                <p>
                  Deletes every name resolution poisoning finding of this tenant, including its victims. Exclusions
                  and the settings on this page are kept. Findings are detected again if the devices keep answering
                  name queries, unless an exclusion matches them.
                </p>

                <button className="btn btn-sm btn-danger" onClick={onDeleteAllNameResolution}>Delete All Findings</button>
              </div>
            </div>
            : null}
        </div>
      </div>
    </React.Fragment>
  )

}
