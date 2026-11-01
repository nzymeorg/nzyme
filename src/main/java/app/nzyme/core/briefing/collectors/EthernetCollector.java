package app.nzyme.core.briefing.collectors;

import app.nzyme.core.briefing.*;
import app.nzyme.core.ethernet.beacons.BeaconsConfiguration;
import app.nzyme.core.ethernet.dns.tunnels.DnsTunnelConfiguration;
import app.nzyme.core.ethernet.icmp.IcmpConfiguration;
import app.nzyme.core.ethernet.recon.ReconConfiguration;
import app.nzyme.core.ethernet.spoofing.Spoofing;
import app.nzyme.core.ethernet.spoofing.SpoofingConfiguration;
import app.nzyme.core.rest.responses.briefing.*;
import app.nzyme.core.util.Bucketing;
import app.nzyme.core.util.TimeRange;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.jdbi.v3.core.Handle;
import org.joda.time.DateTime;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static app.nzyme.core.briefing.BriefingItems.plural;

public class EthernetCollector {

    private final BriefingContext ctx;
    private final AlertsCollector alerts;

    private List<Map<String, Object>> portalIntegrityErrors;
    private Map<String, DateTime> portalIntegrityLastReport;
    private long monitorsTotal;
    private long monitorsEnabled;
    private long contextNetworks;

    private List<String> disabledMethods;
    private int totalMethods;

    private boolean beaconDetectionEnabled;
    private long beaconsTotal;
    private long beaconsUnclassified;

    private boolean dhcpDetectionEnabled;
    private DateTime dhcpLearningEnd;
    private boolean dhcpLearningActive;
    private long dhcpServersTrusted;
    private long dhcpServersUntrusted;

    public EthernetCollector(BriefingContext ctx, AlertsCollector alerts) {
        this.ctx = ctx;
        this.alerts = alerts;
    }

    public EthernetCollector load() {
        ctx.nzyme().getDatabase().useHandle(handle -> {
            loadPortalIntegrity(handle);

            monitorsTotal = handle.createQuery("SELECT COUNT(*) FROM monitors WHERE organization_id = :organization_id " +
                            "AND tenant_id = :tenant_id AND type LIKE 'ETHERNET_%'")
                    .bind("organization_id", ctx.organizationId())
                    .bind("tenant_id", ctx.tenantId())
                    .mapTo(Long.class).one();
            monitorsEnabled = handle.createQuery("SELECT COUNT(*) FROM monitors WHERE organization_id = :organization_id " +
                            "AND tenant_id = :tenant_id AND type LIKE 'ETHERNET_%' AND enabled = true")
                    .bind("organization_id", ctx.organizationId())
                    .bind("tenant_id", ctx.tenantId())
                    .mapTo(Long.class).one();

            contextNetworks = handle.createQuery("SELECT COUNT(*) FROM context_networks " +
                            "WHERE organization_id = :organization_id AND tenant_id = :tenant_id")
                    .bind("organization_id", ctx.organizationId())
                    .bind("tenant_id", ctx.tenantId())
                    .mapTo(Long.class).one();

            beaconsTotal = handle.createQuery("SELECT COUNT(*) FROM beacons " +
                            "WHERE organization_id = :organization_id AND tenant_id = :tenant_id")
                    .bind("organization_id", ctx.organizationId())
                    .bind("tenant_id", ctx.tenantId())
                    .mapTo(Long.class).one();
            beaconsUnclassified = handle.createQuery("SELECT COUNT(*) FROM beacons " +
                            "WHERE organization_id = :organization_id AND tenant_id = :tenant_id " +
                            "AND classification = 'UNKNOWN'")
                    .bind("organization_id", ctx.organizationId())
                    .bind("tenant_id", ctx.tenantId())
                    .mapTo(Long.class).one();

            dhcpServersTrusted = handle.createQuery("SELECT COUNT(*) FROM spoofing_dhcp_servers " +
                            "WHERE organization_id = :organization_id AND tenant_id = :tenant_id AND trusted = true")
                    .bind("organization_id", ctx.organizationId())
                    .bind("tenant_id", ctx.tenantId())
                    .mapTo(Long.class).one();
            dhcpServersUntrusted = handle.createQuery("SELECT COUNT(*) FROM spoofing_dhcp_servers " +
                            "WHERE organization_id = :organization_id AND tenant_id = :tenant_id AND trusted = false")
                    .bind("organization_id", ctx.organizationId())
                    .bind("tenant_id", ctx.tenantId())
                    .mapTo(Long.class).one();
        });

        loadDetectionMethods();

        return this;
    }

