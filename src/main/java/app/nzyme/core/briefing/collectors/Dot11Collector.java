package app.nzyme.core.briefing.collectors;

import app.nzyme.core.briefing.*;
import app.nzyme.core.dot11.monitoring.ssids.KnownSSIDsRegistryKeys;
import app.nzyme.core.detection.alerts.DetectionType;
import app.nzyme.core.detection.alerts.db.DetectionAlertEntry;
import app.nzyme.core.dot11.Dot11;
import app.nzyme.core.dot11.db.BSSIDAndSSIDCountHistogramEntry;
import app.nzyme.core.dot11.db.DiscoHistogramEntry;
import app.nzyme.core.dot11.db.monitoring.MonitoredBSSID;
import app.nzyme.core.dot11.db.monitoring.MonitoredChannel;
import app.nzyme.core.dot11.db.monitoring.MonitoredSSID;
import app.nzyme.core.rest.responses.briefing.*;
import app.nzyme.core.taps.db.metrics.Dot11FrequencyAndChannelWidthEntry;
import app.nzyme.core.util.Bucketing;
import app.nzyme.core.util.TimeRange;
import app.nzyme.core.util.filters.Filters;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import jakarta.annotation.Nullable;

import java.util.*;

import static app.nzyme.core.briefing.BriefingItems.plural;

public class Dot11Collector {

    // Described per network here, so the alerts collector skips them.
    public static final Set<DetectionType> NETWORK_ALERT_TYPES = Sets.newHashSet(
            DetectionType.DOT11_MONITOR_BSSID, DetectionType.DOT11_MONITOR_CHANNEL,
            DetectionType.DOT11_MONITOR_SECURITY_SUITE, DetectionType.DOT11_MONITOR_FINGERPRINT,
            DetectionType.DOT11_MONITOR_SIGNAL_TRACK, DetectionType.DOT11_MONITOR_DISCO_ANOMALIES,
            DetectionType.DOT11_MONITOR_SIMILAR_LOOKING_SSID, DetectionType.DOT11_MONITOR_SSID_SUBSTRING,
            DetectionType.DOT11_UNAPPROVED_CLIENT
    );

    private final BriefingContext ctx;
    private final AlertsCollector alerts;

    private record NetworkState(MonitoredSSID network,
                                List<MonitoredBSSID> bssids,
                                long bssidsWithoutFingerprints,
                                List<MonitoredChannel> channels,
                                long securitySuites,
                                Set<DetectionType> activeTypes,
                                Set<DetectionType> newTypesInWindow,
                                long pendingClients,
                                long newPendingClients) { }

    private List<NetworkState> networks;
    private Set<Integer> expectedFrequencies;
    private Set<Integer> scannedFrequencies;
    private Map<String, List<Integer>> missingFrequenciesByNetwork;
    private long knownNetworksPending;
    private long knownNetworksNew;

    public Dot11Collector(BriefingContext ctx, AlertsCollector alerts) {
        this.ctx = ctx;
        this.alerts = alerts;
    }

    public Dot11Collector load() {
        loadMonitoredNetworks();
        loadChannelCoverage();
        loadApprovals();

        return this;
    }

