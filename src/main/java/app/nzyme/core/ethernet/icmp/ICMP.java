package app.nzyme.core.ethernet.icmp;

import app.nzyme.core.NzymeNode;
import app.nzyme.core.database.OrderDirection;
import app.nzyme.core.database.generic.TwoColumnHistogramOrderColumn;
import app.nzyme.core.database.generic.ThreeColumnWithKeyHistogramOrderColumn;
import app.nzyme.core.shared.db.GenericIntegerHistogramEntry;
import app.nzyme.core.ethernet.Ethernet;
import app.nzyme.core.ethernet.icmp.db.*;
import app.nzyme.core.util.Bucketing;
import app.nzyme.core.util.TimeRange;
import app.nzyme.core.util.filters.FilterSql;
import app.nzyme.core.util.filters.FilterSqlFragment;
import app.nzyme.core.util.filters.Filters;
import app.nzyme.plugin.RegistryKey;
import jakarta.annotation.Nullable;
import org.jdbi.v3.core.Handle;
import org.joda.time.DateTime;

import java.util.*;

public class ICMP {

    public enum OrderColumn {

        LAST_SEEN("last_seen"),
        FIRST_SEEN("first_seen"),
        SOURCE_ADDRESS("source_address"),
        DESTINATION_ADDRESS("destination_address"),
        ICMP_TYPE("icmp_type"),
        PACKET_COUNT("packet_count");

        private final String columnName;

        OrderColumn(String columnName) {
            this.columnName = columnName;
        }

        public String getColumnName() {
            return columnName;
        }
    }

    public enum RedirectOrderColumn {

        SENDER_ADDRESS("sender_address"),
        GATEWAY_ADDRESS("gateway_address"),
        TARGET_ADDRESS("target_address"),
        PACKET_COUNT("packet_count"),
        FIRST_SEEN("first_seen"),
        LAST_SEEN("last_seen");

        private final String columnName;

        RedirectOrderColumn(String columnName) {
            this.columnName = columnName;
        }

        public String getColumnName() {
            return columnName;
        }
    }

    public enum TracerouteOrderColumn {

        SOURCE_ADDRESS("source_address"),
        DESTINATION_ADDRESS("destination_address"),
        PROTOCOL("protocol"),
        ROUTER_COUNT("jsonb_array_length(routers)"),
        DESTINATION_REACHED("destination_reached"),
        PACKET_COUNT("packet_count"),
        FIRST_SEEN("first_seen"),
        LAST_SEEN("last_seen");

        private final String columnName;

        TracerouteOrderColumn(String columnName) {
            this.columnName = columnName;
        }

        public String getColumnName() {
            return columnName;
        }
    }

    public record TracerouteGroup(@Nullable String value, @Nullable String mac, long count) { }

    public record StatisticsBucket(DateTime bucket, long total, long echoRequests, long echoReplies,
                                   long unreachable, long timeExceeded, long redirects, long other) { }

    public record TopAddress(String address, @Nullable String mac, long packetCount) { }

    public record TypeCount(int type, long packetCount) { }

    private final NzymeNode nzyme;
    private final IcmpExclusions exclusions;

    public ICMP(Ethernet ethernet) {
        this.nzyme = ethernet.getNzyme();
        this.exclusions = new IcmpExclusions(nzyme);
    }

    public IcmpExclusions exclusions() {
        return exclusions;
    }

    public IcmpConfiguration getConfiguration(UUID organizationId, UUID tenantId) {
        return IcmpConfiguration.create(
                booleanValue(IcmpRegistryKeys.REDIRECT_DETECTION_ENABLED, organizationId, tenantId),
                booleanValue(IcmpRegistryKeys.TRACEROUTE_DETECTION_ENABLED, organizationId, tenantId),
                intValue(IcmpRegistryKeys.DETECTION_WINDOW_MINUTES, organizationId, tenantId),
                intValue(IcmpRegistryKeys.TRACEROUTE_MINIMUM_ROUTERS, organizationId, tenantId),
                intValue(IcmpRegistryKeys.DETECTION_RETENTION_TIME_DAYS, organizationId, tenantId),
                booleanValue(IcmpRegistryKeys.ALERT_ON_REDIRECT, organizationId, tenantId),
                booleanValue(IcmpRegistryKeys.ALERT_ON_TRACEROUTE, organizationId, tenantId),
                booleanValue(IcmpRegistryKeys.TUNNEL_DETECTION_ENABLED, organizationId, tenantId),
                intValue(IcmpRegistryKeys.TUNNEL_MINIMUM_PAYLOAD_BYTES, organizationId, tenantId),
                intValue(IcmpRegistryKeys.TUNNEL_MINIMUM_PACKETS, organizationId, tenantId),
                booleanValue(IcmpRegistryKeys.ALERT_ON_TUNNEL, organizationId, tenantId)
        );
    }

    public String stringValue(RegistryKey key, UUID organizationId, UUID tenantId) {
        return nzyme.getDatabaseCoreRegistry().getValue(key.key(), organizationId, tenantId)
                .orElse(key.defaultValue().orElseThrow());
    }

    public boolean booleanValue(RegistryKey key, UUID organizationId, UUID tenantId) {
        return Boolean.parseBoolean(stringValue(key, organizationId, tenantId));
    }

    public int intValue(RegistryKey key, UUID organizationId, UUID tenantId) {
        return Integer.parseInt(stringValue(key, organizationId, tenantId));
    }