    // DHCP learning mirrors the DHCP servers page: starts with the first server seen unless restarted, can end early.
    private void loadDetectionMethods() {
        disabledMethods = Lists.newArrayList();
        totalMethods = 0;

        BeaconsConfiguration beacons = ctx.nzyme().getEthernet().beacons().getConfiguration(ctx.organizationId(), ctx.tenantId());
        ReconConfiguration recon = ctx.nzyme().getEthernet().recon().getConfiguration(ctx.organizationId(), ctx.tenantId());
        SpoofingConfiguration spoofing = ctx.nzyme().getEthernet().spoofing().getConfiguration(ctx.organizationId(), ctx.tenantId());
        DnsTunnelConfiguration dnsTunnels = ctx.nzyme().getEthernet().dnsTunnels().getConfiguration(ctx.organizationId(), ctx.tenantId());
        IcmpConfiguration icmp = ctx.nzyme().getEthernet().icmp().getConfiguration(ctx.organizationId(), ctx.tenantId());

        method("beacons", beacons.detectionEnabled());
        method("recon scans", recon.detectionEnabled());
        method("ARP spoofing", spoofing.arpDetectionEnabled());
        method("rogue DHCP servers", spoofing.dhcpDetectionEnabled());
        method("DNS spoofing", spoofing.dnsDetectionEnabled());
        method("name resolution spoofing", spoofing.nameresDetectionEnabled());
        method("DNS tunnels", dnsTunnels.detectionEnabled());
        method("ICMP redirects", icmp.redirectDetectionEnabled());
        method("ICMP tunnels", icmp.tunnelDetectionEnabled());

        beaconDetectionEnabled = beacons.detectionEnabled();
        dhcpDetectionEnabled = spoofing.dhcpDetectionEnabled();

        Spoofing spoofingService = ctx.nzyme().getEthernet().spoofing();
        DateTime learningStart = spoofingService.findDhcpLearningStartedAt(ctx.organizationId(), ctx.tenantId())
                .or(() -> spoofingService.findEarliestDhcpServerFirstSeen(ctx.organizationId(), ctx.tenantId()))
                .orElse(null);
        Optional<DateTime> learningEndedAt = spoofingService.findDhcpLearningEndedAt(ctx.organizationId(), ctx.tenantId());
        dhcpLearningEnd = learningStart == null ? null : learningStart.plusHours(spoofing.dhcpLearningPeriodHours());
        if (dhcpLearningEnd != null && learningEndedAt.isPresent() && learningEndedAt.get().isBefore(dhcpLearningEnd)) {
            dhcpLearningEnd = learningEndedAt.get();
        }
        dhcpLearningActive = dhcpLearningEnd != null && spoofing.dhcpLearningPeriodHours() > 0
                && dhcpLearningEnd.isAfterNow();
    }

    private static String describeRemaining(DateTime until) {
        long minutes = Math.max(1, (until.getMillis() - DateTime.now().getMillis()) / 60000);
        if (minutes < 60) {
            return minutes + " " + plural(minutes, "minute", "minutes");
        }
        long hours = minutes / 60;
        if (hours < 48) {
            return hours + " " + plural(hours, "hour", "hours");
        }
        long days = hours / 24;
        return days + " " + plural(days, "day", "days");
    }

    private void method(String name, boolean enabled) {
        totalMethods++;
        if (!enabled) {
            disabledMethods.add(name);
        }
    }

