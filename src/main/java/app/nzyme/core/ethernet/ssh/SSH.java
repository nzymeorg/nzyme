package app.nzyme.core.ethernet.ssh;

import app.nzyme.core.NzymeNode;
import app.nzyme.core.database.OrderDirection;
import app.nzyme.core.database.generic.L4AddressDataAddressNumberNumberAggregationResult;
import app.nzyme.core.database.generic.StringNumberNumberAggregationResult;
import app.nzyme.core.database.generic.ThreeColumnWithKeyHistogramOrderColumn;
import app.nzyme.core.ethernet.Ethernet;
import app.nzyme.core.ethernet.ssh.db.SSHSessionEntry;
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

public class SSH {

    public enum OrderColumn {

        SESSION_ID("MIN(ssh.uuid)"),
        CLIENT_ADDRESS("MIN(tcp.source_address)"),
        CLIENT_MAC("MIN(tcp.source_mac)"),
        CLIENT_TYPE("MIN(client_version_software) || MIN(client_version_version) || MIN(client_version_comments)"),
        SERVER_ADDRESS("MIN(tcp.destination_address)"),
        SERVER_MAC("MIN(tcp.destination_mac)"),
        SERVER_TYPE("MIN(server_version_software) || MIN(server_version_version) || MIN(server_version_comments)"),
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

    private static final String FROM_SSH_WITH_TCP =
            "FROM ssh_sessions AS ssh " +
                    "LEFT JOIN l4_sessions AS tcp ON tcp.session_key = ssh.tcp_session_key " +
                    "AND tcp.l4_type = 'TCP' AND tcp.tap_uuid = ssh.tap_uuid " +
                    "AND tcp.start_time >= (ssh.established_at - interval '1 minute') " +
                    "AND tcp.start_time <= (ssh.established_at + interval '1 minute') ";

    private static final String TIME_RANGE_WITHIN =
            "ssh.most_recent_segment_time >= :tr_from AND ssh.most_recent_segment_time <= :tr_to ";

    private static final String TIME_RANGE_OVERLAPS =
            "ssh.most_recent_segment_time >= :tr_from AND ssh.established_at <= :tr_to ";

    private static final String SESSION_ENTRY_COLUMNS =
            "SELECT ssh.tcp_session_key, " +
                    "MIN(ssh.client_version_version) AS client_version_version, " +
                    "MIN(ssh.client_version_software) AS client_version_software, " +
                    "MIN(ssh.client_version_comments) AS client_version_comments, " +
                    "MIN(ssh.server_version_version) AS server_version_version, " +
                    "MIN(ssh.server_version_software) AS server_version_software, " +
                    "MIN(ssh.server_version_comments) AS server_version_comments, " +
                    "MIN(ssh.connection_status) AS connection_status, " +
                    "MAX(ssh.tunneled_bytes) AS tunneled_bytes, " +
                    "MIN(ssh.established_at) AS established_at, " +
                    "MAX(ssh.terminated_at) AS terminated_at, " +
                    "MAX(ssh.most_recent_segment_time) AS most_recent_segment_time, " +
                    "(EXTRACT(EPOCH FROM " +
                    "(MAX(ssh.most_recent_segment_time) - MIN(ssh.established_at))) * 1000" +
                    ") AS duration_ms ";

    private static final String BYTES_VALUE = "COALESCE(SUM(sess.tunneled_bytes), 0)::bigint";

    private final NzymeNode nzyme;

    public SSH(Ethernet ethernet) {
        this.nzyme = ethernet.getNzyme();
    }

    public long countAllSessions(TimeRange timeRange, Filters filters, List<UUID> taps) {
        if (taps.isEmpty()) {
            return 0;
        }

        FilterSqlFragment filterFragment = FilterSql.generate(filters, new SSHFilters());

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT COUNT(*) FROM (" +
                                "SELECT ssh.tcp_session_key " +
                                FROM_SSH_WITH_TCP +
                                "WHERE " + TIME_RANGE_WITHIN +
                                "AND ssh.tap_uuid IN (<taps>) " + filterFragment.whereSql() + " " +
                                "GROUP BY ssh.tcp_session_key HAVING 1=1 " + filterFragment.havingSql() + " " +
                                ") AS sessions")
                        .bindList("taps", taps)
                        .bindMap(filterFragment.bindings())
                        .bind("tr_from", timeRange.from())
                        .bind("tr_to", timeRange.to())
                        .mapTo(Long.class)
                        .one()
        );
    }

