package app.nzyme.core.ethernet.ipv6transition;

import app.nzyme.core.NzymeNode;
import app.nzyme.core.database.OrderDirection;
import app.nzyme.core.database.generic.L4AddressDataAddressNumberNumberAggregationResult;
import app.nzyme.core.database.generic.ThreeColumnWithKeyHistogramOrderColumn;
import app.nzyme.core.ethernet.Ethernet;
import app.nzyme.core.ethernet.L4Type;
import app.nzyme.core.ethernet.l4.db.L4Session;
import app.nzyme.core.ethernet.ipv6transition.db.Ipv6TransitionTunnelEntry;
import app.nzyme.core.shared.db.GenericIntegerHistogramEntry;
import app.nzyme.core.util.Bucketing;
import app.nzyme.core.util.TimeRange;
import app.nzyme.core.util.Tools;
import app.nzyme.core.util.filters.FilterSql;
import app.nzyme.core.util.filters.FilterSqlFragment;
import app.nzyme.core.util.filters.Filters;
import org.joda.time.DateTime;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class Ipv6Transition {

    public enum OrderColumn {

        TUNNEL_ID("MIN(itt.uuid::text)"),
        CLIENT_ADDRESS("MIN(itt.source_address)"),
        CLIENT_MAC("MIN(itt.source_mac)"),
        SERVER_ADDRESS("MIN(itt.destination_address)"),
        SERVER_MAC("MIN(itt.destination_mac)"),
        SERVER_PORT("MIN(itt.destination_port)"),
        TYPE("tunnel_type"),
        TRANSPORT("transport"),
        CONNECTION_STATUS("connection_status"),
        TUNNELED_BYTES("tunneled_bytes"),
        ESTABLISHED_AT("established_at"),
        TERMINATED_AT("terminated_at"),
        MOST_RECENT_SEGMENT_TIME("most_recent_segment_time"),
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
        private final String columnPrefix;

        Side(String alias, String columnPrefix) {
            this.alias = alias;
            this.columnPrefix = columnPrefix;
        }

    }

    private static final String TIME_RANGE_WITHIN =
            "itt.most_recent_segment_time >= :tr_from AND itt.most_recent_segment_time <= :tr_to ";

    private static final String TIME_RANGE_OVERLAPS =
            "itt.most_recent_segment_time >= :tr_from AND itt.established_at <= :tr_to ";

    private static final String TUNNEL_ENTRY_COLUMNS =
            "SELECT itt.session_key, " +
                    "MIN(itt.transport) AS transport, " +
                    "MIN(itt.tunnel_type) AS tunnel_type, " +
                    "MAX(itt.details::text) AS details, " +
                    "MIN(itt.connection_status) AS connection_status, " +
                    "MAX(itt.tunneled_bytes) AS tunneled_bytes, " +
                    addressColumns(Side.CLIENT) +
                    addressColumns(Side.SERVER) +
                    "MIN(itt.established_at) AS established_at, " +
                    "MAX(itt.terminated_at) AS terminated_at, " +
                    "MAX(itt.most_recent_segment_time) AS most_recent_segment_time, " +
                    "(EXTRACT(EPOCH FROM " +
                    "(MAX(itt.most_recent_segment_time) - MIN(itt.established_at))) * 1000" +
                    ") AS duration_ms ";

    private final NzymeNode nzyme;

    public Ipv6Transition(Ethernet ethernet) {
        this.nzyme = ethernet.getNzyme();
    }

    public long countAllTunnels(TimeRange timeRange, Filters filters, List<UUID> taps) {
        if (taps.isEmpty()) {
            return 0;
        }

        FilterSqlFragment filterFragment = FilterSql.generate(filters, new Ipv6TransitionFilters());

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT COUNT(*) FROM (" +
                                "SELECT itt.session_key FROM ipv6_transition_tunnels AS itt " +
                                "WHERE " + TIME_RANGE_WITHIN +
                                "AND itt.tap_uuid IN (<taps>) " + filterFragment.whereSql() + " " +
                                "GROUP BY itt.session_key HAVING 1=1 " + filterFragment.havingSql() + " " +
                                ") AS tunnels")
                        .bindList("taps", taps)
                        .bindMap(filterFragment.bindings())
                        .bind("tr_from", timeRange.from())
                        .bind("tr_to", timeRange.to())
                        .mapTo(Long.class)
                        .one()
        );
    }

    public List<Ipv6TransitionTunnelEntry> findAllTunnels(TimeRange timeRange,
                                               Filters filters,
                                               OrderColumn orderColumn,
                                               OrderDirection orderDirection,
                                               int limit,
                                               int offset,
                                               List<UUID> taps) {
        if (taps.isEmpty()) {
            return Collections.emptyList();
        }

        FilterSqlFragment filterFragment = FilterSql.generate(filters, new Ipv6TransitionFilters());

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery(TUNNEL_ENTRY_COLUMNS +
                                "FROM ipv6_transition_tunnels AS itt " +
                                "WHERE " + TIME_RANGE_WITHIN +
                                "AND itt.tap_uuid IN (<taps>) " + filterFragment.whereSql() + " " +
                                "GROUP BY itt.session_key HAVING 1=1 " + filterFragment.havingSql() + " " +
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
                        .mapTo(Ipv6TransitionTunnelEntry.class)
                        .list()
        );
    }

    public Optional<Ipv6TransitionTunnelEntry> findTunnel(String sessionKey, List<UUID> taps) {
        if (taps.isEmpty()) {
            return Optional.empty();
        }

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery(TUNNEL_ENTRY_COLUMNS +
                                "FROM ipv6_transition_tunnels AS itt " +
                                "WHERE itt.session_key = :session_key AND itt.tap_uuid IN (<taps>) " +
                                "GROUP BY itt.session_key")
                        .bindList("taps", taps)
                        .bind("session_key", sessionKey)
                        .mapTo(Ipv6TransitionTunnelEntry.class)
                        .findOne()
        );
    }

    public Optional<Ipv6TransitionTunnelEntry> findTunnelOfL4Session(L4Type l4Type,
                                                          String sessionKey,
                                                          DateTime sessionStartTime,
                                                          List<UUID> taps) {
        if (taps.isEmpty()) {
            return Optional.empty();
        }

        Optional<Ipv6TransitionTunnelEntry> direct = findTunnel(sessionKey, taps);
        if (direct.isPresent()) {
            return direct;
        }

        Optional<L4Session> session = nzyme.getEthernet().l4().findSession(l4Type, sessionStartTime, sessionKey, taps);
        if (session.isEmpty()) {
            return Optional.empty();
        }

        String untimedSessionKey = Tools.buildUntimedL4Key(
                session.get().source().address(),
                session.get().destination().address(),
                session.get().source().port(),
                session.get().destination().port()
        );

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery(TUNNEL_ENTRY_COLUMNS +
                                "FROM ipv6_transition_tunnels AS itt " +
                                "WHERE itt.untimed_session_key = :untimed_session_key AND itt.transport = :transport " +
                                "AND itt.established_at <= :start_time_to " +
                                "AND itt.most_recent_segment_time >= :start_time_from " +
                                "AND itt.tap_uuid IN (<taps>) " +
                                "GROUP BY itt.session_key ORDER BY MAX(itt.most_recent_segment_time) DESC LIMIT 1")
                        .bindList("taps", taps)
                        .bind("untimed_session_key", untimedSessionKey)
                        .bind("transport", l4Type.toString())
                        .bind("start_time_from", sessionStartTime.minusMinutes(1))
                        .bind("start_time_to", sessionStartTime.plusMinutes(1))
                        .mapTo(Ipv6TransitionTunnelEntry.class)
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

        FilterSqlFragment filterFragment = FilterSql.generate(filters, new Ipv6TransitionFilters());

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("WITH buckets AS (" +
                                "SELECT generate_series(" +
                                "date_trunc(:date_trunc, :tr_from::timestamptz), " +
                                "date_trunc(:date_trunc, :tr_to::timestamptz), " +
                                "make_interval(secs => :bucket_size_s)" +
                                ") AS bucket" +
                                "), " +
                                "tun AS (" + tunnelAggregateSelect(filterFragment, TIME_RANGE_OVERLAPS) + ") " +
                                "SELECT b.bucket AS bucket, COUNT(tun.session_key) AS value " +
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

        FilterSqlFragment filterFragment = FilterSql.generate(filters, new Ipv6TransitionFilters());

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT COUNT(DISTINCT tun." + side.columnPrefix + "_address) FROM (" +
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

        FilterSqlFragment filterFragment = FilterSql.generate(filters, new Ipv6TransitionFilters());

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("WITH tun AS (" +
                                tunnelAggregateSelect(filterFragment, TIME_RANGE_WITHIN) + ") " +
                                "SELECT " + keyAddressColumns(side) +
                                "COUNT(*) AS value1, " +
                                "COALESCE(SUM(tun.tunneled_bytes), 0)::bigint AS value2 " +
                                "FROM tun " +
                                "GROUP BY tun." + side.columnPrefix + "_address " +
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
        return "SELECT itt.session_key AS session_key, " +
                addressColumns(Side.CLIENT) +
                addressColumns(Side.SERVER) +
                "MAX(itt.tunneled_bytes) AS tunneled_bytes, " +
                "MIN(itt.established_at) AS established_at, " +
                "MAX(itt.most_recent_segment_time) AS most_recent_segment_time " +
                "FROM ipv6_transition_tunnels AS itt " +
                "WHERE " + timeRangeCondition +
                "AND itt.tap_uuid IN (<taps>) " + filterFragment.whereSql() + " " +
                "GROUP BY itt.session_key HAVING 1=1 " + filterFragment.havingSql() + " ";
    }

    private static String addressColumns(Side side) {
        String c = "itt." + side.columnPrefix;
        String a = side.columnPrefix;

        return "MIN(" + c + "_address) AS " + a + "_address, " +
                "MIN(" + c + "_mac) AS " + a + "_mac, " +
                "MIN(" + c + "_port) AS " + a + "_port, " +
                "MIN(" + c + "_address_geo_asn_number) AS " + a + "_address_geo_asn_number, " +
                "MIN(" + c + "_address_geo_asn_name) AS " + a + "_address_geo_asn_name, " +
                "MIN(" + c + "_address_geo_asn_domain) AS " + a + "_address_geo_asn_domain, " +
                "MIN(" + c + "_address_geo_city) AS " + a + "_address_geo_city, " +
                "MIN(" + c + "_address_geo_country_code) AS " + a + "_address_geo_country_code, " +
                "MIN(" + c + "_address_geo_latitude) AS " + a + "_address_geo_latitude, " +
                "MIN(" + c + "_address_geo_longitude) AS " + a + "_address_geo_longitude, " +
                "BOOL_OR(" + c + "_address_is_site_local) AS " + a + "_address_is_site_local, " +
                "BOOL_OR(" + c + "_address_is_loopback) AS " + a + "_address_is_loopback, " +
                "BOOL_OR(" + c + "_address_is_multicast) AS " + a + "_address_is_multicast, ";
    }

    private static String keyAddressColumns(Side side) {
        String t = "tun." + side.columnPrefix;

        return "host(" + t + "_address) AS key, " +
                "host(" + t + "_address) AS key_address, " +
                "MAX(" + t + "_mac) AS key_mac, " +
                "MAX(" + t + "_port) AS key_port, " +
                "MAX(" + t + "_address_geo_asn_number) AS key_address_geo_asn_number, " +
                "MAX(" + t + "_address_geo_asn_name) AS key_address_geo_asn_name, " +
                "MAX(" + t + "_address_geo_asn_domain) AS key_address_geo_asn_domain, " +
                "MAX(" + t + "_address_geo_city) AS key_address_geo_city, " +
                "MAX(" + t + "_address_geo_country_code) AS key_address_geo_country_code, " +
                "MAX(" + t + "_address_geo_latitude) AS key_address_geo_latitude, " +
                "MAX(" + t + "_address_geo_longitude) AS key_address_geo_longitude, " +
                "BOOL_OR(" + t + "_address_is_site_local) AS key_address_is_site_local, " +
                "BOOL_OR(" + t + "_address_is_loopback) AS key_address_is_loopback, " +
                "BOOL_OR(" + t + "_address_is_multicast) AS key_address_is_multicast, ";
    }

}
