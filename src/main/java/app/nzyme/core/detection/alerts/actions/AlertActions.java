package app.nzyme.core.detection.alerts.actions;

import app.nzyme.core.NzymeNode;
import app.nzyme.core.assets.AssetManager;
import app.nzyme.core.assets.db.AssetEntry;
import app.nzyme.core.assets.identity.AssetIdentity;
import app.nzyme.core.assets.identity.AssetIdentityType;
import app.nzyme.core.assets.identity.db.AssetLinkEntry;
import app.nzyme.core.detection.alerts.DetectionType;
import app.nzyme.core.detection.alerts.db.DetectionAlertEntry;
import app.nzyme.core.dot11.Dot11;
import app.nzyme.core.dot11.db.Dot11KnownClient;
import app.nzyme.core.dot11.db.Dot11KnownNetwork;
import app.nzyme.core.dot11.db.monitoring.CustomBanditDescription;
import app.nzyme.core.dot11.db.monitoring.MonitoredBSSID;
import app.nzyme.core.dot11.db.monitoring.MonitoredChannel;
import app.nzyme.core.dot11.db.monitoring.MonitoredFingerprint;
import app.nzyme.core.dot11.db.monitoring.MonitoredSSID;
import app.nzyme.core.dot11.db.monitoring.MonitoredSecuritySuite;
import app.nzyme.core.ethernet.beacons.db.BeaconEntry;
import app.nzyme.core.ethernet.dhcp.DHCPFingerprintExclusionMatcher;
import app.nzyme.core.ethernet.icmp.IcmpDetectionType;
import app.nzyme.core.ethernet.spoofing.DhcpOfferedParameters;
import app.nzyme.core.ethernet.spoofing.DhcpSpoofingFindingType;
import app.nzyme.core.ethernet.spoofing.db.DhcpSpoofingFindingEntry;
import app.nzyme.core.ethernet.spoofing.db.DhcpSpoofingServerEntry;
import app.nzyme.core.rest.authentication.AuthenticatedUser;
import app.nzyme.core.uav.db.UavEntry;
import app.nzyme.core.util.TimeRangeFactory;
import app.nzyme.plugin.Subsystem;
import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static app.nzyme.core.detection.alerts.actions.AlertActionType.*;

/*
 * Every action is executed by an existing endpoint of the feature it changes, so an action is only returned if
 * that endpoint would accept the user.
 */
public class AlertActions {

    public static final int MAX_FINGERPRINTS_WITH_BSSID = 25;

    private static final String DOT11_PERMISSION = "dot11_monitoring_manage";
    private static final String ETHERNET_PERMISSION = "ethernet_monitoring_manage";
    private static final String ASSETS_PERMISSION = "ethernet_assets_manage";
    private static final String UAV_PERMISSION = "uav_monitoring_manage";

    private final NzymeNode nzyme;

    public AlertActions(NzymeNode nzyme) {
        this.nzyme = nzyme;
    }

