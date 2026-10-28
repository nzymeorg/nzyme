package app.nzyme.core.ethernet.vpn;

import app.nzyme.core.NzymeNode;
import app.nzyme.core.database.OrderDirection;
import app.nzyme.core.database.generic.L4AddressDataAddressNumberNumberAggregationResult;
import app.nzyme.core.database.generic.ThreeColumnWithKeyHistogramOrderColumn;
import app.nzyme.core.ethernet.Ethernet;
import app.nzyme.core.ethernet.L4Type;
import app.nzyme.core.ethernet.l4.db.L4Session;
import app.nzyme.core.ethernet.vpn.db.VpnTunnelEntry;
import app.nzyme.core.shared.db.GenericIntegerHistogramEntry;
import app.nzyme.core.util.Bucketing;
import app.nzyme.core.util.TimeRange;
import app.nzyme.core.util.Tools;
import app.nzyme.core.util.filters.FilterSql;
import app.nzyme.core.util.filters.FilterSqlFragment;
import app.nzyme.core.util.filters.Filters;
import app.nzyme.plugin.RegistryKey;
import org.joda.time.DateTime;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class VPN {

    public enum OrderColumn {

        TUNNEL_ID("MIN(vpn.uuid)"),
        CLIENT_ADDRESS("MIN(vpn.source_address)"),
        CLIENT_MAC("MIN(vpn.source_mac)"),
        SERVER_ADDRESS("MIN(vpn.destination_address)"),
        SERVER_MAC("MIN(vpn.destination_mac)"),
        SERVER_PORT("MIN(vpn.destination_port)"),
        TYPE("vpn_type"),
        PROVIDER("provider"),
        TRANSPORT("l4_type"),
        HANDSHAKE_STATUS("handshake_status"),
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
            "vpn.most_recent_segment_time >= :tr_from AND vpn.most_recent_segment_time <= :tr_to ";

    private static final String TIME_RANGE_OVERLAPS =
            "vpn.most_recent_segment_time >= :tr_from AND vpn.established_at <= :tr_to ";

    private static final String TUNNEL_ENTRY_COLUMNS =
            "SELECT vpn.session_key, " +
                    "MIN(vpn.l4_type) AS l4_type, " +
                    "MIN(vpn.vpn_type) AS vpn_type, " +
                    "MIN(vpn.handshake_status) AS handshake_status, " +
                    "MAX(vpn.message_types) AS message_types, " +
                    "MIN(vpn.provider) AS provider, " +
                    "MIN(vpn.server_name) AS server_name, " +
                    "MAX(vpn.details::text) AS details, " +
                    "MIN(vpn.connection_status) AS connection_status, " +
                    "MAX(vpn.tunneled_bytes) AS tunneled_bytes, " +
                    addressColumns(Side.CLIENT) +
                    addressColumns(Side.SERVER) +
                    "MIN(vpn.established_at) AS established_at, " +
                    "MAX(vpn.terminated_at) AS terminated_at, " +
                    "MAX(vpn.most_recent_segment_time) AS most_recent_segment_time, " +
                    "(EXTRACT(EPOCH FROM " +
                    "(MAX(vpn.most_recent_segment_time) - MIN(vpn.established_at))) * 1000" +
                    ") AS duration_ms ";

    private final NzymeNode nzyme;

    public VPN(Ethernet ethernet) {
        this.nzyme = ethernet.getNzyme();
    }

    public boolean excludeTailscaleByDefault(UUID organizationId, UUID tenantId) {
        return booleanValue(VpnRegistryKeys.EXCLUDE_TAILSCALE_BY_DEFAULT, organizationId, tenantId);
    }

    public String stringValue(RegistryKey key, UUID organizationId, UUID tenantId) {
        return nzyme.getDatabaseCoreRegistry().getValue(key.key(), organizationId, tenantId)
                .orElse(key.defaultValue().orElseThrow());
    }

    public boolean booleanValue(RegistryKey key, UUID organizationId, UUID tenantId) {
        return Boolean.parseBoolean(stringValue(key, organizationId, tenantId));
    }

    public long countAllTunnels(TimeRange timeRange, Filters filters, List<UUID> taps) {
        if (taps.isEmpty()) {
            return 0;
        }

        FilterSqlFragment filterFragment = FilterSql.generate(filters, new VPNFilters());

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT COUNT(*) FROM (" +
                                "SELECT vpn.session_key FROM vpn_tunnels AS vpn " +
                                "WHERE " + TIME_RANGE_WITHIN +
                                "AND vpn.tap_uuid IN (<taps>) " + filterFragment.whereSql() + " " +
                                "GROUP BY vpn.session_key HAVING 1=1 " + filterFragment.havingSql() + " " +
                                ") AS tunnels")
                        .bindList("taps", taps)
                        .bindMap(filterFragment.bindings())
                        .bind("tr_from", timeRange.from())
                        .bind("tr_to", timeRange.to())
                        .mapTo(Long.class)
                        .one()
        );
    }

    public List<VpnTunnelEntry> findAllTunnels(TimeRange timeRange,
                                               Filters filters,
                                               OrderColumn orderColumn,
                                               OrderDirection orderDirection,
                                               int limit,
                                               int offset,
                                               List<UUID> taps) {
        if (taps.isEmpty()) {
            return Collections.emptyList();
        }

        FilterSqlFragment filterFragment = FilterSql.generate(filters, new VPNFilters());

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery(TUNNEL_ENTRY_COLUMNS +
                                "FROM vpn_tunnels AS vpn " +
                                "WHERE " + TIME_RANGE_WITHIN +
                                "AND vpn.tap_uuid IN (<taps>) " + filterFragment.whereSql() + " " +
                                "GROUP BY vpn.session_key HAVING 1=1 " + filterFragment.havingSql() + " " +
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
                        .mapTo(VpnTunnelEntry.class)
                        .list()
        );
    }

    public Optional<VpnTunnelEntry> findTunnel(String sessionKey, List<UUID> taps) {
        if (taps.isEmpty()) {
            return Optional.empty();
        }

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery(TUNNEL_ENTRY_COLUMNS +
                                "FROM vpn_tunnels AS vpn " +
                                "WHERE vpn.session_key = :session_key AND vpn.tap_uuid IN (<taps>) " +
                                "GROUP BY vpn.session_key")
                        .bindList("taps", taps)
                        .bind("session_key", sessionKey)
                        .mapTo(VpnTunnelEntry.class)
                        .findOne()
        );
    }

    public Optional<VpnTunnelEntry> findTunnelOfL4Session(L4Type l4Type,
                                                          String sessionKey,
                                                          DateTime sessionStartTime,
                                                          List<UUID> taps) {
        if (taps.isEmpty()) {
            return Optional.empty();
        }

        Optional<VpnTunnelEntry> direct = findTunnel(sessionKey, taps);
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
                                "FROM vpn_tunnels AS vpn " +
                                "WHERE vpn.untimed_session_key = :untimed_session_key AND vpn.l4_type = :l4_type " +
                                "AND vpn.established_at <= :start_time_to " +
                                "AND vpn.most_recent_segment_time >= :start_time_from " +
                                "AND vpn.tap_uuid IN (<taps>) " +
                                "GROUP BY vpn.session_key ORDER BY MAX(vpn.most_recent_segment_time) DESC LIMIT 1")
                        .bindList("taps", taps)
                        .bind("untimed_session_key", untimedSessionKey)
                        .bind("l4_type", l4Type.toString())
                        .bind("start_time_from", sessionStartTime.minusMinutes(1))
                        .bind("start_time_to", sessionStartTime.plusMinutes(1))
                        .mapTo(VpnTunnelEntry.class)
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

        FilterSqlFragment filterFragment = FilterSql.generate(filters, new VPNFilters());

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

        FilterSqlFragment filterFragment = FilterSql.generate(filters, new VPNFilters());

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

        FilterSqlFragment filterFragment = FilterSql.generate(filters, new VPNFilters());

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
        return "SELECT vpn.session_key AS session_key, " +
                addressColumns(Side.CLIENT) +
                addressColumns(Side.SERVER) +
                "MAX(vpn.tunneled_bytes) AS tunneled_bytes, " +
                "MIN(vpn.established_at) AS established_at, " +
                "MAX(vpn.most_recent_segment_time) AS most_recent_segment_time " +
                "FROM vpn_tunnels AS vpn " +
                "WHERE " + timeRangeCondition +
                "AND vpn.tap_uuid IN (<taps>) " + filterFragment.whereSql() + " " +
                "GROUP BY vpn.session_key HAVING 1=1 " + filterFragment.havingSql() + " ";
    }

    private static String addressColumns(Side side) {
        String c = "vpn." + side.columnPrefix;
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