    public List<BriefingAttentionItemResponse> attention() {
        List<BriefingAttentionItemResponse> items = Lists.newArrayList();

        for (NetworkState state : networks) {
            MonitoredSSID network = state.network();
            BriefingLinkResponse link = BriefingLinks.to("DOT11_MONITORED_NETWORK", "uuid", network.uuid().toString());

            if (!network.isEnabled()) {
                items.add(BriefingItems.attention(BriefingSeverity.INFO, "monitoring", "DOT11",
                        "Monitoring of network \"" + network.ssid() + "\" is switched off",
                        "No detections run for this network until it is enabled again.",
                        1, null, link));
                continue;
            }

            if (!state.activeTypes().isEmpty()) {
                items.add(BriefingItems.attention(BriefingSeverity.ALERT, "monitoring", "DOT11",
                        "Network \"" + network.ssid() + "\" is alerting right now",
                        describeTypes(state.activeTypes()),
                        state.activeTypes().size(), null, link));
            } else if (!state.newTypesInWindow().isEmpty()) {
                items.add(BriefingItems.attention(BriefingSeverity.ALERT, "monitoring", "DOT11",
                        "Network \"" + network.ssid() + "\" alerted in this window",
                        "Quiet again now. " + describeTypes(state.newTypesInWindow()),
                        state.newTypesInWindow().size(), null, link));
            }

            if (state.pendingClients() > 0) {
                items.add(BriefingItems.attention(BriefingSeverity.NOTICE, "approval", "DOT11",
                        state.pendingClients() + " " + plural(state.pendingClients(), "client", "clients")
                                + " on \"" + network.ssid() + "\" " + plural(state.pendingClients(), "waits", "wait")
                                + " for approval",
                        state.newPendingClients() + " of them first seen in this window.",
                        state.pendingClients(), null,
                        BriefingLinks.to("DOT11_MONITORED_CLIENTS", "uuid", network.uuid().toString())));
            }
        }

        if (knownNetworksPending > 0) {
            items.add(BriefingItems.attention(BriefingSeverity.NOTICE, "approval", "DOT11",
                    knownNetworksPending + " " + plural(knownNetworksPending, "SSID waits", "SSIDs wait") + " for approval",
                    knownNetworksNew + " of them first seen in this window.",
                    knownNetworksPending, null, BriefingLinks.to("DOT11_KNOWN_NETWORKS")));
        }

        for (Map.Entry<String, List<Integer>> missing : missingFrequenciesByNetwork.entrySet()) {
            List<String> channels = missing.getValue().stream().map(Dot11Collector::describeFrequency).toList();
            items.add(BriefingItems.attention(BriefingSeverity.NOTICE, "coverage", "DOT11",
                    "No tap scans " + plural(channels.size(), "a channel", "channels")
                            + " expected for \"" + missing.getKey() + "\"",
                    "Not scanned: " + String.join(", ", channels) + ". Unexpected activity there goes unnoticed.",
                    channels.size(), null, tapsLink()));
        }

        return items;
    }