    public long countAllPackets(TimeRange timeRange, Filters filters, List<UUID> taps) {
        if (taps.isEmpty()) {
            return 0;
        }

        FilterSqlFragment filterFragment = FilterSql.generate(filters, new IcmpFilters());

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT COUNT(*) FROM icmp_packets " +
                                "WHERE last_seen >= :tr_from AND last_seen <= :tr_to " +
                                "AND tap_uuid IN (<taps>) " + filterFragment.whereSql())
                        .bindList("taps", taps)
                        .bindMap(filterFragment.bindings())
                        .bind("tr_from", timeRange.from())
                        .bind("tr_to", timeRange.to())
                        .mapTo(Long.class)
                        .one()
        );
    }

    public List<IcmpPacketEntry> findAllPackets(TimeRange timeRange,
                                                Filters filters,
                                                int limit,
                                                int offset,
                                                OrderColumn orderColumn,
                                                OrderDirection orderDirection,
                                                List<UUID> taps) {
        if (taps.isEmpty()) {
            return Collections.emptyList();
        }

        FilterSqlFragment filterFragment = FilterSql.generate(filters, new IcmpFilters());

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT * FROM icmp_packets " +
                                "WHERE last_seen >= :tr_from AND last_seen <= :tr_to " +
                                "AND tap_uuid IN (<taps>) " + filterFragment.whereSql() + " " +
                                "ORDER BY <order_column> <order_direction>, id DESC LIMIT :limit OFFSET :offset")
                        .bindList("taps", taps)
                        .bindMap(filterFragment.bindings())
                        .bind("tr_from", timeRange.from())
                        .bind("tr_to", timeRange.to())
                        .bind("limit", limit)
                        .bind("offset", offset)
                        .define("order_column", orderColumn.getColumnName())
                        .define("order_direction", orderDirection)
                        .mapTo(IcmpPacketEntry.class)
                        .list()
        );
    }

    public List<StatisticsBucket> getStatistics(TimeRange timeRange,
                                                Bucketing.BucketingConfiguration bucketing,
                                                Filters filters,
                                                List<UUID> taps) {
        if (taps.isEmpty()) {
            return Collections.emptyList();
        }

        FilterSqlFragment filterFragment = FilterSql.generate(filters, new IcmpFilters());

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT date_trunc(:date_trunc, last_seen) AS bucket, " +
                                "SUM(packet_count) AS total, " +
                                "SUM(packet_count) FILTER (WHERE icmp_type = 8) AS echo_requests, " +
                                "SUM(packet_count) FILTER (WHERE icmp_type = 0) AS echo_replies, " +
                                "SUM(packet_count) FILTER (WHERE icmp_type = 3) AS unreachable, " +
                                "SUM(packet_count) FILTER (WHERE icmp_type = 11) AS time_exceeded, " +
                                "SUM(packet_count) FILTER (WHERE icmp_type = 5) AS redirects, " +
                                "SUM(packet_count) FILTER (WHERE icmp_type NOT IN (0, 3, 5, 8, 11)) AS other " +
                                "FROM icmp_packets WHERE last_seen >= :tr_from AND last_seen <= :tr_to " +
                                "AND tap_uuid IN (<taps>) " + filterFragment.whereSql() +
                                "GROUP BY bucket ORDER BY bucket DESC")
                        .bind("date_trunc", bucketing.type().getDateTruncName())
                        .bind("tr_from", timeRange.from())
                        .bind("tr_to", timeRange.to())
                        .bindMap(filterFragment.bindings())
                        .bindList("taps", taps)
                        .map((rs, ctx) -> new StatisticsBucket(
                                new DateTime(rs.getTimestamp("bucket")),
                                rs.getLong("total"),
                                rs.getLong("echo_requests"),
                                rs.getLong("echo_replies"),
                                rs.getLong("unreachable"),
                                rs.getLong("time_exceeded"),
                                rs.getLong("redirects"),
                                rs.getLong("other")
                        ))
                        .list()
        );
    }

    private static String addressColumn(String column) {
        if (!column.equals("source") && !column.equals("destination")) {
            throw new IllegalArgumentException("Unknown address column.");
        }
        return column;
    }

    public long countAddresses(String column, TimeRange timeRange, Filters filters, List<UUID> taps) {
        if (taps.isEmpty()) {
            return 0;
        }

        String col = addressColumn(column);
        FilterSqlFragment filterFragment = FilterSql.generate(filters, new IcmpFilters());

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT COUNT(DISTINCT <column>_address) FROM icmp_packets " +
                                "WHERE last_seen >= :tr_from AND last_seen <= :tr_to AND tap_uuid IN (<taps>) " +
                                filterFragment.whereSql())
                        .define("column", col)
                        .bind("tr_from", timeRange.from())
                        .bind("tr_to", timeRange.to())
                        .bindList("taps", taps)
                        .bindMap(filterFragment.bindings())
                        .mapTo(Long.class)
                        .one()
        );
    }

    public List<TopAddress> topAddresses(String column,
                                         TimeRange timeRange,
                                         Filters filters,
                                         List<UUID> taps,
                                         TwoColumnHistogramOrderColumn orderColumn,
                                         OrderDirection orderDirection,
                                         int limit,
                                         int offset) {
        if (taps.isEmpty()) {
            return Collections.emptyList();
        }

        String col = addressColumn(column);
        String order = orderColumn == TwoColumnHistogramOrderColumn.KEY ? "address" : "packet_count";
        FilterSqlFragment filterFragment = FilterSql.generate(filters, new IcmpFilters());

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT host(<column>_address) AS address, " +
                                "mode() WITHIN GROUP (ORDER BY <column>_mac) AS mac, " +
                                "SUM(packet_count) AS packet_count FROM icmp_packets " +
                                "WHERE last_seen >= :tr_from AND last_seen <= :tr_to AND tap_uuid IN (<taps>) " +
                                filterFragment.whereSql() +
                                "GROUP BY <column>_address ORDER BY <order_column> <order_direction> " +
                                "LIMIT :limit OFFSET :offset")
                        .define("column", col)
                        .define("order_column", order)
                        .define("order_direction", orderDirection)
                        .bind("tr_from", timeRange.from())
                        .bind("tr_to", timeRange.to())
                        .bindList("taps", taps)
                        .bindMap(filterFragment.bindings())
                        .bind("limit", limit)
                        .bind("offset", offset)
                        .map((rs, ctx) -> new TopAddress(rs.getString("address"), rs.getString("mac"), rs.getLong("packet_count")))
                        .list()
        );
    }

    public long countTypes(TimeRange timeRange, Filters filters, List<UUID> taps) {
        if (taps.isEmpty()) {
            return 0;
        }

        FilterSqlFragment filterFragment = FilterSql.generate(filters, new IcmpFilters());

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT COUNT(DISTINCT icmp_type) FROM icmp_packets " +
                                "WHERE last_seen >= :tr_from AND last_seen <= :tr_to AND tap_uuid IN (<taps>) " +
                                filterFragment.whereSql())
                        .bind("tr_from", timeRange.from())
                        .bind("tr_to", timeRange.to())
                        .bindList("taps", taps)
                        .bindMap(filterFragment.bindings())
                        .mapTo(Long.class)
                        .one()
        );
    }

    public List<TypeCount> typeCounts(TimeRange timeRange,
                                      Filters filters,
                                      List<UUID> taps,
                                      TwoColumnHistogramOrderColumn orderColumn,
                                      OrderDirection orderDirection,
                                      int limit,
                                      int offset) {
        if (taps.isEmpty()) {
            return Collections.emptyList();
        }

        String order = orderColumn == TwoColumnHistogramOrderColumn.KEY ? "icmp_type" : "packet_count";
        FilterSqlFragment filterFragment = FilterSql.generate(filters, new IcmpFilters());

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT icmp_type, SUM(packet_count) AS packet_count FROM icmp_packets " +
                                "WHERE last_seen >= :tr_from AND last_seen <= :tr_to AND tap_uuid IN (<taps>) " +
                                filterFragment.whereSql() + "GROUP BY icmp_type " +
                                "ORDER BY <order_column> <order_direction> LIMIT :limit OFFSET :offset")
                        .define("order_column", order)
                        .define("order_direction", orderDirection)
                        .bind("tr_from", timeRange.from())
                        .bind("tr_to", timeRange.to())
                        .bindList("taps", taps)
                        .bindMap(filterFragment.bindings())
                        .bind("limit", limit)
                        .bind("offset", offset)
                        .map((rs, ctx) -> new TypeCount(rs.getInt("icmp_type"), rs.getLong("packet_count")))
                        .list()
        );
    }

    public enum UnreachableOrderColumn {

        HOST("source_address"),
        SOURCE("destination_address"),
        PROTOCOL("quoted_protocol"),
        PORT("quoted_destination_port"),
        PACKET_COUNT("packet_count"),
        FIRST_SEEN("first_seen"),
        LAST_SEEN("last_seen");

        private final String columnName;

        UnreachableOrderColumn(String columnName) {
            this.columnName = columnName;
        }

        public String getColumnName() {
            return columnName;
        }
    }

    public record UnreachableRow(String hostAddress, @Nullable String hostMac, String sourceAddress,
                                 @Nullable String sourceMac, Integer protocol, Integer port, long packetCount,
                                 DateTime firstSeen, DateTime lastSeen) { }

    public record UnreachableAddressGroup(String address, @Nullable String mac, long packetCount) { }

    public record UnreachablePortGroup(Integer protocol, Integer port, long packetCount) { }

    public record UnreachableHostPortGroup(String address, @Nullable String mac, Integer port, long packetCount) { }

    private static final String UNREACHABLE_WHERE =
            "tap_uuid IN (<taps>) AND last_seen >= :tr_from AND last_seen <= :tr_to " +
            "AND icmp_type = 3 AND icmp_code = 3 AND quoted_destination_port IS NOT NULL ";

    public long countUnreachables(TimeRange timeRange, Filters filters, List<UUID> taps) {
        if (taps.isEmpty()) {
            return 0;
        }

        FilterSqlFragment filterFragment = FilterSql.generate(filters, new IcmpUnreachableFilters());
        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT COUNT(*) FROM (SELECT 1 FROM icmp_packets WHERE " + UNREACHABLE_WHERE +
                                filterFragment.whereSql() +
                                "GROUP BY source_address, destination_address, quoted_protocol, quoted_destination_port) x")
                        .bindList("taps", taps)
                        .bind("tr_from", timeRange.from())
                        .bind("tr_to", timeRange.to())
                        .bindMap(filterFragment.bindings())
                        .mapTo(Long.class)
                        .one()
        );
    }

    public List<UnreachableRow> findUnreachables(TimeRange timeRange,
                                                 Filters filters,
                                                 List<UUID> taps,
                                                 UnreachableOrderColumn orderColumn,
                                                 OrderDirection orderDirection,
                                                 int limit,
                                                 int offset) {
        if (taps.isEmpty()) {
            return Collections.emptyList();
        }

        FilterSqlFragment filterFragment = FilterSql.generate(filters, new IcmpUnreachableFilters());
        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT host(source_address) AS host_address, " +
                                "mode() WITHIN GROUP (ORDER BY source_mac) AS host_mac, " +
                                "host(destination_address) AS src_address, " +
                                "mode() WITHIN GROUP (ORDER BY destination_mac) AS src_mac, " +
                                "quoted_protocol AS protocol, quoted_destination_port AS port, " +
                                "SUM(packet_count) AS packet_count, MIN(first_seen) AS first_seen, " +
                                "MAX(last_seen) AS last_seen FROM icmp_packets WHERE " + UNREACHABLE_WHERE +
                                filterFragment.whereSql() +
                                "GROUP BY source_address, destination_address, quoted_protocol, quoted_destination_port " +
                                "ORDER BY <order_column> <order_direction> NULLS LAST, last_seen DESC " +
                                "LIMIT :limit OFFSET :offset")
                        .define("order_column", orderColumn.getColumnName())
                        .define("order_direction", orderDirection)
                        .bindList("taps", taps)
                        .bind("tr_from", timeRange.from())
                        .bind("tr_to", timeRange.to())
                        .bindMap(filterFragment.bindings())
                        .bind("limit", limit)
                        .bind("offset", offset)
                        .map((rs, ctx) -> new UnreachableRow(
                                rs.getString("host_address"),
                                rs.getString("host_mac"),
                                rs.getString("src_address"),
                                rs.getString("src_mac"),
                                (Integer) rs.getObject("protocol"),
                                (Integer) rs.getObject("port"),
                                rs.getLong("packet_count"),
                                new DateTime(rs.getTimestamp("first_seen")),
                                new DateTime(rs.getTimestamp("last_seen"))
                        ))
                        .list()
        );
    }

    public List<GenericIntegerHistogramEntry> unreachableHistogram(TimeRange timeRange,
                                                                   Filters filters,
                                                                   List<UUID> taps,
                                                                   Bucketing.BucketingConfiguration bucketing) {
        if (taps.isEmpty()) {
            return Collections.emptyList();
        }

        FilterSqlFragment filterFragment = FilterSql.generate(filters, new IcmpUnreachableFilters("p"));
        String step = "1 " + bucketing.type().getDateTruncName();

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT b.bucket AS bucket, COALESCE(SUM(p.packet_count), 0)::integer AS value " +
                                "FROM generate_series(date_trunc(:date_trunc, :tr_from::timestamptz), " +
                                ":tr_to::timestamptz, :step::interval) AS b(bucket) " +
                                "LEFT JOIN icmp_packets p ON p.tap_uuid IN (<taps>) " +
                                "AND p.last_seen >= b.bucket AND p.last_seen < b.bucket + :step::interval " +
                                "AND p.icmp_type = 3 AND p.icmp_code = 3 AND p.quoted_destination_port IS NOT NULL " +
                                filterFragment.whereSql() +
                                "GROUP BY b.bucket ORDER BY b.bucket DESC")
                        .bindList("taps", taps)
                        .bindMap(filterFragment.bindings())
                        .bind("date_trunc", bucketing.type().getDateTruncName())
                        .bind("step", step)
                        .bind("tr_from", timeRange.from())
                        .bind("tr_to", timeRange.to())
                        .mapTo(GenericIntegerHistogramEntry.class)
                        .list()
        );
    }

    private static String addressColumnStrict(String column) {
        if (!column.equals("source") && !column.equals("destination")) {
            throw new IllegalArgumentException("Unknown address column.");
        }
        return column;
    }

    public long countUnreachableAddressGroups(String column, TimeRange timeRange, Filters filters, List<UUID> taps) {
        if (taps.isEmpty()) {
            return 0;
        }

        String col = addressColumnStrict(column);
        FilterSqlFragment filterFragment = FilterSql.generate(filters, new IcmpUnreachableFilters());
        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT COUNT(DISTINCT <column>_address) FROM icmp_packets WHERE " +
                                UNREACHABLE_WHERE + filterFragment.whereSql())
                        .define("column", col)
                        .bindList("taps", taps)
                        .bind("tr_from", timeRange.from())
                        .bind("tr_to", timeRange.to())
                        .bindMap(filterFragment.bindings())
                        .mapTo(Long.class)
                        .one()
        );
    }

    public List<UnreachableAddressGroup> unreachableAddressGroups(String column,
                                                                  TimeRange timeRange,
                                                                  Filters filters,
                                                                  List<UUID> taps,
                                                                  TwoColumnHistogramOrderColumn orderColumn,
                                                                  OrderDirection orderDirection,
                                                                  int limit,
                                                                  int offset) {
        if (taps.isEmpty()) {
            return Collections.emptyList();
        }

        String col = addressColumnStrict(column);
        String order = orderColumn == TwoColumnHistogramOrderColumn.KEY ? "address" : "packet_count";
        FilterSqlFragment filterFragment = FilterSql.generate(filters, new IcmpUnreachableFilters());
        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT host(<column>_address) AS address, " +
                                "mode() WITHIN GROUP (ORDER BY <column>_mac) AS mac, " +
                                "SUM(packet_count) AS packet_count FROM icmp_packets WHERE " +
                                UNREACHABLE_WHERE + filterFragment.whereSql() +
                                "GROUP BY <column>_address ORDER BY <order_column> <order_direction> " +
                                "LIMIT :limit OFFSET :offset")
                        .define("column", col)
                        .define("order_column", order)
                        .define("order_direction", orderDirection)
                        .bindList("taps", taps)
                        .bind("tr_from", timeRange.from())
                        .bind("tr_to", timeRange.to())
                        .bindMap(filterFragment.bindings())
                        .bind("limit", limit)
                        .bind("offset", offset)
                        .map((rs, ctx) -> new UnreachableAddressGroup(rs.getString("address"), rs.getString("mac"), rs.getLong("packet_count")))
                        .list()
        );
    }

    public long countUnreachablePortGroups(TimeRange timeRange, Filters filters, List<UUID> taps) {
        if (taps.isEmpty()) {
            return 0;
        }

        FilterSqlFragment filterFragment = FilterSql.generate(filters, new IcmpUnreachableFilters());
        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT COUNT(*) FROM (SELECT 1 FROM icmp_packets WHERE " + UNREACHABLE_WHERE +
                                filterFragment.whereSql() + "GROUP BY quoted_protocol, quoted_destination_port) x")
                        .bindList("taps", taps)
                        .bind("tr_from", timeRange.from())
                        .bind("tr_to", timeRange.to())
                        .bindMap(filterFragment.bindings())
                        .mapTo(Long.class)
                        .one()
        );
    }

    public List<UnreachablePortGroup> unreachablePortGroups(TimeRange timeRange,
                                                            Filters filters,
                                                            List<UUID> taps,
                                                            TwoColumnHistogramOrderColumn orderColumn,
                                                            OrderDirection orderDirection,
                                                            int limit,
                                                            int offset) {
        if (taps.isEmpty()) {
            return Collections.emptyList();
        }

        String order = orderColumn == TwoColumnHistogramOrderColumn.KEY ? "port" : "packet_count";
        FilterSqlFragment filterFragment = FilterSql.generate(filters, new IcmpUnreachableFilters());
        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT quoted_protocol AS protocol, quoted_destination_port AS port, " +
                                "SUM(packet_count) AS packet_count FROM icmp_packets WHERE " + UNREACHABLE_WHERE +
                                filterFragment.whereSql() +
                                "GROUP BY quoted_protocol, quoted_destination_port " +
                                "ORDER BY <order_column> <order_direction> LIMIT :limit OFFSET :offset")
                        .define("order_column", order)
                        .define("order_direction", orderDirection)
                        .bindList("taps", taps)
                        .bind("tr_from", timeRange.from())
                        .bind("tr_to", timeRange.to())
                        .bindMap(filterFragment.bindings())
                        .bind("limit", limit)
                        .bind("offset", offset)
                        .map((rs, ctx) -> new UnreachablePortGroup((Integer) rs.getObject("protocol"), (Integer) rs.getObject("port"), rs.getLong("packet_count")))
                        .list()
        );
    }

    public long countUnreachableHostPortGroups(TimeRange timeRange, Filters filters, List<UUID> taps) {
        if (taps.isEmpty()) {
            return 0;
        }

        FilterSqlFragment filterFragment = FilterSql.generate(filters, new IcmpUnreachableFilters());
        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT COUNT(*) FROM (SELECT 1 FROM icmp_packets WHERE " + UNREACHABLE_WHERE +
                                filterFragment.whereSql() + "GROUP BY source_address, quoted_destination_port) x")
                        .bindList("taps", taps)
                        .bind("tr_from", timeRange.from())
                        .bind("tr_to", timeRange.to())
                        .bindMap(filterFragment.bindings())
                        .mapTo(Long.class)
                        .one()
        );
    }

    public List<UnreachableHostPortGroup> unreachableHostPortGroups(TimeRange timeRange,
                                                                    Filters filters,
                                                                    List<UUID> taps,
                                                                    ThreeColumnWithKeyHistogramOrderColumn orderColumn,
                                                                    OrderDirection orderDirection,
                                                                    int limit,
                                                                    int offset) {
        if (taps.isEmpty()) {
            return Collections.emptyList();
        }

        String order = switch (orderColumn) {
            case KEY -> "address";
            case VALUE1 -> "port";
            case VALUE2 -> "packet_count";
        };
        FilterSqlFragment filterFragment = FilterSql.generate(filters, new IcmpUnreachableFilters());
        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT host(source_address) AS address, " +
                                "mode() WITHIN GROUP (ORDER BY source_mac) AS mac, " +
                                "quoted_destination_port AS port, SUM(packet_count) AS packet_count " +
                                "FROM icmp_packets WHERE " + UNREACHABLE_WHERE + filterFragment.whereSql() +
                                "GROUP BY source_address, quoted_destination_port " +
                                "ORDER BY <order_column> <order_direction> LIMIT :limit OFFSET :offset")
                        .define("order_column", order)
                        .define("order_direction", orderDirection)
                        .bindList("taps", taps)
                        .bind("tr_from", timeRange.from())
                        .bind("tr_to", timeRange.to())
                        .bindMap(filterFragment.bindings())
                        .bind("limit", limit)
                        .bind("offset", offset)
                        .map((rs, ctx) -> new UnreachableHostPortGroup(rs.getString("address"), rs.getString("mac"), (Integer) rs.getObject("port"), rs.getLong("packet_count")))
                        .list()
        );
    }

    public enum UnreachableHostOrderColumn {

        SOURCE("destination_address"),
        UNREACHABLE_HOST("quoted_destination_address"),
        PROTOCOL("quoted_protocol"),
        REASON("icmp_code"),
        PACKET_COUNT("packet_count"),
        FIRST_SEEN("first_seen"),
        LAST_SEEN("last_seen");

        private final String columnName;

        UnreachableHostOrderColumn(String columnName) {
            this.columnName = columnName;
        }

        public String getColumnName() {
            return columnName;
        }
    }

    public record UnreachableHostEvent(String sourceAddress, @Nullable String sourceMac, String unreachableHost,
                                       Integer protocol, int code, String routerAddress, @Nullable String routerMac,
                                       long packetCount, DateTime firstSeen, DateTime lastSeen) { }

    private static final String UNREACHABLE_HOST_WHERE =
            "tap_uuid IN (<taps>) AND last_seen >= :tr_from AND last_seen <= :tr_to " +
            "AND icmp_type = 3 AND icmp_code IN (0, 1) AND quoted_destination_address IS NOT NULL ";

    public long countUnreachableHostEvents(TimeRange timeRange, Filters filters, List<UUID> taps) {
        if (taps.isEmpty()) {
            return 0;
        }

        FilterSqlFragment filterFragment = FilterSql.generate(filters, new IcmpUnreachableHostFilters());
        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT COUNT(*) FROM (SELECT 1 FROM icmp_packets WHERE " + UNREACHABLE_HOST_WHERE +
                                filterFragment.whereSql() +
                                "GROUP BY destination_address, quoted_destination_address, quoted_protocol, icmp_code) x")
                        .bindList("taps", taps)
                        .bind("tr_from", timeRange.from())
                        .bind("tr_to", timeRange.to())
                        .bindMap(filterFragment.bindings())
                        .mapTo(Long.class)
                        .one()
        );
    }

    public List<UnreachableHostEvent> findUnreachableHostEvents(TimeRange timeRange,
                                                                Filters filters,
                                                                List<UUID> taps,
                                                                UnreachableHostOrderColumn orderColumn,
                                                                OrderDirection orderDirection,
                                                                int limit,
                                                                int offset) {
        if (taps.isEmpty()) {
            return Collections.emptyList();
        }

        FilterSqlFragment filterFragment = FilterSql.generate(filters, new IcmpUnreachableHostFilters());
        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT host(destination_address) AS src_addr, " +
                                "mode() WITHIN GROUP (ORDER BY destination_mac) AS src_mac, " +
                                "host(quoted_destination_address) AS unreach_host, " +
                                "quoted_protocol AS protocol, icmp_code AS code, " +
                                "mode() WITHIN GROUP (ORDER BY host(source_address)) AS router_addr, " +
                                "mode() WITHIN GROUP (ORDER BY source_mac) AS router_mac, " +
                                "SUM(packet_count) AS packet_count, MIN(first_seen) AS first_seen, " +
                                "MAX(last_seen) AS last_seen FROM icmp_packets WHERE " + UNREACHABLE_HOST_WHERE +
                                filterFragment.whereSql() +
                                "GROUP BY destination_address, quoted_destination_address, quoted_protocol, icmp_code " +
                                "ORDER BY <order_column> <order_direction> NULLS LAST, last_seen DESC " +
                                "LIMIT :limit OFFSET :offset")
                        .define("order_column", orderColumn.getColumnName())
                        .define("order_direction", orderDirection)
                        .bindList("taps", taps)
                        .bind("tr_from", timeRange.from())
                        .bind("tr_to", timeRange.to())
                        .bindMap(filterFragment.bindings())
                        .bind("limit", limit)
                        .bind("offset", offset)
                        .map((rs, ctx) -> new UnreachableHostEvent(
                                rs.getString("src_addr"),
                                rs.getString("src_mac"),
                                rs.getString("unreach_host"),
                                (Integer) rs.getObject("protocol"),
                                rs.getInt("code"),
                                rs.getString("router_addr"),
                                rs.getString("router_mac"),
                                rs.getLong("packet_count"),
                                new DateTime(rs.getTimestamp("first_seen")),
                                new DateTime(rs.getTimestamp("last_seen"))
                        ))
                        .list()
        );
    }

    public List<GenericIntegerHistogramEntry> unreachableHostsHistogram(TimeRange timeRange,
                                                                        Filters filters,
                                                                        List<UUID> taps,
                                                                        Bucketing.BucketingConfiguration bucketing) {
        if (taps.isEmpty()) {
            return Collections.emptyList();
        }

        FilterSqlFragment filterFragment = FilterSql.generate(filters, new IcmpUnreachableHostFilters("p"));
        String step = "1 " + bucketing.type().getDateTruncName();

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT b.bucket AS bucket, COALESCE(SUM(p.packet_count), 0)::integer AS value " +
                                "FROM generate_series(date_trunc(:date_trunc, :tr_from::timestamptz), " +
                                ":tr_to::timestamptz, :step::interval) AS b(bucket) " +
                                "LEFT JOIN icmp_packets p ON p.tap_uuid IN (<taps>) " +
                                "AND p.last_seen >= b.bucket AND p.last_seen < b.bucket + :step::interval " +
                                "AND p.icmp_type = 3 AND p.icmp_code IN (0, 1) AND p.quoted_destination_address IS NOT NULL " +
                                filterFragment.whereSql() +
                                "GROUP BY b.bucket ORDER BY b.bucket DESC")
                        .bindList("taps", taps)
                        .bindMap(filterFragment.bindings())
                        .bind("date_trunc", bucketing.type().getDateTruncName())
                        .bind("step", step)
                        .bind("tr_from", timeRange.from())
                        .bind("tr_to", timeRange.to())
                        .mapTo(GenericIntegerHistogramEntry.class)
                        .list()
        );
    }

    private record UnreachableHostBreakdownColumn(String valueExpr, String macExpr, String groupCol) { }

    private static UnreachableHostBreakdownColumn unreachableHostBreakdownColumn(String by) {
        switch (by == null ? "" : by.toUpperCase()) {
            case "UNREACHABLE_HOST":
                return new UnreachableHostBreakdownColumn("host(quoted_destination_address)", "NULL", "quoted_destination_address");
            case "SOURCE":
                return new UnreachableHostBreakdownColumn("host(destination_address)",
                        "mode() WITHIN GROUP (ORDER BY destination_mac)", "destination_address");
            case "ROUTER":
                return new UnreachableHostBreakdownColumn("host(source_address)",
                        "mode() WITHIN GROUP (ORDER BY source_mac)", "source_address");
            case "PROTOCOL":
                return new UnreachableHostBreakdownColumn("quoted_protocol::text", "NULL", "quoted_protocol");
            default:
                throw new IllegalArgumentException("Unknown unreachable host breakdown column [" + by + "].");
        }
    }

    public long countUnreachableHostBreakdown(String by, TimeRange timeRange, Filters filters, List<UUID> taps) {
        if (taps.isEmpty()) {
            return 0;
        }

        UnreachableHostBreakdownColumn col = unreachableHostBreakdownColumn(by);
        FilterSqlFragment filterFragment = FilterSql.generate(filters, new IcmpUnreachableHostFilters());
        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT COUNT(*) FROM (SELECT 1 FROM icmp_packets WHERE " + UNREACHABLE_HOST_WHERE +
                                filterFragment.whereSql() + "GROUP BY <group_col>) x")
                        .define("group_col", col.groupCol())
                        .bindList("taps", taps)
                        .bind("tr_from", timeRange.from())
                        .bind("tr_to", timeRange.to())
                        .bindMap(filterFragment.bindings())
                        .mapTo(Long.class)
                        .one()
        );
    }

    public List<UnreachableAddressGroup> unreachableHostBreakdown(String by,
                                                                  TimeRange timeRange,
                                                                  Filters filters,
                                                                  List<UUID> taps,
                                                                  TwoColumnHistogramOrderColumn orderColumn,
                                                                  OrderDirection orderDirection,
                                                                  int limit,
                                                                  int offset) {
        if (taps.isEmpty()) {
            return Collections.emptyList();
        }

        UnreachableHostBreakdownColumn col = unreachableHostBreakdownColumn(by);
        String order = orderColumn == TwoColumnHistogramOrderColumn.KEY ? "value" : "packet_count";
        FilterSqlFragment filterFragment = FilterSql.generate(filters, new IcmpUnreachableHostFilters());
        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT <value_expr> AS value, <mac_expr> AS mac, SUM(packet_count) AS packet_count " +
                                "FROM icmp_packets WHERE " + UNREACHABLE_HOST_WHERE + filterFragment.whereSql() +
                                "GROUP BY <group_col> ORDER BY <order_column> <order_direction> " +
                                "LIMIT :limit OFFSET :offset")
                        .define("value_expr", col.valueExpr())
                        .define("mac_expr", col.macExpr())
                        .define("group_col", col.groupCol())
                        .define("order_column", order)
                        .define("order_direction", orderDirection)
                        .bindList("taps", taps)
                        .bind("tr_from", timeRange.from())
                        .bind("tr_to", timeRange.to())
                        .bindMap(filterFragment.bindings())
                        .bind("limit", limit)
                        .bind("offset", offset)
                        .map((rs, ctx) -> new UnreachableAddressGroup(rs.getString("value"), rs.getString("mac"), rs.getLong("packet_count")))
                        .list()
        );
    }

    private static String timeRangeCondition(String tableAlias) {
        String p = tableAlias == null || tableAlias.isEmpty() ? "" : tableAlias + ".";
        return "AND " + p + "first_seen <= :tr_to AND " + p + "last_seen >= :tr_from ";
    }

    private static String tapsCondition(@Nullable List<UUID> taps, String tableAlias) {
        if (taps == null) {
            return "";
        }

        if (taps.isEmpty()) {
            return "AND false ";
        }

        String column = tableAlias == null || tableAlias.isEmpty() ? "tap_uuids" : tableAlias + ".tap_uuids";
        return "AND EXISTS (SELECT 1 FROM jsonb_array_elements_text(" + column + ") AS tap WHERE tap IN (<taps>)) ";
    }

    private static <T extends org.jdbi.v3.core.statement.SqlStatement<T>> T bindTaps(T query, @Nullable List<UUID> taps) {
        if (taps != null && !taps.isEmpty()) {
            query.bindList("taps", taps.stream().map(UUID::toString).toList());
        }

        return query;
    }

    public long countRedirects(Filters filters, @Nullable List<UUID> taps, TimeRange timeRange, UUID organizationId, UUID tenantId) {
        FilterSqlFragment filterFragment = FilterSql.generate(filters, new IcmpRedirectFilters());
        return nzyme.getDatabase().withHandle(handle ->
                bindTaps(handle.createQuery("SELECT COUNT(*) FROM icmp_redirects WHERE organization_id = :organization_id " +
                                "AND tenant_id = :tenant_id " + timeRangeCondition("") + tapsCondition(taps, null) + filterFragment.whereSql()), taps)
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
                        .bind("tr_from", timeRange.from())
                        .bind("tr_to", timeRange.to())
                        .bindMap(filterFragment.bindings())
                        .mapTo(Long.class)
                        .one()
        );
    }

    public List<IcmpRedirectEntry> findAllRedirects(Filters filters,
                                                    @Nullable List<UUID> taps,
                                                    TimeRange timeRange,
                                                    RedirectOrderColumn orderColumn,
                                                    OrderDirection orderDirection,
                                                    int limit,
                                                    int offset,
                                                    UUID organizationId,
                                                    UUID tenantId) {
        FilterSqlFragment filterFragment = FilterSql.generate(filters, new IcmpRedirectFilters());
        return nzyme.getDatabase().withHandle(handle ->
                bindTaps(handle.createQuery("SELECT * FROM icmp_redirects WHERE organization_id = :organization_id " +
                                "AND tenant_id = :tenant_id " + timeRangeCondition("") + tapsCondition(taps, null) + filterFragment.whereSql() +
                                "ORDER BY <order_column> <order_direction>, id DESC " +
                                "LIMIT :limit OFFSET :offset"), taps)
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
                        .bind("tr_from", timeRange.from())
                        .bind("tr_to", timeRange.to())
                        .bindMap(filterFragment.bindings())
                        .bind("limit", limit)
                        .bind("offset", offset)
                        .define("order_column", orderColumn.getColumnName())
                        .define("order_direction", orderDirection)
                        .mapTo(IcmpRedirectEntry.class)
                        .list()
        );
    }

    public Optional<IcmpRedirectEntry> findRedirect(UUID uuid, UUID organizationId, UUID tenantId) {
        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT * FROM icmp_redirects WHERE uuid = :uuid " +
                                "AND organization_id = :organization_id AND tenant_id = :tenant_id")
                        .bind("uuid", uuid)
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
                        .mapTo(IcmpRedirectEntry.class)
                        .findOne()
        );
    }

    public Optional<IcmpRedirectEntry> findRedirectByIdentity(Handle handle,
                                                              UUID organizationId,
                                                              UUID tenantId,
                                                              String senderAddress,
                                                              String gatewayAddress,
                                                              String targetAddress) {
        return handle.createQuery("SELECT * FROM icmp_redirects WHERE organization_id = :organization_id " +
                        "AND tenant_id = :tenant_id AND sender_address = :sender_address::inet " +
                        "AND gateway_address = :gateway_address::inet AND target_address = :target_address::inet")
                .bind("organization_id", organizationId)
                .bind("tenant_id", tenantId)
                .bind("sender_address", senderAddress)
                .bind("gateway_address", gatewayAddress)
                .bind("target_address", targetAddress)
                .mapTo(IcmpRedirectEntry.class)
                .findOne();
    }

    public IcmpRedirectEntry createRedirect(Handle handle,
                                            UUID organizationId,
                                            UUID tenantId,
                                            String senderAddress,
                                            @Nullable String senderMac,
                                            String gatewayAddress,
                                            String targetAddress,
                                            @Nullable String targetMac,
                                            List<String> redirectedDestinations,
                                            long packetCount,
                                            Collection<UUID> tapUuids,
                                            DateTime firstSeen,
                                            DateTime lastSeen) {
        return handle.createQuery("INSERT INTO icmp_redirects(uuid, organization_id, tenant_id, sender_address, " +
                        "sender_mac, gateway_address, target_address, target_mac, redirected_destinations, " +
                        "packet_count, tap_uuids, first_seen, last_seen, created_at, updated_at) VALUES(:uuid, " +
                        ":organization_id, :tenant_id, :sender_address::inet, :sender_mac, :gateway_address::inet, " +
                        ":target_address::inet, :target_mac, :redirected_destinations::jsonb, :packet_count, " +
                        ":tap_uuids::jsonb, :first_seen, :last_seen, NOW(), NOW()) RETURNING *")
                .bind("uuid", UUID.randomUUID())
                .bind("organization_id", organizationId)
                .bind("tenant_id", tenantId)
                .bind("sender_address", senderAddress)
                .bind("sender_mac", senderMac)
                .bind("gateway_address", gatewayAddress)
                .bind("target_address", targetAddress)
                .bind("target_mac", targetMac)
                .bind("redirected_destinations", JsonArrays.toJson(redirectedDestinations))
                .bind("packet_count", packetCount)
                .bind("tap_uuids", JsonArrays.toJson(tapUuids.stream().map(UUID::toString).toList()))
                .bind("first_seen", firstSeen)
                .bind("last_seen", lastSeen)
                .mapTo(IcmpRedirectEntry.class)
                .one();
    }

    public void updateRedirect(Handle handle,
                               long id,
                               List<String> redirectedDestinations,
                               long additionalPackets,
                               Collection<UUID> tapUuids,
                               DateTime lastSeen) {
        handle.createUpdate("UPDATE icmp_redirects SET " +
                        "redirected_destinations = (SELECT COALESCE(jsonb_agg(DISTINCT d), '[]'::jsonb) FROM " +
                        "jsonb_array_elements_text(redirected_destinations || :redirected_destinations::jsonb) AS d), " +
                        "packet_count = packet_count + :additional_packets, " +
                        "tap_uuids = (SELECT COALESCE(jsonb_agg(DISTINCT t), '[]'::jsonb) FROM " +
                        "jsonb_array_elements_text(tap_uuids || :tap_uuids::jsonb) AS t), " +
                        "last_seen = GREATEST(last_seen, :last_seen), updated_at = NOW() WHERE id = :id")
                .bind("id", id)
                .bind("redirected_destinations", JsonArrays.toJson(redirectedDestinations))
                .bind("additional_packets", additionalPackets)
                .bind("tap_uuids", JsonArrays.toJson(tapUuids.stream().map(UUID::toString).toList()))
                .bind("last_seen", lastSeen)
                .execute();
    }

    public long countTraceroutes(Filters filters, @Nullable List<UUID> taps, TimeRange timeRange, UUID organizationId, UUID tenantId) {
        FilterSqlFragment filterFragment = FilterSql.generate(filters, new IcmpTracerouteFilters());
        return nzyme.getDatabase().withHandle(handle ->
                bindTaps(handle.createQuery("SELECT COUNT(*) FROM icmp_traceroutes WHERE organization_id = :organization_id " +
                                "AND tenant_id = :tenant_id " + timeRangeCondition("") + tapsCondition(taps, null) + filterFragment.whereSql()), taps)
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
                        .bind("tr_from", timeRange.from())
                        .bind("tr_to", timeRange.to())
                        .bindMap(filterFragment.bindings())
                        .mapTo(Long.class)
                        .one()
        );
    }

    public List<IcmpTracerouteEntry> findAllTraceroutes(Filters filters,
                                                        @Nullable List<UUID> taps,
                                                        TimeRange timeRange,
                                                        TracerouteOrderColumn orderColumn,
                                                        OrderDirection orderDirection,
                                                        int limit,
                                                        int offset,
                                                        UUID organizationId,
                                                        UUID tenantId) {
        FilterSqlFragment filterFragment = FilterSql.generate(filters, new IcmpTracerouteFilters());
        return nzyme.getDatabase().withHandle(handle ->
                bindTaps(handle.createQuery("SELECT * FROM icmp_traceroutes WHERE organization_id = :organization_id " +
                                "AND tenant_id = :tenant_id " + timeRangeCondition("") + tapsCondition(taps, null) + filterFragment.whereSql() +
                                "ORDER BY <order_column> <order_direction>, id DESC " +
                                "LIMIT :limit OFFSET :offset"), taps)
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
                        .bind("tr_from", timeRange.from())
                        .bind("tr_to", timeRange.to())
                        .bindMap(filterFragment.bindings())
                        .bind("limit", limit)
                        .bind("offset", offset)
                        .define("order_column", orderColumn.getColumnName())
                        .define("order_direction", orderDirection)
                        .mapTo(IcmpTracerouteEntry.class)
                        .list()
        );
    }

    public List<GenericIntegerHistogramEntry> tracerouteHistogram(Filters filters,
                                                                  @Nullable List<UUID> taps,
                                                                  UUID organizationId,
                                                                  UUID tenantId,
                                                                  TimeRange timeRange,
                                                                  Bucketing.BucketingConfiguration bucketing) {
        FilterSqlFragment filterFragment = FilterSql.generate(filters, new IcmpTracerouteFilters("t"));
        String step = "1 " + bucketing.type().getDateTruncName();

        return nzyme.getDatabase().withHandle(handle ->
                bindTaps(handle.createQuery("SELECT b.bucket AS bucket, COUNT(t.id)::integer AS value " +
                                "FROM generate_series(date_trunc(:date_trunc, :tr_from::timestamptz), " +
                                ":tr_to::timestamptz, :step::interval) AS b(bucket) " +
                                "LEFT JOIN icmp_traceroutes t ON t.organization_id = :organization_id " +
                                "AND t.tenant_id = :tenant_id AND t.first_seen < b.bucket + :step::interval " +
                                "AND t.last_seen >= b.bucket " + tapsCondition(taps, "t") + filterFragment.whereSql() +
                                "GROUP BY b.bucket ORDER BY b.bucket DESC"), taps)
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
                        .bindMap(filterFragment.bindings())
                        .bind("date_trunc", bucketing.type().getDateTruncName())
                        .bind("step", step)
                        .bind("tr_from", timeRange.from())
                        .bind("tr_to", timeRange.to())
                        .mapTo(GenericIntegerHistogramEntry.class)
                        .list()
        );
    }

    private record TracerouteGroupColumn(String valueExpr, String macExpr, String groupCol) { }

    private static TracerouteGroupColumn tracerouteGroupColumn(String by) {
        switch (by == null ? "" : by.toUpperCase()) {
            case "SENDER":
                return new TracerouteGroupColumn("host(source_address)",
                        "mode() WITHIN GROUP (ORDER BY source_mac)", "source_address");
            case "DESTINATION":
                return new TracerouteGroupColumn("host(destination_address)", "NULL", "destination_address");
            case "PROTOCOL":
                return new TracerouteGroupColumn("protocol::text", "NULL", "protocol");
            default:
                throw new IllegalArgumentException("Unknown traceroute histogram column [" + by + "].");
        }
    }

    public long countTracerouteGroups(String by, Filters filters, @Nullable List<UUID> taps, TimeRange timeRange, UUID organizationId, UUID tenantId) {
        TracerouteGroupColumn col = tracerouteGroupColumn(by);
        FilterSqlFragment filterFragment = FilterSql.generate(filters, new IcmpTracerouteFilters());
        return nzyme.getDatabase().withHandle(handle ->
                bindTaps(handle.createQuery("SELECT COUNT(*) FROM (SELECT 1 FROM icmp_traceroutes " +
                                "WHERE organization_id = :organization_id AND tenant_id = :tenant_id " +
                                timeRangeCondition("") + tapsCondition(taps, null) + filterFragment.whereSql() + "GROUP BY <group_col>) x"), taps)
                        .define("group_col", col.groupCol())
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
                        .bind("tr_from", timeRange.from())
                        .bind("tr_to", timeRange.to())
                        .bindMap(filterFragment.bindings())
                        .mapTo(Long.class)
                        .one()
        );
    }

    public List<TracerouteGroup> tracerouteGroups(String by,
                                                  Filters filters,
                                                  @Nullable List<UUID> taps,
                                                  TimeRange timeRange,
                                                  TwoColumnHistogramOrderColumn orderColumn,
                                                  OrderDirection orderDirection,
                                                  int limit,
                                                  int offset,
                                                  UUID organizationId,
                                                  UUID tenantId) {
        TracerouteGroupColumn col = tracerouteGroupColumn(by);
        String order = orderColumn == TwoColumnHistogramOrderColumn.KEY ? "value" : "value_count";
        FilterSqlFragment filterFragment = FilterSql.generate(filters, new IcmpTracerouteFilters());
        return nzyme.getDatabase().withHandle(handle ->
                bindTaps(handle.createQuery("SELECT <value_expr> AS value, <mac_expr> AS mac, COUNT(*) AS value_count " +
                                "FROM icmp_traceroutes WHERE organization_id = :organization_id " +
                                "AND tenant_id = :tenant_id " + timeRangeCondition("") + tapsCondition(taps, null) + filterFragment.whereSql() +
                                "GROUP BY <group_col> ORDER BY <order_column> <order_direction> " +
                                "LIMIT :limit OFFSET :offset"), taps)
                        .define("value_expr", col.valueExpr())
                        .define("mac_expr", col.macExpr())
                        .define("group_col", col.groupCol())
                        .define("order_column", order)
                        .define("order_direction", orderDirection)
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
                        .bind("tr_from", timeRange.from())
                        .bind("tr_to", timeRange.to())
                        .bindMap(filterFragment.bindings())
                        .bind("limit", limit)
                        .bind("offset", offset)
                        .map((rs, ctx) -> new TracerouteGroup(rs.getString("value"), rs.getString("mac"), rs.getLong("value_count")))
                        .list()
        );
    }

    public Optional<IcmpTracerouteEntry> findTraceroute(UUID uuid, UUID organizationId, UUID tenantId) {
        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT * FROM icmp_traceroutes WHERE uuid = :uuid " +
                                "AND organization_id = :organization_id AND tenant_id = :tenant_id")
                        .bind("uuid", uuid)
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
                        .mapTo(IcmpTracerouteEntry.class)
                        .findOne()
        );
    }

    public Optional<IcmpTracerouteEntry> findActiveTraceroute(Handle handle,
                                                              UUID organizationId,
                                                              UUID tenantId,
                                                              String sourceAddress,
                                                              String destinationAddress,
                                                              DateTime since) {
        return handle.createQuery("SELECT * FROM icmp_traceroutes WHERE organization_id = :organization_id " +
                        "AND tenant_id = :tenant_id AND source_address = :source_address::inet " +
                        "AND destination_address = :destination_address::inet AND last_seen >= :since " +
                        "ORDER BY last_seen DESC LIMIT 1")
                .bind("organization_id", organizationId)
                .bind("tenant_id", tenantId)
                .bind("source_address", sourceAddress)
                .bind("destination_address", destinationAddress)
                .bind("since", since)
                .mapTo(IcmpTracerouteEntry.class)
                .findOne();
    }

    public IcmpTracerouteEntry createTraceroute(Handle handle,
                                                UUID organizationId,
                                                UUID tenantId,
                                                String sourceAddress,
                                                @Nullable String sourceMac,
                                                String destinationAddress,
                                                @Nullable Integer protocol,
                                                List<String> routers,
                                                long packetCount,
                                                boolean destinationReached,
                                                Collection<UUID> tapUuids,
                                                DateTime firstSeen,
                                                DateTime lastSeen) {
        return handle.createQuery("INSERT INTO icmp_traceroutes(uuid, organization_id, tenant_id, source_address, " +
                        "source_mac, destination_address, protocol, routers, packet_count, destination_reached, " +
                        "tap_uuids, first_seen, last_seen, created_at, updated_at) VALUES(:uuid, :organization_id, " +
                        ":tenant_id, :source_address::inet, :source_mac, :destination_address::inet, :protocol, " +
                        ":routers::jsonb, :packet_count, :destination_reached, :tap_uuids::jsonb, :first_seen, " +
                        ":last_seen, NOW(), NOW()) RETURNING *")
                .bind("uuid", UUID.randomUUID())
                .bind("organization_id", organizationId)
                .bind("tenant_id", tenantId)
                .bind("source_address", sourceAddress)
                .bind("source_mac", sourceMac)
                .bind("destination_address", destinationAddress)
                .bind("protocol", protocol)
                .bind("routers", JsonArrays.toJson(routers))
                .bind("packet_count", packetCount)
                .bind("destination_reached", destinationReached)
                .bind("tap_uuids", JsonArrays.toJson(tapUuids.stream().map(UUID::toString).toList()))
                .bind("first_seen", firstSeen)
                .bind("last_seen", lastSeen)
                .mapTo(IcmpTracerouteEntry.class)
                .one();
    }

    public void updateTraceroute(Handle handle,
                                 long id,
                                 List<String> routers,
                                 long packetCount,
                                 boolean destinationReached,
                                 Collection<UUID> tapUuids,
                                 DateTime lastSeen) {
        handle.createUpdate("UPDATE icmp_traceroutes SET routers = :routers::jsonb, " +
                        "packet_count = GREATEST(packet_count, :packet_count), " +
                        "destination_reached = destination_reached OR :destination_reached, " +
                        "tap_uuids = (SELECT COALESCE(jsonb_agg(DISTINCT t), '[]'::jsonb) FROM " +
                        "jsonb_array_elements_text(tap_uuids || :tap_uuids::jsonb) AS t), " +
                        "last_seen = GREATEST(last_seen, :last_seen), updated_at = NOW() WHERE id = :id")
                .bind("id", id)
                .bind("routers", JsonArrays.toJson(routers))
                .bind("packet_count", packetCount)
                .bind("destination_reached", destinationReached)
                .bind("tap_uuids", JsonArrays.toJson(tapUuids.stream().map(UUID::toString).toList()))
                .bind("last_seen", lastSeen)
                .execute();
    }
    
    public enum TunnelOrderColumn {

        SOURCE_ADDRESS("source_address"),
        DESTINATION_ADDRESS("destination_address"),
        MAX_PAYLOAD_BYTES("max_payload_bytes"),
        PACKET_COUNT("packet_count"),
        FIRST_SEEN("first_seen"),
        LAST_SEEN("last_seen");

        private final String columnName;

        TunnelOrderColumn(String columnName) {
            this.columnName = columnName;
        }

        public String getColumnName() {
            return columnName;
        }
    }

    public long countTunnels(Filters filters, @Nullable List<UUID> taps, TimeRange timeRange, UUID organizationId, UUID tenantId) {
        FilterSqlFragment filterFragment = FilterSql.generate(filters, new IcmpTunnelFilters());
        return nzyme.getDatabase().withHandle(handle ->
                bindTaps(handle.createQuery("SELECT COUNT(*) FROM icmp_tunnels WHERE organization_id = :organization_id " +
                                "AND tenant_id = :tenant_id " + timeRangeCondition("") + tapsCondition(taps, null) + filterFragment.whereSql()), taps)
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
                        .bind("tr_from", timeRange.from())
                        .bind("tr_to", timeRange.to())
                        .bindMap(filterFragment.bindings())
                        .mapTo(Long.class)
                        .one()
        );
    }

    public List<IcmpTunnelEntry> findAllTunnels(Filters filters,
                                                @Nullable List<UUID> taps,
                                                TimeRange timeRange,
                                                TunnelOrderColumn orderColumn,
                                                OrderDirection orderDirection,
                                                int limit,
                                                int offset,
                                                UUID organizationId,
                                                UUID tenantId) {
        FilterSqlFragment filterFragment = FilterSql.generate(filters, new IcmpTunnelFilters());
        return nzyme.getDatabase().withHandle(handle ->
                bindTaps(handle.createQuery("SELECT * FROM icmp_tunnels WHERE organization_id = :organization_id " +
                                "AND tenant_id = :tenant_id " + timeRangeCondition("") + tapsCondition(taps, null) + filterFragment.whereSql() +
                                "ORDER BY <order_column> <order_direction>, id DESC LIMIT :limit OFFSET :offset"), taps)
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
                        .bind("tr_from", timeRange.from())
                        .bind("tr_to", timeRange.to())
                        .bindMap(filterFragment.bindings())
                        .bind("limit", limit)
                        .bind("offset", offset)
                        .define("order_column", orderColumn.getColumnName())
                        .define("order_direction", orderDirection)
                        .mapTo(IcmpTunnelEntry.class)
                        .list()
        );
    }

    public List<GenericIntegerHistogramEntry> tunnelHistogram(Filters filters,
                                                              @Nullable List<UUID> taps,
                                                              UUID organizationId,
                                                              UUID tenantId,
                                                              TimeRange timeRange,
                                                              Bucketing.BucketingConfiguration bucketing) {
        FilterSqlFragment filterFragment = FilterSql.generate(filters, new IcmpTunnelFilters("t"));
        String step = "1 " + bucketing.type().getDateTruncName();

        return nzyme.getDatabase().withHandle(handle ->
                bindTaps(handle.createQuery("SELECT b.bucket AS bucket, COUNT(t.id)::integer AS value " +
                                "FROM generate_series(date_trunc(:date_trunc, :tr_from::timestamptz), " +
                                ":tr_to::timestamptz, :step::interval) AS b(bucket) " +
                                "LEFT JOIN icmp_tunnels t ON t.organization_id = :organization_id " +
                                "AND t.tenant_id = :tenant_id AND t.first_seen < b.bucket + :step::interval " +
                                "AND t.last_seen >= b.bucket " + tapsCondition(taps, "t") + filterFragment.whereSql() +
                                "GROUP BY b.bucket ORDER BY b.bucket DESC"), taps)
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
                        .bindMap(filterFragment.bindings())
                        .bind("date_trunc", bucketing.type().getDateTruncName())
                        .bind("step", step)
                        .bind("tr_from", timeRange.from())
                        .bind("tr_to", timeRange.to())
                        .mapTo(GenericIntegerHistogramEntry.class)
                        .list()
        );
    }

    public Optional<IcmpTunnelEntry> findActiveTunnel(Handle handle,
                                                      UUID organizationId,
                                                      UUID tenantId,
                                                      String sourceAddress,
                                                      String destinationAddress,
                                                      DateTime since) {
        return handle.createQuery("SELECT * FROM icmp_tunnels WHERE organization_id = :organization_id " +
                        "AND tenant_id = :tenant_id AND source_address = :source_address::inet " +
                        "AND destination_address = :destination_address::inet AND last_seen >= :since " +
                        "ORDER BY last_seen DESC LIMIT 1")
                .bind("organization_id", organizationId)
                .bind("tenant_id", tenantId)
                .bind("source_address", sourceAddress)
                .bind("destination_address", destinationAddress)
                .bind("since", since)
                .mapTo(IcmpTunnelEntry.class)
                .findOne();
    }

    public IcmpTunnelEntry createTunnel(Handle handle,
                                        UUID organizationId,
                                        UUID tenantId,
                                        String sourceAddress,
                                        @Nullable String sourceMac,
                                        String destinationAddress,
                                        @Nullable String destinationMac,
                                        long maxPayloadBytes,
                                        long packetCount,
                                        Collection<UUID> tapUuids,
                                        DateTime firstSeen,
                                        DateTime lastSeen) {
        return handle.createQuery("INSERT INTO icmp_tunnels(uuid, organization_id, tenant_id, source_address, " +
                        "source_mac, destination_address, destination_mac, max_payload_bytes, packet_count, " +
                        "tap_uuids, first_seen, last_seen, created_at, updated_at) VALUES(:uuid, :organization_id, " +
                        ":tenant_id, :source_address::inet, :source_mac, :destination_address::inet, :destination_mac, " +
                        ":max_payload_bytes, :packet_count, :tap_uuids::jsonb, :first_seen, :last_seen, NOW(), NOW()) RETURNING *")
                .bind("uuid", UUID.randomUUID())
                .bind("organization_id", organizationId)
                .bind("tenant_id", tenantId)
                .bind("source_address", sourceAddress)
                .bind("source_mac", sourceMac)
                .bind("destination_address", destinationAddress)
                .bind("destination_mac", destinationMac)
                .bind("max_payload_bytes", maxPayloadBytes)
                .bind("packet_count", packetCount)
                .bind("tap_uuids", JsonArrays.toJson(tapUuids.stream().map(UUID::toString).toList()))
                .bind("first_seen", firstSeen)
                .bind("last_seen", lastSeen)
                .mapTo(IcmpTunnelEntry.class)
                .one();
    }

    public void updateTunnel(Handle handle,
                             long id,
                             long maxPayloadBytes,
                             long packetCount,
                             Collection<UUID> tapUuids,
                             DateTime lastSeen) {
        handle.createUpdate("UPDATE icmp_tunnels SET " +
                        "max_payload_bytes = GREATEST(max_payload_bytes, :max_payload_bytes), " +
                        "packet_count = GREATEST(packet_count, :packet_count), " +
                        "tap_uuids = (SELECT COALESCE(jsonb_agg(DISTINCT t), '[]'::jsonb) FROM " +
                        "jsonb_array_elements_text(tap_uuids || :tap_uuids::jsonb) AS t), " +
                        "last_seen = GREATEST(last_seen, :last_seen), updated_at = NOW() WHERE id = :id")
                .bind("id", id)
                .bind("max_payload_bytes", maxPayloadBytes)
                .bind("packet_count", packetCount)
                .bind("tap_uuids", JsonArrays.toJson(tapUuids.stream().map(UUID::toString).toList()))
                .bind("last_seen", lastSeen)
                .execute();
    }

    public Optional<IcmpTunnelEntry> findTunnel(UUID uuid, UUID organizationId, UUID tenantId) {
        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT * FROM icmp_tunnels WHERE uuid = :uuid " +
                                "AND organization_id = :organization_id AND tenant_id = :tenant_id")
                        .bind("uuid", uuid)
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
                        .mapTo(IcmpTunnelEntry.class)
                        .findOne()
        );
    }

    private record TunnelBreakdownColumn(String valueExpr, String macExpr, String groupCol, String countExpr) { }

    private static TunnelBreakdownColumn tunnelBreakdownColumn(String by) {
        switch (by == null ? "" : by.toUpperCase()) {
            case "PEER":
                return new TunnelBreakdownColumn("host(destination_address)",
                        "mode() WITHIN GROUP (ORDER BY destination_mac)", "destination_address",
                        "COUNT(DISTINCT source_address)");
            case "SOURCE":
                return new TunnelBreakdownColumn("host(source_address)",
                        "mode() WITHIN GROUP (ORDER BY source_mac)", "source_address",
                        "COUNT(DISTINCT destination_address)");
            default:
                throw new IllegalArgumentException("Unknown tunnel breakdown column [" + by + "].");
        }
    }

    public long countTunnelBreakdown(String by, Filters filters, @Nullable List<UUID> taps, TimeRange timeRange, UUID organizationId, UUID tenantId) {
        TunnelBreakdownColumn col = tunnelBreakdownColumn(by);
        FilterSqlFragment filterFragment = FilterSql.generate(filters, new IcmpTunnelFilters());
        return nzyme.getDatabase().withHandle(handle ->
                bindTaps(handle.createQuery("SELECT COUNT(*) FROM (SELECT 1 FROM icmp_tunnels " +
                                "WHERE organization_id = :organization_id AND tenant_id = :tenant_id " +
                                timeRangeCondition("") + tapsCondition(taps, null) + filterFragment.whereSql() +
                                "GROUP BY <group_col>) x"), taps)
                        .define("group_col", col.groupCol())
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
                        .bind("tr_from", timeRange.from())
                        .bind("tr_to", timeRange.to())
                        .bindMap(filterFragment.bindings())
                        .mapTo(Long.class)
                        .one()
        );
    }

    public List<UnreachableAddressGroup> tunnelBreakdown(String by,
                                                         Filters filters,
                                                         @Nullable List<UUID> taps,
                                                         TimeRange timeRange,
                                                         TwoColumnHistogramOrderColumn orderColumn,
                                                         OrderDirection orderDirection,
                                                         int limit,
                                                         int offset,
                                                         UUID organizationId,
                                                         UUID tenantId) {
        TunnelBreakdownColumn col = tunnelBreakdownColumn(by);
        String order = orderColumn == TwoColumnHistogramOrderColumn.KEY ? "value" : "value_count";
        FilterSqlFragment filterFragment = FilterSql.generate(filters, new IcmpTunnelFilters());
        return nzyme.getDatabase().withHandle(handle ->
                bindTaps(handle.createQuery("SELECT <value_expr> AS value, <mac_expr> AS mac, <count_expr> AS value_count " +
                                "FROM icmp_tunnels WHERE organization_id = :organization_id AND tenant_id = :tenant_id " +
                                timeRangeCondition("") + tapsCondition(taps, null) + filterFragment.whereSql() +
                                "GROUP BY <group_col> ORDER BY <order_column> <order_direction> " +
                                "LIMIT :limit OFFSET :offset"), taps)
                        .define("value_expr", col.valueExpr())
                        .define("mac_expr", col.macExpr())
                        .define("count_expr", col.countExpr())
                        .define("group_col", col.groupCol())
                        .define("order_column", order)
                        .define("order_direction", orderDirection)
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
                        .bind("tr_from", timeRange.from())
                        .bind("tr_to", timeRange.to())
                        .bindMap(filterFragment.bindings())
                        .bind("limit", limit)
                        .bind("offset", offset)
                        .map((rs, ctx) -> new UnreachableAddressGroup(rs.getString("value"), rs.getString("mac"), rs.getLong("value_count")))
                        .list()
        );
    }

    public void retentionCleanDetections(UUID organizationId, UUID tenantId, DateTime cutoff) {
        nzyme.getDatabase().useHandle(handle -> {
            handle.createUpdate("DELETE FROM icmp_redirects WHERE organization_id = :organization_id " +
                            "AND tenant_id = :tenant_id AND last_seen < :cutoff")
                    .bind("organization_id", organizationId)
                    .bind("tenant_id", tenantId)
                    .bind("cutoff", cutoff)
                    .execute();
            handle.createUpdate("DELETE FROM icmp_traceroutes WHERE organization_id = :organization_id " +
                            "AND tenant_id = :tenant_id AND last_seen < :cutoff")
                    .bind("organization_id", organizationId)
                    .bind("tenant_id", tenantId)
                    .bind("cutoff", cutoff)
                    .execute();
            handle.createUpdate("DELETE FROM icmp_tunnels WHERE organization_id = :organization_id " +
                            "AND tenant_id = :tenant_id AND last_seen < :cutoff")
                    .bind("organization_id", organizationId)
                    .bind("tenant_id", tenantId)
                    .bind("cutoff", cutoff)
                    .execute();
        });
    }

}
