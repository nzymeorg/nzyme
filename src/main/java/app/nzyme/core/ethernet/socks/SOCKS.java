package app.nzyme.core.ethernet.socks;

import app.nzyme.core.NzymeNode;
import app.nzyme.core.database.OrderDirection;
import app.nzyme.core.database.generic.L4AddressDataAddressNumberNumberAggregationResult;
import app.nzyme.core.database.generic.ThreeColumnWithKeyHistogramOrderColumn;
import app.nzyme.core.ethernet.Ethernet;
import app.nzyme.core.ethernet.socks.db.SocksTunnelEntry;
import app.nzyme.core.shared.db.GenericIntegerHistogramEntry;
import app.nzyme.core.util.Bucketing;
import app.nzyme.core.util.TimeRange;
import app.nzyme.core.util.filters.FilterSql;
import app.nzyme.core.util.filters.FilterSqlFragment;
import app.nzyme.core.util.filters.Filters;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class SOCKS {

    public enum OrderColumn {

        TUNNEL_ID("MIN(socks.uuid)"),
        CLIENT_ADDRESS("MIN(tcp.source_address)"),
        CLIENT_MAC("MIN(tcp.source_mac)"),
        SERVER_ADDRESS("MIN(tcp.destination_address)"),
        SERVER_MAC("MIN(tcp.destination_mac)"),
        TYPE("socks_type"),
        CONNECTION_STATUS("connection_status"),
        TUNNELED_BYTES("tunneled_bytes"),
        ESTABLISHED_AT("established_at"),
        TERMINATED_AT("terminated_at"),
        DURATION("duration_ms");

        private final String columnName;

        OrderColumn(String columnName) {
            this.columnName = columnName;
        }

        public String getColumnName() {
            return columnName;
        }

    }

    private enum Side {

        CLIENT("client", "source"),
        SERVER("server", "destination");

        private final String alias;
        private final String tcpPrefix;

        Side(String alias, String tcpPrefix) {
            this.alias = alias;
            this.tcpPrefix = tcpPrefix;
        }

    }

    private static final String FROM_SOCKS_WITH_TCP =
            "FROM socks_tunnels AS socks " +
                    "LEFT JOIN l4_sessions AS tcp ON tcp.session_key = socks.tcp_session_key " +
                    "AND tcp.l4_type = 'TCP' AND tcp.tap_uuid = socks.tap_uuid " +
                    "AND tcp.start_time >= (socks.established_at - interval '1 minute') " +
                    "AND tcp.start_time <= (socks.established_at + interval '1 minute') ";

    private static final String TIME_RANGE_WITHIN =
            "socks.most_recent_segment_time >= :tr_from AND socks.most_recent_segment_time <= :tr_to ";

    private static final String TIME_RANGE_OVERLAPS =
            "socks.most_recent_segment_time >= :tr_from AND socks.established_at <= :tr_to ";

    private static final String TUNNEL_ENTRY_COLUMNS =
            "SELECT socks.tcp_session_key, " +
                    "MIN(socks.socks_type) AS socks_type, " +
                    "MIN(socks.authentication_status) AS authentication_status, " +
                    "MIN(socks.handshake_status) AS handshake_status, " +
                    "MIN(socks.connection_status) AS connection_status, " +
                    "MIN(socks.username) AS username, " +
                    "MAX(socks.tunneled_bytes) AS tunneled_bytes, " +
                    "MIN(socks.tunneled_destination_address) AS tunneled_destination_address, " +
                    "MIN(socks.tunneled_destination_host) AS tunneled_destination_host, " +
                    "MIN(socks.tunneled_destination_port) AS tunneled_destination_port, " +
                    "MIN(socks.established_at) AS established_at, " +
                    "MAX(socks.terminated_at) AS terminated_at, " +
                    "MAX(socks.most_recent_segment_time) AS most_recent_segment_time, " +
                    "(EXTRACT(EPOCH FROM " +
                    "(MAX(socks.most_recent_segment_time) - MIN(socks.established_at))) * 1000" +
                    ") AS duration_ms ";

    private final NzymeNode nzyme;

    public SOCKS(Ethernet ethernet) {
        this.nzyme = ethernet.getNzyme();
    }

    public long countAllTunnels(TimeRange timeRange, Filters filters, List<UUID> taps) {
        if (taps.isEmpty()) {
            return 0;
        }

        FilterSqlFragment filterFragment = FilterSql.generate(filters, new SOCKSFilters());

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT COUNT(*) FROM (" +
                                "SELECT socks.tcp_session_key " +
                                FROM_SOCKS_WITH_TCP +
                                "WHERE " + TIME_RANGE_WITHIN +
                                "AND socks.tap_uuid IN (<taps>) " + filterFragment.whereSql() + " " +
                                "GROUP BY socks.tcp_session_key HAVING 1=1 " + filterFragment.havingSql() + " " +
                                ") AS tunnels")
                        .bindList("taps", taps)
                        .bindMap(filterFragment.bindings())
                        .bind("tr_from", timeRange.from())
                        .bind("tr_to", timeRange.to())
                        .mapTo(Long.class)
                        .one()
        );
    }

    public List<SocksTunnelEntry> findAllTunnels(TimeRange timeRange,
                                                 Filters filters,
                                                 OrderColumn orderColumn,
                                                 OrderDirection orderDirection,
                                                 int limit,
                                                 int offset,
                                                 List<UUID> taps) {
        if (taps.isEmpty()) {
            return Collections.emptyList();
        }

        FilterSqlFragment filterFragment = FilterSql.generate(filters, new SOCKSFilters());

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery(TUNNEL_ENTRY_COLUMNS +
                                FROM_SOCKS_WITH_TCP +
                                "WHERE " + TIME_RANGE_WITHIN +
                                "AND socks.tap_uuid IN (<taps>) " + filterFragment.whereSql() + " " +
                                "GROUP BY socks.tcp_session_key HAVING 1=1 " + filterFragment.havingSql() + " " +
                                "ORDER BY <order_column> <order_direction> " +
                                "LIMIT :limit OFFSET :offset")
                        .bindList("taps", taps)
                        .bindMap(filterFragment.bindings())
                        .bind("tr_from", timeRange.from())
                        .bind("tr_to", timeRange.to())
                        .bind("limit", limit)
                        .bind("offset", offset)
                        .define("order_column", orderColumn.getColumnName())
                        .define("order_direction", orderDirection)
                        .mapTo(SocksTunnelEntry.class)
                        .list()
        );
    }

    public Optional<SocksTunnelEntry> findTunnel(String sessionKey, List<UUID> taps) {
        if (taps.isEmpty()) {
            return Optional.empty();
        }

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery(TUNNEL_ENTRY_COLUMNS +
                                FROM_SOCKS_WITH_TCP +
                                "WHERE socks.tcp_session_key = :tcp_session_key AND socks.tap_uuid IN (<taps>) " +
                                "GROUP BY socks.tcp_session_key")
                        .bindList("taps", taps)
                        .bind("tcp_session_key", sessionKey)
                        .mapTo(SocksTunnelEntry.class)
                        .findOne()
        );
    }

    public List<GenericIntegerHistogramEntry> getActiveTunnelsHistogram(TimeRange timeRange,
                                                                        Bucketing.BucketingConfiguration bucketing,
                                                                        Filters filters,
                                                                        List<UUID> taps) {
        if (taps.isEmpty()) {
            return Collections.emptyList();
        }

        FilterSqlFragment filterFragment = FilterSql.generate(filters, new SOCKSFilters());

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("WITH buckets AS (" +
                                "SELECT generate_series(" +
                                "date_trunc(:date_trunc, :tr_from::timestamptz), " +
                                "date_trunc(:date_trunc, :tr_to::timestamptz), " +
                                "make_interval(secs => :bucket_size_s)" +
                                ") AS bucket" +
                                "), " +
                                "tun AS (" + tunnelAggregateSelect(filterFragment, TIME_RANGE_OVERLAPS) + ") " +
                                "SELECT b.bucket AS bucket, COUNT(tun.tcp_session_key) AS value " +
                                "FROM buckets AS b " +
                                "LEFT JOIN tun " +
                                "ON tun.established_at <= b.bucket + make_interval(secs => :bucket_size_s) " +
                                "AND tun.most_recent_segment_time >= b.bucket " +
                                "GROUP BY b.bucket ORDER BY b.bucket DESC")
                        .bind("tr_from", timeRange.from())
                        .bind("tr_to", timeRange.to())
                        .bind("date_trunc", bucketing.type().getDateTruncName())
                        .bind("bucket_size_s", bucketing.bucketSizeMs() / 1000.0)
                        .bindList("taps", taps)
                        .bindMap(filterFragment.bindings())
                        .mapTo(GenericIntegerHistogramEntry.class)
                        .list()
        );
    }

    public long getTopClientsCount(TimeRange timeRange, Filters filters, List<UUID> taps) {
        return countDistinctAddresses(Side.CLIENT, timeRange, filters, taps);
    }

    public List<L4AddressDataAddressNumberNumberAggregationResult> getTopClients(TimeRange timeRange,
                                                                                 Filters filters,
                                                                                 int limit,
                                                                                 int offset,
                                                                                 ThreeColumnWithKeyHistogramOrderColumn orderColumn,
                                                                                 OrderDirection orderDirection,
                                                                                 List<UUID> taps) {
        return findTopAddresses(Side.CLIENT, timeRange, filters, limit, offset, orderColumn, orderDirection, taps);
    }

    public long getTopServersCount(TimeRange timeRange, Filters filters, List<UUID> taps) {
        return countDistinctAddresses(Side.SERVER, timeRange, filters, taps);
    }

    public List<L4AddressDataAddressNumberNumberAggregationResult> getTopServers(TimeRange timeRange,
                                                                                 Filters filters,
                                                                                 int limit,
                                                                                 int offset,
                                                                                 ThreeColumnWithKeyHistogramOrderColumn orderColumn,
                                                                                 OrderDirection orderDirection,
                                                                                 List<UUID> taps) {
        return findTopAddresses(Side.SERVER, timeRange, filters, limit, offset, orderColumn, orderDirection, taps);
    }

    private long countDistinctAddresses(Side side, TimeRange timeRange, Filters filters, List<UUID> taps) {
        if (taps.isEmpty()) {
            return 0;
        }

        FilterSqlFragment filterFragment = FilterSql.generate(filters, new SOCKSFilters());

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT COUNT(DISTINCT tun." + side.alias + "_address) FROM (" +
                                tunnelAggregateSelect(filterFragment, TIME_RANGE_WITHIN) +
                                ") AS tun")
                        .bindList("taps", taps)
                        .bindMap(filterFragment.bindings())
                        .bind("tr_from", timeRange.from())
                        .bind("tr_to", timeRange.to())
                        .mapTo(Long.class)
                        .one()
        );
    }

    private List<L4AddressDataAddressNumberNumberAggregationResult> findTopAddresses(Side side,
                                                                                     TimeRange timeRange,
                                                                                     Filters filters,
                                                                                     int limit,
                                                                                     int offset,
                                                                                     ThreeColumnWithKeyHistogramOrderColumn orderColumn,
                                                                                     OrderDirection orderDirection,
                                                                                     List<UUID> taps) {
        if (taps.isEmpty()) {
            return Collections.emptyList();
        }

        FilterSqlFragment filterFragment = FilterSql.generate(filters, new SOCKSFilters());

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("WITH tun AS (" +
                                tunnelAggregateSelect(filterFragment, TIME_RANGE_WITHIN) + ") " +
                                "SELECT " + keyAddressColumns(side) +
                                "COUNT(*) AS value1, " +
                                "COALESCE(SUM(tun.tunneled_bytes), 0)::bigint AS value2 " +
                                "FROM tun " +
                                "WHERE tun." + side.alias + "_address IS NOT NULL " +
                                "GROUP BY tun." + side.alias + "_address " +
                                "ORDER BY <order_column> <order_direction>, key ASC " +
                                "LIMIT :limit OFFSET :offset")
                        .bindList("taps", taps)
                        .bindMap(filterFragment.bindings())
                        .bind("tr_from", timeRange.from())
                        .bind("tr_to", timeRange.to())
                        .bind("limit", limit)
                        .bind("offset", offset)
                        .define("order_column", orderColumn.getColumnName())
                        .define("order_direction", orderDirection)
                        .mapTo(L4AddressDataAddressNumberNumberAggregationResult.class)
                        .list()
        );
    }

    private String tunnelAggregateSelect(FilterSqlFragment filterFragment, String timeRangeCondition) {
        return "SELECT socks.tcp_session_key AS tcp_session_key, " +
                addressAttributeColumns(Side.CLIENT) +
                addressAttributeColumns(Side.SERVER) +
                "MAX(socks.tunneled_bytes) AS tunneled_bytes, " +
                "MIN(socks.established_at) AS established_at, " +
                "MAX(socks.most_recent_segment_time) AS most_recent_segment_time " +
                FROM_SOCKS_WITH_TCP +
                "WHERE " + timeRangeCondition +
                "AND socks.tap_uuid IN (<taps>) " + filterFragment.whereSql() + " " +
                "GROUP BY socks.tcp_session_key HAVING 1=1 " + filterFragment.havingSql() + " ";
    }

    private static String addressAttributeColumns(Side side) {
        String tcp = "tcp." + side.tcpPrefix;
        String a = side.alias;

        return "MIN(" + tcp + "_address) AS " + a + "_address, " +
                "MIN(" + tcp + "_port) AS " + a + "_port, " +
                "MIN(" + tcp + "_address_geo_asn_number) AS " + a + "_geo_asn_number, " +
                "MIN(" + tcp + "_address_geo_asn_name) AS " + a + "_geo_asn_name, " +
                "MIN(" + tcp + "_address_geo_asn_domain) AS " + a + "_geo_asn_domain, " +
                "MIN(" + tcp + "_address_geo_city) AS " + a + "_geo_city, " +
                "MIN(" + tcp + "_address_geo_country_code) AS " + a + "_geo_country_code, " +
                "MIN(" + tcp + "_address_geo_latitude) AS " + a + "_geo_latitude, " +
                "MIN(" + tcp + "_address_geo_longitude) AS " + a + "_geo_longitude, " +
                "BOOL_OR(" + tcp + "_address_is_site_local) AS " + a + "_is_site_local, " +
                "BOOL_OR(" + tcp + "_address_is_loopback) AS " + a + "_is_loopback, " +
                "BOOL_OR(" + tcp + "_address_is_multicast) AS " + a + "_is_multicast, ";
    }

    private static String keyAddressColumns(Side side) {
        String t = "tun." + side.alias;

        return "host(" + t + "_address) AS key, " +
                "host(" + t + "_address) AS key_address, " +
                "MAX(" + t + "_port) AS key_port, " +
                "MAX(" + t + "_geo_asn_number) AS key_address_geo_asn_number, " +
                "MAX(" + t + "_geo_asn_name) AS key_address_geo_asn_name, " +
                "MAX(" + t + "_geo_asn_domain) AS key_address_geo_asn_domain, " +
                "MAX(" + t + "_geo_city) AS key_address_geo_city, " +
                "MAX(" + t + "_geo_country_code) AS key_address_geo_country_code, " +
                "MAX(" + t + "_geo_latitude) AS key_address_geo_latitude, " +
                "MAX(" + t + "_geo_longitude) AS key_address_geo_longitude, " +
                "BOOL_OR(" + t + "_is_site_local) AS key_address_is_site_local, " +
                "BOOL_OR(" + t + "_is_loopback) AS key_address_is_loopback, " +
                "BOOL_OR(" + t + "_is_multicast) AS key_address_is_multicast, ";
    }

}