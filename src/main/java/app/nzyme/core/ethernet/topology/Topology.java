package app.nzyme.core.ethernet.topology;

import app.nzyme.core.database.OrderDirection;
import app.nzyme.core.ethernet.Ethernet;
import app.nzyme.core.ethernet.topology.TopologyNodeSelector.Side;
import app.nzyme.core.util.TimeRange;
import app.nzyme.core.util.filters.FilterSql;
import app.nzyme.core.util.filters.FilterSqlFragment;
import app.nzyme.core.util.filters.Filters;
import app.nzyme.plugin.Database;
import jakarta.annotation.Nullable;
import org.jdbi.v3.core.statement.Query;
import org.joda.time.DateTime;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

public class Topology {

    public enum Scope {
        ALL, INTERNAL, EXTERNAL, OUTBOUND, INBOUND
    }

    public enum InternalLevel {
        HOST, SUBNET, TAP
    }

    public enum ExternalLevel {
        HOST, NETWORK, COUNTRY
    }

    public enum ServiceLevel {
        PROTOCOL, PORT
    }

    public enum NodeLevel {
        HOST, SUBNET, TAP, NETWORK, COUNTRY
    }

    public enum RankingDimension {

        INTERNAL_HOST(true, InternalLevel.HOST, null, null),
        INTERNAL_SUBNET(true, InternalLevel.SUBNET, null, null),
        INTERNAL_TAP(true, InternalLevel.TAP, null, null),
        EXTERNAL_HOST(false, null, ExternalLevel.HOST, null),
        EXTERNAL_NETWORK(false, null, ExternalLevel.NETWORK, null),
        EXTERNAL_COUNTRY(false, null, ExternalLevel.COUNTRY, null),
        SERVICE_PROTOCOL(false, null, null, ServiceLevel.PROTOCOL),
        SERVICE_PORT(false, null, null, ServiceLevel.PORT);

        private final boolean internal;
        @Nullable
        private final InternalLevel internalLevel;
        @Nullable
        private final ExternalLevel externalLevel;
        @Nullable
        private final ServiceLevel serviceLevel;

        RankingDimension(boolean internal, @Nullable InternalLevel internalLevel,
                         @Nullable ExternalLevel externalLevel, @Nullable ServiceLevel serviceLevel) {
            this.internal = internal;
            this.internalLevel = internalLevel;
            this.externalLevel = externalLevel;
            this.serviceLevel = serviceLevel;
        }

        public boolean isService() {
            return serviceLevel != null;
        }

    }

    public enum RankingOrder {
        BYTES, SESSIONS, PEERS, FIRST_SEEN, LAST_SEEN
    }

    // Every node narrows to sessions that have it on either end. Every excluded node removes those sessions.
    public record Selection(TimeRange timeRange, Filters filters, List<UUID> taps, Scope scope,
                            List<TopologyNodeSelector> nodes, List<TopologyNodeSelector> excludedNodes) {}

    public record Node(String id, boolean internal, NodeLevel level, @Nullable String address,
                       @Nullable String network, @Nullable UUID tapUuid, @Nullable Integer asnNumber,
                       @Nullable String asnName, @Nullable String countryCode) {}

    public record Service(String id, @Nullable String l4Type, @Nullable Integer port, @Nullable String protocol) {}

    // Of the sessions open in the bucket, sessionsNew were not open in the bucket before it.
    public record SeriesPoint(int bucket, long sessions, long sessionsNew, long bytesTx, long bytesRx) {}

    // Bytes are seen from the source: TX is what the source sent, RX what it received.
    public record Link(String source, String target, @Nullable String service, long sessions, long sessionsTcp,
                       long bytesTx, long bytesRx, DateTime firstSeen, DateTime lastSeen, List<SeriesPoint> series) {}

    public record Links(List<Node> nodes, List<Service> services, List<Link> links, long totalLinks,
                        long totalSessions, long totalBytes, TopologyBuckets buckets) {}

    // Bytes are seen from the tap: TX is what the internal side sent, RX what it received.
    public record GeoFlow(UUID tapUuid, boolean outbound, @Nullable Double latitude, @Nullable Double longitude,
                          @Nullable String city, @Nullable String countryCode, long sessions, long bytesTx,
                          long bytesRx, DateTime firstSeen, DateTime lastSeen, List<SeriesPoint> series) {}

    public record GeoFlows(List<GeoFlow> flows, long totalFlows, long totalSessions, long totalBytes,
                           TopologyBuckets buckets) {}

    public record TimelineBucket(DateTime bucket, long sessionsInternal, long sessionsOutbound, long sessionsInbound,
                                 long sessionsExternal, long bytesInternal, long bytesOutbound, long bytesInbound,
                                 long bytesExternal) {}

    public record TimelineTotals(long sessionsInternal, long sessionsOutbound, long sessionsInbound,
                                 long sessionsExternal, long bytesInternal, long bytesOutbound, long bytesInbound,
                                 long bytesExternal) {}

    public record Timeline(List<TimelineBucket> buckets, TimelineTotals totals, TopologyBuckets configuration) {}

    public record Summary(long internalHosts, long externalHosts, long externalNetworks, long externalCountries) {}

    public record RankingRow(@Nullable Node node, @Nullable Service service, long sessions,
                             @Nullable Long sessionsInitiated, long bytesSent, long bytesReceived,
                             @Nullable Long peers, @Nullable Long clients, @Nullable Long servers,
                             DateTime firstSeen, DateTime lastSeen) {}

    public record Ranking(List<RankingRow> rows, long total) {}

    public record AddressAsset(String address, UUID uuid, String mac, @Nullable String hostname) {}

    private record Where(String sql, Map<String, Object> bindings, TimeRange timeRange) {}

    private static final String EXCLUSIONS = "AND NOT source_address_is_loopback " +
            "AND NOT destination_address_is_loopback AND NOT source_address_is_multicast " +
            "AND NOT destination_address_is_multicast AND destination_address <> '255.255.255.255'::inet " +
            "AND destination_mac IS DISTINCT FROM 'FF:FF:FF:FF:FF:FF' ";

    private static final String BYTES_TX = "COALESCE(bytes_tx_count, 0)";
    private static final String BYTES_RX = "COALESCE(bytes_rx_count, 0)";

    private static final String BUCKET = "floor((EXTRACT(EPOCH FROM most_recent_segment_time)::double precision " +
            "- :b_from) / :b_size)::integer";

    // The tags of a session without the marker tag, which is not a protocol. NULL for untagged sessions.
    private static final String V_TAGS = "CASE WHEN jsonb_typeof(tags) = 'array' " +
            "THEN NULLIF((tags - 'Unencrypted')::text, '[]') END";

    // Taps report the tags of a session in no fixed order.
    private static final String PROTOCOL_NAME = "(SELECT string_agg(t, '/' ORDER BY t) " +
            "FROM jsonb_array_elements_text(v_tags::jsonb) AS t)";

    private final Supplier<Database> database;

    public Topology(Ethernet ethernet) {
        this.database = () -> ethernet.getNzyme().getDatabase();
    }

    public Topology(Database database) {
        this.database = () -> database;
    }

