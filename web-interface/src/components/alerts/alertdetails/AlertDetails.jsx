import React from "react";
import GenericAlertDetails from "./GenericAlertDetails";
import MonitorChannelAlertDetails from "./dot11/MonitorChannelAlertDetails";
import MonitorBSSIDAlertDetails from "./dot11/MonitorBSSIDAlertDetails";
import MonitorFingerprintAlertDetails from "./dot11/MonitorFingerprintAlertDetails";
import MonitorSecuritySuiteAlertDetails from "./dot11/MonitorSecuritySuiteAlertDetails";
import MonitorSignalTrackAlertDetails from "./dot11/MonitorSignalTrackAlertDetails";
import BanditContactAlertDetails from "./dot11/BanditContactAlertDetails";
import BanditContactPwnagotchiAlertDetails from "./dot11/BanditContactPwnagotchiAlertDetails";
import MonitorDiscoAnomalyAlertDetails from "./dot11/MonitorDiscoAnomalyAlertDetails";
import SimilarLookingSSIDAlertDetails from "./dot11/SimilarLookingSSIDAlertDetails";
import RestrictedSSIDSubstringAlertDetails from "./dot11/RestrictedSSIDSubstringAlertDetails";
import MonitoredProbeRequestAlertDetails from "./dot11/MonitoredProbeRequestAlertDetails";
import UnapprovedSSIDAlertDetails from "./dot11/UnapprovedSSIDAlertDetails";
import UnapprovedClientAlertDetails from "./dot11/UnapprovedClientAlertDetails";
import UavDetectedAlertDetails from "./uav/UavDetectedAlertDetails";
import MonitorTriggeredAlertDetails from "./generic/MonitorTriggeredAlertDetails";
import EnvironmentalSevereAlertDetails from "./generic/EnvironmentalSevereAlertDetails";
import BeaconAlertDetails from "./ethernet/BeaconAlertDetails";
import ReconScanAlertDetails from "./ethernet/ReconScanAlertDetails";
import ArpSpoofingAlertDetails from "./ethernet/ArpSpoofingAlertDetails";
import DhcpSpoofingAlertDetails from "./ethernet/DhcpSpoofingAlertDetails";
import NameResolutionSpoofingAlertDetails from "./ethernet/NameResolutionSpoofingAlertDetails";
import IcmpAlertDetails from "./ethernet/IcmpAlertDetails";

function AlertDetails(props) {

  const alert = props.alert;

  switch (alert.detection_type) {
    case "MONITOR_TRIGGERED_ETHERNET":
    case "MONITOR_TRIGGERED_DOT11":
    case "MONITOR_TRIGGERED_BLUETOOTH":
      return <MonitorTriggeredAlertDetails alert={alert} />
    case "DOT11_MONITOR_BSSID":
      return <MonitorBSSIDAlertDetails alert={alert} />
    case "DOT11_MONITOR_CHANNEL":
      return <MonitorChannelAlertDetails alert={alert} />
    case "DOT11_MONITOR_FINGERPRINT":
      return <MonitorFingerprintAlertDetails alert={alert} />
    case "DOT11_MONITOR_SECURITY_SUITE":
      return <MonitorSecuritySuiteAlertDetails alert={alert} />
    case "DOT11_MONITOR_SIGNAL_TRACK":
      return <MonitorSignalTrackAlertDetails alert={alert} />
    case "DOT11_MONITOR_DISCO_ANOMALIES":
      return <MonitorDiscoAnomalyAlertDetails alert={alert} />
    case "DOT11_BANDIT_CONTACT":
      if (alert.attributes.bandit_name === "Pwnagotchi") {
        return <BanditContactPwnagotchiAlertDetails alert={alert} />
      } else {
        return <BanditContactAlertDetails alert={alert} />
      }
    case "DOT11_MONITOR_SIMILAR_LOOKING_SSID":
      return <SimilarLookingSSIDAlertDetails alert={alert} />
    case "DOT11_MONITOR_SSID_SUBSTRING":
      return <RestrictedSSIDSubstringAlertDetails alert={alert} />
    case "DOT11_PROBEREQ":
      return <MonitoredProbeRequestAlertDetails alert={alert} />
    case "DOT11_UNAPPROVED_SSID":
      return <UnapprovedSSIDAlertDetails alert={alert} />
    case "DOT11_UNAPPROVED_CLIENT":
      return <UnapprovedClientAlertDetails alert={alert} />
    case "UAV_DETECTED_CLASSIFICATION_UNKNOWN":
    case "UAV_DETECTED_CLASSIFICATION_FRIENDLY":
    case "UAV_DETECTED_CLASSIFICATION_NEUTRAL":
    case "UAV_DETECTED_CLASSIFICATION_HOSTILE":
      return <UavDetectedAlertDetails alert={alert} />
    case "ENVIRONMENTAL_SEVERE_ALERT":
      return <EnvironmentalSevereAlertDetails alert={alert} />
    case "BEACON_NEW":
    case "BEACON_NEW_SOURCE":
    case "BEACON_ACTIVITY_SUSPICIOUS":
    case "BEACON_ACTIVITY_MALICIOUS":
      return <BeaconAlertDetails alert={alert} />
    case "RECON_SCAN_NEW":
    case "RECON_FINGERPRINTING_NEW":
      return <ReconScanAlertDetails alert={alert} />
    case "SPOOFING_ARP_NEW":
      return <ArpSpoofingAlertDetails alert={alert} />
    case "SPOOFING_DHCP_ROGUE_SERVER":
    case "SPOOFING_DHCP_PARAMETERS_CHANGED":
      return <DhcpSpoofingAlertDetails alert={alert} />
    case "SPOOFING_NAME_RESOLUTION_POISONING":
      return <NameResolutionSpoofingAlertDetails alert={alert} />
    case "ICMP_REDIRECT":
    case "ICMP_TRACEROUTE":
      return <IcmpAlertDetails alert={alert} />
    default:
      return <GenericAlertDetails alert={alert} />
  }

}

export default AlertDetails;