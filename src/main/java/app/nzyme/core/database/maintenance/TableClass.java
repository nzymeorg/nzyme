package app.nzyme.core.database.maintenance;

import java.util.List;
import java.util.Optional;

public enum TableClass {

    UPSERT(
            "upsert",
            "Session and ledger tables",
            "Inserted once and updated with every tap report. Vacuumed early and without throttling, with free " +
                    "space on every page for updates.",
            1, 1000, 2, 85, true,
            List.of("l4_sessions", "tls_sessions", "nat_traversal_discoveries", "nat_stun_negotiation_flows",
                    "vpn_tunnels", "ssh_sessions", "socks_tunnels", "rtsp_streams", "dhcp_transactions",
                    "webrtc_conversations", "tcp_probes", "assets_attributes", "assets_communication",
                    "assets_activity", "beacon_checkins", "tasks_queue", "recon_probe_history")
    ),

    APPEND(
            "append",
            "Time series tables",
            "Inserted, never updated, and deleted in bulk when they age out. Pages are filled completely.",
            5, 1000, 2, 100, false,
            List.of("arp_packets", "icmp_packets", "dns_log", "dns_pairs", "dns_statistics", "l4_statistics",
                    "ntp_transactions", "name_resolution_responses", "portal_integrity_reports",
                    "portal_integrity_hops", "portal_integrity_dhcp_leases", "assets_statistics", "dot11_ssids",
                    "dot11_bssids", "dot11_channels", "dot11_channel_histograms", "dot11_bssid_clients",
                    "dot11_clients", "dot11_client_probereq_ssids", "dot11_disco_activity",
                    "dot11_disco_activity_receivers", "dot11_timeline_events", "bluetooth_devices", "uavs",
                    "uavs_timelines", "uavs_vectors", "tap_metrics_gauges", "tap_metrics_timers",
                    "node_metrics_gauges", "node_metrics_timers", "tap_captures_frequencies", "events",
                    "database_maintenance_log")
    ),

    HOT(
            "hot",
            "Small, constantly updated tables",
            "A handful of rows rewritten many times per minute. Half of every page is kept free and the table " +
                    "is vacuumed after a few changes.",
            5, 50, 10, 50, false,
            List.of("assets", "assets_hostnames", "assets_ip_addresses", "nodes", "taps", "tap_buses",
                    "tap_captures", "bus_channels", "health_indicators", "registry", "context_mac_addresses",
                    "database_usage_snapshots",
                    "context_mac_addresses_transparent", "auth_users", "dot11_known_networks", "monitors",
                    "beacons", "beacon_sources", "beacon_exclusions", "recon_scan_targets", "recon_scans",
                    "detection_alerts", "detection_alert_attributes", "detection_alert_timeline", "certificates",
                    "spoofing_dhcp_detection_state")
    );

    private final String key;
    private final String title;
    private final String description;
    private final int defaultVacuumScalePercent;
    private final int defaultVacuumThreshold;
    private final int defaultAnalyzeScalePercent;
    private final int defaultFillfactor;
    private final boolean disableVacuumCostDelay;
    private final List<String> tables;

    TableClass(String key,
               String title,
               String description,
               int defaultVacuumScalePercent,
               int defaultVacuumThreshold,
               int defaultAnalyzeScalePercent,
               int defaultFillfactor,
               boolean disableVacuumCostDelay,
               List<String> tables) {
        this.key = key;
        this.title = title;
        this.description = description;
        this.defaultVacuumScalePercent = defaultVacuumScalePercent;
        this.defaultVacuumThreshold = defaultVacuumThreshold;
        this.defaultAnalyzeScalePercent = defaultAnalyzeScalePercent;
        this.defaultFillfactor = defaultFillfactor;
        this.disableVacuumCostDelay = disableVacuumCostDelay;
        this.tables = tables;
    }

    public String getKey() {
        return key;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public int getDefaultVacuumScalePercent() {
        return defaultVacuumScalePercent;
    }

    public int getDefaultVacuumThreshold() {
        return defaultVacuumThreshold;
    }

    public int getDefaultAnalyzeScalePercent() {
        return defaultAnalyzeScalePercent;
    }

    public int getDefaultFillfactor() {
        return defaultFillfactor;
    }

    public boolean isDisableVacuumCostDelay() {
        return disableVacuumCostDelay;
    }

    public List<String> getTables() {
        return tables;
    }

    public static Optional<TableClass> ofTable(String tableName) {
        for (TableClass c : values()) {
            if (c.tables.contains(tableName)) {
                return Optional.of(c);
            }
        }

        return Optional.empty();
    }

}