    public Links links(Selection selection, InternalLevel internalLevel, ExternalLevel externalLevel,
                       @Nullable ServiceLevel serviceLevel, int limit) {
        Optional<Where> resolved = where(selection);
        if (resolved.isEmpty()) {
            return new Links(List.of(), List.of(), List.of(), 0, 0, 0,
                    TopologyBuckets.of(DateTime.now().minusHours(1), DateTime.now()));
        }

        Where where = resolved.get();
        TopologyBuckets buckets = TopologyBuckets.of(where.timeRange().from(), where.timeRange().to());

        // Grouped by tap, every tap is a node of its own and keeps what it recorded.
        boolean merged = merges(selection) && internalLevel != InternalLevel.TAP;
        String count = sessionCount(merged);

        String[] source = keyColumns(Side.SOURCE, internalLevel, externalLevel);
        String[] destination = keyColumns(Side.DESTINATION, internalLevel, externalLevel);

        String sql = "WITH base AS (" +
                "SELECT source_address_is_site_local AS s_local, " + keySelect(source, "s") + ", " +
                "destination_address_is_site_local AS d_local, " + keySelect(destination, "d") + ", " +
                serviceSelect(serviceLevel) + ", b, " +
                count + " AS sessions, SUM(b_new) AS sessions_new, " +
                "COALESCE(SUM(b_new) FILTER (WHERE l4_type = 'TCP'), 0) AS sessions_tcp, " +
                "SUM(b_tx) AS bytes_tx, SUM(b_rx) AS bytes_rx, " +
                "MIN(start_time) AS first_seen, MAX(most_recent_segment_time) AS last_seen, " +
                attributeSelect(Side.SOURCE, "s") + ", " + attributeSelect(Side.DESTINATION, "d") + " " +
                "FROM " + from(where, merged, serviceLevel != null, true) +
                "GROUP BY 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14" +
                "), ids AS (" +
                "SELECT " + nodeId("s", internalLevel, externalLevel) + " AS s_id, " +
                nodeId("d", internalLevel, externalLevel) + " AS d_id, " +
                serviceId(serviceLevel) + " AS v_id, " + serviceProtocol(serviceLevel) + " AS v_proto, " +
                "base.* FROM base" +
                "), norm AS (" +
                "SELECT s_id, d_id, v_id, v_proto, b, " +
                "SUM(sessions) AS sessions, SUM(sessions_new) AS sessions_new, SUM(sessions_tcp) AS sessions_tcp, " +
                "SUM(bytes_tx) AS bytes_tx, SUM(bytes_rx) AS bytes_rx, MIN(first_seen) AS first_seen, " +
                "MAX(last_seen) AS last_seen, " + attributeMerge("s") + ", " + attributeMerge("d") + " " +
                "FROM ids GROUP BY 1, 2, 3, 4, 5" +
                "), top AS (" +
                "SELECT s_id, d_id, v_id, COUNT(*) OVER () AS total_links, " +
                "SUM(SUM(sessions_new)) OVER () AS total_sessions, " +
                "SUM(SUM(bytes_tx + bytes_rx)) OVER () AS total_bytes " +
                "FROM norm GROUP BY s_id, d_id, v_id " +
                "ORDER BY SUM(bytes_tx + bytes_rx) DESC, SUM(sessions_new) DESC, s_id, d_id, v_id LIMIT :limit" +
                ") " +
                "SELECT n.*, t.total_links, t.total_sessions, t.total_bytes FROM norm AS n " +
                "JOIN top AS t ON t.s_id = n.s_id AND t.d_id = n.d_id AND t.v_id = n.v_id";

        Map<String, NodeAttributes> nodes = new LinkedHashMap<>();
        Map<String, ServiceAttributes> services = new LinkedHashMap<>();
        Map<String, LinkAccumulator> links = new LinkedHashMap<>();
        long[] totals = new long[3];

        database.get().useHandle(handle -> bind(handle.createQuery(sql), selection, where, buckets)
                .bind("limit", limit)
                .reduceResultSet(0, (rows, rs, ctx) -> {
                    String sourceId = rs.getString("s_id");
                    String targetId = rs.getString("d_id");
                    String serviceId = serviceLevel == null ? null : rs.getString("v_id");
                    long bytes = rs.getLong("bytes_tx") + rs.getLong("bytes_rx");

                    nodes.computeIfAbsent(sourceId, id -> new NodeAttributes()).merge(rs, "s");
                    nodes.computeIfAbsent(targetId, id -> new NodeAttributes()).merge(rs, "d");
                    if (serviceId != null) {
                        services.computeIfAbsent(serviceId, id -> new ServiceAttributes())
                                .merge(rs.getString("v_proto"), bytes + 1);
                    }

                    links.computeIfAbsent(sourceId + "|" + targetId + "|" + serviceId,
                                    key -> new LinkAccumulator(sourceId, targetId, serviceId))
                            .add(rs.getInt("b"), rs.getLong("sessions"), rs.getLong("sessions_new"),
                                    rs.getLong("sessions_tcp"), rs.getLong("bytes_tx"), rs.getLong("bytes_rx"),
                                    dateTime(rs.getTimestamp("first_seen")), dateTime(rs.getTimestamp("last_seen")));

                    totals[0] = rs.getLong("total_links");
                    totals[1] = rs.getLong("total_sessions");
                    totals[2] = rs.getLong("total_bytes");
                    return rows + 1;
                }));

        List<Link> result = new ArrayList<>();
        for (LinkAccumulator link : links.values()) {
            result.add(link.build());
        }
        result.sort(Comparator.comparingLong((Link l) -> l.bytesTx() + l.bytesRx()).reversed()
                .thenComparing(Link::source).thenComparing(Link::target));

        List<Node> nodeList = new ArrayList<>();
        for (Map.Entry<String, NodeAttributes> node : nodes.entrySet()) {
            nodeList.add(node.getValue().build(node.getKey()));
        }

        List<Service> serviceList = new ArrayList<>();
        for (Map.Entry<String, ServiceAttributes> service : services.entrySet()) {
            serviceList.add(service.getValue().build(service.getKey()));
        }

        return new Links(nodeList, serviceList, result, totals[0], totals[1], totals[2], buckets);
    }

