import React, {useContext} from "react";
import {Link} from "react-router-dom";
import numeral from "numeral";
import ApiRoutes from "../../../../util/ApiRoutes";
import {UserContext} from "../../../../App";
import {userHasPermission} from "../../../../util/Tools";
import TcpFingerprint from "../../../ethernet/assets/identity/TcpFingerprint";
import {changeSignalTitle, linkEvidenceTitle} from "../../../ethernet/assets/identity/AssetIdentityHelpers";

export default function AssetIdentityAlertDetails({alert}) {

  const a = alert.attributes;
  const type = alert.detection_type;

  const user = useContext(UserContext);
  const canManage = userHasPermission(user, "ethernet_assets_manage");

  const fingerprints = (csv) => {
    if (!csv) {
      return <span className="text-muted">n/a</span>;
    }

    return csv.split(", ").map((fp, i) => <React.Fragment key={fp}>{i > 0 ? <br /> : null}<TcpFingerprint value={fp} /></React.Fragment>);
  }

  const signals = (csv, titleOf) => {
    if (!csv) {
      return <span className="text-muted">None</span>;
    }

    return csv.split(", ").map((s) => titleOf(s)).join(", ");
  }

  const excludeLink = (mac, exclusionType, label) => {
    if (!canManage) {
      return null;
    }

    return (
      <React.Fragment>
        <dt>Exclusions</dt>
        <dd>
          <Link to={ApiRoutes.ETHERNET.ASSETS.IDENTITY.EXCLUSIONS.CREATE + "?mac=" + mac + "&type=" + exclusionType}>
            {label}
          </Link>
        </dd>
      </React.Fragment>
    );
  };

  const details = () => {
    if (type === "ASSETS_MAC_DUPLICATE") {
      return (
        <React.Fragment>
          <dt>Finding</dt>
          <dd>
            <Link to={ApiRoutes.ETHERNET.ASSETS.IDENTITY.DUPLICATE_DETAILS(a.finding_uuid)}>
              Duplicate MAC address {a.mac}
            </Link>
          </dd>
          <dt>MAC Address</dt>
          <dd><span className="machine-data">{a.mac}</span></dd>
          <dt>Stacks</dt>
          <dd>{numeral(a.stack_count).format("0,0")} different TCP stacks</dd>
          <dt>Fingerprints</dt>
          <dd>{fingerprints(a.fingerprints)}</dd>
          <dt>At Time of Alert</dt>
          <dd>{numeral(a.session_count).format("0,0")} sessions</dd>
          {excludeLink(a.mac, "DUPLICATE_MAC", "Exclude this address from duplicate detection")}
        </React.Fragment>
      )
    }

    if (type === "ASSETS_IDENTITY_CHANGED") {
      return (
        <React.Fragment>
          <dt>Finding</dt>
          <dd>
            <Link to={ApiRoutes.ETHERNET.ASSETS.IDENTITY.CHANGE_DETAILS(a.finding_uuid)}>
              Identity change of {a.mac}
            </Link>
          </dd>
          <dt>MAC Address</dt>
          <dd><span className="machine-data">{a.mac}</span></dd>
          <dt>Score</dt>
          <dd>{numeral(a.score).format("0,0")}</dd>
          <dt>Changes</dt>
          <dd>{signals(a.signals, changeSignalTitle)}</dd>
          {a.previous_fingerprint || a.new_fingerprint ?
            <React.Fragment>
              <dt>Previous TCP Fingerprint</dt>
              <dd>{fingerprints(a.previous_fingerprint)}</dd>
              <dt>New TCP Fingerprint</dt>
              <dd>{fingerprints(a.new_fingerprint)}</dd>
            </React.Fragment> : null}
          {excludeLink(a.mac, "IDENTITY_CHANGE", "Exclude this asset from identity change detection")}
        </React.Fragment>
      )
    }

    return (
      <React.Fragment>
        <dt>Finding</dt>
        <dd>
          <Link to={ApiRoutes.ETHERNET.ASSETS.IDENTITY.LINK_DETAILS(a.link_uuid)}>
            {a.predecessor_mac} continues as {a.mac}
          </Link>
        </dd>
        <dt>Old MAC Address</dt>
        <dd><span className="machine-data">{a.predecessor_mac}</span></dd>
        <dt>New MAC Address</dt>
        <dd><span className="machine-data">{a.mac}</span></dd>
        <dt>Confidence</dt>
        <dd>{numeral(a.confidence).format("0,0")}%</dd>
        <dt>Evidence</dt>
        <dd>{signals(a.signals, linkEvidenceTitle)}</dd>
        {excludeLink(a.mac, "MAC_CHANGE", "Exclude the new address from MAC address change detection")}
      </React.Fragment>
    )
  }

  const explanation = () => {
    if (type === "ASSETS_MAC_DUPLICATE") {
      return "Two devices used the same MAC address at the same time. Nzyme tells them apart by how their TCP " +
        "stacks open connections, and only counts stacks that sent their packets directly, not through a router " +
        "or NAT box. An attacker who clones the address of a live device to bypass port security or inherit its " +
        "network access looks like this. So does a wireless bridge that hides its clients behind one address. " +
        "Compare the stacks on the finding page.";
    }

    if (type === "ASSETS_IDENTITY_CHANGED") {
      return "An established asset became a different device over time: the identity it had is gone and a new " +
        "one took its place under the same MAC address. Each changed attribute is scored and the alert is raised " +
        "at the threshold. An attacker who cloned the address of a device that is now offline looks like this. " +
        "The finding page lists each change with its old and new value.";
    }

    return "A new asset looks like a device that recently went silent under another MAC address. The evidence " +
      "is shared hostnames, addresses, DHCP identity, TCP stacks, TLS clients and inbound ports, with rare " +
      "values counting more than ones a whole fleet shares. A device that randomizes its address or had its " +
      "network card replaced looks like this, and so does an attacker who reappears under a new address. " +
      "Confirm or dismiss the link on the finding page.";
  }

  const falsePositives = () => {
    if (type === "ASSETS_MAC_DUPLICATE") {
      return (
        <React.Fragment>
          <li>Wireless bridges and media converters that masquerade all their clients behind one MAC address.
            Add an exclusion for the bridge.</li>
          <li>Virtual machine hosts that forward guest traffic in a way that keeps the initial TTL intact, for
            example bridged networking on the same host.</li>
          <li>Load balancers or firewalls with one shared MAC address in front of several real servers.</li>
          <li>A device that changes its TCP settings at runtime, for example a kernel or driver update that was
            applied without a reboot.</li>
        </React.Fragment>
      )
    }

    if (type === "ASSETS_IDENTITY_CHANGED") {
      return (
        <React.Fragment>
          <li>Reinstalled machines and major operating system upgrades.</li>
          <li>Dual-boot systems and virtual machines with pass-through network cards. Exclude the asset.</li>
          <li>A network card that was moved to another machine, or a MAC address that a DHCP server or
            administrator reassigned to a new device.</li>
          <li>Docking stations whose MAC address is shared by whichever laptop is connected.</li>
        </React.Fragment>
      )
    }

    return (
      <React.Fragment>
        <li>Two identical devices from the same vendor with the same configuration, for example a fleet of
          thin clients or IoT devices that share hostnames and TCP stacks.</li>
        <li>A device that was replaced by a new one of the same model and inherited its hostname and address.</li>
        <li>DHCP servers that hand the released address of a silent device to the next one that asks.</li>
      </React.Fragment>
    )
  }

  return (
    <React.Fragment>
      <div className="row mt-3">
        <div className="col-md-6">
          <div className="card">
            <div className="card-body">
              <h3>Details</h3>
              <dl>
                {details()}
                <p className="mb-0 mt-3">{explanation()}</p>
              </dl>
            </div>
          </div>
        </div>

        <div className="col-md-6">
          <div className="card">
            <div className="card-body">
              <h3>Possible false-positives</h3>

              <p>
                These circumstances can lead to false-positive alerts of this type:
              </p>

              <ul className="mb-0">
                {falsePositives()}
              </ul>
            </div>
          </div>
        </div>
      </div>
    </React.Fragment>
  )

}