    public List<BriefingCheckResponse> checks(TapsCollector taps) {
        List<BriefingCheckResponse> checks = Lists.newArrayList();

        if (ctx.canManageDot11Monitoring()) {
            if (networks.isEmpty()) {
                checks.add(BriefingItems.warn("dot11_monitored_networks", "No monitored networks",
                        "Nzyme only watches the integrity of WiFi networks it monitors. Add your own networks to be alerted to evil twins, impersonation and unexpected changes.",
                        BriefingLinks.to("DOT11_MONITORING_CREATE")));
            } else {
                long enabled = networks.stream().filter(n -> n.network().isEnabled()).count();
                checks.add(BriefingItems.check("dot11_monitored_networks", enabled == networks.size() ? "OK" : "INFO",
                        enabled + " of " + networks.size() + " monitored " + plural(networks.size(), "network", "networks") + " enabled",
                        null, BriefingLinks.to("DOT11_MONITORING")));

                for (NetworkState state : networks) {
                    if (!state.network().isEnabled()) {
                        continue;
                    }
                    List<String> gaps = Lists.newArrayList();
                    if (state.bssids().isEmpty() && state.network().enabledUnexpectedBSSID()) {
                        gaps.add("no expected BSSIDs");
                    }
                    if (state.channels().isEmpty() && state.network().enabledUnexpectedChannel()) {
                        gaps.add("no expected channels");
                    }
                    if (state.securitySuites() == 0 && state.network().enabledUnexpectedSecuritySuites()) {
                        gaps.add("no expected security suites");
                    }
                    if (state.bssidsWithoutFingerprints() > 0 && state.network().enabledUnexpectedFingerprint()) {
                        gaps.add(state.bssidsWithoutFingerprints() + " "
                                + plural(state.bssidsWithoutFingerprints(), "BSSID", "BSSIDs") + " without fingerprints");
                    }
                    // Every other method is fine to switch off. These two guard against impersonation
                    // and need no expected values, so there is no reason to run without them.
                    if (!state.network().enabledSimilarLookingSSID()) {
                        gaps.add("similar SSIDs detection switched off");
                    }
                    if (!state.network().enabledSSIDSubstring()) {
                        gaps.add("restricted SSID substrings detection switched off");
                    }

                    BriefingLinkResponse link = BriefingLinks.to("DOT11_MONITORED_NETWORK", "uuid", state.network().uuid().toString());
                    if (gaps.isEmpty()) {
                        checks.add(BriefingItems.ok("dot11_network_" + state.network().uuid(),
                                "\"" + state.network().ssid() + "\" is fully configured", null, link));
                    } else {
                        checks.add(BriefingItems.warn("dot11_network_" + state.network().uuid(),
                                "\"" + state.network().ssid() + "\" has configuration gaps",
                                capitalize(String.join(", ", gaps)) + ".", link));
                    }
                }
            }

            if (!expectedFrequencies.isEmpty()) {
                List<Integer> missing = expectedFrequencies.stream().filter(f -> !scannedFrequencies.contains(f)).sorted().toList();
                if (missing.isEmpty()) {
                    checks.add(BriefingItems.ok("dot11_coverage", "All " + expectedFrequencies.size()
                            + " monitored " + plural(expectedFrequencies.size(), "channel is", "channels are") + " scanned",
                            null, tapsLink()));
                } else {
                    checks.add(BriefingItems.warn("dot11_coverage", missing.size() + " monitored "
                            + plural(missing.size(), "channel is", "channels are") + " not scanned by any tap",
                            String.join(", ", missing.stream().map(Dot11Collector::describeFrequency).toList()) + ".",
                            tapsLink()));
                }
            }

            boolean knownNetworkMonitoring = ctx.nzyme().getDatabaseCoreRegistry()
                    .getValue(KnownSSIDsRegistryKeys.IS_ENABLED.key(), ctx.organizationId(), ctx.tenantId())
                    .map(Boolean::parseBoolean).orElse(false);
            checks.add(BriefingItems.check("dot11_known_networks", knownNetworkMonitoring ? "OK" : "INFO",
                    knownNetworkMonitoring ? "Known network monitoring is on" : "Known network monitoring is off",
                    knownNetworkMonitoring ? null : "New SSIDs showing up in range are not flagged for approval.",
                    BriefingLinks.to("DOT11_KNOWN_NETWORKS")));

            long clientMonitoring = networks.stream().filter(n -> n.network().enabledClientMonitoring()).count();
            checks.add(BriefingItems.check("dot11_client_monitoring", clientMonitoring > 0 ? "OK" : "INFO",
                    clientMonitoring == 0 ? "Client monitoring is off for all networks"
                            : "Client monitoring on " + clientMonitoring + " " + plural(clientMonitoring, "network", "networks"),
                    clientMonitoring == 0 ? "Unknown clients joining your networks are not tracked." : null,
                    BriefingLinks.to("DOT11_MONITORING")));
        }

        return checks;
    }

    public Map<String, Object> activity() {
        Map<String, Object> activity = Maps.newHashMap();

        TimeRange combined = ctx.window().combined();
        Bucketing.BucketingConfiguration bucketing = Bucketing.getConfig(combined);
        Dot11 dot11 = ctx.nzyme().getDot11();

        List<BriefingHistogramBucketResponse> bssidHistogram = Lists.newArrayList();
        List<BriefingHistogramBucketResponse> ssidHistogram = Lists.newArrayList();
        for (BSSIDAndSSIDCountHistogramEntry e : dot11.getBSSIDAndSSIDCountHistogram(combined, bucketing, Filters.create(Maps.newHashMap()), ctx.taps())) {
            bssidHistogram.add(BriefingHistogramBucketResponse.create(e.bucket(), e.bssidCount()));
            ssidHistogram.add(BriefingHistogramBucketResponse.create(e.bucket(), e.ssidCount()));
        }
        activity.put("bssids", bssidHistogram);
        activity.put("ssids", ssidHistogram);

        List<BriefingHistogramBucketResponse> disco = Lists.newArrayList();
        for (DiscoHistogramEntry e : dot11.getDiscoHistogram(combined, bucketing, ctx.taps(), Filters.create(Maps.newHashMap()), null)) {
            disco.add(BriefingHistogramBucketResponse.create(e.bucket(), e.frameCount()));
        }
        activity.put("disco", disco);

        return activity;
    }