    public GeoFlows geo(Selection selection, int limit) {
        Optional<Where> resolved = where(selection);
        if (resolved.isEmpty()) {
            return new GeoFlows(List.of(), 0, 0, 0, TopologyBuckets.of(DateTime.now().minusHours(1), DateTime.now()));
        }

        Where where = resolved.get();
        TopologyBuckets buckets = TopologyBuckets.of(where.timeRange().from(), where.timeRange().to());

        // Internal addresses carry no GeoIP data, so the side that has any is the external one.
        String latitude = "COALESCE(destination_address_geo_latitude, source_address_geo_latitude)";
        String longitude = "COALESCE(destination_address_geo_longitude, source_address_geo_longitude)";
        String country = "COALESCE(destination_address_geo_country_code, source_address_geo_country_code)::text";
        String city = "COALESCE(destination_address_geo_city, source_address_geo_city)";
        String located = latitude + " IS NOT NULL AND " + longitude + " IS NOT NULL";

        String sql = "WITH base AS (" +
                "SELECT tap_uuid, source_address_is_site_local AS outbound, " +
                "CASE WHEN " + located + " THEN round(" + latitude + " * 100)::integer END AS lat_h, " +
                "CASE WHEN " + located + " THEN round(" + longitude + " * 100)::integer END AS lon_h, " +
                "CASE WHEN " + located + " THEN NULL ELSE " + country + " END AS cc_key, " +
                "b, COUNT(*) AS sessions, SUM(b_new) AS sessions_new, SUM(b_tx) AS bytes_tx, " +
                "SUM(b_rx) AS bytes_rx, MIN(start_time) AS first_seen, " +
                "MAX(most_recent_segment_time) AS last_seen, MAX(" + city + ") AS city, " +
                "MAX(" + country + ") AS country_code " +
                "FROM " + from(where, false, false, true) +
                "AND source_address_is_site_local <> destination_address_is_site_local " +
                "GROUP BY 1, 2, 3, 4, 5, 6" +
                "), keyed AS (" +
                "SELECT *, COALESCE(lat_h::text || ',' || lon_h::text, 'cc:' || COALESCE(cc_key, '-')) AS loc_id " +
                "FROM base" +
                "), top AS (" +
                "SELECT tap_uuid, outbound, loc_id, COUNT(*) OVER () AS total_flows, " +
                "SUM(SUM(sessions_new)) OVER () AS total_sessions, " +
                "SUM(SUM(bytes_tx + bytes_rx)) OVER () AS total_bytes " +
                "FROM keyed GROUP BY tap_uuid, outbound, loc_id " +
                "ORDER BY SUM(bytes_tx + bytes_rx) DESC, SUM(sessions_new) DESC, loc_id, tap_uuid, outbound " +
                "LIMIT :limit" +
                ") " +
                "SELECT k.*, t.total_flows, t.total_sessions, t.total_bytes FROM keyed AS k " +
                "JOIN top AS t ON t.tap_uuid = k.tap_uuid AND t.outbound = k.outbound AND t.loc_id = k.loc_id";

        Map<String, GeoFlowAccumulator> flows = new LinkedHashMap<>();
        long[] totals = new long[3];

        database.get().useHandle(handle -> bind(handle.createQuery(sql), selection, where, buckets)
                .bind("limit", limit)
                .reduceResultSet(0, (rows, rs, ctx) -> {
                    UUID tapUuid = rs.getObject("tap_uuid", UUID.class);
                    boolean outbound = rs.getBoolean("outbound");

                    GeoFlowAccumulator flow = flows.get(tapUuid + "|" + outbound + "|" + rs.getString("loc_id"));
                    if (flow == null) {
                        Integer latH = (Integer) rs.getObject("lat_h");
                        Integer lonH = (Integer) rs.getObject("lon_h");
                        flow = new GeoFlowAccumulator(tapUuid, outbound,
                                latH == null ? null : latH / 100.0, lonH == null ? null : lonH / 100.0);
                        flows.put(tapUuid + "|" + outbound + "|" + rs.getString("loc_id"), flow);
                    }

                    // The session's TX is what its source sent. For inbound sessions the source is external.
                    long internalTx = outbound ? rs.getLong("bytes_tx") : rs.getLong("bytes_rx");
                    long internalRx = outbound ? rs.getLong("bytes_rx") : rs.getLong("bytes_tx");
                    flow.add(rs.getInt("b"), rs.getLong("sessions"), rs.getLong("sessions_new"), internalTx, internalRx,
                            dateTime(rs.getTimestamp("first_seen")), dateTime(rs.getTimestamp("last_seen")),
                            rs.getString("city"), rs.getString("country_code"));

                    totals[0] = rs.getLong("total_flows");
                    totals[1] = rs.getLong("total_sessions");
                    totals[2] = rs.getLong("total_bytes");
                    return rows + 1;
                }));

        List<GeoFlow> result = new ArrayList<>();
        for (GeoFlowAccumulator flow : flows.values()) {
            result.add(flow.build());
        }
        result.sort(Comparator.comparingLong((GeoFlow f) -> f.bytesTx() + f.bytesRx()).reversed());

        return new GeoFlows(result, totals[0], totals[1], totals[2], buckets);
    }

    public Timeline timeline(Selection selection) {
        Optional<Where> resolved = where(selection);
        if (resolved.isEmpty()) {
            return new Timeline(List.of(), new TimelineTotals(0, 0, 0, 0, 0, 0, 0, 0),
                    TopologyBuckets.of(DateTime.now().minusHours(1), DateTime.now()));
        }

        Where where = resolved.get();
        TopologyBuckets buckets = TopologyBuckets.of(where.timeRange().from(), where.timeRange().to());

        String internal = "source_address_is_site_local AND destination_address_is_site_local";
        String outbound = "source_address_is_site_local AND NOT destination_address_is_site_local";
        String inbound = "NOT source_address_is_site_local AND destination_address_is_site_local";
        String external = "NOT source_address_is_site_local AND NOT destination_address_is_site_local";
        String bytes = "SUM(b_tx + b_rx)";
        boolean merged = merges(selection);
        String count = sessionCount(merged);

        String sql = "SELECT b, " +
                "COALESCE(" + count + " FILTER (WHERE " + internal + "), 0) AS sessions_internal, " +
                "COALESCE(" + count + " FILTER (WHERE " + outbound + "), 0) AS sessions_outbound, " +
                "COALESCE(" + count + " FILTER (WHERE " + inbound + "), 0) AS sessions_inbound, " +
                "COALESCE(" + count + " FILTER (WHERE " + external + "), 0) AS sessions_external, " +
                "COALESCE(SUM(b_new) FILTER (WHERE " + internal + "), 0) AS new_internal, " +
                "COALESCE(SUM(b_new) FILTER (WHERE " + outbound + "), 0) AS new_outbound, " +
                "COALESCE(SUM(b_new) FILTER (WHERE " + inbound + "), 0) AS new_inbound, " +
                "COALESCE(SUM(b_new) FILTER (WHERE " + external + "), 0) AS new_external, " +
                "COALESCE(" + bytes + " FILTER (WHERE " + internal + "), 0) AS bytes_internal, " +
                "COALESCE(" + bytes + " FILTER (WHERE " + outbound + "), 0) AS bytes_outbound, " +
                "COALESCE(" + bytes + " FILTER (WHERE " + inbound + "), 0) AS bytes_inbound, " +
                "COALESCE(" + bytes + " FILTER (WHERE " + external + "), 0) AS bytes_external " +
                "FROM " + from(where, merged, false, true) + "GROUP BY 1";

        Map<Integer, TimelineBucket> found = new HashMap<>();
        long[] totals = new long[8];
        database.get().useHandle(handle -> bind(handle.createQuery(sql), selection, where, buckets)
                .reduceResultSet(0, (rows, rs, ctx) -> {
                    int index = rs.getInt("b");
                    TimelineBucket bucket = new TimelineBucket(buckets.bucketStart(index),
                            rs.getLong("sessions_internal"), rs.getLong("sessions_outbound"),
                            rs.getLong("sessions_inbound"), rs.getLong("sessions_external"),
                            rs.getLong("bytes_internal"), rs.getLong("bytes_outbound"),
                            rs.getLong("bytes_inbound"), rs.getLong("bytes_external"));
                    found.put(index, bucket);

                    totals[0] += rs.getLong("new_internal");
                    totals[1] += rs.getLong("new_outbound");
                    totals[2] += rs.getLong("new_inbound");
                    totals[3] += rs.getLong("new_external");
                    totals[4] += bucket.bytesInternal();
                    totals[5] += bucket.bytesOutbound();
                    totals[6] += bucket.bytesInbound();
                    totals[7] += bucket.bytesExternal();
                    return rows + 1;
                }));

        List<TimelineBucket> result = new ArrayList<>();
        for (int i = 0; i < buckets.count(); i++) {
            TimelineBucket bucket = found.get(i);
            result.add(bucket != null ? bucket
                    : new TimelineBucket(buckets.bucketStart(i), 0, 0, 0, 0, 0, 0, 0, 0));
        }

        return new Timeline(result, new TimelineTotals(totals[0], totals[1], totals[2], totals[3], totals[4],
                totals[5], totals[6], totals[7]), buckets);
    }

