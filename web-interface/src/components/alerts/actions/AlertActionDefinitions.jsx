import React from "react";
import ApiRoutes from "../../../util/ApiRoutes";
import Dot11Service from "../../../services/Dot11Service";
import SpoofingService from "../../../services/ethernet/SpoofingService";
import AssetsService from "../../../services/ethernet/AssetsService";
import BeaconsService from "../../../services/ethernet/BeaconsService";
import UavService from "../../../services/UavService";
import {beaconClassificationToName} from "../../ethernet/beacons/BeaconClassification";
import {capitalizeFirstLetter} from "../../../util/Tools";

const dot11Service = new Dot11Service();
const spoofingService = new SpoofingService();
const assetsService = new AssetsService();
const beaconsService = new BeaconsService();
const uavService = new UavService();

const BEACON_CLASSIFICATIONS = ["UNKNOWN", "BENIGN", "SUSPICIOUS", "MALICIOUS"]
  .map((c) => ({value: c, title: beaconClassificationToName(c)}));

const uavClassificationToName = (c) => capitalizeFirstLetter(c.toLowerCase());

const UAV_CLASSIFICATIONS = ["UNKNOWN", "FRIENDLY", "NEUTRAL", "HOSTILE"]
  .map((c) => ({value: c, title: uavClassificationToName(c)}));

const value = (v) => <span className="machine-data">{v}</span>

const fingerprintsOf = (target) => target.fingerprints ? target.fingerprints.split(",") : [];

const withParameters = (route, parameters) => {
  const query = new URLSearchParams();
  Object.keys(parameters).forEach((key) => {
    if (parameters[key]) {
      query.set(key, parameters[key]);
    }
  });

  return route + "?" + query.toString();
}

const link = (title, description, to) => ({
  kind: "link",
  title: title,
  description: description,
  to: to
});