    public List<AlertAction> resolve(DetectionAlertEntry alert, Map<String, String> attributes, AuthenticatedUser user) {
        if (alert.organizationId() == null || alert.tenantId() == null) {
            return List.of();
        }

        DetectionType type;
        try {
            type = DetectionType.valueOf(alert.detectionType());
        } catch (IllegalArgumentException e) {
            return List.of();
        }

        boolean administrator = user.isSuperAdministrator()
                || (user.isOrganizationAdministrator() && alert.organizationId().equals(user.getOrganizationId()));
        Set<String> permissions = administrator ? Set.of()
                : Sets.newHashSet(nzyme.getAuthenticationService().findPermissionsOfUser(user.getUserId()));

        Scope scope = new Scope(alert, attributes, user, administrator, permissions);
        List<AlertAction> actions = Lists.newArrayList();

        switch (type) {
            case DOT11_MONITOR_BSSID, DOT11_MONITOR_CHANNEL, DOT11_MONITOR_SECURITY_SUITE,
                 DOT11_MONITOR_FINGERPRINT, DOT11_MONITOR_SIGNAL_TRACK, DOT11_MONITOR_DISCO_ANOMALIES,
                 DOT11_MONITOR_SIMILAR_LOOKING_SSID, DOT11_MONITOR_SSID_SUBSTRING, DOT11_UNAPPROVED_CLIENT,
                 DOT11_UNAPPROVED_SSID, DOT11_PROBEREQ, DOT11_BANDIT_CONTACT -> {
                if (allowed(scope, DOT11_PERMISSION, Subsystem.DOT11)) {
                    dot11(type, scope, actions);
                }
            }
            case BEACON_NEW, BEACON_NEW_SOURCE, BEACON_ACTIVITY_SUSPICIOUS, BEACON_ACTIVITY_MALICIOUS,
                 RECON_SCAN_NEW, RECON_FINGERPRINTING_NEW, SPOOFING_ARP_NEW, SPOOFING_NDP_NEW,
                 SPOOFING_DHCP_ROGUE_SERVER, SPOOFING_DHCP_PARAMETERS_CHANGED, SPOOFING_RA_ROGUE_ROUTER,
                 SPOOFING_RA_PARAMETERS_CHANGED, SPOOFING_NAME_RESOLUTION_POISONING, SPOOFING_DNS_NEW,
                 DNS_TUNNEL, ICMP_REDIRECT, ICMP_TRACEROUTE, ICMP_TUNNEL -> {
                if (allowed(scope, ETHERNET_PERMISSION, Subsystem.ETHERNET)) {
                    ethernetMonitoring(type, scope, actions);
                }
            }
            case ASSETS_DHCP_FINGERPRINT_NEW, ASSETS_MAC_DUPLICATE, ASSETS_IDENTITY_CHANGED, ASSETS_MAC_CHANGED -> {
                if (allowed(scope, ASSETS_PERMISSION, Subsystem.ETHERNET)) {
                    assets(type, scope, actions);
                }
            }
            case UAV_DETECTED_CLASSIFICATION_UNKNOWN, UAV_DETECTED_CLASSIFICATION_FRIENDLY,
                 UAV_DETECTED_CLASSIFICATION_NEUTRAL, UAV_DETECTED_CLASSIFICATION_HOSTILE -> {
                if (allowed(scope, UAV_PERMISSION, Subsystem.UAV)) {
                    uav(scope, actions);
                }
            }
            default -> { }
        }

        return actions;
    }

    // The same subsystem check the REST layer runs before it lets a request through.
    private boolean allowed(Scope scope, String permission, Subsystem subsystem) {
        return (scope.administrator() || scope.permissions().contains(permission))
                && nzyme.getSubsystems().isEnabled(
                        subsystem, scope.user().getOrganizationId(), scope.user().getTenantId());
    }

    private void dot11(DetectionType type, Scope scope, List<AlertAction> actions) {
        switch (type) {
            case DOT11_UNAPPROVED_SSID -> unapprovedSsid(scope, actions);
            case DOT11_PROBEREQ -> actions.add(AlertAction.available(DOT11_OPEN_PROBE_REQUESTS));
            case DOT11_BANDIT_CONTACT -> customBandit(scope, actions);
            default -> monitoredNetwork(type, scope, actions);
        }
    }