    private void loadMonitoredNetworks() {
        networks = Lists.newArrayList();

        if (!ctx.canManageDot11Monitoring()) {
            return;
        }

        Dot11 dot11 = ctx.nzyme().getDot11();
        for (MonitoredSSID network : dot11.findAllMonitoredSSIDs(ctx.organizationId(), ctx.tenantId())) {
            List<MonitoredBSSID> bssids = dot11.findMonitoredBSSIDsOfMonitoredNetwork(network.id());
            long withoutFingerprints = bssids.stream()
                    .filter(b -> dot11.findMonitoredFingerprintsOfMonitoredBSSID(b.id()).isEmpty())
                    .count();
            List<MonitoredChannel> channels = dot11.findMonitoredChannelsOfMonitoredNetwork(network.id());
            long suites = dot11.findMonitoredSecuritySuitesOfMonitoredNetwork(network.id()).size();

            Set<DetectionType> activeTypes = Sets.newHashSet();
            if (ctx.canViewAlerts()) {
                for (DetectionAlertEntry alert : ctx.nzyme().getDetectionAlertService().findAllActiveAlertsOfMonitoredNetwork(network.uuid())) {
                    DetectionType type = AlertsCollector.typeOf(alert);
                    if (type != null && isMethodEnabled(network, type)) {
                        activeTypes.add(type);
                    }
                }
            }

            Set<DetectionType> newTypes = Sets.newHashSet();
            if (ctx.canViewAlerts()) {
                for (DetectionAlertEntry alert : alerts.newAlerts()) {
                    if (network.uuid().equals(alert.dot11MonitoredNetworkId())) {
                        DetectionType type = AlertsCollector.typeOf(alert);
                        if (type != null && isMethodEnabled(network, type)) {
                            newTypes.add(type);
                        }
                    }
                }
            }

            long pendingClients = 0;
            long newPendingClients = 0;
            if (network.enabledClientMonitoring()) {
                pendingClients = ctx.nzyme().getDatabase().withHandle(handle ->
                        handle.createQuery("SELECT COUNT(*) FROM dot11_known_clients WHERE monitored_network_id = :id " +
                                        "AND is_approved = false AND is_ignored = false")
                                .bind("id", network.id())
                                .mapTo(Long.class)
                                .one());
                newPendingClients = ctx.nzyme().getDatabase().withHandle(handle ->
                        handle.createQuery("SELECT COUNT(*) FROM dot11_known_clients WHERE monitored_network_id = :id " +
                                        "AND is_approved = false AND is_ignored = false " +
                                        "AND first_seen >= :from AND first_seen <= :to")
                                .bind("id", network.id())
                                .bind("from", ctx.window().from())
                                .bind("to", ctx.window().to())
                                .mapTo(Long.class)
                                .one());
            }

            networks.add(new NetworkState(network, bssids, withoutFingerprints, channels, suites, activeTypes,
                    newTypes, pendingClients, newPendingClients));
        }
    }

    private void loadChannelCoverage() {
        expectedFrequencies = Sets.newHashSet();
        scannedFrequencies = Sets.newHashSet();
        missingFrequenciesByNetwork = Maps.newTreeMap();

        for (UUID tap : ctx.taps()) {
            for (Dot11FrequencyAndChannelWidthEntry f : ctx.nzyme().getTapManager().findDot11FrequenciesOfTap(tap)) {
                scannedFrequencies.add(f.frequency());
            }
        }

        for (NetworkState state : networks) {
            if (!state.network().isEnabled() || !state.network().enabledUnexpectedChannel()) {
                continue;
            }
            List<Integer> missing = Lists.newArrayList();
            for (MonitoredChannel channel : state.channels()) {
                int frequency = (int) channel.frequency();
                expectedFrequencies.add(frequency);
                if (!scannedFrequencies.contains(frequency)) {
                    missing.add(frequency);
                }
            }
            if (!missing.isEmpty() && !ctx.taps().isEmpty()) {
                Collections.sort(missing);
                missingFrequenciesByNetwork.put(state.network().ssid(), missing);
            }
        }
    }

