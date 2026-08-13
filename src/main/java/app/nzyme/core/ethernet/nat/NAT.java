package app.nzyme.core.ethernet.nat;

import app.nzyme.core.NzymeNode;
import app.nzyme.core.database.OrderDirection;
import app.nzyme.core.database.generic.StringStringNumberAggregationResult;
import app.nzyme.core.ethernet.Ethernet;
import app.nzyme.core.ethernet.nat.db.NATTraversalDiscoveryEntry;
import app.nzyme.core.ethernet.nat.db.NATTraversalDiscoveryHistogramBucket;
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
                handle.createQuery("SELECT COUNT(*) FROM (SELECT s.destination AS key, " +
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

}