    private void monitoredNetwork(DetectionType type, Scope scope, List<AlertAction> actions) {
        UUID networkId = scope.alert().dot11MonitoredNetworkId();
        if (networkId == null) {
            return;
        }

        Optional<MonitoredSSID> found = nzyme.getDot11().findMonitoredSSID(networkId)
                .filter(n -> n.organizationId().equals(scope.organizationId())
                        && n.tenantId().equals(scope.tenantId()));
        if (found.isEmpty()) {
            return;
        }

        MonitoredSSID network = found.get();
        String networkUuid = network.uuid().toString();

        switch (type) {
            case DOT11_MONITOR_BSSID -> {
                String bssid = scope.attribute("bssid");
                if (bssid != null) {
                    boolean monitored = !findMonitoredBssids(network, bssid).isEmpty();
                    List<String> fingerprints = monitored ? List.of() : observedFingerprints(scope, bssid);
                    boolean limited = fingerprints.size() > MAX_FINGERPRINTS_WITH_BSSID;
                    actions.add(AlertAction.of(DOT11_ADD_BSSID, monitored,
                            "network_uuid", networkUuid,
                            "bssid", bssid,
                            "fingerprints", monitored ? null : Joiner.on(",").join(
                                    fingerprints.stream().limit(MAX_FINGERPRINTS_WITH_BSSID).toList()),
                            "fingerprints_limited", monitored ? null : String.valueOf(limited)));
                }
                actions.add(AlertAction.available(DOT11_OPEN_MONITORED_NETWORK, "network_uuid", networkUuid));
            }
            case DOT11_MONITOR_FINGERPRINT -> {
                String bssid = scope.attribute("bssid");
                String fingerprint = scope.attribute("fingerprint");
                List<MonitoredBSSID> monitoredBssids = bssid == null
                        ? List.of() : findMonitoredBssids(network, bssid);
                if (fingerprint != null && !monitoredBssids.isEmpty()) {
                    // A BSSID that was stored twice counts with the fingerprints of all its rows, like in detection.
                    boolean monitored = monitoredBssids.stream()
                            .flatMap(b -> nzyme.getDot11().findMonitoredFingerprintsOfMonitoredBSSID(b.id()).stream())
                            .map(MonitoredFingerprint::fingerprint)
                            .anyMatch(fingerprint::equals);
                    actions.add(AlertAction.of(DOT11_ADD_FINGERPRINT, monitored,
                            "network_uuid", networkUuid,
                            "bssid_uuid", monitoredBssids.get(0).uuid().toString(),
                            "bssid", bssid,
                            "fingerprint", fingerprint));
                }
                actions.add(AlertAction.available(DOT11_OPEN_MONITORED_NETWORK, "network_uuid", networkUuid));
            }
            case DOT11_MONITOR_CHANNEL -> {
                Long frequency = scope.longAttribute("frequency");
                if (frequency != null && Dot11.frequencyToChannel(frequency.intValue()) != -1) {
                    boolean monitored = nzyme.getDot11().findMonitoredChannelsOfMonitoredNetwork(network.id())
                            .stream()
                            .map(MonitoredChannel::frequency)
                            .anyMatch(frequency::equals);
                    actions.add(AlertAction.of(DOT11_ADD_CHANNEL, monitored,
                            "network_uuid", networkUuid,
                            "frequency", String.valueOf(frequency)));
                }
                actions.add(AlertAction.available(DOT11_OPEN_MONITORED_NETWORK, "network_uuid", networkUuid));
            }
            case DOT11_MONITOR_SECURITY_SUITE -> {
                String suite = scope.attribute("suite");
                if (suite != null) {
                    List<String> monitoredSuites = nzyme.getDot11()
                            .findMonitoredSecuritySuitesOfMonitoredNetwork(network.id())
                            .stream()
                            .map(MonitoredSecuritySuite::securitySuite)
                            .toList();
                    boolean monitored = Dot11.isMonitoredSecuritySuite(monitoredSuites, suite);
                    actions.add(AlertAction.of(DOT11_ADD_SECURITY_SUITE, monitored,
                            "network_uuid", networkUuid,
                            "suite", suite));
                }
                actions.add(AlertAction.available(DOT11_OPEN_MONITORED_NETWORK, "network_uuid", networkUuid));
            }
            case DOT11_MONITOR_SIGNAL_TRACK -> {
                // The track detector settings are open to administrators only.
                if (scope.administrator() && scope.attribute("bssid") != null
                        && scope.longAttribute("channel") != null) {
                    actions.add(AlertAction.available(DOT11_OPEN_TRACK_DETECTOR,
                            "bssid", scope.attribute("bssid"),
                            "ssid", network.ssid(),
                            "frequency", scope.attribute("channel"),
                            "tap_name", scope.attribute("tap_name")));
                }
                actions.add(AlertAction.available(DOT11_OPEN_MONITORED_NETWORK, "network_uuid", networkUuid));
            }
            case DOT11_MONITOR_DISCO_ANOMALIES ->
                    actions.add(AlertAction.available(DOT11_OPEN_DISCO_CONFIGURATION, "network_uuid", networkUuid));
            case DOT11_MONITOR_SIMILAR_LOOKING_SSID -> {
                String similarSsid = scope.attribute("similar_ssid");
                if (similarSsid != null) {
                    List<String> allowed = nzyme.getDot11().findAllowedSimilarSSIDs(network.id());
                    if (allowed.contains(similarSsid) || allowed.size() < Dot11.MAX_ALLOWED_SIMILAR_SSIDS) {
                        actions.add(AlertAction.of(DOT11_ALLOW_SIMILAR_SSID, allowed.contains(similarSsid),
                                "network_uuid", networkUuid, "ssid", similarSsid));
                    }
                }
                actions.add(AlertAction.available(DOT11_OPEN_SIMILAR_SSID_CONFIGURATION, "network_uuid", networkUuid));
            }
            case DOT11_MONITOR_SSID_SUBSTRING ->
                    actions.add(AlertAction.available(DOT11_OPEN_RESTRICTED_SUBSTRINGS, "network_uuid", networkUuid));
            case DOT11_UNAPPROVED_CLIENT -> {
                String mac = scope.attribute("client_mac");
                Optional<Dot11KnownClient> client = mac == null
                        ? Optional.empty() : nzyme.getDot11().findKnownClient(mac, network.id());
                if (client.isPresent()) {
                    String clientUuid = client.get().uuid().toString();
                    if (!client.get().isIgnored()) {
                        actions.add(AlertAction.of(DOT11_APPROVE_CLIENT, client.get().isApproved(),
                                "network_uuid", networkUuid, "client_uuid", clientUuid, "mac", mac));
                    }
                    if (!client.get().isApproved()) {
                        actions.add(AlertAction.of(DOT11_IGNORE_CLIENT, client.get().isIgnored(),
                                "network_uuid", networkUuid, "client_uuid", clientUuid, "mac", mac));
                    }
                }
                actions.add(AlertAction.available(DOT11_OPEN_CLIENT_MONITORING, "network_uuid", networkUuid));
            }
            default -> { }
        }
    }