    public Summary summary(Selection selection) {
        Optional<Where> resolved = where(selection);
        if (resolved.isEmpty()) {
            return new Summary(0, 0, 0, 0);
        }

        Where where = resolved.get();

        String sql = "WITH endpoints AS (" +
                "SELECT source_address_is_site_local AS local, source_address AS address, " +
                "source_address_geo_asn_number AS asn, source_address_geo_country_code AS country " +
                "FROM l4_sessions WHERE " + where.sql() + "GROUP BY 1, 2, 3, 4 " +
                "UNION " +
                "SELECT destination_address_is_site_local, destination_address, " +
                "destination_address_geo_asn_number, destination_address_geo_country_code " +
                "FROM l4_sessions WHERE " + where.sql() + "GROUP BY 1, 2, 3, 4" +
                ") " +
                "SELECT COUNT(DISTINCT address) FILTER (WHERE local) AS internal_hosts, " +
                "COUNT(DISTINCT address) FILTER (WHERE NOT local) AS external_hosts, " +
                "COUNT(DISTINCT asn) FILTER (WHERE NOT local) AS external_networks, " +
                "COUNT(DISTINCT country) FILTER (WHERE NOT local) AS external_countries FROM endpoints";

        return database.get().withHandle(handle -> bind(handle.createQuery(sql), selection, where, null)
                .map((rs, ctx) -> new Summary(rs.getLong("internal_hosts"), rs.getLong("external_hosts"),
                        rs.getLong("external_networks"), rs.getLong("external_countries")))
                .one());
    }

    public Ranking ranking(Selection selection, RankingDimension dimension, RankingOrder order,
                           OrderDirection direction, int limit, int offset) {
        Optional<Where> resolved = where(selection);
        if (resolved.isEmpty()) {
            return new Ranking(List.of(), 0);
        }

        return dimension.isService()
                ? serviceRanking(selection, resolved.get(), dimension, order, direction, limit, offset)
                : nodeRanking(selection, resolved.get(), dimension, order, direction, limit, offset);
    }

    // A session between two endpoints of the same node, like two addresses of one network, counts once.
    private Ranking nodeRanking(Selection selection, Where where, RankingDimension dimension, RankingOrder order,
                                OrderDirection direction, int limit, int offset) {
        boolean merged = merges(selection) && dimension != RankingDimension.INTERNAL_TAP;
        String sessions = merged ? "session_count" : "1";
        String sessionRows = from(where, merged, false, false);

        InternalLevel internalLevel = dimension.internalLevel == null ? InternalLevel.HOST : dimension.internalLevel;
        ExternalLevel externalLevel = dimension.externalLevel == null ? ExternalLevel.HOST : dimension.externalLevel;

        String[] source = keyColumns(Side.SOURCE, internalLevel, externalLevel);
        String[] destination = keyColumns(Side.DESTINATION, internalLevel, externalLevel);
        String sourceMatches = (dimension.internal ? "" : "NOT ") + Side.SOURCE.local();
        String destinationMatches = (dimension.internal ? "" : "NOT ") + Side.DESTINATION.local();

        StringBuilder sameNode = new StringBuilder(sourceMatches);
        for (int i = 0; i < source.length; i++) {
            sameNode.append(" AND ").append(source[i]).append(" IS NOT DISTINCT FROM ").append(destination[i]);
        }

        String orderColumn = switch (order) {
            case SESSIONS -> "sessions";
            case PEERS -> "peers";
            case FIRST_SEEN -> "first_seen";
            case LAST_SEEN -> "last_seen";
            default -> "(bytes_sent + bytes_received)";
        };

        String sql = "WITH e AS (" +
                "SELECT " + keySelect(source, "k") + ", destination_address AS peer, " + sessions + " AS n, " +
                sessions + " AS initiated, " + BYTES_TX + " AS sent, " + BYTES_RX + " AS received, start_time, " +
                "most_recent_segment_time AS mrst, " + Side.SOURCE.asnNumber() + " AS asn, " +
                Side.SOURCE.asnName() + " AS asn_name, " + Side.SOURCE.countryCode() + " AS cc " +
                "FROM " + sessionRows + "AND " + sourceMatches + " " +
                "UNION ALL " +
                "SELECT " + keySelect(destination, "k") + ", source_address AS peer, " + sessions + " AS n, " +
                "0 AS initiated, " + BYTES_RX + " AS sent, " + BYTES_TX + " AS received, start_time, " +
                "most_recent_segment_time AS mrst, " + Side.DESTINATION.asnNumber() + " AS asn, " +
                Side.DESTINATION.asnName() + " AS asn_name, " + Side.DESTINATION.countryCode() + " AS cc " +
                "FROM " + sessionRows + "AND " + destinationMatches + " " +
                "AND NOT (" + sameNode + ")" +
                "), pairs AS (" +
                "SELECT k_a, k_n, k_c, k_t, peer, SUM(n) AS sessions, SUM(initiated) AS initiated, " +
                "SUM(sent) AS sent, SUM(received) AS received, MIN(start_time) AS first_seen, " +
                "MAX(mrst) AS last_seen, MAX(asn) AS asn, MAX(asn_name) AS asn_name, MIN(cc) AS cc_min, " +
                "MAX(cc) AS cc_max FROM e GROUP BY k_a, k_n, k_c, k_t, peer" +
                "), ranked AS (" +
                "SELECT " + (dimension.internal ? "TRUE" : "FALSE") + " AS k_local, k_a, k_n, k_c, k_t, " +
                "SUM(sessions) AS sessions, SUM(initiated) AS sessions_initiated, SUM(sent) AS bytes_sent, " +
                "SUM(received) AS bytes_received, COUNT(*) AS peers, MIN(first_seen) AS first_seen, " +
                "MAX(last_seen) AS last_seen, MAX(asn) AS k_asn, MAX(asn_name) AS k_asn_name, " +
                "MIN(cc_min) AS k_cc_min, MAX(cc_max) AS k_cc_max FROM pairs GROUP BY k_a, k_n, k_c, k_t" +
                ") " +
                "SELECT " + nodeId("k", internalLevel, externalLevel) + " AS k_id, ranked.*, " +
                "COUNT(*) OVER () AS total FROM ranked " +
                "ORDER BY " + orderColumn + " " + direction + ", (bytes_sent + bytes_received) DESC, 1 " +
                "LIMIT :limit OFFSET :offset";

        long[] total = new long[1];
        List<RankingRow> rows = database.get().withHandle(handle ->
                bind(handle.createQuery(sql), selection, where, null)
                        .bind("limit", limit)
                        .bind("offset", offset)
                        .map((rs, ctx) -> {
                            total[0] = rs.getLong("total");
                            NodeAttributes attributes = new NodeAttributes();
                            attributes.merge(rs, "k");
                            return new RankingRow(attributes.build(rs.getString("k_id")), null,
                                    rs.getLong("sessions"), rs.getLong("sessions_initiated"),
                                    rs.getLong("bytes_sent"), rs.getLong("bytes_received"), rs.getLong("peers"),
                                    null, null, dateTime(rs.getTimestamp("first_seen")),
                                    dateTime(rs.getTimestamp("last_seen")));
                        })
                        .list());

        return new Ranking(rows, rows.isEmpty() && offset > 0
                ? countRanking(selection, where, "SELECT 1 FROM (" +
                "SELECT " + keySelect(source, "k") + " FROM l4_sessions WHERE " + where.sql() +
                "AND " + sourceMatches + " UNION SELECT " + keySelect(destination, "k") +
                " FROM l4_sessions WHERE " + where.sql() + "AND " + destinationMatches + ") AS keys")
                : total[0]);
    }