    public List<BriefingAttentionItemResponse> attention() {
        List<BriefingAttentionItemResponse> items = Lists.newArrayList();

        for (Map<String, Object> error : portalIntegrityErrors) {
            items.add(BriefingItems.attention(BriefingSeverity.NOTICE, "portal", "ETHERNET",
                    "Portal integrity probe on tap \"" + error.get("tap_name") + "\" failed",
                    String.valueOf(error.get("error")), 1, (DateTime) error.get("probed_at"),
                    BriefingLinks.to("PORTAL_INTEGRITY")));
        }

        return items;
    }

    public List<BriefingCheckResponse> checks(TapsCollector taps) {
        List<BriefingCheckResponse> checks = Lists.newArrayList();

        if (contextNetworks == 0) {
            if (taps.capturesType("Ethernet")) {
                checks.add(BriefingItems.info("context_networks", "Add your internal networks for better context",
                        "Nzyme uses them to tell internal from external traffic, to identify devices and to enrich "
                                + "findings with network names.",
                        BriefingLinks.to("CONTEXT_NETWORKS")));
            }
        } else {
            checks.add(BriefingItems.ok("context_networks", contextNetworks + " internal "
                    + plural(contextNetworks, "network", "networks") + " defined", null, BriefingLinks.to("CONTEXT_NETWORKS")));
        }

        BriefingLinkResponse monitors = ctx.canManageEthernetMonitoring() ? BriefingLinks.to("ETHERNET_MONITORS") : null;
        if (monitorsTotal == 0) {
            checks.add(BriefingItems.info("ethernet_monitors", "No Ethernet monitors configured",
                    "Monitors alert on custom conditions in your traffic.", monitors));
        } else {
            checks.add(BriefingItems.check("ethernet_monitors", monitorsEnabled == monitorsTotal ? "OK" : "INFO",
                    monitorsEnabled + " of " + monitorsTotal + " Ethernet " + plural(monitorsTotal, "monitor", "monitors") + " enabled",
                    null, monitors));
        }

        if (ctx.canManageEthernetMonitoring() && !disabledMethods.isEmpty()) {
            checks.add(BriefingItems.info("ethernet_methods", disabledMethods.size() + " of " + totalMethods
                            + " Ethernet detection " + plural(disabledMethods.size(), "method is", "methods are") + " switched off",
                    "Off: " + String.join(", ", disabledMethods) + ".", null));
        }

        if (beaconDetectionEnabled) {
            if (beaconsUnclassified > 0) {
                checks.add(BriefingItems.warn("beacons_classification", beaconsUnclassified + " of " + beaconsTotal + " "
                                + plural(beaconsTotal, "beacon waits", "beacons wait") + " for classification",
                        "Mark each one benign, suspicious or malicious so Nzyme knows which beacons matter.",
                        BriefingLinks.to("BEACONS")));
            } else if (beaconsTotal > 0) {
                checks.add(BriefingItems.ok("beacons_classification", "All " + beaconsTotal + " "
                        + plural(beaconsTotal, "beacon is", "beacons are") + " classified", null, BriefingLinks.to("BEACONS")));
            } else {
                checks.add(BriefingItems.ok("beacons_classification", "No beacons detected yet", null,
                        BriefingLinks.to("BEACONS")));
            }
        }

        if (dhcpDetectionEnabled) {
            if (dhcpLearningActive) {
                checks.add(BriefingItems.warn("dhcp_learning", "DHCP server learning period ends in "
                                + describeRemaining(dhcpLearningEnd),
                        "Every DHCP server that answers clients until then is trusted automatically, including rogue ones. "
                                + dhcpServersTrusted + " " + plural(dhcpServersTrusted, "server", "servers") + " trusted so far.",
                        BriefingLinks.to("SPOOFING_DHCP")));
            } else if (dhcpServersTrusted + dhcpServersUntrusted == 0) {
                checks.add(BriefingItems.info("dhcp_learning", "No DHCP servers observed yet",
                        "Rogue server detection has nothing to compare against until a DHCP server answers a client.",
                        BriefingLinks.to("SPOOFING_DHCP")));
            } else if (dhcpServersUntrusted > 0) {
                checks.add(BriefingItems.warn("dhcp_learning", dhcpServersUntrusted + " untrusted DHCP "
                                + plural(dhcpServersUntrusted, "server", "servers") + " next to " + dhcpServersTrusted + " trusted",
                        "Learning is complete. Trust the untrusted servers if they are yours, otherwise they are rogue.",
                        BriefingLinks.to("SPOOFING_DHCP")));
            } else {
                checks.add(BriefingItems.ok("dhcp_learning", dhcpServersTrusted + " trusted DHCP "
                                + plural(dhcpServersTrusted, "server", "servers") + ", learning complete",
                        null, BriefingLinks.to("SPOOFING_DHCP")));
            }
        }

        // Taps do not report whether portal integrity is configured, so missing probes are never a warning.
        if (!portalIntegrityLastReport.isEmpty()) {
            checks.add(BriefingItems.ok("portal_integrity",
                    "Portal integrity probes report from " + portalIntegrityLastReport.size() + " "
                            + plural(portalIntegrityLastReport.size(), "tap", "taps"),
                    null, BriefingLinks.to("PORTAL_INTEGRITY")));
        }

        return checks;
    }