    private List<MonitoredBSSID> findMonitoredBssids(MonitoredSSID network, String bssid) {
        return nzyme.getDot11().findMonitoredBSSIDsOfMonitoredNetwork(network.id())
                .stream()
                .filter(b -> b.bssid().equalsIgnoreCase(bssid))
                .toList();
    }

    private List<String> observedFingerprints(Scope scope, String bssid) {
        List<UUID> taps = nzyme.getTapManager()
                .allTapUUIDsAccessibleByUser(scope.user(), scope.organizationId(), scope.tenantId());
        if (taps.isEmpty()) {
            return List.of();
        }

        return nzyme.getDot11().findFingerprintsOfBSSID(bssid, TimeRangeFactory.oneDay(), taps)
                .stream()
                .filter(Objects::nonNull)
                .filter(f -> f.length() == 64)
                .sorted()
                .limit(MAX_FINGERPRINTS_WITH_BSSID + 1)
                .toList();
    }

    private void unapprovedSsid(Scope scope, List<AlertAction> actions) {
        String ssid = scope.attribute("ssid");
        Optional<Dot11KnownNetwork> network = ssid == null ? Optional.empty()
                : nzyme.getDot11().findKnownNetworkBySSID(ssid, scope.organizationId(), scope.tenantId());

        if (network.isPresent()) {
            String uuid = network.get().uuid().toString();
            if (!network.get().isIgnored()) {
                actions.add(AlertAction.of(DOT11_APPROVE_SSID, network.get().isApproved(),
                        "known_network_uuid", uuid, "ssid", ssid));
            }
            if (!network.get().isApproved()) {
                actions.add(AlertAction.of(DOT11_IGNORE_SSID, network.get().isIgnored(),
                        "known_network_uuid", uuid, "ssid", ssid));
            }
        }

        actions.add(AlertAction.available(DOT11_OPEN_SSID_MONITORING));
    }

