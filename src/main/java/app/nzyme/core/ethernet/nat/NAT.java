package app.nzyme.core.ethernet.nat;

import app.nzyme.core.NzymeNode;
import app.nzyme.core.database.OrderDirection;
import app.nzyme.core.database.generic.StringStringNumberAggregationResult;
import app.nzyme.core.ethernet.Ethernet;
import app.nzyme.core.ethernet.nat.db.NATTraversalDiscoveryEntry;
import app.nzyme.core.ethernet.nat.db.NATTraversalDiscoveryHistogramBucket;
import app.nzyme.core.ethernet.nat.db.STUNNegotiationEntry;
import app.nzyme.core.ethernet.nat.db.STUNNegotiationFilters;
import app.nzyme.core.util.Bucketing;
import app.nzyme.core.util.TimeRange;
import app.nzyme.core.util.filters.FilterSql;
import app.nzyme.core.util.filters.FilterSqlFragment;
import app.nzyme.core.util.filters.Filters;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class NAT {

    public enum DiscoveryOrderColumn {

        STATUS("status"),
        SOURCE_MAC("source_mac"),
        SOURCE_ADDRESS("source_address"),
        DESTINATION_ADDRESS("destination_address"),
        MAPPED_ADDRESSES("mapped_addresses"),
        INITIATED_AT("first_seen");

        private final String columnName;

        DiscoveryOrderColumn(String columnName) {
            this.columnName = columnName;
        }

        public String getColumnName() {
            return columnName;
        }

    }

    public enum NegotiationOrderColumn {

        IS_ACTIVE("is_active"),
        SUCCESSFUL("successful"),
        SOURCE_MAC("source_mac"),
        SOURCE_ADDRESS("source_address"),
        DESTINATION_MAC("destination_mac"),
        DESTINATION_ADDRESS("destination_address"),
        BYTES("bytes_exchanged"),
        LAST_ACTIVITY("last_activity"),
        INITIATED_AT("first_seen");

        private final String columnName;

        NegotiationOrderColumn(String columnName) {
            this.columnName = columnName;
        }

        public String getColumnName() {
            return columnName;
        }

    }

    private final NzymeNode nzyme;

    public NAT(Ethernet ethernet) {
        this.nzyme = ethernet.getNzyme();
    }

    public long countAllDiscoveries(TimeRange timeRange,
                                    Filters filters,
                                    List<UUID> taps) {
        if (taps.isEmpty()) {
            return 0;
        }

        FilterSqlFragment filterFragment = FilterSql.generate(filters, new NATTraversalDiscoveryFilters());

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT COUNT(*) FROM (" +
                                "SELECT 1 " +
                                "FROM nat_traversal_discoveries AS d " +
                                "LEFT JOIN l4_sessions AS s ON s.session_key = d.l4_session_key " +
                                "AND s.start_time >= d.first_seen - INTERVAL '10 seconds' " +
                                "AND s.start_time <= d.first_seen + INTERVAL '10 seconds' " +
                                "AND s.l4_type = UPPER(d.transport) AND d.tap_uuid = s.tap_uuid " +
                                "LEFT JOIN LATERAL jsonb_array_elements(" +
                                "CASE WHEN jsonb_typeof(d.mapped_addresses) = 'array' THEN d.mapped_addresses " +
                                "ELSE '[]'::jsonb END) AS m(elem) ON true " +
                                "WHERE d.most_recent_segment_time >= :tr_from " +
                                "AND d.most_recent_segment_time <= :tr_to " +
                                "AND d.tap_uuid IN (<taps>)" + filterFragment.whereSql() +
                                "GROUP BY d.l4_session_key HAVING 1=1 " + filterFragment.havingSql() +
                                ") x")
                        .bindList("taps", taps)
                        .bindMap(filterFragment.bindings())
                        .bind("tr_from", timeRange.from())
                        .bind("tr_to", timeRange.to())
                        .mapTo(Long.class)
                        .one()
        );
    }

    public List<NATTraversalDiscoveryEntry> findAllDiscoveries(TimeRange timeRange,
                                                               Filters filters,
                                                               DiscoveryOrderColumn orderColumn,
                                                               OrderDirection orderDirection,
                                                               int limit,
                                                               int offset,
                                                               List<UUID> taps) {
        if (taps.isEmpty()) {
            return Collections.emptyList();
        }

        FilterSqlFragment filterFragment = FilterSql.generate(filters, new NATTraversalDiscoveryFilters());

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT MAX(d.l4_session_key) AS session_key, " +
                                "UPPER(MAX(d.transport)) AS transport, MAX(d.status) AS status, " +
                                "MAX(d.most_recent_segment_time) AS most_recent_segment_time, " +
                                "MIN(d.first_seen) AS first_seen, MAX(s.source_mac) AS source_mac, " +
                                "MAX(s.end_time) AS terminated_at, " +
                                "MAX(s.source_address) AS source_address, MAX(s.source_port) AS source_port, " +
                                "MAX(s.source_address_geo_asn_number) AS source_address_geo_asn_number, " +
                                "MAX(s.source_address_geo_asn_name) AS source_address_geo_asn_name, " +
                                "MAX(s.source_address_geo_asn_domain) AS source_address_geo_asn_domain, " +
                                "MAX(s.source_address_geo_city) AS source_address_geo_city, " +
                                "MAX(s.source_address_geo_country_code) AS source_address_geo_country_code, " +
                                "MAX(s.source_address_geo_latitude) AS source_address_geo_latitude, " +
                                "MAX(s.source_address_geo_longitude) AS source_address_geo_longitude, " +
                                "BOOL_OR(s.source_address_is_site_local) AS source_address_is_site_local, " +
                                "BOOL_OR(s.source_address_is_loopback) AS source_address_is_loopback, " +
                                "BOOL_OR(s.source_address_is_multicast) AS source_address_is_multicast, " +
                                "MAX(s.destination_mac) AS destination_mac, " +
                                "MAX(s.destination_address) AS destination_address, " +
                                "MAX(s.destination_port) AS destination_port, " +
                                "MAX(s.destination_address_geo_asn_number) AS destination_address_geo_asn_number, " +
                                "MAX(s.destination_address_geo_asn_name) AS destination_address_geo_asn_name, " +
                                "MAX(s.destination_address_geo_asn_domain) AS destination_address_geo_asn_domain, " +
                                "MAX(s.destination_address_geo_city) AS destination_address_geo_city, " +
                                "MAX(s.destination_address_geo_country_code) AS destination_address_geo_country_code, " +
                                "MAX(s.destination_address_geo_latitude) AS destination_address_geo_latitude, " +
                                "MAX(s.destination_address_geo_longitude) AS destination_address_geo_longitude, " +
                                "BOOL_OR(s.destination_address_is_site_local) AS destination_address_is_site_local, " +
                                "BOOL_OR(s.destination_address_is_loopback) AS destination_address_is_loopback, " +
                                "BOOL_OR(s.destination_address_is_multicast) AS destination_address_is_multicast, " +
                                "COALESCE(jsonb_agg(DISTINCT m.elem) FILTER (WHERE m.elem IS NOT NULL), " +
                                "'[]'::jsonb) AS mapped_addresses " +
                                "FROM nat_traversal_discoveries AS d " +
                                "LEFT JOIN l4_sessions AS s ON s.session_key = d.l4_session_key " +
                                "AND s.start_time >= d.first_seen - INTERVAL '10 seconds' " +
                                "AND s.start_time <= d.first_seen + INTERVAL '10 seconds' " +
                                "AND s.l4_type = UPPER(d.transport) AND d.tap_uuid = s.tap_uuid " +
                                "LEFT JOIN LATERAL jsonb_array_elements(" +
                                "CASE WHEN jsonb_typeof(d.mapped_addresses) = 'array' THEN d.mapped_addresses " +
                                "ELSE '[]'::jsonb END) AS m(elem) ON true " +
                                "WHERE d.most_recent_segment_time >= :tr_from " +
                                "AND d.most_recent_segment_time <= :tr_to " +
                                "AND d.tap_uuid IN (<taps>)" + filterFragment.whereSql() +
                                "GROUP BY d.l4_session_key HAVING 1=1 " + filterFragment.havingSql() +
                                "ORDER BY <order_column> <order_direction> LIMIT :limit OFFSET :offset")
                        .bindList("taps", taps)
                        .bindMap(filterFragment.bindings())
                        .bind("tr_from", timeRange.from())
                        .bind("tr_to", timeRange.to())
                        .bind("limit", limit)
                        .bind("offset", offset)
                        .define("order_column", orderColumn.getColumnName())
                        .define("order_direction", orderDirection)
                        .mapTo(NATTraversalDiscoveryEntry.class)
                        .list()
        );
    }

    public Optional<NATTraversalDiscoveryEntry> findOneDiscovery(String sessionKey, List<UUID> taps) {
        if (taps.isEmpty()) {
            return Optional.empty();
        }

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT MAX(d.l4_session_key) AS session_key, " +
                                "UPPER(MAX(d.transport)) AS transport, MAX(d.status) AS status, " +
                                "MAX(d.most_recent_segment_time) AS most_recent_segment_time, " +
                                "MIN(d.first_seen) AS first_seen, MAX(s.source_mac) AS source_mac, " +
                                "MAX(s.end_time) AS terminated_at, " +
                                "MAX(s.source_address) AS source_address, MAX(s.source_port) AS source_port, " +
                                "MAX(s.source_address_geo_asn_number) AS source_address_geo_asn_number, " +
                                "MAX(s.source_address_geo_asn_name) AS source_address_geo_asn_name, " +
                                "MAX(s.source_address_geo_asn_domain) AS source_address_geo_asn_domain, " +
                                "MAX(s.source_address_geo_city) AS source_address_geo_city, " +
                                "MAX(s.source_address_geo_country_code) AS source_address_geo_country_code, " +
                                "MAX(s.source_address_geo_latitude) AS source_address_geo_latitude, " +
                                "MAX(s.source_address_geo_longitude) AS source_address_geo_longitude, " +
                                "BOOL_OR(s.source_address_is_site_local) AS source_address_is_site_local, " +
                                "BOOL_OR(s.source_address_is_loopback) AS source_address_is_loopback, " +
                                "BOOL_OR(s.source_address_is_multicast) AS source_address_is_multicast, " +
                                "MAX(s.destination_mac) AS destination_mac, " +
                                "MAX(s.destination_address) AS destination_address, " +
                                "MAX(s.destination_port) AS destination_port, " +
                                "MAX(s.destination_address_geo_asn_number) AS destination_address_geo_asn_number, " +
                                "MAX(s.destination_address_geo_asn_name) AS destination_address_geo_asn_name, " +
                                "MAX(s.destination_address_geo_asn_domain) AS destination_address_geo_asn_domain, " +
                                "MAX(s.destination_address_geo_city) AS destination_address_geo_city, " +
                                "MAX(s.destination_address_geo_country_code) AS destination_address_geo_country_code, " +
                                "MAX(s.destination_address_geo_latitude) AS destination_address_geo_latitude, " +
                                "MAX(s.destination_address_geo_longitude) AS destination_address_geo_longitude, " +
                                "BOOL_OR(s.destination_address_is_site_local) AS destination_address_is_site_local, " +
                                "BOOL_OR(s.destination_address_is_loopback) AS destination_address_is_loopback, " +
                                "BOOL_OR(s.destination_address_is_multicast) AS destination_address_is_multicast, " +
                                "COALESCE(jsonb_agg(DISTINCT m.elem) FILTER (WHERE m.elem IS NOT NULL), " +
                                "'[]'::jsonb) AS mapped_addresses " +
                                "FROM nat_traversal_discoveries AS d " +
                                "LEFT JOIN l4_sessions AS s ON s.session_key = d.l4_session_key " +
                                "AND s.start_time >= d.first_seen - INTERVAL '10 seconds' " +
                                "AND s.start_time <= d.first_seen + INTERVAL '10 seconds' " +
                                "AND s.l4_type = UPPER(d.transport) AND d.tap_uuid = s.tap_uuid " +
                                "LEFT JOIN LATERAL jsonb_array_elements(" +
                                "CASE WHEN jsonb_typeof(d.mapped_addresses) = 'array' THEN d.mapped_addresses " +
                                "ELSE '[]'::jsonb END) AS m(elem) ON true " +
                                "WHERE d.l4_session_key = :session_key " +
                                "AND d.tap_uuid IN (<taps>) " +
                                "GROUP BY d.l4_session_key")
                        .bindList("taps", taps)
                        .bind("session_key", sessionKey)
                        .mapTo(NATTraversalDiscoveryEntry.class)
                        .findOne()
        );
    }

    public List<NATTraversalDiscoveryHistogramBucket> getTraversalDiscoveryHistogram(TimeRange timeRange,
                                                                                     Bucketing.BucketingConfiguration bucketing,
                                                                                     Filters filters,
                                                                                     List<UUID> taps) {
        if (taps.isEmpty()) {
            return Collections.emptyList();
        }

        FilterSqlFragment filterFragment = FilterSql.generate(filters, new NATTraversalDiscoveryFilters());

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT date_trunc('minute', d.first_seen) AS bucket, " +
                                "COUNT(*) FILTER (WHERE d.status = 'COMPLETE') AS complete_count, " +
                                "COUNT(*) FILTER (WHERE d.status = 'INCOMPLETE') AS incomplete_count, " +
                                "COUNT(*) FILTER (WHERE d.status = 'ERROR') AS error_count " +
                                "FROM nat_traversal_discoveries AS d " +
                                "LEFT JOIN l4_sessions AS s ON s.session_key = d.l4_session_key " +
                                "AND s.start_time >= d.first_seen - INTERVAL '10 seconds' " +
                                "AND s.start_time <= d.first_seen + INTERVAL '10 seconds' " +
                                "AND s.l4_type = UPPER(d.transport) AND d.tap_uuid = s.tap_uuid " +
                                "WHERE d.first_seen >= :tr_from AND d.first_seen <= :tr_to " +
                                "AND d.tap_uuid IN (<taps>) " + filterFragment.whereSql() +
                                "GROUP BY bucket HAVING 1=1 " + filterFragment.havingSql() + " " +
                                "ORDER BY bucket DESC")
                        .bindList("taps", taps)
                        .bindMap(filterFragment.bindings())
                        .bind("date_trunc", bucketing.type().getDateTruncName())
                        .bind("tr_from", timeRange.from())
                        .bind("tr_to", timeRange.to())
                        .mapTo(NATTraversalDiscoveryHistogramBucket.class)
                        .list()
        );
    }

    public long countTraversalDiscoveryTopClientsHistogram(TimeRange timeRange,
                                                           Filters filters,
                                                           List<UUID> taps) {
        if (taps.isEmpty()) {
            return 0;
        }

        FilterSqlFragment filterFragment = FilterSql.generate(filters, new NATTraversalDiscoveryFilters());

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT COUNT(*) FROM (SELECT s.source_address AS key, " +
                                "s.source_mac AS value1, COUNT(*) AS value2 " +
                                "FROM nat_traversal_discoveries AS d " +
                                "LEFT JOIN l4_sessions AS s ON s.session_key = d.l4_session_key " +
                                "AND s.start_time >= d.first_seen - INTERVAL '10 seconds' " +
                                "AND s.start_time <= d.first_seen + INTERVAL '10 seconds' " +
                                "AND s.l4_type = UPPER(d.transport) AND d.tap_uuid = s.tap_uuid " +
                                "WHERE d.first_seen >= :tr_from AND d.first_seen <= :tr_to " +
                                "AND d.tap_uuid IN (<taps>) " + filterFragment.whereSql() +
                                "GROUP BY s.source_address, s.source_mac " +
                                "HAVING 1=1 " + filterFragment.havingSql() + ") AS ignored")
                        .bind("tr_from", timeRange.from())
                        .bind("tr_to", timeRange.to())
                        .bindMap(filterFragment.bindings())
                        .bindList("taps", taps)
                        .mapTo(Long.class)
                        .one()
        );
    }

    public List<StringStringNumberAggregationResult> getTraversalDiscoveryTopClientsHistogram(TimeRange timeRange,
                                                                                              Filters filters,
                                                                                              int limit,
                                                                                              int offset,
                                                                                              List<UUID> taps) {
        if (taps.isEmpty()) {
            return Collections.emptyList();
        }

        FilterSqlFragment filterFragment = FilterSql.generate(filters, new NATTraversalDiscoveryFilters());

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT s.source_address AS key, s.source_mac AS value1, COUNT(*) AS value2 " +
                                "FROM nat_traversal_discoveries AS d " +
                                "LEFT JOIN l4_sessions AS s ON s.session_key = d.l4_session_key " +
                                "AND s.start_time >= d.first_seen - INTERVAL '10 seconds' " +
                                "AND s.start_time <= d.first_seen + INTERVAL '10 seconds' " +
                                "AND s.l4_type = UPPER(d.transport) AND d.tap_uuid = s.tap_uuid " +
                                "WHERE d.first_seen >= :tr_from AND d.first_seen <= :tr_to " +
                                "AND d.tap_uuid IN (<taps>) " + filterFragment.whereSql() +
                                "GROUP BY s.source_address, s.source_mac HAVING 1=1 " + filterFragment.havingSql() +
                                "ORDER BY value2 DESC LIMIT :limit OFFSET :offset")
                        .bind("tr_from", timeRange.from())
                        .bind("tr_to", timeRange.to())
                        .bind("limit", limit)
                        .bind("offset", offset)
                        .bindMap(filterFragment.bindings())
                        .bindList("taps", taps)
                        .mapTo(StringStringNumberAggregationResult.class)
                        .list()
        );
    }


    public long countTraversalDiscoveryTopServersHistogram(TimeRange timeRange,
                                                           Filters filters,
                                                           List<UUID> taps) {
        if (taps.isEmpty()) {
            return 0;
        }

        FilterSqlFragment filterFragment = FilterSql.generate(filters, new NATTraversalDiscoveryFilters());

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT COUNT(*) FROM (SELECT s.destination_address AS key, " +
                                "s.destination_mac AS value1, COUNT(*) AS value2 " +
                                "FROM nat_traversal_discoveries AS d " +
                                "LEFT JOIN l4_sessions AS s ON s.session_key = d.l4_session_key " +
                                "AND s.start_time >= d.first_seen - INTERVAL '10 seconds' " +
                                "AND s.start_time <= d.first_seen + INTERVAL '10 seconds' " +
                                "AND s.l4_type = UPPER(d.transport) AND d.tap_uuid = s.tap_uuid " +
                                "WHERE d.first_seen >= :tr_from AND d.first_seen <= :tr_to " +
                                "AND d.tap_uuid IN (<taps>) " + filterFragment.whereSql() +
                                "GROUP BY s.destination_address, s.destination_mac " +
                                "HAVING 1=1 " + filterFragment.havingSql() + ") AS ignored")
                        .bind("tr_from", timeRange.from())
                        .bind("tr_to", timeRange.to())
                        .bindMap(filterFragment.bindings())
                        .bindList("taps", taps)
                        .mapTo(Long.class)
                        .one()
        );
    }

    public List<StringStringNumberAggregationResult> getTraversalDiscoveryTopServersHistogram(TimeRange timeRange,
                                                                                              Filters filters,
                                                                                              int limit,
                                                                                              int offset,
                                                                                              List<UUID> taps) {
        if (taps.isEmpty()) {
            return Collections.emptyList();
        }

        FilterSqlFragment filterFragment = FilterSql.generate(filters, new NATTraversalDiscoveryFilters());

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT s.destination_address AS key, s.destination_mac AS value1, COUNT(*) AS value2 " +
                                "FROM nat_traversal_discoveries AS d " +
                                "LEFT JOIN l4_sessions AS s ON s.session_key = d.l4_session_key " +
                                "AND s.start_time >= d.first_seen - INTERVAL '10 seconds' " +
                                "AND s.start_time <= d.first_seen + INTERVAL '10 seconds' " +
                                "AND s.l4_type = UPPER(d.transport) AND d.tap_uuid = s.tap_uuid " +
                                "WHERE d.first_seen >= :tr_from AND d.first_seen <= :tr_to " +
                                "AND d.tap_uuid IN (<taps>) " + filterFragment.whereSql() +
                                "GROUP BY s.destination_address, s.destination_mac HAVING 1=1 " + filterFragment.havingSql() +
                                "ORDER BY value2 DESC LIMIT :limit OFFSET :offset")
                        .bind("tr_from", timeRange.from())
                        .bind("tr_to", timeRange.to())
                        .bind("limit", limit)
                        .bind("offset", offset)
                        .bindMap(filterFragment.bindings())
                        .bindList("taps", taps)
                        .mapTo(StringStringNumberAggregationResult.class)
                        .list()
        );
    }

    public long countAllNegotiations(TimeRange timeRange, Filters filters, List<UUID> taps) {
        if (taps.isEmpty()) {
            return 0;
        }
        FilterSqlFragment filterFragment = FilterSql.generate(filters, new STUNNegotiationFilters());

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT COUNT(*) FROM (" +
                                "SELECT 1 FROM nat_stun_negotiation_flows AS n " +
                                "LEFT JOIN l4_sessions AS s ON s.session_key = n.l4_session_key " +
                                "AND s.start_time >= n.first_seen - INTERVAL '10 seconds' " +
                                "AND s.start_time <= n.first_seen + INTERVAL '10 seconds' " +
                                "AND s.l4_type = UPPER(n.transport) AND n.tap_uuid = s.tap_uuid " +
                                "WHERE n.last_activity >= :tr_from AND n.last_activity <= :tr_to " +
                                "AND n.tap_uuid IN (<taps>)" + filterFragment.whereSql() +
                                " GROUP BY n.negotiation_key HAVING 1=1 " + filterFragment.havingSql() +
                                ") AS ignored")
                        .bindList("taps", taps)
                        .bindMap(filterFragment.bindings())
                        .bind("tr_from", timeRange.from())
                        .bind("tr_to", timeRange.to())
                        .mapTo(Long.class)
                        .one()
        );
    }

    public List<STUNNegotiationEntry> findAllNegotiations(TimeRange timeRange,
                                                          Filters filters,
                                                          NegotiationOrderColumn orderColumn,
                                                          OrderDirection orderDirection,
                                                          int limit, int offset,
                                                          List<UUID> taps) {
        if (taps.isEmpty()) {
            return Collections.emptyList();
        }
        FilterSqlFragment filterFragment = FilterSql.generate(filters, new STUNNegotiationFilters());

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT MAX(n.negotiation_key) AS negotiation_key, " +
                                "MAX(n.negotiation_key_sha256) AS negotiation_key_sha256, " +
                                "UPPER(MAX(n.transport)) AS transport, BOOL_OR(n.successful) AS successful, " +
                                "BOOL_OR(n.is_turn) AS is_turn, MAX(n.first_seen) AS first_seen, " +
                                "MAX(n.last_activity) AS last_activity," +
                                "(MAX(s.most_recent_segment_time) >= NOW() - INTERVAL '60 seconds') AS is_active, " +
                                "MAX(s.bytes_rx_count+s.bytes_tx_count) AS bytes_exchanged, " +
                                "MAX(s.source_port) AS source_port, MAX(s.source_mac) AS source_mac, " +
                                "MAX(s.source_address) AS source_address, MAX(s.source_port) AS source_port, " +
                                "MAX(s.source_address_geo_asn_number) AS source_address_geo_asn_number, " +
                                "MAX(s.source_address_geo_asn_name) AS source_address_geo_asn_name, " +
                                "MAX(s.source_address_geo_asn_domain) AS source_address_geo_asn_domain, " +
                                "MAX(s.source_address_geo_city) AS source_address_geo_city, " +
                                "MAX(s.source_address_geo_country_code) AS source_address_geo_country_code, " +
                                "MAX(s.source_address_geo_latitude) AS source_address_geo_latitude, " +
                                "MAX(s.source_address_geo_longitude) AS source_address_geo_longitude, " +
                                "BOOL_OR(s.source_address_is_site_local) AS source_address_is_site_local, " +
                                "BOOL_OR(s.source_address_is_loopback) AS source_address_is_loopback, " +
                                "BOOL_OR(s.source_address_is_multicast) AS source_address_is_multicast, " +
                                "MAX(s.destination_mac) FILTER (WHERE n.successful) AS destination_mac, " +
                                "MAX(s.destination_address) FILTER (WHERE n.successful) AS destination_address, " +
                                "MAX(s.destination_port) FILTER (WHERE n.successful) AS destination_port, " +
                                "MAX(s.destination_address_geo_asn_number) FILTER (WHERE n.successful) AS destination_address_geo_asn_number, " +
                                "MAX(s.destination_address_geo_asn_name) FILTER (WHERE n.successful) AS destination_address_geo_asn_name, " +
                                "MAX(s.destination_address_geo_asn_domain) FILTER (WHERE n.successful) AS destination_address_geo_asn_domain, " +
                                "MAX(s.destination_address_geo_city) FILTER (WHERE n.successful) AS destination_address_geo_city, " +
                                "MAX(s.destination_address_geo_country_code) FILTER (WHERE n.successful) AS destination_address_geo_country_code, " +
                                "MAX(s.destination_address_geo_latitude) FILTER (WHERE n.successful) AS destination_address_geo_latitude, " +
                                "MAX(s.destination_address_geo_longitude) FILTER (WHERE n.successful) AS destination_address_geo_longitude, " +
                                "BOOL_OR(s.destination_address_is_site_local) FILTER (WHERE n.successful) AS destination_address_is_site_local, " +
                                "BOOL_OR(s.destination_address_is_loopback) FILTER (WHERE n.successful) AS destination_address_is_loopback, " +
                                "BOOL_OR(s.destination_address_is_multicast) FILTER (WHERE n.successful) AS destination_address_is_multicast, " +
                                "COALESCE(jsonb_agg(DISTINCT me.elem) FILTER (WHERE me.elem IS NOT NULL), '[]'::jsonb) AS mapped_addresses, " +
                                "COALESCE(jsonb_agg(DISTINCT pe.elem) FILTER (WHERE pe.elem IS NOT NULL), '[]'::jsonb) AS peer_addresses, " +
                                "COALESCE(jsonb_agg(DISTINCT re.elem) FILTER (WHERE re.elem IS NOT NULL), '[]'::jsonb) AS relayed_addresses " +
                                "FROM nat_stun_negotiation_flows AS n " +
                                "LEFT JOIN l4_sessions AS s ON s.session_key = n.l4_session_key " +
                                "AND s.start_time >= n.first_seen - INTERVAL '10 seconds' " +
                                "AND s.start_time <= n.first_seen + INTERVAL '10 seconds' " +
                                "AND s.l4_type = UPPER(n.transport) AND n.tap_uuid = s.tap_uuid " +
                                "LEFT JOIN LATERAL jsonb_array_elements(CASE WHEN jsonb_typeof(n.mapped_addresses) = 'array' THEN n.mapped_addresses ELSE '[]'::jsonb END) AS me(elem) ON true " +
                                "LEFT JOIN LATERAL jsonb_array_elements(CASE WHEN jsonb_typeof(n.peer_addresses) = 'array' THEN n.peer_addresses ELSE '[]'::jsonb END) AS pe(elem) ON true " +
                                "LEFT JOIN LATERAL jsonb_array_elements(CASE WHEN jsonb_typeof(n.relayed_addresses) = 'array' THEN n.relayed_addresses ELSE '[]'::jsonb END) AS re(elem) ON true " +
                                "WHERE n.last_activity >= :tr_from AND n.last_activity <= :tr_to " +
                                "AND n.tap_uuid IN (<taps>)" + filterFragment.whereSql() +
                                " GROUP BY n.negotiation_key HAVING 1=1 " + filterFragment.havingSql() +
                                " ORDER BY <order_column> <order_direction> LIMIT :limit OFFSET :offset")
                        .bindList("taps", taps)
                        .bindMap(filterFragment.bindings())
                        .bind("tr_from", timeRange.from())
                        .bind("tr_to", timeRange.to())
                        .bind("limit", limit)
                        .bind("offset", offset)
                        .define("order_column", orderColumn.getColumnName())
                        .define("order_direction", orderDirection)
                        .mapTo(STUNNegotiationEntry.class)
                        .list()
        );
    }

    public Optional<STUNNegotiationEntry> findOneNegotiation(String negotiationKey, List<UUID> taps) {
        if (taps.isEmpty()) {
            return Optional.empty();
        }

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT MAX(n.negotiation_key) AS negotiation_key, " +
                                "UPPER(MAX(n.transport)) AS transport, " +
                                "BOOL_OR(n.successful) AS successful, " +
                                "BOOL_OR(n.is_turn) AS is_turn, " +
                                "MAX(n.first_seen) AS first_seen, " +
                                "MAX(n.last_activity) AS last_activity, " +
                                "(MAX(n.last_activity) >= NOW() - INTERVAL '60 seconds') AS is_active, " +
                                "MAX(s.source_mac) AS source_mac, " +
                                "MAX(s.source_address) AS source_address, MAX(s.source_port) AS source_port, " +
                                "MAX(s.source_address_geo_asn_number) AS source_address_geo_asn_number, " +
                                "MAX(s.source_address_geo_asn_name) AS source_address_geo_asn_name, " +
                                "MAX(s.source_address_geo_asn_domain) AS source_address_geo_asn_domain, " +
                                "MAX(s.source_address_geo_city) AS source_address_geo_city, " +
                                "MAX(s.source_address_geo_country_code) AS source_address_geo_country_code, " +
                                "MAX(s.source_address_geo_latitude) AS source_address_geo_latitude, " +
                                "MAX(s.source_address_geo_longitude) AS source_address_geo_longitude, " +
                                "BOOL_OR(s.source_address_is_site_local) AS source_address_is_site_local, " +
                                "BOOL_OR(s.source_address_is_loopback) AS source_address_is_loopback, " +
                                "BOOL_OR(s.source_address_is_multicast) AS source_address_is_multicast, " +
                                "MAX(s.destination_mac) FILTER (WHERE n.successful) AS destination_mac, " +
                                "MAX(s.destination_address) FILTER (WHERE n.successful) AS destination_address, " +
                                "MAX(s.destination_port) FILTER (WHERE n.successful) AS destination_port, " +
                                "MAX(s.destination_address_geo_asn_number) FILTER (WHERE n.successful) AS destination_address_geo_asn_number, " +
                                "MAX(s.destination_address_geo_asn_name) FILTER (WHERE n.successful) AS destination_address_geo_asn_name, " +
                                "MAX(s.destination_address_geo_asn_domain) FILTER (WHERE n.successful) AS destination_address_geo_asn_domain, " +
                                "MAX(s.destination_address_geo_city) FILTER (WHERE n.successful) AS destination_address_geo_city, " +
                                "MAX(s.destination_address_geo_country_code) FILTER (WHERE n.successful) AS destination_address_geo_country_code, " +
                                "MAX(s.destination_address_geo_latitude) FILTER (WHERE n.successful) AS destination_address_geo_latitude, " +
                                "MAX(s.destination_address_geo_longitude) FILTER (WHERE n.successful) AS destination_address_geo_longitude, " +
                                "BOOL_OR(s.destination_address_is_site_local) FILTER (WHERE n.successful) AS destination_address_is_site_local, " +
                                "BOOL_OR(s.destination_address_is_loopback) FILTER (WHERE n.successful) AS destination_address_is_loopback, " +
                                "BOOL_OR(s.destination_address_is_multicast) FILTER (WHERE n.successful) AS destination_address_is_multicast, " +
                                "COALESCE(jsonb_agg(DISTINCT me.elem) FILTER (WHERE me.elem IS NOT NULL), '[]'::jsonb) AS mapped_addresses, " +
                                "COALESCE(jsonb_agg(DISTINCT pe.elem) FILTER (WHERE pe.elem IS NOT NULL), '[]'::jsonb) AS peer_addresses, " +
                                "COALESCE(jsonb_agg(DISTINCT re.elem) FILTER (WHERE re.elem IS NOT NULL), '[]'::jsonb) AS relayed_addresses " +
                                "FROM nat_stun_negotiation_flows AS n " +
                                "LEFT JOIN l4_sessions AS s ON s.session_key = n.l4_session_key " +
                                "AND s.start_time >= n.first_seen - INTERVAL '10 seconds' " +
                                "AND s.start_time <= n.first_seen + INTERVAL '10 seconds' " +
                                "AND s.l4_type = UPPER(n.transport) AND n.tap_uuid = s.tap_uuid " +
                                "LEFT JOIN LATERAL jsonb_array_elements(CASE WHEN jsonb_typeof(n.mapped_addresses) = 'array' THEN n.mapped_addresses ELSE '[]'::jsonb END) AS me(elem) ON true " +
                                "LEFT JOIN LATERAL jsonb_array_elements(CASE WHEN jsonb_typeof(n.peer_addresses) = 'array' THEN n.peer_addresses ELSE '[]'::jsonb END) AS pe(elem) ON true " +
                                "LEFT JOIN LATERAL jsonb_array_elements(CASE WHEN jsonb_typeof(n.relayed_addresses) = 'array' THEN n.relayed_addresses ELSE '[]'::jsonb END) AS re(elem) ON true " +
                                "WHERE n.negotiation_key = :negotiation_key AND n.tap_uuid IN (<taps>) " +
                                "GROUP BY n.negotiation_key")
                        .bindList("taps", taps)
                        .bind("negotiation_key", negotiationKey)
                        .mapTo(STUNNegotiationEntry.class)
                        .findOne()
        );
    }

    public List<STUNNegotiationEntry> findFlowsOfNegotiation(String negotiationKey, List<UUID> taps) {
        if (taps.isEmpty()) {
            return Collections.emptyList();
        }

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT n.negotiation_key, UPPER(n.transport) AS transport, " +
                                "n.successful, n.is_turn, n.first_seen, n.last_activity, " +
                                "(n.last_activity >= NOW() - INTERVAL '60 seconds') AS is_active, " +
                                "s.source_mac, s.source_address, s.source_port, " +
                                "s.source_address_geo_asn_number, s.source_address_geo_asn_name, " +
                                "s.source_address_geo_asn_domain, s.source_address_geo_city, " +
                                "s.source_address_geo_country_code, s.source_address_geo_latitude, " +
                                "s.source_address_geo_longitude, s.source_address_is_site_local, " +
                                "s.source_address_is_loopback, s.source_address_is_multicast, " +
                                "s.destination_mac, s.destination_address, s.destination_port, " +
                                "s.destination_address_geo_asn_number, s.destination_address_geo_asn_name, " +
                                "s.destination_address_geo_asn_domain, s.destination_address_geo_city, " +
                                "s.destination_address_geo_country_code, s.destination_address_geo_latitude, " +
                                "s.destination_address_geo_longitude, s.destination_address_is_site_local, " +
                                "s.destination_address_is_loopback, s.destination_address_is_multicast, " +
                                "n.mapped_addresses, n.peer_addresses, n.relayed_addresses " +
                                "FROM nat_stun_negotiation_flows AS n " +
                                "LEFT JOIN l4_sessions AS s ON s.session_key = n.l4_session_key " +
                                "AND s.start_time >= n.first_seen - INTERVAL '10 seconds' " +
                                "AND s.start_time <= n.first_seen + INTERVAL '10 seconds' " +
                                "AND s.l4_type = UPPER(n.transport) AND n.tap_uuid = s.tap_uuid " +
                                "WHERE n.negotiation_key = :negotiation_key AND n.tap_uuid IN (<taps>) " +
                                "ORDER BY n.first_seen ASC")
                        .bindList("taps", taps)
                        .bind("negotiation_key", negotiationKey)
                        .mapTo(STUNNegotiationEntry.class)
                        .list()
        );
    }

}