    private void loadApprovals() {
        knownNetworksPending = 0;
        knownNetworksNew = 0;

        if (!ctx.canManageDot11Monitoring()) {
            return;
        }

        knownNetworksPending = ctx.nzyme().getDatabase().withHandle(handle ->
                handle.createQuery("SELECT COUNT(*) FROM dot11_known_networks WHERE organization_id = :organization_id " +
                                "AND tenant_id = :tenant_id AND is_approved = false AND is_ignored = false")
                        .bind("organization_id", ctx.organizationId())
                        .bind("tenant_id", ctx.tenantId())
                        .mapTo(Long.class)
                        .one());

        knownNetworksNew = ctx.nzyme().getDatabase().withHandle(handle ->
                handle.createQuery("SELECT COUNT(*) FROM dot11_known_networks WHERE organization_id = :organization_id " +
                                "AND tenant_id = :tenant_id AND is_approved = false AND is_ignored = false " +
                                "AND first_seen >= :from AND first_seen <= :to")
                        .bind("organization_id", ctx.organizationId())
                        .bind("tenant_id", ctx.tenantId())
                        .bind("from", ctx.window().from())
                        .bind("to", ctx.window().to())
                        .mapTo(Long.class)
                        .one());
    }

    private static boolean isMethodEnabled(MonitoredSSID network, DetectionType type) {
        return switch (type) {
            case DOT11_MONITOR_BSSID -> network.enabledUnexpectedBSSID();
            case DOT11_MONITOR_CHANNEL -> network.enabledUnexpectedChannel();
            case DOT11_MONITOR_SECURITY_SUITE -> network.enabledUnexpectedSecuritySuites();
            case DOT11_MONITOR_FINGERPRINT -> network.enabledUnexpectedFingerprint();
            case DOT11_MONITOR_SIGNAL_TRACK -> network.enabledUnexpectedSignalTracks();
            case DOT11_MONITOR_DISCO_ANOMALIES -> network.enabledDiscoMonitor();
            case DOT11_MONITOR_SIMILAR_LOOKING_SSID -> network.enabledSimilarLookingSSID();
            case DOT11_MONITOR_SSID_SUBSTRING -> network.enabledSSIDSubstring();
            case DOT11_UNAPPROVED_CLIENT -> network.enabledClientMonitoring() && network.enabledClientEventing();
            default -> false;
        };
    }

    private static String describeTypes(Set<DetectionType> types) {
        List<String> names = types.stream()
                .map(Dot11Collector::shortTypeName)
                .sorted()
                .toList();
        return capitalize(String.join(", ", names)) + ".";
    }

    private static String shortTypeName(DetectionType type) {
        return switch (type) {
            case DOT11_MONITOR_BSSID -> "unexpected BSSID";
            case DOT11_MONITOR_CHANNEL -> "unexpected channel";
            case DOT11_MONITOR_SECURITY_SUITE -> "unexpected security suite";
            case DOT11_MONITOR_FINGERPRINT -> "unexpected fingerprint";
            case DOT11_MONITOR_SIGNAL_TRACK -> "multiple signal tracks";
            case DOT11_MONITOR_DISCO_ANOMALIES -> "disconnection anomalies";
            case DOT11_MONITOR_SIMILAR_LOOKING_SSID -> "similar looking SSID";
            case DOT11_MONITOR_SSID_SUBSTRING -> "forbidden SSID substring";
            case DOT11_UNAPPROVED_CLIENT -> "unapproved client";
            default -> type.getTitle().toLowerCase();
        };
    }

    private @Nullable BriefingLinkResponse tapsLink() {
        return ctx.canOpenTaps() ? BriefingLinks.to("TAPS") : null;
    }

    public static String describeFrequency(int frequency) {
        return "channel " + Dot11.frequencyToChannel(frequency) + " (" + frequency + " MHz)";
    }

    private static String capitalize(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

}