    private void customBandit(Scope scope, List<AlertAction> actions) {
        String fingerprint = scope.attribute("fingerprint");
        if (fingerprint == null || !"true".equals(scope.attribute("bandit_is_custom"))) {
            return;
        }

        Optional<CustomBanditDescription> bandit = nzyme.getDot11()
                .findCustomBanditByFingerprint(fingerprint, scope.organizationId(), scope.tenantId());
        bandit.ifPresent(b -> actions.add(AlertAction.available(DOT11_OPEN_CUSTOM_BANDIT,
                "bandit_uuid", b.uuid().toString(), "bandit_name", b.name())));
    }

    private void ethernetMonitoring(DetectionType type, Scope scope, List<AlertAction> actions) {
        UUID org = scope.organizationId();
        UUID tenant = scope.tenantId();

        switch (type) {
            case BEACON_NEW, BEACON_NEW_SOURCE, BEACON_ACTIVITY_SUSPICIOUS, BEACON_ACTIVITY_MALICIOUS -> {
                UUID uuid = scope.uuidAttribute("beacon_uuid");
                Optional<BeaconEntry> beacon = uuid == null
                        ? Optional.empty() : nzyme.getEthernet().beacons().findBeacon(uuid, org, tenant);
                if (beacon.isPresent()) {
                    actions.add(AlertAction.available(BEACON_CLASSIFY,
                            "beacon_uuid", uuid.toString(), "classification", beacon.get().classification()));
                    actions.add(AlertAction.available(BEACON_CREATE_EXCLUSION, "beacon_uuid", uuid.toString()));
                }
            }
            case RECON_SCAN_NEW, RECON_FINGERPRINTING_NEW -> {
                UUID uuid = scope.uuidAttribute("scan_uuid");
                if (uuid != null && nzyme.getEthernet().recon().findScan(uuid, org, tenant).isPresent()) {
                    actions.add(AlertAction.available(RECON_CREATE_EXCLUSION, "scan_uuid", uuid.toString()));
                }
            }
            case SPOOFING_ARP_NEW, SPOOFING_NDP_NEW -> {
                UUID uuid = scope.uuidAttribute("finding_uuid");
                if (uuid != null && nzyme.getEthernet().spoofing().findArpFinding(uuid, org, tenant).isPresent()) {
                    actions.add(spoofingExclusion("finding", uuid));
                }
            }
            case SPOOFING_NAME_RESOLUTION_POISONING -> {
                UUID uuid = scope.uuidAttribute("finding_uuid");
                if (uuid != null
                        && nzyme.getEthernet().spoofing().findNameResolutionFinding(uuid, org, tenant).isPresent()) {
                    actions.add(spoofingExclusion("nameres_finding", uuid));
                }
            }
            case SPOOFING_DNS_NEW -> {
                UUID uuid = scope.uuidAttribute("finding_uuid");
                if (uuid != null && nzyme.getEthernet().spoofing().findDnsFinding(uuid, org, tenant).isPresent()) {
                    actions.add(spoofingExclusion("dns_finding", uuid));
                }
            }
            case SPOOFING_DHCP_ROGUE_SERVER, SPOOFING_DHCP_PARAMETERS_CHANGED, SPOOFING_RA_ROGUE_ROUTER,
                 SPOOFING_RA_PARAMETERS_CHANGED -> dhcpSpoofing(scope, actions);
            case DNS_TUNNEL -> {
                UUID uuid = scope.uuidAttribute("finding_uuid");
                if (uuid != null && nzyme.getEthernet().dnsTunnels().findFinding(uuid, org, tenant).isPresent()) {
                    actions.add(AlertAction.available(DNS_TUNNEL_CREATE_EXCLUSION, "finding_uuid", uuid.toString()));
                }
            }
            case ICMP_REDIRECT -> actions.add(AlertAction.available(ICMP_CREATE_EXCLUSION,
                    "detection_type", IcmpDetectionType.REDIRECT.name(),
                    "source", scope.attribute("sender_address"),
                    "source_mac", scope.attribute("sender_mac"),
                    "gateway", scope.attribute("gateway_address")));
            case ICMP_TRACEROUTE -> actions.add(AlertAction.available(ICMP_CREATE_EXCLUSION,
                    "detection_type", IcmpDetectionType.TRACEROUTE.name(),
                    "source", scope.attribute("source_address"),
                    "source_mac", scope.attribute("source_mac")));
            case ICMP_TUNNEL -> actions.add(AlertAction.available(ICMP_CREATE_EXCLUSION,
                    "detection_type", IcmpDetectionType.TUNNEL.name(),
                    "source", scope.attribute("source_address"),
                    "source_mac", scope.attribute("source_mac")));
            default -> { }
        }
    }

