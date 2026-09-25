package app.nzyme.core.ethernet.nat;

import app.nzyme.core.NzymeNode;
import app.nzyme.core.database.OrderDirection;
import app.nzyme.core.database.generic.*;
import app.nzyme.core.ethernet.Ethernet;
import app.nzyme.core.ethernet.nat.db.NATTraversalDiscoveryEntry;
import app.nzyme.core.ethernet.nat.db.NATTraversalDiscoveryHistogramBucket;
import app.nzyme.core.ethernet.nat.db.STUNNegotiationEntry;
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
        IS_TURN("is_turn"),
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

    public long countTraversalDiscoveryTopClientsHistogram(TimeRange timeRange, Filters filters, List<UUID> taps) {
        if (taps.isEmpty()) {
            return 0;
        }
        FilterSqlFragment filterFragment = FilterSql.generate(filters, new NATTraversalDiscoveryFilters());

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT COUNT(*) FROM (" +
                                "SELECT source_address FROM (" +
                                discoverySessionEndpointsSelect(filterFragment) +
                                ") AS sess WHERE source_address IS NOT NULL " +
                                "GROUP BY source_address" +
                                ") AS distinct_clients")
                        .bindList("taps", taps)
                        .bindMap(filterFragment.bindings())
                        .bind("tr_from", timeRange.from())
                        .bind("tr_to", timeRange.to())
                        .mapTo(Long.class)
                        .one()
        );
    }

    public List<L4AddressDataAddressNumberNumberAggregationResult> getTraversalDiscoveryTopClientsHistogram(TimeRange timeRange,
                                                                                                            Filters filters,
                                                                                                            int limit, int offset,
                                                                                                            ThreeColumnWithKeyHistogramOrderColumn orderColumn,
                                                                                                            OrderDirection orderDirection,
                                                                                                            List<UUID> taps) {
        if (taps.isEmpty()) {
            return Collections.emptyList();
        }
        FilterSqlFragment filterFragment = FilterSql.generate(filters, new NATTraversalDiscoveryFilters());

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("WITH sess AS (" + discoverySessionEndpointsSelectWithAttrs(filterFragment) + ") " +
                                "SELECT host(sess.source_address) AS key, " +
                                "host(sess.source_address) AS key_address, " +
                                "MAX(sess.source_mac) AS key_mac, MAX(sess.source_port) AS key_port, " +
                                "MAX(sess.source_geo_asn_number) AS key_address_geo_asn_number, " +
                                "MAX(sess.source_geo_asn_name) AS key_address_geo_asn_name, " +
                                "MAX(sess.source_geo_asn_domain) AS key_address_geo_asn_domain, " +
                                "MAX(sess.source_geo_city) AS key_address_geo_city, " +
                                "MAX(sess.source_geo_country_code) AS key_address_geo_country_code, " +
                                "MAX(sess.source_geo_latitude) AS key_address_geo_latitude, " +
                                "MAX(sess.source_geo_longitude) AS key_address_geo_longitude, " +
                                "BOOL_OR(sess.source_is_site_local) AS key_address_is_site_local, " +
                                "BOOL_OR(sess.source_is_loopback) AS key_address_is_loopback, " +
                                "BOOL_OR(sess.source_is_multicast) AS key_address_is_multicast, " +
                                "COUNT(*) FILTER (WHERE sess.status = 'COMPLETE') AS value1, " +
                                "COUNT(*) FILTER (WHERE sess.status = 'INCOMPLETE') AS value2 " +
                                "FROM sess " +
                                "WHERE sess.source_address IS NOT NULL " +
                                "GROUP BY sess.source_address " +
                                "ORDER BY <order_column> <order_direction> LIMIT :limit OFFSET :offset")
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

    public long countTraversalDiscoveryTopServersHistogram(TimeRange timeRange, Filters filters, List<UUID> taps) {
        if (taps.isEmpty()) {
            return 0;
        }
        FilterSqlFragment filterFragment = FilterSql.generate(filters, new NATTraversalDiscoveryFilters());

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT COUNT(*) FROM (" +
                                "SELECT destination_address FROM (" +
                                discoverySessionEndpointsSelect(filterFragment) +
                                ") AS sess WHERE destination_address IS NOT NULL " +
                                "GROUP BY destination_address" +
                                ") AS distinct_servers")
                        .bindList("taps", taps)
                        .bindMap(filterFragment.bindings())
                        .bind("tr_from", timeRange.from())
                        .bind("tr_to", timeRange.to())
                        .mapTo(Long.class)
                        .one()
        );
    }

    public List<L4AddressDataAddressNumberNumberAggregationResult> getTraversalDiscoveryTopServersHistogram(TimeRange timeRange,
                                                                                                            Filters filters,
                                                                                                            int limit, int offset,
                                                                                                            ThreeColumnWithKeyHistogramOrderColumn orderColumn,
                                                                                                            OrderDirection orderDirection,
                                                                                                            List<UUID> taps) {
        if (taps.isEmpty()) {
            return Collections.emptyList();
        }
        FilterSqlFragment filterFragment = FilterSql.generate(filters, new NATTraversalDiscoveryFilters());

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("WITH sess AS (" + discoverySessionEndpointsSelectWithAttrs(filterFragment) + ") " +
                                "SELECT host(sess.destination_address) AS key, " +
                                "host(sess.destination_address) AS key_address, " +
                                "MAX(sess.destination_mac) AS key_mac, MAX(sess.destination_port) AS key_port, " +
                                "MAX(sess.destination_geo_asn_number) AS key_address_geo_asn_number, " +
                                "MAX(sess.destination_geo_asn_name) AS key_address_geo_asn_name, " +
                                "MAX(sess.destination_geo_asn_domain) AS key_address_geo_asn_domain, " +
                                "MAX(sess.destination_geo_city) AS key_address_geo_city, " +
                                "MAX(sess.destination_geo_country_code) AS key_address_geo_country_code, " +
                                "MAX(sess.destination_geo_latitude) AS key_address_geo_latitude, " +
                                "MAX(sess.destination_geo_longitude) AS key_address_geo_longitude, " +
                                "BOOL_OR(sess.destination_is_site_local) AS key_address_is_site_local, " +
                                "BOOL_OR(sess.destination_is_loopback) AS key_address_is_loopback, " +
                                "BOOL_OR(sess.destination_is_multicast) AS key_address_is_multicast, " +
                                "COUNT(*) FILTER (WHERE sess.status = 'COMPLETE') AS value1, " +
                                "COUNT(*) FILTER (WHERE sess.status = 'INCOMPLETE') AS value2 " +
                                "FROM sess " +
                                "WHERE sess.destination_address IS NOT NULL " +
                                "GROUP BY sess.destination_address " +
                                "ORDER BY <order_column> <order_direction> LIMIT :limit OFFSET :offset")
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
                                "COALESCE(MAX(s.source_mac) FILTER (WHERE n.successful), MAX(s.source_mac)) AS source_mac, " +
                                "COALESCE(MAX(s.source_address) FILTER (WHERE n.successful), MAX(s.source_address)) AS source_address, " +
                                "COALESCE(MAX(s.source_port) FILTER (WHERE n.successful), MAX(s.source_port)) AS source_port, " +
                                "COALESCE(MAX(s.source_address_geo_asn_number) FILTER (WHERE n.successful), MAX(s.source_address_geo_asn_number)) AS source_address_geo_asn_number, " +
                                "COALESCE(MAX(s.source_address_geo_asn_name) FILTER (WHERE n.successful), MAX(s.source_address_geo_asn_name)) AS source_address_geo_asn_name, " +
                                "COALESCE(MAX(s.source_address_geo_asn_domain) FILTER (WHERE n.successful), MAX(s.source_address_geo_asn_domain)) AS source_address_geo_asn_domain, " +
                                "COALESCE(MAX(s.source_address_geo_city) FILTER (WHERE n.successful), MAX(s.source_address_geo_city)) AS source_address_geo_city, " +
                                "COALESCE(MAX(s.source_address_geo_country_code) FILTER (WHERE n.successful), MAX(s.source_address_geo_country_code)) AS source_address_geo_country_code, " +
                                "COALESCE(MAX(s.source_address_geo_latitude) FILTER (WHERE n.successful), MAX(s.source_address_geo_latitude)) AS source_address_geo_latitude, " +
                                "COALESCE(MAX(s.source_address_geo_longitude) FILTER (WHERE n.successful), MAX(s.source_address_geo_longitude)) AS source_address_geo_longitude, " +
                                "COALESCE(BOOL_OR(s.source_address_is_site_local) FILTER (WHERE n.successful), BOOL_OR(s.source_address_is_site_local)) AS source_address_is_site_local, " +
                                "COALESCE(BOOL_OR(s.source_address_is_loopback) FILTER (WHERE n.successful), BOOL_OR(s.source_address_is_loopback)) AS source_address_is_loopback, " +
                                "COALESCE(BOOL_OR(s.source_address_is_multicast) FILTER (WHERE n.successful), BOOL_OR(s.source_address_is_multicast)) AS source_address_is_multicast, " +
                                "COALESCE(MAX(s.destination_mac) FILTER (WHERE n.successful), MAX(s.destination_mac)) AS destination_mac, " +
                                "COALESCE(MAX(s.destination_address) FILTER (WHERE n.successful), MAX(s.destination_address)) AS destination_address, " +
                                "COALESCE(MAX(s.destination_port) FILTER (WHERE n.successful), MAX(s.destination_port)) AS destination_port, " +
                                "COALESCE(MAX(s.destination_address_geo_asn_number) FILTER (WHERE n.successful), MAX(s.destination_address_geo_asn_number)) AS destination_address_geo_asn_number, " +
                                "COALESCE(MAX(s.destination_address_geo_asn_name) FILTER (WHERE n.successful), MAX(s.destination_address_geo_asn_name)) AS destination_address_geo_asn_name, " +
                                "COALESCE(MAX(s.destination_address_geo_asn_domain) FILTER (WHERE n.successful), MAX(s.destination_address_geo_asn_domain)) AS destination_address_geo_asn_domain, " +
                                "COALESCE(MAX(s.destination_address_geo_city) FILTER (WHERE n.successful), MAX(s.destination_address_geo_city)) AS destination_address_geo_city, " +
                                "COALESCE(MAX(s.destination_address_geo_country_code) FILTER (WHERE n.successful), MAX(s.destination_address_geo_country_code)) AS destination_address_geo_country_code, " +
                                "COALESCE(MAX(s.destination_address_geo_latitude) FILTER (WHERE n.successful), MAX(s.destination_address_geo_latitude)) AS destination_address_geo_latitude, " +
                                "COALESCE(MAX(s.destination_address_geo_longitude) FILTER (WHERE n.successful), MAX(s.destination_address_geo_longitude)) AS destination_address_geo_longitude, " +
                                "COALESCE(BOOL_OR(s.destination_address_is_site_local) FILTER (WHERE n.successful), BOOL_OR(s.destination_address_is_site_local)) AS destination_address_is_site_local, " +
                                "COALESCE(BOOL_OR(s.destination_address_is_loopback) FILTER (WHERE n.successful), BOOL_OR(s.destination_address_is_loopback)) AS destination_address_is_loopback, " +
                                "COALESCE(BOOL_OR(s.destination_address_is_multicast) FILTER (WHERE n.successful), BOOL_OR(s.destination_address_is_multicast)) AS destination_address_is_multicast, " +
                                "COALESCE(jsonb_agg(DISTINCT me.elem) FILTER (WHERE me.elem IS NOT NULL), '[]'::jsonb) AS mapped_addresses, " +
                                "COALESCE(jsonb_agg(DISTINCT pe.elem) FILTER (WHERE pe.elem IS NOT NULL), '[]'::jsonb) AS peer_addresses, " +
                                "COALESCE(jsonb_agg(DISTINCT re.elem) FILTER (WHERE re.elem IS NOT NULL), '[]'::jsonb) AS relayed_addresses, " +
                                "COALESCE(jsonb_agg(DISTINCT te.elem) FILTER (WHERE te.elem IS NOT NULL), '[]'::jsonb) AS tags " +
                                "FROM nat_stun_negotiation_flows AS n " +
                                "LEFT JOIN l4_sessions AS s ON s.session_key = n.l4_session_key " +
                                "AND s.start_time >= n.first_seen - INTERVAL '10 seconds' " +
                                "AND s.start_time <= n.first_seen + INTERVAL '10 seconds' " +
                                "AND s.l4_type = UPPER(n.transport) AND n.tap_uuid = s.tap_uuid " +
                                "LEFT JOIN LATERAL jsonb_array_elements(CASE WHEN jsonb_typeof(n.mapped_addresses) = 'array' THEN n.mapped_addresses ELSE '[]'::jsonb END) AS me(elem) ON true " +
                                "LEFT JOIN LATERAL jsonb_array_elements(CASE WHEN jsonb_typeof(n.peer_addresses) = 'array' THEN n.peer_addresses ELSE '[]'::jsonb END) AS pe(elem) ON true " +
                                "LEFT JOIN LATERAL jsonb_array_elements(CASE WHEN jsonb_typeof(n.relayed_addresses) = 'array' THEN n.relayed_addresses ELSE '[]'::jsonb END) AS re(elem) ON true " +
                                "LEFT JOIN LATERAL jsonb_array_elements(CASE WHEN jsonb_typeof(s.tags) = 'array' THEN s.tags ELSE '[]'::jsonb END) AS te(elem) ON true " +
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

    public Optional<STUNNegotiationEntry> findOneNegotiation(String negotiationKeySha256, List<UUID> taps) {
        if (taps.isEmpty()) {
            return Optional.empty();
        }

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT MAX(n.negotiation_key) AS negotiation_key, " +
                                "MAX(n.negotiation_key_sha256) AS negotiation_key_sha256, " +
                                "UPPER(MAX(n.transport)) AS transport, " +
                                "BOOL_OR(n.successful) AS successful, " +
                                "BOOL_OR(n.is_turn) AS is_turn, " +
                                "MAX(n.first_seen) AS first_seen, " +
                                "MAX(n.last_activity) AS last_activity, " +
                                "(MAX(n.last_activity) >= NOW() - INTERVAL '60 seconds') AS is_active, " +
                                "MAX(s.bytes_rx_count+s.bytes_tx_count) AS bytes_exchanged, " +
                                "COALESCE(MAX(s.source_mac) FILTER (WHERE n.successful), MAX(s.source_mac)) AS source_mac, " +
                                "COALESCE(MAX(s.source_address) FILTER (WHERE n.successful), MAX(s.source_address)) AS source_address, " +
                                "COALESCE(MAX(s.source_port) FILTER (WHERE n.successful), MAX(s.source_port)) AS source_port, " +
                                "COALESCE(MAX(s.source_address_geo_asn_number) FILTER (WHERE n.successful), MAX(s.source_address_geo_asn_number)) AS source_address_geo_asn_number, " +
                                "COALESCE(MAX(s.source_address_geo_asn_name) FILTER (WHERE n.successful), MAX(s.source_address_geo_asn_name)) AS source_address_geo_asn_name, " +
                                "COALESCE(MAX(s.source_address_geo_asn_domain) FILTER (WHERE n.successful), MAX(s.source_address_geo_asn_domain)) AS source_address_geo_asn_domain, " +
                                "COALESCE(MAX(s.source_address_geo_city) FILTER (WHERE n.successful), MAX(s.source_address_geo_city)) AS source_address_geo_city, " +
                                "COALESCE(MAX(s.source_address_geo_country_code) FILTER (WHERE n.successful), MAX(s.source_address_geo_country_code)) AS source_address_geo_country_code, " +
                                "COALESCE(MAX(s.source_address_geo_latitude) FILTER (WHERE n.successful), MAX(s.source_address_geo_latitude)) AS source_address_geo_latitude, " +
                                "COALESCE(MAX(s.source_address_geo_longitude) FILTER (WHERE n.successful), MAX(s.source_address_geo_longitude)) AS source_address_geo_longitude, " +
                                "COALESCE(BOOL_OR(s.source_address_is_site_local) FILTER (WHERE n.successful), BOOL_OR(s.source_address_is_site_local)) AS source_address_is_site_local, " +
                                "COALESCE(BOOL_OR(s.source_address_is_loopback) FILTER (WHERE n.successful), BOOL_OR(s.source_address_is_loopback)) AS source_address_is_loopback, " +
                                "COALESCE(BOOL_OR(s.source_address_is_multicast) FILTER (WHERE n.successful), BOOL_OR(s.source_address_is_multicast)) AS source_address_is_multicast, " +
                                "COALESCE(MAX(s.destination_mac) FILTER (WHERE n.successful), MAX(s.destination_mac)) AS destination_mac, " +
                                "COALESCE(MAX(s.destination_address) FILTER (WHERE n.successful), MAX(s.destination_address)) AS destination_address, " +
                                "COALESCE(MAX(s.destination_port) FILTER (WHERE n.successful), MAX(s.destination_port)) AS destination_port, " +
                                "COALESCE(MAX(s.destination_address_geo_asn_number) FILTER (WHERE n.successful), MAX(s.destination_address_geo_asn_number)) AS destination_address_geo_asn_number, " +
                                "COALESCE(MAX(s.destination_address_geo_asn_name) FILTER (WHERE n.successful), MAX(s.destination_address_geo_asn_name)) AS destination_address_geo_asn_name, " +
                                "COALESCE(MAX(s.destination_address_geo_asn_domain) FILTER (WHERE n.successful), MAX(s.destination_address_geo_asn_domain)) AS destination_address_geo_asn_domain, " +
                                "COALESCE(MAX(s.destination_address_geo_city) FILTER (WHERE n.successful), MAX(s.destination_address_geo_city)) AS destination_address_geo_city, " +
                                "COALESCE(MAX(s.destination_address_geo_country_code) FILTER (WHERE n.successful), MAX(s.destination_address_geo_country_code)) AS destination_address_geo_country_code, " +
                                "COALESCE(MAX(s.destination_address_geo_latitude) FILTER (WHERE n.successful), MAX(s.destination_address_geo_latitude)) AS destination_address_geo_latitude, " +
                                "COALESCE(MAX(s.destination_address_geo_longitude) FILTER (WHERE n.successful), MAX(s.destination_address_geo_longitude)) AS destination_address_geo_longitude, " +
                                "COALESCE(BOOL_OR(s.destination_address_is_site_local) FILTER (WHERE n.successful), BOOL_OR(s.destination_address_is_site_local)) AS destination_address_is_site_local, " +
                                "COALESCE(BOOL_OR(s.destination_address_is_loopback) FILTER (WHERE n.successful), BOOL_OR(s.destination_address_is_loopback)) AS destination_address_is_loopback, " +
                                "COALESCE(BOOL_OR(s.destination_address_is_multicast) FILTER (WHERE n.successful), BOOL_OR(s.destination_address_is_multicast)) AS destination_address_is_multicast, " +
                                "COALESCE(jsonb_agg(DISTINCT me.elem) FILTER (WHERE me.elem IS NOT NULL), '[]'::jsonb) AS mapped_addresses, " +
                                "COALESCE(jsonb_agg(DISTINCT pe.elem) FILTER (WHERE pe.elem IS NOT NULL), '[]'::jsonb) AS peer_addresses, " +
                                "COALESCE(jsonb_agg(DISTINCT re.elem) FILTER (WHERE re.elem IS NOT NULL), '[]'::jsonb) AS relayed_addresses, " +
                                "COALESCE(jsonb_agg(DISTINCT te.elem) FILTER (WHERE te.elem IS NOT NULL), '[]'::jsonb) AS tags " +
                                "FROM nat_stun_negotiation_flows AS n " +
                                "LEFT JOIN l4_sessions AS s ON s.session_key = n.l4_session_key " +
                                "AND s.start_time >= n.first_seen - INTERVAL '10 seconds' " +
                                "AND s.start_time <= n.first_seen + INTERVAL '10 seconds' " +
                                "AND s.l4_type = UPPER(n.transport) AND n.tap_uuid = s.tap_uuid " +
                                "LEFT JOIN LATERAL jsonb_array_elements(CASE WHEN jsonb_typeof(n.mapped_addresses) = 'array' THEN n.mapped_addresses ELSE '[]'::jsonb END) AS me(elem) ON true " +
                                "LEFT JOIN LATERAL jsonb_array_elements(CASE WHEN jsonb_typeof(n.peer_addresses) = 'array' THEN n.peer_addresses ELSE '[]'::jsonb END) AS pe(elem) ON true " +
                                "LEFT JOIN LATERAL jsonb_array_elements(CASE WHEN jsonb_typeof(n.relayed_addresses) = 'array' THEN n.relayed_addresses ELSE '[]'::jsonb END) AS re(elem) ON true " +
                                "LEFT JOIN LATERAL jsonb_array_elements(CASE WHEN jsonb_typeof(s.tags) = 'array' THEN s.tags ELSE '[]'::jsonb END) AS te(elem) ON true " +
                                "WHERE n.negotiation_key_sha256 = :negotiation_key_sha256 AND n.tap_uuid IN (<taps>) " +
                                "GROUP BY n.negotiation_key")
                        .bindList("taps", taps)
                        .bind("negotiation_key_sha256", negotiationKeySha256)
                        .mapTo(STUNNegotiationEntry.class)
                        .findOne()
        );
    }

    public List<STUNNegotiationEntry> findFlowsOfNegotiation(String negotiationKeySha256, List<UUID> taps) {
        if (taps.isEmpty()) {
            return Collections.emptyList();
        }

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT n.negotiation_key, UPPER(n.transport) AS transport, " +
                                "n.successful, n.negotiation_key_sha256, n.is_turn, n.first_seen, n.last_activity, " +
                                "(n.last_activity >= NOW() - INTERVAL '60 seconds') AS is_active, " +
                                "s.bytes_rx_count+s.bytes_tx_count AS bytes_exchanged, " +
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
                                "n.mapped_addresses, n.peer_addresses, n.relayed_addresses, " +
                                "COALESCE(s.tags, '[]'::jsonb) AS tags " +
                                "FROM nat_stun_negotiation_flows AS n " +
                                "LEFT JOIN l4_sessions AS s ON s.session_key = n.l4_session_key " +
                                "AND s.start_time >= n.first_seen - INTERVAL '10 seconds' " +
                                "AND s.start_time <= n.first_seen + INTERVAL '10 seconds' " +
                                "AND s.l4_type = UPPER(n.transport) AND n.tap_uuid = s.tap_uuid " +
                                "WHERE n.negotiation_key_sha256 = :negotiation_key_sha256 AND n.tap_uuid IN (<taps>) " +
                                "ORDER BY n.first_seen ASC")
                        .bindList("taps", taps)
                        .bind("negotiation_key_sha256", negotiationKeySha256)
                        .mapTo(STUNNegotiationEntry.class)
                        .list()
        );
    }

    public List<GenericIntegerHistogramEntry> getActiveNegotiationsHistogram(TimeRange timeRange,
                                                                             Bucketing.BucketingConfiguration bucketing,
                                                                             Filters filters,
                                                                             List<UUID> taps) {
        if (taps.isEmpty()) {
            return Collections.emptyList();
        }

        FilterSqlFragment filterFragment = FilterSql.generate(filters, new STUNNegotiationFilters());

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery(
                                "WITH buckets AS (" +
                                        "SELECT generate_series(" +
                                        "date_trunc(:date_trunc, :tr_from::timestamptz), " +
                                        "date_trunc(:date_trunc, :tr_to::timestamptz), " +
                                        "make_interval(secs => :bucket_size_s)" +
                                        ") AS bucket" +
                                        "), " +
                                        "sessions AS (" +
                                        "SELECT n.negotiation_key, " +
                                        "MIN(n.first_seen) AS session_start, " +
                                        "MAX(n.last_activity) AS session_end " +
                                        "FROM nat_stun_negotiation_flows AS n " +
                                        "LEFT JOIN l4_sessions AS s ON s.session_key = n.l4_session_key " +
                                        "AND s.start_time >= n.first_seen - INTERVAL '10 seconds' " +
                                        "AND s.start_time <= n.first_seen + INTERVAL '10 seconds' " +
                                        "AND s.l4_type = UPPER(n.transport) AND n.tap_uuid = s.tap_uuid " +
                                        "WHERE n.last_activity >= :tr_from AND n.first_seen <= :tr_to " +
                                        "AND n.tap_uuid IN (<taps>)" + filterFragment.whereSql() +
                                        " GROUP BY n.negotiation_key HAVING 1=1 " + filterFragment.havingSql() +
                                        ") " +
                                        "SELECT b.bucket AS bucket, COUNT(sess.negotiation_key) AS value " +
                                        "FROM buckets AS b " +
                                        "LEFT JOIN sessions AS sess " +
                                        "ON sess.session_start <= b.bucket + make_interval(secs => :bucket_size_s) " +
                                        "AND sess.session_end >= b.bucket " +
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

    public long getNegotiationTopServersCount(TimeRange timeRange, Filters filters, List<UUID> taps) {
        if (taps.isEmpty()) {
            return 0;
        }
        FilterSqlFragment filterFragment = FilterSql.generate(filters, new STUNNegotiationFilters());

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT COUNT(*) FROM (" +
                                "SELECT server_address FROM (" +
                                negotiationSessionEndpointsSelect(filterFragment) +
                                ") AS sess WHERE server_address IS NOT NULL " +
                                "GROUP BY server_address" +
                                ") AS distinct_servers")
                        .bindList("taps", taps)
                        .bindMap(filterFragment.bindings())
                        .bind("tr_from", timeRange.from())
                        .bind("tr_to", timeRange.to())
                        .mapTo(Long.class)
                        .one()
        );
    }

    public List<L4AddressDataAddressNumberNumberAggregationResult> getNegotiationTopServers(TimeRange timeRange,
                                                                                            Filters filters,
                                                                                            int limit, int offset,
                                                                                            ThreeColumnWithKeyHistogramOrderColumn orderColumn,
                                                                                            OrderDirection orderDirection,
                                                                                            List<UUID> taps) {
        if (taps.isEmpty()) {
            return Collections.emptyList();
        }
        FilterSqlFragment filterFragment = FilterSql.generate(filters, new STUNNegotiationFilters());

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("WITH sess AS (" + negotiationSessionEndpointsSelectWithAttrs(filterFragment) + ") " +
                                "SELECT host(sess.server_address) AS key, " +
                                "host(sess.server_address) AS key_address, " +
                                "MAX(sess.server_mac) AS key_mac, MAX(sess.server_port) AS key_port, " +
                                "MAX(sess.server_geo_asn_number) AS key_address_geo_asn_number, " +
                                "MAX(sess.server_geo_asn_name) AS key_address_geo_asn_name, " +
                                "MAX(sess.server_geo_asn_domain) AS key_address_geo_asn_domain, " +
                                "MAX(sess.server_geo_city) AS key_address_geo_city, " +
                                "MAX(sess.server_geo_country_code) AS key_address_geo_country_code, " +
                                "MAX(sess.server_geo_latitude) AS key_address_geo_latitude, " +
                                "MAX(sess.server_geo_longitude) AS key_address_geo_longitude, " +
                                "BOOL_OR(sess.server_is_site_local) AS key_address_is_site_local, " +
                                "BOOL_OR(sess.server_is_loopback) AS key_address_is_loopback, " +
                                "BOOL_OR(sess.server_is_multicast) AS key_address_is_multicast, " +
                                "COUNT(*) AS value1, " +
                                "COALESCE(SUM(sess.bytes_exchanged), 0) AS value2 " +
                                "FROM sess " +
                                "WHERE sess.server_address IS NOT NULL " +
                                "GROUP BY sess.server_address " +
                                "ORDER BY <order_column> <order_direction> LIMIT :limit OFFSET :offset")
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

    public long getNegotiationTopClientsCount(TimeRange timeRange, Filters filters, List<UUID> taps) {
        if (taps.isEmpty()) {
            return 0;
        }
        FilterSqlFragment filterFragment = FilterSql.generate(filters, new STUNNegotiationFilters());

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT COUNT(*) FROM (" +
                                "SELECT client_address FROM (" +
                                negotiationSessionEndpointsSelect(filterFragment) +
                                ") AS sess WHERE client_address IS NOT NULL " +
                                "GROUP BY client_address" +
                                ") AS distinct_clients")
                        .bindList("taps", taps)
                        .bindMap(filterFragment.bindings())
                        .bind("tr_from", timeRange.from())
                        .bind("tr_to", timeRange.to())
                        .mapTo(Long.class)
                        .one()
        );
    }

    public List<L4AddressDataAddressNumberNumberAggregationResult> getNegotiationTopClients(TimeRange timeRange,
                                                                                            Filters filters,
                                                                                            int limit, int offset,
                                                                                            ThreeColumnWithKeyHistogramOrderColumn orderColumn,
                                                                                            OrderDirection orderDirection,
                                                                                            List<UUID> taps) {
        if (taps.isEmpty()) {
            return Collections.emptyList();
        }
        FilterSqlFragment filterFragment = FilterSql.generate(filters, new STUNNegotiationFilters());

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("WITH sess AS (" + negotiationSessionEndpointsSelectWithAttrs(filterFragment) + ") " +
                                "SELECT host(sess.client_address) AS key, " +
                                "host(sess.client_address) AS key_address, " +
                                "MAX(sess.client_mac) AS key_mac, MAX(sess.client_port) AS key_port, " +
                                "MAX(sess.client_geo_asn_number) AS key_address_geo_asn_number, " +
                                "MAX(sess.client_geo_asn_name) AS key_address_geo_asn_name, " +
                                "MAX(sess.client_geo_asn_domain) AS key_address_geo_asn_domain, " +
                                "MAX(sess.client_geo_city) AS key_address_geo_city, " +
                                "MAX(sess.client_geo_country_code) AS key_address_geo_country_code, " +
                                "MAX(sess.client_geo_latitude) AS key_address_geo_latitude, " +
                                "MAX(sess.client_geo_longitude) AS key_address_geo_longitude, " +
                                "BOOL_OR(sess.client_is_site_local) AS key_address_is_site_local, " +
                                "BOOL_OR(sess.client_is_loopback) AS key_address_is_loopback, " +
                                "BOOL_OR(sess.client_is_multicast) AS key_address_is_multicast, " +
                                "COUNT(*) AS value1, " +
                                "COALESCE(SUM(sess.bytes_exchanged), 0) AS value2 " +
                                "FROM sess " +
                                "WHERE sess.client_address IS NOT NULL " +
                                "GROUP BY sess.client_address " +
                                "ORDER BY <order_column> <order_direction> LIMIT :limit OFFSET :offset")
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

    private String negotiationSessionEndpointsSelect(FilterSqlFragment filterFragment) {
        return "SELECT n.negotiation_key, " +
                "COALESCE(MAX(s.source_address) FILTER (WHERE n.successful), MAX(s.source_address)) AS client_address, " +
                "COALESCE(MAX(s.destination_address) FILTER (WHERE n.successful), MAX(s.destination_address)) AS server_address, " +
                "MAX(s.bytes_rx_count + s.bytes_tx_count) AS bytes_exchanged " +
                "FROM nat_stun_negotiation_flows AS n " +
                "LEFT JOIN l4_sessions AS s ON s.session_key = n.l4_session_key " +
                "AND s.start_time >= n.first_seen - INTERVAL '10 seconds' " +
                "AND s.start_time <= n.first_seen + INTERVAL '10 seconds' " +
                "AND s.l4_type = UPPER(n.transport) AND n.tap_uuid = s.tap_uuid " +
                "WHERE n.last_activity >= :tr_from AND n.last_activity <= :tr_to " +
                "AND n.tap_uuid IN (<taps>)" + filterFragment.whereSql() +
                " GROUP BY n.negotiation_key HAVING 1=1 " + filterFragment.havingSql();
    }

    private String negotiationSessionEndpointsSelectWithAttrs(FilterSqlFragment filterFragment) {
        return "SELECT n.negotiation_key, " +
                "COALESCE(MAX(s.source_address) FILTER (WHERE n.successful), MAX(s.source_address)) AS client_address, " +
                "COALESCE(MAX(s.source_mac) FILTER (WHERE n.successful), MAX(s.source_mac)) AS client_mac, " +
                "COALESCE(MAX(s.source_port) FILTER (WHERE n.successful), MAX(s.source_port)) AS client_port, " +
                "COALESCE(MAX(s.source_address_geo_asn_number) FILTER (WHERE n.successful), MAX(s.source_address_geo_asn_number)) AS client_geo_asn_number, " +
                "COALESCE(MAX(s.source_address_geo_asn_name) FILTER (WHERE n.successful), MAX(s.source_address_geo_asn_name)) AS client_geo_asn_name, " +
                "COALESCE(MAX(s.source_address_geo_asn_domain) FILTER (WHERE n.successful), MAX(s.source_address_geo_asn_domain)) AS client_geo_asn_domain, " +
                "COALESCE(MAX(s.source_address_geo_city) FILTER (WHERE n.successful), MAX(s.source_address_geo_city)) AS client_geo_city, " +
                "COALESCE(MAX(s.source_address_geo_country_code) FILTER (WHERE n.successful), MAX(s.source_address_geo_country_code)) AS client_geo_country_code, " +
                "COALESCE(MAX(s.source_address_geo_latitude) FILTER (WHERE n.successful), MAX(s.source_address_geo_latitude)) AS client_geo_latitude, " +
                "COALESCE(MAX(s.source_address_geo_longitude) FILTER (WHERE n.successful), MAX(s.source_address_geo_longitude)) AS client_geo_longitude, " +
                "COALESCE(BOOL_OR(s.source_address_is_site_local) FILTER (WHERE n.successful), BOOL_OR(s.source_address_is_site_local)) AS client_is_site_local, " +
                "COALESCE(BOOL_OR(s.source_address_is_loopback) FILTER (WHERE n.successful), BOOL_OR(s.source_address_is_loopback)) AS client_is_loopback, " +
                "COALESCE(BOOL_OR(s.source_address_is_multicast) FILTER (WHERE n.successful), BOOL_OR(s.source_address_is_multicast)) AS client_is_multicast, " +
                "COALESCE(MAX(s.destination_address) FILTER (WHERE n.successful), MAX(s.destination_address)) AS server_address, " +
                "COALESCE(MAX(s.destination_mac) FILTER (WHERE n.successful), MAX(s.destination_mac)) AS server_mac, " +
                "COALESCE(MAX(s.destination_port) FILTER (WHERE n.successful), MAX(s.destination_port)) AS server_port, " +
                "COALESCE(MAX(s.destination_address_geo_asn_number) FILTER (WHERE n.successful), MAX(s.destination_address_geo_asn_number)) AS server_geo_asn_number, " +
                "COALESCE(MAX(s.destination_address_geo_asn_name) FILTER (WHERE n.successful), MAX(s.destination_address_geo_asn_name)) AS server_geo_asn_name, " +
                "COALESCE(MAX(s.destination_address_geo_asn_domain) FILTER (WHERE n.successful), MAX(s.destination_address_geo_asn_domain)) AS server_geo_asn_domain, " +
                "COALESCE(MAX(s.destination_address_geo_city) FILTER (WHERE n.successful), MAX(s.destination_address_geo_city)) AS server_geo_city, " +
                "COALESCE(MAX(s.destination_address_geo_country_code) FILTER (WHERE n.successful), MAX(s.destination_address_geo_country_code)) AS server_geo_country_code, " +
                "COALESCE(MAX(s.destination_address_geo_latitude) FILTER (WHERE n.successful), MAX(s.destination_address_geo_latitude)) AS server_geo_latitude, " +
                "COALESCE(MAX(s.destination_address_geo_longitude) FILTER (WHERE n.successful), MAX(s.destination_address_geo_longitude)) AS server_geo_longitude, " +
                "COALESCE(BOOL_OR(s.destination_address_is_site_local) FILTER (WHERE n.successful), BOOL_OR(s.destination_address_is_site_local)) AS server_is_site_local, " +
                "COALESCE(BOOL_OR(s.destination_address_is_loopback) FILTER (WHERE n.successful), BOOL_OR(s.destination_address_is_loopback)) AS server_is_loopback, " +
                "COALESCE(BOOL_OR(s.destination_address_is_multicast) FILTER (WHERE n.successful), BOOL_OR(s.destination_address_is_multicast)) AS server_is_multicast, " +
                "MAX(s.bytes_rx_count + s.bytes_tx_count) AS bytes_exchanged " +
                "FROM nat_stun_negotiation_flows AS n " +
                "LEFT JOIN l4_sessions AS s ON s.session_key = n.l4_session_key " +
                "AND s.start_time >= n.first_seen - INTERVAL '10 seconds' " +
                "AND s.start_time <= n.first_seen + INTERVAL '10 seconds' " +
                "AND s.l4_type = UPPER(n.transport) AND n.tap_uuid = s.tap_uuid " +
                "WHERE n.last_activity >= :tr_from AND n.last_activity <= :tr_to " +
                "AND n.tap_uuid IN (<taps>)" + filterFragment.whereSql() +
                " GROUP BY n.negotiation_key HAVING 1=1 " + filterFragment.havingSql();
    }

    private String discoverySessionEndpointsSelect(FilterSqlFragment filterFragment) {
        return "SELECT d.l4_session_key, MAX(d.status) AS status, " +
                "MAX(s.source_address) AS source_address, " +
                "MAX(s.destination_address) AS destination_address " +
                "FROM nat_traversal_discoveries AS d " +
                "LEFT JOIN l4_sessions AS s ON s.session_key = d.l4_session_key " +
                "AND s.start_time >= d.first_seen - INTERVAL '10 seconds' " +
                "AND s.start_time <= d.first_seen + INTERVAL '10 seconds' " +
                "AND s.l4_type = UPPER(d.transport) AND d.tap_uuid = s.tap_uuid " +
                "WHERE d.first_seen >= :tr_from AND d.first_seen <= :tr_to " +
                "AND d.tap_uuid IN (<taps>)" + filterFragment.whereSql() +
                " GROUP BY d.l4_session_key HAVING 1=1 " + filterFragment.havingSql();
    }

    private String discoverySessionEndpointsSelectWithAttrs(FilterSqlFragment filterFragment) {
        return "SELECT d.l4_session_key, MAX(d.status) AS status, " +
                "MAX(s.source_address) AS source_address, " +
                "MAX(s.source_mac) AS source_mac, MAX(s.source_port) AS source_port, " +
                "MAX(s.source_address_geo_asn_number) AS source_geo_asn_number, " +
                "MAX(s.source_address_geo_asn_name) AS source_geo_asn_name, " +
                "MAX(s.source_address_geo_asn_domain) AS source_geo_asn_domain, " +
                "MAX(s.source_address_geo_city) AS source_geo_city, " +
                "MAX(s.source_address_geo_country_code) AS source_geo_country_code, " +
                "MAX(s.source_address_geo_latitude) AS source_geo_latitude, " +
                "MAX(s.source_address_geo_longitude) AS source_geo_longitude, " +
                "BOOL_OR(s.source_address_is_site_local) AS source_is_site_local, " +
                "BOOL_OR(s.source_address_is_loopback) AS source_is_loopback, " +
                "BOOL_OR(s.source_address_is_multicast) AS source_is_multicast, " +
                "MAX(s.destination_address) AS destination_address, " +
                "MAX(s.destination_mac) AS destination_mac, MAX(s.destination_port) AS destination_port, " +
                "MAX(s.destination_address_geo_asn_number) AS destination_geo_asn_number, " +
                "MAX(s.destination_address_geo_asn_name) AS destination_geo_asn_name, " +
                "MAX(s.destination_address_geo_asn_domain) AS destination_geo_asn_domain, " +
                "MAX(s.destination_address_geo_city) AS destination_geo_city, " +
                "MAX(s.destination_address_geo_country_code) AS destination_geo_country_code, " +
                "MAX(s.destination_address_geo_latitude) AS destination_geo_latitude, " +
                "MAX(s.destination_address_geo_longitude) AS destination_geo_longitude, " +
                "BOOL_OR(s.destination_address_is_site_local) AS destination_is_site_local, " +
                "BOOL_OR(s.destination_address_is_loopback) AS destination_is_loopback, " +
                "BOOL_OR(s.destination_address_is_multicast) AS destination_is_multicast " +
                "FROM nat_traversal_discoveries AS d " +
                "LEFT JOIN l4_sessions AS s ON s.session_key = d.l4_session_key " +
                "AND s.start_time >= d.first_seen - INTERVAL '10 seconds' " +
                "AND s.start_time <= d.first_seen + INTERVAL '10 seconds' " +
                "AND s.l4_type = UPPER(d.transport) AND d.tap_uuid = s.tap_uuid " +
                "WHERE d.first_seen >= :tr_from AND d.first_seen <= :tr_to " +
                "AND d.tap_uuid IN (<taps>)" + filterFragment.whereSql() +
                " GROUP BY d.l4_session_key HAVING 1=1 " + filterFragment.havingSql();
    }


}