    private Ranking serviceRanking(Selection selection, Where where, RankingDimension dimension, RankingOrder order,
                                   OrderDirection direction, int limit, int offset) {
        ServiceLevel level = dimension.serviceLevel;
        boolean merged = merges(selection);

        String orderColumn = switch (order) {
            case SESSIONS -> "sessions";
            case PEERS -> "clients";
            case FIRST_SEEN -> "first_seen";
            case LAST_SEEN -> "last_seen";
            default -> "(bytes_sent + bytes_received)";
        };

        String grouped = "SELECT " + serviceId(level) + " AS v_id, " + serviceProtocol(level) + " AS v_proto, " +
                "source_address, destination_address, sessions, bytes_tx, bytes_rx, first_seen, last_seen " +
                "FROM (" +
                "SELECT " + serviceSelect(level) + ", source_address, destination_address, " +
                sessionCount(merged) + " AS sessions, " +
                "SUM(" + BYTES_TX + ") AS bytes_tx, SUM(" + BYTES_RX + ") AS bytes_rx, " +
                "MIN(start_time) AS first_seen, MAX(most_recent_segment_time) AS last_seen " +
                "FROM " + from(where, merged, true, false) + "GROUP BY 1, 2, 3, 4, 5" +
                ") AS base";

        String sql = "WITH norm AS (" + grouped + "), ranked AS (" +
                "SELECT v_id, SUM(sessions) AS sessions, SUM(bytes_tx) AS bytes_sent, " +
                "SUM(bytes_rx) AS bytes_received, COUNT(DISTINCT source_address) AS clients, " +
                "COUNT(DISTINCT destination_address) AS servers, MIN(first_seen) AS first_seen, " +
                "MAX(last_seen) AS last_seen FROM norm GROUP BY v_id" +
                // A protocol names a port if it moved most of the bytes on it. A few stray tagged sessions do not.
                "), protocols AS (" +
                "SELECT DISTINCT ON (v_id) v_id, v_proto, bytes FROM (" +
                "SELECT v_id, v_proto, SUM(bytes_tx + bytes_rx) AS bytes FROM norm " +
                "WHERE v_proto IS NOT NULL GROUP BY 1, 2) AS named ORDER BY v_id, bytes DESC, v_proto" +
                ") " +
                "SELECT ranked.*, CASE WHEN protocols.bytes * 2 >= ranked.bytes_sent + ranked.bytes_received " +
                "THEN protocols.v_proto END AS v_proto, COUNT(*) OVER () AS total FROM ranked " +
                "LEFT JOIN protocols ON protocols.v_id = ranked.v_id " +
                "ORDER BY " + orderColumn + " " + direction + ", (bytes_sent + bytes_received) DESC, ranked.v_id " +
                "LIMIT :limit OFFSET :offset";

        long[] total = new long[1];
        List<RankingRow> rows = database.get().withHandle(handle ->
                bind(handle.createQuery(sql), selection, where, null)
                        .bind("limit", limit)
                        .bind("offset", offset)
                        .map((rs, ctx) -> {
                            total[0] = rs.getLong("total");
                            ServiceAttributes attributes = new ServiceAttributes();
                            attributes.merge(rs.getString("v_proto"), 1);
                            return new RankingRow(null, attributes.build(rs.getString("v_id")),
                                    rs.getLong("sessions"), null, rs.getLong("bytes_sent"),
                                    rs.getLong("bytes_received"), null, rs.getLong("clients"),
                                    rs.getLong("servers"), dateTime(rs.getTimestamp("first_seen")),
                                    dateTime(rs.getTimestamp("last_seen")));
                        })
                        .list());

        return new Ranking(rows, rows.isEmpty() && offset > 0
                ? countRanking(selection, where, "SELECT DISTINCT v_id FROM (" + grouped + ") AS keys")
                : total[0]);
    }

    // Only needed for a page past the last row, which carries no total itself.
    private long countRanking(Selection selection, Where where, String keys) {
        return database.get().withHandle(handle ->
                bind(handle.createQuery("SELECT COUNT(*) FROM (" + keys + ") AS counted"), selection, where, null)
                        .mapTo(Long.class)
                        .one());
    }

