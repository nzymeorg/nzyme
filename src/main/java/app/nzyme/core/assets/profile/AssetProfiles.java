package app.nzyme.core.assets.profile;

import app.nzyme.core.NzymeNode;
import app.nzyme.core.assets.db.AssetEntry;
import app.nzyme.core.detection.alerts.DetectionAlertService;
import app.nzyme.core.ethernet.CIDR;
import app.nzyme.core.ethernet.beacons.db.BeaconExclusionEntry;
import app.nzyme.core.ethernet.dhcp.DHCPFingerprintExclusionMatcher;
import app.nzyme.core.ethernet.dhcp.db.DHCPFingerprintExclusionEntry;
import app.nzyme.core.ethernet.dns.tunnels.db.DnsTunnelExclusionEntry;
import app.nzyme.core.ethernet.icmp.db.IcmpExclusionEntry;
import app.nzyme.core.ethernet.recon.db.ReconExclusionEntry;
import app.nzyme.core.ethernet.spoofing.db.SpoofingExclusionEntry;
import app.nzyme.core.util.TimeRange;
import app.nzyme.core.shared.db.GenericIntegerHistogramEntry;
import app.nzyme.core.util.Bucketing;
import app.nzyme.core.util.filters.FilterSql;
import app.nzyme.core.util.filters.FilterSqlFragment;
import app.nzyme.core.util.filters.Filters;
import app.nzyme.core.database.OrderDirection;
import app.nzyme.core.util.Tools;
import com.google.common.base.Strings;
import com.google.common.net.InetAddresses;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.statement.Query;
import org.joda.time.DateTime;
import org.joda.time.LocalDate;

import javax.annotation.Nullable;
import java.net.InetAddress;
import java.sql.Timestamp;
import java.util.*;

public class AssetProfiles {

    private static final Logger LOG = LogManager.getLogger(AssetProfiles.class);

    private final NzymeNode nzyme;

    public AssetProfiles(NzymeNode nzyme) {
        this.nzyme = nzyme;
    }

    public record ActivityCell(int dayOfWeek, int hour, long events, long bytes, long arpPackets, long dhcpTransactions,
                               long dnsQueries, long tcpSessions, long udpSessions) {}
    public record ActivityDay(LocalDate day, long events, long bytes, long arpPackets, long dhcpTransactions,
                              long dnsQueries, long tcpSessions, long udpSessions) {}
    public record AttributeEntry(String dimension, String value, long count, long bytes, DateTime firstSeen, DateTime lastSeen) {}
    public record CommunicationEdge(String direction, String l4Type, String peerType, String peerKey,
                                    @Nullable String peerMac, @Nullable String peerAddress,
                                    @Nullable Integer peerAsnNumber, @Nullable String peerAsnName,
                                    @Nullable String peerCountryCode, @Nullable String peerCity,
                                    @Nullable Double peerLatitude, @Nullable Double peerLongitude, int port,
                                    String protocol, long sessions, long bytesRx, long bytesTx, DateTime firstSeen,
                                    DateTime lastSeen) {}
    public record TimelineEvent(String type, DateTime timestamp, String value, @Nullable String detail) {}
    public record AlertSummary(String detectionType, long count, long activeCount, DateTime lastSeen,
                               List<String> attributeKeys) {}
    public record FindingSummary(String type, String role, long count, @Nullable DateTime lastSeen) {}
    public record TlsSummary(long sessions, Map<String, Long> versions, long selfSignedServers, long untrustedChains) {}
    public record ExclusionMatch(String type, UUID uuid, String name, boolean enabled, String matchedBy,
                                 long matchCount, @Nullable DateTime lastMatchedAt) {}