    public Map<String, Object> activity() {
        Map<String, Object> activity = Maps.newHashMap();

        TimeRange combined = ctx.window().combined();
        Bucketing.BucketingConfiguration bucketing = Bucketing.getConfig(combined);

        List<BriefingHistogramBucketResponse> assets = ctx.nzyme().getDatabase().withHandle(handle ->
                handle.createQuery("SELECT DATE_TRUNC(:date_trunc, timestamp) AS bucket, MAX(asset_count) AS value " +
                                "FROM assets_statistics WHERE organization_id = :organization_id " +
                                "AND tenant_id = :tenant_id AND timestamp >= :from AND timestamp <= :to " +
                                "GROUP BY bucket ORDER BY bucket")
                        .bind("date_trunc", bucketing.type().getDateTruncName())
                        .bind("organization_id", ctx.organizationId())
                        .bind("tenant_id", ctx.tenantId())
                        .bind("from", combined.from())
                        .bind("to", combined.to())
                        .map((rs, c) -> BriefingHistogramBucketResponse.create(
                                new DateTime(rs.getTimestamp("bucket")), rs.getLong("value")))
                        .list()
        );
        activity.put("assets", assets);

        return activity;
    }

    private void loadPortalIntegrity(Handle handle) {
        portalIntegrityErrors = Lists.newArrayList();
        portalIntegrityLastReport = Maps.newTreeMap();

        if (ctx.taps().isEmpty()) {
            return;
        }

        handle.createQuery("SELECT r.tap_uuid, t.name AS tap_name, r.error, r.probed_at FROM portal_integrity_reports AS r " +
                        "JOIN taps AS t ON t.uuid = r.tap_uuid WHERE r.tap_uuid IN (<taps>) " +
                        "AND r.probed_at >= :from AND r.probed_at <= :to AND r.error IS NOT NULL AND r.error <> '' " +
                        "ORDER BY r.probed_at DESC LIMIT 5")
                .bindList("taps", ctx.taps())
                .bind("from", ctx.window().from())
                .bind("to", ctx.window().to())
                .map((rs, c) -> {
                    Map<String, Object> row = Maps.newHashMap();
                    row.put("tap_name", rs.getString("tap_name"));
                    row.put("error", rs.getString("error"));
                    row.put("probed_at", new DateTime(rs.getTimestamp("probed_at")));
                    return row;
                })
                .forEach(portalIntegrityErrors::add);

        handle.createQuery("SELECT t.name AS tap_name, MAX(r.probed_at) AS last_report FROM portal_integrity_reports AS r " +
                        "JOIN taps AS t ON t.uuid = r.tap_uuid WHERE r.tap_uuid IN (<taps>) " +
                        "AND r.probed_at >= :active_since GROUP BY t.name")
                .bind("active_since", DateTime.now().minusHours(24))
                .bindList("taps", ctx.taps())
                .map((rs, c) -> Map.entry(rs.getString("tap_name"), new DateTime(rs.getTimestamp("last_report"))))
                .forEach(e -> portalIntegrityLastReport.put(e.getKey(), e.getValue()));
    }

}