    private AlertAction spoofingExclusion(String parameter, UUID findingUuid) {
        return AlertAction.available(SPOOFING_CREATE_EXCLUSION,
                "parameter", parameter, "finding_uuid", findingUuid.toString());
    }

    private void dhcpSpoofing(Scope scope, List<AlertAction> actions) {
        UUID uuid = scope.uuidAttribute("finding_uuid");
        Optional<DhcpSpoofingFindingEntry> finding = uuid == null ? Optional.empty()
                : nzyme.getEthernet().spoofing().findDhcpFinding(uuid, scope.organizationId(), scope.tenantId());
        if (finding.isEmpty()) {
            return;
        }

        Optional<DhcpSpoofingServerEntry> server = nzyme.getEthernet().spoofing().findDhcpServerByIdentity(
                finding.get().protocol(), finding.get().serverMac(), finding.get().serverAddress(), scope.organizationId(), scope.tenantId());

        if (server.isPresent()) {
            String serverUuid = server.get().uuid().toString();
            if (finding.get().type() == DhcpSpoofingFindingType.ROGUE_SERVER) {
                actions.add(AlertAction.of(DHCP_TRUST_SERVER, server.get().trusted(),
                        "server_uuid", serverUuid, "server_address", server.get().address()));
            } else if (server.get().trusted()) {
                DhcpOfferedParameters baseline = server.get().baseline();
                DhcpOfferedParameters latest = server.get().latest();
                boolean matches = baseline == null || latest == null || !latest.deviatesFrom(baseline);
                actions.add(AlertAction.of(DHCP_ACCEPT_CONFIGURATION, matches,
                        "server_uuid", serverUuid, "server_address", server.get().address()));
            }
        }

        actions.add(spoofingExclusion("dhcp_finding", uuid));
    }