    public List<ActivityCell> activityHeatmap(UUID organizationId, UUID tenantId, String mac, int days,
                                              int timezoneOffsetMinutes) {
        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT EXTRACT(ISODOW FROM shifted)::int AS dow, " +
                                "EXTRACT(HOUR FROM shifted)::int AS hour, " +
                                "SUM(arp_packets + dhcp_transactions + dns_queries + tcp_sessions + udp_sessions) AS events, " +
                                "SUM(bytes_rx + bytes_tx) AS bytes, SUM(arp_packets) AS arp_packets, " +
                                "SUM(dhcp_transactions) AS dhcp_transactions, SUM(dns_queries) AS dns_queries, " +
                                "SUM(tcp_sessions) AS tcp_sessions, SUM(udp_sessions) AS udp_sessions " +
                                "FROM (SELECT (bucket AT TIME ZONE 'UTC') + make_interval(mins => :tz) AS shifted, * " +
                                "FROM assets_activity WHERE organization_id = :organization_id " +
                                "AND tenant_id = :tenant_id AND mac = :mac AND bucket >= :since) AS a " +
                                "GROUP BY 1, 2 ORDER BY 1, 2")
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
                        .bind("mac", mac)
                        .bind("since", DateTime.now().minusDays(days))
                        .bind("tz", timezoneOffsetMinutes)
                        .map((rs, ctx) -> new ActivityCell(rs.getInt("dow"), rs.getInt("hour"),
                                rs.getLong("events"), rs.getLong("bytes"), rs.getLong("arp_packets"),
                                rs.getLong("dhcp_transactions"), rs.getLong("dns_queries"),
                                rs.getLong("tcp_sessions"), rs.getLong("udp_sessions")))
                        .list()
        );
    }

    public List<ActivityDay> activityCalendar(UUID organizationId, UUID tenantId, String mac, int days,
                                              int timezoneOffsetMinutes) {
        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT ((bucket AT TIME ZONE 'UTC') + make_interval(mins => :tz))::date AS day, " +
                                "SUM(arp_packets + dhcp_transactions + dns_queries + tcp_sessions + udp_sessions) AS events, " +
                                "SUM(bytes_rx + bytes_tx) AS bytes, SUM(arp_packets) AS arp_packets, " +
                                "SUM(dhcp_transactions) AS dhcp_transactions, SUM(dns_queries) AS dns_queries, " +
                                "SUM(tcp_sessions) AS tcp_sessions, SUM(udp_sessions) AS udp_sessions " +
                                "FROM assets_activity WHERE organization_id = :organization_id " +
                                "AND tenant_id = :tenant_id AND mac = :mac AND bucket >= :since " +
                                "GROUP BY 1 ORDER BY 1")
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
                        .bind("mac", mac)
                        .bind("since", DateTime.now().minusDays(days))
                        .bind("tz", timezoneOffsetMinutes)
                        .map((rs, ctx) -> new ActivityDay(
                                LocalDate.parse(rs.getString("day")),
                                rs.getLong("events"), rs.getLong("bytes"), rs.getLong("arp_packets"),
                                rs.getLong("dhcp_transactions"), rs.getLong("dns_queries"),
                                rs.getLong("tcp_sessions"), rs.getLong("udp_sessions")))
                        .list()
        );
    }

    public List<AttributeEntry> attributes(UUID organizationId, UUID tenantId, String mac) {
        return attributes(organizationId, tenantId, mac, Set.of());
    }

    public List<AttributeEntry> attributes(UUID organizationId, UUID tenantId, String mac, Set<String> excludedDimensions) {
        String exclusion = excludedDimensions.isEmpty() ? "" : "AND dimension NOT IN (<excluded>) ";

        return nzyme.getDatabase().withHandle(handle -> {
            Query query = handle.createQuery("SELECT dimension, value, count, bytes, first_seen, last_seen FROM assets_attributes " +
                            "WHERE organization_id = :organization_id AND tenant_id = :tenant_id AND mac = :mac " +
                            exclusion + "ORDER BY dimension ASC, last_seen DESC")
                    .bind("organization_id", organizationId)
                    .bind("tenant_id", tenantId)
                    .bind("mac", mac);
            if (!excludedDimensions.isEmpty()) {
                query.bindList("excluded", new ArrayList<>(excludedDimensions));
            }
            return query.map((rs, ctx) -> new AttributeEntry(rs.getString("dimension"), rs.getString("value"),
                            rs.getLong("count"), rs.getLong("bytes"), dateTime(rs.getTimestamp("first_seen")),
                            dateTime(rs.getTimestamp("last_seen"))))
                    .list();
        });
    }

    public record InboundPortTotals(long ports, long sessions, long bytes, long maxSessions, long maxBytes) {}

    public enum InboundPortOrder {
        PORT, COUNT, BYTES, FIRST_SEEN, LAST_SEEN
    }

    public InboundPortTotals inboundPortTotals(UUID organizationId, UUID tenantId, String mac) {
        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT COUNT(*) AS ports, COALESCE(SUM(count), 0) AS sessions, " +
                                "COALESCE(SUM(bytes), 0) AS bytes, COALESCE(MAX(count), 0) AS max_sessions, " +
                                "COALESCE(MAX(bytes), 0) AS max_bytes FROM assets_attributes " +
                                "WHERE organization_id = :organization_id AND tenant_id = :tenant_id AND mac = :mac " +
                                "AND dimension = 'INBOUND_PORT'")
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
                        .bind("mac", mac)
                        .map((rs, ctx) -> new InboundPortTotals(rs.getLong("ports"), rs.getLong("sessions"),
                                rs.getLong("bytes"), rs.getLong("max_sessions"), rs.getLong("max_bytes")))
                        .one()
        );
    }

    // Values look like "TCP/22".
    public List<AttributeEntry> inboundPorts(UUID organizationId, UUID tenantId, String mac, InboundPortOrder order,
                                             OrderDirection direction, int limit, int offset) {
        String dir = direction == OrderDirection.ASC ? "ASC" : "DESC";
        String orderBy = switch (order) {
            case PORT -> "NULLIF(regexp_replace(split_part(value, '/', 2), '\\D', '', 'g'), '')::int " + dir + ", value " + dir;
            case BYTES -> "bytes " + dir + ", count DESC, value ASC";
            case FIRST_SEEN -> "first_seen " + dir + ", value ASC";
            case LAST_SEEN -> "last_seen " + dir + ", value ASC";
            default -> "count " + dir + ", bytes DESC, value ASC";
        };

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT dimension, value, count, bytes, first_seen, last_seen FROM assets_attributes " +
                                "WHERE organization_id = :organization_id AND tenant_id = :tenant_id AND mac = :mac " +
                                "AND dimension = 'INBOUND_PORT' ORDER BY " + orderBy + " LIMIT :limit OFFSET :offset")
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
                        .bind("mac", mac)
                        .bind("limit", limit)
                        .bind("offset", offset)
                        .map((rs, ctx) -> new AttributeEntry(rs.getString("dimension"), rs.getString("value"),
                                rs.getLong("count"), rs.getLong("bytes"), dateTime(rs.getTimestamp("first_seen")),
                                dateTime(rs.getTimestamp("last_seen"))))
                        .list()
        );
    }

    // Values look like "TCP/22 SSH".
    public List<AttributeEntry> inboundPortProtocols(UUID organizationId, UUID tenantId, String mac, List<String> ports) {
        if (ports.isEmpty()) {
            return List.of();
        }

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT dimension, value, count, bytes, first_seen, last_seen FROM assets_attributes " +
                                "WHERE organization_id = :organization_id AND tenant_id = :tenant_id AND mac = :mac " +
                                "AND dimension = 'INBOUND_PORT_PROTOCOL' AND split_part(value, ' ', 1) IN (<ports>) " +
                                "ORDER BY count DESC, value ASC")
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
                        .bind("mac", mac)
                        .bindList("ports", ports)
                        .map((rs, ctx) -> new AttributeEntry(rs.getString("dimension"), rs.getString("value"),
                                rs.getLong("count"), rs.getLong("bytes"), dateTime(rs.getTimestamp("first_seen")),
                                dateTime(rs.getTimestamp("last_seen"))))
                        .list()
        );
    }

    public long countAssetsUsingGateway(UUID organizationId, UUID tenantId, String gatewayMac) {
        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT COUNT(*) FROM assets_attributes WHERE organization_id = :organization_id " +
                                "AND tenant_id = :tenant_id AND dimension = 'GATEWAY_MAC' AND value = :mac")
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
                        .bind("mac", gatewayMac)
                        .mapTo(Long.class)
                        .one()
        );
    }

    public List<CommunicationEdge> communication(UUID organizationId, UUID tenantId, String mac, TimeRange timeRange,
                                                 int limit) {
        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT direction, l4_type, peer_type, peer_key, port, protocol, MAX(peer_mac) AS peer_mac, " +
                                "MAX(host(peer_address)) AS peer_address, MAX(peer_asn_number) AS peer_asn_number, " +
                                "MAX(peer_asn_name) AS peer_asn_name, MAX(peer_country_code) AS peer_country_code, " +
                                "MAX(peer_city) AS peer_city, MAX(peer_latitude) AS peer_latitude, " +
                                "MAX(peer_longitude) AS peer_longitude, " +
                                "SUM(sessions) AS sessions, SUM(bytes_rx) AS bytes_rx, SUM(bytes_tx) AS bytes_tx, " +
                                "MIN(first_seen) AS first_seen, MAX(last_seen) AS last_seen " +
                                "FROM assets_communication WHERE organization_id = :organization_id " +
                                "AND tenant_id = :tenant_id AND mac = :mac " +
                                "AND bucket >= :tr_from::date AND bucket <= :tr_to::date " +
                                "GROUP BY direction, l4_type, peer_type, peer_key, port, protocol " +
                                "ORDER BY SUM(bytes_rx + bytes_tx) DESC, SUM(sessions) DESC LIMIT :limit")
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
                        .bind("mac", mac)
                        .bind("tr_from", timeRange.from().toLocalDate().toString())
                        .bind("tr_to", timeRange.to().toLocalDate().toString())
                        .bind("limit", limit)
                        .map((rs, ctx) -> new CommunicationEdge(
                                rs.getString("direction"), rs.getString("l4_type"), rs.getString("peer_type"),
                                rs.getString("peer_key"), rs.getString("peer_mac"), rs.getString("peer_address"),
                                (Integer) rs.getObject("peer_asn_number"), rs.getString("peer_asn_name"),
                                rs.getString("peer_country_code"), rs.getString("peer_city"),
                                (Double) rs.getObject("peer_latitude"), (Double) rs.getObject("peer_longitude"),
                                rs.getInt("port"), rs.getString("protocol"),
                                rs.getLong("sessions"), rs.getLong("bytes_rx"),
                                rs.getLong("bytes_tx"), dateTime(rs.getTimestamp("first_seen")),
                                dateTime(rs.getTimestamp("last_seen"))))
                        .list()
        );
    }

    private static final String TIMELINE_UNION =
            "SELECT 'ASSET_FIRST_SEEN' AS type, a.first_seen AS ts, a.mac AS value, NULL AS detail " +
            "FROM assets AS a WHERE a.id = :asset_id " +
            "UNION ALL SELECT 'HOSTNAME', h.first_seen, h.hostname, h.source FROM assets_hostnames AS h " +
            "WHERE h.asset_id = :asset_id " +
            "UNION ALL SELECT 'IP_ADDRESS', i.first_seen, host(i.address), i.source FROM assets_ip_addresses AS i " +
            "WHERE i.asset_id = :asset_id " +
            "UNION ALL SELECT 'ATTRIBUTE', at.first_seen, at.value, at.dimension FROM assets_attributes AS at " +
            "WHERE at.organization_id = :organization_id AND at.tenant_id = :tenant_id AND at.mac = :mac " +
            "AND at.dimension <> 'INBOUND_PORT_PROTOCOL' " +
            "UNION ALL SELECT 'ALERT', da.created_at, da.detection_type, da.uuid::text FROM detection_alerts AS da " +
            "WHERE da.organization_id = :organization_id AND da.tenant_id = :tenant_id " +
            "AND EXISTS (SELECT 1 FROM detection_alert_attributes AS aa WHERE aa.detection_alert_id = da.id " +
            "AND aa.attribute_value = :mac) ";

    public enum TimelineOrder {
        TIMESTAMP, TYPE, VALUE
    }

    public long countTimeline(AssetEntry asset, TimeRange timeRange, Filters filters) {
        FilterSqlFragment filterFragment = FilterSql.generate(filters, new AssetTimelineFilters());

        return nzyme.getDatabase().withHandle(handle ->
                bindTimeline(handle.createQuery("SELECT COUNT(*) FROM (" + TIMELINE_UNION + ") AS t " +
                        "WHERE t.ts >= :tr_from AND t.ts <= :tr_to " + filterFragment.whereSql()),
                        asset, timeRange, filterFragment)
                        .mapTo(Long.class)
                        .one()
        );
    }

    public List<TimelineEvent> timeline(AssetEntry asset, TimeRange timeRange, Filters filters,
                                        TimelineOrder order, OrderDirection direction, int limit, int offset) {
        String dir = direction == OrderDirection.ASC ? "ASC" : "DESC";
        String orderBy = switch (order) {
            case TYPE -> "t.type " + dir + ", t.detail " + dir + ", t.ts DESC";
            case VALUE -> "t.value " + dir + ", t.ts DESC";
            default -> "t.ts " + dir;
        };

        FilterSqlFragment filterFragment = FilterSql.generate(filters, new AssetTimelineFilters());

        return nzyme.getDatabase().withHandle(handle ->
                bindTimeline(handle.createQuery("SELECT * FROM (" + TIMELINE_UNION + ") AS t " +
                        "WHERE t.ts >= :tr_from AND t.ts <= :tr_to " + filterFragment.whereSql() + " " +
                        "ORDER BY " + orderBy + " LIMIT :limit OFFSET :offset"), asset, timeRange, filterFragment)
                        .bind("limit", limit)
                        .bind("offset", offset)
                        .map((rs, ctx) -> new TimelineEvent(rs.getString("type"), dateTime(rs.getTimestamp("ts")),
                                rs.getString("value"), rs.getString("detail")))
                        .list()
        );
    }

    public List<GenericIntegerHistogramEntry> timelineHistogram(AssetEntry asset, TimeRange timeRange, Filters filters,
                                                               Bucketing.BucketingConfiguration bucketing) {
        FilterSqlFragment filterFragment = FilterSql.generate(filters, new AssetTimelineFilters());

        return nzyme.getDatabase().withHandle(handle ->
                bindTimeline(handle.createQuery("SELECT date_trunc(:date_trunc, t.ts) AS bucket, COUNT(*) AS value " +
                        "FROM (" + TIMELINE_UNION + ") AS t " +
                        "WHERE t.ts >= :tr_from AND t.ts <= :tr_to " + filterFragment.whereSql() + " " +
                        "GROUP BY bucket ORDER BY bucket DESC"), asset, timeRange, filterFragment)
                        .bind("date_trunc", bucketing.type().getDateTruncName())
                        .mapTo(GenericIntegerHistogramEntry.class)
                        .list()
        );
    }

    private Query bindTimeline(Query query, AssetEntry asset, TimeRange timeRange, FilterSqlFragment filterFragment) {
        return query.bind("asset_id", asset.id())
                .bind("organization_id", asset.organizationId())
                .bind("tenant_id", asset.tenantId())
                .bind("mac", asset.mac())
                .bind("tr_from", timeRange.from())
                .bind("tr_to", timeRange.to())
                .bindMap(filterFragment.bindings());
    }

    public List<AlertSummary> alerts(UUID organizationId, UUID tenantId, String mac) {
        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT da.detection_type, COUNT(DISTINCT da.id) AS count, " +
                                "COUNT(DISTINCT da.id) FILTER (WHERE NOT da.is_resolved " +
                                "AND da.last_seen > NOW() - make_interval(mins => :active_minutes)) AS active_count, " +
                                "MAX(da.last_seen) AS last_seen, " +
                                "STRING_AGG(DISTINCT aa.attribute_key, ',') AS keys " +
                                "FROM detection_alerts AS da " +
                                "JOIN detection_alert_attributes AS aa ON aa.detection_alert_id = da.id " +
                                "WHERE da.organization_id = :organization_id AND da.tenant_id = :tenant_id " +
                                "AND aa.attribute_value = :mac " +
                                "GROUP BY da.detection_type ORDER BY last_seen DESC")
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
                        .bind("mac", mac)
                        .bind("active_minutes", DetectionAlertService.ACTIVE_THRESHOLD_MINUTES)
                        .map((rs, ctx) -> new AlertSummary(rs.getString("detection_type"), rs.getLong("count"),
                                rs.getLong("active_count"), dateTime(rs.getTimestamp("last_seen")),
                                Arrays.asList(rs.getString("keys").split(","))))
                        .list()
        );
    }

    public List<FindingSummary> findings(UUID organizationId, UUID tenantId, String mac) {
        List<FindingSummary> result = new ArrayList<>();

        nzyme.getDatabase().useHandle(handle -> {
            finding(handle, result, "RECON_SCAN", "ACTOR",
                    "SELECT COUNT(*) AS c, MAX(last_seen) AS ls FROM recon_scans WHERE organization_id = :organization_id " +
                            "AND tenant_id = :tenant_id AND source_mac = :mac", organizationId, tenantId, mac);

            for (String classification : List.of("UNKNOWN", "BENIGN", "SUSPICIOUS", "MALICIOUS")) {
                finding(handle, result, "BEACON_" + classification, "ACTOR",
                        "SELECT COUNT(*) AS c, MAX(bs.last_seen) AS ls FROM beacon_sources AS bs " +
                                "JOIN beacons AS b ON b.id = bs.beacon_id WHERE b.organization_id = :organization_id " +
                                "AND b.tenant_id = :tenant_id AND bs.source_mac = :mac AND b.classification = '" +
                                classification + "'", organizationId, tenantId, mac);
            }

            finding(handle, result, "DNS_TUNNEL", "ACTOR",
                    "SELECT COUNT(*) AS c, MAX(last_seen) AS ls FROM dns_tunnel_findings " +
                            "WHERE organization_id = :organization_id AND tenant_id = :tenant_id AND client_mac = :mac",
                    organizationId, tenantId, mac);

            finding(handle, result, "ICMP_TUNNEL", "ACTOR",
                    "SELECT COUNT(*) AS c, MAX(last_seen) AS ls FROM icmp_tunnels " +
                            "WHERE organization_id = :organization_id AND tenant_id = :tenant_id AND source_mac = :mac",
                    organizationId, tenantId, mac);
            finding(handle, result, "ICMP_TUNNEL", "VICTIM",
                    "SELECT COUNT(*) AS c, MAX(last_seen) AS ls FROM icmp_tunnels " +
                            "WHERE organization_id = :organization_id AND tenant_id = :tenant_id AND destination_mac = :mac",
                    organizationId, tenantId, mac);

            finding(handle, result, "ICMP_TRACEROUTE", "ACTOR",
                    "SELECT COUNT(*) AS c, MAX(last_seen) AS ls FROM icmp_traceroutes " +
                            "WHERE organization_id = :organization_id AND tenant_id = :tenant_id AND source_mac = :mac",
                    organizationId, tenantId, mac);

            finding(handle, result, "ICMP_REDIRECT", "ACTOR",
                    "SELECT COUNT(*) AS c, MAX(last_seen) AS ls FROM icmp_redirects " +
                            "WHERE organization_id = :organization_id AND tenant_id = :tenant_id AND sender_mac = :mac",
                    organizationId, tenantId, mac);
            finding(handle, result, "ICMP_REDIRECT", "VICTIM",
                    "SELECT COUNT(*) AS c, MAX(last_seen) AS ls FROM icmp_redirects " +
                            "WHERE organization_id = :organization_id AND tenant_id = :tenant_id AND target_mac = :mac",
                    organizationId, tenantId, mac);

            finding(handle, result, "SPOOFING_ARP", "ACTOR",
                    "SELECT COUNT(*) AS c, MAX(last_seen) AS ls FROM spoofing_arp_findings " +
                            "WHERE organization_id = :organization_id AND tenant_id = :tenant_id AND spoofer_mac = :mac",
                    organizationId, tenantId, mac);
            finding(handle, result, "SPOOFING_ARP", "VICTIM",
                    "SELECT COUNT(*) AS c, MAX(f.last_seen) AS ls FROM spoofing_arp_findings AS f " +
                            "WHERE f.organization_id = :organization_id AND f.tenant_id = :tenant_id " +
                            "AND (f.owner_mac = :mac OR EXISTS (SELECT 1 FROM spoofing_arp_victims AS v " +
                            "WHERE v.finding_id = f.id AND v.mac = :mac))",
                    organizationId, tenantId, mac);

            finding(handle, result, "SPOOFING_DHCP", "ACTOR",
                    "SELECT COUNT(*) AS c, MAX(last_seen) AS ls FROM spoofing_dhcp_findings " +
                            "WHERE organization_id = :organization_id AND tenant_id = :tenant_id AND server_mac = :mac",
                    organizationId, tenantId, mac);
            finding(handle, result, "SPOOFING_DHCP", "VICTIM",
                    "SELECT COUNT(*) AS c, MAX(f.last_seen) AS ls FROM spoofing_dhcp_findings AS f " +
                            "WHERE f.organization_id = :organization_id AND f.tenant_id = :tenant_id " +
                            "AND EXISTS (SELECT 1 FROM spoofing_dhcp_victims AS v WHERE v.finding_id = f.id AND v.mac = :mac)",
                    organizationId, tenantId, mac);

            finding(handle, result, "SPOOFING_DNS", "ACTOR",
                    "SELECT COUNT(*) AS c, MAX(last_seen) AS ls FROM spoofing_dns_findings " +
                            "WHERE organization_id = :organization_id AND tenant_id = :tenant_id AND spoofer_mac = :mac",
                    organizationId, tenantId, mac);
            finding(handle, result, "SPOOFING_DNS", "VICTIM",
                    "SELECT COUNT(*) AS c, MAX(f.last_seen) AS ls FROM spoofing_dns_findings AS f " +
                            "WHERE f.organization_id = :organization_id AND f.tenant_id = :tenant_id " +
                            "AND (f.owner_mac = :mac OR EXISTS (SELECT 1 FROM spoofing_dns_victims AS v " +
                            "WHERE v.finding_id = f.id AND v.mac = :mac))",
                    organizationId, tenantId, mac);

            finding(handle, result, "SPOOFING_NAME_RESOLUTION", "ACTOR",
                    "SELECT COUNT(*) AS c, MAX(last_seen) AS ls FROM spoofing_nameres_findings " +
                            "WHERE organization_id = :organization_id AND tenant_id = :tenant_id AND responder_mac = :mac",
                    organizationId, tenantId, mac);
            finding(handle, result, "SPOOFING_NAME_RESOLUTION", "VICTIM",
                    "SELECT COUNT(*) AS c, MAX(f.last_seen) AS ls FROM spoofing_nameres_findings AS f " +
                            "WHERE f.organization_id = :organization_id AND f.tenant_id = :tenant_id " +
                            "AND EXISTS (SELECT 1 FROM spoofing_nameres_victims AS v WHERE v.finding_id = f.id AND v.mac = :mac)",
                    organizationId, tenantId, mac);
        });

        return result;
    }

    private void finding(Handle handle, List<FindingSummary> result, String type, String role, String sql,
                         UUID organizationId, UUID tenantId, String mac) {
        try {
            handle.createQuery(sql)
                    .bind("organization_id", organizationId)
                    .bind("tenant_id", tenantId)
                    .bind("mac", mac)
                    .map((rs, ctx) -> new FindingSummary(type, role, rs.getLong("c"), dateTime(rs.getTimestamp("ls"))))
                    .findOne()
                    .filter(f -> f.count() > 0)
                    .ifPresent(result::add);
        } catch (Exception e) {
            LOG.error("Could not query findings [{}/{}] of asset [{}].", type, role, mac, e);
        }
    }

    public TlsSummary tls(UUID organizationId, UUID tenantId, String mac, TimeRange timeRange) {
        List<UUID> taps = Tools.getTapUuids(nzyme, organizationId, tenantId);
        if (taps.isEmpty()) {
            return new TlsSummary(0, Map.of(), 0, 0);
        }

        return nzyme.getDatabase().withHandle(handle -> {
            Map<String, Long> versions = new LinkedHashMap<>();
            long[] totals = new long[3];

            handle.createQuery("SELECT COALESCE(version, 'Unknown') AS version, COUNT(*) AS c, " +
                            "COUNT(*) FILTER (WHERE server_leaf_self_signed) AS self_signed, " +
                            "COUNT(*) FILTER (WHERE chain_trusted = false) AS untrusted " +
                            "FROM tls_sessions WHERE source_mac = :mac AND tap_uuid IN (<taps>) " +
                            "AND established_at >= :tr_from AND established_at <= :tr_to " +
                            "GROUP BY version ORDER BY version")
                    .bind("mac", mac)
                    .bindList("taps", taps)
                    .bind("tr_from", timeRange.from())
                    .bind("tr_to", timeRange.to())
                    .map((rs, ctx) -> {
                        versions.put(rs.getString("version"), rs.getLong("c"));
                        totals[0] += rs.getLong("c");
                        totals[1] += rs.getLong("self_signed");
                        totals[2] += rs.getLong("untrusted");
                        return null;
                    })
                    .list();

            return new TlsSummary(totals[0], versions, totals[1], totals[2]);
        });
    }

    public List<ExclusionMatch> exclusions(UUID organizationId, UUID tenantId, String mac, List<String> ipAddresses) {
        List<ExclusionMatch> result = new ArrayList<>();
        String normalizedMac = mac.trim().toUpperCase();

        nzyme.getDatabase().useHandle(handle -> {
            for (DHCPFingerprintExclusionEntry e : handle.createQuery("SELECT * FROM dhcp_fingerprint_exclusions " +
                            "WHERE organization_id = :organization_id AND tenant_id = :tenant_id ORDER BY name ASC")
                    .bind("organization_id", organizationId).bind("tenant_id", tenantId)
                    .mapTo(DHCPFingerprintExclusionEntry.class).list()) {
                try {
                    if (DHCPFingerprintExclusionMatcher.create(e).matchesMac(normalizedMac)) {
                        result.add(new ExclusionMatch("DHCP_FINGERPRINT", e.uuid(), e.name(), e.enabled(),
                                "MAC address", e.matchCount(), e.lastMatchedAt()));
                    }
                } catch (Exception ex) {
                    LOG.debug("Skipping unparseable DHCP fingerprint exclusion [{}].", e.uuid(), ex);
                }
            }

            for (SpoofingExclusionEntry e : handle.createQuery("SELECT * FROM spoofing_exclusions " +
                            "WHERE organization_id = :organization_id AND tenant_id = :tenant_id ORDER BY name ASC")
                    .bind("organization_id", organizationId).bind("tenant_id", tenantId)
                    .mapTo(SpoofingExclusionEntry.class).list()) {
                String matchedBy = matchedBy(e.mac(), e.addressCidr(), normalizedMac, ipAddresses);
                if (matchedBy != null) {
                    result.add(new ExclusionMatch("SPOOFING", e.uuid(), e.name(), e.enabled(), matchedBy,
                            e.matchCount(), e.lastMatchedAt()));
                }
            }

            for (BeaconExclusionEntry e : handle.createQuery("SELECT * FROM beacon_exclusions " +
                            "WHERE organization_id = :organization_id AND tenant_id = :tenant_id ORDER BY name ASC")
                    .bind("organization_id", organizationId).bind("tenant_id", tenantId)
                    .mapTo(BeaconExclusionEntry.class).list()) {
                String matchedBy = matchedBy(e.sourceMac(), e.sourceCidr(), normalizedMac, ipAddresses);
                if (matchedBy != null) {
                    result.add(new ExclusionMatch("BEACON", e.uuid(), e.name(), e.enabled(), matchedBy,
                            e.matchCount(), e.lastMatchedAt()));
                }
            }

            for (IcmpExclusionEntry e : handle.createQuery("SELECT * FROM icmp_exclusions " +
                            "WHERE organization_id = :organization_id AND tenant_id = :tenant_id ORDER BY name ASC")
                    .bind("organization_id", organizationId).bind("tenant_id", tenantId)
                    .mapTo(IcmpExclusionEntry.class).list()) {
                String matchedBy = matchedBy(e.sourceMac(), e.sourceCidr(), normalizedMac, ipAddresses);
                if (matchedBy != null) {
                    result.add(new ExclusionMatch("ICMP", e.uuid(), e.name(), e.enabled(), matchedBy,
                            e.matchCount(), e.lastMatchedAt()));
                }
            }

            for (ReconExclusionEntry e : handle.createQuery("SELECT * FROM recon_exclusions " +
                            "WHERE organization_id = :organization_id AND tenant_id = :tenant_id ORDER BY name ASC")
                    .bind("organization_id", organizationId).bind("tenant_id", tenantId)
                    .mapTo(ReconExclusionEntry.class).list()) {
                String matchedBy = matchedBy(e.sourceMac(), e.sourceCidr(), normalizedMac, ipAddresses);
                if (matchedBy != null) {
                    result.add(new ExclusionMatch("RECON", e.uuid(), e.name(), e.enabled(), matchedBy,
                            e.matchCount(), e.lastMatchedAt()));
                }
            }

            for (DnsTunnelExclusionEntry e : handle.createQuery("SELECT * FROM dns_tunnel_exclusions " +
                            "WHERE organization_id = :organization_id AND tenant_id = :tenant_id ORDER BY name ASC")
                    .bind("organization_id", organizationId).bind("tenant_id", tenantId)
                    .mapTo(DnsTunnelExclusionEntry.class).list()) {
                String matchedBy = matchedBy(null, e.clientAddressCidr(), normalizedMac, ipAddresses);
                if (matchedBy != null) {
                    result.add(new ExclusionMatch("DNS_TUNNEL", e.uuid(), e.name(), e.enabled(), matchedBy,
                            e.matchCount(), e.lastMatchedAt()));
                }
            }
        });

        return result;
    }

    @Nullable
    private static String matchedBy(@Nullable String exclusionMac, @Nullable String exclusionCidr,
                                    String mac, List<String> ipAddresses) {
        if (!Strings.isNullOrEmpty(exclusionMac)
                && exclusionMac.trim().toUpperCase().replace("-", ":").equals(mac)) {
            return "MAC address";
        }

        if (!Strings.isNullOrEmpty(exclusionCidr)) {
            CIDR cidr;
            try {
                cidr = CIDR.parse(exclusionCidr.trim());
            } catch (IllegalArgumentException e) {
                return null;
            }

            for (String ip : ipAddresses) {
                if (cidrContains(cidr, ip)) {
                    return "IP address " + ip;
                }
            }
        }

        return null;
    }

    public static boolean cidrContains(CIDR cidr, @Nullable String address) {
        if (address == null || !InetAddresses.isInetAddress(address)) {
            return false;
        }

        InetAddress addr = InetAddresses.forString(address);
        byte[] a = addr.getAddress();
        byte[] n = cidr.address().getAddress();
        if (a.length != n.length) {
            return false;
        }

        int prefix = cidr.prefix();
        for (int i = 0; i < prefix; i++) {
            int mask = 1 << (7 - i % 8);
            if ((a[i / 8] & mask) != (n[i / 8] & mask)) {
                return false;
            }
        }

        return true;
    }

    @Nullable
    private static DateTime dateTime(@Nullable Timestamp ts) {
        return ts == null ? null : new DateTime(ts);
    }

}