// The alert page only shows alerts of the tenant selected in the header, so links can lead to pages that work on it.
const AlertActionDefinitions = {

  DOT11_ADD_BSSID: {
    kind: "call",
    title: "Add BSSID to Monitored Network",
    description: (t) => {
      const count = fingerprintsOf(t).length;
      if (count === 0) {
        return <span>Add BSSID {value(t.bssid)} to the monitored network. No fingerprints are added with it.</span>
      }

      return t.fingerprints_limited === "true"
        ? <span>Add BSSID {value(t.bssid)} and {count} of the fingerprints it advertised in the last 24 hours to the
            monitored network. It advertised more, and only {count} are added.</span>
        : <span>Add BSSID {value(t.bssid)} and {count === 1 ? "the fingerprint" : "the " + count + " fingerprints"} it
            advertised in the last 24 hours to the monitored network.</span>
    },
    doneDescription: (t) => <span>BSSID {value(t.bssid)} is an expected BSSID of the monitored network.</span>,
    confirmation: (t) => {
      const fingerprints = fingerprintsOf(t);
      return "Add BSSID " + t.bssid + " to the monitored network?"
        + (fingerprints.length > 0 ? "\n\nFingerprints:\n" + fingerprints.join("\n") : "");
    },
    success: "BSSID added.",
    run: (t, alert, onSuccess, onError) => dot11Service.writeMonitoredNetworkImportData(
      t.network_uuid, [{bssid: t.bssid, fingerprints: fingerprintsOf(t)}], [], [], onSuccess, onError)
  },

  DOT11_ADD_FINGERPRINT: {
    kind: "call",
    title: "Add Fingerprint to BSSID",
    description: (t) => <span>Add the observed fingerprint to the expected fingerprints of BSSID {value(t.bssid)}.</span>,
    doneDescription: (t) => <span>The observed fingerprint is an expected fingerprint of BSSID {value(t.bssid)}.</span>,
    confirmation: (t) => "Add fingerprint " + t.fingerprint + " to BSSID " + t.bssid + "?",
    success: "Fingerprint added.",
    run: (t, alert, onSuccess, onError) => dot11Service.createMonitoredBSSIDFingerprint(
      t.network_uuid, t.bssid_uuid, t.fingerprint, onSuccess, onError)
  },

  DOT11_ADD_CHANNEL: {
    kind: "call",
    title: "Add Frequency to Monitored Network",
    description: (t) => <span>Add {value(t.frequency + " MHz")} to the expected frequencies of the monitored network.</span>,
    doneDescription: (t) => <span>{value(t.frequency + " MHz")} is an expected frequency of the monitored network.</span>,
    confirmation: (t) => "Add frequency " + t.frequency + " MHz to the monitored network?",
    success: "Frequency added.",
    run: (t, alert, onSuccess, onError) => dot11Service.createMonitoredChannel(
      t.network_uuid, parseInt(t.frequency, 10), onSuccess, onError)
  },

  DOT11_ADD_SECURITY_SUITE: {
    kind: "call",
    title: "Add Security Suite to Monitored Network",
    description: (t) => <span>Add {value(t.suite)} to the expected security suites of the monitored network.</span>,
    doneDescription: (t) => <span>{value(t.suite)} is an expected security suite of the monitored network.</span>,
    confirmation: (t) => "Add security suite " + t.suite + " to the monitored network?",
    success: "Security suite added.",
    run: (t, alert, onSuccess, onError) => dot11Service.createMonitoredSecuritySuite(
      t.network_uuid, t.suite, onSuccess, onError)
  },

  DOT11_APPROVE_SSID: {
    kind: "call",
    title: "Approve SSID",
    description: (t) => <span>Approve SSID {value(t.ssid)}. Approved SSIDs do not raise this alert.</span>,
    doneDescription: (t) => <span>SSID {value(t.ssid)} is approved.</span>,
    confirmation: (t) => "Approve SSID " + t.ssid + "?",
    success: "SSID approved.",
    run: (t, alert, onSuccess, onError) => dot11Service.approveKnownNetwork(
      t.known_network_uuid, alert.organization_id, alert.tenant_id, onSuccess, onError)
  },

  DOT11_IGNORE_SSID: {
    kind: "call",
    title: "Ignore SSID",
    description: (t) => <span>Ignore SSID {value(t.ssid)}. Ignored SSIDs do not raise this alert and stay unapproved.</span>,
    doneDescription: (t) => <span>SSID {value(t.ssid)} is ignored.</span>,
    confirmation: (t) => "Ignore SSID " + t.ssid + "?",
    success: "SSID ignored.",
    run: (t, alert, onSuccess, onError) => dot11Service.ignoreKnownNetwork(
      t.known_network_uuid, alert.organization_id, alert.tenant_id, onSuccess, onError)
  },

  DOT11_APPROVE_HIDDEN_ACCESS_POINT: {
    kind: "call",
    title: "Approve Hidden Access Point",
    description: (t) => <span>Approve hidden access point {value(t.bssid)}. Approved access points do not raise
      this alert.</span>,
    doneDescription: (t) => <span>Hidden access point {value(t.bssid)} is approved.</span>,
    confirmation: (t) => "Approve hidden access point " + t.bssid + "?",
    success: "Hidden access point approved.",
    run: (t, alert, onSuccess, onError) => dot11Service.applyToHiddenAccessPoints(
      "approve", [t.hidden_access_point_uuid], alert.organization_id, alert.tenant_id, onSuccess, onError),
    changedNothing: (result) => result.affected === 0
  },

  DOT11_IGNORE_HIDDEN_ACCESS_POINT: {
    kind: "call",
    title: "Ignore Hidden Access Point",
    description: (t) => <span>Ignore hidden access point {value(t.bssid)}. Ignored access points do not raise this
      alert and stay unapproved.</span>,
    doneDescription: (t) => <span>Hidden access point {value(t.bssid)} is ignored.</span>,
    confirmation: (t) => "Ignore hidden access point " + t.bssid + "?",
    success: "Hidden access point ignored.",
    run: (t, alert, onSuccess, onError) => dot11Service.applyToHiddenAccessPoints(
      "ignore", [t.hidden_access_point_uuid], alert.organization_id, alert.tenant_id, onSuccess, onError),
    changedNothing: (result) => result.affected === 0
  },

  DOT11_APPROVE_CLIENT: {
    kind: "call",
    title: "Approve Client",
    description: (t) => <span>Approve client {value(t.mac)} for the monitored network.</span>,
    doneDescription: (t) => <span>Client {value(t.mac)} is approved for the monitored network.</span>,
    confirmation: (t) => "Approve client " + t.mac + "?",
    success: "Client approved.",
    run: (t, alert, onSuccess, onError) => dot11Service.approveMonitoredClient(t.network_uuid, t.client_uuid, onSuccess, onError)
  },

  DOT11_IGNORE_CLIENT: {
    kind: "call",
    title: "Ignore Client",
    description: (t) => <span>Ignore client {value(t.mac)}. Ignored clients do not raise this alert and stay unapproved.</span>,
    doneDescription: (t) => <span>Client {value(t.mac)} is ignored for the monitored network.</span>,
    confirmation: (t) => "Ignore client " + t.mac + "?",
    success: "Client ignored.",
    run: (t, alert, onSuccess, onError) => dot11Service.ignoreMonitoredClient(t.network_uuid, t.client_uuid, onSuccess, onError)
  },

  DOT11_ALLOW_SIMILAR_SSID: {
    kind: "call",
    title: "Allow Similar SSID",
    description: (t) => <span>Allow SSID {value(t.ssid)}. Allowed SSIDs do not raise the similar SSID alert of the
      monitored network.</span>,
    doneDescription: (t) => <span>SSID {value(t.ssid)} is an allowed SSID of the monitored network.</span>,
    confirmation: (t) => "Allow SSID " + t.ssid + "?",
    success: "SSID allowed.",
    run: (t, alert, onSuccess, onError) => dot11Service.addAllowedSimilarSSID(t.network_uuid, t.ssid, onSuccess, onError)
  },

  DOT11_OPEN_MONITORED_NETWORK: link(
    "Open Monitored Network Configuration",
    () => "Change the expected configuration and alert settings of the monitored network.",
    (t) => ApiRoutes.DOT11.MONITORING.SSID_DETAILS(t.network_uuid)),

  DOT11_OPEN_TRACK_DETECTOR: link(
    "Open Signal Tracks of BSSID",
    (t) => <span>The signal track detector is configured per tap. Select only tap {value(t.tap_name)} in the
      header of the page.</span>,
    (t) => ApiRoutes.DOT11.NETWORKS.SSIDS.DETAILS(
      encodeURIComponent(t.bssid), encodeURIComponent(t.ssid), encodeURIComponent(t.frequency))),

  DOT11_OPEN_DISCO_CONFIGURATION: link(
    "Open Disconnection Detection Configuration",
    () => "Change the disconnection detection method of the monitored network.",
    (t) => ApiRoutes.DOT11.MONITORING.DISCO.CONFIGURATION(t.network_uuid)),

  DOT11_OPEN_DISCO_DETECTION_SETTINGS: link(
    "Open Disconnection Anomaly Settings",
    () => "Turn detections of disconnection anomalies on or off and change their thresholds.",
    () => ApiRoutes.DOT11.DISCO.SETTINGS),

  DOT11_OPEN_CHANNEL_SWITCH_CONFIGURATION: link(
    "Open Channel Switch Monitor Configuration",
    () => "Change the detection methods or the threshold of the channel switch monitor of the monitored network.",
    (t) => ApiRoutes.DOT11.MONITORING.CHANNEL_SWITCH_CONFIGURATION(t.network_uuid)),

  DOT11_OPEN_CHANNEL_SWITCH_SETTINGS: link(
    "Open Channel Switch Settings",
    () => "Change the detection methods or the threshold for networks that are not monitored, or limit alerts to monitored networks.",
    () => ApiRoutes.DOT11.CHANNEL_SWITCHES.SETTINGS),

  DOT11_OPEN_SIMILAR_SSID_CONFIGURATION: link(
    "Open Similar SSID Monitor Configuration",
    () => "Change the similarity threshold or the allowed SSIDs of the monitored network.",
    (t) => ApiRoutes.DOT11.MONITORING.SIMILAR_SSID_CONFIGURATION(t.network_uuid)),

  DOT11_OPEN_RESTRICTED_SUBSTRINGS: link(
    "Open Restricted SSID Substring Configuration",
    () => "Change the restricted substrings of the monitored network.",
    (t) => ApiRoutes.DOT11.MONITORING.RESTRICTED_SUBSTRINGS_CONFIGURATION(t.network_uuid)),

  DOT11_OPEN_CLIENT_MONITORING: link(
    "Open Client Monitoring Configuration",
    () => "Review all clients of the monitored network.",
    (t) => ApiRoutes.DOT11.MONITORING.CLIENTS_CONFIGURATION(t.network_uuid)),

  DOT11_OPEN_SSID_MONITORING: link(
    "Open SSID Monitoring",
    () => "Review all discovered SSIDs.",
    () => ApiRoutes.DOT11.MONITORING.SSIDS.INDEX),

  DOT11_OPEN_HIDDEN_ACCESS_POINTS: link(
    "Open Hidden Access Points",
    () => "Review all recorded hidden access points.",
    () => ApiRoutes.DOT11.MONITORING.SSIDS.HIDDEN_ACCESS_POINTS),

  DOT11_OPEN_PROBE_REQUESTS: link(
    "Open Monitored Probe Requests",
    () => "Change the monitored probe requests.",
    () => ApiRoutes.DOT11.MONITORING.PROBE_REQUESTS.INDEX),

  DOT11_OPEN_CUSTOM_BANDIT: link(
    "Open Custom Bandit",
    (t) => <span>The fingerprint belongs to the custom bandit {value(t.bandit_name)}.</span>,
    (t) => ApiRoutes.DOT11.MONITORING.BANDITS.CUSTOM_DETAILS(t.bandit_uuid)),

  BEACON_CLASSIFY: {
    kind: "choice",
    title: "Classify Beacon",
    description: (t) => <span>The beacon is classified as {beaconClassificationToName(t.classification)}.</span>,
    choices: BEACON_CLASSIFICATIONS,
    current: (t) => t.classification,
    success: "Classification saved.",
    run: (t, alert, classification, onSuccess, onError) => beaconsService.classifyBeacon(
      t.beacon_uuid, alert.organization_id, alert.tenant_id, classification, onSuccess, onError)
  },

  BEACON_CREATE_EXCLUSION: link(
    "Create Beacon Exclusion",
    () => "An exclusion keeps this beacon from being detected again.",
    (t) => withParameters(ApiRoutes.ETHERNET.BEACONS.EXCLUSIONS.CREATE, {beacon: t.beacon_uuid})),

  RECON_CREATE_EXCLUSION: link(
    "Create Scan Exclusion",
    () => "An exclusion keeps scans like this one from being detected again.",
    (t) => withParameters(ApiRoutes.ETHERNET.RECON.SCANS.EXCLUSIONS.CREATE, {scan: t.scan_uuid})),

  SPOOFING_CREATE_EXCLUSION: link(
    "Create Spoofing Exclusion",
    () => "An exclusion keeps this device from showing up as spoofing again.",
    (t) => withParameters(ApiRoutes.ETHERNET.SPOOFING.EXCLUSIONS.CREATE, {[t.parameter]: t.finding_uuid})),

  DHCP_TRUST_SERVER: {
    kind: "call",
    title: "Trust Server",
    description: (t) => <span>Trust {value(t.server_address)}. A trusted server or router raises no rogue server
      findings.</span>,
    doneDescription: (t) => <span>{value(t.server_address)} is trusted.</span>,
    confirmation: () => "Trust this server or router? It will no longer raise rogue server findings, and the "
      + "configuration it hands out right now becomes its expected configuration.",
    success: "Now trusted.",
    run: (t, alert, onSuccess, onError) => spoofingService.setDhcpServerTrusted(
      t.server_uuid, alert.organization_id, alert.tenant_id, true, onSuccess, onError)
  },

  DHCP_ACCEPT_CONFIGURATION: {
    kind: "call",
    title: "Accept Configuration",
    description: (t) => <span>Accept the configuration {value(t.server_address)} hands out now as its expected
      configuration.</span>,
    doneDescription: (t) => <span>{value(t.server_address)} hands out its expected configuration.</span>,
    confirmation: () => "Accept the configuration this server hands out right now as its expected configuration?",
    success: "Configuration accepted.",
    run: (t, alert, onSuccess, onError) => spoofingService.acceptDhcpServerConfiguration(
      t.server_uuid, alert.organization_id, alert.tenant_id, onSuccess, onError)
  },

  DNS_TUNNEL_CREATE_EXCLUSION: link(
    "Create DNS Tunnel Exclusion",
    () => "An exclusion keeps this domain or client from being detected as a tunnel again.",
    (t) => withParameters(ApiRoutes.ETHERNET.DNS.TUNNELS.EXCLUSIONS.CREATE, {finding: t.finding_uuid})),

  ICMP_CREATE_EXCLUSION: link(
    "Create ICMP Exclusion",
    () => "An exclusion keeps this device from raising this ICMP alert again.",
    (t) => withParameters(ApiRoutes.ETHERNET.ICMP.EXCLUSIONS.CREATE,
      {type: t.detection_type, source: t.source, source_mac: t.source_mac, gateway: t.gateway})),

  DHCP_FINGERPRINT_ACCEPT: {
    kind: "call",
    title: "Accept DHCP Fingerprint",
    description: () => "Accept the new fingerprint as the known fingerprint of the asset. The previous "
      + "fingerprint raises this alert from then on.",
    doneDescription: () => "The new fingerprint is the known fingerprint of the asset.",
    confirmation: (t) => "Accept fingerprint " + t.fingerprint + " for " + t.transaction_type.toLowerCase()
      + " transactions of this asset?",
    success: "Fingerprint accepted.",
    run: (t, alert, onSuccess, onError) => assetsService.acceptDHCPFingerprint(
      t.asset_uuid, t.transaction_type, t.fingerprint, alert.organization_id, alert.tenant_id, onSuccess, onError)
  },

  DHCP_FINGERPRINT_CREATE_EXCLUSION: link(
    "Create DHCP Fingerprint Exclusion",
    () => "An exclusion keeps this asset from raising DHCP fingerprint alerts.",
    (t) => withParameters(ApiRoutes.ETHERNET.ASSETS.DHCP.EXCLUSIONS.CREATE, {mac: t.mac})),

  ASSET_IDENTITY_CREATE_EXCLUSION: link(
    "Create Asset Identity Exclusion",
    () => "An exclusion keeps this address out of this identity detection.",
    (t) => withParameters(ApiRoutes.ETHERNET.ASSETS.IDENTITY.EXCLUSIONS.CREATE, {mac: t.mac, type: t.type})),

  ASSET_LINK_CONFIRM: {
    kind: "call",
    title: "Confirm Asset Link",
    description: () => "Confirm that both addresses belong to one device.",
    doneDescription: () => "The link between the two addresses is confirmed.",
    confirmation: () => "Mark the two assets as one device?",
    success: "Link confirmed.",
    run: (t, alert, onSuccess, onError) => assetsService.setAssetLinkStatus(
      t.link_uuid, alert.organization_id, alert.tenant_id, "CONFIRMED", onSuccess, onError)
  },

  ASSET_LINK_DISMISS: {
    kind: "call",
    title: "Dismiss Asset Link",
    description: () => "Dismiss the link. A dismissed link is kept so that it is not detected again.",
    doneDescription: () => "The link between the two addresses is dismissed.",
    confirmation: () => "Dismiss this link? The assets are then treated as two devices.",
    success: "Link dismissed.",
    run: (t, alert, onSuccess, onError) => assetsService.setAssetLinkStatus(
      t.link_uuid, alert.organization_id, alert.tenant_id, "DISMISSED", onSuccess, onError)
  },

  UAV_CLASSIFY: {
    kind: "choice",
    title: "Classify UAV",
    description: (t) => <span>The UAV is classified as {uavClassificationToName(t.classification)}.</span>,
    choices: UAV_CLASSIFICATIONS,
    current: (t) => t.classification,
    success: "Classification saved.",
    run: (t, alert, classification, onSuccess, onError) => uavService.classifyUav(
      t.identifier, alert.organization_id, alert.tenant_id, classification, onSuccess, onError)
  }

}

export default AlertActionDefinitions;