    // Assets are known per tenant, not per tap. An address that more than one asset used in the range gets none.
    public Map<String, AddressAsset> findAssetsOfAddresses(UUID organizationId, UUID tenantId, TimeRange timeRange,
                                                           Collection<String> addresses) {
        if (addresses.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, AddressAsset> result = new HashMap<>();
        database.get().useHandle(handle ->
                handle.createQuery("SELECT host(used.address) AS address, a.uuid, a.mac, " +
                                "(SELECT h.hostname FROM assets_hostnames AS h WHERE h.asset_id = a.id " +
                                "ORDER BY h.last_seen DESC LIMIT 1) AS hostname FROM (" +
                                "SELECT i.address, MIN(i.asset_id) AS asset_id " +
                                "FROM assets_ip_addresses AS i JOIN assets AS o ON o.id = i.asset_id " +
                                "WHERE o.organization_id = :organization_id AND o.tenant_id = :tenant_id " +
                                "AND i.address = ANY(string_to_array(:addresses, ',')::inet[]) " +
                                "AND i.first_seen <= :tr_to AND i.last_seen >= :tr_from " +
                                "GROUP BY i.address HAVING COUNT(DISTINCT i.asset_id) = 1" +
                                ") AS used JOIN assets AS a ON a.id = used.asset_id")
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
                        .bind("addresses", String.join(",", addresses))
                        .bind("tr_from", timeRange.from())
                        .bind("tr_to", timeRange.to())
                        .map((rs, ctx) -> new AddressAsset(rs.getString("address"),
                                rs.getObject("uuid", UUID.class), rs.getString("mac"), rs.getString("hostname")))
                        .forEach(asset -> result.put(asset.address(), asset)));

        return result;
    }

    private Optional<Where> where(Selection selection) {
        if (selection.taps().isEmpty()) {
            return Optional.empty();
        }

        TimeRange timeRange = selection.timeRange();
        if (timeRange.isAllTime()) {
            // An "all time" range spans from 1970 far into the future. The buckets need the range of the data.
            Optional<TimeRange> bounds = database.get().withHandle(handle ->
                    handle.createQuery("SELECT MIN(most_recent_segment_time) AS first, " +
                                    "MAX(most_recent_segment_time) AS last FROM l4_sessions " +
                                    "WHERE tap_uuid IN (<taps>)")
                            .bindList("taps", selection.taps())
                            .map((rs, ctx) -> rs.getTimestamp("first") == null
                                    ? Optional.<TimeRange>empty()
                                    : Optional.of(TimeRange.create(dateTime(rs.getTimestamp("first")),
                                    dateTime(rs.getTimestamp("last")), false)))
                            .one());
            if (bounds.isEmpty()) {
                return Optional.empty();
            }
            timeRange = bounds.get();
        }

        FilterSqlFragment filters = FilterSql.generate(selection.filters(), new TopologyFilters());
        Map<String, Object> bindings = new HashMap<>(filters.bindings());

        StringBuilder sql = new StringBuilder("tap_uuid IN (<taps>) AND most_recent_segment_time >= :tr_from " +
                "AND most_recent_segment_time <= :tr_to " + EXCLUSIONS);

        switch (selection.scope()) {
            case INTERNAL:
                sql.append("AND source_address_is_site_local AND destination_address_is_site_local ");
                break;
            case EXTERNAL:
                sql.append("AND NOT (source_address_is_site_local AND destination_address_is_site_local) ");
                break;
            case OUTBOUND:
                sql.append("AND source_address_is_site_local AND NOT destination_address_is_site_local ");
                break;
            case INBOUND:
                sql.append("AND NOT source_address_is_site_local AND destination_address_is_site_local ");
                break;
            default:
        }

        for (int i = 0; i < selection.nodes().size(); i++) {
            TopologyNodeSelector node = selection.nodes().get(i);
            String bind = "node_" + i;
            sql.append("AND ((").append(node.sql(Side.SOURCE, bind)).append(") OR (")
                    .append(node.sql(Side.DESTINATION, bind)).append(")) ");
            bindings.putAll(node.bindings(bind));
        }

        for (int i = 0; i < selection.excludedNodes().size(); i++) {
            TopologyNodeSelector node = selection.excludedNodes().get(i);
            String bind = "xnode_" + i;
            // Not "NOT": an endpoint without an autonomous system or country does not match a node, it is not unknown.
            sql.append("AND ((").append(node.sql(Side.SOURCE, bind)).append(") OR (")
                    .append(node.sql(Side.DESTINATION, bind)).append(")) IS NOT TRUE ");
            bindings.putAll(node.bindings(bind));
        }

        sql.append(filters.whereSql()).append(" ");

        bindings.put("tr_from", timeRange.from());
        bindings.put("tr_to", timeRange.to());

        return Optional.of(new Where(sql.toString(), bindings, timeRange));
    }

    private static Query bind(Query query, Selection selection, Where where, @Nullable TopologyBuckets buckets) {
        query.bindList("taps", selection.taps()).bindMap(where.bindings());

        if (buckets != null) {
            query.bind("b_from", buckets.fromEpochSeconds()).bind("b_size", buckets.sizeSeconds());
        }

        return query;
    }

    private static boolean merges(Selection selection) {
        return selection.taps().size() > 1;
    }

    private static String sessionCount(boolean merged) {
        return merged ? "SUM(session_count)" : "COUNT(*)";
    }

    // Ends in a condition, so callers can append more with AND.
    private static String from(Where where, boolean merged, boolean withTags, boolean bucketed) {
        String sessions;
        if (merged) {
            sessions = "(" + mergedSessions(where, withTags, bucketed) + ") AS l4_sessions WHERE TRUE ";
        } else if (bucketed) {
            sessions = "(" + spreadSessions(where) + ") AS l4_sessions WHERE TRUE ";
        } else {
            sessions = "l4_sessions WHERE " + where.sql();
        }

        return withTags ? "(SELECT *, " + V_TAGS + " AS v_tags FROM " + sessions + ") AS s " : sessions;
    }

    /*
     * Taps at different collection points record the same traffic under session keys of their own (the key includes
     * the start time a tap saw), so sessions cannot be matched across taps. Instead, per tap, the sessions between
     * two addresses on one port (and in one bucket, if bucketed) are summed up, and the tap that saw the most wins.
     * A row of the result stands for those sessions, with their number in session_count.
     */
    private static String mergedSessions(Where where, boolean withTags, boolean bucketed) {
        StringBuilder attributes = new StringBuilder();
        for (Side side : Side.values()) {
            attributes.append("BOOL_OR(").append(side.local()).append(") AS ").append(side.local()).append(", ")
                    .append("MAX(").append(side.asnNumber()).append(") AS ").append(side.asnNumber()).append(", ")
                    .append("MAX(").append(side.asnName()).append(") AS ").append(side.asnName()).append(", ")
                    .append("MAX(").append(side.countryCode()).append(") AS ").append(side.countryCode()).append(", ");
        }

        String sessions = bucketed
                ? "(" + spreadSessions(where) + ") AS l4_sessions "
                : "(SELECT *, 0 AS b, 1 AS b_new, " + BYTES_TX + " AS b_tx, " + BYTES_RX + " AS b_rx " +
                "FROM l4_sessions WHERE " + where.sql() + ") AS l4_sessions ";

        String perTap = "SELECT tap_uuid, source_address, destination_address, l4_type, destination_port, " +
                (withTags ? "tags" : "NULL::jsonb") + " AS tags, b, " +
                attributes + "COUNT(*) AS session_count, SUM(b_new) AS b_new, SUM(b_tx) AS bytes_tx_count, " +
                "SUM(b_rx) AS bytes_rx_count, MIN(start_time) AS start_time, " +
                "MAX(most_recent_segment_time) AS most_recent_segment_time " +
                "FROM " + sessions + "GROUP BY 1, 2, 3, 4, 5, 6, 7";

        if (withTags) {
            // A tap reports the same tags in different orders. Without this sum, the maximum below would keep only
            // one of the orders.
            perTap = "SELECT tap_uuid, source_address, destination_address, l4_type, destination_port, " +
                    "tags_sorted AS tags, b, " + attributes + "SUM(session_count) AS session_count, " +
                    "SUM(b_new) AS b_new, SUM(bytes_tx_count) AS bytes_tx_count, " +
                    "SUM(bytes_rx_count) AS bytes_rx_count, MIN(start_time) AS start_time, " +
                    "MAX(most_recent_segment_time) AS most_recent_segment_time " +
                    "FROM (SELECT per_tap.*, CASE WHEN jsonb_typeof(tags) = 'array' " +
                    "THEN (SELECT jsonb_agg(t ORDER BY t) FROM jsonb_array_elements_text(tags) AS t) END " +
                    "AS tags_sorted FROM (" + perTap + ") AS per_tap) AS sorted GROUP BY 1, 2, 3, 4, 5, 6, 7";
        }

        return "SELECT source_address, destination_address, l4_type, destination_port, tags, " +
                attributes + "MAX(session_count) AS session_count, MAX(bytes_tx_count) AS bytes_tx_count, " +
                "MAX(bytes_rx_count) AS bytes_rx_count, b, MAX(b_new) AS b_new, MAX(bytes_tx_count) AS b_tx, " +
                "MAX(bytes_rx_count) AS b_rx, MIN(start_time) AS start_time, " +
                "MAX(most_recent_segment_time) AS most_recent_segment_time " +
                "FROM (" + perTap + ") AS per_tap " +
                "GROUP BY source_address, destination_address, l4_type, destination_port, tags, b";
    }

    /*
     * A session that was open across several buckets gets a row in each of them, with its bytes split by how long it
     * was open in each. b_new is 1 in the first of those rows, so that sessions can still be counted once. A session
     * that started before the first bucket is treated as if it started with it.
     */
    private static String spreadSessions(Where where) {
        String opened = "GREATEST(start_time, to_timestamp(:b_from))";
        String bucketStart = "date_bin(make_interval(secs => :b_size), most_recent_segment_time, " +
                "to_timestamp(:b_from))";

        // Rounding the running total instead of each share keeps the sum of the shares equal to the total.
        String share = "(round(%1$s * done_after) - round(%1$s * done_before))::bigint";

        // The first branch is nearly all sessions. It must stay as cheap as a plain scan.
        return "SELECT l4_sessions.*, " + BUCKET + " AS b, 1 AS b_new, " + BYTES_TX + " AS b_tx, " +
                BYTES_RX + " AS b_rx " +
                "FROM l4_sessions WHERE " + where.sql() + "AND " + opened + " >= " + bucketStart + " " +
                "UNION ALL " +
                "SELECT l4_sessions.*, b, (b = first_b)::integer, " + String.format(share, BYTES_TX) + ", " +
                String.format(share, BYTES_RX) + " " +
                "FROM l4_sessions " +
                "CROSS JOIN LATERAL (SELECT " +
                "EXTRACT(EPOCH FROM most_recent_segment_time)::double precision AS open_to, " +
                "GREATEST(EXTRACT(EPOCH FROM start_time)::double precision, :b_from) AS open_from) AS life " +
                "CROSS JOIN LATERAL (SELECT floor((open_from - :b_from) / :b_size)::integer AS first_b) AS first " +
                "CROSS JOIN LATERAL generate_series(first_b, " + BUCKET + ") AS b " +
                "CROSS JOIN LATERAL (SELECT " +
                "GREATEST(0, (:b_from + b::double precision * :b_size - open_from) / (open_to - open_from)) " +
                "AS done_before, " +
                "LEAST(1, (:b_from + (b + 1)::double precision * :b_size - open_from) / (open_to - open_from)) " +
                "AS done_after) AS share " +
                "WHERE " + where.sql() + "AND " + opened + " < " + bucketStart + " ";
    }

    private static String[] keyColumns(Side side, InternalLevel internalLevel, ExternalLevel externalLevel) {
        String local = side.local();
        String address = side.address();

        String internalAddress = switch (internalLevel) {
            case HOST -> address;
            case SUBNET -> "network(set_masklen(" + address + ", CASE WHEN family(" + address + ") = 4 " +
                    "THEN 24 ELSE 64 END))::inet";
            default -> "NULL::inet";
        };
        String externalAddress = externalLevel == ExternalLevel.HOST ? address : "NULL::inet";

        String number = externalLevel == ExternalLevel.NETWORK
                ? "CASE WHEN NOT " + local + " THEN " + side.asnNumber() + " END" : "NULL::integer";

        String country = switch (externalLevel) {
            case NETWORK -> "CASE WHEN NOT " + local + " AND " + side.asnNumber() + " IS NULL THEN " +
                    side.countryCode() + "::text END";
            case COUNTRY -> "CASE WHEN NOT " + local + " THEN " + side.countryCode() + "::text END";
            default -> "NULL::text";
        };

        String tap = internalLevel == InternalLevel.TAP ? "CASE WHEN " + local + " THEN tap_uuid END" : "NULL::uuid";

        return new String[]{
                "CASE WHEN " + local + " THEN " + internalAddress + " ELSE " + externalAddress + " END",
                number, country, tap
        };
    }

    private static String keySelect(String[] columns, String prefix) {
        return columns[0] + " AS " + prefix + "_a, " + columns[1] + " AS " + prefix + "_n, " +
                columns[2] + " AS " + prefix + "_c, " + columns[3] + " AS " + prefix + "_t";
    }

    private static String nodeId(String prefix, InternalLevel internalLevel, ExternalLevel externalLevel) {
        String internal = switch (internalLevel) {
            case HOST -> "'host:' || host(" + prefix + "_a)";
            case SUBNET -> "'subnet:' || text(" + prefix + "_a)";
            case TAP -> "'tap:' || " + prefix + "_t::text";
        };

        String external = switch (externalLevel) {
            case HOST -> "'ext:' || host(" + prefix + "_a)";
            case NETWORK -> "CASE WHEN " + prefix + "_n IS NOT NULL THEN 'asn:' || " + prefix + "_n::text " +
                    "ELSE 'noasn:' || COALESCE(" + prefix + "_c, '" + TopologyNodeSelector.UNKNOWN + "') END";
            case COUNTRY -> "'cc:' || COALESCE(" + prefix + "_c, '" + TopologyNodeSelector.UNKNOWN + "')";
        };

        return "CASE WHEN " + prefix + "_local THEN " + internal + " ELSE " + external + " END";
    }

    private static String attributeSelect(Side side, String prefix) {
        return "MAX(" + side.asnNumber() + ") AS " + prefix + "_asn, " +
                "MAX(" + side.asnName() + ") AS " + prefix + "_asn_name, " +
                "MIN(" + side.countryCode() + ") AS " + prefix + "_cc_min, " +
                "MAX(" + side.countryCode() + ") AS " + prefix + "_cc_max";
    }

    private static String attributeMerge(String prefix) {
        return "MAX(" + prefix + "_asn) AS " + prefix + "_asn, " +
                "MAX(" + prefix + "_asn_name) AS " + prefix + "_asn_name, " +
                "MIN(" + prefix + "_cc_min) AS " + prefix + "_cc_min, " +
                "MAX(" + prefix + "_cc_max) AS " + prefix + "_cc_max";
    }

    // By protocol, tagged sessions are keyed by their tags alone. Untagged ones fall back to transport and port.
    private static String serviceSelect(@Nullable ServiceLevel level) {
        if (level == null) {
            return "NULL::text AS v_type, NULL::integer AS v_port, NULL::text AS v_tags";
        }

        if (level == ServiceLevel.PORT) {
            return "l4_type::text AS v_type, destination_port AS v_port, v_tags";
        }

        return "CASE WHEN v_tags IS NULL THEN l4_type::text END AS v_type, " +
                "CASE WHEN v_tags IS NULL THEN destination_port END AS v_port, v_tags";
    }

    private static String serviceId(@Nullable ServiceLevel level) {
        if (level == null) {
            return "''";
        }

        String port = "'port:' || v_type || '/' || v_port::text";
        return level == ServiceLevel.PORT
                ? port : "CASE WHEN v_tags IS NULL THEN " + port + " ELSE 'proto:' || " + PROTOCOL_NAME + " END";
    }

    private static String serviceProtocol(@Nullable ServiceLevel level) {
        return level == null ? "NULL::text" : "CASE WHEN v_tags IS NULL THEN NULL ELSE " + PROTOCOL_NAME + " END";
    }

    @Nullable
    private static DateTime dateTime(@Nullable Timestamp timestamp) {
        return timestamp == null ? null : new DateTime(timestamp);
    }

    private static class NodeAttributes {

        @Nullable
        private Integer asnNumber;
        @Nullable
        private String asnName;
        @Nullable
        private String countryMin;
        @Nullable
        private String countryMax;

        void merge(ResultSet rs, String prefix) throws SQLException {
            Integer number = (Integer) rs.getObject(prefix + "_asn");
            if (number != null && (asnNumber == null || number > asnNumber)) {
                asnNumber = number;
            }

            String name = rs.getString(prefix + "_asn_name");
            if (name != null && (asnName == null || name.compareTo(asnName) > 0)) {
                asnName = name;
            }

            String min = rs.getString(prefix + "_cc_min");
            if (min != null && (countryMin == null || min.compareTo(countryMin) < 0)) {
                countryMin = min;
            }

            String max = rs.getString(prefix + "_cc_max");
            if (max != null && (countryMax == null || max.compareTo(countryMax) > 0)) {
                countryMax = max;
            }
        }

        Node build(String id) {
            // The ID was built by the query from stored values. It is taken apart, not validated like one a client sent.
            int separator = id.indexOf(':');
            String value = id.substring(separator + 1);
            // An autonomous system usually spans countries. A country is only reported if all traffic agrees on it.
            String country = countryMin != null && countryMin.equals(countryMax) ? countryMin : null;

            switch (TopologyNodeSelector.Kind.ofPrefix(id.substring(0, separator))) {
                case HOST:
                    return new Node(id, true, NodeLevel.HOST, value, null, null, null, null, null);
                case SUBNET:
                    return new Node(id, true, NodeLevel.SUBNET, null, value, null, null, null, null);
                case TAP:
                    return new Node(id, true, NodeLevel.TAP, null, null, UUID.fromString(value), null, null, null);
                case EXTERNAL_HOST:
                    return new Node(id, false, NodeLevel.HOST, value, null, null, asnNumber, asnName, country);
                case ASN:
                    return new Node(id, false, NodeLevel.NETWORK, null, null, null, Integer.parseInt(value),
                            asnName, country);
                case NO_ASN:
                    return new Node(id, false, NodeLevel.NETWORK, null, null, null, null, null,
                            TopologyNodeSelector.UNKNOWN.equals(value) ? null : value);
                default:
                    return new Node(id, false, NodeLevel.COUNTRY, null, null, null, null, null,
                            TopologyNodeSelector.UNKNOWN.equals(value) ? null : value);
            }
        }

    }

    private static class ServiceAttributes {

        private final Map<String, Long> weightByProtocol = new HashMap<>();
        private long weight;

        void merge(@Nullable String protocol, long weight) {
            this.weight += weight;
            if (protocol != null && !protocol.isEmpty()) {
                weightByProtocol.merge(protocol, weight, Long::sum);
            }
        }

        Service build(String id) {
            String protocol = weightByProtocol.entrySet().stream()
                    .max(Map.Entry.<String, Long>comparingByValue().thenComparing(Map.Entry.comparingByKey()))
                    .filter(dominant -> dominant.getValue() * 2 >= weight)
                    .map(Map.Entry::getKey)
                    .orElse(null);

            if (id.startsWith("port:")) {
                String[] parts = id.substring("port:".length()).split("/");
                return new Service(id, parts[0], Integer.parseInt(parts[1]), protocol);
            }

            return new Service(id, null, null, id.substring("proto:".length()));
        }

    }

    private static class SeriesAccumulator {

        private final Map<Integer, long[]> points = new HashMap<>();
        private long sessions;
        private long bytesTx;
        private long bytesRx;
        @Nullable
        private DateTime firstSeen;
        @Nullable
        private DateTime lastSeen;

        void add(int bucket, long sessions, long sessionsNew, long bytesTx, long bytesRx, @Nullable DateTime first,
                 @Nullable DateTime last) {
            long[] point = points.computeIfAbsent(bucket, b -> new long[4]);
            point[0] += sessions;
            point[1] += sessionsNew;
            point[2] += bytesTx;
            point[3] += bytesRx;

            this.sessions += sessionsNew;
            this.bytesTx += bytesTx;
            this.bytesRx += bytesRx;

            if (first != null && (firstSeen == null || first.isBefore(firstSeen))) {
                firstSeen = first;
            }
            if (last != null && (lastSeen == null || last.isAfter(lastSeen))) {
                lastSeen = last;
            }
        }

        List<SeriesPoint> series() {
            List<SeriesPoint> series = new ArrayList<>();
            points.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(point ->
                    series.add(new SeriesPoint(point.getKey(), point.getValue()[0], point.getValue()[1],
                            point.getValue()[2], point.getValue()[3])));
            return series;
        }

    }

    private static class LinkAccumulator {

        private final String source;
        private final String target;
        @Nullable
        private final String service;
        private final SeriesAccumulator series = new SeriesAccumulator();
        private long sessionsTcp;

        LinkAccumulator(String source, String target, @Nullable String service) {
            this.source = source;
            this.target = target;
            this.service = service;
        }

        void add(int bucket, long sessions, long sessionsNew, long sessionsTcp, long bytesTx, long bytesRx,
                 @Nullable DateTime first, @Nullable DateTime last) {
            this.series.add(bucket, sessions, sessionsNew, bytesTx, bytesRx, first, last);
            this.sessionsTcp += sessionsTcp;
        }

        Link build() {
            return new Link(source, target, service, series.sessions, sessionsTcp, series.bytesTx, series.bytesRx,
                    series.firstSeen, series.lastSeen, series.series());
        }

    }

    private static class GeoFlowAccumulator {

        private final UUID tapUuid;
        private final boolean outbound;
        @Nullable
        private final Double latitude;
        @Nullable
        private final Double longitude;
        private final SeriesAccumulator series = new SeriesAccumulator();
        @Nullable
        private String city;
        @Nullable
        private String countryCode;

        GeoFlowAccumulator(UUID tapUuid, boolean outbound, @Nullable Double latitude, @Nullable Double longitude) {
            this.tapUuid = tapUuid;
            this.outbound = outbound;
            this.latitude = latitude;
            this.longitude = longitude;
        }

        void add(int bucket, long sessions, long sessionsNew, long bytesTx, long bytesRx, @Nullable DateTime first,
                 @Nullable DateTime last, @Nullable String city, @Nullable String countryCode) {
            this.series.add(bucket, sessions, sessionsNew, bytesTx, bytesRx, first, last);

            if (city != null && (this.city == null || city.compareTo(this.city) > 0)) {
                this.city = city;
            }
            if (countryCode != null && (this.countryCode == null || countryCode.compareTo(this.countryCode) > 0)) {
                this.countryCode = countryCode;
            }
        }

        GeoFlow build() {
            return new GeoFlow(tapUuid, outbound, latitude, longitude, city, countryCode, series.sessions,
                    series.bytesTx, series.bytesRx, series.firstSeen, series.lastSeen, series.series());
        }

    }

}