    public List<SSHSessionEntry> findAllSessions(TimeRange timeRange,
                                                 Filters filters,
                                                 OrderColumn orderColumn,
                                                 OrderDirection orderDirection,
                                                 int limit,
                                                 int offset,
                                                 List<UUID> taps) {
        if (taps.isEmpty()) {
            return Collections.emptyList();
        }

        FilterSqlFragment filterFragment = FilterSql.generate(filters, new SSHFilters());

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery(SESSION_ENTRY_COLUMNS +
                                FROM_SSH_WITH_TCP +
                                "WHERE " + TIME_RANGE_WITHIN +
                                "AND ssh.tap_uuid IN (<taps>) " + filterFragment.whereSql() + " " +
                                "GROUP BY ssh.tcp_session_key HAVING 1=1 " + filterFragment.havingSql() + " " +
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
                        .mapTo(SSHSessionEntry.class)
                        .list()
        );
    }

    public Optional<SSHSessionEntry> findSession(String sessionKey, List<UUID> taps) {
        if (taps.isEmpty()) {
            return Optional.empty();
        }

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery(SESSION_ENTRY_COLUMNS +
                                FROM_SSH_WITH_TCP +
                                "WHERE ssh.tcp_session_key = :tcp_session_key AND ssh.tap_uuid IN (<taps>) " +
                                "GROUP BY ssh.tcp_session_key")
                        .bindList("taps", taps)
                        .bind("tcp_session_key", sessionKey)
                        .mapTo(SSHSessionEntry.class)
                        .findOne()
        );
    }

    public List<GenericIntegerHistogramEntry> getActiveSessionsHistogram(TimeRange timeRange,
                                                                         Bucketing.BucketingConfiguration bucketing,
                                                                         Filters filters,
                                                                         List<UUID> taps) {
        if (taps.isEmpty()) {
            return Collections.emptyList();
        }

        FilterSqlFragment filterFragment = FilterSql.generate(filters, new SSHFilters());

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("WITH buckets AS (" +
                                "SELECT generate_series(" +
                                "date_trunc(:date_trunc, :tr_from::timestamptz), " +
                                "date_trunc(:date_trunc, :tr_to::timestamptz), " +
                                "make_interval(secs => :bucket_size_s)" +
                                ") AS bucket" +
                                "), " +
                                "sess AS (" + sessionAggregateSelect(filterFragment, TIME_RANGE_OVERLAPS) + ") " +
                                "SELECT b.bucket AS bucket, COUNT(sess.tcp_session_key) AS value " +
                                "FROM buckets AS b " +
                                "LEFT JOIN sess " +
                                "ON sess.established_at <= b.bucket + make_interval(secs => :bucket_size_s) " +
                                "AND sess.most_recent_segment_time >= b.bucket " +
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
        return findTopAddresses(Side.CLIENT, "COUNT(*)",
                timeRange, filters, limit, offset, orderColumn, orderDirection, taps);
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
        return findTopAddresses(Side.SERVER, "COUNT(*)",
                timeRange, filters, limit, offset, orderColumn, orderDirection, taps);
    }

    public long getTopClientTypesCount(TimeRange timeRange, Filters filters, List<UUID> taps) {
        return countDistinctTypes(Side.CLIENT, timeRange, filters, taps);
    }

    public List<StringNumberNumberAggregationResult> getTopClientTypes(TimeRange timeRange,
                                                                       Filters filters,
                                                                       int limit,
                                                                       int offset,
                                                                       ThreeColumnWithKeyHistogramOrderColumn orderColumn,
                                                                       OrderDirection orderDirection,
                                                                       List<UUID> taps) {
        return findTopTypes(Side.CLIENT, timeRange, filters, limit, offset, orderColumn, orderDirection, taps);
    }

    public long getTopServerTypesCount(TimeRange timeRange, Filters filters, List<UUID> taps) {
        return countDistinctTypes(Side.SERVER, timeRange, filters, taps);
    }

    public List<StringNumberNumberAggregationResult> getTopServerTypes(TimeRange timeRange,
                                                                       Filters filters,
                                                                       int limit,
                                                                       int offset,
                                                                       ThreeColumnWithKeyHistogramOrderColumn orderColumn,
                                                                       OrderDirection orderDirection,
                                                                       List<UUID> taps) {
        return findTopTypes(Side.SERVER, timeRange, filters, limit, offset, orderColumn, orderDirection, taps);
    }

    private long countDistinctAddresses(Side side, TimeRange timeRange, Filters filters, List<UUID> taps) {
        if (taps.isEmpty()) {
            return 0;
        }

        FilterSqlFragment filterFragment = FilterSql.generate(filters, new SSHFilters());

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT COUNT(DISTINCT sess." + side.alias + "_address) FROM (" +
                                sessionAggregateSelect(filterFragment, TIME_RANGE_WITHIN) +
                                ") AS sess")
                        .bindList("taps", taps)
                        .bindMap(filterFragment.bindings())
                        .bind("tr_from", timeRange.from())
                        .bind("tr_to", timeRange.to())
                        .mapTo(Long.class)
                        .one()
        );
    }

    private List<L4AddressDataAddressNumberNumberAggregationResult> findTopAddresses(Side side,
                                                                                     String value1Sql,
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

        FilterSqlFragment filterFragment = FilterSql.generate(filters, new SSHFilters());

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("WITH sess AS (" +
                                sessionAggregateSelect(filterFragment, TIME_RANGE_WITHIN) + ") " +
                                "SELECT " + keyAddressColumns(side) +
                                value1Sql + " AS value1, " +
                                BYTES_VALUE + " AS value2 " +
                                "FROM sess " +
                                "WHERE sess." + side.alias + "_address IS NOT NULL " +
                                "GROUP BY sess." + side.alias + "_address " +
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

    private long countDistinctTypes(Side side, TimeRange timeRange, Filters filters, List<UUID> taps) {
        if (taps.isEmpty()) {
            return 0;
        }

        FilterSqlFragment filterFragment = FilterSql.generate(filters, new SSHFilters());

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT COUNT(DISTINCT " + typeKeyExpression(side) + ") FROM (" +
                                sessionAggregateSelect(filterFragment, TIME_RANGE_WITHIN) +
                                ") AS sess")
                        .bindList("taps", taps)
                        .bindMap(filterFragment.bindings())
                        .bind("tr_from", timeRange.from())
                        .bind("tr_to", timeRange.to())
                        .mapTo(Long.class)
                        .one()
        );
    }

    private List<StringNumberNumberAggregationResult> findTopTypes(Side side,
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

        FilterSqlFragment filterFragment = FilterSql.generate(filters, new SSHFilters());

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("WITH sess AS (" +
                                sessionAggregateSelect(filterFragment, TIME_RANGE_WITHIN) + ") " +
                                "SELECT t.type_key AS key, " +
                                "COUNT(*) AS value1, " +
                                "COALESCE(SUM(t.tunneled_bytes), 0)::bigint AS value2 " +
                                "FROM (SELECT " + typeKeyExpression(side) + " AS type_key, " +
                                "sess.tunneled_bytes FROM sess) AS t " +
                                "WHERE t.type_key IS NOT NULL " +
                                "GROUP BY t.type_key " +
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
                        .mapTo(StringNumberNumberAggregationResult.class)
                        .list()
        );
    }

    private String sessionAggregateSelect(FilterSqlFragment filterFragment, String timeRangeCondition) {
        return "SELECT ssh.tcp_session_key AS tcp_session_key, " +
                addressAttributeColumns(Side.CLIENT) +
                addressAttributeColumns(Side.SERVER) +
                "MIN(ssh.client_version_software) AS client_version_software, " +
                "MIN(ssh.client_version_comments) AS client_version_comments, " +
                "MIN(ssh.server_version_software) AS server_version_software, " +
                "MIN(ssh.server_version_comments) AS server_version_comments, " +
                "MAX(ssh.tunneled_bytes) AS tunneled_bytes, " +
                "MIN(ssh.established_at) AS established_at, " +
                "MAX(ssh.most_recent_segment_time) AS most_recent_segment_time, " +
                "(EXTRACT(EPOCH FROM " +
                "(MAX(ssh.most_recent_segment_time) - MIN(ssh.established_at))) * 1000)::bigint AS duration_ms " +
                FROM_SSH_WITH_TCP +
                "WHERE " + timeRangeCondition +
                "AND ssh.tap_uuid IN (<taps>) " + filterFragment.whereSql() + " " +
                "GROUP BY ssh.tcp_session_key HAVING 1=1 " + filterFragment.havingSql() + " ";
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
        String s = "sess." + side.alias;

        return "host(" + s + "_address) AS key, " +
                "host(" + s + "_address) AS key_address, " +
                "MAX(" + s + "_port) AS key_port, " +
                "MAX(" + s + "_geo_asn_number) AS key_address_geo_asn_number, " +
                "MAX(" + s + "_geo_asn_name) AS key_address_geo_asn_name, " +
                "MAX(" + s + "_geo_asn_domain) AS key_address_geo_asn_domain, " +
                "MAX(" + s + "_geo_city) AS key_address_geo_city, " +
                "MAX(" + s + "_geo_country_code) AS key_address_geo_country_code, " +
                "MAX(" + s + "_geo_latitude) AS key_address_geo_latitude, " +
                "MAX(" + s + "_geo_longitude) AS key_address_geo_longitude, " +
                "BOOL_OR(" + s + "_is_site_local) AS key_address_is_site_local, " +
                "BOOL_OR(" + s + "_is_loopback) AS key_address_is_loopback, " +
                "BOOL_OR(" + s + "_is_multicast) AS key_address_is_multicast, ";
    }

    private static String typeKeyExpression(Side side) {
        String software = "NULLIF(sess." + side.alias + "_version_software, '')";
        String comments = "NULLIF(sess." + side.alias + "_version_comments, '')";

        return "(" + software + " || COALESCE(' (' || " + comments + " || ')', ''))";
    }

}