    private void assets(DetectionType type, Scope scope, List<AlertAction> actions) {
        String mac = scope.attribute("mac");

        switch (type) {
            case ASSETS_DHCP_FINGERPRINT_NEW -> {
                UUID assetUuid = scope.uuidAttribute("asset_uuid");
                String transactionType = scope.attribute("transaction_type");
                String fingerprint = scope.attribute("new_fingerprint");
                Optional<AssetEntry> asset = assetUuid == null ? Optional.empty() : nzyme.getAssetsManager()
                        .findAsset(assetUuid, scope.organizationId(), scope.tenantId());
                if (asset.isPresent() && fingerprint != null && transactionType != null
                        && DHCPFingerprintExclusionMatcher.TRANSACTION_TYPES.contains(transactionType)) {
                    actions.add(AlertAction.of(DHCP_FINGERPRINT_ACCEPT,
                            fingerprint.equals(AssetManager.dhcpFingerprintOf(asset.get(), transactionType)),
                            "asset_uuid", assetUuid.toString(),
                            "transaction_type", transactionType,
                            "fingerprint", fingerprint));
                }
                if (mac != null) {
                    actions.add(AlertAction.available(DHCP_FINGERPRINT_CREATE_EXCLUSION, "mac", mac));
                }
            }
            case ASSETS_MAC_DUPLICATE -> identityExclusion(mac, AssetIdentityType.DUPLICATE_MAC, actions);
            case ASSETS_IDENTITY_CHANGED -> identityExclusion(mac, AssetIdentityType.IDENTITY_CHANGE, actions);
            case ASSETS_MAC_CHANGED -> {
                UUID uuid = scope.uuidAttribute("link_uuid");
                Optional<AssetLinkEntry> link = uuid == null ? Optional.empty() : nzyme.getAssetsManager().identity()
                        .findLink(uuid, scope.organizationId(), scope.tenantId());
                if (link.isPresent()) {
                    String status = link.get().status();
                    if (!AssetIdentity.LINK_STATUS_DISMISSED.equals(status)) {
                        actions.add(AlertAction.of(ASSET_LINK_CONFIRM,
                                AssetIdentity.LINK_STATUS_CONFIRMED.equals(status), "link_uuid", uuid.toString()));
                    }
                    if (!AssetIdentity.LINK_STATUS_CONFIRMED.equals(status)) {
                        actions.add(AlertAction.of(ASSET_LINK_DISMISS,
                                AssetIdentity.LINK_STATUS_DISMISSED.equals(status), "link_uuid", uuid.toString()));
                    }
                }
                identityExclusion(mac, AssetIdentityType.MAC_CHANGE, actions);
            }
            default -> { }
        }
    }

    private void identityExclusion(@Nullable String mac, AssetIdentityType type, List<AlertAction> actions) {
        if (mac != null) {
            actions.add(AlertAction.available(ASSET_IDENTITY_CREATE_EXCLUSION, "mac", mac, "type", type.name()));
        }
    }

    private void uav(Scope scope, List<AlertAction> actions) {
        String identifier = scope.attribute("identifier");
        if (identifier == null) {
            return;
        }

        List<UUID> taps = nzyme.getTapManager()
                .allTapUUIDsAccessibleByUser(scope.user(), scope.organizationId(), scope.tenantId());
        Optional<UavEntry> uav = nzyme.getUav()
                .findUav(identifier, scope.organizationId(), scope.tenantId(), taps);
        if (uav.isEmpty()) {
            return;
        }

        // The classification of a matching UAV type wins over the manual one, so setting it would change nothing.
        boolean classifiedByType = nzyme.getUav()
                .matchUavType(uav.get().idSerial(), scope.tenantId(), scope.organizationId())
                .map(type -> type.defaultClassification() != null)
                .orElse(false);
        if (!classifiedByType) {
            actions.add(AlertAction.available(UAV_CLASSIFY,
                    "identifier", identifier, "classification", uav.get().classification()));
        }
    }

    private record Scope(DetectionAlertEntry alert,
                         Map<String, String> attributes,
                         AuthenticatedUser user,
                         boolean administrator,
                         Set<String> permissions) {

        UUID organizationId() {
            return alert.organizationId();
        }

        UUID tenantId() {
            return alert.tenantId();
        }

        @Nullable
        String attribute(String key) {
            return Strings.emptyToNull(attributes.get(key));
        }

        @Nullable
        UUID uuidAttribute(String key) {
            String value = attribute(key);
            if (value == null) {
                return null;
            }

            try {
                return UUID.fromString(value);
            } catch (IllegalArgumentException e) {
                return null;
            }
        }

        @Nullable
        Long longAttribute(String key) {
            String value = attribute(key);
            if (value == null) {
                return null;
            }

            try {
                return Long.parseLong(value);
            } catch (NumberFormatException e) {
                return null;
            }
        }

    }

}
